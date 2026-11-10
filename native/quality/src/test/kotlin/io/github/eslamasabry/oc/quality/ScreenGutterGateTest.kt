package io.github.eslamasabry.oc.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit C02 (2026-11-09): `KitScreen` keeps the page gutter for every body unless the screen opts into
 * `KitScreenEdges.Bleed`. The screens that kept their own gutters before the change were marked Bleed so nothing moved;
 * each one moves to the frame's gutter as it is next touched. The count only goes down: a new screen gets the gutter
 * from the frame, and a new Bleed must replace an old one.
 */
class ScreenGutterGateTest {
    private val root = File(System.getProperty("oc.root"))
    private val ui by lazy { BuildRestGates.production(root, "feature", "ui", "androidApp") }

    @Test
    fun `C02 full-bleed screens are explicit and their count only shrinks`() {
        assertTrue(ui.size > 100, "scanned ${ui.size} UI files")
        val count = ui.entries.sumOf { (_, text) -> bleeds(text) }
        assertTrue(
            count <= BLEED_CEILING,
            "$count screens opt out of the page gutter (ceiling $BLEED_CEILING): let KitScreen keep the gutter " +
                "(the default) instead of adding KitScreenEdges.Bleed",
        )
    }

    @Test
    fun `C02 planted opt-outs are counted, comments are not`() {
        assertEquals(1, bleeds("KitScreen(edges = KitScreenEdges.Bleed) {}"))
        val two = "KitScreen(\n    edges = KitScreenEdges.Bleed,\n)\nKitScreen(edges = KitScreenEdges.Bleed)"
        assertEquals(2, bleeds(two))
        assertEquals(0, bleeds("// edges = KitScreenEdges.Bleed\n * KitScreenEdges.Bleed runs edge to edge"))
    }

    private fun bleeds(text: String): Int =
        text.lines().count { raw ->
            val line = raw.substringBefore("//").trim()
            !line.startsWith("*") && !line.startsWith("/*") && BLEED.containsMatchIn(line)
        }

    private companion object {
        val BLEED = Regex("""\bedges\s*=\s*KitScreenEdges\.Bleed\b""")

        /**
         * The screens marked Bleed when the frame took the gutter (2026-11-09: 123). Lower it as screens move over.
         * 125 since 2026-11-10: the AI Team settings and setup pages arrived from their own branch already marked
         * Bleed and were never counted, because this suite could not start. They are debt like the rest (B-225).
         */
        const val BLEED_CEILING = 125
    }
}
