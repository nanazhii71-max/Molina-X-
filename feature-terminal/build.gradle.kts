plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.molinax.manager.terminal"
    compileSdk = rootProject.extra["molinaxCompileSdk"] as Int

    defaultConfig {
        minSdk = rootProject.extra["molinaxMinSdk"] as Int
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

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${rootProject.extra["molinaxSerializationCore"]}")
    implementation("androidx.datastore:datastore-preferences:${rootProject.extra["molinaxDatastorePreferences"]}")

    implementation(project(":core-common"))

    val composeBom = platform("androidx.compose:compose-bom:${rootProject.extra["molinaxComposeBom"]}")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // MODIFY blueprint §8.3 (keputusan eksplisit pengguna, storage permission gate di
    // TerminalHost.kt): activity-compose untuk rememberLauncherForActivityResult/
    // ActivityResultContracts, versi disamakan dengan yang sudah dipakai app/build.gradle.kts
    // (androidx.activity:activity-compose:1.13.0) supaya tidak ada drift versi dalam satu repo.
    // androidx.core untuk ContextCompat.checkSelfPermission -- versi 1.19.0 diverifikasi sebagai
    // stable release terbaru dari developer.android.com/jetpack/androidx/releases/core sebelum
    // dipakai, bukan tebakan.
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core:1.19.0")

    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.3")
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3")

    implementation("com.squareup.okhttp3:okhttp:${rootProject.extra["molinaxOkHttp"]}")
    implementation("org.apache.commons:commons-compress:1.28.0")
}
