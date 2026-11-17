package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.integrations.usage.SignInState
import io.github.eslamasabry.oc.engine.integrations.usage.SignedInOn
import io.github.eslamasabry.oc.engine.integrations.usage.SubscriptionState
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnection
import io.github.eslamasabry.oc.engine.integrations.usage.UsageConnectionKind
import io.github.eslamasabry.oc.engine.integrations.usage.UsageReading
import io.github.eslamasabry.oc.engine.integrations.usage.UsageReadingStatus
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSubscription
import io.github.eslamasabry.oc.engine.integrations.usage.mergeUsageSubscriptions
import io.github.eslamasabry.oc.feature.agents.hub.AgentTileState
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.hub.agentTile
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import io.github.eslamasabry.oc.feature.plans.presentation.PlanAgentTruth
import io.github.eslamasabry.oc.feature.plans.presentation.plansUi
import io.github.eslamasabry.oc.feature.usage.presentation.UsageAgentTruth
import io.github.eslamasabry.oc.feature.usage.presentation.limitsUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/**
 * signin-followups: one state, four readers. Claude Code's sign-in facts are driven through signed out, not confirmed,
 * plan limit and signed in; the Agents tile, New conversation's chooser row, Usage (Limits and Plans) and Home's notice
 * are each built from the same row (`PhoneAgentsState.agents`, which `AppPhoneAgents.knownSignIn` and
 * `PhoneAgentFacts` fill) and must say the same thing. The Usage reader's own "no account found" never decides the
 * word.
 */
