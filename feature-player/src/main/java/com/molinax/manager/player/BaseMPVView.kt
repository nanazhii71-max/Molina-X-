package com.molinax.manager.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

// Port dari is.xyz.mpv.BaseMPVView (mpv-android, commit terbaru default branch) —
// hanya berisi kode esensial untuk menampilkan gambar ke layar. Konsisten dengan MPVLib.kt
// yang sudah di-port ke package com.molinax.manager.player.
//
// Perbedaan dari upstream:
// - package `is`.xyz.mpv -> com.molinax.manager.player (menyesuaikan MPVLib.kt yang sudah ada)
// - TAG log "mpv" -> "MolinaXPlayer" (konsisten dengan konvensi logging Molina-X lainnya,
//   lihat core-common/logging/AppLogger.kt)
// - `attrs` dibuat nullable (upstream selalu inflate MPVView dari XML lewat ViewBinding
//   sehingga AttributeSet non-null selalu tersedia; Molina-X memakai Jetpack Compose
//   (AndroidView interop), yang memanggil constructor View secara programatik tanpa XML
//   inflation sama sekali -- AttributeSet non-null tidak pernah ada di jalur itu).
// - GAP KRITIS Phase 4 (audit 17 Sep 2026) diperbaiki di sini: `initialize()`/`destroy()`
//   upstream digabung jadi satu unit "lifecycle Activity" (create+init sekali di
//   MPVActivity.onCreate, destroy sekali di onDestroy) karena MPVActivity's View berumur
//   sepanjang hidup Activity. Molina-X TIDAK punya Activity sepanjang itu untuk Player --
//   NavigationHost (app/.../NavigationHost.kt) hanya menyimpan SATU entry back stack
//   (backStack.clear() + add() tiap ganti tab, VERIFIED dari source), jadi Composable
//   PlayerHost (dan AndroidView MPVView di dalamnya) DIBONGKAR TOTAL setiap pindah tab --
//   memanggil initialize()/destroy() versi upstream apa adanya di situ akan
//   menghidupkan+mematikan context native mpv setiap pindah tab, membunuh audio latar
//   belakang yang justru jadi alasan foreground service (blueprint §7) dibangun.
//   Fix: pisahkan "core native context" (MPVLib.create/init/destroy, sekali per sesi
//   pemutaran, dimiliki PlayerPlaybackService -- lihat komentar di file itu) dari
//   "attach/detach Surface" (holder.addCallback/removeCallback, per instance View, boleh
//   dipanggil ulang tiap MPVView baru dibuat Compose). initialize()/destroy() instance di
//   bawah sekarang HANYA mengurus bagian kedua; bagian pertama pindah ke
//   ensureCoreInitialized()/destroyCore() (companion, idempotent, tidak butuh instance View).

