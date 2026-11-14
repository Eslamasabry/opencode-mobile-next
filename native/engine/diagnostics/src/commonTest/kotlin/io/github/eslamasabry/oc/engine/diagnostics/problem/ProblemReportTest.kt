package io.github.eslamasabry.oc.engine.diagnostics.problem

import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.Diagnostics
import io.github.eslamasabry.oc.engine.diagnostics.job.FailedJobReport
import io.github.eslamasabry.oc.engine.diagnostics.job.SetupComponent
import io.github.eslamasabry.oc.engine.diagnostics.job.SetupComponentState
import io.github.eslamasabry.oc.engine.diagnostics.job.SetupJobSnapshot
import io.github.eslamasabry.oc.engine.diagnostics.job.SetupJobState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Fake credentials, split so no scanner mistakes this file for a leak.
private const val FAKE_ANTHROPIC = "sk-ant-" + "api03-FAKEFAKEFAKEFAKEFAKE1234567890"
private const val FAKE_OPENAI = "sk-proj-" + "FAKEfakeFAKEfake0987654321zzzz"
private const val FAKE_BEARER = "Bearer " + "eyFAKEtokenFAKEtokenFAKEtoken12345"
private const val FAKE_PASSWORD = "hunter2-fake-server-pass"
private const val FAKE_REGISTERED = "loaded-provider-secret-FAKE-42"
private const val PROVIDERS_JSON =
    "{\"providers\":[{\"id\":\"anthropic\",\"options\":{\"apiKey\":\"$FAKE_ANTHROPIC\"}}," +
        "{\"id\":\"openai\",\"key\":\"$FAKE_OPENAI\"}]}"

/** Synthetic documentation-range style addresses built at run time (no literal address in the source). */
private val FAKE_LAN = listOf(192, 168, 1, 20).joinToString(".")
private val FAKE_PRIVATE = listOf(10, 0, 0, 7).joinToString(".")
private val FAKE_TAILNET = listOf(100, 70, 12, 4).joinToString(".")

class ProblemReportTest {
    private val redactor = Redactor()
    private val base = Instant.parse("2026-09-27T10:00:00Z")

    private fun event(
        message: String,
        kind: ProblemEventKind = ProblemEventKind.Error,
        stack: String = "",
        source: String = "flutter",
        minute: Int = 0,
    ) = ProblemReportEvent(kind, base + minute.minutes, source, message, stack)

    private fun build(
        description: String,
        version: String = "1.0.44+50",
        error: AttachedError? = null,
        connection: String? = null,
        fallbackTitle: String? = null,
        events: List<ProblemReportEvent> = emptyList(),
        maxErrors: Int = 10,
        maxTimings: Int = 10,
        maxStackLines: Int = 8,
    ) = ProblemReport.build(
        redactor, description, version, "Android", error, connection, fallbackTitle, events, maxErrors, maxTimings, maxStackLines,
    )

    /** Splits a form-encoded query back into its fields (as GitHub reads them). */
    private fun query(uri: String): Map<String, String> =
        uri.substringAfter('?').split('&').associate { pair ->
            val (k, v) = pair.split('=', limit = 2)
            decode(k) to decode(v)
        }

