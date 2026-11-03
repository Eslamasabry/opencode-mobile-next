package io.github.eslamasabry.oc.engine.agents.phoneagents

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.auth.AgentAuth
import io.github.eslamasabry.oc.engine.agents.auth.AuthProjectionJson
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCapabilities
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.certification.AgentCertification
import io.github.eslamasabry.oc.engine.agents.diagnostics.AgentHelperStatus
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeed
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.host.LegacySignInStatus
import io.github.eslamasabry.oc.engine.agents.ownership.AgentOwnership
import io.github.eslamasabry.oc.engine.agents.readiness.AgentReadiness
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentHost
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// The facts of the named test/phone_agents_controller_test.dart tests (BD13, BA4, BA1, BA5, deletion, sign-in reset),
// asserted against the native owners; PhoneAgentsSession composes the close paths that had no owner.

private val LOCAL: OwnerId = ProfileId("local")
private val claude = AgentCatalog.builtIn.byId(AgentId.CLAUDE)!!

private object SessionClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
}

/** The Dart `w.events.log` host: every step is logged in order. */
private class LoggingHost(private val log: MutableList<String>) : FakeAgentHost() {
    var legacy: LegacySignInStatus = LegacySignInStatus.SignedIn
    var legacyReads = 0

    override suspend fun stop(owner: OwnerId, guardedForUpdate: Boolean): Outcome<Unit> {
        log += "host.stop"
        return super.stop(owner, guardedForUpdate)
    }

    override suspend fun legacySignInStatus(owner: OwnerId, agent: AgentId): Outcome<LegacySignInStatus> {
        legacyReads++
        return legacy.ok()
    }
}

private class World(scope: TestScope) {
    val log = mutableListOf<String>()
    val host = LoggingHost(log)
    var opens = 0
    val feed = ChatFeed(emptyList(), scope.backgroundScope)
    fun session(owner: OwnerId = LOCAL): PhoneAgentsSession {
        opens++
        return PhoneAgentsSession(
            owner = owner,
            host = host,
            auth = AgentAuth(host, owner, SessionClock),
            cancelInstall = { log += "host.cancelInstall"; Unit.ok() },
            disposeHost = { log += "host.dispose" },
        ).also { it.attachFeed(feed) }
    }
}

private fun readyRow() = AgentReadiness.row(
    claude,
    AgentArchitecture.Arm64,
    ServerCapabilities(),
    PhoneAgentRuntime(AgentId.CLAUDE, installed = true, hostAvailable = true, architectureQualified = true, signInPhase = AgentSignInPhase.SignedIn),
)

private fun assertAll(expected: Boolean, c: AgentCapabilities, label: String) {
    assertEquals(expected, c.resumeVerified, label)
    assertEquals(expected, c.modelList, label)
    assertEquals(expected, c.permissions, label)
    assertEquals(expected, c.images, label)
    assertEquals(expected, c.cancel, label)
}

class PhoneAgentsSessionTest {
    @Test
    fun `B-LINUX-127 BD13 helper evidence is read-only and rejects late disposed results`() = runTest {
        val w = World(this)
        val session = w.session()
        val expected = AgentHelperStatus(
            running = false,
            lastExitAtEpochMillis = 1_791_590_400_000, // 2026-10-09 UTC
            lastExitCode = 137,
            lastStopRequested = false,
            possibleResourceKill = true,
        )
        val startsBefore = w.host.calls.count { it.startsWith("ensureRunning") }
        assertSame(expected, session.helperStatus { expected })
        assertEquals(startsBefore, w.host.calls.count { it.startsWith("ensureRunning") })
        val delayed = CompletableDeferred<AgentHelperStatus>()
        val pending = async { session.helperStatus { delayed.await() } }
        runCurrent()
        session.close(PhoneAgentsCloseReason.ProfileDeletion)
        delayed.complete(expected)
        assertNull(pending.await())
    }

    @Test
    fun `B-LINUX-127 BA4 gives arm64 Claude the connected helper certification`() {
        // The connected helper is the pinned Paseo release (AgentHelper starts exactly pins.paseo.version).
        val helper = AgentPins.builtIn.paseo.version
        val certification = AgentCertification.bundled
        assertAll(true, certification.capabilitiesFor(claude, AgentArchitecture.Arm64, helper), "claude")
        for (descriptor in AgentCatalog.builtIn.agents) {
            if (descriptor.id == AgentId.CLAUDE) continue
            assertAll(false, certification.capabilitiesFor(descriptor, AgentArchitecture.Arm64, helper), descriptor.id.raw)
        }
    }

