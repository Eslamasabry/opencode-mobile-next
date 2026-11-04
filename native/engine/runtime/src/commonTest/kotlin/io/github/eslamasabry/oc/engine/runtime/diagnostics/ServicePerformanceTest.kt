package io.github.eslamasabry.oc.engine.runtime.diagnostics

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.setup.FakeLinuxChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Performance diagnostics on the Linux channel (test/builtin_performance_diagnostics_test.dart). */
class ServicePerformanceTest {
    private fun Outcome<RuntimePerformance>.value(): RuntimePerformance = (this as Outcome.Ok).value

    @Test
    fun `B-LINUX-026 performance reads each service lifetime through the channel`() = runTest {
        val channel = FakeLinuxChannel { _, _ ->
            mapOf(
                "serverRunning" to true,
                "prootMode" to "seccomp",
                "prootFilters" to 1,
                "serverFilters" to 2,
                "workHeld" to true,
                "services" to mapOf(
                    "server" to mapOf(
                        "running" to true, "lastExitCode" to 137, "lastUptimeMs" to 4000, "uptimeMs" to 250,
                        "restartCount" to 3, "exitReason" to "memory_or_phantom_kill",
                    ),
                    "helper" to mapOf(
                        "running" to false, "lastExitCode" to 0, "lastUptimeMs" to 120000L, "uptimeMs" to null,
                        "restartCount" to 0, "exitReason" to "exited",
                    ),
                ),
            ).ok()
        }
        val performance = ServicePerformance(channel, supported = true).read().value()
        assertEquals(listOf(FakeLinuxChannel.Call("performance", null)), channel.calls)
        assertTrue(performance.serverRunning)
        assertEquals(ProotMode.Seccomp, performance.prootMode)
        assertEquals(1L, performance.prootFilters)
        assertEquals(2L, performance.serverFilters)
        assertTrue(performance.workHeld)
        assertEquals(listOf("server", "helper"), performance.services.keys.toList())
        assertEquals(
            ServiceLifetime(true, lastExitCode = 137, lastUptimeMs = 4000, uptimeMs = 250, restartCount = 3, exitReason = ServiceExitReason.MemoryOrPhantomKill),
            performance.services["server"],
        )
        assertEquals(
            ServiceLifetime(false, lastExitCode = 0, lastUptimeMs = 120000, uptimeMs = null, restartCount = 0, exitReason = ServiceExitReason.Exited),
            performance.services["helper"],
        )
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> { (performance.services as MutableMap<String, ServiceLifetime>).clear() }
    }

    @Test
    fun `B-LINUX-026 older apps and absent channel results have no service records`() = runTest {
        assertTrue(ServicePerformance(FakeLinuxChannel(), supported = true).read().value().services.isEmpty())
        assertTrue(RuntimePerformance.fromMap(emptyMap<String, Any?>()).services.isEmpty())
        assertTrue(RuntimePerformance.fromMap(mapOf("serverRunning" to true)).services.isEmpty())
    }

    @Test
    fun `B-LINUX-026 malformed service containers and entries are ignored`() {
        for (value in listOf<Any?>(null, "bad", false, 7, emptyList<Any?>())) {
            assertTrue(RuntimePerformance.fromMap(mapOf("services" to value)).services.isEmpty(), "$value")
        }
        val performance = RuntimePerformance.fromMap(
            mapOf(
                "services" to mapOf(
                    null to emptyMap<String, Any?>(),
                    7 to emptyMap<String, Any?>(),
                    "" to emptyMap<String, Any?>(),
                    "not-a-map" to "bad",
                    "not-a-map-either" to emptyList<Any?>(),
                    "server" to emptyMap<Any?, Any?>(),
                ),
            ),
        )
        assertEquals(listOf("server"), performance.services.keys.toList())
        assertEquals(ServiceLifetime(), performance.services["server"])
        val server = performance.services.getValue("server")
        assertFalse(server.running)
        assertNull(server.lastExitCode)
        assertNull(server.lastUptimeMs)
        assertNull(server.uptimeMs)
        assertEquals(0L, server.restartCount)
        assertEquals(ServiceExitReason.Unknown, server.exitReason)
    }

    @Test
    fun `B-LINUX-026 malformed numbers never fabricate an exit or uptime`() {
        for (invalid in listOf<Any?>(null, "137", true, 1.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            val record = ServiceLifetime.fromMap(
                mapOf(
                    "running" to "true", "lastExitCode" to invalid, "lastUptimeMs" to invalid, "uptimeMs" to invalid,
                    "restartCount" to invalid, "exitReason" to "new-native-reason",
                ),
            )
            assertEquals(ServiceLifetime(), record, "$invalid")
        }
        val negative = ServiceLifetime.fromMap(mapOf("lastExitCode" to -1, "lastUptimeMs" to -1, "uptimeMs" to -1, "restartCount" to -1))
        assertEquals(-1L, negative.lastExitCode)
        assertNull(negative.lastUptimeMs)
        assertNull(negative.uptimeMs)
        assertEquals(0L, negative.restartCount)
        val filters = RuntimePerformance.fromMap(mapOf("prootFilters" to Double.NaN, "serverFilters" to Double.POSITIVE_INFINITY))
        assertNull(filters.prootFilters)
        assertNull(filters.serverFilters)
    }

    @Test
    fun `B-LINUX-026 an unknown reason stays unknown even with exit 137`() {
        val record = ServiceLifetime.fromMap(mapOf("lastExitCode" to 137))
        assertEquals(137L, record.lastExitCode)
        assertEquals(ServiceExitReason.Unknown, record.exitReason)
        for (invalid in listOf<Any?>(null, 137, false, "oom", "sigkill")) {
            assertEquals(ServiceExitReason.Unknown, ServiceExitReason.parse(invalid), "$invalid")
        }
    }

    @Test
    fun `B-LINUX-026 zero durations are observations rather than missing values`() {
        val record = ServiceLifetime.fromMap(mapOf("lastExitCode" to 0, "lastUptimeMs" to 0, "uptimeMs" to 0, "restartCount" to 0))
        assertEquals(0L, record.lastExitCode)
        assertEquals(0L, record.lastUptimeMs)
        assertEquals(0L, record.uptimeMs)
        assertEquals(0L, record.restartCount)
    }

    @Test
    fun `B-LINUX-026 unsupported platforms return an empty snapshot without a call`() = runTest {
        val channel = FakeLinuxChannel { _, _ -> emptyMap<String, Any?>().ok() }
        val performance = ServicePerformance(channel, supported = false).read().value()
        assertTrue(channel.calls.isEmpty())
        assertFalse(performance.serverRunning)
        assertTrue(performance.services.isEmpty())
    }
}
