package io.github.eslamasabry.oc.engine.agents.account

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// Port of test/agent_account_controller_test.dart with test/support/account_fakes.dart's FakeAccountSession.

private val signedOut = AgentRuntimeAccount(requiresSignIn = true)
private val signedIn = AgentRuntimeAccount(type = "chatgpt", email = "person@example.com", plan = "plus", requiresSignIn = true)
private val code = AccountDeviceCode("https://auth.openai.com/codex/device", "TEST-1234")

private object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)
}

private class FakeAccountSession : AgentAccountSession {
    override var active = true
    override var supported = true
    val notifications = MutableSharedFlow<AccountEvent>(extraBufferCapacity = 16)
    override val events: Flow<AccountEvent> get() = notifications
    var account = signedOut
    var buckets: List<AccountRateBucket> = emptyList()
    var tokens = AccountTokenUsage()
    var onLimits: (suspend () -> Outcome<ImmutableList<AccountRateBucket>>)? = null
    var onUsage: (suspend () -> Outcome<AccountTokenUsage>)? = null
    var onStart: (suspend () -> Outcome<AccountDeviceCode>)? = null
    var onCancel: (suspend () -> Outcome<Boolean>)? = null
    var starts = 0
    var cancels = 0
    var reads = 0
    var limitReads = 0
    var usageReads = 0
    var closed = false

    fun emit(event: AccountEvent) = check(notifications.tryEmit(event))

    override suspend fun read(): Outcome<AgentRuntimeAccount> {
        reads++
        return account.ok()
    }

    override suspend fun rateLimits(): Outcome<ImmutableList<AccountRateBucket>> {
        limitReads++
        return onLimits?.invoke() ?: buckets.toImmutableList().ok()
    }

    override suspend fun usage(): Outcome<AccountTokenUsage> {
        usageReads++
        return onUsage?.invoke() ?: tokens.ok()
    }

    override suspend fun startDeviceLogin(): Outcome<AccountDeviceCode> {
        starts++
        return onStart?.invoke() ?: code.ok()
    }

    override suspend fun cancelLogin(): Outcome<Boolean> {
        cancels++
        return onCancel?.invoke() ?: true.ok()
    }

    override suspend fun close() {
        closed = true
        active = false
    }
}

private class World(val scope: TestScope) {
    val session = FakeAccountSession()
    val controller = AgentAccountController(session, scope.backgroundScope, FixedClock)
    val state get() = controller.state.value
}

private fun world(scope: TestScope): World = World(scope).also { scope.runCurrent() }

class AgentAccountControllerTest {
    @Test
    fun `B-AGENT-002 refresh never starts sign-in and reported zero is preserved`() = runTest {
        val w = world(this)
        w.controller.refresh()
        assertTrue(w.controller.canSignIn)
        assertEquals(0, w.session.starts)
        assertEquals(0, w.session.limitReads)
        w.session.account = signedIn
        w.session.tokens = AccountTokenUsage(lifetimeTokens = 0)
        w.session.buckets = listOf(AccountRateBucket(null, AccountRateWindow(0, null, null), null))
        w.controller.refresh()
        assertFalse(w.controller.canSignIn)
        assertEquals(0L, w.state.usage!!.lifetimeTokens)
        assertNull(w.state.usage!!.peakDailyTokens)
        assertEquals(0, w.state.limits!!.single().primary!!.usedPercent)
        assertEquals(0, w.session.starts)
    }

    @Test
    fun `B-AGENT-002 independent unavailable usage leaves fresh account and limits`() = runTest {
        val w = world(this)
        w.session.account = signedIn
        w.session.buckets = listOf(AccountRateBucket("Codex", AccountRateWindow(25, 300, null), null))
        w.session.onUsage = { Failure.Unavailable(FeatureId("agentAccount")).failed() }
        w.controller.refresh()
        assertEquals(AccountPanelStatus.Ready, w.state.status)
        assertEquals(signedIn.email, w.state.account!!.email)
        assertEquals(25, w.state.limits!!.single().primary!!.usedPercent)
        assertNull(w.state.usage)
        assertFalse(w.state.metricsLoading)
        w.session.onUsage = null
        w.session.tokens = AccountTokenUsage(peakDailyTokens = 42)
        w.session.onLimits = { throw IllegalStateException("fixture") }
        w.controller.refresh()
        assertNull(w.state.limits)
        assertEquals(42L, w.state.usage!!.peakDailyTokens)
    }

    @Test
    fun `B-AGENT-002 unsupported runtime and API-key auth do not probe subscription metrics`() = runTest {
        val w = world(this)
        w.session.supported = false
        w.controller.refresh()
        assertEquals(AccountPanelStatus.Unavailable, w.state.status)
        assertEquals(0, w.session.reads)
        assertFalse(w.controller.canSignIn)
        w.session.supported = true
        w.session.account = AgentRuntimeAccount(type = "apiKey", requiresSignIn = true)
        w.controller.refresh()
        assertTrue(w.state.account!!.signedIn)
        assertEquals(0, w.session.limitReads)
        assertEquals(0, w.session.usageReads)
    }

