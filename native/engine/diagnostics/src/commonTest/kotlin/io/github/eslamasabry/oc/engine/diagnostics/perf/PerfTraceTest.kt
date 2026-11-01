package io.github.eslamasabry.oc.engine.diagnostics.perf

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class PerfTraceTest {
    private val logged = mutableListOf<String>()
    private val time = TestTimeSource()
    private fun trace(capacity: Int = PerfTrace.CAPACITY) = PerfTrace(Redactor(), TestClock(), time, capacity, logged::add)

    private fun PerfTrace.only(name: String) = spans().single { it.name == name }

    @Test
    fun `B-BUILD-025 spans nest through the coroutine context`() =
        runTest {
            val trace = trace()
            trace.span("outer") {
                trace.span("inner") { trace.mark("inside", parent = trace.current()) }
                trace.measure("sync", parent = trace.current()) { 1 }
            }
            assertEquals("outer", trace.only("inner").parent)
            assertEquals("outer", trace.only("sync").parent)
            assertEquals("inner", trace.only("inside").parent)
            assertTrue(trace.only("inside").isMark)
            assertNull(trace.only("outer").parent)
            assertEquals(listOf("inside", "inner", "sync", "outer"), trace.spans().map { it.name })
        }

    @Test
    fun `B-BUILD-025 work that outlives its parent is not attributed to it`() =
        runTest {
            val trace = trace()
            val later = CompletableDeferred<Unit>()
            var background: Job? = null
            trace.span("connect") {
                // A stream started inside connect keeps its context; what it does after connect ended is not connect's.
                val context = currentCoroutineContext().minusKey(Job)
                background = backgroundScope.launch(context) { later.await(); trace.span("events.refresh") {} }
            }
            later.complete(Unit)
            background!!.join()
            assertNull(trace.only("events.refresh").parent)
        }

    @Test
    fun `B-BUILD-025 an error passes through untouched and marks the span`() =
        runTest {
            val trace = trace()
            val error = IllegalStateException("boom")
            // Through a coroutine the error may be a stack-recovered copy of the same type and message.
            assertEquals("boom", assertFailsWith<IllegalStateException> { trace.span<Unit>("fails") { throw error } }.message)
            assertSame(error, assertFailsWith<IllegalStateException> { trace.measure<Unit>("fails.sync") { throw error } })
            assertEquals(PerfOutcome.Error, trace.only("fails").outcome)
            assertTrue(trace.only("fails.sync").failed)
            assertEquals(2, logged.count { "outcome=error" in it })
        }

    @Test
    fun `B-BUILD-025 the ring keeps only the newest spans`() {
        val trace = trace(capacity = 3)
        for (i in 0 until 5) trace.measure("step$i") {}
        assertEquals(listOf("step2", "step3", "step4"), trace.spans().map { it.name })
        assertEquals(listOf("step4", "step3"), trace.recent(2).map { it.name })
        for (i in 5 until 12) trace.measure("step$i") {}
        assertEquals(listOf("step9", "step10", "step11"), trace.spans().map { it.name })
    }

    @Test
    fun `B-BUILD-025 markOnce fires once per process`() {
        val trace = trace()
        trace.markOnce("app.first_frame")
        trace.markOnce("app.first_frame")
        assertEquals(1, trace.spans().count { it.name == "app.first_frame" })
    }

    @Test
    fun `B-BUILD-025 log lines have the fixed tag and shape and fast spans stay out of the log`() =
        runTest {
            val trace = trace()
            trace.span("connect.health", attrs = mapOf("n" to 2)) {}
            trace.begin("http oc1 GET /path", logMinMs = 50).finish()
            assertEquals(2, trace.spans().size)
            assertEquals(1, logged.size)
            assertTrue(Regex("""^OCTRACE \d+(\.\d)?ms connect\.health n=2$""").matches(logged.single()), logged.single())
        }

    @Test
    fun `B-BUILD-025 recordSince and recordDuration time work across callbacks`() {
        val trace = trace()
        val start = trace.nowMicros
        time += 2_500.milliseconds / 1000
        trace.recordSince("events.first", start, attrs = mapOf("api" to "oc1"))
        trace.recordDuration("setup.component", 8.seconds, attrs = mapOf("id" to "linux"))
        assertEquals(2_500, trace.only("events.first").durationMicros)
        assertEquals(8000.0, trace.only("setup.component").durationMs)
        assertTrue(logged.last().startsWith("OCTRACE 8000ms setup.component id=linux"), logged.last())
        assertEquals("1.0ms", formatMs(1.0))
        assertEquals("9.9ms", formatMs(9.94))
        assertEquals("12ms", formatMs(12.4))
    }

    @Test
    fun `B-BUILD-025 stats group by name with nearest-rank percentiles`() {
        val trace = trace()
        for (ms in listOf(10, 20, 30, 40, 1000)) trace.recordDuration("catalog.load", ms.milliseconds)
        trace.recordDuration("sessions.refresh", 5.milliseconds, error = true)
        trace.mark("not.a.stat")
        val stats = trace.stats()
        assertEquals(listOf("catalog.load", "sessions.refresh"), stats.map { it.name })
        val catalog = stats.first()
        assertEquals(5, catalog.count)
        assertEquals(30.0, catalog.p50Ms)
        assertEquals(1000.0, catalog.p95Ms)
        assertEquals(1000.0, catalog.maxMs)
        assertEquals(1, stats.last().errors)
    }

    @Test
    fun `B-SEC-117 secret-named attributes and token-like values are redacted`() {
        val trace = trace()
        trace.measure(
            "login",
            attrs =
                mapOf(
                    "Authorization" to "Basic b3BlbmNvZGU6aHVudGVyMg==",
                    "password" to "hunter2",
                    "api_key" to "k",
                    "note" to "value ${"a1B2".repeat(10)} and http://user:pw@host/x",
                ),
        ) {}
        val span = trace.only("login")
        assertEquals(PerfTrace.REDACTED, span.attrs["Authorization"])
        assertEquals(PerfTrace.REDACTED, span.attrs["password"])
        assertEquals(PerfTrace.REDACTED, span.attrs["api_key"])
        assertFalse("a1B2a1B2" in span.attrs["note"]!!)
        assertFalse("user:pw" in span.attrs["note"]!!)
        val report = trace.reportText()
        assertFalse("hunter2" in report)
        assertFalse("b3BlbmNvZGU6aHVudGVyMg" in report)
        assertFalse(logged.any { "hunter2" in it || "b3BlbmNvZGU6aHVudGVyMg" in it })
    }

    @Test
    fun `B-SEC-117 names carrying a registered secret or a long token never reach the ring`() {
        val redactor = Redactor()
        redactor.registerSecret("hunter2-secret")
        val trace = PerfTrace(redactor, TestClock(), time, logSink = logged::add)
        trace.mark("http GET /x/hunter2-secret/${"q8Zx1kP0vR7mN2sL5tY9wE3uI6oA4dFgHjK"}")
        val name = trace.spans().single().name
        assertFalse("hunter2-secret" in name)
        assertFalse("q8Zx1kP0vR7mN2sL5tY9wE3uI6oA4dFgHjK" in name)
        assertTrue(logged.single().startsWith("OCTRACE mark "))
    }

    @Test
    fun `B-SEC-117 path templates drop ids and every query value`() {
        assertEquals("/session/:id/message", TraceLabels.pathTemplate("/session/ses_4fA9kQ2zzZ/message?limit=100"))
        assertEquals(
            "/api/session/:id/permission/:id",
            TraceLabels.pathTemplate(
                "http://127.0.0.1:4096/api/session/0b7c6c8e-1f2a-4f7e-9c1d-3a2b1c0d9e8f/permission/per_abcdef123?auth_token=secret",
            ),
        )
        assertEquals("/pty/:id/connect", TraceLabels.pathTemplate("/pty/42/connect"))
        assertEquals("/file/:id", TraceLabels.pathTemplate("/file/%2Fhome%2Fme"))
        assertEquals("/provider", TraceLabels.pathTemplate("/provider"))
        assertEquals("/", TraceLabels.pathTemplate("http://host"))
    }

    @Test
    fun `B-SEC-117 the password script is only ever labelled generically`() {
        val password = "hunter2-secret"
        val script =
            "set -eu\numask 077\nmkdir -p /root/.oc-builtin\nprintf '%s' '$password' > ${TraceLabels.PASSWORD_FILE}.tmp\n" +
                "mv ${TraceLabels.PASSWORD_FILE}.tmp ${TraceLabels.PASSWORD_FILE}\n"
        assertEquals("write-password", TraceLabels.scriptLabel(script))
        assertEquals("test -d '…'", TraceLabels.scriptLabel("test -d '/p/secret'"))
        assertEquals("ls -la /root", TraceLabels.scriptLabel("set -eu\n\n# c\nls -la /root\n"))
        assertEquals("script", TraceLabels.scriptLabel("set -eu\n"))
    }
}
