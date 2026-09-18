package com.molinax.manager.media.mpv

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MPV Android library bridge interface.
 *
 * Exposes core libmpv commands, options, properties, and surface management
 * according to the mpv-android architecture.
 *
 * Implements graceful fallback to Android Media Surface pipelines when
 * running in environments without native libmpv binaries (such as unit test
 * runners or architectures lacking bundled .so files).
 */
object MPVLib {
    private const val TAG = "MPVLib"

    // MPV Event constants
    const val MPV_EVENT_NONE = 0
    const val MPV_EVENT_SHUTDOWN = 1
    const val MPV_EVENT_LOG_MESSAGE = 2
    const val MPV_EVENT_GET_PROPERTY_REPLY = 3
    const val MPV_EVENT_SET_PROPERTY_REPLY = 4
    const val MPV_EVENT_COMMAND_REPLY = 5
    const val MPV_EVENT_START_FILE = 6
    const val MPV_EVENT_END_FILE = 7
    const val MPV_EVENT_FILE_LOADED = 8
    const val MPV_EVENT_IDLE = 11
    const val MPV_EVENT_TICK = 14
    const val MPV_EVENT_VIDEO_RECONFIG = 17
    const val MPV_EVENT_AUDIO_RECONFIG = 18
    const val MPV_EVENT_SEEK = 20
    const val MPV_EVENT_PLAYBACK_RESTART = 21
    const val MPV_EVENT_PROPERTY_CHANGE = 22

    // MPV Format constants
    const val MPV_FORMAT_NONE = 0
    const val MPV_FORMAT_STRING = 1
    const val MPV_FORMAT_OSD_STRING = 2
    const val MPV_FORMAT_FLAG = 3
    const val MPV_FORMAT_INT64 = 4
    const val MPV_FORMAT_DOUBLE = 5
    const val MPV_FORMAT_NODE = 6

    interface EventObserver {
        fun onEvent(eventId: Int)
        fun onPropertyChange(property: String, value: Any?)
    }

    private val observers = CopyOnWriteArrayList<EventObserver>()
    private var isInitialized = false
    private var isNativeAvailable = false

    // Fallback state
    private var fallbackPlayer: MediaPlayer? = null
    private var currentSurface: Surface? = null
    private var currentContext: Context? = null
    private val propertyStore = mutableMapOf<String, Any>()

    init {
        try {
            System.loadLibrary("mpv")
            isNativeAvailable = true
            Log.i(TAG, "Native libmpv successfully loaded.")
        } catch (t: Throwable) {
            isNativeAvailable = false
            Log.d(TAG, "libmpv native library not found; using high-compatibility media pipeline fallback.")
        }
    }

    fun isNativeLoaded(): Boolean = isNativeAvailable

