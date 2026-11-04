package io.github.eslamasabry.oc.android.background

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Ports test/background_pause_test.dart (B-ANDR-015, -016, -017). Every case starts from an old saved opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class PauseTest {
    private val optIn = FakeOptIn(true)

    @Test
    fun `B-ANDR-015 a paused live mode stays paused on cold start despite an old enabled preference`() = runTest {
        for (reason in PauseReason.entries - PauseReason.None) {
            val optIn = FakeOptIn(true)
            val port = FakeLivePort().apply { onPause = { ok(pausedFor(reason)) } }
            val c = liveMode(backgroundScope, port, optIn)
            c.restore()
            assertEquals(listOf("getBackgroundPause"), port.calls, reason.name)
            with(c.state.value) {
                assertFalse(enabled)
                assertFalse(active)
                assertEquals(reason, pause.reason)
                assertEquals(AT, pause.atMs)
                assertTrue(pause.canResume)
            }
            assertEquals(false, optIn.value)
        }
    }

    @Test
    fun `B-ANDR-015 an unreadable pause receipt cannot silently restore the old opt-in`() = runTest {
        val port = FakeLivePort().apply { onPause = { refused("storage") } }
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        assertEquals(listOf("getBackgroundPause"), port.calls)
        assertFalse(c.state.value.enabled)
        assertFalse(c.state.value.pause.supported)
        assertNull(c.state.value.lastError)
        assertEquals(true, optIn.value)
    }

    @Test
    fun `B-ANDR-015 an intentional opt-out clears a previously durable pause`() = runTest {
        val port =
            FakeLivePort().apply {
                onPause = { ok(pausedFor()) }
                all { ok(ServiceStatus(enabled = false, active = false)) }
            }
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        c.setEnabled(false)
        assertFalse(c.state.value.pause.paused)
        assertEquals(PauseReason.None, c.state.value.pause.reason)
    }

    @Test
    fun `B-ANDR-016 Resume clears a pause only after native foreground activation`() = runTest {
        val port = FakeLivePort().apply { onPause = { ok(pausedFor()) } }
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        val result = c.resume()
        assertNull(result.error)
        assertTrue(result.state.active)
        assertFalse(result.state.paused)
        assertEquals(PauseReason.None, result.state.reason)
        assertFalse(c.state.value.stoppedByAndroidTimeout)
        assertTrue(c.state.value.enabled)
        assertEquals(true, optIn.value)
        assertEquals(listOf("getBackgroundPause", "enable"), port.calls)
    }

    @Test
    fun `B-ANDR-016 an accepted but inactive start keeps the pause and answers resumeFailed`() = runTest {
        val port =
            FakeLivePort().apply {
                onPause = { ok(pausedFor()) }
                onEnable = { ok(status(false, pause = pausedFor(), enabled = true)) }
            }
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        val before = c.state.value.pause
        val result = c.resume()
        assertEquals(ResumeError.ResumeFailed, result.error)
        assertEquals(before, result.state)
        assertFalse(c.state.value.enabled)
        assertEquals(false, optIn.value)
    }

    @Test
    fun `B-ANDR-016 a refused Resume preserves the pause without native error text`() = runTest {
        for (code in listOf("notification_denied", "foreground_service_failed", "permission_in_progress")) {
            val optIn = FakeOptIn(true)
            val port =
                FakeLivePort().apply {
                    onPause = { ok(pausedFor()) }
                    onEnable = { refused(code) }
                }
            val c = liveMode(backgroundScope, port, optIn)
            c.restore()
            val result = c.resume()
            assertEquals(if (code == "permission_in_progress") ResumeError.Busy else ResumeError.ResumeFailed, result.error, code)
            assertTrue(result.state.paused)
            assertNull(c.state.value.lastError)
            assertEquals(false, optIn.value)
        }
    }

    @Test
    fun `B-ANDR-016 a second Resume cannot request another service while one is pending`() = runTest {
        val pending = CompletableDeferred<ServiceReply<ServiceStatus>>()
        var starts = 0
        val port =
            FakeLivePort().apply {
                onPause = { ok(pausedFor()) }
                onEnable = {
                    starts++
                    pending.await()
                }
            }
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        val first = async { c.resume() }
        runCurrent()
        assertEquals(ResumeError.Busy, c.resume().error)
        assertEquals(1, starts)
        pending.complete(ok(status(true)))
        assertNull(first.await().error)
    }

    @Test
    fun `B-ANDR-016 a late receipt read cannot overwrite a newer successful Resume`() = runTest {
        val pending = CompletableDeferred<ServiceReply<PauseState>>()
        var reads = 0
        val port = FakeLivePort().apply { onPause = { if (++reads == 1) ok(pausedFor()) else pending.await() } }
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        val oldRead = async { c.refreshPause() }
        runCurrent()
        assertTrue(c.resume().state.active)
        pending.complete(ok(pausedFor()))
        assertTrue(oldRead.await().active)
        assertTrue(c.state.value.active)
        assertTrue(c.state.value.enabled)
    }

    @Test
    fun `B-ANDR-016 a timeout during Resume wins over the older successful start reply`() = runTest {
        val pending = CompletableDeferred<ServiceReply<ServiceStatus>>()
        val port =
            FakeLivePort().apply {
                onPause = { ok(pausedFor(PauseReason.Interrupted)) }
                onEnable = { pending.await() }
            }
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        val resuming = async { c.resume() }
        runCurrent()
        c.onServiceStopped(LiveModeController.REASON_SYSTEM_TIMEOUT, pausedFor())
        pending.complete(ok(status(true)))
        val result = resuming.await()
        assertEquals(ResumeError.ResumeFailed, result.error)
        assertEquals(PauseReason.TimeLimit, result.state.reason)
        assertFalse(c.state.value.active)
        assertFalse(c.state.value.enabled)
        runCurrent()
        assertEquals(false, optIn.value)
    }

    @Test
    fun `B-ANDR-017 the notification Pause keeps its intentional semantics`() = runTest {
        val port = FakeLivePort().apply { onPause = { ok(pausedFor()) } }
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        c.onServiceStopped(LiveModeController.REASON_USER_PAUSE)
        assertFalse(c.state.value.pause.paused)
        assertEquals(PauseReason.None, c.state.value.pause.reason)
        assertFalse(c.state.value.stoppedByAndroidTimeout)
    }

    @Test
    fun `B-ANDR-017 an older enable reply cannot roll back an explicit notification Pause`() = runTest {
        val pending = CompletableDeferred<ServiceReply<ServiceStatus>>()
        val port = FakeLivePort().apply { all { pending.await() } }
        val c = liveMode(backgroundScope, port, optIn)
        // Cold saved opt-in with no confirmed service yet: the combination in which restore requests a start.
        assertTrue(c.state.value.enabled)
        val enabling = async { c.setEnabled(true) }
        runCurrent()
        c.onServiceStopped(LiveModeController.REASON_USER_PAUSE)
        pending.complete(ok(status(true)))
        assertFalse(enabling.await())
        with(c.state.value) {
            assertFalse(enabled)
            assertFalse(active)
            assertFalse(pause.paused)
            assertFalse(stoppedByAndroidTimeout)
            assertNull(lastError)
        }
        runCurrent()
        assertEquals(false, optIn.value)
    }

    @Test
    fun `B-ANDR-017 a late Resume error respects a platform that became unsupported`() = runTest {
        val pending = CompletableDeferred<ServiceReply<ServiceStatus>>()
        val support = MutableSupport()
        val port =
            FakeLivePort().apply {
                onPause = { ok(pausedFor()) }
                onEnable = { pending.await() }
            }
        val c = liveMode(backgroundScope, port, optIn, support)
        c.restore()
        val resuming = async { c.resume() }
        runCurrent()
        support.supported = false
        pending.complete(refused("failed"))
        val result = resuming.await()
        assertEquals(ResumeError.Unavailable, result.error)
        assertFalse(result.state.supported)
        with(c.state.value) {
            assertFalse(pause.supported)
            assertFalse(active)
            assertFalse(enabled)
            assertNull(lastError)
        }
    }

    @Test
    fun `B-ANDR-017 unsupported platforms do not start, read or change the Android opt-in`() = runTest {
        val port = FakeLivePort().apply { all { ok(status(true)) } }
        val c = liveMode(backgroundScope, port, optIn, MutableSupport(false))
        c.restore()
        assertEquals(ResumeError.Unavailable, c.resume().error)
        assertFalse(c.state.value.pause.supported)
        assertEquals(emptyList(), port.calls)
        assertEquals(true, optIn.value)
    }

    @Test
    fun `B-ANDR-017 native pause parsing rejects malformed and secret-bearing categories`() {
        val paused =
            mapOf<String, Any?>(
                "supported" to true, "active" to false, "paused" to true, "reason" to "timeLimit", "at" to AT,
                "canResume" to true,
            )
        assertTrue(PauseState.fromWire(paused).paused)
        for (malformed in listOf(
            paused + ("reason" to "private-password"),
            paused + ("at" to 0L),
            paused + ("at" to Double.POSITIVE_INFINITY),
            paused + ("active" to "true"),
            paused + ("canResume" to 1),
        )) {
            assertFalse(PauseState.fromWire(malformed).supported, malformed.toString())
        }
        val active =
            mapOf<String, Any?>(
                "supported" to true, "active" to true, "paused" to false, "reason" to "none", "at" to null,
                "canResume" to false,
            )
        assertFalse(PauseState.fromWire(active).paused)
        assertFalse(PauseState.fromWire(null).supported)
        assertFalse(PauseState.fromWire(mapOf("supported" to "true")).supported)
    }
}
