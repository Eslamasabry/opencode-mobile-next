package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.world.AgentSetUpNeed
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-AGENT-113 (set up at first use, the reply seam): omp opens its conversation without credentials and its first answer
 * fails ("No model selected.", "No API key found for …"), so the failure is a failed reply, not a failed send. Such a
 * reply of a set-up-at-first-use agent reads "<agent> needs setting up before it can answer" with "Set up <agent>" — for
 * the conversation's first answer, or any answer while the agent is still not set up. Once its set-up is marked, the
 * same failure is the plain reply failure. Agents the app can check keep today's words.
 *
 * B-AGENT-189 follow-up: the chat's "Sign in again" opens the sign-in terminal for the phone's owner when the
 * conversation runs on the phone's agents host (its `<owner>-agents` profile, which that terminal refuses).
 */
class AgentSetUpReplyTest {
    private val helperRef = ConversationRef(ProfileId("phone-agents"), SessionId("agent-1"))
    private val owner = ProfileId("phone")

    private class Outside(private val owner: ProfileId?, private val phoneServer: Boolean = true) : ChatOutside {
        val setUp = mutableSetOf<String>()

        override fun isPhoneServer(ref: ConversationRef): Boolean = phoneServer

        override fun phoneAgentOwner(ref: ConversationRef): ProfileId? = owner

        override fun agentsSetUp(owner: ProfileId): Set<String> = setUp.toSet()

        override fun mask(text: String): String = text

        override fun copy(text: String): Boolean = false

        override fun share(text: String): Boolean = false

        override fun checkLink(url: String): LinkCheck = LinkCheck.Refused

        override fun openLink(url: String): Boolean = false
    }

    private class AgentWorld(ref: ConversationRef, val runtime: String?) : FakeChatWorld(ref) {
        override fun runtimeId(): String? = runtime
    }

    private val nav = RecordingNavigator()

    private fun failedReply(turn: String, reason: String, provider: String?) =
        FooterRow("footer:$turn", turn, failed = true, reason = reason, providerId = provider)

    /** Newest first: the failed reply of [turn] under its prompt, after [earlier] turns. */
    private fun rows(turn: String, reason: String, provider: String?, earlier: List<TranscriptRow> = emptyList()) =
        (listOf(failedReply(turn, reason, provider), prompt(turn, "hello")) + earlier).toImmutableList()

    private fun chat(runtime: String?, rows: List<TranscriptRow>, outside: Outside = Outside(owner)): ChatPresenter {
        val world = AgentWorld(helperRef, runtime)
        world.transcript.value = world.transcript.value.copy(rows = rows.toImmutableList())
        return ChatPresenter(world, nav, Immediate, outside = outside)
    }

    @Test
    fun `B-AGENT-113 omp's first answer without a model or key needs setting up`() {
        for (said in listOf("No model selected.\n\nUse /login to choose one.", "No API key found for anthropic.")) {
            val ui = chat("omp-acp", rows("u1", said, "omp-acp")).current()
            assertEquals(mapOf("u1" to AgentSetUpNeed("omp-acp", "Oh My Pi")), ui.setUpReplies.toMap(), said)
            assertTrue(ui.rows.any { it.key == "prompt:u1" }, "the person's message stays as sent")
        }
    }

    @Test
    fun `B-AGENT-113 after omp's set-up is marked the same failure is the plain reply failure`() {
        val outside = Outside(owner)
        val p = chat("omp-acp", rows("u1", "No model selected.", "omp-acp"), outside)
        assertEquals(setOf("u1"), p.current().setUpReplies.keys)
        outside.setUp += "omp-acp"
        assertTrue(p.current().setUpReplies.isEmpty())
    }

    @Test
    fun `B-AGENT-113 a later answer needs setting up only while omp is still not set up`() {
        val earlier = listOf<TranscriptRow>(footer("u1"), prompt("u1", "first"))
        val notSetUp = chat("omp-acp", rows("u2", "No API key found for openai.", "omp-acp", earlier)).current()
        assertEquals(setOf("u2"), notSetUp.setUpReplies.keys)
        val setUp = Outside(owner).apply { this.setUp += "omp-acp" }
        assertTrue(chat("omp-acp", rows("u2", "No API key found for openai.", "omp-acp", earlier), setUp).current().setUpReplies.isEmpty())
        // The conversation's first answer still says so, whatever was set up before.
        val first = Outside(owner).apply { this.setUp += "omp-acp" }
        assertEquals(setOf("u1"), chat("omp-acp", rows("u1", "No model selected.", "omp-acp"), first).current().setUpReplies.keys)
    }

    @Test
    fun `B-AGENT-113 Qwen Code and Goose replies refused for sign-in get the same line`() {
        assertEquals(
            AgentSetUpNeed("qwen", "Qwen Code"),
            chat("qwen", rows("u1", "Authentication required. Use Qwen Code CLI to authenticate first.", "qwen")).current().setUpReplies["u1"],
        )
        assertEquals(AgentSetUpNeed("goose", "Goose"), chat("goose", rows("u1", "auth_required", "goose")).current().setUpReplies["u1"])
        // Goose's answer failing for another reason is a plain failed reply.
        assertTrue(chat("goose", rows("u1", "Internal error", "goose")).current().setUpReplies.isEmpty())
    }

    @Test
    fun `B-AGENT-113 a Codex reply failure keeps today's words`() {
        assertTrue(chat("codex", rows("u1", "Authentication required", "codex")).current().setUpReplies.isEmpty())
        assertTrue(chat(null, rows("u1", "Authentication required", "codex")).current().setUpReplies.isEmpty())
        // Not this phone's agents: nothing to set up from here.
        assertTrue(chat("omp-acp", rows("u1", "No model selected.", "omp-acp"), Outside(null)).current().setUpReplies.isEmpty())
    }

    @Test
    fun `B-AGENT-113 Set up on a failed reply opens omp's set-up terminal for the phone owner`() {
        val p = chat("omp-acp", rows("u1", "No model selected.", "omp-acp"))
        p.setUpAgent(p.current().setUpReplies.getValue("u1").agentId)
        assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "omp-acp")), nav.opened.toList())
    }

    @Test
    fun `B-AGENT-189 Sign in again in a phone agents conversation opens the terminal for the phone owner`() {
        val p = ChatPresenter(AgentWorld(helperRef, "claude"), nav, Immediate, outside = Outside(owner))
        assertTrue(p.phoneAgentSignIn())
        p.openAgentSignIn("claude")
        assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "claude")), nav.opened.toList())
    }

    @Test
    fun `B-AGENT-189 Sign in again elsewhere is unchanged`() {
        // A conversation on the phone's own server keeps its own profile (it is the owner).
        val own = ConversationRef(owner, SessionId("s1"))
        ChatPresenter(AgentWorld(own, null), nav, Immediate, outside = Outside(null)).openAgentSignIn("codex")
        assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "codex")), nav.opened.toList())
        // A conversation on a computer opens nothing and does not offer it.
        val computer = ChatPresenter(AgentWorld(Ref, null), nav, Immediate, outside = Outside(null, phoneServer = false))
        assertFalse(computer.phoneAgentSignIn())
        computer.openAgentSignIn("codex")
        assertEquals(1, nav.opened.size)
        assertNull(nav.opened.drop(1).firstOrNull())
    }
}
