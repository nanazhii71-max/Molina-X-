package com.molinax.media.mpv

import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.molinax.player.AspectRatioMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Reactive playback state model for MPV Player.
 */
data class MPVPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volumeBoostDb: Int = 0,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val currentMediaTitle: String? = null,
    val currentMediaSource: String? = null,
    val isSurfaceReady: Boolean = false,
    val error: String? = null
)

/**
 * Production-ready MPV Player engine managing the libmpv lifecycle,
 * video output surface attachment, hardware acceleration, audio boost,
 * and reactive UI state dispatch.
 */
class MPVPlayerEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : MPVLib.EventObserver {

    private val tag = "MPVPlayerEngine"

    private val _playbackState = MutableStateFlow(MPVPlaybackState())
    val playbackState: StateFlow<MPVPlaybackState> = _playbackState.asStateFlow()

    private var activeSurfaceHolder: SurfaceHolder? = null
    private var progressTickerJob: Job? = null

    init {
        initializeMpv()
    }

    private fun initializeMpv() {
        Log.i(tag, "Initializing MPV Media Subsystem...")
        MPVLib.create(context)

        // Configure optimal mobile options (GPU video output, hardware decoding, audio)
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("hwdec", "auto-safe")
        MPVLib.setOptionString("ao", "audiotrack")
        MPVLib.setOptionString("ytdl", "yes")
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setOptionString("demuxer-max-bytes", "64MiB")

        // Register observer for core MPV playback properties
        MPVLib.addObserver(this)
        MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("speed", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("video-aspect-override", MPVLib.MPV_FORMAT_STRING)

        MPVLib.init()
    }

    /**
     * Attaches the video rendering surface from a SurfaceHolder.
     */
    fun attachSurface(holder: SurfaceHolder) {
        activeSurfaceHolder = holder
        val surface = holder.surface
        if (surface != null && surface.isValid) {
            MPVLib.attachSurface(surface)
            _playbackState.value = _playbackState.value.copy(isSurfaceReady = true)
            Log.d(tag, "Attached Surface to MPV engine: $surface")
        }
    }

    /**
     * Attaches a raw Android Surface directly.
     */
    fun attachSurface(surface: Surface) {
        if (surface.isValid) {
            MPVLib.attachSurface(surface)
            _playbackState.value = _playbackState.value.copy(isSurfaceReady = true)
            Log.d(tag, "Attached raw Surface to MPV engine: $surface")
        }
    }

    /**
     * Detaches the video rendering surface when views are destroyed or hidden.
     */
    fun detachSurface() {
        activeSurfaceHolder = null
        MPVLib.detachSurface()
        _playbackState.value = _playbackState.value.copy(isSurfaceReady = false)
        Log.d(tag, "Detached Surface from MPV engine")
    }

    /**
     * Loads and plays a local file or streaming URL.
     */
    fun loadMedia(source: String, title: String? = null, startPositionMs: Long = 0L) {
        val displayTitle = title ?: File(source).nameWithoutExtension
        _playbackState.value = _playbackState.value.copy(
            currentMediaSource = source,
            currentMediaTitle = displayTitle,
            isBuffering = true,
            currentPositionMs = startPositionMs
        )

        Log.i(tag, "Loading media in MPV: $source (title: $displayTitle)")
        MPVLib.command(arrayOf("loadfile", source, "replace"))

        if (startPositionMs > 0) {
            val startSec = startPositionMs.toDouble() / 1000.0
            MPVLib.command(arrayOf("seek", startSec.toString(), "absolute"))
        }

        startProgressTicker()
    }

    fun play() {
        MPVLib.setPropertyBoolean("pause", false)
        _playbackState.value = _playbackState.value.copy(isPlaying = true)
        startProgressTicker()
    }

    fun pause() {
        MPVLib.setPropertyBoolean("pause", true)
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        stopProgressTicker()
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        val durationMs = _playbackState.value.durationMs.coerceAtLeast(1L)
        val clampedPos = positionMs.coerceIn(0L, durationMs)
        val posSec = clampedPos.toDouble() / 1000.0
        MPVLib.command(arrayOf("seek", posSec.toString(), "absolute"))
        _playbackState.value = _playbackState.value.copy(currentPositionMs = clampedPos)
    }

    fun seekRelative(seconds: Int) {
        MPVLib.command(arrayOf("seek", seconds.toString(), "relative"))
        val newPos = (_playbackState.value.currentPositionMs + (seconds * 1000L))
            .coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(0L))
        _playbackState.value = _playbackState.value.copy(currentPositionMs = newPos)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4.0f)
        MPVLib.setPropertyDouble("speed", clamped.toDouble())
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clamped)
    }

    fun setVolumeBoost(boostDb: Int) {
        val clamped = boostDb.coerceIn(0, 18)
        _playbackState.value = _playbackState.value.copy(volumeBoostDb = clamped)

        if (clamped > 0) {
            // In MPV, audio filters apply loudness equalization via af command
            MPVLib.command(arrayOf("af", "set", "volume=volume=+${clamped}dB:precision=fixed"))
        } else {
            MPVLib.command(arrayOf("af", "clr", ""))
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _playbackState.value = _playbackState.value.copy(aspectRatio = mode)
        val mpvRatio = when (mode) {
            AspectRatioMode.FIT -> "-1"
            AspectRatioMode.FILL -> "0"
            AspectRatioMode.SIXTEEN_NINE -> "16:9"
            AspectRatioMode.FOUR_THREE -> "4:3"
        }
        MPVLib.setPropertyString("video-aspect-override", mpvRatio)
    }

    fun stop() {
        MPVLib.command(arrayOf("stop"))
        stopProgressTicker()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            currentPositionMs = 0L,
            isBuffering = false
        )
    }

    fun release() {
        stopProgressTicker()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
        scope.cancel()
    }

    // MPV Event Callbacks
    override fun onEvent(eventId: Int) {
        when (eventId) {
            MPVLib.MPV_EVENT_START_FILE -> {
                _playbackState.value = _playbackState.value.copy(isBuffering = true)
            }
            MPVLib.MPV_EVENT_FILE_LOADED -> {
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = false,
                    isPlaying = true
                )
            }
            MPVLib.MPV_EVENT_END_FILE -> {
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isBuffering = false
                )
            }
            MPVLib.MPV_EVENT_IDLE -> {
                _playbackState.value = _playbackState.value.copy(
                    isBuffering = false
                )
            }
        }
    }

    override fun onPropertyChange(property: String, value: Any?) {
        when (property) {
            "time-pos" -> {
                val sec = (value as? Number)?.toDouble() ?: return
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = (sec * 1000.0).toLong()
                )
            }
            "duration" -> {
                val sec = (value as? Number)?.toDouble() ?: return
                _playbackState.value = _playbackState.value.copy(
                    durationMs = (sec * 1000.0).toLong()
                )
            }
            "pause" -> {
                val isPaused = (value as? Boolean) ?: false
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = !isPaused
                )
            }
            "speed" -> {
                val speed = (value as? Number)?.toFloat() ?: 1.0f
                _playbackState.value = _playbackState.value.copy(
                    playbackSpeed = speed
                )
            }
        }
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive) {
                if (_playbackState.value.isPlaying) {
                    val posSec = MPVLib.getPropertyDouble("time-pos")
                    val durSec = MPVLib.getPropertyDouble("duration")
                    if (posSec != null) {
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = (posSec * 1000.0).toLong()
                        )
                    }
                    if (durSec != null && durSec > 0) {
                        _playbackState.value = _playbackState.value.copy(
                            durationMs = (durSec * 1000.0).toLong()
                        )
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
    }
}
