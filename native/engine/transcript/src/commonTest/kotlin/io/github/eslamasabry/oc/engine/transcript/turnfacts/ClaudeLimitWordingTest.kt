package io.github.eslamasabry.oc.engine.transcript.turnfacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** signin-followups: Claude Code's own limit sentences are usage limits, so "Switch model" and the limit words follow. */
class ClaudeLimitWordingTest {
    private val limitLines =
        listOf(
            "You've hit your weekly limit · resets Nov 20, 1am (UTC)",
            "You've hit your 5-hour limit · resets 3pm",
            "You've hit your session limit",
            "You've reached your usage limit",
            "You've reached your weekly usage limit. Resets Nov 20, 1am (UTC)",
        )

    @Test
    fun `SIGNIN-T-1 a limit sentence of the agent is a usage limit and the model is the way on`() {
        for (line in limitLines) {
            assertEquals(Cause.UsageLimit, causeOf(line), line)
            assertEquals(AgentRunFailure(AgentRunFailureKind.ModelUnavailable, out = true), agentRunFailure(line), line)
        }
    }

    @Test
    fun `SIGNIN-T-2 prose that only mentions a limit keeps its class`() {
        for (prose in
            listOf(
                "The limit of the buffer is 4096 bytes",
                "I reached your office, the speed limit there was 30",
                "Raise the usage limit in settings if you want more",
                "Consider setting your own limit",
            )) {
            assertNull(causeOf(prose), prose)
            assertEquals(AgentRunFailureKind.Unknown, classifyAgentRunFailure(prose), prose)
        }
    }

    @Test
    fun `SIGNIN-T-3 sign-in and busy sentences keep their class`() {
        assertEquals(AgentRunFailureKind.SignIn, classifyAgentRunFailure("Failed to authenticate: OAuth session expired"))
        assertEquals(AgentRunFailureKind.SignIn, classifyAgentRunFailure("Please run /login"))
        assertEquals(AgentRunFailureKind.SignIn, classifyAgentRunFailure("401 Unauthorized"))
        assertEquals(AgentRunFailureKind.Busy, classifyAgentRunFailure("429 Too Many Requests"))
        assertEquals(Cause.RateLimited, causeOf("Rate limit exceeded, backing off"))
        assertEquals(AgentRunFailureKind.NoCredit, classifyAgentRunFailure("Insufficient credits for this request"))
    }
}
