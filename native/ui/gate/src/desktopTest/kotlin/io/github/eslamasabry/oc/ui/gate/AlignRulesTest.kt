package io.github.eslamasabry.oc.ui.gate

import io.github.eslamasabry.oc.ui.gate.AlignRules.Align
import io.github.eslamasabry.oc.ui.gate.AlignRules.Box
import io.github.eslamasabry.oc.ui.gate.AlignRules.Kind
import io.github.eslamasabry.oc.ui.gate.AlignRules.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Each alignment rule (native/docs/kit/ALIGN-RULES.md) fires on a fixture that breaks it and stays quiet on the
 * same fixture done right. The fixtures are the model the rules read, so a rule is tested without a render.
 */
class AlignRulesTest {
    private val w = 412f // NV: the census phone width
    private val h = 915f // NV: the census phone height

    /** A tree under one root (id 0) that fills the window. */
    private class Tree {
        val nodes = arrayListOf(Node(0, null, Box(0f, 0f, 412f, 915f), Box(0f, 0f, 412f, 915f)))
        private var next = 1

        @Suppress("LongParameterList") // NV: a fixture node names every field it sets
        fun add(
            parent: Int, l: Float, t: Float, r: Float, b: Float,
            kind: Kind = Kind.Other, words: String = "", glyph: Float? = null, tap: Boolean = false,
            bleed: Boolean = false, list: Boolean = false, align: Align = Align.Start, inkR: Float? = null,
            ellipsized: Boolean = false, contentEdge: Boolean = false, lines: Int = 1,
            line0: Pair<Float, Float>? = null,
        ): Int {
            val id = next++
            val box = Box(l, t, r, b)
            nodes += Node(
                id, parent, box, box, kind, words, null, tap, bleed, list = list, align = align,
                inkL = if (kind == Kind.Text) l else null, inkR = if (kind == Kind.Text) inkR ?: r else null,
                ellipsized = ellipsized, contentEdge = contentEdge, lines = lines, line0 = line0, glyph = glyph,
            )
            return id
        }

        fun text(parent: Int, l: Float, t: Float, r: Float, b: Float) = add(parent, l, t, r, b, Kind.Text, "Words")

        fun icon(parent: Int, l: Float, t: Float, size: Float = 24f, box: Float = size) =
            add(parent, l, t, l + box, t + box, Kind.Glyph, "", glyph = size)

        /** A list row (tappable, bleeding) at [t] with a title at [titleX] and a chevron ending at [endX]. */
        fun row(list: Int, t: Float, height: Float = 56f, titleX: Float = 56f, endX: Float? = 396f): Int {
            val r = add(list, 0f, t, 412f, t + height, tap = true, bleed = true)
            add(r, titleX, t + 18f, 200f, t + 38f, Kind.Text, "Row at $t")
            if (endX != null) icon(r, endX - 20f, t + 18f, 20f)
            return r
        }
    }

    private fun rules(screen: Boolean = true, build: Tree.() -> Unit): List<String> =
        AlignRules.check(Tree().apply(build).nodes, w, h, screen).map { it.rule }

    @Test
    fun `start-edge words off the page columns are reported and words on them are not`() {
        assertTrue("start-edge" in rules { text(0, 32f, 100f, 300f, 120f) })
        assertTrue("start-edge" !in rules { text(0, 16f, 100f, 300f, 120f); text(0, 56f, 140f, 300f, 160f) })
        assertTrue("start-edge" !in rules { text(0, 20f, 100f, 300f, 120f) }) // the sheet rail
        // a line's second words
        assertTrue("start-edge" !in rules { text(0, 16f, 100f, 80f, 120f); text(0, 88f, 100f, 300f, 120f) })
        assertTrue("start-edge" !in rules(screen = false) { text(0, 32f, 100f, 300f, 120f) }) // a lone kit part
        val centred = Tree().apply { add(0, 150f, 100f, 260f, 120f, Kind.Text, "Centred", align = Align.Centre) }
        assertTrue(AlignRules.check(centred.nodes, w, h, true).none { it.rule == "start-edge" })
    }

    @Test
    fun `start-edge icons sit on an edge or in the leading tile's centre`() {
        assertTrue("start-edge" in rules { icon(0, 24f, 100f) })
        assertTrue("start-edge" !in rules { icon(0, 18f, 100f) }) // centre 30: the 28 dp tile on the 16 dp edge
        assertTrue("start-edge" !in rules { icon(0, 16f, 100f) })
    }

