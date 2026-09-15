# Terminal Subsystem

## Kepemilikan sesi shell — Bound Service (Opsi A)

`TerminalSession` (proses shell yang jalan lewat `proot` ke dalam rootfs Debian) dipegang oleh
`TerminalService` (`feature-terminal/.../service/TerminalService.kt`), **bukan** oleh
`TerminalHost` (Compose UI). Ini pola resmi, diverifikasi langsung dari source
`termux/termux-app` (`app/src/main/java/com/termux/app/TermuxService.java`):

- `TerminalService` di-*bind* lewat in-process `LocalBinder` (`Binder` biasa, bukan AIDL/IPC —
  hanya dipakai dari proses yang sama, persis javadoc resmi `TermuxService.LocalBinder`).
- `TerminalHost` memanggil `startForegroundService()` lalu `bindService(..., flags = 0)` — start
  dan bind sengaja dipisah (pola resmi `TermuxActivity.onCreate()`) supaya proses shell tetap
  hidup terlepas dari ada/tidaknya Activity yang sedang bind ke service.
- `TerminalHost` hanya *unbind* saat Composable-nya dibuang (pindah tab, Activity destroy) — TIDAK
  memanggil `stopService()`. `TerminalService` tetap hidup sebagai foreground service dengan
  notification persisten sampai pengguna menekan aksi "Stop" di notification (`ACTION_STOP`).

Kenapa ini penting: kalau `TerminalSession` dibuat & di-attach langsung di dalam Composable
(pendekatan lama, sebelum refactor ini), proses shell ikut mati begitu Activity di-destroy karena
tidak ada pemilik lain yang memegang referensinya.

## Notification & foreground service

- Notification channel `molinax_terminal`, `IMPORTANCE_LOW` (tidak mengganggu, konsisten dengan
  pola resmi `TermuxService.setupNotificationChannel()`).
- `startForeground()` dipanggil di `TerminalService.onCreate()`, sebelum sesi shell mulai
  diprovisi — bukan menunggu sesi siap. Ini wajib: OS baru menganggap service benar-benar
  foreground setelah `startForeground()` dipanggil, telat memanggilnya berisiko service
  di-kill sebelum sempat "menyatakan diri" sebagai foreground.
- Aksi "Stop" di notification mengirim `Intent` dengan `action = TerminalService.ACTION_STOP` ke
  service itu sendiri (`PendingIntent.getService`), yang lalu memanggil `finishIfRunning()` pada
  sesi aktif dan `stopSelf()`.

## Reconnect setelah Activity di-destroy / app kembali dari background

Mekanisme reconnect **built-in** dari desain Bound Service di atas, bukan kode terpisah:

1. `TerminalHost` selalu `bindService()` ulang setiap kali Composable-nya dibuat (mis. Activity
   baru setelah di-destroy sistem, atau pengguna berpindah balik ke tab Terminal).
2. Kalau `TerminalService` masih hidup (proses shell masih jalan di background), `bindService()`
   langsung mendapat `LocalBinder` yang sama, dan `TerminalHost` membaca `TerminalService.state`
   yang saat itu sudah `SessionReady(session)` — sesi lama langsung di-attach ulang ke
   `TerminalView` baru tanpa memulai shell baru (`ensureSessionStarted()` bersifat idempoten,
   ditandai lewat `hasAttemptedStart`/`session != null`, bukan asal restart).
3. Kalau `TerminalService` sendiri sudah tidak hidup (proses aplikasi benar-benar dibunuh OS,
   bukan cuma Activity-nya), tidak ada state untuk direkoneksi — `bindService()` akan memicu
   `onCreate()` baru dan sesi shell baru dimulai dari `ensureSessionStarted()`. Ini batas nyata
   dari model in-process saat ini: sesi TIDAK bisa dipulihkan lintas proses aplikasi (beda dengan
   TermuxService yang punya file transcript/log persisten opsional) — dicatat sebagai gap yang
   sengaja belum ditutup, bukan diasumsikan sudah aman.

## Phantom process killer (Android versi lebih baru)

Di Android versi setelah baseline proyek ini (API 26), proses anak yang di-spawn lewat
`ProcessBuilder`/`exec()` (termasuk proses `proot` yang di-spawn `TerminalSession`) berisiko kena
*phantom process killer* meski app-nya sendiri sudah berstatus foreground service — ini pembatasan
level OS di luar kendali `startForeground()` biasa. Untuk baseline Android 8.0/API 26 sendiri
proyek ini, pembatasan itu belum berlaku seketat versi yang lebih baru. Karena
`targetSdk` proyek ini dikunci ke 26 (lihat `molinaxTargetSdk` di root `build.gradle.kts`), mekanisme
mitigasi tambahan (mis. permintaan pengecualian phantom-process-killer, atau retry otomatis saat
proses anak terdeteksi mati tanpa `sessionFinishedListener` normal) **belum diimplementasikan** —
ditandai sebagai fase lanjutan terpisah (lihat blueprint §11, "Fase lanjutan"), bukan hutang
diam-diam: reconnect dasar (di atas) sudah menutup sebagian besar kasus nyata untuk baseline API 26,
sisanya butuh keputusan eksplisit user sebelum dikerjakan kalau/ketika `targetSdk` dinaikkan.
