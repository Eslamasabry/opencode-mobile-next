package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.selection.PhoneChatAgents
import io.github.eslamasabry.oc.feature.agents.hub.AgentTileState
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.feature.agents.hub.agentTile
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * signin-truth (owner, two phones): Claude Code read "signed out" hours after it was signed in, and stayed so. A lapse a
 * run reported was kept until a sign-in completed in the app and beat every later sign-in check. The state shown is now
 * derived from the newest evidence, in one rule ([PhoneAgentFacts.lapseStands]) that every reader's state is built
 * with: a signed-in check asked after the lapse ends it, a lapse reported after the check stands.
 */
class SignInTruthTest {
    private val claude = AgentCatalog.builtIn.byId(AgentId.CLAUDE)!!
    private val version = mapOf("installed" to true, "foundVersion" to claude.recipe!!.version)

    private fun read(atMs: Long, state: AuthState = AuthState.SignedIn) =
        PhoneAgentFacts.of(version, AuthProjection(state), signedInAt = atMs)

    /** The sign-in phase a row is built from, as AppPhoneAgents.knownSignIn derives it from the registry. */
    private fun phase(fact: PhoneAgentFacts.Fact, nowMs: Long = 0): AgentSignInPhase? {
        if (PhoneAgentFacts.lapseStands(fact, fact.signedInAt.takeIf { fact.signedIn != null }))
            return AgentSignInPhase.SignedOut
        val read = PhoneAgentFacts.signIn(fact)?.let(PhoneAgentRules::phaseOf)
        return if (read != AgentSignInPhase.SignedOut && PhoneAgentFacts.limitStands(fact, nowMs))
            AgentSignInPhase.LimitReached
        else read
    }

    private fun row(fact: PhoneAgentFacts.Fact, nowMs: Long = 0): PhoneAgentRow {
        val status =
            PhoneAgentRules.status(
                claude,
                AgentArchitecture.Arm64,
                true,
                true,
                qualified = true,
                signIn = phase(fact, nowMs),
            )
        return PhoneAgentRow("claude", "Claude Code", status, signInAvailable = true)
    }

    private enum class Said { Ready, SignedOut, Limit }

    /** What the home, the Agents page's tile and New conversation's "Agent and model" list say for the same row. */
    private fun everywhere(row: PhoneAgentRow): List<Said> {
        val state = PhoneAgentsState(available = true, agents = persistentListOf(row))
        val home = HomeAgentNoticeRules.notices(state) { true }.lines.map { it.kind }
        val tile = agentTile(row, null).state
        val offer = PhoneChatAgents.offer(NewConversationPhoneRules.facts(state), AgentId.OPENCODE)
        val chosen = offer.choices.any { it.id == AgentId.CLAUDE }
        val setUp = offer.needsSetup.any { it.id == AgentId.CLAUDE }
        return listOf(
            when {
                HomeAgentNoticeKind.SignedOut in home -> Said.SignedOut
                HomeAgentNoticeKind.LimitReached in home -> Said.Limit
                else -> Said.Ready
            },
            when (tile) {
                AgentTileState.SignInNeeded -> Said.SignedOut
                AgentTileState.Ready -> Said.Ready
                AgentTileState.Limit -> Said.Limit
                else -> error("tile $tile")
            },
            // The chooser: a choice, a "Set up" row (a sign-in to fix), or neither (a limit: nothing to fix here).
            when {
                chosen -> Said.Ready
                setUp -> Said.SignedOut
                else -> Said.Limit
            },
        )
    }

    @Test
    fun `I147-TRUTH-1 a signed-in check asked after a lapse ends it, Ready everywhere`() {
        val lapsed = PhoneAgentFacts.lapsed(mapOf("claude" to read(1_000)), "claude", atMs = 2_000)
        assertEquals(List(3) { Said.SignedOut }, everywhere(row(lapsed.getValue("claude"))))
        val checked = PhoneAgentFacts.merge(lapsed, mapOf("claude" to read(3_000)))
        val fact = checked.getValue("claude")
        assertFalse(fact.lapsed)
        assertNull(fact.lapsedAt, "the ended lapse is not kept to come back")
        assertEquals(List(3) { Said.Ready }, everywhere(row(fact)))
        // Across an app start as well.
        assertEquals(List(3) { Said.Ready }, everywhere(row(PhoneAgentFacts.decode(PhoneAgentFacts.encode(checked)).getValue("claude"))))
    }

