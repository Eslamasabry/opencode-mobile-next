package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitReason
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitRecord
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class ReportProblemStartupTest {
    private val clock = TestClock()
    private val redactor = Redactor()
    private val files = FakeReportFiles()
    private var opens = 0

    // One "process": its own in-memory ring and trace; the folder ([files]) outlives it.
    private var diagnostics = Diagnostics(redactor, clock)
    private var trace = PerfTrace(redactor, clock)
    private var startup = newStartup()

    private fun newStartup(maxEntries: Int = 128, maxBytes: Int = 262_144) =
        ReportProblemStartup({ opens++; files }, diagnostics, trace, redactor, clock, maxEntries, maxBytes)

    private fun restart() {
        startup.current?.close()
        diagnostics = Diagnostics(redactor, clock)
        trace = PerfTrace(redactor, clock)
        startup = newStartup()
    }

    private fun reopened() = assertNotNull(ReportProblem.open(files, redactor, clock).getOrNull())

    private val crash = ExitRecord(reason = ExitReason.CRASH, timestampMs = clock.now().minus(5.minutes).toEpochMilliseconds())

    private fun expectNoSecret(text: String) {
        for (secret in listOf(PROVIDER_KEY, BEARER, PASSWORD)) assertFalse(secret in text, "leaked into: $text")
    }

    @Test
    fun `B-STATE-075 start opens once, imports what came before and keeps every kind redacted across a crash and reopen`() {
        diagnostics.record("provider", "provider rejected $PROVIDER_KEY (Bearer $BEARER)", "#0 send (password=$PASSWORD)")
        trace.mark("provider.load", mapOf("apiKey" to PROVIDER_KEY))

        val started = assertNotNull(startup.start())
        assertSame(started, startup.start(), "one store per process")
        assertEquals(1, opens)
        assertSame(started, startup.current)

        started.recordAndroidExit(crash)
        started.recordThermal("severe", 0.9)
        diagnostics.record("test", "later failure token=$PROVIDER_KEY")

        // A crash here runs no close or flush: read what is already on disk.
        val raw = assertNotNull(files.text(ReportFile.Snapshot))
        expectNoSecret(raw)
        assertContains(raw, "androidExit")
        assertContains(raw, "thermal")

        val reopened = reopened()
        assertEquals(ProblemEventKind.entries.toSet(), reopened.entries.map { it.kind }.toSet())
        assertEquals(1, reopened.entries.count { "later failure" in it.message })
        expectNoSecret(reopened.reportText())
        expectNoSecret(reopened.reportJson().toString())
    }

    @Test
    fun `B-STATE-075 the started report stays within its entry and byte bounds`() {
        startup = newStartup(maxEntries = 5, maxBytes = 4_096)
        val report = assertNotNull(startup.start()).report
        for (i in 0 until 40) {
            clock.advance(1.minutes) // past the ring's repeat window, so each is its own entry
            diagnostics.record("loop", "failure $i ${"x".repeat(400)}")
        }
        assertTrue(report.entries.size <= 5)
        assertTrue(report.entries.last().message.startsWith("failure 39"))
        assertTrue(files.files.getValue(ReportFile.Snapshot).size <= 4_096)
    }

    @Test
    fun `B-STATE-072 the diagnostics Clear action erases the saved report, also after a restart`() {
        var started = assertNotNull(startup.start())
        diagnostics.record("test", "before restart")
        started.recordAndroidExit(crash)
        assertNotNull(files.text(ReportFile.Snapshot))

        restart()
        started = assertNotNull(startup.start())
        assertTrue(started.report.entries.isNotEmpty())

        diagnostics.record("test", "after restart")
        diagnostics.clear()
        assertTrue(started.report.entries.isEmpty())
        assertNull(files.text(ReportFile.Snapshot))
        assertNull(files.text(ReportFile.Pending))
        assertTrue(reopened().entries.isEmpty())
    }

    @Test
    fun `B-STATE-077 exit recovery keeps the typed exit once, without an error copy, and not again after a restart`() {
        fun launch() {
            val session = assertNotNull(startup.start())
            // What the launch intake does: the ring gets its line about the exit, the report gets the typed record.
            diagnostics.record(ReportProblem.ANDROID_EXIT_SOURCE, "Android exit: crash")
            session.recordAndroidExit(crash)
        }

        launch()
        var report = assertNotNull(startup.current).report
        val exits = report.entries.filter { it.kind == ProblemEventKind.AndroidExit }
        assertEquals(1, exits.size)
        assertContains(exits.single().message, "reason=4")
        assertEquals(ExitReason.CRASH, crash.reason)
        expectNoSecret(exits.single().message)
        assertTrue(ReportProblem.ANDROID_EXIT_SOURCE in diagnostics.errors.value.map { it.source })
        assertEquals(1, report.entries.count { it.source == ReportProblem.ANDROID_EXIT_SOURCE }, "the diagnostics copy is not stored twice")

        restart()
        launch()
        report = assertNotNull(startup.current).report
        assertEquals(1, report.entries.count { it.kind == ProblemEventKind.AndroidExit })
    }

    @Test
    fun `B-STATE-075 thermal changes only are kept and unknown readings add nothing`() {
        val started = assertNotNull(startup.start())
        started.recordThermal("unknown")
        started.recordThermal("none")
        started.recordThermal("none")
        started.recordThermal("severe")
        started.recordThermal("severe")
        val thermal = started.report.entries.filter { it.kind == ProblemEventKind.Thermal }.map { it.message }
        assertEquals(2, thermal.size)
        assertTrue(thermal.first().startsWith("status=none"))
        assertTrue(thermal.last().startsWith("status=severe"))
    }

    @Test
    fun `B-STATE-075 a store that cannot open leaves one fixed diagnostics line and no report`() {
        files.refuseOpen = true
        assertNull(startup.start())
        assertNull(startup.current)
        assertNull(startup.start())
        assertEquals(1, opens)
        val entry = diagnostics.errors.value.single()
        assertEquals(ReportProblemStartup.OPEN_FAILED_MESSAGE, entry.message)
        assertEquals(ReportProblemStartup.SOURCE, entry.source)
        assertFalse("/data/" in diagnostics.reportText())
    }

    @Test
    fun `B-STATE-073 closing the session detaches capture, is repeatable and keeps history`() {
        val started = assertNotNull(startup.start())
        diagnostics.record("test", "kept")
        started.close()
        started.close()
        diagnostics.record("test", "after close")
        started.recordThermal("severe")
        started.recordAndroidExit(crash)
        assertEquals(listOf("kept"), reopened().entries.map { it.message })
    }

    private companion object {
        const val PROVIDER_KEY = "sk-proj-fakeStartupProviderKey123456789"
        const val BEARER = "fakeStartupBearerValue"
        const val PASSWORD = "fakeStartupPassword"
    }
}
