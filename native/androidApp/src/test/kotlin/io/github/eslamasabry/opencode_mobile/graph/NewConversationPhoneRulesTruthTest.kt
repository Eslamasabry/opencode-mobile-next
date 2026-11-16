package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** signin-followups: New conversation's chooser words an agent as the Agents page does. */
class NewConversationPhoneRulesTruthTest {
    private fun state(vararg rows: PhoneAgentRow) =
        PhoneAgentsState(available = true, agents = persistentListOf(*rows))

    @Test
    fun `SIGNIN-N-20 a check that could not run is not confirmed, never signed out`() {
        val s =
            state(
                PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.SignInNeeded, signInCheckFailed = true),
                PhoneAgentRow("codex", "Codex", PhoneAgentStatus.SignInNeeded),
            )
        assertEquals(setOf("claude"), NewConversationPhoneRules.signInNotConfirmed(s))
        assertEquals(setOf("codex"), NewConversationPhoneRules.signInNeeded(s))
    }

    @Test
    fun `SIGNIN-N-21 an agent at its limit carries its reset`() {
        val s = state(PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Limit, limitResets = "Nov 20, 1:00 AM"))
        assertEquals(mapOf<String, String?>("claude" to "Nov 20, 1:00 AM"), NewConversationPhoneRules.limit(s))
    }
}
