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
    }
}

rootProject.name = "Molina-X"

include(":app")
include(":core-common")
include(":feature-player")
include(":feature-editor")
include(":feature-terminal")
include(":feature-utilities")
