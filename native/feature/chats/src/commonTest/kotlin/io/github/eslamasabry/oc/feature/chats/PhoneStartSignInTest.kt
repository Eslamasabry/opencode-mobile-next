package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.WireTag
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneStartRules
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneStartWay
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue 147 (owner's phone): Claude Code's start was refused by the helper because its sign-in had expired; the page
 * said "didn't confirm". A start refused for the agent's sign-in now reads "<Agent> needs you to sign in again" with
 * Sign in; any other refusal keeps its way forward.
 */
class PhoneStartSignInTest {
    @Test
    fun `I147-AGY-8 a start the helper refused for the agent's sign-in offers Sign in`() {
        assertEquals(
            PhoneStartWay.SignIn,
            PhoneStartRules.way(Failure.Rejected(null, WireTag(PhoneStartRules.SIGN_IN_TAG)), null, emptyList()),
        )
        assertEquals(
            PhoneStartWay.TryAgain,
            PhoneStartRules.way(Failure.Rejected(null, WireTag("Paseo AgentRefused")), null, emptyList()),
        )
    }
}

/** signin-followups: "not ready" is asked again; real set-up refusals keep their set-up. */
class PhoneStartNotReadyTest {
    private fun refusal(text: String) =
        Failure.ComputerDidNotAnswer(io.github.eslamasabry.oc.core.security.Redactor().details(text))

    @Test
    fun `SIGNIN-N-10 a start refused as not ready offers Check again, not set-up`() {
        assertEquals(PhoneStartWay.CheckAgain, PhoneStartRules.way(refusal("Provider claude is not ready (unavailable)"), null, emptyList()))
    }

    @Test
    fun `SIGNIN-N-11 a refusal that is really about set-up keeps its set-up`() {
        assertEquals(PhoneStartWay.SetUp, PhoneStartRules.way(refusal("claude is not installed"), null, emptyList()))
        assertEquals(PhoneStartWay.SetUp, PhoneStartRules.way(refusal("No API key"), null, emptyList()))
        assertEquals(PhoneStartWay.TryAgain, PhoneStartRules.way(refusal("the agent stopped"), null, emptyList()))
    }
}
