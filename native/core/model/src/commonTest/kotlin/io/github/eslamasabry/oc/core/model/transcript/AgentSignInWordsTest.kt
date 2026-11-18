package io.github.eslamasabry.oc.core.model.transcript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-AGENT-189 (#137, build 10073): one rule for "the agent's sign-in is gone". Claude Code's own words for an expired
 * sign-in reached the owner as raw text because seven copies of the pattern missed them. Two meanings stay apart:
 * signed in once and ran out ("sign in again"), and never signed in ("sign in").
 */
class AgentSignInWordsTest {
    private val ownerText = "Unable to validate model: OAuth refresh token is no longer valid; run /login to re-authenticate"

    @Test
    fun `B-AGENT-189 Claude Code's refresh token notice is an expired sign-in`() {
        assertTrue(AgentSignInWords.expired(ownerText))
        assertTrue(AgentSignInWords.gone(ownerText))
        assertFalse(AgentSignInWords.neverSignedIn(ownerText))
    }

    @Test
    fun `B-AGENT-189 other wordings of a sign-in that ran out`() {
        val lapsed =
            listOf(
                "Failed to authenticate: OAuth session expired and could not be refreshed",
                "Your login session has expired",
                "token has been revoked",
                "OAuth refresh token is no longer valid",
                "The access token is no longer valid; please run /login to re-authenticate",
                "Error: invalid refresh token",
                "Please re-authenticate",
                "Your access token could not be refreshed because your refresh token was already used. " +
                    "Please log out and sign in again.",
            )
        for (text in lapsed) {
            assertTrue(AgentSignInWords.expired(text), text)
            assertTrue(AgentSignInWords.gone(text), text)
        }
    }

    @Test
    fun `B-AGENT-189 never signed in is gone but not expired, with or without please`() {
        for (text in listOf("Not logged in. Please run /login", "Not logged in · Run /login", "run /login")) {
            assertTrue(AgentSignInWords.gone(text), text)
            assertFalse(AgentSignInWords.expired(text), text)
        }
        assertTrue(AgentSignInWords.neverSignedIn("Not logged in. Please run /login"))
        assertFalse(AgentSignInWords.neverSignedIn(ownerText))
    }

    @Test
    fun `B-AGENT-189 a key refusal is gone, not expired, and ordinary words are neither`() {
        assertTrue(AgentSignInWords.gone("401 Unauthorized: invalid api key"))
        assertFalse(AgentSignInWords.expired("401 Unauthorized: invalid api key"))
        assertTrue(AgentSignInWords.gone("Failed to authenticate"))
        assertFalse(AgentSignInWords.expired("Failed to authenticate"))
        assertFalse(AgentSignInWords.gone("Unable to validate model: the model is overloaded"))
        assertFalse(AgentSignInWords.gone("ECONNRESET: fetch failed"))
        assertFalse(AgentSignInWords.expired("The file lib/main.dart does not exist"))
    }

    @Test
    fun `B-AGENT-189 every reader of the error text files the expired notice as a provider sign-in`() {
        assertEquals(
            MessageErrorKind.ProviderAuth,
            refineMessageErrorKind(MessageErrorKind.Unknown, ownerText),
        )
    }

    @Test
    fun `B-AGENT-189 another process refreshing the sign-in is not a lapse`() {
        val busy = "Failed to refresh OAuth token: another Claude Code process is refreshing it or exited mid-refresh."
        assertFalse(AgentSignInWords.expired(busy))
        assertFalse(AgentSignInWords.gone(busy))
    }

    @Test
    fun `B-AGENT-433 the rule's answer as a value keeps expired, never signed in and a refusal apart`() {
        assertEquals(AgentSignIn.Expired, AgentSignInWords.of(ownerText))
        assertEquals(
            AgentSignIn.Expired,
            AgentSignInWords.of("Failed to authenticate: OAuth session expired and could not be refreshed"),
        )
        assertEquals(AgentSignIn.NeverSignedIn, AgentSignInWords.of("Not logged in. Please run /login"))
        assertEquals(AgentSignIn.Refused, AgentSignInWords.of("401 Unauthorized: invalid api key"))
        assertEquals(AgentSignIn.Refused, AgentSignInWords.of("run /login"))
        assertNull(AgentSignInWords.of("Failed to refresh OAuth token: another Claude Code process is refreshing it"))
        assertNull(AgentSignInWords.of("Unable to validate model: the model is overloaded"))
        assertNull(AgentSignInWords.of(null))
    }
}
