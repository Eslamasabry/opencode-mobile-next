package io.github.eslamasabry.oc.feature.chat.rb.o42

import io.github.eslamasabry.oc.feature.chat.inbox.presentation.ChatInboxRow
import io.github.eslamasabry.oc.feature.chat.inbox.presentation.PendingNotice
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlin.test.Test
import kotlin.test.assertEquals

/** N-48 (E20-S01): one notice for an unconfirmed send, never a "messages are waiting" line beside it. */
class PendingNoticeTest {
    private val note = PendingSend(1, "did it arrive?", PendingState.Unconfirmed)

    @Test
    fun `B-STATE-008 the held send is said under its bubble, not counted as waiting`() {
        val held = ChatInboxRow("q1", "did it arrive?", local = true, dispatched = true)
        assertEquals(emptyList(), PendingNotice.unsaid(listOf(held), listOf(note)))
    }

    @Test
    fun `B-STATE-008 Send again's fresh copy is not a second notice while the old note still stands`() {
        val copy = ChatInboxRow("q2", "did it arrive? ", local = true, dispatched = false)
        assertEquals(emptyList(), PendingNotice.unsaid(listOf(copy), listOf(note)))
    }

    @Test
    fun `B-STATE-008 other waiting sends are still counted once`() {
        val other = ChatInboxRow("q3", "something else", local = true)
        val server = ChatInboxRow("i1", "did it arrive?", local = false)
        assertEquals(listOf(other, server), PendingNotice.unsaid(listOf(other, server), listOf(note)))
        val copy = ChatInboxRow("q2", "did it arrive?", local = true)
        assertEquals(
            listOf(copy),
            PendingNotice.unsaid(listOf(copy), listOf(note.copy(state = PendingState.Sending))),
            "with no unconfirmed note the copy is an ordinary waiting send",
        )
    }
}
