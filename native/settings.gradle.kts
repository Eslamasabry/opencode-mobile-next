pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets a host that lacks JDK 17 (build) or JDK 21 (Robolectric/host-test launcher) download it (ADR §8, audit #10).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "opencode-mobile-native"

// Module discovery (plan 03 S2): every direct child folder of these layer folders that holds a build.gradle.kts is a
// module. Adding a module never edits this file. MODULE_OWNERS is the gate (ModuleOwnersTest in :quality).
val layerFolders = listOf("core", "protocol", "engine", "ui", "feature", "android", "tools")
for (layer in layerFolders) {
    val dir = settingsDir.resolve(layer)
    if (!dir.isDirectory) continue
    dir.listFiles()!!
        .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
        .sortedBy { it.name }
        .forEach { include(":$layer:${it.name}") }
}
listOf("androidApp", "desktopApp", "quality")
    .filter { settingsDir.resolve("$it/build.gradle.kts").isFile }
    .forEach { include(":$it") }
