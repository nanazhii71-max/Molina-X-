plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.molinax.manager.player"
    compileSdk = rootProject.extra["molinaxCompileSdk"] as Int

    // Path ke Android.mk — dibaca AGP untuk mengorkestrasi ndk-build sebagai
    // task Gradle asli (bukan CLI standalone di luar Gradle seperti pola
    // upstream mpv-android sendiri, lihat catatan di VENDOR.md).
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    defaultConfig {
        minSdk = rootProject.extra["molinaxMinSdk"] as Int

        ndk {
            // Konsisten dengan app/build.gradle.kts — hanya 2 ABI target
            // Molina-X, bukan 4 ABI upstream (x86/x86_64 tidak relevan).
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            ndkBuild {
                // PREFIX32/PREFIX64 diteruskan sebagai macro ndk-build
                // (dipakai Android.mk untuk resolve $(PREFIX)/lib/*.so per
                // ABI) — setara PREFIX32/PREFIX64 di mpv-android.sh upstream.
                // Diisi dari env var yang di-set CI SEBELUM ./gradlew
                // dijalankan (lihat build-verify.yml: download+extract
                // artifact mpv-prefix-<abi>, lalu export
                // MPV_PREFIX_ARMV7/MPV_PREFIX_ARM64 ke path hasil extract).
                // Kalau env var kosong (bukan konteks CI kita), ndk-build
                // akan gagal keras dengan error file-not-found yang jelas
                // saat resolve $(PREFIX)/lib/libmpv.so — sengaja tidak
                // ditambah guard tebak-tebakan di Gradle, biar kegagalan
                // nyata dari toolchain yang bicara, bukan asumsi Gradle.
                val prefixArmv7 = System.getenv("MPV_PREFIX_ARMV7") ?: ""
                val prefixArm64 = System.getenv("MPV_PREFIX_ARM64") ?: ""
                arguments(
                    "PREFIX32=$prefixArmv7",
                    "PREFIX64=$prefixArm64"
                )
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(rootProject.extra["molinaxJvmToolchain"] as Int)
}

// Media3 (Phase 4, keputusan arsitektur 17 Sep 2026): MediaSessionService+SimpleBasePlayer,
// menggantikan androidx.media legacy yang di-deprecate per rilis 1.8.0-nya sendiri.
val molinaxMedia3Version = rootProject.extra["molinaxMedia3"] as String

// core-ktx (17 Sep 2026): dibutuhkan MPVView.kt untuk ContextCompat.getDisplayOrDefault().
// VERIFIED developer.android.com/jetpack/androidx/releases/core, versi dipin di root
// build.gradle.kts (extra["molinaxCoreKtx"]) supaya module lain yang butuh core-ktx nanti
// pakai sumber versi yang sama, bukan angka literal terpisah per module.
val molinaxCoreKtxVersion = rootProject.extra["molinaxCoreKtx"] as String

dependencies {
    implementation(project(":core-common"))

    val composeBom = platform("androidx.compose:compose-bom:${rootProject.extra["molinaxComposeBom"]}")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.media3:media3-session:$molinaxMedia3Version")
    implementation("androidx.media3:media3-common:$molinaxMedia3Version")

    implementation("androidx.core:core-ktx:$molinaxCoreKtxVersion")

    // GAP KRITIS Phase 4 (audit 17 Sep 2026, PlayerHost.kt wiring): activity-compose untuk
    // rememberLauncherForActivityResult/ActivityResultContracts.OpenDocument (input Local, SAF).
    // Versi disamakan persis dengan feature-terminal/build.gradle.kts (androidx.activity:
    // activity-compose:1.13.0) supaya tidak ada drift versi dalam satu repo -- sudah diverifikasi
    // di situ sebagai versi stable resmi, tidak diverifikasi ulang di sini (sumber sama).
    implementation("androidx.activity:activity-compose:1.13.0")
}
