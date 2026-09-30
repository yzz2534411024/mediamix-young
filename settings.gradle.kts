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

rootProject.name = "mediamix-kmp"
include(":shared")
include(":androidApp")
include(":desktopApp")
include(":composeUi")