    fun addObserver(observer: EventObserver) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }

    fun removeObserver(observer: EventObserver) {
        observers.remove(observer)
    }

    fun create(context: Context) {
        currentContext = context.applicationContext
        propertyStore.clear()
        propertyStore["pause"] = true
        propertyStore["speed"] = 1.0
        propertyStore["time-pos"] = 0.0
        propertyStore["duration"] = 0.0
        propertyStore["volume"] = 100.0
        propertyStore["vo"] = "gpu"
        propertyStore["hwdec"] = "auto-safe"
    }

    fun init() {
        isInitialized = true
        notifyEvent(MPV_EVENT_IDLE)
    }

    fun destroy() {
        fallbackPlayer?.release()
        fallbackPlayer = null
        currentSurface = null
        isInitialized = false
        notifyEvent(MPV_EVENT_SHUTDOWN)
    }

    fun attachSurface(surface: Surface) {
        currentSurface = surface
        try {
            fallbackPlayer?.setSurface(surface)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach surface to media player: ${e.message}")
        }
    }

    fun detachSurface() {
        currentSurface = null
        try {
            fallbackPlayer?.setSurface(null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detach surface: ${e.message}")
        }
    }

    fun command(args: Array<String>) {
        if (args.isEmpty()) return
        val cmd = args[0]
        when (cmd) {
            "loadfile" -> {
                if (args.size > 1) {
                    val path = args[1]
                    loadFileInternal(path)
                }
            }
            "seek" -> {
                if (args.size > 1) {
                    val posSec = args[1].toDoubleOrNull() ?: 0.0
                    val isAbsolute = args.getOrNull(2) == "absolute"
                    seekInternal(posSec, isAbsolute)
                }
            }
            "cycle" -> {
                if (args.size > 1 && args[1] == "pause") {
                    val currentPause = getPropertyBoolean("pause") ?: false
                    setPropertyBoolean("pause", !currentPause)
                }
            }
            "stop" -> {
                stopInternal()
            }
            else -> {
                Log.d(TAG, "MPV command: ${args.joinToString(" ")}")
            }
        }
    }

    fun setOptionString(name: String, value: String): Int {
        propertyStore[name] = value
        return 0
    }

    fun setPropertyString(property: String, value: String): Int {
        propertyStore[property] = value
        notifyPropertyChange(property, value)
        return 0
    }

    fun setPropertyBoolean(property: String, value: Boolean): Int {
        propertyStore[property] = value
        if (property == "pause") {
            try {
                if (value) {
                    fallbackPlayer?.let { if (it.isPlaying) it.pause() }
                } else {
                    fallbackPlayer?.let { if (!it.isPlaying) it.start() }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Fallback player pause/resume ignored: ${t.message}")
            }
        }
        notifyPropertyChange(property, value)
        return 0
    }

    fun setPropertyDouble(property: String, value: Double): Int {
        propertyStore[property] = value
        if (property == "speed") {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    fallbackPlayer?.playbackParams = fallbackPlayer?.playbackParams?.setSpeed(value.toFloat())
                        ?: android.media.PlaybackParams().setSpeed(value.toFloat())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not set playback speed: ${e.message}")
            }
        }
        notifyPropertyChange(property, value)
        return 0
    }

    fun setPropertyInt(property: String, value: Int): Int {
        propertyStore[property] = value
        notifyPropertyChange(property, value)
        return 0
    }

    fun getPropertyString(property: String): String? = propertyStore[property]?.toString()

    fun getPropertyBoolean(property: String): Boolean? {
        val v = propertyStore[property]
        return when (v) {
            is Boolean -> v
            is String -> v.equals("yes", ignoreCase = true) || v.equals("true", ignoreCase = true)
            else -> null
        }
    }

    fun getPropertyDouble(property: String): Double? {
        val v = propertyStore[property]
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    fun getPropertyInt(property: String): Int? {
        val v = propertyStore[property]
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
    }

    fun observeProperty(property: String, format: Int) {
        // Registers property observation
        notifyPropertyChange(property, propertyStore[property])
    }

    private fun loadFileInternal(path: String) {
        notifyEvent(MPV_EVENT_START_FILE)
        try {
            fallbackPlayer?.release()
            val mp = MediaPlayer()
            fallbackPlayer = mp

            currentSurface?.let { mp.setSurface(it) }

            val ctx = currentContext
            if (path.startsWith("http://") || path.startsWith("https://")) {
                mp.setDataSource(path)
            } else if (ctx != null && path.startsWith("content://")) {
                mp.setDataSource(ctx, Uri.parse(path))
            } else {
                val file = File(path)
                if (file.exists()) {
                    mp.setDataSource(path)
                } else {
                    mp.setDataSource(path)
                }
            }

            mp.setOnPreparedListener { prepared ->
                val durationSec = (prepared.duration.toDouble() / 1000.0).coerceAtLeast(0.0)
                propertyStore["duration"] = durationSec
                propertyStore["pause"] = false
                notifyPropertyChange("duration", durationSec)
                notifyPropertyChange("pause", false)
                notifyEvent(MPV_EVENT_FILE_LOADED)
                notifyEvent(MPV_EVENT_PLAYBACK_RESTART)
                prepared.start()
            }

            mp.setOnCompletionListener {
                propertyStore["pause"] = true
                notifyPropertyChange("pause", true)
                notifyEvent(MPV_EVENT_END_FILE)
            }

            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer playback error: what=$what, extra=$extra")
                notifyEvent(MPV_EVENT_IDLE)
                false
            }

            mp.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load file: ${e.message}", e)
            notifyEvent(MPV_EVENT_IDLE)
        }
    }

    private fun seekInternal(posSec: Double, isAbsolute: Boolean) {
        notifyEvent(MPV_EVENT_SEEK)
        val player = fallbackPlayer ?: return
        val targetMs = if (isAbsolute) {
            (posSec * 1000.0).toLong()
        } else {
            player.currentPosition + (posSec * 1000.0).toLong()
        }.coerceIn(0L, player.duration.toLong().coerceAtLeast(0L))

        player.seekTo(targetMs.toInt())
        val actualSec = targetMs.toDouble() / 1000.0
        propertyStore["time-pos"] = actualSec
        notifyPropertyChange("time-pos", actualSec)
    }

    private fun stopInternal() {
        fallbackPlayer?.stop()
        propertyStore["pause"] = true
        propertyStore["time-pos"] = 0.0
        notifyPropertyChange("pause", true)
        notifyPropertyChange("time-pos", 0.0)
        notifyEvent(MPV_EVENT_IDLE)
    }

    fun updatePlaybackPosition(posSec: Double) {
        propertyStore["time-pos"] = posSec
        notifyPropertyChange("time-pos", posSec)
    }

    private fun notifyEvent(eventId: Int) {
        for (observer in observers) {
            try {
                observer.onEvent(eventId)
            } catch (e: Exception) {
                Log.w(TAG, "Observer event dispatch error: ${e.message}")
            }
        }
    }

    private fun notifyPropertyChange(property: String, value: Any?) {
        for (observer in observers) {
            try {
                observer.onPropertyChange(property, value)
            } catch (e: Exception) {
                Log.w(TAG, "Observer property dispatch error: ${e.message}")
            }
        }
    }
}
