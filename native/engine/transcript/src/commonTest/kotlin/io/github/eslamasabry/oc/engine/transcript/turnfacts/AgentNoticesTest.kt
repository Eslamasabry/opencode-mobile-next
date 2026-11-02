package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Port of test/paseo_compaction_test.dart's protocol-neutral rules (the Paseo item mapper is out of scope).
class AgentNoticesTest {
    private val kiloSignedOut =
        "[System Error] Internal error: You need to sign in to use this model.\n\ncode: -32603\n\n" +
            "Internal error: You need to sign in to use this model. | code=-32603 | " +
            "data={\"service\":\"session\",\"errorName\":\"APIError\"}"

    @Test
    fun `B-CHAT-069 a compaction is a notice in the transcript, not a reply`() {
        val done = compactionNoticeFacts(status = "completed", trigger = "manual", preTokens = null)
        assertEquals(CompactionNoticeFacts(CompactionState.Completed, CompactionTrigger.Manual, null), done)
        assertEquals("v2:compaction", COMPACTION_PART_TYPE)
        val running = compactionNoticeFacts(status = "running", trigger = "other", preTokens = 1200.0)
        assertEquals(CompactionNoticeFacts(CompactionState.Running, null, null), running)
        assertEquals(167_001L, compactionNoticeFacts("completed", "auto", 167_000.6).preTokens)
        assertNull(compactionNoticeFacts("completed", "auto", 0.0).preTokens)
    }

    @Test
    fun `B-CHAT-069 an expired sign-in is a sign-in error, its text kept for Details`() {
        val text = "Failed to authenticate: OAuth session expired and could not be refreshed"
        val cls = classifyAgentError(text)
        assertEquals(AgentErrorClass.SignIn, cls)
        assertTrue(isAgentErrorRow(cls))
        assertEquals(MessageErrorKind.ProviderAuth, agentErrorKind(cls))
    }

    @Test
    fun `B-CHAT-069 an agent asking to sign in is a sign-in error`() {
        val cls = classifyAgentError(kiloSignedOut)
        assertEquals(AgentErrorClass.SignIn, cls)
        assertEquals(MessageErrorKind.ProviderAuth, agentErrorKind(cls))
        // Outside the bridge's error report, an ask to sign in could be advice.
        assertEquals(AgentErrorClass.Other, classifyAgentError("You need to sign in to GitHub first, then push."))
    }

    @Test
    fun `B-CHAT-069 the helper own error report is an error row, not reply text`() {
        val cls = classifyAgentError("[System Error] Internal error: model overloaded\n\ncode: -32603")
        assertEquals(AgentErrorClass.HelperError, cls)
        assertTrue(isAgentErrorRow(cls))
        assertNull(agentErrorKind(cls))
    }

    @Test
    fun `B-CHAT-069 Claude Code failed-call line is a failed turn and words that mention it stay a reply`() {
        for (line in
            listOf(
                "API Error: Can't reach the API server — check your internet or DNS (EAI_AGAIN)",
                "API Error: 529 {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}",
                "API Error: Request timed out.",
                "Claude AI usage limit reached|1761836400",
                "Credit balance is too low",
            )
        ) {
            assertEquals(AgentErrorClass.TurnFailure, classifyAgentError(line), line)
        }
        val prose = classifyAgentError("The API Error: line you saw came from the proxy; I fixed it.")
        assertEquals(AgentErrorClass.Other, prose)
        assertFalse(isAgentErrorRow(prose))
    }

    @Test
    fun `B-CHAT-069 a busy refresh elsewhere is not a sign-in failure`() {
        val busy = "Failed to refresh OAuth token: another Claude Code process is refreshing it or exited mid-refresh."
        assertFalse(AgentSignInFailure.containsMatchIn(busy))
        assertEquals(AgentErrorClass.Other, classifyAgentError(busy))
    }
}
