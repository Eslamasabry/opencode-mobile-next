package io.github.eslamasabry.oc.engine.agents.auth

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.host.AuthAction
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.host.LegacySignInStatus
import io.github.eslamasabry.oc.engine.agents.host.TransientLabel
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentHost
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val OWNER: OwnerId = ProfileId("phone")
private val CLAUDE = AgentId.CLAUDE
private val FX = AgentId("fx")
private val CODEX = AgentId("codex")

/** A clock on the test's virtual time. */
private class TestClock(private val scope: TestScope, var offset: kotlin.time.Duration = kotlin.time.Duration.ZERO) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000 + scope.currentTime) + offset
}

/** Records every native auth call and lets a test hold one open. */
private class AuthHost : FakeAgentHost() {
    val authCalls = mutableListOf<Pair<AgentId, AuthAction>>()
    var peak = 0
    private var active = 0
    var answers: MutableMap<AgentId, AuthProjection> = mutableMapOf()
    var signOutAnswer: AuthProjection = AuthProjection(AuthState.SignedOut)
    var gate: CompletableDeferred<Unit>? = null
    var hang = false
    var fail = false
    var legacy: LegacySignInStatus = LegacySignInStatus.Unknown
    var legacyCalls = 0
    val scripted = ArrayDeque<AuthProjection>()

    init {
        authResult = { _, agent, action ->
            authCalls += agent to action
            active++
            peak = maxOf(peak, active)
            try {
                gate?.await()
                if (hang) awaitCancellation()
                if (fail) {
                    Failure.Device().failed()
                } else if (action == AuthAction.SignOut) {
                    signOutAnswer.ok()
                } else {
                    (scripted.removeFirstOrNull() ?: answers[agent] ?: AuthProjection(AuthState.SignedOut)).ok()
                }
            } finally {
                active--
            }
        }
    }

    override suspend fun legacySignInStatus(owner: OwnerId, agent: AgentId): Outcome<LegacySignInStatus> {
        legacyCalls++
        return legacy.ok()
    }
}

class AgentAuthTest {
    private fun TestScope.auth(host: AuthHost, clock: TestClock = TestClock(this)) = AgentAuth(host, OWNER, clock)

