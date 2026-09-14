plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.molinax.manager.common"
    compileSdk = rootProject.extra["molinaxCompileSdk"] as Int

    defaultConfig {
        minSdk = rootProject.extra["molinaxMinSdk"] as Int
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(rootProject.extra["molinaxJvmToolchain"] as Int)
}
