package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.agents.signin.SignInCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner, 2026-11-18: "Login successful." and the page said "isn't signed in yet". Three answers, never two. */
class SignInCheckRulesTest {
    @Test
    fun `B-AGENT-443 only the agent's own answer is signed in or signed out, everything else could not check`() {
        assertEquals(SignInCheck.SignedIn, SignInCheckRules.answer(mapOf("state" to "signedIn", "accountDisplayName" to "a")))
        assertEquals(SignInCheck.SignedOut, SignInCheckRules.answer(mapOf("state" to "signedOut")))
        assertEquals(SignInCheck.SignedOut, SignInCheckRules.answer(mapOf("state" to "error", "error" to "signInExpired")))
        for (code in listOf("agentBusy", "timedOut", "hostUnavailable", "invalidResponse", "invalidContext"))
            assertEquals(SignInCheck.CouldNotCheck(code), SignInCheckRules.answer(mapOf("state" to "error", "error" to code)))
        // No answer from the bridge, or text that is not a fixed code: never shown, never "not signed in".
        assertEquals(SignInCheck.CouldNotCheck("noAnswer"), SignInCheckRules.answer(null))
        assertEquals(
            SignInCheck.CouldNotCheck("noAnswer"),
            SignInCheckRules.answer(mapOf("state" to "error", "error" to "token sk-ant-xyz leaked")),
        )
    }

    @Test
    fun `B-AGENT-445 the retries are bounded and their pauses grow`() {
        assertEquals(4, SignInCheckRules.TRIES)
        val pauses = (0 until SignInCheckRules.TRIES).map(SignInCheckRules::pauseMillis)
        assertEquals(0L, pauses.first())
        assertTrue(pauses.zipWithNext().all { (a, b) -> b > a })
        assertTrue(pauses.sum() <= 15_000L)
    }
}
