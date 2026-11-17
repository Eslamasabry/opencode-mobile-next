package io.github.eslamasabry.oc.engine.analytics.health

import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class AppExitTest {
    private class Platform(private val exit: HealthExit) : HealthPlatform {
        override suspend fun deviceContext(): HealthDeviceContext? = null
        override suspend fun exits(limit: Int) = listOf(exit)
        override fun coldStartMs(): Long? = null
        override val appVersion = "3.0.1"
        override val declaredBuildKind: String? = null
    }
    private class Marks : HealthExitMarks {
        var crashAt = 0L
        var analyticsAt = 0L
        override fun lastExitAtMs() = crashAt
        override suspend fun setLastExitAtMs(atMs: Long) { crashAt = atMs }
        override fun lastAnalyticsExitAtMs() = analyticsAt
        override suspend fun setLastAnalyticsExitAtMs(atMs: Long): Boolean { analyticsAt = atMs; return true }
    }
    private object Store : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent) = true
    }
    @Test fun `B-435 previous exit reasons have exactly the analytics words`() {
        assertEquals(listOf("crash", "crash", "anr", "low_memory", "excessive_resource", "signaled", "user_requested", "dependency_died", "other", "other", "other"),
            listOf(4, 5, 6, 3, 9, 2, 10, 12, 13, 1, 16).map(::appExitReasonWord))
    }
    @Test fun `B-435 one previous exit is counted once under usage consent independently of crash consent`() = runTest {
        val vendor = RecordingVendor()
        val service = TelemetryService(vendor, true, scope = this)
        service.start(TelemetryConsent(usage = false, crashes = true), Store, MemoryTelemetryReportStore())
        val marks = Marks()
        val platform = Platform(HealthExit(6, 5_000, true))
        val health = HealthTelemetry(service, platform, marks, backgroundScope, Clock.System)
        health.start(); runCurrent()
        assertTrue(vendor.events.none { it.first == "app_exit" })
        assertEquals(listOf("process_exit" to mapOf("exit_reason" to "anr")), vendor.failures)
        service.setConsent(true, true); runCurrent()
        assertEquals(listOf(mapOf("exit_reason" to "anr", "foreground" to "yes")), vendor.events.filter { it.first == "app_exit" }.map { it.second })
        health.start(); service.setConsent(false, true); service.setConsent(true, true); runCurrent()
        assertEquals(1, vendor.events.count { it.first == "app_exit" })
        val again = HealthTelemetry(service, platform, marks, backgroundScope, Clock.System)
        again.start(); runCurrent()
        assertEquals(1, vendor.events.count { it.first == "app_exit" }, "persisted marker prevents the same OS exit at another start")
    }
    @Test fun `B-435 user requested is analytics only while crash and anr remain nonfatals`() = runTest {
        for (reason in listOf(10, 4, 6)) {
            val vendor = RecordingVendor()
            val service = TelemetryService(vendor, true, scope = this)
            service.start(TelemetryConsent(true, true), Store, MemoryTelemetryReportStore())
            val health = HealthTelemetry(service, Platform(HealthExit(reason, 5_000, false)), Marks(), backgroundScope, Clock.System)
            health.start(); runCurrent()
            assertEquals(if (reason == 10) 0 else 1, vendor.failures.count { it.first == "process_exit" })
            assertEquals(listOf(mapOf("exit_reason" to appExitReasonWord(reason), "foreground" to "no")), vendor.events.filter { it.first == "app_exit" }.map { it.second })
        }
    }
    @Test fun `B-435 unknown importance is omitted and consent off sends no exit to either path`() = runTest {
        val vendor = RecordingVendor()
        val service = TelemetryService(vendor, true, scope = this)
        service.start(TelemetryConsent(false, false), Store, MemoryTelemetryReportStore())
        HealthTelemetry(service, Platform(HealthExit(3, 5_000)), Marks(), backgroundScope, Clock.System).start(); runCurrent()
        assertTrue(vendor.events.isEmpty() && vendor.failures.isEmpty())
        service.setConsent(true, false); runCurrent()
        assertEquals(listOf(mapOf("exit_reason" to "low_memory")), vendor.events.filter { it.first == "app_exit" }.map { it.second })
        assertTrue(vendor.failures.isEmpty())
    }
}
