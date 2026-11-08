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
 * from Flutter 2.1.5's `health_telemetry_test.dart` group "send a problem report", and the collector's contents.
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
    fun `B-190-032 the report holds the last 500 log lines, keys and exits, already redacted`() = runTest {
        val vendor = RecordingVendor()
        val service = TelemetryService(vendor, available = true, scope = this)
        service.start(TelemetryConsent(crashes = true), NoStore, MemoryTelemetryReportStore())
        service.setHealthKeys(mapOf("build_kind" to "store"))
        for (i in 0 until 700) service.ring.add("line $i")
        service.ring.add("opened http://10.0.0.5:4096/x and /home/me/secret")
        val platform =
            object : HealthPlatform {
                override suspend fun deviceContext(): HealthDeviceContext? = null

                override suspend fun exits(limit: Int): List<HealthExit> = listOf(HealthExit(4, 90_000))

                override fun coldStartMs(): Long? = null

                override val appVersion: String = "3.0.0"
                override val declaredBuildKind: String? = null
            }
        val text =
            ProblemReportCollector(
                    service,
                    ReportRedaction(Redactor()),
                    platform = platform,
                    clock = object : kotlin.time.Clock {
                        override fun now(): Instant = Instant.parse("2026-11-08T12:00:00Z")
                    },
                )
                .build()
        assertTrue("Made: 2026-11-08 12:00:00 UTC" in text, text.take(200))
        assertTrue("build_kind=store" in text)
        assertTrue("1970-01-01 00:01:30 UTC crash" in text)
        assertTrue("Log (last 500 lines)" in text)
        assertTrue("line 699" in text)
        assertFalse("line 100\n" in text)
        assertFalse("10.0.0.5" in text)
        assertFalse("/home/me" in text)
        assertTrue(text.lines().count { it.startsWith("  ") } < 520)
        assertTrue(vendor.reports.isEmpty(), "building the preview sends nothing")
    }

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }
}