abstract class BaseMPVView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    /**
     * Attach View ini ke Surface (SurfaceHolder callback) dan pastikan core native mpv aktif
     * (idempotent lewat ensureCoreInitialized -- aman dipanggil walau core sudah diinisialisasi
     * lebih dulu oleh PlayerPlaybackService, dan aman dipanggil ulang tiap instance MPVView baru
     * dibuat Compose saat user membuka kembali tab Player).
     *
     * Call ini setiap kali View baru akan ditampilkan (Compose AndroidView factory).
     */
    fun initialize(configDir: String, cacheDir: String) {
        ensureCoreInitialized(
            context = context,
            configDir = configDir,
            cacheDir = cacheDir,
            applyOptions = ::initOptions,
            applyPostOptions = ::postInitOptions,
            observe = ::observeProperties,
        )
        holder.addCallback(this)
    }

    /**
     * Detach View ini dari Surface. TIDAK mematikan core native mpv -- core dimiliki
     * PlayerPlaybackService (lihat file itu) supaya pemutaran tetap jalan di background
     * walau View/Composable ini dibongkar (pindah tab). Panggil destroyCore() secara
     * eksplisit dari Service saat pemutaran benar-benar berakhir (MPV_EVENT_SHUTDOWN) atau
     * saat proses app berakhir.
     *
     * Call ini setiap kali View ini akan dibongkar (Compose AndroidView onDispose/onRelease).
     */
    fun destroy() {
        // Disable surface callbacks to avoid using uninitialized mpv state
        holder.removeCallback(this)
    }

    // internal (bukan protected): PlayerPlaybackService (pemilik core, lihat komentar di atas)
    // perlu memanggil ketiga fungsi ini lewat instance MPVView headless untuk reuse opsi yang
    // sama persis dengan View asli, tanpa duplikasi logic -- protected tidak cukup karena
    // Service bukan subclass BaseMPVView/MPVView, cuma sama-sama tinggal di modul feature-player.
    internal abstract fun initOptions()
    internal abstract fun postInitOptions()

    internal abstract fun observeProperties()

    private var filePath: String? = null

    /**
     * Set the first file to be played once the player is ready.
     */
    fun playFile(filePath: String) {
        this.filePath = filePath
    }

    private var voInUse: String = "gpu"

    /**
     * Sets the VO to use.
     * It is automatically disabled/enabled when the surface dis-/appears.
     */
    fun setVo(vo: String) {
        voInUse = vo
        MPVLib.setOptionString("vo", vo)
    }

    // Surface callbacks

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.w(TAG, "attaching surface")
        MPVLib.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        MPVLib.setOptionString("force-window", "yes")

        if (filePath != null) {
            MPVLib.command(arrayOf("loadfile", filePath as String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            MPVLib.setPropertyString("vo", voInUse)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.w(TAG, "detaching surface")
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        // Note that before calling detachSurface() we need to be sure that libmpv
        // is done using the surface.
        // FIXME: There could be a race condition here, because I don't think
        // setting a property will wait for VO deinit.
        MPVLib.detachSurface()
    }

    companion object {
        private const val TAG = "MolinaXPlayer"

        @Volatile
        private var coreReady = false
        private val coreLock = Any()

        /** True jika MPVLib.create()+init() sudah aktif -- independen dari View mana pun. */
        val isCoreReady: Boolean get() = coreReady

        /**
         * Inisialisasi context native mpv SEKALI (idempotent, thread-safe), independen dari
         * instance View mana pun. Dipanggil dari PlayerPlaybackService.onCreate() (pemilik utama,
         * lihat komentar arsitektur di file itu) dan dari BaseMPVView.initialize() (guard kedua,
         * berjaga-jaga kalau View sempat siap lebih dulu daripada Service -- urutan siapa duluan
         * tidak masalah karena idempotent).
         */
        fun ensureCoreInitialized(
            context: Context,
            configDir: String,
            cacheDir: String,
            applyOptions: () -> Unit,
            applyPostOptions: () -> Unit,
            observe: () -> Unit,
        ) {
            if (coreReady) return
            synchronized(coreLock) {
                if (coreReady) return
                Log.i(TAG, "ensureCoreInitialized: membuat context native mpv")
                MPVLib.create(context.applicationContext)

                /* set normal options (user-supplied config can override) */
                MPVLib.setOptionString("config", "yes")
                MPVLib.setOptionString("config-dir", configDir)
                for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
                    MPVLib.setOptionString(opt, cacheDir)
                applyOptions()

                MPVLib.init()

                /* set hardcoded options */
                applyPostOptions()
                // could mess up VO init before surfaceCreated() is called
                MPVLib.setOptionString("force-window", "no")
                // need to idle at least once for playFile() logic to work
                MPVLib.setOptionString("idle", "once")

                observe()
                coreReady = true
            }
        }

        /**
         * Matikan context native mpv. Panggil HANYA saat pemutaran benar-benar berakhir
         * (MPV_EVENT_SHUTDOWN diobservasi PlayerPlaybackService) atau saat proses app berakhir --
         * BUKAN dari BaseMPVView.destroy() (lihat komentar di sana).
         */
        fun destroyCore() {
            if (!coreReady) return
            synchronized(coreLock) {
                if (!coreReady) return
                Log.i(TAG, "destroyCore: mematikan context native mpv")
                MPVLib.destroy()
                coreReady = false
            }
        }
    }
}
