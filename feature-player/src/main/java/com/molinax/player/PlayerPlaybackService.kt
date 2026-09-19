package com.molinax.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground service Player (blueprint §7: wajib, bukan opsional). Menggantikan pola
 * androidx.media legacy (MediaSessionCompat+NotificationCompat.MediaStyle) yang disebut
 * literal di blueprint -- library itu resmi di-deprecate per rilis 1.8.0 (androidx.media),
 * keputusan eksplisit pengguna: migrasi ke Media3 (MediaSessionService+MediaSession).
 *
 * Notification otomatis di-generate Media3 dari state MolinaXMpvPlayer (DefaultMediaNotificationProvider
 * bawaan MediaSessionService) -- tidak dibangun manual seperti BackgroundPlaybackService.kt upstream.
 *
 * KEPUTUSAN ARSITEKTUR LIFECYCLE mpv (17 Sep 2026, "ikuti pola mpv-android" -- audit gap Phase 4):
 * Upstream (MPVActivity) memegang context native mpv (MPVLib.create()+init() di onCreate,
 * MPVLib.destroy() di onDestroy) karena View-nya (MPVView, embedded lewat XML) berumur
 * sepanjang hidup Activity -- backgrounding (home button) cuma onPause, BUKAN onDestroy, jadi
 * View+context tetap hidup, BackgroundPlaybackService cuma numpang notification+observer, TIDAK
 * pernah membuat context sendiri (VERIFIED dari source upstream, 0 pemanggilan MPVLib.create/init
 * di BackgroundPlaybackService.kt).
 *
 * Molina-X tidak punya Activity seumur itu untuk Player -- NavigationHost (app/.../
 * NavigationHost.kt) cuma menyimpan SATU entry back stack, jadi Composable PlayerHost (dan
 * AndroidView MPVView di dalamnya) DIBONGKAR TOTAL tiap pindah tab, bukan cuma "onPause". Supaya
 * audio tetap jalan di background persis seperti upstream (itulah tujuan foreground service ini
 * dibangun), context native mpv PINDAH kepemilikan ke Service ini -- satu-satunya komponen
 * Molina-X yang benar-benar seumur sesi pemutaran (bukan seumur composition Compose). PlayerHost
 * (View/Surface) tetap boleh dibuat-ulang bebas tiap kali user buka tab Player lagi; itu memang
 * sudah didesain aman lewat surfaceCreated()/surfaceDestroyed() upstream (cuma attach/detach VO,
 * tidak pernah menyentuh context) -- lihat BaseMPVView.kt.
 *
 * Konsekuensi: onCreate() di sini SEKARANG memanggil BaseMPVView.ensureCoreInitialized() SEBELUM
 * membuat MolinaXMpvPlayer (bukan lagi mengasumsikan sudah diinisialisasi View, lihat riwayat
 * commit 54eeffa). Dipakai instance MPVView headless (context, attrs=null) MURNI untuk reuse
 * initOptions()/postInitOptions()/observeProperties() -- satu sumber kebenaran opsi mpv yang
 * sama persis dipakai View asli di PlayerHost, instance ini TIDAK PERNAH ditambah ke window
 * (tidak pernah menerima surface, holder.addCallback() tidak pernah dipanggil untuknya).
 *
 * onTaskRemoved() SENGAJA TIDAK di-override -- perilaku default MediaSessionService (VERIFIED
 * developer.android.com/media/media3/session/background-playback, bagian "Implement the service
 * lifecycle"): "By default, the service is left running if playback is ongoing, and is stopped
 * otherwise" -- ini SUDAH persis semantik shouldBackground() upstream tanpa perlu implementasi
 * manual (bukan gap, keputusan sadar untuk tidak overengineer ulang apa yang library sudah
 * sediakan).
 */
class PlayerPlaybackService : MediaSessionService() {

    private lateinit var player: MolinaXMpvPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()

        val optionsHost = MPVView(applicationContext)
        BaseMPVView.ensureCoreInitialized(
            context = applicationContext,
            configDir = filesDir.path,
            cacheDir = cacheDir.path,
            applyOptions = optionsHost::initOptions,
            applyPostOptions = optionsHost::postInitOptions,
            observe = optionsHost::observeProperties,
        )

        player = MolinaXMpvPlayer(
            applicationLooper = Looper.getMainLooper(),
            onShutdown = {
                // Persis pola upstream BackgroundPlaybackService.event() -> stopSelf() saat
                // MPV_EVENT_SHUTDOWN. Core baru benar-benar dimatikan di onDestroy() (dipanggil
                // sistem setelah stopSelf(), lihat siklus hidup Service resmi), bukan di sini
                // langsung -- supaya onDestroy() tetap satu-satunya tempat pelepasan resource.
                stopSelf()
            },
        )

        // feature-player tidak boleh depend ke modul app (arah dependency Molina-X selalu
        // App Host -> feature module, blueprint §5), jadi activity utama app dibuka lewat
        // launch intent package, bukan referensi langsung ke class MainActivity.
        val sessionActivityPendingIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT }
            ?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val builder = MediaSession.Builder(this, player)
        if (sessionActivityPendingIntent != null) {
            builder.setSessionActivity(sessionActivityPendingIntent)
        }
        mediaSession = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        player.release()
        mediaSession.release()
        BaseMPVView.destroyCore()
        super.onDestroy()
    }
}
