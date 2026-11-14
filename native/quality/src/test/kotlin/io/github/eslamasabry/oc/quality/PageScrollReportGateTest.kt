package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit 2 (2026-11-14): a page's content never ends in a hard line. The kit frame dissolves it under the top chrome
 * and above the bottom edge, but only where the page's scroller says where its content lies: `kitPageScrollState()`,
 * `kitPageListState()` or `KitReportPageScroll(state)`. A feature page scroller built on a bare `rememberScrollState()`
 * keeps a hard cut at the bottom. The count only goes down; what is left are a sheet's body (its own scroll) and the
 * conversation's states, which the transcript's frame edges.
 */
class PageScrollReportGateTest {
    private val root = File(System.getProperty("oc.root"))
    private val ui by lazy { BuildRestGates.production(root, "feature") }

    @Test
    fun `PAGE-EDGES a page scroller reports to the frame, the bare ones only shrink`() {
        assertTrue(ui.size > 100, "scanned ${ui.size} feature files")
        val hits = ui.entries.flatMap { (path, text) -> List(bare(text)) { path } }
        assertTrue(
            hits.size <= CEILING,
            "${hits.size} page scrollers use a bare rememberScrollState() (ceiling $CEILING): use kitPageScrollState() " +
                "so the frame can end the page softly:\n" + hits.joinToString("\n"),
        )
    }

    @Test
    fun `PAGE-EDGES planted bare scrollers are counted, reporting ones and comments are not`() {
        assertEquals(1, bare("Column(Modifier.verticalScroll(rememberScrollState())) {}"))
        assertEquals(0, bare("Column(Modifier.verticalScroll(kitPageScrollState())) {}"))
        assertEquals(0, bare("// .verticalScroll(rememberScrollState())\n * verticalScroll(rememberScrollState())"))
    }

    private fun bare(text: String): Int =
        text.lines().count { raw ->
            val line = raw.substringBefore("//").trim()
            !line.startsWith("*") && !line.startsWith("/*") && BARE.containsMatchIn(line)
        }

    private companion object {
        val BARE = Regex("""verticalScroll\(\s*rememberScrollState\(\)\s*\)""")

        /** 2026-11-14: a chat state's body (ChatStates) and two AI Team sheets (FleetGateSheet, FleetStartRunSheet). */
        const val CEILING = 3
    }
}