    @Test
    fun `B-AGENT-003 owned completion before start response never restores the code`() = runTest {
        val w = world(this)
        w.controller.refresh()
        val held = CompletableDeferred<Outcome<AccountDeviceCode>>()
        w.session.onStart = { held.await() }
        val pending = async { w.controller.signIn() }
        runCurrent()
        assertEquals(AccountLoginStatus.Starting, w.state.loginStatus)
        w.session.account = signedIn
        w.session.emit(AccountEvent(AccountEventKind.LoginCompleted, success = true))
        held.complete(code.ok())
        pending.await()
        runCurrent()
        assertNull(w.state.deviceCode)
        assertEquals(AccountLoginStatus.Completed, w.state.loginStatus)
        assertTrue(w.state.account!!.signedIn)
    }

    @Test
    fun `B-AGENT-003 cancel while starting suppresses the late code until owned receipt settles`() = runTest {
        val w = world(this)
        w.controller.refresh()
        val held = CompletableDeferred<Outcome<AccountDeviceCode>>()
        w.session.onStart = { held.await() }
        w.session.onCancel = { false.ok() }
        val pending = async { w.controller.signIn() }
        runCurrent()
        w.controller.cancel()
        assertEquals(AccountLoginStatus.Uncertain, w.state.loginStatus)
        assertFalse(w.controller.canSignIn)
        held.complete(code.ok())
        pending.await()
        assertNull(w.state.deviceCode)
        w.session.emit(AccountEvent(AccountEventKind.LoginCancelled, success = true))
        runCurrent()
        assertEquals(AccountLoginStatus.Cancelled, w.state.loginStatus)
        assertTrue(w.controller.canSignIn)
    }

    @Test
    fun `B-AGENT-003 failed cancellation stays uncertain and allows only explicit retry`() = runTest {
        val w = world(this)
        w.controller.refresh()
        w.controller.signIn()
        w.session.onCancel = { throw IllegalStateException("fixture") }
        w.controller.cancel()
        assertNull(w.state.deviceCode)
        assertEquals(AccountLoginStatus.Uncertain, w.state.loginStatus)
        assertTrue(w.controller.canCancel)
        w.controller.signIn()
        assertEquals(1, w.session.starts)
        w.session.onCancel = { true.ok() }
        w.controller.cancel()
        assertEquals(AccountLoginStatus.Cancelled, w.state.loginStatus)
        assertEquals(2, w.session.cancels)
    }

    @Test
    fun `B-AGENT-003 account change clears stale metrics before delayed read completes`() = runTest {
        val w = world(this)
        val history = mutableListOf<AccountPanelState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { w.controller.state.collect { history += it } }
        w.session.account = signedIn
        val held = CompletableDeferred<Outcome<ImmutableList<AccountRateBucket>>>()
        w.session.onLimits = { held.await() }
        val oldRead = async { w.controller.refresh() }
        runCurrent()
        assertTrue(w.state.account!!.signedIn)
        val before = history.size
        w.session.account = signedOut
        w.session.emit(AccountEvent(AccountEventKind.Changed))
        runCurrent()
        // The first state after the notice has no identity or metrics (Dart: cleared synchronously).
        val cleared = history[before]
        assertNull(cleared.account)
        assertNull(cleared.limits)
        assertNull(cleared.usage)
        held.complete(persistentListOf(AccountRateBucket("Old account", AccountRateWindow(80, 60, null), null)).ok())
        oldRead.await()
        runCurrent()
        assertFalse(w.state.account!!.signedIn)
        assertNull(w.state.limits)
        assertTrue(history.none { s -> s.limits?.any { it.name == "Old account" } == true })
        assertEquals(0, w.session.usageReads)
    }

    @Test
    fun `B-AGENT-003 account change while waiting clears code and cancels only this session`() = runTest {
        val w = world(this)
        w.controller.refresh()
        w.controller.signIn()
        assertEquals(code, w.state.deviceCode)
        val held = CompletableDeferred<Outcome<Boolean>>()
        w.session.onCancel = { held.await() }
        w.session.account = signedIn
        w.session.emit(AccountEvent(AccountEventKind.Changed))
        runCurrent()
        assertNull(w.state.deviceCode)
        assertEquals(1, w.session.cancels)
        held.complete(false.ok())
        runCurrent()
        assertEquals(AccountLoginStatus.Uncertain, w.state.loginStatus)
        assertTrue(w.state.account!!.signedIn)
        assertEquals(1, w.session.starts)
    }

    @Test
    fun `B-AGENT-003 disconnect invalidates in-flight login and reads with no replay`() = runTest {
        val w = world(this)
        w.controller.refresh()
        val held = CompletableDeferred<Outcome<AccountDeviceCode>>()
        w.session.onStart = { held.await() }
        val pending = async { w.controller.signIn() }
        runCurrent()
        w.session.active = false
        w.session.emit(AccountEvent(AccountEventKind.Disconnected))
        runCurrent()
        held.complete(code.ok())
        pending.await()
        assertEquals(AccountPanelStatus.Disconnected, w.state.status)
        assertNull(w.state.deviceCode)
        assertNull(w.state.account)
        assertFalse(w.controller.canSignIn)
        assertFalse(w.controller.canCancel)
        w.controller.refresh()
        assertEquals(1, w.session.starts)
    }

    @Test
    fun `B-AGENT-003 disposing during login never publishes a late code`() = runTest {
        val w = world(this)
        w.controller.refresh()
        val held = CompletableDeferred<Outcome<AccountDeviceCode>>()
        w.session.onStart = { held.await() }
        val pending = async { w.controller.signIn() }
        runCurrent()
        w.controller.close()
        held.complete(code.ok())
        pending.await()
        runCurrent()
        assertTrue(w.session.closed)
        assertNull(w.state.deviceCode)
    }
}
