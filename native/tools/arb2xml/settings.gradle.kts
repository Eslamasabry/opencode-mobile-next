// Standalone build: this tool is NOT part of native/settings.gradle.kts and uses no build-logic.
// Run it from this folder: ./gradlew test run --args="convert …" (see docs/native/slice0/P6-strings.md).
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "arb2xml"
