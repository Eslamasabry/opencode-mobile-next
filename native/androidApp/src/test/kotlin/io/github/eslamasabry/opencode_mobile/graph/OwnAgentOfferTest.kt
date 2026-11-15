package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue 147: a person's own agent is offered in New conversation like a built-in one, with its own provider id. */
class OwnAgentOfferTest {
    private val own = PhoneAgentRow("own-my-agent", "My Agent", PhoneAgentStatus.SetUpAtFirstUse, needsSetUp = false)
    private val claude = PhoneAgentRow("claude", "Claude Code", PhoneAgentStatus.Ready)

    @Test
    fun `B-AGENT-OWN-090 an own agent is a choice in New conversation after the built-in ones`() {
        val state =
            PhoneAgentsState(available = true, agents = persistentListOf(own, claude), owner = ProfileId("owner-a"))
        val facts = NewConversationPhoneRules.facts(state)
        val offer = PhoneChatAgents.offer(facts, AgentId.OPENCODE)
        assertEquals(listOf("claude", "own-my-agent"), offer.choices.map { it.id.raw })
        assertEquals("My Agent", offer.choices.last().name)
        assertTrue(offer.needsSetup.isEmpty())
    }

    @Test
    fun `B-AGENT-OWN-091 while the helper is down an own agent is still a choice (a start starts the helper)`() {
        val stopped = own.copy(status = PhoneAgentStatus.Stopped)
        val facts = NewConversationPhoneRules.facts(PhoneAgentsState(available = true, agents = persistentListOf(stopped)))
        assertEquals(listOf("own-my-agent"), PhoneChatAgents.offer(facts, AgentId.OPENCODE).choices.map { it.id.raw })
    }

    @Test
    fun `B-AGENT-OWN-092 the helper provider id of an own agent is its id, built-in agents keep the catalog's`() {
        assertEquals("own-my-agent", NewConversationPhoneRules.providerOf("own-my-agent"))
        assertEquals("omp-acp", NewConversationPhoneRules.providerOf("omp-acp"))
        assertEquals("claude", NewConversationPhoneRules.providerOf("claude"))
        assertNull(NewConversationPhoneRules.providerOf("someone-else"))
        assertNull(NewConversationPhoneRules.providerOf("own-"))
    }
}
