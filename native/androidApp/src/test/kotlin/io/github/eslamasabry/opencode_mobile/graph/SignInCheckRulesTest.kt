package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.agents.signin.SignInCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner, 2026-11-18: "Login successful." and the page said "isn't signed in yet". Three answers, never two. */
class SignInCheckRulesTest {
    @Test
    fun `B-AGENT-443 only the agent's own answer is signed in or out, everything else could not check`() {
        fun error(code: String) = mapOf("state" to "error", "error" to code)
        val signedIn = mapOf("state" to "signedIn", "accountDisplayName" to "a")
        assertEquals(SignInCheck.SignedIn, SignInCheckRules.answer(signedIn))
        assertEquals(SignInCheck.SignedOut, SignInCheckRules.answer(mapOf("state" to "signedOut")))
        assertEquals(SignInCheck.SignedOut, SignInCheckRules.answer(error("signInExpired")))
        for (code in listOf("agentBusy", "timedOut", "hostUnavailable", "invalidResponse", "invalidContext"))
            assertEquals(SignInCheck.CouldNotCheck(code), SignInCheckRules.answer(error(code)))
        // No answer from the bridge, or text that is not a fixed code: never shown, never "not signed in".
        assertEquals(SignInCheck.CouldNotCheck("noAnswer"), SignInCheckRules.answer(null))
        assertEquals(
            SignInCheck.CouldNotCheck("noAnswer"),
            SignInCheckRules.answer(error("token xyz leaked")),
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