    @Test
    fun `I147-TRUTH-2 a lapse reported after the last signed-in check stands everywhere`() {
        val checked = mapOf("claude" to read(3_000))
        val lapsed = PhoneAgentFacts.lapsed(checked, "claude", atMs = 4_000, PhoneAgentFacts.LapseSource.Reply)
        val fact = lapsed.getValue("claude")
        assertTrue(fact.lapsed)
        assertEquals(List(3) { Said.SignedOut }, everywhere(row(fact)))
        val restarted = PhoneAgentFacts.decode(PhoneAgentFacts.encode(lapsed)).getValue("claude")
        assertEquals(PhoneAgentFacts.LapseSource.Reply, restarted.lapseSource)
        assertEquals(List(3) { Said.SignedOut }, everywhere(row(restarted)))
    }

    @Test
    fun `I147-TRUTH-3 an old reply read again after a newer check changes nothing, a check that did not answer is no evidence`() {
        val checked = mapOf("claude" to read(5_000))
        assertEquals(checked, PhoneAgentFacts.lapsed(checked, "claude", atMs = 4_000), "a failure older than the check")
        val lapsed = PhoneAgentFacts.lapsed(checked, "claude", atMs = 6_000)
        // A check that timed out keeps no answer and no time: the lapse stands.
        val noAnswer = PhoneAgentFacts.merge(lapsed, mapOf("claude" to PhoneAgentFacts.of(version, null, signedInAt = 7_000)))
        assertTrue(noAnswer.getValue("claude").lapsed)
        // A newer check that says signed out keeps it signed out (the check now decides).
        val out = PhoneAgentFacts.merge(lapsed, mapOf("claude" to read(7_000, AuthState.SignedOut)))
        assertEquals(PhoneAgentStatus.SignInNeeded, row(out.getValue("claude")).status)
        // The rule itself: no check time proves nothing newer; a completed sign-in at or after the lapse ends it.
        val fact = lapsed.getValue("claude")
        assertTrue(PhoneAgentFacts.lapseStands(fact, null))
        assertTrue(PhoneAgentFacts.lapseStands(fact, 5_999))
        assertFalse(PhoneAgentFacts.lapseStands(fact, 6_000))
        assertFalse(PhoneAgentFacts.lapseStands(fact.copy(signInDoneAt = 6_000), null))
    }

    @Test
    fun `I147-TRUTH-5 the Galaxy case - a weekly-limit reply, a start refused as not ready, a signed-in check - reads Plan limit, never signed out`() {
        // Build 10050: every Claude Code conversation ends with "You've hit your weekly limit · resets Nov 20, 1am
        // (UTC)"; Home, the tile and the chooser said signed out. A lapse kept from earlier (any start refused "not
        // ready") outlived every later check.
        val said = java.time.Instant.parse("2026-11-16T15:19:00Z").toEpochMilli()
        val words = "You've hit your weekly limit · resets Nov 20, 1am (UTC)"
        val reset = AgentLimitReset.resetAt(words, said)
        assertEquals(java.time.Instant.parse("2026-11-20T01:00:00Z").toEpochMilli(), reset)
        val earlier = PhoneAgentFacts.lapsed(mapOf("claude" to read(said - 3_600_000)), "claude", said - 60_000)
        assertEquals(List(3) { Said.SignedOut }, everywhere(row(earlier.getValue("claude"), said)))
        // The reply at the limit.
        val limited = PhoneAgentFacts.limited(earlier, "claude", said, reset)
        assertEquals(List(3) { Said.Limit }, everywhere(row(limited.getValue("claude"), said + 1_000)))
        // A start refused as not ready while the helper started is not news about the sign-in.
        assertFalse(
            FirstMessageRules.agentNeedsYou(
                io.github.eslamasabry.oc.core.common.Failure.ComputerDidNotAnswer(
                    io.github.eslamasabry.oc.core.security.Redactor().details("Provider claude is not ready")
                )
            )
        )
        // The next sign-in check says signed in: still the limit (the check does not read the plan's usage).
        val checked = PhoneAgentFacts.merge(limited, mapOf("claude" to read(said + 120_000)))
        val fact = checked.getValue("claude")
        assertEquals(List(3) { Said.Limit }, everywhere(row(fact, said + 130_000)))
        // Across an app start, and Ready once the limit resets.
        val restarted = PhoneAgentFacts.decode(PhoneAgentFacts.encode(checked)).getValue("claude")
        assertEquals(List(3) { Said.Limit }, everywhere(row(restarted, said + 130_000)))
        assertEquals(List(3) { Said.Ready }, everywhere(row(restarted, reset!! + 1)))
        // A real sign-in failure reported after the limit stands again.
        val lapsedAfter = PhoneAgentFacts.lapsed(checked, "claude", said + 200_000)
        assertEquals(List(3) { Said.SignedOut }, everywhere(row(lapsedAfter.getValue("claude"), said + 210_000)))
    }

