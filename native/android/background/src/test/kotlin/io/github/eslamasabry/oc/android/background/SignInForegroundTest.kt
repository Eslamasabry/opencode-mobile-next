package io.github.eslamasabry.oc.android.background

import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Ports test/background_signin_foreground_test.dart (B-ANDR-019, -020) on the real LiveModeController.
@OptIn(ExperimentalCoroutinesApi::class)
class SignInForegroundTest {
    private class World(scope: CoroutineScope) {
        var pause: PauseState = unpaused
        var pauseGate: CompletableDeferred<ServiceReply<PauseState>>? = null
        var enableGate: CompletableDeferred<ServiceStatus>? = null
        var enableError: String? = null
        var disableError: String? = null
        var ackActive = true
        var nativeActive = false
        var disableAckActive = false
        val optIn = FakeOptIn()
        val support = MutableSupport()
        val port =
            FakeLivePort().apply {
                onPause = { pauseGate?.await() ?: ok(pause) }
                onEnable = {
                    val error = enableError
                    val gate = enableGate
                    when {
                        error != null -> refused(error)
                        gate != null -> gate.await().let { receipt -> nativeActive = receipt.active; ok(receipt) }
                        else -> ok(status(ackActive)).also { nativeActive = ackActive }
                    }
                }
                onDisable = {
                    val error = disableError
                    if (error != null) refused(error) else ok(status(disableAckActive)).also { nativeActive = disableAckActive }
                }
                onStatus = { ok(status(nativeActive)) }
            }
        val controller = liveMode(scope, port, optIn, support)
        val calls: List<String> get() = port.calls

        fun count(method: String) = calls.count { it == method }
    }

    private fun TestScope.world() = World(backgroundScope)

    private fun TestScope.losses(lease: ForegroundLease): MutableList<ForegroundLoss> {
        val seen = mutableListOf<ForegroundLoss>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { lease.lost.collect { seen += it } }
        return seen
    }

    private suspend fun assertRefused(lease: ForegroundLease, reason: ForegroundFailure, code: String? = null) {
        val admission = assertIs<LeaseAdmission.Refused>(lease.ready())
        assertEquals(reason, admission.loss.reason)
        if (code != null) assertEquals(code, admission.loss.code)
    }

    @Test
    fun `B-ANDR-019 a sign-in lease acknowledges foreground without changing the person's preference`() = runTest {
        val w = world()
        val lease = w.controller.reserve()
        assertFalse(lease.active)
        assertEquals(LeaseAdmission.Ready, lease.ready())
        assertTrue(lease.active)
        assertTrue(w.controller.state.value.active)
        assertFalse(w.controller.state.value.enabled)
        assertNull(w.optIn.value)
        assertEquals(listOf("getBackgroundPause", "enable"), w.calls)
        assertIs<Outcome.Ok<Unit>>(lease.release())
        assertFalse(lease.active)
        assertEquals(listOf("getBackgroundPause", "enable", "disable"), w.calls)
        assertNull(w.optIn.value)
    }

    @Test
    fun `B-ANDR-019 concurrent reservations join one native enable and the last release stops it`() = runTest {
        val w = world()
        w.enableGate = CompletableDeferred()
        val first = w.controller.reserve()
        val second = w.controller.reserve()
        runCurrent()
        assertEquals(listOf("getBackgroundPause", "enable"), w.calls)
        assertFalse(first.active)
        assertFalse(second.active)
        w.enableGate!!.complete(status(true))
        assertEquals(LeaseAdmission.Ready, first.ready())
        assertEquals(LeaseAdmission.Ready, second.ready())
        first.release()
        assertTrue(second.active)
        assertEquals(0, w.count("disable"))
        second.release()
        assertEquals(1, w.count("disable"))
    }

    @Test
    fun `B-ANDR-019 an already active service is borrowed, never enabled or disabled`() = runTest {
        val w = world()
        w.pause = running
        w.nativeActive = true
        val lease = w.controller.reserve()
        assertEquals(LeaseAdmission.Ready, lease.ready())
        lease.release()
        assertEquals(listOf("getBackgroundPause"), w.calls)
        assertTrue(w.controller.state.value.active)
        assertFalse(w.controller.state.value.enabled)
        assertNull(w.optIn.value)
    }

    @Test
    fun `B-ANDR-019 a durable pause still lets the sign-in the person asked for start the service`() = runTest {
        val w = world()
        w.pause = pausedFor(at = 1_791_417_600_000L)
        val lease = w.controller.reserve()
        assertEquals(LeaseAdmission.Ready, lease.ready())
        assertTrue(lease.active)
        assertFalse(w.controller.state.value.enabled)
        lease.release()
        assertEquals(listOf("getBackgroundPause", "enable", "disable"), w.calls)
        assertNull(w.optIn.value)
    }

