package io.github.eslamasabry.oc.android.background

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Ports test/sign_in_foreground_causes_test.dart (B-ANDR-081): what the person reads under Details names why Android
// refused or stopped the service. The terminal starts only on Ready (LocalTerminalSessions._launchSignIn).
@OptIn(ExperimentalCoroutinesApi::class)
class SignInCausesTest {
    /** Flutter `_Android`: `enable` runs the service unless a start failure is set; status follows it. */
    private class Android(scope: CoroutineScope) {
        var pause: PauseState = unpaused
        var startFailure: String? = null
        var running = false
        val port =
            FakeLivePort().apply {
                onPause = { ok(pause) }
                onEnable = {
                    running = startFailure == null
                    ok(current())
                }
                onDisable = {
                    running = false
                    ok(current())
                }
                onStatus = { ok(current()) }
            }
        val controller = liveMode(scope, port)

        fun current() = status(running).copy(startFailure = startFailure)
    }

    /** What the terminal would show: started, or the coded admission failure. */
    private suspend fun startSignIn(android: Android): Pair<ForegroundLease, String?> {
        val lease = android.controller.reserve()
        return when (val admission = lease.ready()) {
            LeaseAdmission.Ready -> lease to null
            is LeaseAdmission.Refused -> lease to SignInCodes.detail(SignInCodes.STEP_ADMISSION, admission.loss)
        }
    }

    @Test
    fun `B-ANDR-081 a service Android did not start is named, not called lost`() = runTest {
        for ((failure, code) in listOf(
            "ackTimeout" to "service-not-started",
            "refused" to "service-refused",
            "receiptUnsaved" to "service-receipt-unsaved",
        )) {
            val android = Android(backgroundScope)
            android.startFailure = failure
            val (lease, detail) = startSignIn(android)
            assertEquals("sign-in-admission/$code", detail)
            assertFalse(lease.active, "the terminal never started")
            lease.release()
        }
    }

    @Test
    fun `B-ANDR-081 a durable pause does not stop a sign-in the person asked for`() = runTest {
        for (reason in listOf(PauseReason.AppUpdated, PauseReason.UserStopped, PauseReason.TimeLimit)) {
            val android = Android(backgroundScope)
            android.pause = pausedFor(reason, at = 1_791_417_600_000L)
            val (lease, detail) = startSignIn(android)
            assertEquals(null, detail, reason.name)
            assertTrue(lease.active, reason.name)
            assertTrue("enable" in android.port.calls)
            lease.release()
            assertFalse(android.running, "released after the sign-in")
        }
    }

    @Test
    fun `B-ANDR-081 a running sign-in says what stopped its service`() = runTest {
        val android = Android(backgroundScope)
        var (lease, detail) = startSignIn(android)
        assertEquals(null, detail)
        var lost = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            lease.lost.collect { lost += SignInCodes.detail(SignInCodes.STEP_LOST, it) }
        }
        android.controller.onServiceStopped(
            LiveModeController.REASON_SYSTEM_TIMEOUT,
            pausedFor(PauseReason.TimeLimit, at = 1_791_417_600_000L),
        )
        runCurrent()
        assertEquals(listOf<String?>("sign-in-lost/android-time-limit"), lost)
        lease.release()

        android.pause = unpaused
        val second = startSignIn(android)
        lease = second.first
        assertTrue(lease.active)
        lost = mutableListOf()
        val watched = lease
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            watched.lost.collect { lost += SignInCodes.detail(SignInCodes.STEP_LOST, it) }
        }
        android.running = false
        android.controller.refreshStatus()
        runCurrent()
        assertEquals(listOf<String?>("sign-in-lost/service-gone"), lost)
        assertIs<ForegroundLease>(lease)
    }
}
