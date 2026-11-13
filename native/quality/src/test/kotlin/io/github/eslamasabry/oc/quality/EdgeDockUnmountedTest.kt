package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rings edge dock is removed (owner decision 2026-11-11); a small lamp will replace it later. Until then the app
 * binds no dock to the home or to a conversation, and Settings offers no switch for one. The dock's kit parts, its
 * model and its stored preference stay in the repository for the lamp to reuse; only the mounting is gone.
 */
class EdgeDockUnmountedTest {
    private val root = File(System.getProperty("oc.root") ?: ".").absoluteFile

    private fun appSources(): List<Pair<String, String>> =
        File(root, "androidApp/src/main").walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.relativeTo(root).path to it.readText() }
            .toList()

    @Test
    fun `the app binds no edge dock to the home or to a conversation`() {
        val hits = appSources().filter { (_, text) -> Regex("""binding<(Home|Chat)EdgeDock>""").containsMatchIn(text) }.map { it.first }
        assertEquals(emptyList(), hits, "an edge dock is mounted again in: $hits")
    }

    @Test
    fun `the app hands Settings no edge dock switch`() {
        val hits = appSources().filter { (_, text) -> text.contains("EdgeDockSetting") }.map { it.first }
        assertEquals(emptyList(), hits, "Settings is offered a dock switch in: $hits")
    }
}
