package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatAgents
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatFact
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Agents page's card and New conversation's "Who works on it" decide by one rule
 * ([phoneAgentStartsConversation]): for every state an agent can be in, the card offers New conversation exactly when
 * the list offers the agent as a choice (owner, 2026-11-11: "Antigravity not showing" while its card said Ready).
 */
class ConversationOfferAgreementTest {
    private fun sheetLists(row: PhoneAgentRow): Boolean {
        // As the app builds New conversation's facts (NewConversationWiring.phoneFacts) from the same rows.
        val fact = PhoneChatFact(AgentId(row.id), row.name, row.id, ready = phoneAgentStartsConversation(row), fixable = true)
        return PhoneChatAgents.offer(listOf(fact), AgentId.OPENCODE).choices.any { it.id.raw == row.id }
    }

    @Test
    fun `AGENT-OFFER the card and the sheet agree for every agent state, certified or not`() {
        // Antigravity has no row in the bundled chat certification; Claude Code has one.
        for (id in listOf("antigravity", "claude")) {
            for (status in PhoneAgentStatus.entries) {
                val row = PhoneAgentRow(id, id, status, installable = true, signInAvailable = true)
                val card = cardOffersConversation(agentTile(row, null), chat = true)
                assertEquals(card, sheetLists(row), "$id $status: the card offers New conversation = the sheet lists it")
            }
        }
    }

    @Test
    fun `AGENT-OFFER a ready Antigravity is offered on both screens`() {
        val row = PhoneAgentRow("antigravity", "Antigravity", PhoneAgentStatus.Ready)
        assertEquals(true, cardOffersConversation(agentTile(row, null), chat = true))
        assertEquals(true, sheetLists(row))
    }

    @Test
    fun `AGENT-OFFER while the helper did not start no card offers a conversation`() {
        val row = PhoneAgentRow("antigravity", "Antigravity", PhoneAgentStatus.Ready)
        assertEquals(false, cardOffersConversation(agentTile(row, null, helperDown = true), chat = true))
        assertEquals(false, cardOffersConversation(agentTile(row, null), chat = false))
    }
}
