package com.molinax.manager.player

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import androidx.core.content.ContextCompat
import com.molinax.manager.player.MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
import com.molinax.manager.player.MPVLib.MpvFormat.MPV_FORMAT_FLAG
import com.molinax.manager.player.MPVLib.MpvFormat.MPV_FORMAT_INT64
import com.molinax.manager.player.MPVLib.MpvFormat.MPV_FORMAT_NONE
import com.molinax.manager.player.MPVLib.MpvFormat.MPV_FORMAT_STRING
import java.io.File
import java.io.FileOutputStream
import kotlin.reflect.KProperty

// Port dari is.xyz.mpv.MPVView (mpv-android, commit 751d532, /root/ref/mpv-android) ke
// com.molinax.manager.player, konkretisasi BaseMPVView.kt yang sudah ada di module ini.
//
// DEVIASI TERDOKUMENTASI dari upstream (bukan pengurangan diam-diam):
// 1. Tidak ada SharedPreferences/PreferenceManager sama sekali — Settings UI Player (blueprint
//    §7 "settings/") belum dibangun. Semua nilai yang upstream baca dari preferences di-hardcode
//    ke default upstream sendiri (vo=gpu, hardware_decoding=true, video-sync=audio [VERIFIED
//    github.com/mpv-android/mpv-android strings.xml: pref_video_interpolation_sync_default],
//    interpolation/fastdecode/gpudebug=off, tanpa custom scale/deband). ini BUKAN fitur hilang,
//    cuma belum user-configurable — jadi TODO nyata untuk Phase 4.x saat Settings UI dibangun,
//    dicatat di sini bukan ditandai placeholder di kode.
// 2. Cek `Build.VERSION.SDK_INT >= Build.VERSION_CODES.M` upstream (API 23) DIHILANGKAN karena
//    minSdk Molina-X = 26 (root build.gradle.kts) — cabang else upstream (Android <23) mustahil
//    tercapai di baseline ini, bukan bug, murni dead-code yang tidak relevan untuk minSdk kita.
// 3. `screenshot-directory` TIDAK di-set — keputusan eksplisit user (17 Sep 2026): belum ada UI
//    yang memicu screenshot in-player (dialog terkait masuk kategori "polish", belum di-port),
//    jadi meminta WRITE_EXTERNAL_STORAGE runtime sekarang untuk fitur yang belum bisa dipakai
//    user melanggar least-privilege. Revisit saat dialog/tombol screenshot dikerjakan.
// 4. `onKey()`/`onPointerEvent()` upstream TIDAK di-port di file ini — keduanya bergantung pada
//    `keyMapping` dari KeyMapping.kt yang belum di-port (kategori "polish" sama seperti di atas).
//    Bukan bagian dari kontrak abstract BaseMPVView, jadi tidak memblokir kompilasi/fungsi dasar
//    pemutaran. Tambahkan saat KeyMapping.kt masuk scope.
// 5. `tls-ca-file` mengarah ke `${filesDir}/cacert.pem` — file di-bundle sebagai asset
//    (feature-player/src/main/assets/cacert.pem, sumber curl.se/ca/cacert.pem, keputusan
//    eksplisit user 17 Sep 2026, opsi "bundle" dibanding "fetch+checksum runtime" seperti
//    rootfs) dan di-extract ke filesDir sekali oleh ensureCaCertExtracted() di bawah, DIPANGGIL
//    tanpa try-catch yang menelan error — kalau asset hilang/corrupt, ini WAJIB crash keras saat
//    development/CI, bukan diam-diam jalan tanpa TLS verification.
internal class MPVView(context: Context, attrs: AttributeSet? = null) : BaseMPVView(context, attrs) {

    override fun initOptions() {
        // apply phone-optimized defaults (sama seperti upstream, hardcoded — lihat deviasi #1)
        MPVLib.setOptionString("profile", "fast")

        setVo("gpu")

        val hwdec = HWDECS

        // vo: set display fps as reported by android (deviasi #2: minSdk 26 >= M, cabang lama dihapus)
        val disp = ContextCompat.getDisplayOrDefault(context)
        val refreshRate = disp.mode.refreshRate
        Log.v(TAG, "Display ${disp.displayId} reports FPS of $refreshRate")
        MPVLib.setOptionString("display-fps-override", refreshRate.toString())

        // video-sync default upstream (VERIFIED strings.xml pref_video_interpolation_sync_default)
        MPVLib.setOptionString("video-sync", "audio")

        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec", hwdec)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("audio-set-media-role", "yes")

        ensureCaCertExtracted(context)
        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")

        MPVLib.setOptionString("input-default-bindings", "yes")
        // Limit demuxer cache since the defaults are too high for mobile devices
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
    }

