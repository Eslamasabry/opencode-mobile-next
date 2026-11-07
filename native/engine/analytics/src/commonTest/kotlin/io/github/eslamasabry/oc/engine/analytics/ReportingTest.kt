package io.github.eslamasabry.oc.engine.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingReportSink(override val id: SinkId = SinkId.Crashlytics) : ReportSink {
    val reports = mutableListOf<SafeReport>()
    val crumbs = mutableListOf<String>()
    val enabled = mutableListOf<Boolean>()

    override fun setCollectionEnabled(enabled: Boolean) {
        this.enabled += enabled
    }

    override fun breadcrumb(text: String) {
        crumbs += text
    }

    override fun record(report: SafeReport) {
        reports += report
    }
}

class ReportingTest {
    @Test
    fun `B-TELE-001 a token, url, path or sentence in a failure key never reaches the sink`() {
        val sink = RecordingReportSink()
        val reporter = ErrorReporter(sink, initialConsent = true)
        reporter.reportFailure(
            "prompt_not_accepted",
            mapOf(
                "step" to "dispatch",
                "server_version" to "2.1.1",
                "elapsed_ms" to "1234",
                "detail" to "sk-abcdef1234567890",
                "where" to "https://example.com/a?token=1",
                "file" to "/home/me/secret.txt",
                "text" to "please fix my code",
                "Bad Key" to "x",
                "api_key" to "fine",
            ),
        )
        val keys = sink.reports.single().keys
        assertEquals("dispatch", keys["step"])
        assertEquals("2.1.1", keys["server_version"])
        assertEquals("1234", keys["elapsed_ms"])
        for (k in listOf("detail", "where", "file", "text")) assertEquals(ReportRedactor.REDACTED, keys[k], k)
        assertFalse("Bad Key" in keys)
        assertFalse("api_key" in keys)
    }

    @Test
    fun `B-TELE-002 a stack keeps class method line frames and drops messages and paths`() {
        val sink = RecordingReportSink()
        val reporter = ErrorReporter(sink, initialConsent = true)
        reporter.reportCrash(
            "java.lang.IllegalStateException",
            listOf("a.B.run:12", "boom: password is hunter2", "/data/user/0/app/x.kt:3", "c.D.\$lambda:7"),
        )
        val report = sink.reports.single()
        assertEquals(ReportKind.Crash, report.kind)
        assertEquals("java.lang.IllegalStateException", report.name)
        assertEquals(listOf("a.B.run:12", "c.D.\$lambda:7"), report.stack)
    }

    @Test
    fun `B-TELE-003 an odd exception category and failure name are replaced`() {
        val sink = RecordingReportSink()
        val reporter = ErrorReporter(sink, initialConsent = true)
        reporter.reportCrash("Oops: token=abc", emptyList())
        reporter.reportFailure("Has Spaces And Text", emptyMap())
        assertEquals("java.lang.Throwable", sink.reports[0].name)
        assertEquals("unnamed_failure", sink.reports[1].name)
    }

    @Test
    fun `B-TELE-004 breadcrumbs are short lower-case words, anything else is redacted`() {
        val sink = RecordingReportSink()
        val reporter = ErrorReporter(sink, initialConsent = true)
        reporter.breadcrumb("setup step download")
        reporter.breadcrumb("opened https://x.example/y")
        reporter.breadcrumb("Fix my AWS key AKIA1234567890ABCDEF")
        reporter.breadcrumb("x".repeat(100))
        assertEquals(
            listOf("setup step download", ReportRedactor.REDACTED, ReportRedactor.REDACTED, ReportRedactor.REDACTED),
            sink.crumbs,
        )
    }

    @Test
    fun `B-TELE-005 with consent off nothing reaches the sink and turning it on lets reports through`() {
        val sink = RecordingReportSink()
        var stored: Boolean? = null
        val reporter = ErrorReporter(sink, initialConsent = false) { stored = it }
        reporter.reportCrash("java.io.IOException", listOf("a.B.c:1"))
        reporter.reportFailure("voice_model_download_failed")
        reporter.breadcrumb("opened settings")
        assertTrue(sink.reports.isEmpty() && sink.crumbs.isEmpty())
        assertEquals(listOf(false), sink.enabled)

        reporter.setReportingConsent(true)
        reporter.reportFailure("voice_model_download_failed")
        assertEquals(1, sink.reports.size)
        assertEquals(listOf(false, true), sink.enabled)
        assertEquals(true, stored)

        reporter.setReportingConsent(false)
        reporter.reportFailure("voice_model_download_failed")
        assertEquals(1, sink.reports.size)
        assertEquals(false, reporter.reportingConsent.value)
    }

    @Test
    fun `B-TELE-006 swapping the vendor sink gives the new sink the same safe report`() {
        val first = RecordingReportSink(SinkId.Crashlytics)
        val second = RecordingReportSink(SinkId.Custom)
        val keys = mapOf("step" to "unpack", "elapsed_ms" to "90")
        ErrorReporter(first, true).reportFailure("setup_step_failed", keys)
        ErrorReporter(second, true).reportFailure("setup_step_failed", keys)
        assertEquals(first.reports, second.reports)
        assertEquals(SafeReport(ReportKind.Failure, "setup_step_failed", keys), second.reports.single())
    }

    @Test
    fun `B-TELE-007 a sink that throws never breaks the caller and the no-op default reports nothing`() {
        val throwing =
            object : ReportSink by NoOpReportSink {
                override fun record(report: SafeReport) = error("vendor down")
            }
        ErrorReporter(throwing, true).reportFailure("setup_step_failed")
        NoReporting.reportFailure("x")
        NoReporting.reportCrash("y", emptyList())
        assertFalse(NoReporting.reportingConsent.value)
        assertEquals(SinkId.None, NoOpReportSink.id)
    }
}
