// Standalone build: it is NOT included from native/settings.gradle.kts and does not use native/build-logic.
// Run it from this directory: ./gradlew test
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "flutter-migration-readers"