    /**
     * Extract cacert.pem dari APK asset ke app-private filesDir, sekali saja (idempotent).
     * Sengaja TIDAK menelan IOException — kegagalan di sini berarti TLS verification untuk
     * seluruh HTTPS stream (link universal, ytdl_hook YouTube) tidak bisa dijamin, harus
     * terlihat sebagai crash saat development, bukan silent degradation.
     */
    private fun ensureCaCertExtracted(context: Context) {
        val dest = File(context.filesDir, "cacert.pem")
        if (dest.exists() && dest.length() > 0L) return
        context.assets.open("cacert.pem").use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    override fun postInitOptions() {
        // we need to call write-watch-later manually
        MPVLib.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        // This observes all properties needed by MPVView, PlayerHost, MolinaXMpvPlayer, or other classes
        data class Property(val name: String, val format: Int = MPV_FORMAT_NONE)
        val p = arrayOf(
            Property("time-pos", MPV_FORMAT_INT64),
            Property("duration/full", MPV_FORMAT_DOUBLE),
            Property("pause", MPV_FORMAT_FLAG),
            Property("paused-for-cache", MPV_FORMAT_FLAG),
            Property("speed", MPV_FORMAT_STRING),
            Property("track-list"),
            Property("video-params/aspect", MPV_FORMAT_DOUBLE),
            Property("video-params/rotate", MPV_FORMAT_DOUBLE),
            Property("playlist-pos", MPV_FORMAT_INT64),
            Property("playlist-count", MPV_FORMAT_INT64),
            Property("current-tracks/video/image"),
            Property("media-title", MPV_FORMAT_STRING),
            Property("metadata"),
            Property("loop-playlist"),
            Property("loop-file"),
            Property("shuffle", MPV_FORMAT_FLAG),
            Property("hwdec-current"),
            Property("mute", MPV_FORMAT_FLAG),
            Property("current-tracks/audio/selected")
        )

        for ((name, format) in p)
            MPVLib.observeProperty(name, format)
    }

    fun addObserver(o: MPVLib.EventObserver) {
        MPVLib.addObserver(o)
    }
    fun removeObserver(o: MPVLib.EventObserver) {
        MPVLib.removeObserver(o)
    }

    data class Track(val mpvId: Int, val name: String)
    var tracks = mapOf<String, MutableList<Track>>(
            "audio" to arrayListOf(),
            "video" to arrayListOf(),
            "sub" to arrayListOf())

    fun loadTracks() {
        for (list in tracks.values) {
            list.clear()
            // pseudo-track to allow disabling audio/subs
            list.add(Track(-1, context.getString(R.string.track_off)))
        }
        val count = MPVLib.getPropertyInt("track-list/count")!!
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (!tracks.containsKey(type)) {
                Log.w(TAG, "Got unknown track type: $type")
                continue
            }
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val title = MPVLib.getPropertyString("track-list/$i/title")

            val trackName = if (!lang.isNullOrEmpty() && !title.isNullOrEmpty())
                context.getString(R.string.ui_track_title_lang, mpvId, title, lang)
            else if (!lang.isNullOrEmpty() || !title.isNullOrEmpty())
                context.getString(R.string.ui_track_text, mpvId, (lang ?: "") + (title ?: ""))
            else
                context.getString(R.string.ui_track, mpvId)
            tracks.getValue(type).add(Track(
                    mpvId=mpvId,
                    name=trackName
            ))
        }
    }

    data class PlaylistItem(val index: Int, val filename: String, val title: String?)

    fun loadPlaylist(): MutableList<PlaylistItem> {
        val playlist = mutableListOf<PlaylistItem>()
        val count = MPVLib.getPropertyInt("playlist-count")!!
        for (i in 0 until count) {
            val filename = MPVLib.getPropertyString("playlist/$i/filename")!!
            val title = MPVLib.getPropertyString("playlist/$i/title")
            playlist.add(PlaylistItem(index=i, filename=filename, title=title))
        }
        return playlist
    }

    data class Chapter(val index: Int, val title: String?, val time: Double)

