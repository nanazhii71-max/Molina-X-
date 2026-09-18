# Provenance

Folder ini adalah vendor PENUH (tanpa modifikasi) dari:
- Upstream: https://github.com/mpv-android/mpv-android
- Path: buildscripts/
- Pinned commit: 751d53205cc3d878c7161d79a97e1b65ed6ed37b
- Tanggal vendor: 2026-09-16
- Alasan pin ke commit (bukan tag): tag rilis resmi terakhir mereka adalah
  mpv-android-2023-02-27 (usang), sedangkan branch master sudah pakai NDK r30
  dan flag build terkini (Lua+libcurl enabled, libplacebo, curl). Commit di atas
  adalah HEAD master saat vendor dilakukan.

## Penyimpangan sengaja dari upstream (di luar folder ini, di workflow CI kita)
- mpv di-pin ke tag stabil v0.41.0, BUKAN branch master mpv-player/mpv
  seperti yang dilakukan buildscripts/include/ci.sh upstream (baris
  `wget https://github.com/mpv-player/mpv/archive/master.tar.gz`).
  Alasan: reproducibility & stabilitas jangka panjang.
- Kita tidak menjalankan tahap build app mpv-android (gradlew/ndk-build) dari
  ci.sh/mpv-android.sh — hanya sampai `libmpv.so` per ABI, karena Molina-X
  punya application layer sendiri (lihat feature-player/).
