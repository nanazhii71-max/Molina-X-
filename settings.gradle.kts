pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Molina-X"

include(":app")
include(":core-common")
include(":feature-player")
include(":feature-editor")
include(":feature-terminal")
include(":feature-utilities")
