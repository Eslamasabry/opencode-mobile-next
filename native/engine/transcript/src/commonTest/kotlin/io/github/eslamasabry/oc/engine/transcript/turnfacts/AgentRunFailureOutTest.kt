package io.github.eslamasabry.oc.engine.transcript.turnfacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-169 §1: which failed turns are "out" (a limit that waiting a minute will not lift), so the turn offers Switch
 * model.
 *
 * FIXTURES PENDING A LIVE CAPTURE: this environment could not run an OpenCode 2 server against a free OpenRouter or
 * Zen model long enough to hit a real limit, so the shapes below are the documented ones (OpenRouter's 429 body for
 * `free-models-per-min` / `free-models-per-day`, OpenCode Zen's `FreeUsageLimitError`, the `UsageLimitError` of plan
 * providers). Replace them with a captured `errorText` from `/session/{id}/message` once one exists.
 */
class AgentRunFailureOutTest {
    private val now = 1_762_600_000_000L

    /** OpenRouter, documented 429 for a free model's per-minute window (pending a live capture). */
    private val openRouterPerMinute =
        "AI_APICallError: Rate limit exceeded: free-models-per-min. " +
            "{\"error\":{\"message\":\"Rate limit exceeded: free-models-per-min. \",\"code\":429," +
            "\"metadata\":{\"headers\":{\"X-RateLimit-Limit\":\"20\",\"X-RateLimit-Remaining\":\"0\"," +
            "\"X-RateLimit-Reset\":\"${now + 40_000}\"}}}}"

    /** OpenRouter, documented 429 for a free model's daily window (pending a live capture). */
    private val openRouterPerDay =
        "AI_APICallError: Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model " +
            "requests per day. {\"error\":{\"code\":429,\"metadata\":{\"headers\":{\"X-RateLimit-Limit\":\"50\"," +
            "\"X-RateLimit-Remaining\":\"0\",\"X-RateLimit-Reset\":\"${now + 6 * 3_600_000}\"}}}}"

    /** OpenCode Zen free models (the shape the Flutter tests and RetryStatusTest already carry). */
    private val zenFree =
        "FreeUsageLimitError: Free usage limit reached. Subscribe to OpenCode Go to keep going: https://opencode.ai/go"

    @Test
    fun `B-169-001 a per-minute 429 is not out, so no switch is offered`() {
        val f = agentRunFailure(openRouterPerMinute, now)
        assertEquals(AgentRunFailureKind.Busy, f.kind)
        assertFalse(f.out)
        assertFalse(agentRunFailure("429 Too Many Requests", now).out)
        assertFalse(agentRunFailure("Rate limit reached. Please try again in 20s.", now).out)
        assertFalse(agentRunFailure("rate_limit_error: retry after 90 seconds", now).out)
    }

    @Test
    fun `B-169-002 a daily limit, a long wait or given-up retries is out`() {
        assertTrue(agentRunFailure(openRouterPerDay, now).out)
        assertTrue(agentRunFailure("Rate limit exceeded. Try again in 3 hours.", now).out)
        assertTrue(agentRunFailure("429 rate limit: retry after 600 seconds", now).out)
        assertTrue(agentRunFailure("Too many requests (max retries reached)", now).out)
        // An absolute reset only counts with a clock; then six hours away is out.
        val reset = "429 rate limited, x-ratelimit-reset: ${now / 1000 + 6 * 3600}"
        assertTrue(agentRunFailure(reset, now).out)
        assertFalse(agentRunFailure(reset, null).out)
    }

    @Test
    fun `B-169-003 usage, free-tier and credit limits are out, an overloaded provider is not`() {
        assertTrue(agentRunFailure(zenFree).out)
        assertEquals(AgentRunFailureKind.ModelUnavailable, agentRunFailure(zenFree).kind)
        assertTrue(agentRunFailure("UsageLimitError: usage limit reached for claude-opus").out)
        assertTrue(agentRunFailure("Daily limit reached").out)
        assertTrue(agentRunFailure("Insufficient credits for this request").out)
        assertEquals(AgentRunFailureKind.NoCredit, agentRunFailure("402 Payment Required").kind)
        assertFalse(agentRunFailure("overloaded_error: Overloaded").out)
        assertFalse(agentRunFailure("Model not found: openai/gpt-5.6").out)
        assertFalse(agentRunFailure("401 Unauthorized").out)
        assertFalse(agentRunFailure(null).out)
    }

    @Test
    fun `B-169-004 the kind stays what classifyAgentRunFailure says`() {
        for (r in listOf(openRouterPerMinute, openRouterPerDay, zenFree, "socket hang up", "Unknown model x"))
            assertEquals(classifyAgentRunFailure(r), agentRunFailure(r, now).kind, r)
    }
}
