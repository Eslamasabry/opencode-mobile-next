package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.world.AgentSetUpNeed
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * B-AGENT-113 (set up at first use, the chat seam): Qwen Code, Goose and omp cannot report their sign-in ahead of time,
 * so the truth comes from the agent's own answer when a conversation starts. A send that failed because the agent was
 * never set up says "<agent> needs setting up before it can answer" and offers "Set up <agent>" (the Agents page's set-up
 * terminal) instead of Try again; the typed words stay on the bubble and nothing is resent on its own. The agent comes
 * from the conversation itself (its runtime), never from a reply row.
 */
@OptIn(RedactorApi::class)
class AgentSetUpAtFirstUseTest {
    /** A phone agents' conversation: it lives on the helper profile, the owner is the phone's. */
    private val helperRef = ConversationRef(ProfileId("phone-agents"), SessionId("draft-1"))
    private val owner = ProfileId("phone")

    private class Outside(private val owner: ProfileId?) : ChatOutside {
        val setUp = mutableSetOf<String>()
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

        override fun phoneAgentOwner(ref: ConversationRef): ProfileId? = owner

        override fun agentsSetUp(owner: ProfileId): Set<String> = setUp.toSet()

        override fun agentsSetUpChanges(owner: ProfileId): Flow<Unit> = changes

        override fun mask(text: String): String = text

        override fun copy(text: String): Boolean = false

        override fun share(text: String): Boolean = false

        override fun checkLink(url: String): LinkCheck = LinkCheck.Refused

        override fun openLink(url: String): Boolean = false
    }

    private class AgentWorld(ref: ConversationRef, var runtime: String?) : FakeChatWorld(ref) {
        override fun runtimeId(): String? = runtime
    }

    private val nav = RecordingNavigator()

    private fun failed(text: String, failure: Failure, state: PendingState = PendingState.Failed, id: Long = 4) =
        PendingSend(id, text, state, failure = failure)

    private fun said(text: String) = Redacted(text)

    private fun chat(runtime: String?, bubble: PendingSend, outside: Outside = Outside(owner)): Triple<ChatPresenter, AgentWorld, Outside> {
        val world = AgentWorld(helperRef, runtime)
        world.pending.value = persistentListOf(bubble)
        return Triple(ChatPresenter(world, nav, Immediate, outside = outside), world, outside)
    }

