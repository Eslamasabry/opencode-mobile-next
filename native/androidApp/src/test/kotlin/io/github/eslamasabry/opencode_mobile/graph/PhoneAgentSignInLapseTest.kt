package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.protocol.paseo.PaseoAgentSignInTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Issue 147 (owner's phone, build 10045): Claude Code's sign-in could no longer refresh, yet the Agents page and the
 * chooser said Ready because `claude auth status` reads the stored sign-in as signed in. A run that reports the lapse
 * keeps the agent at Sign in needed, across app starts and every read, until a sign-in completes.
 */
class PhoneAgentSignInLapseTest {
    private val signedInRead =
        PhoneAgentFacts.of(mapOf("installed" to true, "foundVersion" to "2.1.0"), AuthProjection(AuthState.SignedIn))

    @Test
    fun `I147-AGY-7 a lapse a run reported outlives every read and an app start`() {
        val lapsed = PhoneAgentFacts.lapsed(mapOf("claude" to signedInRead), "claude", atMs = 1_000)
        assertTrue(lapsed.getValue("claude").lapsed)
        // The check reads "signed in" again: the lapse stands.
        val reread = PhoneAgentFacts.merge(lapsed, mapOf("claude" to signedInRead))
        assertTrue(reread.getValue("claude").lapsed)
        assertEquals(true, reread.getValue("claude").signedIn)
        val restarted = PhoneAgentFacts.decode(PhoneAgentFacts.encode(reread))
        assertEquals(1_000L, restarted.getValue("claude").lapsedAt)
    }

    @Test
    fun `I147-AGY-7b a completed sign-in ends it, and a failure older than that sign-in does not bring it back`() {
        val lapsed = PhoneAgentFacts.lapsed(mapOf("claude" to signedInRead), "claude", atMs = 1_000)
        val again = PhoneAgentFacts.signedInAgain(lapsed, "claude", atMs = 2_000)
        assertFalse(again.getValue("claude").lapsed)
        assertEquals(again, PhoneAgentFacts.lapsed(again, "claude", atMs = 1_500), "an old reply's failure is old news")
        assertTrue(PhoneAgentFacts.lapsed(again, "claude", atMs = 2_500).getValue("claude").lapsed)
        val restarted = PhoneAgentFacts.decode(PhoneAgentFacts.encode(again))
        assertEquals(2_000L, restarted.getValue("claude").signInDoneAt)
    }

    @Test
    fun `I147-AGY-7c a removal or a sign-out ends it, an install keeps it`() {
        val lapsed = PhoneAgentFacts.lapsed(mapOf("claude" to signedInRead), "claude", atMs = 1_000)
        assertTrue(
            PhoneAgentFacts.forget(lapsed, "claude", version = true, signIn = false).getValue("claude").lapsed
        )
        assertNull(
            PhoneAgentFacts.forget(lapsed, "claude", version = false, signIn = true, setUp = true)
                .getValue("claude").lapsedAt
        )
    }

    @Test
    fun `I147-AGY-7d only a create the helper refused for the sign-in counts as a lapse`() {
        assertTrue(FirstMessageRules.signInLapsed(Failure.Rejected(null, PaseoAgentSignInTag)))
        assertFalse(FirstMessageRules.signInLapsed(Failure.Rejected(null, WireTag("Paseo AgentRefused"))))
        assertFalse(FirstMessageRules.signInLapsed(Failure.ComputerDidNotAnswer()))
        assertFalse(FirstMessageRules.signInLapsed(null))
    }

    @Test
    fun `I147-AGY-7e the helper's sign-in refusal tag is the one New conversation reads as Sign in`() {
        assertEquals(
            io.github.eslamasabry.oc.feature.chats.presentation.PhoneStartRules.SIGN_IN_TAG,
            PaseoAgentSignInTag.raw,
        )
    }

    @Test
    fun `I147-AGY-9 an earlier start's record of other words is another message, the same words stay unknown`() {
        assertTrue(FirstMessageRules.otherMessage(recorded = "aaa", now = "bbb"))
        assertFalse(FirstMessageRules.otherMessage(recorded = "aaa", now = "aaa"))
        assertFalse(FirstMessageRules.otherMessage(recorded = null, now = "bbb"), "no digest proves nothing")
    }
}
