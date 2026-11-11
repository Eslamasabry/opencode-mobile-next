package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.StatusAction
import io.github.eslamasabry.oc.android.notifications.StatusKind
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.dock.OutsideAction
import io.github.eslamasabry.oc.engine.attention.dock.OutsideKind
import io.github.eslamasabry.oc.engine.attention.dock.OutsideRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DockOutsideWiringTest {
    private fun ref(s: String) = ConversationRef(ProfileId("p"), SessionId(s))

    private fun row(stop: OutsideAction? = null, pulses: Int = 3) =
        OutsideRow(
            kind = OutsideKind.Working, title = "Working on A", text = "3 of 7 tasks", lines = emptyList(),
            publicTitle = "OpenCode · 1 working", count = 1, needsCount = 0, workingCount = 1, progress = 42,
            subText = "", open = OutsideAction(ref("a"), "Open A"), stop = stop, pulses = pulses,
        )

    @Test
    fun `Stop is on the content only when the row has a stop target, and the pulse count rides in the turn id`() {
        val with = row(OutsideAction(ref("a"), "Stop A")).toStatusContent()
        assertEquals(listOf(StatusAction.Open, StatusAction.StopReply), with.actions)
        assertEquals(ref("a"), with.stopConversation)
        assertEquals("Open A", with.openLabel)
        assertEquals("Stop A", with.stopLabel)
        assertEquals(StatusKind.Working, with.kind)
        assertEquals(42, with.progress)
        assertEquals(3, dockPulsesOf(with.turnId))
        val without = row().toStatusContent()
        assertEquals(listOf(StatusAction.Open), without.actions)
        assertNull(without.stopConversation)
    }

    @Test
    fun `only a dock turn id is read back as a pulse count`() {
        assertNull(dockPulsesOf(null))
        assertNull(dockPulsesOf("turn_1"))
        assertNull(dockPulsesOf("dock:x"))
        assertEquals(0, dockPulsesOf("dock:0"))
    }
}
