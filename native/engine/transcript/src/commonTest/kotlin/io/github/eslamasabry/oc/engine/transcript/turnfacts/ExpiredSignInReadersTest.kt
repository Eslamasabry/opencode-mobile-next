package io.github.eslamasabry.oc.engine.transcript.turnfacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-AGENT-189: the readers in this module use the one shared sign-in rule, so Claude Code's refresh notice is a sign-in. */
class ExpiredSignInReadersTest {
    private val ownerText = "Unable to validate model: OAuth refresh token is no longer valid; run /login to re-authenticate"

    @Test
    fun `B-AGENT-189 a reply that is the refresh token notice is a sign-in error row`() {
        assertEquals(AgentErrorClass.SignIn, classifyAgentError(ownerText))
        assertTrue(agentSignInFailure(ownerText))
    }

    @Test
    fun `B-AGENT-189 a failed turn carrying the refresh token notice is a sign-in failure`() {
        assertEquals(AgentRunFailureKind.SignIn, classifyAgentRunFailure(ownerText))
    }
}