    @Test
    fun `start-edge the screen's distinct edges are listed with their counts`() {
        val t = Tree().apply {
            text(0, 16f, 100f, 300f, 120f)
            text(0, 16f, 130f, 300f, 150f)
            text(0, 32f, 160f, 300f, 180f)
        }
        assertEquals(mapOf(16f to 2, 32f to 1), AlignRules.startEdges(t.nodes, w, h))
    }

    @Test
    fun `gap-step a 9 dp gap between blocks is reported and an 8 dp gap is not`() {
        fun scene(gap: Float) = rules { text(0, 16f, 100f, 300f, 120f); text(0, 16f, 120f + gap, 300f, 140f + gap) }
        assertTrue("gap-step" in scene(9f))
        assertTrue("gap-step" !in scene(8f))
        assertTrue("gap-step" !in scene(2f)) // the row line gap
        assertTrue("gap-step" !in scene(80f)) // page layout, not a step
    }

    @Test
    fun `icon-label an icon off its label's centre is reported`() {
        fun scene(dy: Float) = rules(screen = false) {
            val c = add(0, 16f, 100f, 200f, 148f, tap = true)
            icon(c, 28f, 112f + dy, 24f)
            add(c, 60f, 114f, 180f, 134f, Kind.Text, "Label", line0 = 114f to 134f)
        }
        assertTrue("icon-label" in scene(2f))
        assertTrue("icon-label" !in scene(0f))
    }

    @Test
    fun `end-edge a row's chevron off the list's end edge is reported`() {
        fun scene(odd: Float) = rules(screen = false) {
            val list = add(0, 0f, 100f, 412f, 400f, list = true)
            row(list, 100f); row(list, 156f); row(list, 212f, endX = odd)
        }
        assertTrue("end-edge" in scene(386f))
        assertTrue("end-edge" !in scene(396f))
    }

    @Test
    fun `target a tappable under 48 dp is reported`() {
        assertTrue("target" in rules(screen = false) { add(0, 16f, 100f, 48f, 132f, tap = true) })
        assertTrue("target" !in rules(screen = false) { add(0, 16f, 100f, 64f, 148f, tap = true) })
    }

    @Test
    fun `ellipsis-frame a cut button label or bar title is reported and a cut row line is not`() {
        assertTrue("ellipsis-frame" in rules(screen = false) {
            val b = add(0, 16f, 100f, 200f, 152f, tap = true)
            add(b, 32f, 116f, 184f, 136f, Kind.Text, "Save the conver…", ellipsized = true)
        })
        assertTrue("ellipsis-frame" in rules(screen = false) {
            add(0, 16f, 20f, 300f, 48f, Kind.Text, "A long title…", ellipsized = true, contentEdge = true)
        })
        assertTrue("ellipsis-frame" !in rules(screen = false) {
            add(0, 16f, 100f, 300f, 120f, Kind.Text, "A supporting line…", ellipsized = true)
        })
    }

    @Test
    fun `clip a child laid out past its parent is reported`() {
        fun scene(r: Float) = rules(screen = false) {
            val p = add(0, 16f, 100f, 200f, 148f)
            text(p, 24f, 110f, r, 130f)
        }
        assertTrue("clip" in scene(220f))
        assertTrue("clip" !in scene(190f))
    }

    @Test
    fun `icon-size and icon-squeezed a glyph off the kit sizes or in a smaller box is reported`() {
        assertTrue("icon-size" in rules(screen = false) { icon(0, 16f, 100f, size = 18f) })
        assertTrue("icon-size" !in rules(screen = false) { icon(0, 16f, 100f, size = 16f) })
        assertTrue("icon-squeezed" in rules(screen = false) { icon(0, 16f, 100f, size = 24f, box = 16f) })
        assertTrue("icon-squeezed" !in rules(screen = false) { icon(0, 16f, 100f, size = 24f) })
    }

    @Test
    fun `row-height rows of one kind in one list share a height`() {
        fun scene(odd: Float) = rules(screen = false) {
            val list = add(0, 0f, 100f, 412f, 400f, list = true)
            row(list, 100f); row(list, 156f); row(list, 212f, height = odd)
        }
        assertTrue("row-height" in scene(64f))
        assertTrue("row-height" !in scene(56f))
    }

    @Test
    fun `every rule id is listed once`() {
        assertEquals(AlignRules.RULES.toSet().size, AlignRules.RULES.size)
    }
}