    @Test
    fun `B-AGENT-004 installed agents report explicit account states from the narrow projection`() = runTest {
        val host = AuthHost()
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedIn, TransientLabel("Example account"))
        host.answers[FX] = AuthProjection(AuthState.SignedOut)
        host.answers[CODEX] = AuthProjectionJson.failed(AuthProbeError.NotInstalled)
        val auth = auth(host)
        assertEquals(AuthState.SignedIn, auth.recheck(CLAUDE).state)
        assertEquals("Example account", auth.account(CLAUDE)?.account?.value)
        assertEquals(AuthState.SignedOut, auth.recheck(FX).state)
        val codex = auth.recheck(CODEX)
        assertEquals(AuthState.Error, codex.state)
        assertEquals(AuthProbeError.NotInstalled, codex.error)
        assertFalse("Example" in auth.account(CLAUDE).toString(), "an account label is never printed")
    }

    @Test
    fun `B-LINUX-130 the probe asks the owner home, Claude gets 65 s and others 10 s, and a hung CLI times out`() = runTest {
        val host = AuthHost()
        val auth = auth(host)
        auth.recheck(CLAUDE)
        auth.recheck(FX)
        assertEquals(
            listOf("auth:claude:Probe:${AgentAuth.CLAUDE_PROBE_TIMEOUT}", "auth:fx:Probe:${AgentAuth.PROBE_TIMEOUT}"),
            host.calls.filter { it.startsWith("auth:") },
        )
        host.hang = true
        val start = currentTime
        assertEquals(AuthProbeError.TimedOut, auth.recheck(CODEX).error)
        assertEquals(10_000, currentTime - start, "every check ends within ten seconds")
        val claudeStart = currentTime
        assertEquals(AuthState.SignedOut, auth.recheck(CLAUDE).state, "a timed-out read keeps the last answer")
        assertEquals(65_000, currentTime - claudeStart)
    }

    @Test
    fun `B-LINUX-130 an auth timeout differs from not signed in and a failed host call is HostUnavailable`() = runTest {
        val host = AuthHost()
        host.hang = true
        val auth = auth(host)
        val result = auth.recheck(FX)
        assertEquals(AuthState.Error, result.state)
        assertEquals(AuthProbeError.TimedOut, result.error)
        host.hang = false
        host.fail = true
        assertEquals(AuthProbeError.HostUnavailable, auth.recheck(CODEX).error)
    }

    @Test
    fun `B-LINUX-130 status checks asked at once take turns and two asks for one agent share one check`() = runTest {
        val host = AuthHost()
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedIn)
        host.answers[FX] = AuthProjection(AuthState.SignedIn)
        host.gate = CompletableDeferred()
        val auth = auth(host)
        val list = async { auth.refresh(CLAUDE) }
        val step = async { auth.refresh(CLAUDE) }
        val other = async { auth.refresh(FX) }
        runCurrent()
        host.gate!!.complete(Unit)
        assertTrue(listOf(list.await(), step.await(), other.await()).all { it.state == AuthState.SignedIn })
        assertEquals(1, host.peak, "never two native reads at once for one owner")
        assertEquals(listOf(CLAUDE, FX), host.authCalls.map { it.first })
        auth.recheck(CLAUDE)
        assertEquals(3, host.authCalls.size, "after it ends, Check again runs a fresh check")
    }

    @Test
    fun `B-LINUX-126 a list refresh reuses a recent answer, Check again always asks, an old answer is asked again`() = runTest {
        val host = AuthHost()
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedIn)
        val clock = TestClock(this)
        val auth = auth(host, clock)
        auth.refresh(CLAUDE)
        auth.refresh(CLAUDE)
        auth.refresh(CLAUDE)
        assertEquals(1, host.authCalls.size)
        auth.recheck(CLAUDE)
        assertEquals(2, host.authCalls.size)
        clock.offset = 11.minutes
        auth.refresh(CLAUDE)
        assertEquals(3, host.authCalls.size)
    }

    @Test
    fun `B-LINUX-126 sign-in completes only after a signed-in probe`() = runTest {
        val host = AuthHost()
        val auth = auth(host)
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedOut)
        assertFalse(auth.confirmSignIn(CLAUDE))
        host.answers[CLAUDE] = AuthProjectionJson.failed(AuthProbeError.InvalidResponse)
        assertFalse(auth.confirmSignIn(CLAUDE))
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedIn, TransientLabel("Example account"))
        assertTrue(auth.confirmSignIn(CLAUDE))
        assertEquals("Example account", auth.account(CLAUDE)?.account?.value)
    }

    @Test
    fun `B-LINUX-126 a check that could not run keeps a signed-in agent signed in`() = runTest {
        val host = AuthHost()
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedIn)
        val clock = TestClock(this)
        val auth = auth(host, clock)
        auth.recheck(CLAUDE)
        for (error in listOf(AuthProbeError.HostUnavailable, AuthProbeError.TimedOut)) {
            host.answers[CLAUDE] = AuthProjectionJson.failed(error)
            assertEquals(AuthState.SignedIn, auth.recheck(CLAUDE).state, error.name)
            assertEquals(AuthState.SignedIn, auth.account(CLAUDE)?.state, error.name)
        }
        // An answer from the agent itself still counts.
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedOut)
        clock.offset = 11.minutes
        assertEquals(AuthState.SignedOut, auth.refresh(CLAUDE).state)
        // With no earlier real answer, the could-not-run read stands as it is.
        host.answers[FX] = AuthProjectionJson.failed(AuthProbeError.HostUnavailable)
        assertEquals(AuthProbeError.HostUnavailable, auth.recheck(FX).error)
    }

    @Test
    fun `B-LINUX-126 a late older probe cannot complete a newer signed-out attempt`() = runTest {
        val host = AuthHost()
        val first = CompletableDeferred<Unit>()
        host.gate = first
        host.scripted += AuthProjection(AuthState.SignedIn)
        host.scripted += AuthProjection(AuthState.SignedOut)
        val auth = auth(host)
        val oldAttempt = async { auth.recheck(CLAUDE) }
        runCurrent()
        val confirm = async { auth.confirmSignIn(CLAUDE) }
        runCurrent()
        // The old read answers signed-in late; the newer read, queued behind it, says signed-out.
        host.gate = null
        first.complete(Unit)
        oldAttempt.await()
        assertFalse(confirm.await())
        assertEquals(AuthState.SignedOut, auth.account(CLAUDE)?.state)
    }

    @Test
    fun `B-AGENT-006 sign-out counts only when the following probe says signed out`() = runTest {
        val host = AuthHost()
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedIn, TransientLabel("Example account"))
        val auth = auth(host)
        auth.recheck(CLAUDE)
        host.signOutAnswer = AuthProjection(AuthState.SignedIn)
        assertEquals(SignOutResult.NotConfirmed, auth.signOut(CLAUDE))
        assertEquals(AuthState.SignedIn, auth.account(CLAUDE)?.state)
        host.signOutAnswer = AuthProjectionJson.failed(AuthProbeError.SignOutFailed)
        assertEquals(SignOutResult.NotConfirmed, auth.signOut(CLAUDE))
        assertEquals(AuthState.SignedIn, auth.account(CLAUDE)?.state, "an error is not news about the account")
        host.signOutAnswer = AuthProjection(AuthState.SignedOut)
        assertEquals(SignOutResult.SignedOut, auth.signOut(CLAUDE))
        assertEquals(AuthState.SignedOut, auth.account(CLAUDE)?.state)
        assertNull(auth.account(CLAUDE)?.account)
        assertEquals(3, host.authCalls.count { it == CLAUDE to AuthAction.SignOut })
        assertTrue("auth:claude:SignOut:${AgentAuth.SIGN_OUT_TIMEOUT}" in host.calls)
    }

    @Test
    fun `B-AGENT-006 sign-out is offered only where the bridge supports it`() = runTest {
        val host = AuthHost()
        val auth = auth(host)
        assertTrue(auth.canSignOut(CLAUDE))
        assertTrue(auth.canSignOut(FX))
        assertFalse(auth.canSignOut(CODEX))
        assertEquals(SignOutResult.Unsupported, auth.signOut(CODEX))
        assertTrue(host.authCalls.isEmpty())
    }

    @Test
    fun `B-AGENT-004 closing the profile drops late answers`() = runTest {
        val host = AuthHost()
        host.answers[CLAUDE] = AuthProjection(AuthState.SignedIn)
        val auth = auth(host)
        auth.close()
        auth.recheck(CLAUDE)
        assertNull(auth.account(CLAUDE))
    }
}

