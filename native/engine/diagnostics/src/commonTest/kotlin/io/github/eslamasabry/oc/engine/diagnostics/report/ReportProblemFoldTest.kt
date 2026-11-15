package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfSpan
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** B-332: the saved problem report folds a fault repeated close together into one counted event. */
class ReportProblemFoldTest {
    private val at = Instant.parse("2026-11-14T09:00:00Z")
    private val clock = TestClock(at)
    private val redactor = Redactor()
    private val files = FakeReportFiles()

    private fun open(maxEntries: Int = 128): ReportProblem =
        assertNotNull(ReportProblem.open(files, redactor, clock, maxEntries).getOrNull())

    @Test
    fun `B-332 a saved fault repeated between others is one event with its count and first and last time`() {
        val report = open()
        for (i in 0 until 18) {
            report.recordError("Network · Reset", source = "chat.stream", at = at + (i * 400).milliseconds)
            if (i == 5) report.recordError("connection lost after 3 tries", source = "connection", at = at + 2.seconds)
        }
        val fault = report.entries.single { it.source == "chat.stream" }
        assertEquals(2, report.entries.size)
        assertEquals(18, fault.count)
        assertEquals(at, fault.firstAt)
        assertEquals(at + (17 * 400).milliseconds, fault.at)

        val restarted = open()
        assertEquals(report.entries, restarted.entries)
        val saved = assertNotNull(files.text(ReportFile.Snapshot))
        assertContains(saved, "\"count\":18")
        assertContains(saved, "\"firstTime\":\"$at\"")
        assertContains(report.reportText(), "\"count\": 18")
    }

    @Test
    fun `B-332 different faults never fold, and timings stay one line each`() {
        val report = open()
        report.recordError("failed", source = "chat", at = at)
        report.recordError("failed", source = "files", at = at)
        report.recordError("failed", "at a (A.kt:1)", source = "chat", at = at)
        report.recordError("failed again", source = "chat", at = at)
        report.recordThermal("severe", 0.9, at = at)
        report.recordThermal("severe", 0.9, at = at + 1.seconds) // same kind and text: one thermal event, counted
        val span = PerfSpan(id = 1, name = "request", startMicros = 1, durationMicros = 1_000, wallStart = at)
        report.recordTiming(span)
        report.recordTiming(span.copy(id = 2))
        assertEquals(7, report.entries.size)
        assertEquals(listOf(1, 1, 1, 1), report.entries.filter { it.kind == ProblemEventKind.Error }.map { it.count })
        assertEquals(2, report.entries.single { it.kind == ProblemEventKind.Thermal }.count)
        assertEquals(2, report.entries.count { it.kind == ProblemEventKind.Timing })
    }

    @Test
    fun `B-332 a repeat after the window is a new event and the newest are kept at the limit`() {
        val report = open(maxEntries = 3)
        report.recordError("A", at = at)
        report.recordError("B", at = at + 1.seconds)
        report.recordError("C", at = at + 2.seconds)
        report.recordError("A", at = at + 3.seconds) // folds and becomes newer than B and C
        report.recordError("D", at = at + 4.seconds) // the limit cuts B, the oldest
        assertEquals(listOf("C", "A", "D"), report.entries.map { it.message })
        assertEquals(2, report.entries[1].count)
        assertEquals(at, report.entries[1].firstAt)

        report.recordError("D", at = at + 30.seconds)
        assertEquals(listOf("A", "D", "D"), report.entries.map { it.message })
        assertEquals(listOf(1, 1), report.entries.drop(1).map { it.count })
    }

    @Test
    fun `B-332 a report saved before folding still loads, its close repeats folded`() {
        files.put(
            ReportFile.Snapshot,
            """{"version":1,"entries":[""" +
                """{"kind":"error","time":"2026-11-14T08:00:00Z","source":"chat","message":"failed"},""" +
                """{"kind":"error","time":"2026-11-14T08:00:02Z","source":"chat","message":"failed"},""" +
                """{"kind":"timing","time":"2026-11-14T08:00:03Z","source":"OCTRACE","message":"OCTRACE request 1ms"},""" +
                """{"kind":"error","time":"2026-11-14T08:00:05Z","source":"chat","message":"failed"},""" +
                """{"kind":"error","time":"2026-11-14T08:00:06Z","source":"files","message":"other","stack":"at f"}""" +
                """]}""",
        )
        val report = open()
        assertEquals(3, report.entries.size)
        val failed = report.entries.single { it.message == "failed" }
        assertEquals(3, failed.count)
        assertEquals(Instant.parse("2026-11-14T08:00:00Z"), failed.firstAt)
        assertEquals(Instant.parse("2026-11-14T08:00:05Z"), failed.at)
        val other = report.entries.single { it.message == "other" }
        assertEquals(1, other.count)
        assertEquals("at f", other.stack)
        assertFalse("\"count\"" in other.toJson().toString())
    }

    @Test
    fun `B-332 the ring's repeats reach the saved report as one counted event`() {
        val diagnostics = Diagnostics(redactor, clock)
        val report = open()
        val capture = ReportProblemCapture(report, diagnostics, PerfTrace(redactor, clock))
        try {
            diagnostics.record("chat", "failed", at = at)
            diagnostics.record("files", "other", at = at + 1.seconds)
            diagnostics.record("chat", "failed", at = at + 2.seconds)
            diagnostics.record("chat", "failed", at = at + 3.seconds)
        } finally {
            capture.close()
        }
        val failed = report.entries.single { it.message == "failed" }
        assertEquals(2, report.entries.size)
        assertEquals(3, failed.count)
        assertEquals(diagnostics.errors.value.single { it.message == "failed" }.occurrences, failed.count)
        assertEquals(at, failed.firstAt)
        assertEquals(at + 3.seconds, failed.at)
    }
}
