package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.ErrorFix
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorRecovery
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 0 (first reply): a model or provider failure is a plain failed-reply line with a next step, never silence.
 * The server text only classifies; it is never the line. Failure kinds here are the ones analytics reads.
 */
class P0ModelFailureTest {
    private fun fixes(reason: String) =
        ErrorRecovery.fixes(null, ReplyFailureKind.of(reason), reason, hasSuggestion = false, canCompact = false)

    @Test
    fun `P0-MODEL-001 a rate limit is a busy failure that offers another model`() {
        val reason = "AI_APICallError: Too Many Requests (429): rate limit exceeded, try again later"
        assertEquals(ReplyFailureKind.Busy, ReplyFailureKind.of(reason))
        assertTrue(ErrorFix.ChooseModel in fixes(reason))
    }

    @Test
    fun `P0-MODEL-002 a free-tier daily limit offers another model`() {
        val reason = "Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model requests per day."
        assertTrue(ErrorFix.ChooseModel in fixes(reason))
    }

    @Test
    fun `P0-MODEL-003 sign-in required offers the providers page, and out of credit offers the account`() {
        val signIn = "401 Unauthorized: authentication required for this provider"
        assertEquals(ReplyFailureKind.SignIn, ReplyFailureKind.of(signIn))
        assertTrue(ErrorFix.OpenProviders in fixes(signIn))
        val credit = "Insufficient credits. Add more credits to continue."
        assertEquals(ReplyFailureKind.NoCredit, ReplyFailureKind.of(credit))
        assertTrue(ErrorFix.OpenAccount in fixes(credit))
    }
}
