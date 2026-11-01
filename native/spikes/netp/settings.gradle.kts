// S0-NETP spike: a standalone build, NOT part of native/settings.gradle.kts.
// Run with: ../../gradlew -p . test   (from native/spikes/netp)
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "netp-spike"
