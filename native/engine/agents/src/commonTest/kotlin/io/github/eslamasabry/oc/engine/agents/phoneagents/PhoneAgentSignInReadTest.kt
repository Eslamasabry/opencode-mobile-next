package io.github.eslamasabry.oc.engine.agents.phoneagents

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.auth.AgentAuth
import io.github.eslamasabry.oc.engine.agents.auth.AuthProjectionJson
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.host.AuthAction
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.host.LegacySignInStatus
import io.github.eslamasabry.oc.engine.agents.readiness.AgentReadiness
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentStatus
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentHost
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

// Port of test/phone_agents_legacy_auth_test.dart (the row-refresh sign-in branch; the ConnectionController around it
// is not native yet).

private val OWNER: OwnerId = ProfileId("phone")
private val claude = AgentCatalog.builtIn.byId(AgentId.CLAUDE)!!

private object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
}

/** A host without the multi-agent auth bridge: the probe is unsupported, the native status answers. */
private class LegacyHost(private val legacy: LegacySignInStatus) : FakeAgentHost() {
    var nativeReads = 0

    init {
        authResult = { _, _, _ -> AuthProjectionJson.failed(AuthProbeError.ProbeUnsupported).ok() }
    }

    override suspend fun legacySignInStatus(owner: OwnerId, agent: AgentId): Outcome<LegacySignInStatus> {
        nativeReads++
        return legacy.ok()
    }
}

/** What the Dart `_LegacyHost.inspect` reports for Claude. */
private fun inspected(phase: AgentSignInPhase?) = PhoneAgentRuntime(
    agentId = AgentId.CLAUDE,
    installed = true,
    hostAvailable = true,
    architectureQualified = true,
    signInPhase = phase,
)

private fun status(runtime: PhoneAgentRuntime): PhoneAgentStatus =
    AgentReadiness.row(claude, AgentArchitecture.Arm64, ServerCapabilities(), runtime).status

class PhoneAgentSignInReadTest {
    @Test
    fun `B-LINUX-138 legacy host sign-in truth publishes inventory without a native read`() = runTest {
        val host = LegacyHost(LegacySignInStatus.SignedOut)
        val auth = AgentAuth(host, OWNER, FixedClock)
        val runtime = PhoneAgentSignInRead.apply(claude, inspected(AgentSignInPhase.SignedIn), hostAnswersAuth = false) {
            auth.refresh(it)
        }
        assertEquals(0, host.nativeReads)
        assertEquals(PhoneAgentStatus.Ready, status(runtime))
    }

    @Test
    fun `B-LINUX-138 legacy host without sign-in truth still reads native status`() = runTest {
        val host = LegacyHost(LegacySignInStatus.SignedOut)
        val auth = AgentAuth(host, OWNER, FixedClock)
        val runtime = PhoneAgentSignInRead.apply(claude, inspected(null), hostAnswersAuth = false) { auth.refresh(it) }
        assertEquals(1, host.nativeReads)
        assertEquals(AgentSignInPhase.SignedOut, runtime.signInPhase)
        assertEquals(PhoneAgentStatus.SignedOut, status(runtime))
    }

    @Test
    fun `B-LINUX-138 auth-capable host refreshes despite an inspected signed-in phase`() = runTest {
        val host = LegacyHost(LegacySignInStatus.SignedIn)
        var result = AuthState.SignedOut
        host.authResult = { _, _, action ->
            assertEquals(AuthAction.Probe, action)
            AuthProjection(result).ok()
        }
        val auth = AgentAuth(host, OWNER, FixedClock)
        // Each refresh asks the agent itself (a "Check again" read, so no reuse of the last answer).
        val read: suspend (AgentId) -> AuthProjection = { auth.recheck(it) }
        val first = PhoneAgentSignInRead.apply(claude, inspected(AgentSignInPhase.SignedIn), hostAnswersAuth = true, read)
        assertEquals(1, host.calls.count { it.startsWith("auth:claude") })
        assertEquals(0, host.nativeReads)
        assertEquals(PhoneAgentStatus.SignedOut, status(first))
        result = AuthState.SignedIn
        val second = PhoneAgentSignInRead.apply(claude, inspected(AgentSignInPhase.SignedIn), hostAnswersAuth = true, read)
        assertEquals(2, host.calls.count { it.startsWith("auth:claude") })
        assertEquals(PhoneAgentStatus.Ready, status(second))
    }

    @Test
    fun `B-LINUX-138 a signed-in answer keeps an inspected usage limit and uninstalled agents are not read`() = runTest {
        var reads = 0
        val read: suspend (AgentId) -> AuthProjection = { reads++; AuthProjection(AuthState.SignedIn) }
        val limited = PhoneAgentSignInRead.apply(claude, inspected(AgentSignInPhase.LimitReached), true, read)
        assertEquals(AgentSignInPhase.LimitReached, limited.signInPhase)
        val notInstalled = PhoneAgentSignInRead.apply(claude, inspected(null).copy(installed = false), true, read)
        assertEquals(null, notInstalled.signInPhase)
        assertEquals(1, reads)
        assertEquals(
            AgentSignInPhase.Failed,
            PhoneAgentSignInRead.phase(AuthProjectionJson.failed(AuthProbeError.InvalidResponse), AgentSignInPhase.SignedIn),
        )
    }
}