class OneSignInStateTest {
    private enum class Said { SignedOut, NotConfirmed, Limit, SignedIn }

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
            AgentTileState.Ready -> Said.SignedIn
            else -> null
        }

    private fun chooser(state: PhoneAgentsState): Said? =
        when {
            "claude" in NewConversationPhoneRules.signInNotConfirmed(state) -> Said.NotConfirmed
            "claude" in NewConversationPhoneRules.signInNeeded(state) -> Said.SignedOut
            "claude" in NewConversationPhoneRules.limit(state) -> Said.Limit
            NewConversationPhoneRules.facts(state).any { it.id == AgentId.CLAUDE && it.ready } -> Said.SignedIn
            else -> null
        }

    /** Usage › Limits for a reader that found no account, optionally collapsed after another connection. */
    private fun usage(state: PhoneAgentsState, withLeadingOtherConnection: Boolean = false): Said {
        val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)
        val other = UsageConnection("pc", "Workstation", UsageConnectionKind.PaseoServer)
        val connections = if (withLeadingOtherConnection) listOf(other, phone) else listOf(phone)
        val signedInOn =
            if (withLeadingOtherConnection)
                listOf(SignedInOn(other, SignInState.NotSignedIn), SignedInOn(phone, SignInState.NotSignedIn))
            else listOf(SignedInOn(phone, SignInState.NotSignedIn))
        val reader =
            UsageSubscription(
                "claude|conn:phone",
                "claude",
                "Claude",
                SubscriptionState.NotSignedIn,
                signedInOn = signedInOn,
            )
        val ui =
            limitsUi(
                UsageSnapshot(subscriptions = listOf(reader), connections = connections, loaded = true),
                refreshing = false,
                nowMs = now,
                timezone = "UTC",
                agents = UsageAgentTruths.of(state.agents),
            )
        return when (ui.accounts.single().agent) {
            null -> Said.SignedOut // the reader's own "Not signed in" stands only when the agent is signed out
            is UsageAgentTruth.Limit -> Said.Limit
            is UsageAgentTruth.NotConfirmed -> Said.NotConfirmed
            UsageAgentTruth.SignedIn -> Said.SignedIn
            UsageAgentTruth.SignedOut -> Said.SignedOut
        }
    }

    /** Usage › Plans for the same reader and phone state. */
    private fun plans(state: PhoneAgentsState, withLeadingOtherConnection: Boolean = false): Said {
        val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)
        val other = UsageConnection("pc", "Workstation", UsageConnectionKind.PaseoServer)
        val connections = if (withLeadingOtherConnection) listOf(other, phone) else listOf(phone)
        val signedInOn =
            if (withLeadingOtherConnection)
                listOf(SignedInOn(other, SignInState.NotSignedIn), SignedInOn(phone, SignInState.NotSignedIn))
            else listOf(SignedInOn(phone, SignInState.NotSignedIn))
        val reader =
            UsageSubscription(
                "claude|conn:phone",
                "claude",
                "Claude",
                SubscriptionState.NotSignedIn,
                signedInOn = signedInOn,
            )
        val ui =
            plansUi(
                UsageSnapshot(subscriptions = listOf(reader), connections = connections, loaded = true),
                refreshing = false,
                nowMs = now,
                agents = UsageAgentTruths.planOf(state.agents),
            )
        return when (ui.cards.single().agent) {
            null -> Said.SignedOut
            is PlanAgentTruth.Limit -> Said.Limit
            is PlanAgentTruth.NotConfirmed -> Said.NotConfirmed
            PlanAgentTruth.SignedIn -> Said.SignedIn
            PlanAgentTruth.SignedOut -> Said.SignedOut
        }
    }

    /** Home's notice: a signed-out line, a limit line, or none (signed in or an unconfirmed check is no news). */
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
        assertEquals(expected, usage(state), "Usage Limits")
        assertEquals(expected, plans(state), "Usage Plans")
        assertEquals(expected, usage(state, withLeadingOtherConnection = true), "Usage Limits collapsed")
        assertEquals(expected, plans(state, withLeadingOtherConnection = true), "Usage Plans collapsed")
        // Home lists a signed-out agent and a limit; for signed in or an unconfirmed check it lists nothing.
        val expectedHome = if (expected == Said.NotConfirmed || expected == Said.SignedIn) null else expected
        assertEquals(expectedHome, home(state), "Home's notice")
    }

    @Test
    fun `SIGNIN-ONE-1 signed out, all readers say signed out`() =
        check(signedOut, null, Said.SignedOut)

    @Test
    fun `SIGNIN-ONE-2 not confirmed, tile chooser and Usage say so and Home says nothing`() =
        check(unconfirmed, AgentSignInPhase.Failed, Said.NotConfirmed)

    @Test
    fun `SIGNIN-ONE-3 plan limit, all readers say limit`() = check(limited, null, Said.Limit)

    @Test
    fun `SIGNIN-ONE-4 signed in with no numbers, tile chooser and Usage agree without Not signed in`() =
        check(signedIn, null, Said.SignedIn)

    @Test
    fun `SIGNIN-ONE-5 phone helper Paseo profile is excluded so it cannot shadow PhoneAgents in Usage`() {
        val owner = "phone1"
        val helperId = "$owner-agents"
        assertFalse(PhoneAgentRules.isRemotePaseoProfile(helperId, BackendKind.Paseo, owner))
        // When only real remote Paseo servers + PHONE_ID enter mergeUsageSubscriptions, the phone reading stays PhoneAgents.
        val phone = UsageConnection(UsageConnection.PHONE_ID, "", UsageConnectionKind.PhoneAgents)
        val merged =
            mergeUsageSubscriptions(
                now = Instant.fromEpochMilliseconds(now),
                connections = listOf(phone),
                readings =
                    listOf(
                        UsageReading(
                            "claude",
                            "Claude",
                            helperId,
                            UsageReadingStatus.NotSignedIn,
                            Instant.fromEpochMilliseconds(now),
                        ),
                        UsageReading(
                            "claude",
                            "Claude",
                            UsageConnection.PHONE_ID,
                            UsageReadingStatus.NotSignedIn,
                            Instant.fromEpochMilliseconds(now),
                        ),
                    ),
            )
        val row = row(AgentSignInPhase.Failed)
        val ui =
            limitsUi(
                merged,
                refreshing = false,
                nowMs = now,
                timezone = "UTC",
                agents = UsageAgentTruths.of(listOf(row)),
            )
        assertEquals(UsageAgentTruth.NotConfirmed("Claude Code"), ui.accounts.single().agent)
    }
}
