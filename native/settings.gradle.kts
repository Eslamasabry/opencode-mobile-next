pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets a host that lacks JDK 17 (build) or JDK 21 (Robolectric/host-test launcher) download it
    // (ADR §8, audit #10).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // P12-FIX-LINUX-VOICE (plan 03 §0 settings, plan 10 §2.2): vendored, hash-pinned artifacts.
        // Only the sherpa-onnx
        // group resolves here; tools/vendor/fetch.sh fills it and third_party/SHA256SUMS pins it.
        // Termux v0.118.3 from JitPack; third_party/SHA256SUMS pins the emulator AAR, checked when the
        // TermuxSessionFork transform (build-logic) strips the upstream TerminalSession classes.
        exclusiveContent {
            forRepository { maven { url = uri("https://jitpack.io") } }
            filter { includeGroup("com.github.termux.termux-app") }
        }
        exclusiveContent {
            forRepository {
                maven {
                    url = settingsDir.resolve("third_party/maven").toURI()
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
            }
            filter { includeGroup("com.k2fsa.sherpa.onnx") }
        }
    }
}

if (
    !settingsDir
        .resolve(
            "third_party/maven/com/k2fsa/sherpa/onnx/sherpa-onnx-android/2.0.1/sherpa-onnx-android-2.0.1.aar"
        )
        .isFile
) {
    logger.warn(
        "sherpa-onnx 2.0.1 is missing: run native/tools/vendor/fetch.sh (the app build needs it for offline voice)."
    )
}

rootProject.name = "opencode-mobile-native"

// Module discovery (plan 03 S2): every direct child folder of these layer folders that holds a
// build.gradle.kts is a
// module. Adding a module never edits this file. MODULE_OWNERS is the gate (ModuleOwnersTest in
// :quality).
val layerFolders = listOf("core", "protocol", "engine", "ui", "feature", "android", "tools")

for (layer in layerFolders) {
    val dir = settingsDir.resolve(layer)
    if (!dir.isDirectory) continue
    dir.listFiles()!!
        .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
        .sortedBy { it.name }
        .forEach { include(":$layer:${it.name}") }
}

listOf("androidApp", "desktopApp", "quality", "benchmark")
    .filter { settingsDir.resolve("$it/build.gradle.kts").isFile }
    .forEach { include(":$it") }
