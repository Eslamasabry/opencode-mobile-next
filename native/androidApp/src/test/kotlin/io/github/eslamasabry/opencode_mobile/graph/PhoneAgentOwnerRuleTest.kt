package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * B-AGENT-113 (set up at first use, the chat seam): a phone agent's conversation lives on the helper profile
 * `<owner>-agents`, while the Agents page, its set-up terminal and the set-up list use the phone's owner. The chat's
 * "Set up <agent>" opens the terminal for that owner, and reads that owner's set-up list.
 */
class PhoneAgentOwnerRuleTest {
    @Test
    fun `B-AGENT-113 a phone agents conversation belongs to the phone's owner`() {
        assertEquals(ProfileId("phone"), PhoneAgentOwnerRule.owner(ProfileId("phone-agents"), builtinOwner = "phone"))
    }

    @Test
    fun `B-AGENT-113 other conversations have no phone agents to set up`() {
        // The phone's own server, a computer, another owner's helper, no built-in server saved.
        assertNull(PhoneAgentOwnerRule.owner(ProfileId("phone"), builtinOwner = "phone"))
        assertNull(PhoneAgentOwnerRule.owner(ProfileId("laptop"), builtinOwner = "phone"))
        assertNull(PhoneAgentOwnerRule.owner(ProfileId("old-agents"), builtinOwner = "phone"))
        assertNull(PhoneAgentOwnerRule.owner(ProfileId("phone-agents"), builtinOwner = null))
        assertNull(PhoneAgentOwnerRule.owner(ProfileId("-agents"), builtinOwner = ""))
    }
}
