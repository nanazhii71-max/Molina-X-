plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.molinax.terminal"
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
    implementation(project(":core-common"))

    val composeBom = platform("androidx.compose:compose-bom:${rootProject.extra["molinaxComposeBom"]}")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.3")
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3")
}