    private fun decode(s: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < s.length) {
            when (val c = s[i]) {
                '+' -> bytes += ' '.code.toByte()
                '%' -> {
                    bytes += s.substring(i + 1, i + 3).toInt(16).toByte()
                    i += 2
                }
                else -> bytes += c.code.toByte()
            }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    @Test
    fun `B-SEC-126 a report carries title, description, the attached error, environment and diagnostics newest first`() {
        val report =
            build(
                description = "Tapped Send\nNothing happened",
                error =
                    AttachedError(
                        title = "Couldn't load files", details = "FormatException: bad listing",
                        source = "files-viewer", errorType = "FormatException",
                    ),
                events =
                    listOf(
                        event("newest", minute = 5, stack = "#0 main (lib/a.dart:1)"),
                        event("OCTRACE chat.open 120ms", kind = ProblemEventKind.Timing, source = "OCTRACE", minute = 4),
                        event("oldest", minute = 1),
                    ),
            )
        assertEquals("Couldn't load files", report.title)
        assertEquals("1.0.44+50", report.version)
        assertTrue(report.whatHappened.startsWith("Tapped Send\nNothing happened"))
        assertTrue("Error\nType: FormatException" in report.whatHappened)
        assertFalse("Couldn't load files" in report.whatHappened)
        assertTrue("Where: files-viewer" in report.whatHappened)
        assertTrue("App: 1.0.44+50" in report.whatHappened)
        assertTrue("Platform: Android" in report.whatHappened)
        assertTrue(report.diagnostics.indexOf("newest") < report.diagnostics.indexOf("oldest"))
        assertTrue("[2026-09-27 10:05:00 UTC] error" in report.diagnostics)
        assertTrue("    #0 main (lib/a.dart:1)" in report.diagnostics)
        assertEquals("${report.title}\n\n${report.whatHappened}\n\nDiagnostics\n${report.diagnostics}", report.text)
    }

    @Test
    fun `B-SEC-126 without an error the first description line is the title and no Diagnostics part without diagnostics`() {
        val report = build(description = "  The terminal froze after rotating\nmore words ")
        assertEquals("The terminal froze after rotating", report.title)
        assertTrue(report.diagnostics.isEmpty())
        assertFalse("Diagnostics" in report.text)
        assertTrue(report.text.startsWith("The terminal froze after rotating\n"))
        assertEquals(1, Regex("The terminal froze after rotating").findAll(report.text).count())
    }

    @Test
    fun `B-SEC-126 keeps a bounded number of errors, timings and stack lines`() {
        val report =
            build(
                description = "x", version = "1", maxErrors = 2, maxTimings = 1, maxStackLines = 2,
                events =
                    listOf(
                        event("e1", stack = "f1\nf2\nf3\nf4"),
                        event("t1", kind = ProblemEventKind.Timing),
                        event("t2", kind = ProblemEventKind.Timing),
                        event("e2"),
                        event("e3"),
                    ),
            )
        assertTrue("e1" in report.diagnostics)
        assertTrue("e2" in report.diagnostics)
        assertFalse("e3" in report.diagnostics)
        assertTrue("t1" in report.diagnostics)
        assertFalse("t2" in report.diagnostics)
        assertTrue("    f2" in report.diagnostics)
        assertFalse("    f3" in report.diagnostics)
        assertTrue("… 2 more lines" in report.diagnostics)
    }

    @Test
    fun `B-SEC-126 the platform label and the version fallback`() =
        runTest {
            assertEquals("Windows desktop (experimental — contributor-tested)", ReportPlatform.Windows.label)
            assertEquals("Android", ReportPlatform.Android.label)
            assertEquals("1.0.31+32", ProblemReports.appVersion { "1.0.31" to "32" })
            assertEquals("unknown+0", ProblemReports.appVersion { error("plugin failed") })
            assertEquals("unknown+0", ProblemReports.appVersion { null })
            // A plugin that never answers gives up after two seconds.
            assertEquals("unknown+0", ProblemReports.appVersion(2.seconds) { awaitCancellation() })
        }

    @Test
    fun `B-SEC-127 the connection goes in the environment scrubbed and the newest kept error titles an empty report`() {
        val report =
            build(
                description = "", version = "1.4.0+54",
                connection = "OpenCode 2.0.28 · on this phone · connected",
                fallbackTitle = "Lost the live connection to the server",
            )
        assertEquals("Lost the live connection to the server", report.title)
        assertTrue(
            report.whatHappened.endsWith("Platform: Android\nConnection: OpenCode 2.0.28 · on this phone · connected"),
        )
        val said =
            build(
                description = "Send did nothing", version = "1.4.0+54",
                connection = "http://$FAKE_TAILNET:4096 · connected",
                fallbackTitle = "Lost the live connection to the server",
            )
        assertEquals("Send did nothing", said.title)
        assertFalse(FAKE_TAILNET in said.whatHappened)
        val none = build(description = "", version = "1.4.0+54")
        assertEquals("Problem report", none.title)
        assertFalse("Connection:" in none.whatHappened)
    }

    @Test
    fun `B-SEC-127 a failed job report carries no timing traces and still keeps the errors around it`() {
        val report =
            build(
                description = "", version = "1.0.44+62",
                error =
                    AttachedError(
                        title = "OpenCode was installed, but it did not start.",
                        source = "failed job · setup · setup-1",
                        log = "==> Getting the model list\n[oc] OpenCode was installed but did not start",
                    ),
                events =
                    List(12) { event("OCTRACE 2.9ms linux.setupStatus", ProblemEventKind.Timing, source = "OCTRACE", minute = it) } +
                        event("StateError: kept", minute = 20),
            )
        assertFalse("OCTRACE" in report.diagnostics)
        assertFalse("timing ·" in report.diagnostics)
        assertTrue("[oc] OpenCode was installed but did not start" in report.diagnostics)
        assertTrue("StateError: kept" in report.diagnostics)
        val plain =
            build(
                description = "slow", version = "1.0.44+62",
                events = listOf(event("OCTRACE chat.open 120ms", ProblemEventKind.Timing, source = "OCTRACE")),
            )
        assertTrue("OCTRACE chat.open" in plain.diagnostics)
    }

    @Test
    fun `B-SEC-127 a failed setup job attaches its own log through the report`() {
        val job =
            assertNotNull(
                FailedJobReport.setup(
                    redactor,
                    SetupJobSnapshot(
                        SetupJobState.Failed, listOf(SetupComponent("opencode", SetupComponentState.Failed, "Could not install")),
                        jobId = "setup-1", logTail = "OCTRACE 1ms x\nboom",
                    ),
                    componentId = "opencode",
                ),
            ).toAttachment()
        val report = build(description = "", error = AttachedError(job.title, job.details, job.source, log = job.log))
        assertTrue(report.diagnostics.startsWith("Failed job log (last lines)\nboom"))
        val none = build(description = "", error = AttachedError("t", log = ""))
        assertTrue(none.diagnostics.startsWith("Failed job log: none was kept"))
    }

    @Test
    fun `B-SEC-127 the persisted store and the in-memory list feed the same events newest first`() {
        val diagnostics = Diagnostics(Redactor(), TestClock())
        diagnostics.record("flutter", "Bad state: first")
        diagnostics.record("sse", "Bad state: second")
        val memory = ProblemReports.events(null, diagnostics.errors.value)
        assertEquals(listOf("Bad state: second", "Bad state: first"), memory.map { it.message })
        assertTrue(memory.all { it.isError })
        val stored = memory.asReversed()
        assertEquals(memory, ProblemReports.events(stored, emptyList()))
    }

    @Test
    fun `B-SEC-128 credentials and server addresses are absent from every part and the link`() {
        redactor.registerSecret(FAKE_REGISTERED)
        val dirty =
            "GET https://phone.tail1234.ts.net:4096/config/providers " +
                "failed at http://$FAKE_LAN:4096/session?x=1\n" +
                "then tried $FAKE_PRIVATE:4096 directly\n" +
                "Authorization: $FAKE_BEARER\n" +
                "password=$FAKE_PASSWORD\n" +
                "loaded $FAKE_REGISTERED\n" +
                PROVIDERS_JSON
        val report =
            build(
                description = "Setup broke. $dirty",
                error = AttachedError(title = "Sync failed", details = dirty),
                events = listOf(event(dirty, stack = dirty, source = "sse $FAKE_OPENAI")),
            )
        val link = report.link(maxLength = 100_000)
        val parts = listOf(report.text, link.uri, decode(link.uri.substringAfter('?'))) + query(link.uri).values
        for (part in parts) {
            for (secret in listOf(
                FAKE_ANTHROPIC, FAKE_OPENAI, "eyFAKEtokenFAKEtokenFAKEtoken12345", FAKE_PASSWORD, FAKE_REGISTERED,
                "phone.tail1234.ts.net", FAKE_LAN, FAKE_PRIVATE,
            )) {
                assertFalse(secret in part, secret)
            }
        }
        assertTrue("https://[server]/config/providers" in report.text)
        assertTrue("[address]" in report.text)
    }

    private fun big(events: Int = 40, description: String = "It broke") =
        build(
            description = description,
            events = List(events) { event("error number $it with some words", stack = "frame $it") },
            maxErrors = events,
        )

    @Test
    fun `B-SEC-129 a short report is prefilled whole and passes the external-link policy`() {
        val report = build(description = "Short", events = listOf(event("one error")))
        val link = report.link()
        assertEquals(ProblemReportClipboard.None, link.clipboard)
        assertNull(link.clipboardText)
        assertNotNull(ProblemReports.approve(ExternalLinkPolicy(), link))
        assertTrue(link.uri.startsWith("https://github.com/Eslamasabry/opencode-mobile-next/issues/new?"))
        val q = query(link.uri)
        assertEquals("bug_report.yml", q["template"])
        assertEquals(report.title, q["title"])
        assertEquals("1.0.44+50", q["app-version"])
        assertEquals(report.whatHappened, q["what-happened"])
        assertEquals(report.diagnostics, q["logs"])
    }

    @Test
    fun `B-SEC-129 long diagnostics go to the clipboard and the form says to paste them, the link still fits`() {
        val report = big()
        val link = report.link()
        assertEquals(ProblemReportClipboard.Diagnostics, link.clipboard)
        assertEquals(report.diagnostics, link.clipboardText)
        assertTrue(link.uri.length <= 2048)
        assertNotNull(ProblemReports.approve(ExternalLinkPolicy(), link))
        assertEquals(report.whatHappened, query(link.uri)["what-happened"])
        assertTrue("paste them here" in query(link.uri)["logs"]!!)
    }

    @Test
    fun `B-SEC-129 a very long description copies the whole report`() {
        val report = big(description = "word ".repeat(800))
        val link = report.link()
        assertEquals(ProblemReportClipboard.Whole, link.clipboard)
        assertEquals(report.text, link.clipboardText)
        assertTrue(link.uri.length <= 2048)
        assertTrue("paste it here" in query(link.uri)["what-happened"]!!)
    }
}
