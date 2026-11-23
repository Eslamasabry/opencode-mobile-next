package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** B-STATE-165/166: a busy conversation costs the saved report one redaction per event and one write per window. */
class ReportChurnTest {
    private class CountingFiles(val inner: FakeReportFiles) : ReportFiles by inner {
        var writes = 0

        override fun writePending(bytes: ByteArray) {
            writes++
            inner.writePending(bytes)
        }

        override fun writePendingCached(bytes: ByteArray) {
            writes++
            inner.writePending(bytes)
        }
    }

    @Test
    fun `B-STATE-165 a new report entry is redacted once and kept entries are not redacted again`() {
        val report = assertNotNull(ReportProblem.open(FakeReportFiles(), Redactor(), TestClock()).getOrNull())

        for (index in 0 until 200) report.recordError("failure $index")

        assertEquals(200, report.redactions)
        assertEquals(128, report.entries.size)
        assertEquals("failure 199", report.entries.last().message)
    }

    @Test
    fun `B-STATE-166 timings finished close together reach the disk in one write per five seconds`() {
        val clock = TestClock()
        val redactor = Redactor()
        val diagnostics = Diagnostics(redactor, clock)
        val trace = PerfTrace(redactor, clock)
        val inner = FakeReportFiles()
        val files = CountingFiles(inner)
        val report = assertNotNull(ReportProblem.open(files, redactor, clock).getOrNull())
        val capture = ReportProblemCapture(report, diagnostics, trace)
        val before = files.writes

        for (index in 0 until 200) trace.mark("span.$index")
        assertTrue(files.writes - before <= 1, "writes for 200 marks in one window: ${files.writes - before}")

        clock.advance(5.seconds)
        trace.mark("span.200")
        assertEquals(2, files.writes - before)
        assertEquals(128, report.entries.size)
        assertTrue(report.entries.any { "span.199" in it.message })
        assertTrue(report.entries.any { "span.200" in it.message })

        trace.mark("span.201")
        diagnostics.record("test", "boom")
        assertTrue(report.entries.any { "span.201" in it.message })
        assertEquals("boom", report.entries.last().message)

        trace.mark("span.202")
        capture.close()
        assertTrue("span.202" in assertNotNull(inner.text(ReportFile.Snapshot)))
    }
}