    @Test
    fun `B-ANDR-020 an unknown pause status never starts foreground work`() = runTest {
        val w = world()
        w.pause = PauseState.Unsupported
        val lease = w.controller.reserve()
        assertRefused(lease, ForegroundFailure.Unsupported)
        assertFalse(lease.active)
        assertEquals(listOf("getBackgroundPause"), w.calls)
        lease.release()
    }

    @Test
    fun `B-ANDR-020 notification denial and an inactive acknowledgement fail with typed reasons`() = runTest {
        for (permission in listOf(true, false)) {
            val w = world()
            if (permission) w.enableError = ServiceRefusal.NOTIFICATION_DENIED else w.ackActive = false
            val lease = w.controller.reserve()
            assertRefused(lease, if (permission) ForegroundFailure.Permission else ForegroundFailure.Unavailable)
            assertFalse(lease.active)
            assertFalse(w.controller.state.value.enabled)
            assertNull(w.optIn.value)
            lease.release()
        }
    }

    @Test
    fun `B-ANDR-020 a refused admission names its fixed step, never platform text`() = runTest {
        var w = world()
        w.ackActive = false
        var lease = w.controller.reserve()
        assertRefused(lease, ForegroundFailure.Unavailable, "service-not-started")
        lease.release()

        w = world()
        w.enableError = ServiceRefusal.PERMISSION_IN_PROGRESS
        lease = w.controller.reserve()
        assertRefused(lease, ForegroundFailure.Unavailable, "service-permission_in_progress")
        lease.release()

        w = world()
        w.enableError = "Private /data/path"
        lease = w.controller.reserve()
        assertRefused(lease, ForegroundFailure.Unavailable, "service-platform")
        lease.release()
    }

    @Test
    fun `B-ANDR-020 cancellation before the pause receipt fails readiness promptly and sends no enable`() = runTest {
        val w = world()
        w.pauseGate = CompletableDeferred()
        val lease = w.controller.reserve()
        runCurrent()
        lease.release()
        assertRefused(lease, ForegroundFailure.Cancelled)
        w.pauseGate!!.complete(ok(unpaused))
        runCurrent()
        assertEquals(listOf("getBackgroundPause"), w.calls)
    }

    @Test
    fun `B-ANDR-020 cancellation during the native start ignores and cleans a late successful acknowledgement`() = runTest {
        val w = world()
        w.enableGate = CompletableDeferred()
        val lease = w.controller.reserve()
        runCurrent()
        lease.release()
        assertRefused(lease, ForegroundFailure.Cancelled)
        w.enableGate!!.complete(status(true))
        runCurrent()
        assertFalse(lease.active)
        assertEquals(listOf("getBackgroundPause", "enable", "disable"), w.calls)
        assertFalse(w.controller.state.value.enabled)
        assertFalse(w.controller.state.value.active)
    }

    @Test
    fun `B-ANDR-020 an explicit enable during a lease keeps the service after release`() = runTest {
        val w = world()
        val lease = w.controller.reserve()
        lease.ready()
        assertTrue(w.controller.setEnabled(true))
        lease.release()
        assertTrue(w.controller.state.value.enabled)
        assertTrue(w.controller.state.value.active)
        assertEquals(true, w.optIn.value)
        assertEquals(0, w.count("disable"))
    }

    @Test
    fun `B-ANDR-020 an explicit enable while the acknowledgement is pending joins the activation`() = runTest {
        val w = world()
        w.enableGate = CompletableDeferred()
        val lease = w.controller.reserve()
        runCurrent()
        val enabling = async { w.controller.setEnabled(true) }
        runCurrent()
        w.enableGate!!.complete(status(true))
        assertEquals(LeaseAdmission.Ready, lease.ready())
        assertTrue(enabling.await())
        lease.release()
        assertEquals(1, w.count("enable"))
        assertEquals(0, w.count("disable"))
        assertEquals(true, w.optIn.value)
    }

    @Test
    fun `B-ANDR-020 an explicit disable loses the lease at once and prevents a restart`() = runTest {
        val w = world()
        val lease = w.controller.reserve()
        lease.ready()
        val losses = losses(lease)
        assertFalse(w.controller.setEnabled(false))
        assertFalse(lease.active)
        assertEquals(1, losses.size)
        assertEquals("live-turned-off", losses.single().code)
        lease.release()
        assertEquals(1, w.count("disable"))
        assertEquals(1, w.count("enable"))
    }

