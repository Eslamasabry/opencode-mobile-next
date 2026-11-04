package io.github.eslamasabry.oc.android.background

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

// Ports test/background_live_test.dart and test/app_lifecycle_test.dart:214 (B-ANDR-009, -012, -013, -014).
@OptIn(ExperimentalCoroutinesApi::class)
class LiveModeTest {
    private val enableOnly: suspend (String) -> ServiceReply<ServiceStatus> = { method ->
        ok(ServiceStatus(enabled = method == "enable", active = method == "enable", notificationGranted = true))
    }

    /** Distinct states published after this call (the Flutter `addListener` count). */
    private fun TestScope.changes(controller: LiveModeController): () -> Int {
        val seen = mutableListOf<LiveModeState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.state.collect { seen += it } }
        return { seen.size - 1 }
    }

    @Test
    fun `B-ANDR-012 background live mode persists only after Android enables it`() = runTest {
        val port = FakeLivePort().apply { all(enableOnly) }
        val optIn = FakeOptIn()
        val c = liveMode(backgroundScope, port, optIn)
        assertTrue(c.setEnabled(true))
        assertTrue(c.state.value.active)
        assertEquals(true, optIn.value)
        assertFalse(c.setEnabled(false))
        assertFalse(c.state.value.active)
        assertEquals(false, optIn.value)
        assertEquals(listOf("enable", "disable"), port.calls)
    }

    @Test
    fun `B-ANDR-012 denied notification permission leaves background mode disabled with a reason`() = runTest {
        val port = FakeLivePort().apply { all { refused(ServiceRefusal.NOTIFICATION_DENIED) } }
        val optIn = FakeOptIn()
        val c = liveMode(backgroundScope, port, optIn)
        assertFalse(c.setEnabled(true))
        assertFalse(c.state.value.enabled)
        assertEquals(LiveModeError.NotificationsRequired, c.state.value.lastError)
        assertEquals(false, optIn.value)
    }

    @Test
    fun `B-ANDR-012 restores an enabled preference through the native service`() = runTest {
        val port =
            FakeLivePort().apply {
                all { ok(ServiceStatus(enabled = true, active = true, notificationGranted = true, batteryOptimizationIgnored = true)) }
            }
        val c = liveMode(backgroundScope, port, FakeOptIn(true))
        c.restore()
        assertTrue("enable" in port.calls)
        assertTrue(c.state.value.enabled)
        assertTrue(c.state.value.active)
        assertTrue(c.state.value.batteryOptimizationIgnored)
    }

    @Test
    fun `B-ANDR-012 an unsupported platform ignores a saved opt-in without changing it`() = runTest {
        for (saved in listOf(null, false, true)) {
            val port = FakeLivePort().apply { all { ok(ServiceStatus(enabled = true, active = true)) } }
            val optIn = FakeOptIn(saved)
            val support = MutableSupport(false)
            val c = liveMode(backgroundScope, port, optIn, support)
            assertFalse(c.state.value.enabled, "safe even before restore")
            assertFalse(c.state.value.active)
            c.restore()
            assertFalse(c.state.value.enabled)
            assertFalse(c.setEnabled(true))
            c.refreshStatus()
            assertFalse(c.requestBatteryOptimizationExemption())
            assertFalse(c.setEnabled(false))
            with(c.state.value) {
                assertFalse(enabled)
                assertFalse(active)
                assertFalse(notificationGranted)
                assertFalse(batteryOptimizationIgnored)
                assertFalse(busy)
                assertNull(lastError)
                assertFalse(stoppedByAndroidTimeout)
            }
            assertEquals(emptyList(), port.calls)
            assertEquals(saved, optIn.value)
            val recreated = liveMode(backgroundScope, port, optIn, support)
            assertFalse(recreated.state.value.enabled)
            assertFalse(recreated.state.value.active)
        }
    }

    @Test
    fun `B-ANDR-012 losing platform support clears a previously active Android state but keeps the opt-in`() = runTest {
        for (operation in listOf("restore", "enable")) {
            val port = FakeLivePort().apply { all { ok(ServiceStatus(enabled = true, active = true, notificationGranted = true, batteryOptimizationIgnored = true)) } }
            val optIn = FakeOptIn(true)
            val support = MutableSupport()
            val c = liveMode(backgroundScope, port, optIn, support)
            c.restore()
            assertTrue(c.state.value.active)
            val changes = changes(c)
            support.supported = false
            if (operation == "restore") c.restore() else assertFalse(c.setEnabled(true))
            with(c.state.value) {
                assertFalse(enabled)
                assertFalse(active)
                assertFalse(notificationGranted)
                assertFalse(batteryOptimizationIgnored)
            }
            assertEquals(1, changes())
            assertEquals(listOf("getBackgroundPause", "enable"), port.calls)
            assertEquals(true, optIn.value)

            // A transient unsupported gate must not erase the real Android opt-in.
            support.supported = true
            val recreated = liveMode(backgroundScope, port, optIn, support)
            recreated.restore()
            assertTrue(recreated.state.value.enabled)
            assertTrue(recreated.state.value.active)
            assertEquals(listOf("getBackgroundPause", "enable", "getBackgroundPause", "enable"), port.calls)
        }
    }

    @Test
    fun `B-ANDR-012 a late native enable status or failure cannot undo an unsupported gate`() = runTest {
        for (failure in listOf(false, true)) {
            val pending = CompletableDeferred<ServiceReply<ServiceStatus>>()
            val port = FakeLivePort().apply { onEnable = { pending.await() } }
            val optIn = FakeOptIn(true)
            val support = MutableSupport()
            val c = liveMode(backgroundScope, port, optIn, support)
            val enabling = async { c.setEnabled(true) }
            runCurrent()
            assertTrue(c.state.value.busy)
            support.supported = false
            // Even a retry while Android's old request is busy fails closed.
            assertFalse(c.setEnabled(true))
            pending.complete(
                if (failure) refused("unavailable")
                else ok(ServiceStatus(enabled = true, active = true, notificationGranted = true, batteryOptimizationIgnored = true)),
            )
            assertFalse(enabling.await())
            with(c.state.value) {
                assertFalse(enabled)
                assertFalse(active)
                assertFalse(notificationGranted)
                assertFalse(batteryOptimizationIgnored)
                assertFalse(busy)
                assertNull(lastError)
            }
            assertEquals(listOf("enable"), port.calls)
            assertEquals(true, optIn.value)
        }
    }

    @Test
    fun `B-ANDR-012 an unavailable Android host still rejects enable and reports it`() = runTest {
        val port = FakeLivePort().apply { all { refused(ServiceRefusal.NO_HOST) } }
        val optIn = FakeOptIn()
        val c = liveMode(backgroundScope, port, optIn)
        assertFalse(c.setEnabled(true))
        with(c.state.value) {
            assertFalse(enabled)
            assertFalse(active)
            assertFalse(busy)
            assertEquals(LiveModeError.AndroidAppOnly, lastError)
        }
        assertEquals(false, optIn.value)
    }

    @Test
    fun `B-ANDR-012 malformed Android status fields do not grant background capabilities`() = runTest {
        val malformed =
            ServiceStatus.fromWire(
                mapOf("enabled" to "true", "active" to 1, "notificationGranted" to listOf<Any>(), "batteryOptimizationIgnored" to null),
            )
        val port = FakeLivePort().apply { all { ok(malformed) } }
        val optIn = FakeOptIn()
        val c = liveMode(backgroundScope, port, optIn)
        assertFalse(c.setEnabled(true))
        with(c.state.value) {
            assertFalse(enabled)
            assertFalse(active)
            assertFalse(notificationGranted)
            assertFalse(batteryOptimizationIgnored)
        }
        assertEquals(false, optIn.value)
    }

    @Test
    fun `B-ANDR-013 coding alerts are allowed only while live mode is on and Android lets the app notify`() = runTest {
        val port = FakeLivePort().apply { all { ok(status(true)) } }
        val c = liveMode(backgroundScope, port, FakeOptIn(true))
        c.restore()
        assertTrue(c.state.value.codingAlertsAllowed)
        val off = liveMode(backgroundScope, FakeLivePort(), FakeOptIn())
        assertFalse(off.state.value.codingAlertsAllowed, "disabled live mode never posts a coding alert")
        val denied = c.state.value.copy(notificationGranted = false)
        assertFalse(denied.codingAlertsAllowed)
    }

    @Test
    fun `B-ANDR-013 opening notification settings calls the platform once and swallows a failure`() {
        var opens = 0
        NotificationSettingsShortcut.open(supported = true) { opens++ }
        assertEquals(1, opens)
        NotificationSettingsShortcut.open(supported = true) {
            opens++
            throw IllegalStateException("no activity")
        }
        assertEquals(2, opens)
        NotificationSettingsShortcut.open(supported = false) { opens++ }
        assertEquals(2, opens, "a no-op where the platform has no notification settings")
    }

    @Test
    fun `B-ANDR-014 an Android foreground-service timeout turns live mode off and tells the person`() = runTest {
        val port = FakeLivePort().apply { all { ok(ServiceStatus(enabled = true, active = true, notificationGranted = true)) } }
        val optIn = FakeOptIn(true)
        val c = liveMode(backgroundScope, port, optIn)
        c.restore()
        assertTrue(c.state.value.enabled)
        assertTrue(c.state.value.active)
        val changes = changes(c)
        c.onServiceStopped(LiveModeController.REASON_SYSTEM_TIMEOUT)
        with(c.state.value) {
            assertFalse(enabled)
            assertFalse(active)
            assertTrue(stoppedByAndroidTimeout)
            // Nothing failed, so this is not an error the person can retry away.
            assertNull(lastError)
        }
        assertEquals(1, changes(), "the UI has to hear about it at once")
        runCurrent()
        assertEquals(false, optIn.value, "the persisted preference must not outlive the service")
    }

    @Test
    fun `B-ANDR-014 turning live mode back on clears the timeout notice`() = runTest {
        val port = FakeLivePort().apply { all(enableOnly) }
        val c = liveMode(backgroundScope, port)
        c.onServiceStopped(LiveModeController.REASON_SYSTEM_TIMEOUT)
        assertTrue(c.state.value.stoppedByAndroidTimeout)
        assertTrue(c.setEnabled(true))
        assertFalse(c.state.value.stoppedByAndroidTimeout)
    }

    @Test
    fun `B-ANDR-014 the timeout method name and reasons match the native contract`() {
        assertEquals("backgroundServiceTimeout", LiveModeController.TIMEOUT_METHOD)
        val kotlin =
            File("../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BackgroundConnectionService.kt")
                .readText()
        assertTrue("const val METHOD_TIMEOUT = \"${LiveModeController.TIMEOUT_METHOD}\"" in kotlin)
        assertTrue("const val REASON_SYSTEM_TIMEOUT = \"${LiveModeController.REASON_SYSTEM_TIMEOUT}\"" in kotlin)
        assertTrue("const val REASON_USER_PAUSE = \"${LiveModeController.REASON_USER_PAUSE}\"" in kotlin)
        assertTrue("notifyDartOfTimeout()" in kotlin)
    }

    @Test
    fun `B-ANDR-009 opted-in background mode keeps the shared transport active while backgrounded`() = runTest {
        val port = FakeLivePort().apply { all { ok(ServiceStatus(enabled = true, active = true, notificationGranted = true)) } }
        val c = liveMode(backgroundScope, port, FakeOptIn(true))
        c.restore()
        assertEquals(
            BackgroundLifecycle.Decision.StayConnected(backgroundChecksAllowed = true),
            BackgroundLifecycle.onAppBackgrounded(c.state.value),
        )
        val notOptedIn = liveMode(backgroundScope, FakeLivePort(), FakeOptIn())
        assertEquals(BackgroundLifecycle.Decision.Suspend, BackgroundLifecycle.onAppBackgrounded(notOptedIn.state.value))
    }
}
