// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.hilt) apply false
  kotlin("jvm") version libs.versions.kotlin.get()
}

// Nilai bersama untuk seluruh modul (dipakai via rootProject.extra[...] di
// core-common, feature-player, feature-terminal). Disamakan dengan literal
// yang sudah hardcode di app/, core/, feature-editor/, feature-utilities/,
// dan dengan gradle/libs.versions.toml.
extra["molinaxCompileSdk"] = 36
extra["molinaxMinSdk"] = 26
extra["molinaxJvmToolchain"] = 21
extra["molinaxComposeBom"] = "2024.09.00"
extra["molinaxCoreKtx"] = "1.18.0"
extra["molinaxDatastorePreferences"] = "1.1.7"
extra["molinaxSerializationCore"] = "1.7.3"
extra["molinaxOkHttp"] = "4.10.0"
extra["molinaxMedia3"] = "1.11.1"

kotlin {
  jvmToolchain(21)
}
