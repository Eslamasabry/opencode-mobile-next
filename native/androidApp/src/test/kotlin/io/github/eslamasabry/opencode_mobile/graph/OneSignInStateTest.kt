package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.feature.agents.hub.AgentTileState
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.hub.agentTile
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import io.github.eslamasabry.oc.feature.usage.presentation.UsageAgentTruth
import io.github.eslamasabry.oc.feature.usage.presentation.limitsUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/**
 * signin-followups: one state, four readers. Claude Code's sign-in facts are driven through signed out, not confirmed
 * and plan limit; the Agents tile, New conversation's chooser row, the Usage page and Home's notice are each built from
 * the same row (`PhoneAgentsState.agents`, which `AppPhoneAgents.knownSignIn` and `PhoneAgentFacts` fill) and must say
 * the same thing. The Usage reader's own "no account found" never decides the word.
 */
class OneSignInStateTest {
    private enum class Said { SignedOut, NotConfirmed, Limit }

    private val claude = AgentCatalog.builtIn.byId(AgentId.CLAUDE)!!
    private val version = mapOf("installed" to true, "foundVersion" to claude.recipe!!.version)
    private val reset = 9_000_000L
    private val now = 3_000L

    /** The row as AppPhoneAgents builds it from a sign-in phase (status, check-failed flag, reset words). */
    private fun row(phase: AgentSignInPhase): PhoneAgentRow {
        val status =
            PhoneAgentRules.status(claude, AgentArchitecture.Arm64, true, true, qualified = true, signIn = phase)
        return PhoneAgentRow(
            "claude",
            "Claude Code",
            status,
            signInCheckFailed =
                status == PhoneAgentStatus.SignInNeeded && PhoneAgentRules.signInCheckFailed(claude, phase),
            limitResets = if (status == PhoneAgentStatus.Limit) "Nov 20, 1:00 AM" else null,
        )
    }

    private fun phaseOf(fact: PhoneAgentFacts.Fact): AgentSignInPhase {
        if (PhoneAgentFacts.lapseStands(fact, fact.signedInAt.takeIf { fact.signedIn != null })) return AgentSignInPhase.SignedOut
        val read = PhoneAgentFacts.signIn(fact)?.let(PhoneAgentRules::phaseOf)
        return if (read != AgentSignInPhase.SignedOut && PhoneAgentFacts.limitStands(fact, now)) AgentSignInPhase.LimitReached
        else read ?: AgentSignInPhase.Failed
    }

    private val signedIn = PhoneAgentFacts.of(version, AuthProjection(AuthState.SignedIn), signedInAt = 1_000)
    private val signedOut = PhoneAgentFacts.of(version, AuthProjection(AuthState.SignedOut), signedInAt = 1_000)
    private val limited =
        PhoneAgentFacts.limited(mapOf("claude" to signedIn), "claude", atMs = 2_000, resetAtMs = reset).getValue("claude")
    // A check that could not answer keeps no fact: the phase is the probe's failure.
    private val unconfirmed = PhoneAgentFacts.of(version, null)

    private fun tile(row: PhoneAgentRow): Said? =
        when (agentTile(row, null).state) {
            AgentTileState.SignInNeeded -> if (row.signInCheckFailed) Said.NotConfirmed else Said.SignedOut
            AgentTileState.Limit -> Said.Limit
            else -> null
        }

    private fun chooser(state: PhoneAgentsState): Said? =
        when {
            "claude" in NewConversationPhoneRules.signInNotConfirmed(state) -> Said.NotConfirmed
            "claude" in NewConversationPhoneRules.signInNeeded(state) -> Said.SignedOut
            "claude" in NewConversationPhoneRules.limit(state) -> Said.Limit
            else -> null
        }

    /** The Usage page for a reader that found no account (the case that said "Not signed in" in build 10055). */
    private fun usage(state: PhoneAgentsState): Said? {
        val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)
        val reader =
            UsageSubscription("claude|conn:phone", "claude", "Claude", SubscriptionState.NotSignedIn, readFrom = phone)
        val ui =
            limitsUi(
                UsageSnapshot(subscriptions = listOf(reader), connections = listOf(phone), loaded = true),
                refreshing = false,
                nowMs = now,
                timezone = "UTC",
                agents = UsageAgentTruths.of(state.agents),
            )
        return when (ui.accounts.single().agent) {
            null -> Said.SignedOut // the reader's own "Not signed in" stands only when the agent is signed out
            is UsageAgentTruth.Limit -> Said.Limit
            is UsageAgentTruth.NotConfirmed -> Said.NotConfirmed
            UsageAgentTruth.SignedIn,
            UsageAgentTruth.SignedOut -> Said.SignedOut
        }
    }

    /** Home's notice: a signed-out line, a limit line, or none (a check that did not answer is no news). */
    private fun home(state: PhoneAgentsState): Said? =
        HomeAgentNoticeRules.notices(state) { true }.lines.singleOrNull()?.let {
            when (it.kind) {
                HomeAgentNoticeKind.SignedOut -> Said.SignedOut
                HomeAgentNoticeKind.LimitReached -> Said.Limit
                else -> null
            }
        }

    private fun check(fact: PhoneAgentFacts.Fact, phase: AgentSignInPhase?, expected: Said) {
        val row = row(phase ?: phaseOf(fact))
        val state = PhoneAgentsState(available = true, agents = persistentListOf(row))
        assertEquals(expected, tile(row), "tile")
        assertEquals(expected, chooser(state), "chooser row")
        assertEquals(expected, usage(state), "Usage")
        // Home lists a signed-out agent and a limit; for an unconfirmed check it lists nothing, as the tile's own news.
        assertEquals(if (expected == Said.NotConfirmed) null else expected, home(state), "Home's notice")
    }

    @Test
    fun `SIGNIN-ONE-1 signed out: tile, chooser, Usage and Home all say signed out`() =
        check(signedOut, null, Said.SignedOut)

    @Test
    fun `SIGNIN-ONE-2 not confirmed: tile, chooser and Usage say not confirmed, Home says nothing`() =
        check(unconfirmed, AgentSignInPhase.Failed, Said.NotConfirmed)

    @Test
    fun `SIGNIN-ONE-3 plan limit: tile, chooser, Usage and Home all say limit`() = check(limited, null, Said.Limit)
}
