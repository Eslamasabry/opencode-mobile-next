package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.usage.presentation.UsageAgentTruth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** signin-followups: the Usage page hears the agent's own state, not just that its reader found no account. */
class UsageAgentTruthsTest {
    private fun claude(status: PhoneAgentStatus, failed: Boolean = false, resets: String? = null) =
        UsageAgentTruths.of(
            listOf(PhoneAgentRow("claude", "Claude Code", status, signInCheckFailed = failed, limitResets = resets))
        )["claude"]

    @Test
    fun `a limit is a limit with its reset`() {
        assertEquals(UsageAgentTruth.Limit("Nov 20, 1:00 AM"), claude(PhoneAgentStatus.Limit, resets = "Nov 20, 1:00 AM"))
    }

    @Test
    fun `only a check that ran and found no sign-in is signed out`() {
        assertEquals(UsageAgentTruth.SignedOut, claude(PhoneAgentStatus.SignInNeeded))
        assertEquals(UsageAgentTruth.NotConfirmed("Claude Code"), claude(PhoneAgentStatus.SignInNeeded, failed = true))
    }

    @Test
    fun `an agent that cannot answer yet is not confirmed, never signed out`() {
        for (s in listOf(PhoneAgentStatus.Checking, PhoneAgentStatus.Stopped, PhoneAgentStatus.Unavailable))
            assertEquals(UsageAgentTruth.NotConfirmed("Claude Code"), claude(s))
    }

    @Test
    fun `a ready agent is signed in and a missing one says nothing`() {
        assertEquals(UsageAgentTruth.SignedIn, claude(PhoneAgentStatus.Ready))
        assertFalse("claude" in UsageAgentTruths.of(listOf(PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.NotInstalled))))
    }
}
