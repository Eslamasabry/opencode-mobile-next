package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatAgents
import io.github.eslamasabry.oc.feature.agents.hub.AgentTileState
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentShown
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.hub.agentTile
import io.github.eslamasabry.oc.feature.agents.hub.cardOffersConversation
import io.github.eslamasabry.oc.feature.agents.hub.phoneAgentShown
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue 147, second report: "Antigravity sometimes displays Ready in the Agent and Model section while the New
 * Conversation screen still asks me to set up Antigravity." The same facts go to the three places that state an
 * agent's status (the Agents page's tile, New conversation's "Who works on it" choices with their words, and its
 * set-up rows): all three must say the same status.
 */
class PhoneAgentOneStatusTest {
    private enum class Said { Ready, SetUpAtFirstUse, NeedsSetUpOrSignIn, Starting, NotOffered }

    /** What the Agents page's tile says, reduced to the status. */
    private fun tile(row: PhoneAgentRow): Said {
        val model = agentTile(row, null)
        return when (model.state) {
            AgentTileState.Ready -> Said.Ready
            AgentTileState.SetUp ->
                if (cardOffersConversation(model, chat = true)) Said.SetUpAtFirstUse else Said.NeedsSetUpOrSignIn
            AgentTileState.SignInNeeded,
            AgentTileState.PhoneCheck -> Said.NeedsSetUpOrSignIn
            AgentTileState.Stopped -> Said.Starting
            else -> Said.NotOffered
        }
    }

    /** What New conversation's agent list and its set-up rows say for the same row. */
    private fun newConversation(row: PhoneAgentRow): Said {
        val state = PhoneAgentsState(available = true, agents = persistentListOf(row))
        val offer = PhoneChatAgents.offer(NewConversationPhoneRules.facts(state), AgentId.OPENCODE)
        return when {
            offer.choices.any { it.id.raw == row.id } ->
                when {
                    row.id in NewConversationPhoneRules.setUpAtFirstUse(state) -> Said.SetUpAtFirstUse
                    phoneAgentShown(row) == PhoneAgentShown.Starting -> Said.Starting
                    else -> Said.Ready
                }
            offer.needsSetup.any { it.id.raw == row.id } -> Said.NeedsSetUpOrSignIn
            else -> Said.NotOffered
        }
    }

    private fun shown(row: PhoneAgentRow): Said =
        when (phoneAgentShown(row)) {
            PhoneAgentShown.Ready -> Said.Ready
            PhoneAgentShown.SetUpAtFirstUse -> Said.SetUpAtFirstUse
            PhoneAgentShown.SetUp,
            PhoneAgentShown.SignInNeeded -> Said.NeedsSetUpOrSignIn
            PhoneAgentShown.Starting -> Said.Starting
            else -> Said.NotOffered
        }

    @Test
    fun `B-AGENT-STATUS-001 the tile, the agent list and the set-up rows say one status for the same facts`() {
        val antigravity = PhoneAgentRow("antigravity", "Antigravity", PhoneAgentStatus.Ready, signInAvailable = true)
        val rows =
            listOf(
                antigravity,
                // The kept "signed in" answer lapsed (the helper refused a start for the sign-in, or said not ready).
                antigravity.copy(status = PhoneAgentStatus.SignInNeeded),
                antigravity.copy(status = PhoneAgentStatus.Stopped),
                antigravity.copy(status = PhoneAgentStatus.PhoneCheck),
                PhoneAgentRow("omp-acp", "Oh My Pi", PhoneAgentStatus.SetUpAtFirstUse, signInAvailable = true, needsSetUp = true),
                PhoneAgentRow("omp-acp", "Oh My Pi", PhoneAgentStatus.SetUpAtFirstUse, signInAvailable = true, needsSetUp = false),
            )
        for (row in rows) {
            val expected = shown(row)
            assertEquals(expected, tile(row), "tile: ${row.status} needsSetUp=${row.needsSetUp}")
            assertEquals(expected, newConversation(row), "New conversation: ${row.status} needsSetUp=${row.needsSetUp}")
        }
    }

    @Test
    fun `B-AGENT-STATUS-002 a start refused as not ready is not news about the sign-in, only the helper's sign-in refusal is`() {
        // signin-truth: "is not ready" / "No model selected" while the helper or the agent was still starting set the
        // sticky lapse, and the agent read "Sign in needed" everywhere while it was signed in.
        val redactor = io.github.eslamasabry.oc.core.security.Redactor()
        for (words in listOf("Provider antigravity is not ready", "No model selected", "Provider claude is disabled"))
            assertEquals(
                false,
                FirstMessageRules.agentNeedsYou(
                    io.github.eslamasabry.oc.core.common.Failure.ComputerDidNotAnswer(redactor.details(words))
                ),
                words,
            )
        assertEquals(
            true,
            FirstMessageRules.agentNeedsYou(
                io.github.eslamasabry.oc.core.common.Failure.Rejected(
                    null,
                    io.github.eslamasabry.oc.protocol.paseo.PaseoAgentSignInTag,
                )
            ),
        )
        assertEquals(false, FirstMessageRules.agentNeedsYou(io.github.eslamasabry.oc.core.common.Failure.TimedOut()))
        assertEquals(false, FirstMessageRules.agentNeedsYou(null))
    }
}
