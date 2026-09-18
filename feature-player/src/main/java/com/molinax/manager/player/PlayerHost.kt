package com.molinax.player

import android.content.ComponentName
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.molinax.common.logging.AppLogger

/**
 * Player subsystem UI (Phase 4). Tiga tanggung jawab dipisah jelas, mengikuti keputusan
 * arsitektur "ikuti pola mpv-android" (17 Sep 2026, lihat komentar lengkap di
 * PlayerPlaybackService.kt untuk pemetaan lifecycle):
 *
 * 1. Surface video (AndroidView(MPVView)) -- dibuat/dibongkar bebas tiap Composable ini masuk/
 *    keluar komposisi (pindah tab). AMAN karena context native mpv TIDAK dimiliki View ini lagi
 *    (lihat BaseMPVView.kt) -- initialize()/destroy() instance di sini cuma attach/detach Surface.
 * 2. MediaController -- link kontrol (play/pause) dari UI ke MediaSession yang hidup di
 *    PlayerPlaybackService (VERIFIED developer.android.com/media/media3/session/connect-to-media-app:
 *    "the onStart() method of your Activity or Fragment can be a good place for this" -- dipetakan
 *    ke DisposableEffect(Unit) Composable ini, padanan Compose untuk lifecycle itu).
 * 3. Input Local (SAF ActivityResultContracts.OpenDocument -- blueprint §7 tabel "Tiga metode
 *    input Player": "SAF / runtime storage permission, tanpa dependency tambahan"). Uri hasil
 *    picker diterjemahkan MediaUriResolver lalu diteruskan lewat MPVLib.command("loadfile")
 *    LANGSUNG (bukan lewat BaseMPVView.playFile() yang cuma di-consume surfaceCreated() SEKALI --
 *    pola ini identik MPVActivity.onNewIntent() upstream: "MPVLib.command(arrayOf(\"loadfile\",
 *    filepath))" dipakai persis untuk kasus "sudah berjalan, muat file baru sekarang").
 *
 * Metode input "by title" (ytsearch:) dan "link universal" (ytdl_hook, butuh wiring ytdl_path ke
 * RuntimeExecutionBridge.buildDirectExecWrapper()) BELUM ada di sini -- menyusul, VERIFY FIRST
 * urutan wiring-nya (lihat audit gap Phase 4). DownloadManager (mode simpan file) juga belum ada.
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun PlayerHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf<String?>(null) }

    // Connect MediaController -> MediaSession di PlayerPlaybackService. SessionToken bertipe
    // TYPE_SESSION_SERVICE (constructor dengan ComponentName, VERIFIED dari referensi resmi
    // MediaController.Builder): "The controller will bind to the service as long as it's
    // connected to wake up and keep the service process running" -- artinya start+bind Service
    // ditangani library, PlayerHost TIDAK perlu startForegroundService()/bindService() manual
    // seperti pola TerminalService (beda dari TerminalHost.kt secara sengaja, MediaSessionService
    // punya jalur resmi sendiri, lihat komentar arsitektur PlayerPlaybackService.kt).
    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlayerPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val controller = controllerFuture.get()
                mediaController = controller
                isPlaying = controller.isPlaying
                currentTitle = controller.mediaMetadata.title?.toString()
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        currentTitle = mediaMetadata.title?.toString()
                    }
                })
            },
            MoreExecutors.directExecutor(),
        )

        onDispose {
            AppLogger.i("PlayerHost", "DisposableEffect onDispose: release MediaController")
            MediaController.releaseFuture(controllerFuture)
            mediaController = null
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = MediaUriResolver.resolveToPlayablePath(context, uri)
        AppLogger.i("PlayerHost", "Local input dipilih, path resolved: $path")
        MPVLib.command(arrayOf("loadfile", path))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(currentTitle ?: "Player") }) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MPVView(ctx).also { view ->
                            view.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                        }
                    },
                    onRelease = { view ->
                        view.destroy()
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Button(onClick = { openDocumentLauncher.launch(LOCAL_INPUT_MIME_TYPES) }) {
                    Text("Buka File")
                }
                Button(
                    onClick = {
                        val controller = mediaController ?: return@Button
                        if (controller.isPlaying) controller.pause() else controller.play()
                    },
                ) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
            }
        }
    }
}

// ACTION_OPEN_DOCUMENT (blueprint §7 "Local (sdcard)": SAF, tanpa dependency tambahan) --
// video+audio, konsisten dengan MediaAutoRoute (feature-utilities, Phase 8) yang nanti memakai
// daftar ekstensi resmi libmpv/ffmpeg yang sama (docs/player.md, belum dikunci -- lihat blueprint
// §9.1 catatan struktural).
private val LOCAL_INPUT_MIME_TYPES = arrayOf("video/*", "audio/*")
