package io.github.eslamasabry.opencode_mobile

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner, 2026-11-18: "Login successful." in the sign-in terminal, and the page said "isn't signed in yet". The chat
 * that offered "Sign in again" kept its Claude Code program loaded, so every check was held back as `agentBusy`.
 */
class PhoneAgentAuthHoldBackTest {
    private val minute = TimeUnit.MINUTES.toMillis(1)

    @Test
    fun `B-AGENT-440 the page's check of a sign-in just written runs beside a loaded Claude Code program`() {
        assertFalse(PhoneAgentAuthHoldBack.holdsBack("claude", { true }, afterSignIn = true, { 5_000L }))
        assertFalse(PhoneAgentAuthHoldBack.holdsBack("claude", { true }, afterSignIn = true, { 9 * minute }))
    }

    @Test
    fun `B-AGENT-441 every other check beside a running Claude Code program is still held back`() {
        // Not the sign-in page's check: the hold-back of signin-truth stands.
        assertTrue(PhoneAgentAuthHoldBack.holdsBack("claude", { true }, afterSignIn = false, { 5_000L }))
        // The page's check of a sign-in written long ago (it may need a refresh), or of none at all.
        assertTrue(PhoneAgentAuthHoldBack.holdsBack("claude", { true }, afterSignIn = true, { 11 * minute }))
        assertTrue(PhoneAgentAuthHoldBack.holdsBack("claude", { true }, afterSignIn = true, { null }))
        // A file dated in the future proves nothing.
        assertTrue(PhoneAgentAuthHoldBack.holdsBack("claude", { true }, afterSignIn = true, { -minute }))
        // Nothing runs: nothing to hold back for; other agents never are.
        assertFalse(PhoneAgentAuthHoldBack.holdsBack("claude", { false }, afterSignIn = false, { null }))
        assertFalse(PhoneAgentAuthHoldBack.holdsBack("codex", { true }, afterSignIn = false, { null }))
    }
}
