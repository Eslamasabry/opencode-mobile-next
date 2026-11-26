package io.github.eslamasabry.oc.ui.gate.flicker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rules on pictures written by hand: what scrolling excuses, how a block that leaves together reads as one
 * finding, and how the words of a finding keep one baseline line across clocks.
 */
class FlickerRulesTest {
    private val rules = FlickerRules()

    private fun node(id: Int, label: String, top: Float, scrollers: List<Int> = emptyList()) =
        FrameNode(id, label, null, isText = true, DpBox(0f, top, 100f, top + 20f), scrollers)

    private fun frames(vararg pictures: Pair<List<FrameNode>, Map<Int, Float>>): List<RecordedFrame> =
        pictures.mapIndexed { i, (nodes, scrolls) -> RecordedFrame(i, i * 16L, Picture(nodes, scrolls)) }

    private fun label(id: Int, words: String, left: Float, right: Float, top: Float = 514f) =
        FrameNode(id, words, null, isText = true, DpBox(left, top, right, top + 20f), emptyList(), BUTTON)

    private fun rulesOf(vararg shown: List<FrameNode>): List<FlickerRule> {
        val run = shown.mapIndexed { i, nodes -> RecordedFrame(i, i * 16L, Picture(nodes, emptyMap())) }
        return rules.run(run).map { it.rule }
    }

    @Test
    fun `B-FLICKER-010 a row crossing a scrolling list for a frame is no blink, on a still list it is`() {
        val list = 7
        val passing = node(2, "Row 12", 300f, listOf(list))
        val steady = node(1, "Steady", 0f)
        val scrolled =
            frames(
                listOf(steady) to mapOf(list to 0f),
                listOf(steady, passing) to mapOf(list to 1f),
                listOf(steady) to mapOf(list to 2f),
                listOf(steady) to mapOf(list to 2f),
            )
        assertTrue(rules.run(scrolled).isEmpty(), "a row crossing a scrolling list: ${rules.run(scrolled)}")
        val still =
            frames(
                listOf(steady) to mapOf(list to 0f),
                listOf(steady, passing) to mapOf(list to 0f),
                listOf(steady) to mapOf(list to 0f),
                listOf(steady) to mapOf(list to 0f),
            )
        assertEquals(listOf(FlickerRule.BLINK), rules.run(still).map { it.rule })
    }

    @Test
    fun `B-FLICKER-011 three lines that leave and come back together are one finding naming the first`() {
        val lines =
            listOf(
                node(1, "Retrying 2 in 0:01", 0f),
                node(2, "Can't reach the model's provider.", 20f),
                node(3, "Choose another model", 40f),
            )
        val live = listOf(node(4, "Thinking · 7 s", 0f))
        val none = emptyMap<Int, Float>()
        val run = frames(lines to none, live to none, live to none, lines to none, lines to none)
        val returns = rules.run(run).filter { it.rule == FlickerRule.ABA }
        assertEquals(1, returns.size, "one block, one finding: $returns")
        assertEquals("Retrying # in #:#", returns.single().key)
        assertTrue("+2 more" in returns.single().detail, returns.single().detail)
    }

    @Test
    fun `B-FLICKER-012 a first frame that lays the newest line 260 dp off its settled place is reported once`() {
        val first = frames(listOf(node(1, "hello d8", 527f), node(2, "Sending…", 560f)) to emptyMap())
        val after = listOf(node(1, "hello d8", 790f), node(2, "Sending…", 560f))
        val settled = RecordedFrame(9, 144L, Picture(after, emptyMap()))
        val finding = rules.firstVsSettled(first.single(), settled)
        assertEquals(FlickerRule.FIRST, finding?.rule)
        assertEquals("hello d8", finding?.label)
        assertEquals(null, rules.firstVsSettled(settled, settled))
    }

    @Test
    fun `B-FLICKER-016 a button's label swapped short to long in place is no jump, other moves still are`() {
        val short = label(11, "Update 1 agent", 170f, 230f)
        val long = label(11, "Keep every agent as it is", 110f, 290f)
        val wordy = label(11, "Update every agent and every part it needs now", 50f, 350f)
        fun swap(from: FrameNode, to: FrameNode) =
            rulesOf(listOf(from), listOf(from), listOf(to), listOf(to), listOf(to))
        assertEquals(emptyList<FlickerRule>(), swap(short, long), "a button's label swap read as a fault")
        assertEquals(listOf(FlickerRule.JUMP), swap(short.copy(control = null), long.copy(control = null)))
        assertEquals(listOf(FlickerRule.JUMP), swap(short, long.copy(box = DpBox(110f, 554f, 290f, 574f))))
        assertEquals(listOf(FlickerRule.JUMP), swap(short, wordy))
    }

    @Test
    fun `B-FLICKER-017 a button's label swapped away and back in place is no A-B-A, words leaving it still are`() {
        val short = label(11, "Update 1 agent", 170f, 230f)
        val long = label(12, "Keep every agent as it is", 110f, 290f)
        fun away(from: FrameNode, to: List<FrameNode>) =
            rulesOf(listOf(from), listOf(from), to, to, to, to, listOf(from), listOf(from))
        assertEquals(emptyList<FlickerRule>(), away(short, listOf(long)), "a button's label swap read as a fault")
        assertEquals(listOf(FlickerRule.ABA), away(short.copy(control = null), listOf(long.copy(control = null))))
        assertEquals(listOf(FlickerRule.ABA), away(short, listOf(long.copy(box = DpBox(110f, 554f, 290f, 574f)))))
        assertEquals(listOf(FlickerRule.ABA), away(short, emptyList()))
    }

    private companion object {
        const val BUTTON = 10
    }
}
