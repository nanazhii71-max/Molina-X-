package com.molinax.terminal

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.molinax.common.logging.AppLogger
import com.molinax.terminal.io.TerminalExtraKeys
import com.molinax.terminal.io.extrakeys.ExtraKeysConstants
import com.molinax.terminal.io.extrakeys.ExtraKeysInfo
import com.molinax.terminal.io.extrakeys.ExtraKeysView
import com.molinax.terminal.service.TerminalService
import com.molinax.terminal.service.TerminalServiceState
import com.molinax.terminal.session.MolinaXTerminalViewClient
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * Default row layout dari JavaDoc resmi [ExtraKeysInfo] (contoh "2 row" di kelas itu) --
 * dipakai apa adanya, bukan tebakan. Cukup untuk penggunaan shell interaktif dasar
 * (ESC/TAB/CTRL/ALT/arrow/HOME/END/PGUP/PGDN); kustomisasi extra keys per pengguna
 * (butuh TerminalPreferences yang belum ada -- lihat handoff Phase 3) ditunda ke fase lanjutan.
 */
private const val DEFAULT_EXTRA_KEYS_LAYOUT =
    "[['ESC','/',{key: '-', popup: '|'},'HOME','UP','END','PGUP']," +
        "['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]"

/**
 * VERIFIED dari activity_termux.xml resmi (termux-app tag v0.118.3): baris
 * `android:layout_height="37.5dp"` untuk container extra keys per baris. Layout kita di atas
 * punya 2 baris, jadi total tinggi = 37.5dp x 2 = 75dp. Tanpa tinggi eksplisit ini, ExtraKeysView
 * (GridLayout dengan child LayoutParams width=0/height=0 + weight FILL -- lihat
 * ExtraKeysView.reload()) collapse jadi 0dp di dalam parent WRAP_CONTENT dan tidak pernah terlihat.
 */
