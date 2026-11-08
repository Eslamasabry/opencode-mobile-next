package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionRevert
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.world.ChatUndone
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * S28 story 3 (Flutter `chat_status_line.dart:89-98`, `_restore`, `/redo`): undone turns show a quiet line with the
 * way back. A staged undo (OpenCode 2) opens its review page; an undo the server applied at once is put back.
 */
class S28RedoTest {
    private class UndoWorld : FakeChatWorld() {
        override val undone = MutableStateFlow(ChatUndone.None)
        var putBacks = 0

        override suspend fun putBack(): Outcome<Unit> {
            putBacks++
            undone.value = ChatUndone.None
            return Outcome.Ok(Unit)
        }
    }

    private val world = UndoWorld()
    private val nav = RecordingNavigator()
    private val presenter = ChatPresenter(world, nav, Immediate)

    @Test
    fun `B-CHAT-S28-20 a staged undo shows Review, which opens the staged page and sends nothing`() {
        world.undone.value = ChatUndone.Staged
        val ui = presenter.current()
        assertEquals(ChatStatusKind.StagedUndo, ui.status?.kind)
        assertTrue(ui.canPutBack)
        presenter.putBack()
        assertEquals(listOf<Any>(ReviewRoute.Staged(world.ref)), nav.opened)
        assertEquals(0, world.putBacks)
    }

    @Test
    fun `B-CHAT-S28-21 an undo applied at once shows Put back, which restores and clears the line`() {
        world.undone.value = ChatUndone.Applied
        assertEquals(ChatStatusKind.Undone, presenter.current().status?.kind)
        presenter.putBack()
        assertEquals(1, world.putBacks)
        assertTrue(nav.opened.isEmpty())
        val after = presenter.current()
        assertNull(after.status)
        assertFalse(after.canPutBack)
    }

    @Test
    fun `B-CHAT-S28-22 nothing undone, nothing offered, and a real line outranks the undo line`() {
        assertNull(presenter.current().status)
        presenter.putBack()
        assertEquals(0, world.putBacks)
        world.undone.value = ChatUndone.Applied
        world.link.value = io.github.eslamasabry.oc.feature.chat.world.ChatLink.Offline
        assertEquals(ChatStatusKind.Offline, presenter.current().status?.kind)
    }

    @Test
    fun `B-CHAT-S28-23 the session says how its turns come back`() {
        val sid = SessionId("s1")
        val plain = Session(sid)
        val reverted = Session(sid, reverted = true)
        val staged = Session(sid, stagedRevert = SessionRevert(MessageId("m1"), null, null, null))
        assertEquals(ChatUndone.None, ChatUndone.of(sessionRevert = true, stages = true, session = plain))
        assertEquals(ChatUndone.Staged, ChatUndone.of(sessionRevert = true, stages = true, session = staged))
        assertEquals(ChatUndone.Applied, ChatUndone.of(sessionRevert = true, stages = false, session = reverted))
        assertEquals(ChatUndone.None, ChatUndone.of(sessionRevert = false, stages = false, session = reverted))
    }
}