    @Test
    fun `B-LINUX-127 BA1 missing multi-agent bridge preserves direct Claude status only`() = runTest {
        val w = World(this)
        w.host.authResult = { _, _, _ -> AuthProjectionJson.failed(AuthProbeError.ProbeUnsupported).ok() }
        w.host.legacy = LegacySignInStatus.SignedIn
        val auth = w.session().auth
        auth.recheck(AgentId.CLAUDE)
        assertEquals(AuthState.SignedIn, auth.account(AgentId.CLAUDE)?.state)
        assertNull(auth.account(AgentId.CLAUDE)?.account)
        // Only Claude has the direct status read: another agent stays a typed error with no fallback.
        val codex = auth.recheck(AgentId("codex"))
        assertEquals(AuthState.Error, codex.state)
        assertEquals(AuthProbeError.ProbeUnsupported, codex.error)
        assertEquals(1, w.host.legacyReads)
    }

    @Test
    fun `B-LINUX-128 BA5 protocol switch keeps host account and conversation rows`() = runTest {
        val w = World(this)
        val local = ProfileId("local")
        val two = ProfileId("two")
        val ownership = AgentOwnership(FakeAgentPrefs())
        assertEquals(local, (ownership.migrate(listOf(local, two)) as Outcome.Ok).value)
        val sessions = PhoneAgentSessions(ownership) { w.session(it) }
        w.host.authResult = { _, _, _ -> AuthProjection(AuthState.SignedIn).ok() }
        val original = sessions.sessionFor(local)
        original.auth.recheck(AgentId.CLAUDE)
        original.publishRows(listOf(readyRow()))
        val owner = original.owner
        val hosts = w.opens

        val switched = sessions.sessionFor(two)
        assertEquals(owner, switched.owner)
        assertSame(original, switched)
        assertEquals(AuthState.SignedIn, switched.auth.account(AgentId.CLAUDE)?.state)
        assertEquals(hosts, w.opens)
        assertTrue(w.feed.refresh() is Outcome.Ok) // the conversation feed is not closed by the switch

        val back = sessions.sessionFor(local)
        assertEquals(owner, back.owner)
        assertEquals(hosts, w.opens)
        assertTrue(back.state.value.rows.single { it.id == AgentId.CLAUDE }.chatSelectable)
    }

    @Test
    fun `B-LINUX-128 deletion closes auth, setup, host and feeds before ProfileStore`() = runTest {
        val w = World(this)
        val session = w.session()
        w.host.authResult = { _, _, _ -> AuthProjection(AuthState.SignedIn).ok() }
        session.auth.recheck(AgentId.CLAUDE)
        assertEquals(AuthState.SignedIn, session.auth.account(AgentId.CLAUDE)?.state)
        session.publishRows(listOf(readyRow()))
        w.log.clear()

        session.close(PhoneAgentsCloseReason.ProfileDeletion)
        // The deletion transaction sweeps profile storage only after its closers return.
        w.log += "profileStore.cleanup"
        assertEquals(listOf("host.cancelInstall", "host.stop", "host.dispose", "profileStore.cleanup"), w.log)
        assertEquals(Failure.Refused(RefusalReason.ProfileClosing), (w.feed.refresh() as Outcome.Failed).failure)
        assertTrue(session.state.value.rows.isEmpty())
        assertFalse(session.state.value.needsRestart)
        // Auth answers after close are dropped: nothing new is published.
        w.host.authResult = { _, _, _ -> AuthProjection(AuthState.SignedOut).ok() }
        session.auth.recheck(AgentId.CLAUDE)
        assertEquals(AuthState.SignedIn, session.auth.account(AgentId.CLAUDE)?.state)
    }

    @Test
    fun `B-LINUX-128 clearing saved sign-ins closes clients and asks for a restart`() = runTest {
        val w = World(this)
        val session = w.session()
        session.publishRows(listOf(readyRow()))
        assertFalse(session.state.value.needsRestart)
        session.close(PhoneAgentsCloseReason.SignInReset)
        assertTrue(session.state.value.needsRestart)
        assertFalse(session.state.value.available)
        assertTrue(session.state.value.rows.isEmpty())
        assertEquals(listOf("host.cancelInstall", "host.stop", "host.dispose"), w.log)
        assertIs<Outcome.Failed>(w.feed.refresh())
        assertEquals(Failure.Refused(RefusalReason.ProfileClosing), (session.admitChat() as Outcome.Failed).failure)
    }
}