    @Test
    fun `B-AGENT-113 Qwen Code refused before it started says it needs setting up and opens its set-up terminal`() {
        val (p, world, _) =
            chat("qwen", failed("fix the tests", Failure.Unavailable(FeatureId("agentStart"), said("AuthRequired"))))
        val shown = p.current().pending.single()
        assertEquals(AgentSetUpNeed("qwen", "Qwen Code"), shown.setUp)
        assertEquals("fix the tests", shown.text, "the typed words stay on the bubble")
        assertEquals(PendingState.Failed, shown.state, "not shown as sent")
        assertNotEquals(ChatStatusKind.SendFailed, p.current().status?.kind, "no Not sent / Try again over the chat")
        p.setUpAgent(shown.setUp!!.agentId)
        assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "qwen")), nav.opened.toList())
        assertEquals(emptyList<String>(), world.calls.toList(), "nothing is resent on its own")
    }

    @Test
    fun `B-AGENT-113 Qwen Code's own refusal after the prompt went out reads the same`() {
        val (p, _, _) =
            chat(
                "qwen",
                failed(
                    "hello",
                    Failure.ComputerDidNotAnswer(said("Authentication required. Use Qwen Code CLI to authenticate first.")),
                    PendingState.Unconfirmed,
                ),
            )
        assertEquals(AgentSetUpNeed("qwen", "Qwen Code"), p.current().pending.single().setUp)
    }

    @Test
    fun `B-AGENT-113 a Goose never set up needs setting up, and after its set-up the same failure is a plain Try again`() {
        val bubble = failed("hi goose", Failure.ComputerDidNotAnswer(said("Internal error")), PendingState.Unconfirmed)
        val (p, _, outside) = chat("goose", bubble)
        assertEquals(AgentSetUpNeed("goose", "Goose"), p.current().pending.single().setUp)
        // The set-up terminal ended: back in the chat, the bubble keeps its words and offers its normal action.
        outside.setUp += "goose"
        val after = p.current().pending.single()
        assertNull(after.setUp)
        assertEquals("hi goose", after.text)
        assertEquals(emptyList<AppRoute>(), nav.opened.toList())
    }

    @Test
    fun `B-AGENT-113 a Goose start that fails after its set-up is a plain failure`() {
        val outside = Outside(owner).apply { setUp += "goose" }
        val (p, _, _) = chat("goose", failed("hi", Failure.ComputerDidNotAnswer(said("Internal error")), PendingState.Unconfirmed), outside)
        assertNull(p.current().pending.single().setUp)
    }

    @Test
    fun `B-AGENT-113 after setting up Qwen Code the failed bubble offers Try again, and the next failure's text decides`() {
        val (p, world, outside) = chat("qwen", failed("go", Failure.Unavailable(FeatureId("agentStart"), said("AuthRequired"))))
        assertEquals("qwen", p.current().pending.single().setUp?.agentId)
        outside.setUp += "qwen"
        assertNull(p.current().pending.single().setUp, "set up since this failure: the normal Try again")
        p.retrySend(4)
        assertEquals(listOf("retrySend 4"), world.calls)
        // Tried again and refused again with the same words: the agent still needs setting up.
        world.pending.value = persistentListOf(failed("go", Failure.Unavailable(FeatureId("agentStart"), said("AuthRequired"))).copy(state = PendingState.Sending))
        p.current()
        world.pending.value = persistentListOf(failed("go", Failure.Unavailable(FeatureId("agentStart"), said("AuthRequired"))))
        assertEquals("qwen", p.current().pending.single().setUp?.agentId)
    }

    @Test
    fun `B-AGENT-113 omp without a model needs setting up on its first answer`() {
        val world = AgentWorld(helperRef, "omp-acp")
        world.transcript.value = world.transcript.value.copy(rows = persistentListOf(prompt("u1", "first")))
        world.pending.value =
            persistentListOf(failed("again", Failure.ComputerDidNotAnswer(said("No model selected.")), PendingState.Unconfirmed))
        val p = ChatPresenter(world, nav, Immediate, outside = Outside(owner))
        assertEquals(AgentSetUpNeed("omp-acp", "Oh My Pi"), p.current().pending.single().setUp)
    }

    @Test
    fun `B-AGENT-113 an agent the app can check keeps today's failure words`() {
        val (p, _, _) =
            chat("codex", failed("hi", Failure.Unavailable(FeatureId("agentStart"), said("Authentication required"))))
        assertNull(p.current().pending.single().setUp)
    }

    @Test
    fun `B-AGENT-113 an ordinary failure, an unknown agent or a server without phone agents keeps today's words`() {
        val bubble = failed("hi", Failure.Unavailable(FeatureId("agentStart"), said("AuthRequired")))
        assertNull(chat("qwen", failed("hi", Failure.Unavailable(FeatureId("agentStart"), said("disconnected")))).first.current().pending.single().setUp)
        assertNull(chat(null, bubble).first.current().pending.single().setUp, "the conversation's agent is not known")
        assertNull(chat("qwen", bubble, Outside(null)).first.current().pending.single().setUp, "not this phone's agents")
        // A send still on its way is never read.
        assertNull(chat("qwen", bubble.copy(state = PendingState.Sending)).first.current().pending.single().setUp)
        // An unconfirmed send that may have arrived (no answer of the agent's own) keeps its own words.
        assertNull(chat("goose", bubble.copy(state = PendingState.Unconfirmed, failure = Failure.ComputerDidNotAnswer())).first.current().pending.single().setUp)
        // Without a phone owner, Set up opens nothing.
        val (p, _, _) = chat("qwen", bubble, Outside(null))
        p.setUpAgent("qwen")
        assertEquals(emptyList<AppRoute>(), nav.opened.toList())
    }
}
