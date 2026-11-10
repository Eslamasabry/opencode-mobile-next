package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * B-190 app start, jank, memory pressure, the device context, the custom keys and the last process end (Flutter
 * `health_telemetry_test.dart` group "app start, jank and memory pressure"): each waits for its own consent.
 */
class HealthTelemetryTest {
    private class FakePlatform(var cold: Long? = 1_800, val exitList: List<HealthExit> = emptyList()) : HealthPlatform {
        var contextReads = 0
        var exitReads = 0

        override suspend fun deviceContext(): HealthDeviceContext {
            contextReads++
            return HealthDeviceContext(
                ramBytes = 5L * 1024 * 1024 * 1024,
                freeBytes = 12L * 1024 * 1024 * 1024,
                abi = "arm64",
                webviewMajor = 133,
                notificationsAllowed = true,
            )
        }

        override suspend fun exits(limit: Int): List<HealthExit> {
            exitReads++
            return exitList
        }

        override fun coldStartMs(): Long? = cold

        override val appVersion: String = "3.0.1"
        override val declaredBuildKind: String? = null
    }

    private class Marks(var at: Long = 0) : HealthExitMarks {
        override fun lastExitAtMs(): Long = at

        override suspend fun setLastExitAtMs(atMs: Long) {
            at = atMs
        }
    }

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    private class StepClock(var ms: Long = 0) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
    }

    private fun TestScope.rig(
        usage: Boolean,
        crashes: Boolean,
        platform: FakePlatform = FakePlatform(),
        marks: Marks = Marks(),
        clock: Clock = Clock.System,
    ): Triple<RecordingVendor, TelemetryService, HealthTelemetry> {
        val vendor = RecordingVendor()
        val service = TelemetryService(vendor, available = true, scope = this)
        service.start(TelemetryConsent(usage, crashes), NoStore, MemoryTelemetryReportStore())
        val health = HealthTelemetry(service, platform, marks, backgroundScope, clock)
        health.start()
        runCurrent()
        return Triple(vendor, service, health)
    }

    @Test
    fun `B-190-040 Android exit reasons become the contract words, a normal end none`() {
        assertEquals(
            listOf("crash", "crash_native", "anr", "low_memory", "excessive_resource", "signaled", "user_requested", "dependency_died", "other", "other"),
            listOf(4, 5, 6, 3, 9, 2, 10, 12, 13, 7).map { exitReasonWord(it) },
        )
        assertNull(exitReasonWord(1), "the app quit itself")
        assertNull(exitReasonWord(16), "it was updated")
        assertEquals("2.0", majorMinorOf("2.0.28"))
        assertEquals("2.1", majorMinorOf("v2.1"))
        assertNull(majorMinorOf("local build"))
    }

    @Test
    fun `B-190-041 with usage on - the device context once, the cold start once with its trace`() = runTest {
        val platform = FakePlatform()
        val (vendor, service, health) = rig(usage = true, crashes = false, platform = platform)
        health.firstFrame(); runCurrent()
        assertEquals(
            listOf(
                mapOf(
                    "ram_range" to "4-6",
                    "free_storage_range" to "8-32",
                    "abi" to "arm64",
                    "webview_major" to "133",
                    "notif_allowed" to "yes",
                )
            ),
            vendor.properties,
        )
        assertEquals(listOf("app_start" to mapOf("kind" to "cold", "ms_range" to "1000-2000")), vendor.events)
        assertEquals(listOf(Triple("app_cold_start", emptyMap<String, String>(), 1_800L)), vendor.traces)
        assertEquals(1, platform.contextReads)
        health.warmStart(700)
        assertEquals("app_start" to mapOf("kind" to "warm", "ms_range" to "500-1000"), vendor.events.last())
        assertTrue(service.healthKeys.isEmpty(), "no crash keys without crash consent")
    }

    @Test
    fun `B-190-042 with usage off nothing is measured, read or sent`() = runTest {
        val platform = FakePlatform()
        val (vendor, _, health) = rig(usage = false, crashes = false, platform = platform)
        health.firstFrame()
        health.warmStart(700)
        health.memoryPressure("critical")
        health.jank.onFrames(total = 100, slow = 50)
        health.jank.endSession()
        runCurrent()
        assertTrue(vendor.silent)
        assertEquals(0, platform.contextReads)
        assertEquals(0, platform.exitReads, "with crashes off the exit record is not even read")
        assertTrue(!health.jank.running)
    }

    @Test
    fun `B-190-043 the crash keys are words, and the last process end is reported once as a kind`() = runTest {
        val marks = Marks(at = 10)
        val platform = FakePlatform(exitList = listOf(HealthExit(6, 5_000), HealthExit(4, 1_000)))
        val (vendor, _, health) = rig(usage = false, crashes = true, platform = platform, marks = marks)
        assertEquals(mapOf("app_version" to "3.0.1", "build_kind" to "sideload"), vendor.keys.first())
        assertEquals(listOf("process_exit" to mapOf("exit_reason" to "anr")), vendor.failures)
        assertEquals(5_000, marks.at)
        health.updateConnection("phone", "ready", connections = 2, userServerKind = "phone", serverVersion = "2.1.4")
        health.updateConnection("phone", "ready", connections = 2, userServerKind = "phone", serverVersion = "2.1.4")
        assertEquals(
            mapOf("server_kind" to "phone", "engine_state" to "ready", "connections" to "2-3", "oc_server_version" to "2.1"),
            vendor.keys.last(),
        )
        assertEquals(2, vendor.keys.size, "unchanged facts are not sent again")

        // A normal end is not reported, and an already reported one never twice.
        val again = rig(usage = false, crashes = true, platform = FakePlatform(exitList = listOf(HealthExit(1, 9_000))), marks = Marks(at = 0))
        assertTrue(again.first.failures.isEmpty())
        val seen = rig(usage = false, crashes = true, platform = FakePlatform(exitList = listOf(HealthExit(4, 9_000))), marks = Marks(at = 9_000))
        assertTrue(seen.first.failures.isEmpty())
    }

    @Test
    fun `B-190-044 jank is sent once per page kind, from at least 30 frames`() = runTest {
        val (vendor, _, health) = rig(usage = true, crashes = false, platform = FakePlatform(cold = null))
        val jank = health.jank
        jank.screen(ProductScreen.Chat)
        jank.onFrames(total = 100, slow = 10)
        jank.screen(ProductScreen.Settings)
        jank.onFrames(total = 10, slow = 10) // too few to say anything
        jank.screen(ProductScreen.Chat)
        jank.onFrames(total = 100, slow = 100) // already sent this session
        jank.endSession()
        assertEquals(
            listOf(
                "jank" to
                    mapOf("screen" to "chat", "janky_pct_range" to "5-15", "frozen_range" to "0", "refresh_hz" to "60"),
            ),
            vendor.events,
        )
    }

    @Test
    fun `B-190-045 memory pressure is a listed level, at most every five minutes`() = runTest {
        val clock = StepClock()
        val (vendor, _, health) = rig(usage = true, crashes = false, platform = FakePlatform(cold = null), clock = clock)
        health.memoryPressure("low")
        health.memoryPressure("low")
        health.memoryPressure("very_low")
        clock.ms += 5 * 60_000
        health.memoryPressure("low")
        assertEquals(2, vendor.events.count { it == ("memory_pressure" to mapOf("level" to "low")) })
        assertEquals(2, vendor.events.size)
    }
}
