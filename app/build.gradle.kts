plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.molinax.manager"
    compileSdk = rootProject.extra["molinaxCompileSdk"] as Int
    ndkVersion = rootProject.extra["molinaxNdkVersion"] as String

    defaultConfig {
        applicationId = "com.molinax.manager"
        minSdk = rootProject.extra["molinaxMinSdk"] as Int
        targetSdk = rootProject.extra["molinaxTargetSdk"] as Int
        versionCode = 1
        versionName = "0.1.0-phase1"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
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

dependencies {
    implementation(project(":core-common"))
    implementation(project(":feature-player"))
    implementation(project(":feature-editor"))
    implementation(project(":feature-terminal"))
    implementation(project(":feature-utilities"))

    val composeBom = platform("androidx.compose:compose-bom:${rootProject.extra["molinaxComposeBom"]}")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.navigation3:navigation3-runtime:${rootProject.extra["molinaxNavigation3"]}")
    implementation("androidx.navigation3:navigation3-ui:${rootProject.extra["molinaxNavigation3"]}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${rootProject.extra["molinaxSerializationCore"]}")
}
