package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * B-190 "Send a problem report" (section E): the redaction the person's preview and the sent text go through, ported
 * from Flutter 2.1.5's `health_telemetry_test.dart` group "send a problem report", and the R1 report's contents.
 */
class ReportRedactionTest {
    private val secret = "sk-ant-REDACTME" + "x".repeat(24)

    @Test
    fun `B-190-030 redaction removes a key, a URL, a host, an address, a path and an e-mail`() {
        val redaction = ReportRedaction(Redactor())
        val out =
            redaction.redact(
                """
                12:00:01 event conn_attempt result=fail
                12:00:02 error with key $secret in config
                12:00:03 GET https://admin:hunter22@my-box.tail1234.ts.net:4096/session/ses_1?token=abc123 failed
                12:00:04 server my-box.tail1234.ts.net refused 192.168.1.20:4096
                12:00:05 wrote /data/user/0/io.github.eslamasabry.opencode_mobile/files/home/secret-project/x.dart
                12:00:06 at #0 Foo.bar (package:opencode_mobile/state/connection.dart:120:7)
                12:00:07 contact me@example.com
                12:00:08 loaded C:\Users\someone\proj\file.txt and ~/work/private
                12:00:09 at ConnectionRegistry.open(ConnectionRegistry.kt:171)
                12:00:10 ipv6 fd7a:115c:a1e0::1 and token ${"a1B2".repeat(10)}
                """
                    .trimIndent()
            )
        for (gone in
            listOf(
                "REDACTME", "hunter22", "my-box", "tail1234", "192.168.1.20", "/data/user/0", "secret-project",
                "me@example.com", "someone", "private", "abc123", "fd7a:115c", "a1B2a1B2",
            )) {
            assertFalse(gone in out, "$gone survived:\n$out")
        }
        // What a developer reads a crash by stays.
        assertTrue("package:opencode_mobile/state/connection.dart:120:7" in out)
        assertTrue("ConnectionRegistry.kt:171" in out)
        assertTrue("event conn_attempt result=fail" in out)
        assertTrue("12:00:10 ipv6" in out, "a log time is not an address")
        assertEquals(out, redaction.redact(out), "idempotent")
    }

    @Test
    fun `B-190-031 redaction also masks secrets registered at run time`() {
        val redactor = Redactor().apply { registerSecret("correct-horse-battery") }
        assertFalse("correct-horse" in ReportRedaction(redactor).redact("password was correct-horse-battery today"))
    }

    @Test
    fun `B-190-032 the report holds the words, the last unexpected close, app, phone, connection and problem kinds only`() =
        runTest {
            val vendor = RecordingVendor()
            val service = TelemetryService(vendor, available = true, scope = this)
            service.start(TelemetryConsent(), NoStore, MemoryTelemetryReportStore())
            for (i in 0 until 50) service.ring.add("line $i opened http://10.0.0.5:4096/x and /home/me/secret")
            ProblemTally.note(service.ring, "timeout")
            ProblemTally.note(service.ring, "auth")
            ProblemTally.note(service.ring, "timeout")
            ProblemTally.note(service.ring, "not-a-kind")
            val made = Instant.parse("2026-11-08T12:00:00Z")
            val platform = Platform(listOf(HealthExit(4, made.toEpochMilliseconds() - 60_000)))
            val collector =
                ProblemReportCollector(
                    service,
                    ReportRedaction(Redactor()),
                    platform = platform,
                    connection = { ReportConnection("OpenCode", "2.1.1 on my-box.tail1234.ts.net") },
                    clock = object : kotlin.time.Clock {
                        override fun now(): Instant = made
                    },
                )
            val report = collector.build().withWords(collector.words("It closed at https://my-box.ts.net:4096 with $secret"))
            assertEquals(ReportClose("crash", made.toEpochMilliseconds() - 60_000), report.lastClose)
            assertEquals(ReportConnection("OpenCode", "2.1.1"), report.connection)
            assertEquals(listOf(ReportProblem("timeout", 2), ReportProblem("auth", 1)), report.problems)
            val text = report.text
            assertTrue("Last time the app closed: crash, 2026-11-08 11:59:00 UTC" in text, text)
            assertTrue("App: OpenCode Mobile 3.0.0 (57)" in text, text)
            assertTrue("Phone: Android 15" in text, text)
            assertTrue("Connection: OpenCode 2.1.1 · address taken out" in text, text)
            assertTrue("Recent problems: timeout ×2, auth ×1" in text, text)
            assertTrue("It closed at" in text)
            for (gone in listOf("my-box", "tail1234", "REDACTME", "10.0.0.5", "/home/me", "line 1", "not-a-kind")) {
                assertFalse(gone in text, "$gone survived:\n$text")
            }
            assertTrue(vendor.reports.isEmpty(), "building the report sends nothing")
        }

    @Test
    fun `B-190-033 a normal end, an update or an old crash is not the last unexpected close`() = runTest {
        val service = TelemetryService(RecordingVendor(), available = true, scope = this)
        val made = Instant.parse("2026-11-08T12:00:00Z")
        val clock = object : kotlin.time.Clock {
            override fun now(): Instant = made
        }
        for (exit in
            listOf(
                HealthExit(1, made.toEpochMilliseconds() - 1_000),
                HealthExit(16, made.toEpochMilliseconds() - 1_000),
                HealthExit(4, made.toEpochMilliseconds() - ProblemReportCollector.CLOSE_MAX_AGE_MS - 1),
            )) {
            val report = ProblemReportCollector(service, ReportRedaction(Redactor()), platform = Platform(listOf(exit)), clock = clock).build()
            assertEquals(null, report.lastClose, "$exit")
            assertFalse("Last time the app closed" in report.text)
        }
        val none = ProblemReportCollector(service, ReportRedaction(Redactor()), clock = clock).build()
        assertTrue("Connection: not connected" in none.text)
        assertTrue("Recent problems: none" in none.text)
        assertTrue("(not described)" in none.text)
    }

    private class Platform(private val exitList: List<HealthExit>) : HealthPlatform {
        override suspend fun deviceContext(): HealthDeviceContext? = null

        override suspend fun exits(limit: Int): List<HealthExit> = exitList.take(limit)

        override fun coldStartMs(): Long? = null

        override val appVersion: String = "3.0.0"
        override val declaredBuildKind: String? = null
        override val appBuild: String = "57"
        override val osRelease: String = "15"
    }

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }
}