private val EXTRA_KEYS_HEIGHT = 75.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // MODIFY blueprint §8.3 (keputusan eksplisit pengguna, dimajukan dari "Fase lanjutan"):
    // shared storage dipakai sejak Phase 3 supaya terminal Molina-X bisa dipakai kerja nyata
    // (pengganti Termux asli) mulai Phase 4. Digating DI SINI, SEBELUM DisposableEffect(Unit) di
    // bawah sempat memanggil startForegroundService()/bindService() -- proot spawn (jadi juga
    // bind mount /storage/emulated/0, lihat InteractiveProotSessionFactory.kt) TIDAK BOLEH
    // terjadi sebelum izin granted, supaya proses shell yang jadi anak app selalu lahir dengan
    // grup akses storage yang sudah benar sejak awal (bukan proses lama yang izinnya baru
    // menyusul -- status grup Linux untuk shared storage tidak dijamin ter-refresh pada proses
    // yang sudah berjalan sebelum grant, ini WAJIB diverifikasi nyata lewat `ls
    // /storage/emulated/0/` di dalam sesi setelah izin diberikan, bukan diasumsikan otomatis).
    var storagePermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        storagePermissionGranted = grants.values.all { it }
    }

    if (!storagePermissionGranted) {
        StoragePermissionGate(
            modifier = modifier,
            onRequestPermission = {
                storagePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ),
                )
            },
        )
        return
    }

    var boundService by remember { mutableStateOf<TerminalService?>(null) }
    var uiState by remember { mutableStateOf<TerminalServiceState>(TerminalServiceState.CheckingRootfs) }
    var sessionFinishedMessage by remember { mutableStateOf<String?>(null) }

    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var extraKeysView by remember { mutableStateOf<ExtraKeysView?>(null) }
    // VERIFIED FIX (bug: layar beku setelah Stop dari notif + reopen): sebelumnya ini Boolean
    // sekali-jalan (`terminalWired`). Begitu di-set true, TerminalView tidak PERNAH di-attachSession
    // ulang lagi -- padahal setelah Stop (SIGKILL sengaja, lihat TerminalSession.finishIfRunning()
    // v0.118.3) lalu app dibuka lagi, TerminalService instance BARU membuat TerminalSession BARU,
    // tapi Composable ini (kalau Activity tidak destroy) masih hidup dengan terminalWired=true --
    // sesi baru itu tidak pernah ter-attach ke View, layar tetap menampilkan transkrip sesi lama
    // yang sudah mati. Diganti keyed by identitas objek TerminalSession: setiap kali readySession
    // berbeda dari yang terakhir di-attach, wiring (attachSession + rebind onSessionTextChanged +
    // reload ExtraKeys) dijalankan ulang.
    var attachedSession by remember { mutableStateOf<TerminalSession?>(null) }

    // Bind ke TerminalService (pola resmi TermuxActivity: startService() dulu supaya proses shell
    // tetap hidup terlepas dari siapa yang bind, baru bindService() dengan flags=0 -- BUKAN
    // BIND_AUTO_CREATE, karena start dan bind sengaja dipisah). Hanya di-unbind saat Composable
    // dibuang (pindah tab/Activity destroy) -- TIDAK di-stopService, supaya shell tetap jalan di
    // background persis tujuan Opsi A.
    //
    // `serviceIntent`/`connection` diangkat ke scope composable (bukan lokal di dalam
    // DisposableEffect) supaya bisa dipakai ulang oleh rebind() di bawah.
    val serviceIntent = remember { Intent(context, TerminalService::class.java) }
    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                AppLogger.i("TerminalHost", "onServiceConnected")
                boundService = (binder as TerminalService.LocalBinder).service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                AppLogger.i("TerminalHost", "onServiceDisconnected (binder connection hilang)")
                boundService = null
                // VERIFIED FIX: sesi terpisah dari lifecycle instance Service (finishIfRunning
                // mengirim SIGKILL, session di sisi Service jadi null) -- kalau transkrip lama
                // (attachedSession) tidak ikut dibersihkan di sini, layar tetap menampilkan
                // transkrip sesi yang sudah mati sampai sesi BARU siap.
                attachedSession = null
            }
        }
    }

    // VERIFIED FIX (bug: "boundService null? true" permanen setelah Stop dari notifikasi --
    // tombol "Coba lagi"/"Mulai sesi baru"/Enter mengirim ACTION_RETRY lewat startService() saja,
    // TIDAK PERNAH memicu bindService() lagi). Reconnect otomatis ServiceConnection dari
    // ServiceConnection.onServiceDisconnected javadoc ("client akan otomatis disambungkan ulang")
    // UNVERIFIED untuk kasus stopSelf() normal same-process seperti ini (javadoc itu konteksnya
    // proses biner mati/crash) -- jangan diasumsikan berlaku, karena itu memang tidak terjadi di
    // buktinya (logcat: tidak ada satupun onServiceConnected lagi setelah bind pertama, walau
    // TerminalService instance baru berhasil dibuat berkali-kali).
    //
    // unbindService() dipanggil dulu (dibungkus try/catch -- aman meski koneksi sudah putus
    // lewat onServiceDisconnected dan secara teknis "belum" di-unbind manual) SEBELUM
    // bindService() lagi dengan objek `connection` yang SAMA. Ini deterministik: tidak
    // bergantung pada perilaku auto-reconnect AOSP yang belum terverifikasi untuk kasus ini.
    fun rebind() {
        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            AppLogger.i("TerminalHost", "unbindService sebelum rebind: koneksi belum terdaftar (aman)")
        }
        // PENTING: pakai Intent dengan ACTION_RETRY di sini -- `serviceIntent` (tanpa action)
        // dipakai KHUSUS untuk bindService() (identitas binding harus konsisten dengan yang di
        // DisposableEffect awal). Kalau startForegroundService() dipanggil pakai serviceIntent
        // polos, TerminalService.retry() TIDAK PERNAH terpanggil (onStartCommand masuk cabang
        // `else -> ensureSessionStarted()`, no-op karena hasAttemptedStart sudah true).
        context.startForegroundService(
            Intent(context, TerminalService::class.java).setAction(TerminalService.ACTION_RETRY),
        )
        context.bindService(serviceIntent, connection, 0)
    }

    DisposableEffect(Unit) {
        context.startForegroundService(serviceIntent)
        // flags=0 (bukan BIND_AUTO_CREATE) -- persis pola resmi TermuxActivity: start dan bind
        // sengaja dipisah supaya proses shell tetap hidup terlepas dari ada/tidaknya binder aktif.
        context.bindService(serviceIntent, connection, 0)

        onDispose {
            AppLogger.i("TerminalHost", "DisposableEffect onDispose (composable dibongkar dari komposisi)")
            boundService?.onSessionTextChanged = null
            context.unbindService(connection)
            boundService = null
            attachedSession = null
        }
    }

    LaunchedEffect(boundService) {
        val service = boundService ?: return@LaunchedEffect
        service.ensureSessionStarted()
    }

    LaunchedEffect(boundService) {
        val service = boundService ?: return@LaunchedEffect
        service.state.collect { state -> uiState = state }
    }

    LaunchedEffect(boundService) {
        val service = boundService ?: return@LaunchedEffect
        service.sessionFinishedMessage.collect { message -> sessionFinishedMessage = message }
    }

    // Wiring TerminalView + ExtraKeysView + TerminalSession baru dilakukan sekali, begitu
    // ketiganya (dua View lewat AndroidView interop, satu TerminalSession lewat state dari
    // service) sudah tersedia bersamaan. Tidak bergantung pada urutan komposisi AndroidView.
    val readySession = (uiState as? TerminalServiceState.SessionReady)?.session
    val currentTerminalView = terminalView
    val currentExtraKeysView = extraKeysView
    val currentService = boundService
    if (readySession != null && readySession !== attachedSession &&
        currentTerminalView != null && currentService != null
    ) {
        attachedSession = readySession
        currentService.onSessionTextChanged = { terminalView?.onScreenUpdated() }

        val viewClient = MolinaXTerminalViewClient(
            context,
            currentTerminalView,
            currentExtraKeysView,
            onRestartRequested = {
                AppLogger.i("TerminalHost", "Enter ditekan pada sesi mati, kirim ACTION_RETRY")
                rebind()
            },
        )
        currentTerminalView.setTextSize(defaultTerminalTextSizePx(context))
        currentTerminalView.setTerminalViewClient(viewClient)
        currentTerminalView.attachSession(readySession)

        // VERIFIED FIX (bug: keyboard tidak muncul saat pertama kali dibuka): pola ini
        // direplikasi dari TermuxTerminalViewClient.setSoftKeyboardState (termux-app v0.118.3) --
        // requestFocus() + postDelayed(showSoftInput, 300) SEKALI saat sesi baru siap, DITAMBAH
        // setOnFocusChangeListener permanen supaya keyboard konsisten muncul/hilang tiap kali
        // TerminalView memperoleh/kehilangan fokus (mis. balik dari background, ganti tab).
        // Sebelumnya HANYA mengandalkan tap manual (onSingleTapUp) tanpa delay dan tanpa listener --
        // showSoftInput yang dipanggil sinkron tepat setelah requestFocus() pada frame yang sama
        // sering gagal karena window belum benar-benar mendapat fokus IME.
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentTerminalView.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.postDelayed({ imm.showSoftInput(view, 0) }, 300)
            } else {
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
        currentTerminalView.requestFocus()
        currentTerminalView.postDelayed({ imm.showSoftInput(currentTerminalView, 0) }, 300)

        if (currentExtraKeysView != null) {
            val extraKeysInfo = ExtraKeysInfo(
                DEFAULT_EXTRA_KEYS_LAYOUT,
                ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES,
            )
            currentExtraKeysView.reload(extraKeysInfo)
            currentExtraKeysView.setExtraKeysViewClient(TerminalExtraKeys(currentTerminalView))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Terminal") }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                // VERIFIED FIX (bug: jarak ExtraKeys-ke-keyboard kelebihan): innerPadding dari
                // Scaffold sudah mencakup jatah navigationBars, tapi Modifier.padding(innerPadding)
                // biasa TIDAK menandai jatah itu "sudah dikonsumsi" ke sistem WindowInsets --
                // akibatnya imePadding() di Column SessionReady di bawah menghitung ulang dari nol
                // dan menambah dobel sebesar navigation bar. consumeWindowInsets(innerPadding)
                // (API resmi, developer.android.com/develop/ui/compose/system/insets-ui) memberi
                // tahu child bahwa jatah sebesar innerPadding sudah terpakai, sehingga imePadding()
                // di bawah hanya menambah SISA yang belum terpakai.
                .consumeWindowInsets(innerPadding),
        ) {
            when (val state = uiState) {
                is TerminalServiceState.CheckingRootfs, is TerminalServiceState.StartingSession -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            modifier = Modifier.padding(top = 16.dp),
                            text = if (state is TerminalServiceState.StartingSession) {
                                "Memulai sesi shell..."
                            } else {
                                "Memeriksa rootfs Debian..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is TerminalServiceState.Provisioning -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Menyiapkan rootfs Debian (unduh + verifikasi checksum)...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(
                            progress = { state.fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        )
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "${(state.fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is TerminalServiceState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Gagal menyiapkan Terminal:\n${state.message}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            modifier = Modifier.padding(top = 16.dp),
                            onClick = {
                                AppLogger.i("TerminalHost", "Tombol 'Coba lagi' ditekan")
                                rebind()
                            },
                        ) {
                            Text("Coba lagi")
                        }
                    }
                }

                is TerminalServiceState.SessionReady -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            // VERIFIED FIX: androidx.compose.foundation.layout.imePadding (dokumentasi resmi
                            // developer.android.com) -- di bawah enableEdgeToEdge() (dipakai MainActivity
                            // sejak awal), Compose TIDAK otomatis menggeser konten saat IME muncul walau
                            // windowSoftInputMode=adjustResize sudah diset; perlu imePadding() eksplisit
                            // supaya Column ini (TerminalView + ExtraKeysView) mengecil dan ExtraKeysView
                            // tetap berada tepat di atas keyboard, bukan tertutup olehnya.
                            .imePadding(),
                    ) {
                        sessionFinishedMessage?.let { message ->
                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text(text = message, style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = {
                                        // FIX (bug: tap terdaftar tapi retry() tidak bereaksi):
                                        // sebelumnya boundService?.retry() langsung ke referensi
                                        // binder -- diganti Intent ACTION_RETRY, jalur yang sama
                                        // dengan ACTION_STOP yang sudah terbukti reliable, tidak
                                        // bergantung pada boundService yang bisa stale/null.
                                        AppLogger.i(
                                            "TerminalHost",
                                            "Tombol 'Mulai sesi baru' ditekan, boundService null? ${boundService == null}",
                                        )
                                        rebind()
                                    },
                                ) {
                                    Text("Mulai sesi baru")
                                }
                            }
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = { ctx ->
                                TerminalView(ctx, null).apply {
                                    // VERIFIED FIX (akar bug keyboard DAN copy-paste): TerminalView.java resmi
                                    // (startTextSelectionMode() baris ~1389, onSingleTapUp internal baris ~158)
                                    // memanggil requestFocus() dan diam-diam berhenti kalau gagal. Dalam touch
                                    // mode, requestFocus() SELALU gagal kecuali focusableInTouchMode=true --
                                    // XML asli Termux (activity_termux.xml) set ini eksplisit; karena kita buat
                                    // TerminalView programatik (bukan inflate XML), atribut ini tidak pernah
                                    // ter-set otomatis dan harus diset manual di sini.
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    terminalView = this
                                }
                            },
                        )
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(EXTRA_KEYS_HEIGHT),
                            factory = { ctx -> ExtraKeysView(ctx, null).also { extraKeysView = it } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePermissionGate(modifier: Modifier = Modifier, onRequestPermission: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Terminal butuh izin storage",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = "Molina-X Terminal perlu akses ke /storage/emulated/0 supaya bisa dipakai " +
                "seperti Termux (mis. cd ke /storage/emulated/0/Molina-X). Tanpa izin ini, " +
                "sesi shell tidak akan dimulai.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            modifier = Modifier.padding(top = 16.dp),
            onClick = onRequestPermission,
        ) {
            Text("Berikan Izin Storage")
        }
    }
}

/**
 * Ukuran font default TerminalView dalam pixel, diskalakan dari `scaledDensity` device
 * (setara ~12sp) -- [TerminalView.setTextSize] WAJIB dipanggil sebelum
 * [TerminalView.attachSession] karena `TerminalView.updateSize()` (dipanggil dari
 * `attachSession()`/`onSizeChanged()`) membaca field internal renderer yang baru terisi
 * setelah `setTextSize()` dipanggil (terverifikasi langsung dari source `TerminalView.java`
 * resmi tag v0.118.3 -- tanpa panggilan ini, mRenderer null dan `updateSize()` NPE begitu
 * View sudah diukur/di-resize).
 */
private fun defaultTerminalTextSizePx(context: Context): Int {
    val scaledDensity = context.resources.displayMetrics.scaledDensity
    return (12f * scaledDensity).toInt().coerceAtLeast(1)
}
