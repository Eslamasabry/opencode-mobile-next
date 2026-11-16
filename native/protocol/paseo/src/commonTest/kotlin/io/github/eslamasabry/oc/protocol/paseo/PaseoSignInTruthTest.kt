package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoItemMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * signin-truth (owner, two phones, "Claude Code signed out"): a reply that merely quoted "not logged in", a bridge error
 * about a log file, a start refused for an "api key" or a "401", a usage limit: each could set the sticky "sign-in
 * lapsed" flag. Only an error from the agent's own failure channel with the sign-in meaning counts now. Fixtures are
 * shaped as the agents and the helper send them.
 */
class PaseoSignInTruthTest {
    private fun reply(text: String) =
        paseoItemMessage("agent-1", jsonOf("type" to "assistant_message", "text" to text), "a:1", "claude", 10, 10)!!

    private fun errorItem(message: String) =
        paseoItemMessage("agent-1", jsonOf("type" to "error", "message" to message), "e:1", "claude", 10, 10)!!

    private fun lapses(text: String) = reply(text).info.errorKind == MessageErrorKind.ProviderAuth

    @Test
    fun `I147-TRUTH-10 the agent's own sign-in failure lines are a lapse`() {
        val real =
            listOf(
                // Claude Code 2.x, an expired OAuth token (the turn ends on this line).
                "Failed to authenticate. API Error: 401 {\"type\":\"error\",\"error\":{\"type\":\"authentication_error\"," +
                    "\"message\":\"OAuth token has expired. Please obtain a new token or refresh your existing token.\"}," +
                    "\"request_id\":\"req_011\"}",
                "API Error: 401 {\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}",
                "Invalid API key · Please run /login",
                "Not logged in · Please run /login",
                "OAuth session expired and could not be refreshed",
                "Failed to authenticate: OAuth session expired and could not be refreshed",
                // Paseo's ACP bridge, Kilo signed out.
                "[System Error] Internal error: You need to sign in to use this model.\n\ncode: -32603",
                "[System Error] Authentication required\n\ncode: -32000",
            )
        for (text in real) {
            assertTrue(lapses(text), text)
            assertTrue(reply(text).parts.isEmpty(), "an error row, its words only under Details: $text")
        }
        // The agent's error channel itself.
        assertEquals(MessageErrorKind.ProviderAuth, errorItem("OAuth token has expired").info.errorKind)
        assertEquals(MessageErrorKind.ProviderAuth, errorItem("Not logged in · Please run /login").info.errorKind)
    }

    @Test
    fun `I147-TRUTH-11 prose that quotes the words, and a bridge error about a log, are never a lapse`() {
        val prose =
            listOf(
                "The error `Not logged in · Please run /login` means the CLI has no stored sign-in.",
                "I searched the code for \"authentication_error\" and \"OAuth token has expired\": both come from the API.",
                "When the phone says not logged in, open the Agents page.",
                "Here is the fix:\n```kotlin\nif (text.contains(\"Invalid API key\")) lapse()\n```",
                "You can sign in later.",
            )
        for (text in prose) {
            assertFalse(lapses(text), text)
            assertNotNull(reply(text).parts.singleOrNull(), "a reply stays a reply: $text")
        }
        val logFile = reply("[System Error] Internal error: could not write the log in /tmp/agent\n\ncode: -32603")
        assertTrue(logFile.parts.isEmpty(), "still the bridge's error row")
        assertNotEquals(MessageErrorKind.ProviderAuth, logFile.info.errorKind)
        assertNotEquals(MessageErrorKind.ProviderAuth, errorItem("Request timed out after 600000ms").info.errorKind)
        // A long reply that happens to open with the words is prose.
        assertFalse(lapses("Not logged in is what the CLI prints. " + "Then it explains the rest. ".repeat(100)))
    }

    @Test
    fun `I147-TRUTH-12 a usage limit, a rate limit or an overload has its own words, never signed out`() {
        val limits =
            listOf(
                "Claude AI usage limit reached|1763330400",
                // Claude Code 2.x on the Galaxy (build 10050): it read as an ordinary reply, and the agent as signed out.
                "You've hit your weekly limit · resets Nov 20, 1am (UTC)",
                "You’ve hit your session limit · resets 3pm (Europe/Berlin)",
                "API Error: 429 {\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"This request " +
                    "would exceed your account's rate limit. Please try again later.\"}}",
                "API Error: 529 {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}",
                "Credit balance is too low",
            )
        for (text in limits) {
            val row = reply(text)
            assertTrue(row.parts.isEmpty(), "an error row: $text")
            assertNotEquals(MessageErrorKind.ProviderAuth, row.info.errorKind, text)
            assertEquals(text, row.info.errorText, "the words that pick the limit's own sentence are kept: $text")
        }
        assertNotEquals(
            MessageErrorKind.ProviderAuth,
            errorItem("Rate limited while refreshing the sign-in: 429 Too Many Requests, log in again later").info.errorKind,
        )
    }

    @Test
    fun `I147-TRUTH-13 a create refused for a real sign-in failure is tagged, loose words and not-ready starts are not`() {
        for (reason in
            listOf(
                "Failed to authenticate: OAuth session expired and could not be refreshed",
                "Authentication required",
                "Not logged in · Please run /login",
                "Invalid API key · Please run /login",
                "Agent requires login: run `codex login` first",
            ))
            assertTrue(paseoAgentSignInText(reason), reason)
        for (reason in
            listOf(
                "Provider claude is not ready",
                "No model selected",
                "Missing ANTHROPIC_API_KEY for the custom endpoint",
                "Request failed with status code 401 from the model list",
                "Could not read the log in the working directory",
                "Claude AI usage limit reached|1763330400",
                "You've hit your weekly limit · resets Nov 20, 1am (UTC)",
                "429 Too Many Requests: rate limit while authenticating",
                "Working directory does not exist or is not a directory",
            ))
            assertFalse(paseoAgentSignInText(reason), reason)
    }
}
