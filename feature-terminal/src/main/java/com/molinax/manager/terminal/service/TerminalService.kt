package com.molinax.manager.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Binder
import android.os.IBinder
import com.molinax.manager.common.logging.AppLogger
import com.molinax.manager.terminal.R
import com.molinax.manager.terminal.runtime.PinnedDebianRootfs
import com.molinax.manager.terminal.runtime.RootfsProvisioner
import com.molinax.manager.terminal.session.InteractiveProotSessionFactory
import com.molinax.manager.terminal.session.MolinaXTerminalSessionClient
import com.molinax.manager.terminal.session.ProotBinaryMissingException
import com.molinax.manager.terminal.session.RootfsNotReadyException
import com.molinax.manager.terminal.session.SessionMetadataStore
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Representasi state penyiapan sesi shell, dipakai UI (TerminalHost) untuk render progres.
 * Dipindah dari TerminalHost.kt (sebelumnya sealed class privat di situ) supaya bisa dipakai
 * bersama antara Service dan UI setelah refactor Bound Service (Opsi A, lihat handoff Phase 3).
 */
sealed class TerminalServiceState {
    data object CheckingRootfs : TerminalServiceState()
    data class Provisioning(val fraction: Float) : TerminalServiceState()
    data object StartingSession : TerminalServiceState()
    data class SessionReady(val session: TerminalSession) : TerminalServiceState()
    data class Error(val message: String) : TerminalServiceState()
}

/**
 * Bound Service yang memegang [TerminalSession] secara independen dari lifecycle Activity/Compose
 * -- pola resmi diverifikasi langsung dari source `termux/termux-app`
 * (`app/src/main/java/com/termux/app/TermuxService.java`): in-process [LocalBinder]
 * ("This service is only bound from inside the same process and never uses IPC" -- javadoc
 * `TermuxService.LocalBinder` resmi), `startForeground()` sejak `onCreate()`, dan proses shell
 * tetap hidup walau Activity yang membuka Terminal di-destroy ("this service may outlive the
 * activity when the user or the system disposes of the activity" -- javadoc `TermuxService`
 * resmi). UI (TerminalHost, Compose) hanya *bind* untuk membaca [state] dan menampilkan output --
 * tidak lagi membuat/memegang [TerminalSession] sendiri.
 */
class TerminalService : Service() {

    inner class LocalBinder : Binder() {
        val service: TerminalService get() = this@TerminalService
    }

    private val binder = LocalBinder()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<TerminalServiceState>(TerminalServiceState.CheckingRootfs)
    val state: StateFlow<TerminalServiceState> = _state.asStateFlow()

    /**
     * Terpisah dari [state] dengan sengaja: begitu shell exit, [state] TETAP [TerminalServiceState.SessionReady]
     * (transkrip terakhir masih ditampilkan, TerminalView tidak dibongkar) -- hanya banner pesan ini yang
     * muncul di atasnya. Kalau exit dituliskan ke [state] sebagai Error, UI akan pindah ke layar error dan
     * transkrip yang baru selesai dibaca pengguna hilang begitu saja.
     */
    private val _sessionFinishedMessage = MutableStateFlow<String?>(null)
    val sessionFinishedMessage: StateFlow<String?> = _sessionFinishedMessage.asStateFlow()

    /** Callback UI (di-set oleh TerminalHost saat AndroidView TerminalView siap) untuk repaint layar. */
    var onSessionTextChanged: (() -> Unit)? = null

    private var session: TerminalSession? = null
    private var startInFlight = false

    /**
     * VERIFIED FIX (bug: notifikasi "Stop" hidup lagi & memicu siklus onCreate/onDestroy kedua --
     * dibuktikan dari logcat nyata: "Sesi selesai" ter-log SETELAH "onDestroy", karena
     * [MolinaXTerminalSessionClient] sessionFinishedListener bersifat async dan sebelumnya
     * memanggil [updateNotification] TANPA syarat, termasuk saat stopSelf() sedang berjalan --
     * notify() ulang notifikasi ongoing dengan tombol Stop yang PendingIntent-nya masih valid
     * setelah instance Service ini mati, sehingga tap berikutnya bikin instance BARU cuma untuk
     * langsung mati lagi. Pola resmi diverifikasi dari `termux/termux-app` `TermuxService.java`
     * (`actionStopService()`/`mWantsToStop`): set flag SEBELUM kill, dan skip re-notify kalau
     * flag ini true.
     */
    private var stopRequested = false

