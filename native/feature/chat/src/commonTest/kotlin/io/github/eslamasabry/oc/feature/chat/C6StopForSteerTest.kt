package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.update

/**
 * C6 (Flutter `_stopForSteer`): Stop on the steer note stops the step the message waits on, then the same words go
 * out again so the agent reads them at once; the stopped copy shows once. Too late, nothing is said twice.
 */
class C6StopForSteerTest {
    private val world = FakeChatWorld()
    private val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)

    private fun running(vararg oldestFirst: io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow) {
        world.transcript.update { stateOf(*oldestFirst, run = RunFacts(busy = true)) }
    }

    @Test
    fun `B-CHAT-C6-1 Stop stops the step, then sends the waiting words again, once, and hides the stopped copy`() {
        running(prompt("u1", "run the tests"), prompt("u2", "use the fast ones"))
        presenter.stopForSteer("prompt:u2", "use the fast ones")
        assertEquals(listOf("stop", "sendAgain use the fast ones"), world.calls)
        val keys = presenter.current().rows.map { it.key }
        assertFalse("prompt:u2" in keys, "the withdrawn copy is left out, the resent words show once")
        assertTrue("prompt:u1" in keys)
    }

    @Test
    fun `B-CHAT-C6-2 too late - the agent already answered the message - stops nothing more and sends nothing`() {
        running(prompt("u1", "run"), prompt("u2", "go on"), block("u2", "a2", "p2", 0, "reading it"))
        presenter.stopForSteer("prompt:u2", "go on")
        assertFalse(world.calls.any { it.startsWith("sendAgain") }, "no second copy")
        assertTrue("prompt:u2" in presenter.current().rows.map { it.key })
    }

    @Test
    fun `B-CHAT-C6-3 a stop the server never asked sends nothing`() {
        world.stopAsked = false
        running(prompt("u1", "run"), prompt("u2", "go on"))
        presenter.stopForSteer("prompt:u2", "go on")
        assertFalse(world.calls.any { it.startsWith("sendAgain") })
    }

    @Test
    fun `B-CHAT-C6-4 a refused stop says so and sends nothing`() {
        world.stopResult = io.github.eslamasabry.oc.core.common.Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        running(prompt("u1", "run"), prompt("u2", "go on"))
        presenter.stopForSteer("prompt:u2", "go on")
        assertFalse(world.calls.any { it.startsWith("sendAgain") })
        assertEquals(Failure.Network(NetworkWhy.Offline), presenter.current().status?.failure)
    }

    @Test
    fun `B-CHAT-C6-5 nothing runs, or the words are empty - nothing happens`() {
        world.transcript.update { stateOf(prompt("u1", "run"), prompt("u2", "go on"), run = RunFacts(busy = false)) }
        presenter.stopForSteer("prompt:u2", "go on")
        running(prompt("u1", "run"), prompt("u2", "  "))
        presenter.stopForSteer("prompt:u2", "  ")
        assertTrue(world.calls.isEmpty())
    }
}
