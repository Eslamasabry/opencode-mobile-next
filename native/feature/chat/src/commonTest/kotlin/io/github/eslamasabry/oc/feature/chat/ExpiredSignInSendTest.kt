package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.presentation.SendFailureRules
import io.github.eslamasabry.oc.feature.chat.world.AgentSignInNeed
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-AGENT-189 (build 10073): Claude Code's own "OAuth refresh token is no longer valid; run /login" came back through the
 * phone's agents helper and was filed as "couldn't reach your computer". A failed send whose failure carries the agent's
 * words for a sign-in that ran out is read as that, first: the bubble names the agent and offers its sign-in; the other
 * meaning (never signed in) and ordinary failures are not touched. The kept words are never resent by it.
 */
@OptIn(RedactorApi::class)
class ExpiredSignInSendTest {
    private val ownerText = "Unable to validate model: OAuth refresh token is no longer valid; run /login to re-authenticate"
    private val owner = ProfileId("phone")
    private val ref = ConversationRef(ProfileId("phone-agents"), SessionId("draft-1"))

    private class Outside(private val owner: ProfileId?) : ChatOutside {
        override fun phoneAgentOwner(ref: ConversationRef): ProfileId? = owner

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

    private fun chat(
        runtime: String?,
        bubble: PendingSend,
        outside: Outside = Outside(owner),
    ): Pair<ChatPresenter, AgentWorld> {
        val world = AgentWorld(ref, runtime)
        world.pending.value = persistentListOf(bubble)
        return ChatPresenter(world, nav, Immediate, outside = outside) to world
    }

    private fun failed(text: String, state: PendingState = PendingState.Failed, id: Long = 4) =
        PendingSend(id, text, state, failure = Failure.ComputerDidNotAnswer(Redacted(text)))

    @Test
    fun `B-AGENT-189 a send refused with the refresh token notice names the agent and opens its sign-in`() {
        val (p, world) = chat("claude", PendingSend(4, "Whats next", PendingState.Failed, Failure.ComputerDidNotAnswer(Redacted(ownerText))))
        val shown = p.current().pending.single()
        assertEquals(AgentSignInNeed("claude", "Claude Code"), shown.signIn)
        assertEquals("Whats next", shown.text, "the typed words stay on the bubble")
        assertNotEquals(ChatStatusKind.SendFailed, p.current().status?.kind, "no second 'not sent / Try again' line")
        p.openAgentSignIn(shown.signIn!!.providerId)
        assertEquals(listOf<AppRoute>(AgentsRoute.SignInTerminal(owner, "claude")), nav.opened.toList())
        assertEquals(emptyList<String>(), world.calls.toList(), "nothing is resent on its own")
    }

    @Test
    fun `B-AGENT-189 the same notice after the prompt went out reads the same, and anywhere else too`() {
        val (phone, _) = chat("claude", failed(ownerText, PendingState.Unconfirmed))
        assertEquals("Claude Code", phone.current().pending.single().signIn?.name)
        val (remote, _) = chat("claude", failed(ownerText), Outside(null))
        assertEquals("Claude Code", remote.current().pending.single().signIn?.name)
    }

    @Test
    fun `B-AGENT-189 never signed in and ordinary failures are not an expired sign-in`() {
        val (never, _) = chat("claude", failed("Not logged in. Please run /login"))
        assertNull(never.current().pending.single().signIn, "that is the other meaning: sign in, not sign in again")
        val (plain, _) = chat("claude", failed("ECONNREFUSED 127.0.0.1:6767"))
        assertNull(plain.current().pending.single().signIn)
        assertEquals(ChatStatusKind.SendFailed, plain.current().status?.kind)
        val (unknownAgent, _) = chat(null, failed(ownerText))
        assertNull(unknownAgent.current().pending.single().signIn, "no agent to name: the ordinary words")
    }

    @Test
    fun `B-AGENT-189 the helper's answer carrying the agent's sign-in words is read as a sign-in before any can't-reach words`() {
        assertTrue(SendFailureRules.signInRanOut(Failure.ComputerDidNotAnswer(Redacted(ownerText))))
        assertFalse(SendFailureRules.signInRanOut(Failure.ComputerDidNotAnswer(Redacted("Not logged in. Please run /login"))))
        assertFalse(SendFailureRules.signInRanOut(Failure.ComputerDidNotAnswer()))
        assertFalse(SendFailureRules.signInRanOut(Failure.ComputerDidNotAnswer(Redacted("ECONNREFUSED"))))
    }

    @Test
    fun `B-CHAT-071 the banner under the header is only for a failed message out of sight`() {
        val key = "pending:4"
        assertFalse(SendFailureRules.bannerNeeded(key, listOf("pending:4", "row:1")), "the bubble is on screen and says it")
        assertTrue(SendFailureRules.bannerNeeded(key, listOf("row:3", "row:2")), "scrolled away: the banner tells")
        assertFalse(SendFailureRules.bannerNeeded(key, emptyList()), "nothing laid out yet is not scrolled away")
    }
}