    @Test
    fun `B-ANDR-020 a native timeout loses every lease without restarting`() = runTest {
        val w = world()
        val lease = w.controller.reserve()
        lease.ready()
        val losses = losses(lease)
        w.controller.onServiceStopped(LiveModeController.REASON_SYSTEM_TIMEOUT, pausedFor(at = 1_791_417_600_000L))
        assertFalse(lease.active)
        assertEquals(1, losses.size)
        lease.release()
        assertEquals(listOf("getBackgroundPause", "enable"), w.calls)
    }

    @Test
    fun `B-ANDR-020 an inactive native status invalidates an active lease`() = runTest {
        val w = world()
        val lease = w.controller.reserve()
        lease.ready()
        w.nativeActive = false
        w.controller.refreshStatus()
        assertFalse(lease.active)
        lease.release()
        assertFalse(w.controller.state.value.enabled)
        assertEquals(1, w.count("enable"))
    }

    @Test
    fun `B-ANDR-020 dispose cancels admission and cleans a late native acknowledgement`() = runTest {
        val w = world()
        w.enableGate = CompletableDeferred()
        val lease = w.controller.reserve()
        runCurrent()
        w.controller.dispose()
        assertRefused(lease, ForegroundFailure.Cancelled, "app-closed")
        w.enableGate!!.complete(status(true))
        runCurrent()
        assertFalse(lease.active)
        assertEquals(1, w.count("disable"))
        lease.release()
    }

    @Test
    fun `B-ANDR-020 an active disposal waits for the terminal's cleanup to release the service`() = runTest {
        val w = world()
        val lease = w.controller.reserve()
        lease.ready()
        val losses = losses(lease)
        w.controller.dispose()
        assertFalse(lease.active)
        assertEquals(1, losses.size)
        assertEquals(listOf("getBackgroundPause", "enable"), w.calls)
        lease.release()
        assertEquals(listOf("getBackgroundPause", "enable", "disable"), w.calls)
    }

    @Test
    fun `B-ANDR-020 a failed owned stop reports a typed failure and stays eligible for retry`() = runTest {
        val w = world()
        val lease = w.controller.reserve()
        lease.ready()
        w.disableError = "unavailable"
        assertIs<Outcome.Failed>(lease.release())
        assertFalse(lease.active)
        w.disableError = null
        assertIs<Outcome.Ok<Unit>>(lease.release())
        assertEquals(2, w.count("disable"))
        assertFalse(w.controller.state.value.active)
    }

    @Test
    fun `B-ANDR-020 a person's disable wins a busy native enable and cleans its late receipt`() = runTest {
        val w = world()
        w.enableGate = CompletableDeferred()
        val enabling = async { w.controller.setEnabled(true) }
        runCurrent()
        assertTrue(w.controller.state.value.busy)
        assertTrue(w.controller.state.value.enabled)
        assertFalse(w.controller.setEnabled(false))
        assertFalse(w.controller.state.value.enabled)
        assertEquals(false, w.optIn.value)
        assertEquals(listOf("enable", "disable"), w.calls)
        w.enableGate!!.complete(status(true))
        assertFalse(enabling.await())
        assertFalse(w.controller.state.value.enabled)
        assertFalse(w.controller.state.value.active)
        assertEquals(listOf("enable", "disable", "disable"), w.calls)
        assertEquals(false, w.optIn.value)
    }

    @Test
    fun `B-ANDR-020 platform support lost while reading the pause fails the pending admission`() = runTest {
        val w = world()
        w.pauseGate = CompletableDeferred()
        val lease = w.controller.reserve()
        runCurrent()
        w.support.supported = false
        w.pauseGate!!.complete(ok(unpaused))
        assertRefused(lease, ForegroundFailure.Unsupported)
        lease.release()
        assertFalse(lease.active)
        assertEquals(listOf("getBackgroundPause"), w.calls)
    }

    @Test
    fun `B-ANDR-020 an active disable receipt reports a typed failure and retries the owned stop`() = runTest {
        val w = world()
        val lease = w.controller.reserve()
        lease.ready()
        w.disableAckActive = true
        assertIs<Outcome.Failed>(lease.release())
        assertFalse(lease.active)
        assertTrue(w.controller.state.value.active)
        w.disableAckActive = false
        assertIs<Outcome.Ok<Unit>>(lease.release())
        assertEquals(2, w.count("disable"))
        assertFalse(w.controller.state.value.active)
    }
}
