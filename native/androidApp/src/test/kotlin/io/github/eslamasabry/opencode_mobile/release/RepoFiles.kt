package io.github.eslamasabry.opencode_mobile.release

import java.io.File

/** Reads checked-in files for the app-level contract tests. Unit tests run with `native/androidApp` as working dir. */
internal object RepoFiles {
    /** `native/`: the folder holding `settings.gradle.kts` and `version.properties`. */
    val native: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile && File(it, "version.properties").isFile }
    }

    /** The repository root (the Flutter app and `scripts/` live here). */
    val root: File by lazy { native.parentFile }

    fun native(path: String): File = File(native, path)

    fun text(path: String): String = native(path).also { check(it.isFile) { "missing native/$path" } }.readText()

    fun rootText(path: String): String = File(root, path).also { check(it.isFile) { "missing $path" } }.readText()

    /** Every `src/<set>/AndroidManifest.xml` of a shipping source set (main + build-type/flavor sets, not tests). */
    fun shippedManifests(): List<File> =
        native.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" && it.name != "spikes" && it.name != "benchmark" }
            .filter { it.name == "AndroidManifest.xml" && it.parentFile.parentFile?.name == "src" }
            .filter { it.parentFile.name in setOf("main", "androidMain", "preview", "switch", "release") }
            .sortedBy { it.path }
            .toList()
}
