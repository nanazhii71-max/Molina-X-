package com.molinax.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Binder
import android.os.IBinder
import com.molinax.terminal.R
import com.molinax.terminal.runtime.PinnedDebianRootfs
import com.molinax.terminal.runtime.RootfsProvisioner
import com.molinax.terminal.session.InteractiveProotSessionFactory
import com.molinax.terminal.session.MolinaXTerminalSessionClient
import com.molinax.terminal.session.ProotBinaryMissingException
import com.molinax.terminal.session.RootfsNotReadyException
import com.molinax.terminal.session.SessionMetadataStore
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
        super.onCreate()
        provisioner = RootfsProvisioner(filesDir)
        sessionFactory = InteractiveProotSessionFactory(this, provisioner)
        sessionMetadataStore = SessionMetadataStore(this)

        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(sessionActive = false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            session?.finishIfRunning()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureSessionStarted()
        return START_STICKY
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
                        session = null
                        updateNotification(sessionActive = false)
                        _sessionFinishedMessage.value =
                            "Sesi shell berakhir (exit code ${finishedSession.exitStatus})."
                    },
                )

                val newSession = withContext(Dispatchers.IO) {
                    val sessions = sessionMetadataStore.getSessions()
                    val metadata = sessions.firstOrNull() ?: sessionMetadataStore.addSession(
                        handle = UUID.randomUUID().toString(),
                        displayName = "Terminal",
                        workingDirectory = "/root",
                    )
                    sessionFactory.create(cwd = metadata.workingDirectory, client = sessionClient)
                }

                session = newSession
                updateNotification(sessionActive = true)
                _state.value = TerminalServiceState.SessionReady(newSession)
            } catch (e: RootfsNotReadyException) {
                _state.value = TerminalServiceState.Error(e.message ?: "Rootfs belum siap.")
            } catch (e: ProotBinaryMissingException) {
                _state.value = TerminalServiceState.Error(e.message ?: "Binary proot tidak ditemukan.")
            } catch (e: IOException) {
                _state.value = TerminalServiceState.Error(
                    e.message ?: "Gagal menyiapkan rootfs: ${e::class.simpleName}"
                )
            } finally {
                startInFlight = false
            }
        }
    }

    /** Dipanggil dari tombol "Coba lagi" di UI, baik saat [TerminalServiceState.Error] maupun setelah sesi berakhir. */
    fun retry() {
        session = null
        startSession()
    }

    override fun onDestroy() {
        session?.finishIfRunning()
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
        const val ACTION_STOP = "com.molinax.terminal.action.STOP"
    }
}
