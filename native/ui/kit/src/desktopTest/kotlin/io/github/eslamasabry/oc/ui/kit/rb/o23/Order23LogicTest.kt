package io.github.eslamasabry.oc.ui.kit.rb.o23

import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.agent.AgentGridCell
import io.github.eslamasabry.oc.ui.kit.agent.agentGridLayout
import io.github.eslamasabry.oc.ui.kit.agent.agentLineWords
import io.github.eslamasabry.oc.ui.kit.motion.KitPresenceAxis
import io.github.eslamasabry.oc.ui.kit.motion.presenceGone
import io.github.eslamasabry.oc.ui.kit.motion.presenceSlot
import io.github.eslamasabry.oc.ui.kit.state.KitWindowTone
import io.github.eslamasabry.oc.ui.kit.state.windowInlineFits
import io.github.eslamasabry.oc.ui.kit.state.windowLabel
import io.github.eslamasabry.oc.ui.kit.state.windowStacks
import io.github.eslamasabry.oc.ui.kit.state.windowValueTone
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of order 23's parts, each from its Dart line at the Flutter ref. */
class Order23LogicTest {
    @Test
    fun `B-KIT-023 KitPresence opens its slot along its axis and builds nothing once gone`() {
        assertEquals(50 to 20, presenceSlot(100, 20, .5f, KitPresenceAxis.Horizontal))
        assertEquals(100 to 10, presenceSlot(100, 20, .5f, KitPresenceAxis.Vertical))
        assertEquals(100 to 20, presenceSlot(100, 20, 1f, KitPresenceAxis.Horizontal))
        assertTrue(presenceGone(0f, visible = false))
        assertFalse(presenceGone(0f, visible = true), "a part arriving from nothing is composed")
        assertFalse(presenceGone(.01f, visible = false), "a leaving part keeps drawing until its slot is closed")
    }

    @Test
    fun `KitWindowRow stacks from 1_3x text, colours its value by tone and reads one label`() {
        assertFalse(windowStacks(1.29f))
        assertTrue(windowStacks(1.3f))
        assertTrue(windowStacks(2f))
        assertEquals(TextTone.Primary, windowValueTone(KitWindowTone.Low))
        assertEquals(TextTone.Primary, windowValueTone(KitWindowTone.Out))
        assertEquals(TextTone.Secondary, windowValueTone(KitWindowTone.Normal))
        assertEquals(TextTone.Secondary, windowValueTone(KitWindowTone.Calm))
        assertEquals("Week, 18% left · Thu", windowLabel("Week", "18% left · Thu", null))
        assertEquals("Week, 18% left · Thu, Resets at Wed", windowLabel("Week", "18% left · Thu", "Resets at Wed"))
        // name 64 + gap 12 + value 80 + bar 48 = 204
        assertTrue(windowInlineFits(64.dp, 80.dp, 204.dp))
        assertFalse(windowInlineFits(64.dp, 80.dp, 203.dp))
    }

    @Test
    fun `KitAgentGrid pairs cards, spans an odd last card and goes one column above 1_3x text`() {
        assertEquals(
            listOf(AgentGridCell.Pair(0), AgentGridCell.Pair(2), AgentGridCell.Span(4)),
            agentGridLayout(5, 1f),
        )
        assertEquals(listOf(AgentGridCell.Pair(0), AgentGridCell.Pair(2), AgentGridCell.Pair(4)), agentGridLayout(6, 1.3f))
        assertEquals(listOf(AgentGridCell.Span(0)), agentGridLayout(1, 1f))
        assertEquals(List(3) { AgentGridCell.Single(it) }, agentGridLayout(3, 2f))
    }

    @Test
    fun `KitAgentLine says who and where on one line`() {
        assertEquals("Claude Code · on this phone", agentLineWords("Claude Code", "on this phone"))
    }
}