/** The status-read clause of B-AGENT-027/028 (the code relay is retired, NG8). */
class LegacySignInStatusTest {
    @Test
    fun `B-AGENT-027 without the multi-agent bridge Claude's direct status read stands and creates no login`() = runTest {
        val host = AuthHost()
        host.answers[CLAUDE] = AuthProjectionJson.failed(AuthProbeError.ProbeUnsupported)
        host.legacy = LegacySignInStatus.SignedIn
        val auth = AgentAuth(host, OWNER, TestClock(this))
        val result = auth.recheck(CLAUDE)
        assertEquals(AuthState.SignedIn, result.state)
        assertNull(result.account, "the direct read carries no account label")
        assertEquals(1, host.legacyCalls)
        assertTrue(host.calls.none { it.startsWith("signIn") }, "a status read never starts a sign-in")
        host.legacy = LegacySignInStatus.SignedOut
        assertEquals(AuthState.SignedOut, auth.recheck(CLAUDE).state)
    }

    @Test
    fun `B-AGENT-028 an unknown or rate-limited direct status is an error, and other agents never use it`() = runTest {
        val host = AuthHost()
        host.answers[CLAUDE] = AuthProjectionJson.failed(AuthProbeError.ProbeUnsupported)
        host.answers[FX] = AuthProjectionJson.failed(AuthProbeError.ProbeUnsupported)
        val auth = AgentAuth(host, OWNER, TestClock(this))
        for (status in listOf(LegacySignInStatus.Unknown, LegacySignInStatus.LimitReached)) {
            host.legacy = status
            assertEquals(AuthState.Error, auth.recheck(CLAUDE).state, status.name)
        }
        val calls = host.legacyCalls
        assertEquals(AuthProbeError.ProbeUnsupported, auth.recheck(FX).error)
        assertEquals(calls, host.legacyCalls)
    }

    @Test
    fun `B-AGENT-028 a missing host reads as unavailable, never signed out`() = runTest {
        val host = object : FakeAgentHost() {
            init {
                authResult = { _, _, _ -> Failure.Unavailable(FeatureId("builtinLinux")).failed() }
            }
        }
        val auth = AgentAuth(host, OWNER, TestClock(this))
        assertEquals(AuthProbeError.HostUnavailable, auth.recheck(FX).error)
    }
}
