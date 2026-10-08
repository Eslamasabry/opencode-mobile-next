package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDiagnosticsTest {
    @Test
    fun firstLaunchIsNotARestartAndLaterLaunchesAre() {
        val initial = ServiceDiagnostics()
        val first = initial.launched()
        val second = first.launched()
        val third = second.launched()

        assertFalse(initial.hasLaunched)
        assertTrue(first.hasLaunched)
        assertEquals(0, first.restartCount)
        assertEquals(1, second.restartCount)
        assertEquals(2, third.restartCount)
    }

    @Test
    fun exitRetainsHistoryAndClampsNegativeUptime() {
        val launched = ServiceDiagnostics().launched().launched()
        val exited = launched.exited(1, -42L)

        assertEquals(1, exited.lastExitCode)
        assertEquals(0L, exited.lastUptimeMs)
        assertEquals(1, exited.restartCount)
        assertTrue(exited.hasLaunched)
        assertNull(launched.lastExitCode)
    }

    @Test
    fun snapshotKeepsLastAndCurrentUptimeSeparate() {
        val restarted = ServiceDiagnostics()
            .launched()
            .exited(0, 8_000L)
            .launched()
        val snapshot = restarted.snapshot(running = true, uptimeMs = 500L)

        assertEquals(true, snapshot["running"])
        assertEquals(0, snapshot["lastExitCode"])
        assertEquals(8_000L, snapshot["lastUptimeMs"])
        assertEquals(500L, snapshot["uptimeMs"])
        assertEquals(1, snapshot["restartCount"])
        assertEquals("exited", snapshot["exitReason"])
    }

    @Test
    fun exit137ReportsPossibleMemoryOrPhantomKillWithoutChoosingACause() {
        val snapshot = ServiceDiagnostics()
            .launched()
            .exited(137, 2_000L)
            .snapshot(running = false, uptimeMs = null)

        assertEquals(137, snapshot["lastExitCode"])
        assertEquals("memory_or_phantom_kill", snapshot["exitReason"])
        assertEquals(false, snapshot["running"])
        assertNull(snapshot["uptimeMs"])
    }

    @Test
    fun unknownExitClearsPreviousExitCode() {
        val snapshot = ServiceDiagnostics()
            .launched()
            .exited(137, 2_000L)
            .launched()
            .exited(null, 3_000L)
            .snapshot(running = false, uptimeMs = null)

        assertNull(snapshot["lastExitCode"])
        assertEquals(3_000L, snapshot["lastUptimeMs"])
        assertEquals("unknown", snapshot["exitReason"])
    }

    @Test
    fun restoredStateCountsNextLaunchAndPreservesExit() {
        val saved = ServiceDiagnostics()
            .launched()
            .exited(137, 9_000L)
            .launched()
            .exited(2, 7_000L)
        val restored = ServiceDiagnostics(
            lastExitCode = saved.lastExitCode,
            lastUptimeMs = saved.lastUptimeMs,
            restartCount = saved.restartCount,
            hasLaunched = saved.hasLaunched,
        )
        val relaunched = restored.launched()

        assertEquals(2, relaunched.restartCount)
        assertEquals(2, relaunched.lastExitCode)
        assertEquals(7_000L, relaunched.lastUptimeMs)
        assertTrue(relaunched.hasLaunched)
    }

    @Test
    fun neverLaunchedServiceHasUnknownExitAndNoUptime() {
        assertEquals(
            mapOf(
                "running" to false,
                "lastExitCode" to null,
                "lastUptimeMs" to null,
                "uptimeMs" to null,
                "restartCount" to 0,
                "exitReason" to "unknown",
            ),
            ServiceDiagnostics().snapshot(running = false, uptimeMs = null),
        )
    }
}
