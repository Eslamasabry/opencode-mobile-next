package io.github.eslamasabry.oc.feature.diagnostics

import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.engine.diagnostics.problem.ProblemEventKind
import io.github.eslamasabry.oc.engine.diagnostics.problem.ProblemReportEvent
import io.github.eslamasabry.oc.feature.diagnostics.presentation.ReportHeadline
import io.github.eslamasabry.oc.feature.diagnostics.presentation.ReportHeadlines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** E19-S04: Report a problem's first line is plain words from the record kind, never the raw exit record. */
class ReportHeadlineTest {
    private val at = Instant.parse("2026-11-07T10:00:00Z")

    /** Exactly what `ReportProblem.recordAndroidExit` stores. */
    private fun exit(kind: ExitKind, reason: Int = 10) =
        ProblemReportEvent(
            ProblemEventKind.AndroidExit,
            at,
            ReportHeadlines.ANDROID_EXIT_SOURCE,
            "${kind.name} reason=$reason subReason=-1 status=0 importance=125",
        )

    private fun error(source: String, message: String = "java.lang.IllegalStateException: boom") =
        ProblemReportEvent(ProblemEventKind.Error, at, source, message)

    @Test
    fun `E19-S04 a force-stop from Android settings is not a problem and gives no headline`() {
        assertNull(ReportHeadlines.of(exit(ExitKind.ForceStop)))
        assertNull(ReportHeadlines.newest(listOf(exit(ExitKind.ForceStop))))
    }

    @Test
    fun `E19-S04 an update is not a problem and gives no headline`() {
        assertNull(ReportHeadlines.of(exit(ExitKind.Update, reason = 13)))
    }

    @Test
    fun `E19-S04 a normal end gives no headline`() {
        assertNull(ReportHeadlines.of(exit(ExitKind.Normal, reason = 1)))
    }

    @Test
    fun `E19-S04 a crash says the app closed unexpectedly`() {
        assertEquals(ReportHeadline.AppClosedUnexpectedly, ReportHeadlines.of(exit(ExitKind.Crash, reason = 4)))
    }

    @Test
    fun `E19-S04 low memory and the memory limiter say Android freed memory`() {
        assertEquals(ReportHeadline.AndroidFreedMemory, ReportHeadlines.of(exit(ExitKind.LowMemory, reason = 3)))
        assertEquals(ReportHeadline.AndroidFreedMemory, ReportHeadlines.of(exit(ExitKind.MemoryLimit, reason = 9)))
    }

    @Test
    fun `E19-S04 any other system kill says Android closed the app`() {
        assertEquals(ReportHeadline.AndroidClosedApp, ReportHeadlines.of(exit(ExitKind.Killed, reason = 12)))
    }

    @Test
    fun `E19-S04 an exit record that cannot be read still gets plain words`() {
        val odd = ProblemReportEvent(ProblemEventKind.AndroidExit, at, ReportHeadlines.ANDROID_EXIT_SOURCE, "reason=99")
        assertEquals(ReportHeadline.AndroidClosedApp, ReportHeadlines.of(odd))
    }

    @Test
    fun `E19-S04 every exit kind is mapped`() {
        for (kind in ExitKind.entries) {
            val expected = ReportHeadlines.exit(kind)
            assertEquals(expected, ReportHeadlines.of(exit(kind)), kind.name)
        }
    }

    @Test
    fun `E19-S04 a force-stop newer than a real error leaves the error to name the report`() {
        val events = listOf(exit(ExitKind.ForceStop), error("sse"))
        assertEquals(ReportHeadline.Connection, ReportHeadlines.newest(events))
    }

    @Test
    fun `E19-S04 timings and thermal readings never name the report`() {
        val events =
            listOf(
                ProblemReportEvent(ProblemEventKind.Timing, at, "OCTRACE", "open 120ms"),
                ProblemReportEvent(ProblemEventKind.Thermal, at, "android.thermal", "status=severe headroom=unknown"),
            )
        assertNull(ReportHeadlines.newest(events))
    }

    @Test
    fun `E19-S04 kept errors are named by where they came from`() {
        assertEquals(ReportHeadline.Connection, ReportHeadlines.of(error("sse.reconnect")))
        assertEquals(ReportHeadline.Screen, ReportHeadlines.of(error("widget")))
        assertEquals(ReportHeadline.AppClosedUnexpectedly, ReportHeadlines.of(error("crash.native")))
        assertEquals(ReportHeadline.AppNotResponding, ReportHeadlines.of(error("crash.anr")))
        assertEquals(ReportHeadline.Startup, ReportHeadlines.of(error("bootstrap")))
        assertEquals(ReportHeadline.ReportStore, ReportHeadlines.of(error("report-problem")))
        assertEquals(ReportHeadline.Generic, ReportHeadlines.of(error("app")))
    }
}
