# APP_ABI TIDAK di-hardcode di sini — ABI yang dibangun dikontrol dari
# feature-player/build.gradle.kts (defaultConfig.ndk.abiFilters), konsisten
# dengan app/build.gradle.kts yang sudah pakai arm64-v8a + armeabi-v7a saja.

# android-26, BUKAN android-23 seperti upstream — disamakan dengan
# molinaxMinSdk=26 baseline Molina-X (root build.gradle.kts), supaya native
# code dikompilasi terhadap level API yang benar-benar jadi baseline app,
# bukan level API yang lebih rendah dari yang didukung.
APP_PLATFORM := android-26

APP_STL := c++_shared
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
