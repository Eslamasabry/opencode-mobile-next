package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.Busy
import io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.ConnectionLost
import io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.ModelUnavailable
import io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.NoCredit
import io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.SignIn
import io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind.Unknown
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ports Flutter test/agent_run_failure_test.dart "sorting a refusal". */
class AgentRunFailureTest {
    private val table: List<Pair<String, AgentRunFailureKind>> =
        listOf(
            "Authentication required" to SignIn,
            "AuthRequired" to SignIn,
            "Internal error: You need to sign in to use this model." to SignIn,
            "[System Error] Internal error: You need to sign in to use this model.\n\ncode: -32603" to SignIn,
            "Internal error: You need to sign in to use this model" to SignIn,
            "Failed to authenticate: OAuth session expired" to SignIn,
            "401 Unauthorized" to SignIn,
            "fx needs access. Run fx login to sign in" to SignIn,
            "Insufficient credits for this request" to NoCredit,
            "402 Payment Required" to NoCredit,
            "You exceeded your current quota, please check your plan" to NoCredit,
            "Model kilo/google/gemini-3-pro-image is not available on your plan" to ModelUnavailable,
            "Model not found: openai/gpt-5.6" to ModelUnavailable,
            "Unknown model gemini-9" to ModelUnavailable,
            "Free usage limit reached" to ModelUnavailable,
            "429 Too Many Requests" to Busy,
            "overloaded_error: Overloaded" to Busy,
            "The server is busy, try again later" to Busy,
            "API Error: Can't reach the API server — check your internet or DNS (EAI_AGAIN)" to ConnectionLost,
            "API Error: Request timed out." to ConnectionLost,
            "API Error: Connection error. (ECONNRESET)" to ConnectionLost,
            "API Error: 529 {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}" to Busy,
            "Agent a1 has no pending run" to Unknown,
            "" to Unknown,
        )

    @Test
    fun `B-AGENT-024 a failed turn's reason sorts into the kind a person can act on, as Flutter does`() {
        for ((reason, kind) in table) assertEquals(kind, classifyAgentRunFailure(reason), "\"$reason\"")
        assertEquals(Unknown, classifyAgentRunFailure(null))
    }

    @Test
    fun `B-AGENT-024 no credit is never busy, and a dropped connection is its own kind`() {
        assertEquals(NoCredit, classifyAgentRunFailure("Insufficient balance: billing required"))
        assertEquals(NoCredit, classifyAgentRunFailure("insufficient_quota"))
        assertEquals(ConnectionLost, classifyAgentRunFailure("socket hang up"))
        assertEquals(ConnectionLost, classifyAgentRunFailure("fetch failed: ECONNREFUSED"))
    }
}
