package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.world.StoppedPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * B8 (gate): "Send again" under a reply the server lost sends the newest prompt once, with its files, on the
 * conversation's current agent (Flutter `_retryLast`: returns while sending, carries the prompt's files, sends with
 * the conversation's agent).
 */
class B8SendAgainTest {
    private val photo = PromptAttachment("image/png", "shot.png", "data:image/png;base64,AAAA")

    private inner class World : FakeChatWorld() {
        val prompts = HashMap<String, StoppedPrompt>()
        val resent = mutableListOf<StoppedPrompt>()
        var fail: Failure? = null

        override fun stoppedPrompt(turnId: String): StoppedPrompt? = prompts[turnId]

        override suspend fun sendAgainWith(prompt: StoppedPrompt): Outcome<Unit> {
            calls += "sendAgainWith ${prompt.text}"
            resent += prompt
            return fail?.let { Outcome.Failed(it) } ?: Outcome.Ok(Unit)
        }
    }

    private val world = World()
    private val presenter = ChatPresenter(world, RecordingNavigator(), Immediate)

    @Test
    fun `B8 Send again sends the newest prompt with its files and mention, on the current agent`() {
        world.transcript.value = stateOf(prompt("u1", "fix the build @explore"))
        world.prompts["u1"] = StoppedPrompt("u1", "fix the build @explore", listOf(photo), agent = "plan")
        presenter.retryLastPrompt()
        val sent = world.resent.single()
        assertEquals("fix the build @explore", sent.text)
        assertEquals(listOf(photo), sent.attachments)
        assertNull(sent.agent, "Send again keeps the conversation's agent, as Flutter does")
    }

    @Test
    fun `B8 a second tap before the copy lands sends nothing more`() {
        world.transcript.value = stateOf(prompt("u1", "write the list"))
        world.prompts["u1"] = StoppedPrompt("u1", "write the list")
        presenter.retryLastPrompt()
        presenter.retryLastPrompt()
        assertEquals(1, world.calls.count { it.startsWith("sendAgain") })
        // Once the copy lands it is the newest prompt, and its own Send again works again.
        world.transcript.value = stateOf(prompt("u1", "write the list"), prompt("u2", "write the list"))
        presenter.retryLastPrompt()
        assertEquals(2, world.calls.count { it.startsWith("sendAgain") })
    }

    @Test
    fun `B8 a send that failed can be tried again`() {
        world.transcript.value = stateOf(prompt("u1", "write the list"))
        world.prompts["u1"] = StoppedPrompt("u1", "write the list")
        world.fail = Failure.Server(503)
        presenter.retryLastPrompt()
        world.fail = null
        presenter.retryLastPrompt()
        assertEquals(2, world.resent.size)
    }

    @Test
    fun `B8 without the prompt's record the words alone are sent, as before`() {
        world.transcript.value = stateOf(prompt("u1", "write the list"))
        presenter.retryLastPrompt()
        assertEquals(listOf("sendAgain write the list"), world.calls.filter { it.startsWith("sendAgain") })
    }
}