    /**
     * Beda dengan `session != null`: dipakai supaya [ensureSessionStarted] hanya mencoba SEKALI
     * secara otomatis per lifecycle Service (mis. saat TerminalHost bind pertama kali). Kalau shell
     * sudah exit dan `session` jadi null lagi, bind ulang (mis. Activity kembali dari background)
     * TIDAK otomatis membuka shell baru tanpa sepengetahuan pengguna -- harus lewat [retry] eksplisit
     * (tombol "Coba lagi" / retry setelah banner sesi berakhir).
     */
    private var hasAttemptedStart = false

    private lateinit var provisioner: RootfsProvisioner
    private lateinit var sessionFactory: InteractiveProotSessionFactory
    private lateinit var sessionMetadataStore: SessionMetadataStore

    override fun onCreate() {
        AppLogger.i("TerminalService", "onCreate (instance baru dibuat)")
        super.onCreate()
        provisioner = RootfsProvisioner(filesDir)
        sessionFactory = InteractiveProotSessionFactory(this, provisioner)
        sessionMetadataStore = SessionMetadataStore(this)

        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(sessionActive = false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i(
            "TerminalService",
            "onStartCommand action=${intent?.action} session=${session != null} isRunning=${session?.isRunning}",
        )
        when (intent?.action) {
            ACTION_STOP -> {
                // VERIFIED FIX: set stopRequested SEBELUM finishIfRunning(), supaya
                // sessionFinishedListener (async, bisa nembak setelah stopSelf()/onDestroy())
                // tahu ini stop yang disengaja dan tidak resurrect notifikasi.
                stopRequested = true
                session?.finishIfRunning()
                // VERIFIED FIX dari TermuxService.requestStopService(): stopForeground(true)
                // EKSPLISIT + cancel notifikasi manual sebelum stopSelf() -- terbukti dari logcat
                // bahwa "notifikasi otomatis hilang saat Service destroy" TIDAK reliable di sini
                // karena race dengan callback async sessionFinishedListener.
                stopForeground(true)
                getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RETRY -> {
                // Lewat Intent -> onStartCommand, BUKAN panggilan method langsung ke
                // referensi binder -- jalur ini sudah terbukti reliable (sama seperti
                // ACTION_STOP), tidak bergantung pada boundService yang bisa stale/null
                // di sisi UI (Compose).
                retry()
                return START_STICKY
            }
            else -> {
                ensureSessionStarted()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Aman dipanggil berkali-kali (mis. tiap kali TerminalHost bind ulang) -- hanya benar-benar
     * memulai sesi SEKALI secara otomatis per lifecycle Service. Untuk memulai ulang setelah sesi
     * berakhir, panggil [retry] secara eksplisit, bukan fungsi ini.
     */
    fun ensureSessionStarted() {
        if (hasAttemptedStart) return
        hasAttemptedStart = true
        startSession()
    }

    private fun startSession() {
        if (session != null || startInFlight) return
        startInFlight = true
        _sessionFinishedMessage.value = null

        serviceScope.launch {
            _state.value = TerminalServiceState.CheckingRootfs
            try {
                val spec = PinnedDebianRootfs.forCurrentDevice()
                if (!provisioner.isProvisioned(spec)) {
                    _state.value = TerminalServiceState.Provisioning(0f)
                    withContext(Dispatchers.IO) {
                        provisioner.provision(spec) { bytesRead, totalBytes ->
                            val fraction = if (totalBytes > 0) {
                                (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            _state.value = TerminalServiceState.Provisioning(fraction)
                        }
                    }
                }

                _state.value = TerminalServiceState.StartingSession

                val sessionClient = MolinaXTerminalSessionClient(
                    context = this@TerminalService,
                    textChangedListener = { onSessionTextChanged?.invoke() },
                    titleChangedListener = { },
                    sessionFinishedListener = { finishedSession ->
                        AppLogger.i("TerminalService", "Sesi selesai, exitStatus=${finishedSession.exitStatus}")
                        session = null
                        // VERIFIED FIX: kalau stop ini disengaja (ACTION_STOP), JANGAN re-notify --
                        // itulah yang bikin notifikasi "Stop" hidup lagi setelah Service sudah mati
                        // (lihat catatan di stopRequested/ACTION_STOP di atas).
                        if (!stopRequested) {
                            updateNotification(sessionActive = false)
                            _sessionFinishedMessage.value =
                                "Sesi shell berakhir (exit code ${finishedSession.exitStatus})."
                        }
                    },
                )

                // Hanya kerja I/O-bound (baca metadata, validasi rootfs/proot, susun argumen) yang
                // masuk Dispatchers.IO. Konstruksi TerminalSession() SENGAJA di luar withContext
                // ini -- serviceScope berbasis Dispatchers.Main, jadi begitu withContext(IO) di
                // bawah selesai, eksekusi kembali ke main thread sebelum createSession() dipanggil.
                // Konstruktor TerminalSession (termux-app v0.118.3) membuat android.os.Handler
                // secara internal, yang butuh Looper.prepare() -- hanya tersedia di main thread.
                // Memanggilnya dari Dispatchers.IO menyebabkan
                // "Can't create handler inside thread that has not called Looper.prepare()".
                val launchSpec = withContext(Dispatchers.IO) {
                    val sessions = sessionMetadataStore.getSessions()
                    val metadata = sessions.firstOrNull() ?: sessionMetadataStore.addSession(
                        handle = UUID.randomUUID().toString(),
                        displayName = "Terminal",
                        workingDirectory = "/root",
                    )
                    sessionFactory.prepareLaunchSpec(cwd = metadata.workingDirectory)
                }

                val newSession = sessionFactory.createSession(launchSpec, sessionClient)
                session = newSession
                updateNotification(sessionActive = true)
                _state.value = TerminalServiceState.SessionReady(newSession)
            } catch (e: RootfsNotReadyException) {
                AppLogger.e("TerminalService", "RootfsNotReadyException saat startSession", e)
                _state.value = TerminalServiceState.Error(e.message ?: "Rootfs belum siap.")
            } catch (e: ProotBinaryMissingException) {
                AppLogger.e("TerminalService", "ProotBinaryMissingException saat startSession", e)
                _state.value = TerminalServiceState.Error(e.message ?: "Binary proot tidak ditemukan.")
            } catch (e: IOException) {
                AppLogger.e("TerminalService", "IOException saat startSession", e)
                _state.value = TerminalServiceState.Error(
                    e.message ?: "Gagal menyiapkan rootfs: ${e::class.simpleName}"
                )
            } catch (e: Throwable) {
                // Sebelumnya: exception di luar 3 tipe di atas TIDAK tertangkap sama sekali --
                // korban diam tanpa _state pernah berubah, karena propagate ke default uncaught
                // handler. Ditangkap di sini supaya UI selalu dapat reaksi (layar Error), dan
                // tetap dicatat lengkap untuk analisa.
                AppLogger.e("TerminalService", "Exception TAK TERDUGA saat startSession", e)
                _state.value = TerminalServiceState.Error(
                    "Error tak terduga: ${e::class.simpleName}: ${e.message}"
                )
            } finally {
                startInFlight = false
            }
        }
    }

    /** Dipanggil dari tombol "Coba lagi" di UI, baik saat [TerminalServiceState.Error] maupun setelah sesi berakhir. */
    fun retry() {
        AppLogger.i("TerminalService", "retry() dipanggil, session sebelumnya=${session != null}")
        session = null
        startSession()
    }

    override fun onDestroy() {
        AppLogger.i("TerminalService", "onDestroy dipanggil (instance ini akan dihancurkan)")
        session?.finishIfRunning()
        // VERIFIED FIX: defense-in-depth, persis TermuxService.onDestroy() yang tetap panggil
        // stopForeground(true) di sini WALAUPUN requestStopService() sudah panggilnya duluan --
        // menjaga jalur onDestroy tidak lewat ACTION_STOP (mis. dibunuh sistem) tetap bersih.
        stopForeground(true)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun setupNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Molina-X Terminal",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(sessionActive: Boolean): Notification {
        val stopIntent = Intent(this, TerminalService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Molina-X Terminal")
            .setContentText(
                if (sessionActive) "Sesi shell berjalan" else "Menyiapkan sesi shell..."
            )
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_terminal),
                    "Stop",
                    stopPendingIntent,
                ).build()
            )
            .build()
    }

    private fun updateNotification(sessionActive: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(sessionActive))
    }

    companion object {
        private const val CHANNEL_ID = "molinax_terminal"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.molinax.manager.terminal.action.STOP"
        const val ACTION_RETRY = "com.molinax.manager.terminal.action.RETRY"
    }
}