    @Test
    fun `I147-TRUTH-6 the reset an agent's limit line names is read in its own zone, else nothing`() {
        fun at(iso: String) = java.time.Instant.parse(iso).toEpochMilli()
        assertEquals(
            at("2026-11-20T01:00:00Z"),
            AgentLimitReset.resetAt("You've hit your weekly limit · resets Nov 20, 1am (UTC)", at("2026-11-16T15:19:00Z")),
        )
        // A date past the new year.
        assertEquals(
            at("2027-01-02T13:30:00Z"),
            AgentLimitReset.resetAt("You've hit your weekly limit · resets Jan 2, 1:30pm (UTC)", at("2026-12-30T10:00:00Z")),
        )
        // No date: the next such time, in the named zone.
        assertEquals(
            at("2026-11-17T14:00:00Z"),
            AgentLimitReset.resetAt("You've hit your session limit · resets 3pm (Europe/Berlin)", at("2026-11-16T15:00:00Z")),
        )
        assertEquals(at("2025-11-20T00:00:00Z"), AgentLimitReset.resetAt("Claude AI usage limit reached|1763596800", 0))
        assertNull(AgentLimitReset.resetAt("You've hit your weekly limit", at("2026-11-16T15:19:00Z")))
        assertNull(AgentLimitReset.resetAt("resets Nov 20, 1am (Not/AZone)", at("2026-11-16T15:19:00Z")))
        // A limit with no reset named stands for five hours.
        val kept = PhoneAgentFacts.limited(emptyMap(), "claude", atMs = 1_000, resetAtMs = null).getValue("claude")
        assertTrue(PhoneAgentFacts.limitStands(kept, 1_000 + PhoneAgentFacts.LIMIT_WITHOUT_RESET_MS - 1))
        assertFalse(PhoneAgentFacts.limitStands(kept, 1_000 + PhoneAgentFacts.LIMIT_WITHOUT_RESET_MS))
    }

    @Test
    fun `I147-TRUTH-7 a check that gave no answer keeps the last answer, the kept one after an app start, else not confirmed`() {
        val busy =
            io.github.eslamasabry.oc.engine.agents.auth.AuthProjectionJson.decode(
                kotlinx.serialization.json.JsonObject(
                    mapOf(
                        "state" to kotlinx.serialization.json.JsonPrimitive("error"),
                        "error" to kotlinx.serialization.json.JsonPrimitive("agentBusy"),
                    )
                )
            )
        assertEquals(io.github.eslamasabry.oc.engine.agents.host.AuthProbeError.AgentBusy, busy.error)
        val hostDown =
            io.github.eslamasabry.oc.engine.agents.auth.AuthProjectionJson.failed(
                io.github.eslamasabry.oc.engine.agents.host.AuthProbeError.HostUnavailable
            )
        val signedIn = AuthProjection(AuthState.SignedIn)
        val signedOut = AuthProjection(AuthState.SignedOut)
        // Galaxy 10055 after a cold start: no answer, nothing of this run, the registry said signed out at 05:45.
        assertEquals(signedOut, PhoneAgentRules.standIn(hostDown, previous = null, kept = signedOut))
        assertEquals(signedOut, PhoneAgentRules.standIn(busy, previous = null, kept = signedOut))
        assertEquals(signedIn, PhoneAgentRules.standIn(busy, previous = signedIn, kept = signedOut), "this run's is newer")
        assertNull(PhoneAgentRules.standIn(hostDown, previous = hostDown, kept = null), "nothing known: not confirmed")
        assertNull(PhoneAgentRules.standIn(signedOut, previous = signedIn, kept = null), "an answer is kept as it is")
    }
}
