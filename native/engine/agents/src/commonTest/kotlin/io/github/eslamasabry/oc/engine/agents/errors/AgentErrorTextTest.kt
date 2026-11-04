package io.github.eslamasabry.oc.engine.agents.errors

import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorLanguage.Ar
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorLanguage.En
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentErrorTextTest {
    private val reset = "ECONNRESET: The socket connection was closed unexpectedly. For more " +
        "information, pass `verbose: true` in the second argument to fetch()"

    private fun classify(raw: String) = AgentErrorText.classify(raw)

    @Test
    fun `B-AGENT-012 runtime errors are recognised by cause`() {
        val cases = mapOf(
            reset to AgentErrorCause.ConnectionDropped,
            "socket hang up" to AgentErrorCause.ConnectionDropped,
            "WebSocket inbound queue overflow" to AgentErrorCause.RequestTooLarge,
            "413 Payload Too Large" to AgentErrorCause.RequestTooLarge,
            "Error: 429 Too Many Requests" to AgentErrorCause.RateLimited,
            "rate_limit_exceeded: slow down" to AgentErrorCause.RateLimited,
            "Overloaded" to AgentErrorCause.ProviderBusy,
            "503 Service Unavailable" to AgentErrorCause.ProviderBusy,
            "connect ETIMEDOUT 1.2.3.4:443" to AgentErrorCause.TimedOut,
            "The operation timed out." to AgentErrorCause.TimedOut,
            "getaddrinfo ENOTFOUND api.openai.com" to AgentErrorCause.ProviderUnreachable,
            "fetch failed" to AgentErrorCause.ProviderUnreachable,
            "You exceeded your current quota, please check your plan" to AgentErrorCause.OutOfCredit,
            "ENOSPC: no space left on device, write" to AgentErrorCause.ServerDiskFull,
            "Free usage limit reached. Subscribe to OpenCode Go to keep going: https://opencode.ai/go" to
                AgentErrorCause.FreeTierLimit,
            "FreeUsageLimitError" to AgentErrorCause.FreeTierLimit,
            "APIError 429: Free usage limit reached" to AgentErrorCause.FreeTierLimit,
            "Go usage limit exceeded" to AgentErrorCause.UsageLimit,
        )
        for ((raw, cause) in cases) assertEquals(cause, classify(raw), raw)
        assertEquals(AgentErrorCause.RateLimited, classify("429 Too Many Requests: rate limit exceeded"))
        assertEquals(AgentErrorCause.ProviderAuth, classify("401 Unauthorized: invalid api key"))
        assertEquals(AgentErrorCause.RequestTooLarge, classify("413 Payload Too Large"))
        assertNotEquals(AgentErrorCause.FreeTierLimit, classify("prompt is too long: 210000 tokens > 200000 maximum"))
        assertEquals(AgentErrorCause.UsageLimit, classify("Go usage limit exceeded"))
        assertEquals(AgentErrorCause.RateLimited, classify("429 rate limit; connection closed"))
        assertNull(classify("The file lib/main.dart does not exist"))
        assertNull(classify(""))
    }

    @Test
    fun `B-AGENT-012 a recognised cause is said in plain words, with what happens next`() {
        val words = AgentErrorWords.forText(reset, En)
        assertEquals("The connection to the model dropped.", words.headline)
        assertFalse("retries by itself" in words.hint!!)
        assertTrue(words.humanized)
        assertFalse("ECONNRESET" in words.headline)
        assertFalse("fetch()" in words.headline)
    }

    @Test
    fun `B-AGENT-012 the free-tier limit is said plainly, with the way on`() {
        val words = AgentErrorWords.forText("Free usage limit reached", En)
        assertEquals("OpenCode's free models stopped this reply.", words.headline)
        assertTrue("even when you" in words.hint!!)
        assertTrue(words.humanized)
        assertNotEquals(words.headline, AgentErrorWords.forText("Free usage limit reached", Ar).headline)
    }

    @Test
    fun `B-AGENT-012 unknown failures keep technical text out of the headline`() {
        for (language in AgentErrorLanguage.entries) {
            val words = AgentErrorWords.forText(
                "TypeError: Cannot read properties of undefined; api_key=private-value",
                language,
            )
            assertFalse("TypeError" in words.headline)
            assertFalse("undefined" in words.headline)
            assertFalse("private-value" in words.headline)
            assertNotNull(words.hint)
            assertFalse(words.humanized)
        }
    }

    @Test
    fun `B-AGENT-012 exhausted credit takes precedence over an HTTP 429`() {
        assertEquals(AgentErrorCause.OutOfCredit, classify("HTTP 429: insufficient_quota; check billing"))
    }

    @Test
    fun `B-AGENT-012 missing and rejected provider keys have a sign-in recovery`() {
        for (raw in listOf(
            "401 Unauthorized: invalid_api_key",
            "API key is missing",
            "Incorrect API key provided",
            "AuthenticationError: authentication failed",
        )) {
            assertEquals(AgentErrorCause.ProviderAuth, classify(raw), raw)
            assertTrue(classify(raw)!!.offersSignIn)
            val words = AgentErrorWords.forText(raw, En)
            assertEquals("The model provider needs a valid sign-in.", words.headline)
            assertTrue("provider settings" in words.hint!!)
        }
    }

    @Test
    fun `B-AGENT-012 terminal errors do not promise an automatic retry`() {
        for (raw in listOf("429 Too Many Requests", "503 Service Unavailable", "ETIMEDOUT", "ECONNRESET")) {
            assertFalse("retries by itself" in AgentErrorWords.forText(raw, En).hint!!)
        }
    }

    @Test
    fun `B-AGENT-012 every cause has Arabic wording too`() {
        val words = AgentErrorWords.forText(reset, Ar)
        assertNotEquals(AgentErrorWords.forCause(AgentErrorCause.ConnectionDropped, En).first, words.headline)
        assertNotNull(words.hint)
        for (cause in AgentErrorCause.entries) {
            val (enHead, enHint) = AgentErrorWords.forCause(cause, En)
            val (arHead, arHint) = AgentErrorWords.forCause(cause, Ar)
            assertNotEquals(enHead, arHead, cause.name)
            assertNotEquals(enHint, arHint, cause.name)
        }
    }

    @Test
    fun `B-AGENT-012 details text drops developer advice and the errno prefix`() {
        assertEquals("The socket connection was closed unexpectedly.", AgentErrorText.clean(reset))
        assertEquals("plain", AgentErrorText.clean("plain"))
    }
}
