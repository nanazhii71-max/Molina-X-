package com.molinax.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.molinax.terminal.io.TerminalExtraKeys
import com.molinax.terminal.io.extrakeys.ExtraKeysConstants
import com.molinax.terminal.io.extrakeys.ExtraKeysInfo
import com.molinax.terminal.io.extrakeys.ExtraKeysView
import com.molinax.terminal.service.TerminalService
import com.molinax.terminal.service.TerminalServiceState
import com.molinax.terminal.session.MolinaXTerminalViewClient
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var boundService by remember { mutableStateOf<TerminalService?>(null) }
    var uiState by remember { mutableStateOf<TerminalServiceState>(TerminalServiceState.CheckingRootfs) }
    var sessionFinishedMessage by remember { mutableStateOf<String?>(null) }

    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var extraKeysView by remember { mutableStateOf<ExtraKeysView?>(null) }
    var terminalWired by remember { mutableStateOf(false) }

    // Bind ke TerminalService (pola resmi TermuxActivity: startService() dulu supaya proses shell
    // tetap hidup terlepas dari siapa yang bind, baru bindService() dengan flags=0 -- BUKAN
    // BIND_AUTO_CREATE, karena start dan bind sengaja dipisah). Hanya di-unbind saat Composable
    // dibuang (pindah tab/Activity destroy) -- TIDAK di-stopService, supaya shell tetap jalan di
    // background persis tujuan Opsi A.
    DisposableEffect(Unit) {
        val serviceIntent = Intent(context, TerminalService::class.java)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                boundService = (binder as TerminalService.LocalBinder).service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }

        context.startForegroundService(serviceIntent)
        // flags=0 (bukan BIND_AUTO_CREATE) -- persis pola resmi TermuxActivity: start dan bind
        // sengaja dipisah supaya proses shell tetap hidup terlepas dari ada/tidaknya binder aktif.
        context.bindService(serviceIntent, connection, 0)

        onDispose {
            boundService?.onSessionTextChanged = null
            context.unbindService(connection)
            boundService = null
            terminalWired = false
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
    if (!terminalWired && readySession != null && currentTerminalView != null && currentService != null) {
        terminalWired = true
        currentService.onSessionTextChanged = { terminalView?.onScreenUpdated() }

        val viewClient = MolinaXTerminalViewClient(
            context,
            currentTerminalView,
            currentExtraKeysView,
        )
        currentTerminalView.setTextSize(defaultTerminalTextSizePx(context))
        currentTerminalView.setTerminalViewClient(viewClient)
        currentTerminalView.attachSession(readySession)
        currentTerminalView.requestFocus()

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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                            onClick = { boundService?.retry() },
                        ) {
                            Text("Coba lagi")
                        }
                    }
                }

                is TerminalServiceState.SessionReady -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        sessionFinishedMessage?.let { message ->
                            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                Text(text = message, style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { boundService?.retry() }) {
                                    Text("Mulai sesi baru")
                                }
                            }
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = { ctx -> TerminalView(ctx, null).also { terminalView = it } },
                        )
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { ctx -> ExtraKeysView(ctx, null).also { extraKeysView = it } },
                        )
                    }
                }
            }
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
