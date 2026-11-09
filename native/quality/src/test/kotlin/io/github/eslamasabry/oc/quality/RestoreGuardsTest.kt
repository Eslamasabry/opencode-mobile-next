package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Gap 2 (foundations audit 2026-11-09): what comes back after Android kills the app. Open sheets and menus are saved
 * with `rememberSaveable`; a confirm for a destructive act (delete, remove, forget, disconnect, wipe, reset, stop) and
 * anything that holds a secret are never saved, so the person has to ask again and no password reaches saved state.
 */
class RestoreGuardsTest {
    private val root = File(System.getProperty("oc.root") ?: ".").absoluteFile

    private val sources: List<File> by lazy {
        listOf("ui", "feature", "engine")
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" && "/commonMain/" in it.path && "/build/" !in it.path }
                    .toList()
            }
    }

    private val saved = Regex("""(?:var|val)\s+(\w+)\s+by\s+rememberSaveable""")
    private val destructive = Regex("(?i)(confirm|delete|remove|forget|disconnect|wipe|erase|reset|stop|asking|revoke)")
    private val secret = Regex("(?i)(password|passcode|secret|token|apikey|credential)")

    private fun savedNames(): List<Pair<File, String>> =
        sources.flatMap { f -> saved.findAll(f.readText()).map { f to it.groupValues[1] }.toList() }

    @Test
    fun `gap 2 no confirm for a destructive act is saved across a process death`() {
        val bad = savedNames().filter { (_, name) -> destructive.containsMatchIn(name) }
            .map { (f, name) -> "${f.relativeTo(root)}: $name" }
        assertEquals(emptyList(), bad, "a destructive confirm must use remember, not rememberSaveable")
    }

    @Test
    fun `gap 2 no secret is held in saved state`() {
        val bad = savedNames().filter { (_, name) -> secret.containsMatchIn(name) }
            .map { (f, name) -> "${f.relativeTo(root)}: $name" }
        assertEquals(emptyList(), bad, "a secret must never be saved with rememberSaveable")
    }
}
