package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatAgents
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatFact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        for (id in listOf("antigravity", "claude", "omp-acp")) {
            for (status in PhoneAgentStatus.entries) {
                for (needsSetUp in listOf(false, true)) {
                    val row =
                        PhoneAgentRow(id, id, status, installable = true, signInAvailable = true)
                            .copy(needsSetUp = needsSetUp)
                    val card = cardOffersConversation(agentTile(row, null), chat = true)
                    assertEquals(
                        card,
                        sheetLists(row),
                        "$id $status set up first $needsSetUp: the card offers New conversation = the sheet lists it",
                    )
                }
            }
        }
    }

    @Test
    fun `AGENT-OFFER a ready Antigravity is offered on both screens`() {
        val row = PhoneAgentRow("antigravity", "Antigravity", PhoneAgentStatus.Ready)
        assertEquals(true, cardOffersConversation(agentTile(row, null), chat = true))
        assertEquals(true, sheetLists(row))
    }

    // Owner 2026-11-15: "when oh my pi needs a setup after installation why show new conversation? instead of setup
    // cta". An agent set up at first use that the app never saw set up leads with Set up; once seen set up (its set-up
    // terminal ended, or it answered) it starts conversations like a Ready agent, its set-up kept in its sheet.
    @Test
    fun `B-AGENT-SETUP-001 an agent never seen set up leads with Set up, and New conversation once seen set up`() {
        val fresh =
            PhoneAgentRow("omp-acp", "Oh My Pi", PhoneAgentStatus.SetUpAtFirstUse, signInAvailable = true)
                .copy(needsSetUp = true)
        val tile = agentTile(fresh, null)
        assertEquals(AgentTileState.SetUp, tile.state)
        assertEquals(AgentTileAct.SetUp, tile.act, "Set up is its one act")
        assertFalse(cardOffersConversation(tile, chat = true), "no New conversation on its card")
        assertFalse(phoneAgentStartsConversation(fresh), "no choice in New conversation")
        assertFalse(sheetLists(fresh))

        val seen = fresh.copy(needsSetUp = false)
        val seenTile = agentTile(seen, null)
        assertEquals(AgentTileState.SetUp, seenTile.state)
        assertTrue(cardOffersConversation(seenTile, chat = true), "seen set up: the card's New conversation")
        assertTrue(phoneAgentStartsConversation(seen))
        assertTrue(sheetLists(seen))
    }

    @Test
    fun `AGENT-OFFER while the helper did not start no card offers a conversation`() {
        val row = PhoneAgentRow("antigravity", "Antigravity", PhoneAgentStatus.Ready)
        assertEquals(false, cardOffersConversation(agentTile(row, null, helperDown = true), chat = true))
        assertEquals(false, cardOffersConversation(agentTile(row, null), chat = false))
    }
}