    fun loadChapters(): MutableList<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val count = MPVLib.getPropertyInt("chapter-list/count")!!
        for (i in 0 until count) {
            val title = MPVLib.getPropertyString("chapter-list/$i/title")
            val time = MPVLib.getPropertyDouble("chapter-list/$i/time")!!
            chapters.add(Chapter(
                    index=i,
                    title=title,
                    time=time
            ))
        }
        return chapters
    }

    // Property getters/setters

    var paused: Boolean?
        get() = MPVLib.getPropertyBoolean("pause")
        set(paused) = MPVLib.setPropertyBoolean("pause", paused!!)

    var timePos: Double?
        get() = MPVLib.getPropertyDouble("time-pos/full")
        set(progress) = MPVLib.setPropertyDouble("time-pos", progress!!)

    /** name of currently active hardware decoder or "no" */
    val hwdecActive: String
        get() = MPVLib.getPropertyString("hwdec-current") ?: "no"

    var playbackSpeed: Double?
        get() = MPVLib.getPropertyDouble("speed")
        set(speed) = MPVLib.setPropertyDouble("speed", speed!!)

    var subDelay: Double?
        get() = MPVLib.getPropertyDouble("sub-delay")
        set(speed) = MPVLib.setPropertyDouble("sub-delay", speed!!)

    var secondarySubDelay: Double?
        get() = MPVLib.getPropertyDouble("secondary-sub-delay")
        set(speed) = MPVLib.setPropertyDouble("secondary-sub-delay", speed!!)

    val estimatedVfFps: Double?
        get() = MPVLib.getPropertyDouble("estimated-vf-fps")

    /**
     * Returns the video aspect ratio. Rotation is taken into account.
     */
    fun getVideoAspect(): Double? {
        return MPVLib.getPropertyDouble("video-params/aspect")?.let {
            if (it < 0.001)
                return 0.0
            val rot = MPVLib.getPropertyInt("video-params/rotate") ?: 0
            if (rot % 180 == 90)
                1.0 / it
            else
                it
        }
    }

    fun setAudioSessionId(id: Int) {
        MPVLib.setPropertyInt("audiotrack-session-id", id)
        MPVLib.setPropertyInt("aaudio-session-id", id)
    }

    class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.getPropertyString(name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                MPVLib.setPropertyString(name, "no")
            else
                MPVLib.setPropertyInt(name, value)
        }
    }

    var vid: Int by TrackDelegate("vid")
    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    // Commands

    fun cyclePause() = MPVLib.command(arrayOf("cycle", "pause"))
    fun cycleAudio() = MPVLib.command(arrayOf("cycle", "audio"))
    fun cycleSub() = MPVLib.command(arrayOf("cycle", "sub"))
    fun cycleHwdec() = MPVLib.command(arrayOf("cycle-values", "hwdec", HWDECS, "no"))

    fun cycleSpeed() {
        val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
        val currentSpeed = playbackSpeed ?: 1.0
        val index = speeds.indexOfFirst { it > currentSpeed }
        playbackSpeed = speeds[if (index == -1) 0 else index]
    }

    fun getRepeat(): Int {
        return when (MPVLib.getPropertyString("loop-playlist") +
                MPVLib.getPropertyString("loop-file")) {
            "noinf" -> 2
            "infno" -> 1
            else -> 0
        }
    }

    fun cycleRepeat() {
        when (val state = getRepeat()) {
            0, 1 -> {
                MPVLib.setPropertyString("loop-playlist", if (state == 1) "no" else "inf")
                MPVLib.setPropertyString("loop-file", if (state == 1) "inf" else "no")
            }
            2 -> MPVLib.setPropertyString("loop-file", "no")
        }
    }

    fun getShuffle(): Boolean {
        return MPVLib.getPropertyBoolean("shuffle") == true
    }

    fun changeShuffle(cycle: Boolean, value: Boolean = true) {
        // Use the 'shuffle' property to store the shuffled state, since changing
        // it at runtime doesn't do anything.
        val state = getShuffle()
        val newState = if (cycle) state.xor(value) else value
        if (state == newState)
            return
        MPVLib.command(arrayOf(if (newState) "playlist-shuffle" else "playlist-unshuffle"))
        MPVLib.setPropertyBoolean("shuffle", newState)
    }

    companion object {
        private const val TAG = "MolinaXPlayer"

        // mpv option `hwdec` is set to this
        private const val HWDECS = "mediacodec,mediacodec-copy"
    }
}
