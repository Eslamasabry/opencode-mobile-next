package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorCause
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorText
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.presentation.ChatSignInLapse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * signin-truth (Galaxy, build 10050): every Claude Code conversation ended with "You've hit your weekly limit · resets
 * Nov 20, 1am (UTC)" and the phone said "Claude Code signed out". The limit is told as the limit, in the words the app
 * already has for it, and is never a lapsed sign-in.
 */
class SignInTruthLimitTest {
    private val weekly = "You've hit your weekly limit · resets Nov 20, 1am (UTC)"

    private fun footer(reason: String?, kind: MessageErrorKind? = null, failed: Boolean = true) =
        FooterRow("footer:t1", "t1", failed = failed, reason = reason, errorKind = kind, createdMs = 10, providerId = "claude")

    @Test
    fun `I147-TRUTH-30 a weekly-limit reply is the agent's usage limit, said in its own words`() {
        assertEquals(AgentErrorCause.UsageLimit, AgentErrorText.classify(weekly))
        assertEquals(AgentErrorCause.UsageLimit, AgentErrorText.classify("You’ve hit your session limit · resets 3pm (UTC)"))
        assertEquals(AgentErrorCause.UsageLimit, AgentErrorText.classify("Claude AI usage limit reached|1763596800"))
    }

    @Test
    fun `I147-TRUTH-31 a limit reply is reported as a limit, never as a lapsed sign-in`() {
        assertEquals(weekly, ChatSignInLapse.limit(footer(weekly)))
        assertFalse(ChatSignInLapse.lapsed(footer(weekly)))
        // Even when something tagged it as a sign-in, a limit stays a limit.
        assertFalse(ChatSignInLapse.lapsed(footer(weekly, MessageErrorKind.ProviderAuth)))
        // A real sign-in failure is still a lapse, and no limit.
        val expired = footer("OAuth session expired and could not be refreshed", MessageErrorKind.ProviderAuth)
        assertTrue(ChatSignInLapse.lapsed(expired))
        assertNull(ChatSignInLapse.limit(expired))
        // A reply that came back well is neither.
        assertNull(ChatSignInLapse.limit(footer(null, failed = false)))
        assertFalse(ChatSignInLapse.lapsed(footer(null, failed = false)))
    }
}
