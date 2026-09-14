plugins {
    id("com.android.application") version "9.1.1" apply false
    id("com.android.library") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
}

// Dikonsumsi oleh setiap modul Android (app/, feature-*) mulai Phase 1.
extra["molinaxNdkVersion"] = "28.2.13676358"
extra["molinaxCompileSdk"] = 37
extra["molinaxMinSdk"] = 26
extra["molinaxTargetSdk"] = 26
extra["molinaxJvmToolchain"] = 21
extra["molinaxComposeBom"] = "2026.08.00"
extra["molinaxNavigation3"] = "1.1.7"
extra["molinaxSerializationCore"] = "1.11.0"
