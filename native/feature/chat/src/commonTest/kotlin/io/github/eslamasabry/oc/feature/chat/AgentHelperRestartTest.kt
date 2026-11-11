package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A chat on the phone agents' server whose helper has died: the connection line offers Restart agent, and
 * Try now / Retry start the helper first instead of failing against a dead port. Only on a tap, never by itself.
 */
class AgentHelperRestartTest {
    private class HelperWorld : FakeChatWorld() {
        var helper = true
        var startResult: Outcome<Unit> = Outcome.Ok(Unit)
        override val serverIsAgentHelper: Boolean
            get() = helper

        override fun tryLinkNow() {
            calls += "tryLinkNow"
        }

        override suspend fun startAgentHelper(): Outcome<Unit> {
            calls += "startHelper"
            return startResult
        }
    }

    private val world = HelperWorld().also { it.link.value = ChatLink.Offline }
    private val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)

    @Test
    fun `B-CHAT-AGENT-01 the dropped line offers Restart agent only on the helper's server`() {
        assertTrue(assertNotNull(presenter.current().status).canRestartAgent)
        world.helper = false
        assertFalse(assertNotNull(presenter.current().status).canRestartAgent)
    }

    @Test
    fun `B-CHAT-AGENT-02 Restart agent starts the helper and then reconnects, without a confirm`() {
        presenter.restartAgent()
        assertEquals(listOf("startHelper", "tryLinkNow"), world.calls)
        assertEquals(null, presenter.current().confirm)
    }

    @Test
    fun `B-CHAT-AGENT-03 Try now and Retry on that server start a stopped helper first`() {
        presenter.tryLinkNow()
        assertEquals(listOf("startHelper", "tryLinkNow"), world.calls)
        world.calls.clear()
        presenter.retryLoad()
        assertEquals("startHelper", world.calls.first())
    }

    @Test
    fun `B-CHAT-AGENT-04 a helper that will not start says so, reconnects nothing and offers the action again`() {
        world.startResult = Outcome.Failed(Failure.Unavailable(FeatureId("startAgentHelper")))
        presenter.restartAgent()
        assertEquals(listOf("startHelper"), world.calls)
        val line = assertNotNull(presenter.current().status)
        assertEquals(ChatStatusKind.Offline, line.kind)
        assertTrue(line.agentFailed)
        assertTrue(line.canRestartAgent)
        assertFalse(line.startingAgent)
    }

    @Test
    fun `B-CHAT-AGENT-05 any other server reconnects without touching a helper`() {
        world.helper = false
        presenter.tryLinkNow()
        presenter.restartAgent()
        assertEquals(listOf("tryLinkNow"), world.calls)
    }
}
