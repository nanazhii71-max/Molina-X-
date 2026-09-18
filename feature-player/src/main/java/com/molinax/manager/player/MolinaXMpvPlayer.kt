package com.molinax.manager.player

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Adapter yang membungkus MPVLib (libmpv via JNI) sebagai androidx.media3.common.Player,
 * dipasang ke MediaSession di PlayerPlaybackService (Phase 4).
 *
 * CATATAN SCOPE (bukan placeholder): mpv mengelola playlist internalnya sendiri lewat
 * command "playlist-next"/"playlist-prev" yang tidak terlihat dari wrapper ini. Wrapper
 * HANYA mengekspos satu virtual media item yang merepresentasikan track yang sedang
 * diputar mpv saat ini, untuk kebutuhan notification (judul, posisi, play/pause). Kontrol
 * prev/next multi-item Media3 (COMMAND_SEEK_TO_NEXT_MEDIA_ITEM dkk) belum didukung versi
 * ini -- akan ditambah lewat custom CommandButton pada MediaSession, bukan lewat
 * Player.Commands bawaan, karena playlist mpv tidak dipetakan ke daftar MediaItem Media3.
 *
 * THREAD SAFETY (17 Sep 2026, VERIFIED dari feature-player/src/main/jni/jni_utils.cpp baris 11
 * "vm->AttachCurrentThread"): MPVLib.eventProperty()/event() dipanggil dari thread event mpv
 * sendiri (native, bukan main looper Android) -- pola event thread standar mpv-android.
 * SimpleBasePlayer.invalidateState() WAJIB dipanggil dari applicationLooper thread, jadi semua
 * mutasi state di callback MPVLib.EventObserver di bawah di-post lewat mainHandler, bukan
 * dieksekusi langsung dari thread pemanggil.
 */
@UnstableApi
class MolinaXMpvPlayer(
    private val applicationLooper: Looper,
    // Dipanggil sekali saat MPV_EVENT_SHUTDOWN diterima -- persis pola upstream
    // BackgroundPlaybackService.event() yang memanggil stopSelf() saat event ini muncul.
    // PlayerPlaybackService memakai ini untuk mematikan foreground service + destroyCore()
    // (lihat komentar arsitektur di PlayerPlaybackService.kt).
    private val onShutdown: () -> Unit = {},
) : SimpleBasePlayer(applicationLooper), MPVLib.EventObserver {

    private val mainHandler = Handler(applicationLooper)

    private val virtualMediaItemUid: Any = "molinax-mpv-current"

    // Field ini HANYA boleh dibaca/ditulis dari applicationLooper thread (lihat mainHandler.post
    // di semua callback EventObserver di bawah) -- tidak butuh @Volatile lagi karena tidak ada
    // lagi akses lintas-thread langsung ke field ini.
    private var currentTitle: String = ""
    private var currentDurationMs: Long = C.TIME_UNSET
    private var isPaused: Boolean = true
    private var playbackEnded: Boolean = false

    init {
        MPVLib.addObserver(this)
    }

    private val availableCommands: Player.Commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_RELEASE,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE,
        )
        .build()

    override fun getState(): State {
        val mediaItem = MediaItem.Builder()
            .setMediaId(virtualMediaItemUid.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(currentTitle.ifEmpty { null })
                    .build()
            )
            .build()

        val mediaItemDataBuilder = MediaItemData.Builder(virtualMediaItemUid)
            .setMediaItem(mediaItem)
        if (currentDurationMs != C.TIME_UNSET) {
            mediaItemDataBuilder.setDurationUs(currentDurationMs * 1000L)
        }

        val playbackState = if (playbackEnded) Player.STATE_ENDED else Player.STATE_READY

        return State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaylist(listOf(mediaItemDataBuilder.build()))
            .setCurrentMediaItemIndex(0)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(!isPaused, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs { positionMs() }
            .build()
    }

    private fun positionMs(): Long {
        // MPVLib.getPropertyDouble() adalah synchronous JNI call ke mpv core (bukan callback
        // event thread), aman dipanggil dari applicationLooper thread saat getState() dipanggil
        // Media3 -- beda kasus dengan eventProperty()/event() di bawah.
        val positionSeconds = MPVLib.getPropertyDouble("time-pos") ?: return 0L
        return (positionSeconds * 1000.0).toLong()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        MPVLib.setPropertyBoolean("pause", !playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        // mpv core context (MPVLib.create()+init()) sudah dijamin aktif sebelum instance ini
        // dibuat -- PlayerPlaybackService.onCreate() memanggil BaseMPVView.ensureCoreInitialized()
        // SEBELUM membuat MolinaXMpvPlayer (lihat komentar arsitektur di PlayerPlaybackService.kt);
        // tidak ada langkah prepare tambahan yang perlu dilakukan di sisi wrapper ini.
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        MPVLib.command(arrayOf("stop"))
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        MPVLib.removeObserver(this)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val seconds = positionMs / 1000.0
        MPVLib.command(arrayOf("seek", seconds.toString(), "absolute"))
        return Futures.immediateVoidFuture()
    }

    /* MPVLib.EventObserver -- dipanggil dari thread event mpv (native), BUKAN applicationLooper
       (VERIFIED jni_utils.cpp "AttachCurrentThread"). Semua mutasi state + invalidateState()
       WAJIB di-post ke mainHandler, tidak boleh dieksekusi langsung di sini. */

    override fun eventProperty(property: String) {
        // no-op: tidak ada properti tanpa-value yang diobservasi wrapper ini
    }

    override fun eventProperty(property: String, value: Long) {
        // no-op: tidak ada properti Long yang diobservasi wrapper ini saat ini
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property != "pause") return
        mainHandler.post {
            isPaused = value
            invalidateState()
        }
    }

    override fun eventProperty(property: String, value: String) {
        if (property != "media-title") return
        mainHandler.post {
            currentTitle = value
            invalidateState()
        }
    }

    override fun eventProperty(property: String, value: Double) {
        // FIX (17 Sep 2026): MPVView.observeProperties() meng-observe "duration/full", bukan
        // "duration" polos -- nama sebelumnya tidak akan pernah match, currentDurationMs tidak
        // pernah ter-update. Dikonfirmasi dari MPVView.kt yang baru di-port, bukan asumsi.
        if (property != "duration/full") return
        mainHandler.post {
            currentDurationMs = (value * 1000.0).toLong()
            invalidateState()
        }
    }

    override fun event(eventId: Int) {
        if (eventId == MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN) {
            mainHandler.post {
                playbackEnded = true
                invalidateState()
                onShutdown()
            }
        }
    }
}
