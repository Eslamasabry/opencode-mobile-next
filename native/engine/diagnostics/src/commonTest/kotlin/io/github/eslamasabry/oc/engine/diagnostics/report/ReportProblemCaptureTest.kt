package io.github.eslamasabry.oc.engine.diagnostics.report

import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReportProblemCaptureTest {
    private val clock = TestClock()
    private val redactor = Redactor()
    private val files = FakeReportFiles()
    private val diagnostics = Diagnostics(redactor, clock)
    private val trace = PerfTrace(redactor, clock)
    private var report = open()
    private var capture: ReportProblemCapture? = null

    private fun open(maxEntries: Int = 128) = assertNotNull(ReportProblem.open(files, redactor, clock, maxEntries).getOrNull())

    private fun attach() = ReportProblemCapture(report, diagnostics, trace).also { capture = it }

    @AfterTest
    fun tearDown() {
        capture?.close()
    }

    @Test
    fun `B-SEC-148 buffered errors and timings are captured once and repeated errors are kept`() {
        val at = clock.now()
        diagnostics.record("test", "failed", at = at)
        trace.mark("before.attach")
        attach()
        assertEquals(2, report.entries.size)

        trace.mark("after.attach")
        assertEquals(3, report.entries.size)
        diagnostics.record("test", "failed", at = at)
        assertEquals(4, report.entries.size)
        diagnostics.record("test", "different", at = at)
        assertEquals(5, report.entries.size)
        assertEquals(2, report.entries.count { it.message == "failed" })
    }

    @Test
    fun `B-SEC-148 a long span that finishes after a newer span is kept`() {
        attach()
        val slow = trace.begin("slow")
        trace.mark("fast")
        slow.finish()
        assertEquals(2, report.entries.size)
        assertContains(report.reportText(), "slow")
        assertContains(report.reportText(), "fast")
    }

    @Test
    fun `B-STATE-070 a finished timing is on disk before the call that finished it returns`() {
        attach()
        trace.mark("same.turn.crash.evidence")
        assertEquals(1, report.entries.size)
        assertContains(assertNotNull(files.text(ReportFile.Snapshot)), "same.turn.crash.evidence")
    }

    @Test
    fun `B-STATE-071 only the report capacity of historical timings is imported, in one commit`() {
        report = open(maxEntries = 2)
        for (index in 0 until 5) trace.mark("historical.$index")
        val before = report.state.value.committed
        attach()
        assertEquals(before + 1, report.state.value.committed)
        assertEquals(2, report.entries.size)
        assertContains(report.reportText(), "historical.3")
        assertContains(report.reportText(), "historical.4")
        assertFalse("historical.2" in report.reportText())
    }

    @Test
    fun `B-STATE-072 clearing diagnostics with an empty ring erases restored evidence without trace resurrection`() {
        report.recordError("previous process")
        report = open()
        trace.mark("old.timing")
        attach()
        assertContains(report.reportText(), "previous process")
        assertTrue(diagnostics.isEmpty)

        diagnostics.clear()
        assertTrue(report.entries.isEmpty())
        trace.mark("new.timing")
        assertEquals(1, report.entries.size)
        assertContains(report.reportText(), "new.timing")
        assertFalse("old.timing" in report.reportText())
        assertFalse("previous process" in open().reportText())
    }

    @Test
    fun `B-STATE-073 close detaches every source and can be repeated safely`() {
        val attached = attach()
        attached.close()
        attached.close()
        assertTrue(attached.isClosed)
        diagnostics.record("test", "after.close")
        trace.mark("after.close")
        diagnostics.clear()
        assertTrue(report.entries.isEmpty())
        assertNull(files.text(ReportFile.Snapshot))
    }

    @Test
    fun `B-STATE-073 a failing store never escapes the diagnostics listeners`() {
        attach()
        diagnostics.record("test", "saved first")
        files.refuseWrites = true
        files.refuseDelete += ReportFile.Snapshot
        diagnostics.record("test", "safe failure")
        trace.mark("safe.timing")
        assertTrue(report.storageFailed)
        diagnostics.clear()
        assertTrue(diagnostics.isEmpty)
        assertTrue(report.storageFailed)
        assertEquals(listOf("saved first"), report.entries.map { it.message }, "nothing unsaved is published")
    }

    @Test
    fun `B-SEC-148 trace and captured report redact provider and registered keys`() {
        val known = "fake-registered-trace-credential"
        val provider = "sk-ant-fakeProviderCredential12345"
        redactor.registerSecret(known)
        val logs = mutableListOf<String>()
        val logged = PerfTrace(redactor, clock, logSink = { logs += it })
        capture = ReportProblemCapture(report, diagnostics, logged)
        val parent = logged.begin("parent $provider")
        val child = logged.begin("child $known", attrs = mapOf("detail" to "$known $provider"), parent = parent)
        child.finish()
        parent.finish()
        val persisted = assertNotNull(files.text(ReportFile.Snapshot))
        assertTrue(logs.isNotEmpty())
        for (value in listOf(logs.joinToString("\n"), report.reportText(), persisted)) {
            assertFalse(known in value)
            assertFalse(provider in value)
        }
    }

    @Test
    fun `B-STATE-074 framework error callbacks are recorded with a category and no caller metadata`() {
        val secret = "fake-forwarded-credential"
        redactor.registerSecret(secret)
        var read = 0
        val hostile =
            object : IllegalStateException(secret) {
                override val message: String
                    get() {
                        read++
                        return secret
                    }

                override fun toString(): String {
                    read++
                    return secret
                }
            }
        var forwarded: Throwable? = null
        val handler = AppErrorCapture(diagnostics).handler(previous = { forwarded = it })
        handler(hostile)

        val sent = assertNotNull(forwarded)
        assertEquals("Invalid state", sent.message)
        assertEquals("Invalid state", sent.toString())
        assertNull(sent.cause)
        assertEquals(0, read, "the error's text is never read")
        val entry = diagnostics.errors.value.single()
        assertEquals("Invalid state", entry.message)
        assertEquals(AppErrorCapture.FRAMEWORK_SOURCE, entry.source)
        assertEquals("", entry.stack)
        assertFalse(secret in diagnostics.reportText())
    }

    @Test
    fun `B-STATE-074 a framework error with no previous handler still records only its category`() {
        attach()
        val safe = AppErrorCapture(diagnostics).capture(IllegalArgumentException("password=fake-secret"), source = "ui")
        assertEquals("Invalid argument", safe.category)
        assertSame(null, safe.cause)
        assertEquals("Invalid argument", report.entries.single().message)
        assertEquals("ui", report.entries.single().source)
        assertFalse("fake-secret" in report.reportText())
    }
}
