package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.update

/**
 * S24-CHAT story 6 (Flutter `chat_stop_after_step.dart`): holding Stop sends the running turn a
 * steering note once, the live line says it waits for the step, and it clears when the turn ends.
 */
class S24StopAfterStepTest {
    private val note = "Please stop after the step you are on now and wait for me before doing anything else."
    private val world = FakeChatWorld()
    private val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)

    private fun running(steers: Boolean) {
        world.capabilities.value = ServerCapabilities(promptSteering = steers)
        world.transcript.update { it.copy(run = RunFacts(busy = true)) }
    }

    @Test
    fun `B-CHAT-S24-60 a steering server offers the hold, and the note steers the turn once`() {
        running(steers = true)
        assertTrue(presenter.current().live!!.canStopAfterStep)
        presenter.stopAfterStep(note)
        presenter.stopAfterStep(note) // a second hold sends nothing more
        assertEquals(listOf("sendAgain $note"), world.calls)
        val live = presenter.current().live!!
        assertTrue(live.stoppingAfterStep)
        assertFalse(live.canStopAfterStep)
        assertEquals("", world.draft.value.text) // the box is left as it was
    }

    @Test
    fun `B-CHAT-S24-61 without steering the hold offers nothing and sends nothing`() {
        running(steers = false)
        assertFalse(presenter.current().live!!.canStopAfterStep)
        presenter.stopAfterStep(note)
        assertTrue(world.calls.isEmpty())
    }

    @Test
    fun `B-CHAT-S24-62 the waiting line clears when the turn ends`() {
        running(steers = true)
        presenter.stopAfterStep(note)
        world.transcript.update { it.copy(run = RunFacts(busy = false)) }
        assertNull(presenter.current().live)
        running(steers = true)
        val live = presenter.current().live!!
        assertFalse(live.stoppingAfterStep)
        assertTrue(live.canStopAfterStep)
    }

    @Test
    fun `the stop note is a control message, never drawn as the person's own bubble`() {
        running(steers = true)
        presenter.stopAfterStep(note)
        world.transcript.update {
            it.copy(rows = kotlinx.collections.immutable.persistentListOf(prompt("a", "hello"), prompt("b", note)))
        }
        assertEquals(listOf("hello"), presenter.current().rows.filterIsInstance<io.github.eslamasabry.oc.engine.transcript.state.PromptRow>().map { it.text })
        presenter.undoStopAfterStep() // the withdrawn note leaves nothing behind either
        assertEquals(1, presenter.current().rows.size)
    }
}
