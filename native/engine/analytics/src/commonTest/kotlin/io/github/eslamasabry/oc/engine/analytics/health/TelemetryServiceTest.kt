package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.cleanProductProps
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import io.github.eslamasabry.oc.engine.analytics.product.productValueIsSafe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * B-180/B-190: the twin of Flutter 2.1.5's `health_telemetry_test.dart` and `telemetry_test.dart` — two consents, the
 * fixed words and ranges, nothing sent with a consent off, the problem report and its code.
 */
class TelemetryServiceTest {
    private val newEvents =
        listOf(
            ProductEvent.ConnAttempt, ProductEvent.ConnDrop, ProductEvent.SendResult, ProductEvent.TurnStall,
            ProductEvent.QueueFlush, ProductEvent.LinuxSetupStep, ProductEvent.AgentStart, ProductEvent.BgServiceEnd,
            ProductEvent.UpdateResult, ProductEvent.AppStart, ProductEvent.Jank, ProductEvent.MemoryPressure,
        )

    private fun TestScope.service(
        vendor: RecordingVendor = RecordingVendor(),
        usage: Boolean = false,
        crashes: Boolean = false,
        available: Boolean = true,
        reports: TelemetryReportStore = MemoryTelemetryReportStore(),
    ): TelemetryService =
        TelemetryService(vendor, available, scope = this).also {
            it.start(TelemetryConsent(usage, crashes), store = MemoryConsentStore(), reports = reports)
        }

    private class MemoryConsentStore : TelemetryConsentStore {
        val written = mutableListOf<TelemetryConsent>()

        override suspend fun write(consent: TelemetryConsent): Boolean {
            written += consent
            return true
        }
    }

    // ---- only listed words and ranges are sent ----

    @Test
    fun `B-190-001 the event names are Flutter 2_1_5's, and every new event has fixed values`() {
        assertEquals(
            listOf(
                "app_open", "setup_step_reached", "setup_step_failed", "setup_done", "first_reply",
                "connect_computer_chosen", "update_offered", "update_installed", "screen_view", "feature_used",
                "session_length", "conn_attempt", "conn_drop", "send_result", "turn_stall", "queue_flush",
                "linux_setup_step", "agent_start", "bg_service_end", "update_result", "app_start", "jank",
                "memory_pressure",
            ),
            ProductEvent.entries.map { it.wire },
        )
        assertEquals(ProductEvent.entries.map { it.wire }.toSet(), productSchema.keys)
        for (event in newEvents) {
            val schema = productSchema.getValue(event.wire)
            assertTrue(schema.isNotEmpty(), event.wire)
            schema.values.forEach { assertTrue(it.isNotEmpty(), event.wire) }
        }
        // Every listed value passes the last check, so nothing listed is ever dropped by it.
        productSchema.values.flatMap { it.values }.flatten().forEach { assertTrue(productValueIsSafe(it), it) }
    }

    @Test
    fun `B-190-002 unknown values, extra keys and free text are dropped for every new event`() {
        val hostile =
            listOf(
                "a free text sentence", "/data/user/0/app/files", "vps.example.net", "100.126.15.6",
                "https://host/x?token=1", "x".repeat(60), "", "<>", "500-2001", 42, null,
            )
        for (event in newEvents) {
            val keys = productSchema.getValue(event.wire).keys + listOf("message", "path", "host")
            for (key in keys) for (value in hostile) {
                assertEquals(emptyMap(), cleanProductProps(event, mapOf(key to value)), "${event.wire}.$key=$value")
            }
        }
    }

    @Test
    fun `B-190-003 listed values pass, and only for their own event`() {
        assertEquals(
            mapOf("server_kind" to "phone", "result" to "fail", "fail_kind" to "server_5xx", "ms_range" to ">10000"),
            cleanProductProps(
                ProductEvent.ConnAttempt,
                mapOf(
                    "server_kind" to "phone",
                    "result" to "fail",
                    "fail_kind" to "server_5xx",
                    "ms_range" to ">10000",
                    "error_message" to "connect failed to 10.0.0.2",
                ),
            ),
        )
        assertEquals(emptyMap(), cleanProductProps(ProductEvent.ConnAttempt, mapOf("fail_kind" to "too_large")))
        assertEquals(
            mapOf("fail_kind" to "too_large"),
            cleanProductProps(ProductEvent.SendResult, mapOf("fail_kind" to "too_large")),
        )
        assertEquals(mapOf("signal" to "SIGILL"), cleanProductProps(ProductEvent.LinuxSetupStep, mapOf("signal" to "SIGILL")))
    }

    @Test
    fun `B-190-004 every range function answers only a listed word`() {
        val ms = listOf(-5L, 0, 1, 499, 500, 1999, 2000, 9999, 10000, 59_999, 60_000, 299_999, 300_000, 3_600_000, 99_999_999)
        for (m in ms) {
            assertTrue(HealthRanges.connMsOf(m) in HealthRanges.connMs)
            assertTrue(HealthRanges.firstReplyOf(m) in HealthRanges.firstReply)
            assertTrue(HealthRanges.stallMinutesOf(m) in HealthRanges.stallMinutes)
            assertTrue(HealthRanges.setupMsOf(m) in HealthRanges.setupMs)
            assertTrue(HealthRanges.appStartMsOf(m) in HealthRanges.appStartMs)
        }
        assertEquals("none", HealthRanges.firstReplyOf(null))
        for (n in listOf(-1, 0, 1, 2, 5, 6, 20, 21, 1000)) {
            assertTrue(HealthRanges.countOf(n) in HealthRanges.counts)
            assertTrue(HealthRanges.connectionsOf(n) in HealthRanges.connections)
        }
        for (x in listOf(-1.0, 0.0, 0.5, 2.9, 3.0, 5.9, 12.0, 400.0)) {
            assertTrue(HealthRanges.ramOf(x) in HealthRanges.ram)
            assertTrue(HealthRanges.storageOf(x) in HealthRanges.storage)
            assertTrue(HealthRanges.jankPctOf(x) in HealthRanges.jankPct)
        }
        assertEquals(listOf("60", "90", "120", "other"), listOf(59.9, 90.0, 120.2, 75.0).map(HealthRanges::refreshHzOf))
        assertEquals("<500", HealthRanges.connMsOf(499))
        assertEquals("2000-10000", HealthRanges.connMsOf(2000))
        assertEquals("1-5", HealthRanges.stallMinutesOf(60_000))
        assertEquals("4+", HealthRanges.connectionsOf(4))
    }

    @Test
    fun `B-190-005 custom keys and user properties drop unknown values`() = runTest {
        val vendor = RecordingVendor()
        val s = service(vendor, usage = true, crashes = true)
        s.setHealthKeys(
            mapOf(
                "build_kind" to "store",
                "server_kind" to "laptop.example.com",
                "app_version" to "3.0.1",
                "oc_server_version" to "2.1.1",
                "connections" to "7",
                "exit_reason" to "anr",
                "free_text" to "anything",
            )
        )
        assertEquals(
            listOf(mapOf("build_kind" to "store", "app_version" to "3.0.1", "exit_reason" to "anr")),
            vendor.keys,
        )
        s.setUserProperties(
            mapOf(
                "ram_range" to "6-8",
                "abi" to "mips",
                "webview_major" to 133,
                "notif_allowed" to true,
                "oc_server_version" to "2.1",
                "server_kind" to "both",
                "model" to "Pixel 9",
            )
        )
        assertEquals(
            listOf(
                mapOf(
                    "ram_range" to "6-8",
                    "webview_major" to "133",
                    "notif_allowed" to "yes",
                    "oc_server_version" to "2.1",
                    "server_kind" to "both",
                )
            ),
            vendor.properties,
        )
    }

    @Test
    fun `B-190-006 breadcrumbs are only the listed area-action words`() = runTest {
        val vendor = RecordingVendor()
        val s = service(vendor, crashes = true)
        for (crumb in
            listOf(
                "app.start", "nav.open:chat", "linux.step:node", "agent.start:codex", "settings.change:privacy",
                "nav.open:my-secret-project", "conn.connect to 10.0.0.2", "chat.send hello", "linux.step:", ":chat",
                "update.install",
            )) {
            s.breadcrumb(crumb)
        }
        assertEquals(
            listOf("app.start", "nav.open:chat", "linux.step:node", "agent.start:codex", "settings.change:privacy", "update.install"),
            vendor.crumbs,
        )
    }

    @Test
    fun `B-190-007 an error is sent as a kind with code frames, never its message`() = runTest {
        val vendor = RecordingVendor()
        val s = service(vendor, crashes = true)
        s.reportCrash(
            "java.lang.IllegalStateException",
            listOf("a.B.run:12", "boom: password is hunter2", "/data/user/0/app/x.kt:3", "c.D.\$lambda:7"),
        )
        s.reportCrash("Oops: token=abc", emptyList())
        assertEquals(Triple("java.lang.IllegalStateException", listOf("a.B.run:12", "c.D.\$lambda:7"), true), vendor.crashes[0])
        assertEquals("java.lang.Throwable", vendor.crashes[1].first)
        s.reportFailure(
            "prompt_not_accepted",
            mapOf(
                "step" to "after_send",
                "elapsed_ms" to "1234",
                "detail" to "sk-abcdef1234567890abcdef",
                "where" to "https://example.com/a?token=1",
                "host" to "laptop.tail1234.ts.net",
                "file" to "/home/me/secret.txt",
                "text" to "please fix my code",
                "Bad Key" to "x",
                "exit_reason" to "made_up",
            ),
        )
        s.recordFailure("Has Spaces And Text: 10.0.0.1")
        assertEquals(listOf("prompt_not_accepted" to mapOf("step" to "after_send", "elapsed_ms" to "1234")), vendor.failures)
        assertEquals("invalid_state", TelemetryService.cleanName("Invalid state"))
        assertNull(TelemetryService.cleanName("Has Spaces And Text: 10.0.0.1"))
    }

    // ---- two consents ----

    @Test
    fun `B-180-010 both off (the default) - nothing reaches the vendor`() = runTest {
        val vendor = RecordingVendor()
        val s = service(vendor)
        sendEverything(s)
        assertTrue(vendor.silent)
        assertEquals(listOf(TelemetryConsent()), vendor.collection, "applied once at start, both off")
    }

    @Test
    fun `B-180-011 usage only - events, properties and traces, no crash data`() = runTest {
        val vendor = RecordingVendor()
        sendEverything(service(vendor, usage = true))
        assertTrue(vendor.events.isNotEmpty() && vendor.properties.isNotEmpty() && vendor.traces.isNotEmpty())
        assertTrue(vendor.crashes.isEmpty() && vendor.failures.isEmpty() && vendor.crumbs.isEmpty() && vendor.keys.isEmpty())
    }

    @Test
    fun `B-180-012 crashes only - crash data, no events, properties or traces`() = runTest {
        val vendor = RecordingVendor()
        sendEverything(service(vendor, crashes = true))
        assertTrue(vendor.crashes.isNotEmpty() && vendor.failures.isNotEmpty() && vendor.crumbs.isNotEmpty())
        assertTrue(vendor.keys.isNotEmpty())
        assertTrue(vendor.events.isEmpty() && vendor.properties.isEmpty() && vendor.traces.isEmpty())
    }

    @Test
    fun `B-180-013 each switch applies at once, is stored, and off stops collection`() = runTest {
        val vendor = RecordingVendor()
        val store = MemoryConsentStore()
        val s = TelemetryService(vendor, available = true, scope = this)
        s.start(TelemetryConsent(), store, MemoryTelemetryReportStore())
        assertTrue(s.setConsent(usage = true, crashes = false))
        s.track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
        assertTrue(s.setConsent(usage = false, crashes = false))
        s.track(ProductEvent.AppOpen, mapOf("kind" to "warm"))
        s.setReportingConsent(true)
        runCurrent()
        assertEquals(
            listOf(TelemetryConsent(), TelemetryConsent(true, false), TelemetryConsent(), TelemetryConsent(false, true)),
            vendor.collection,
        )
        assertEquals(listOf(TelemetryConsent(true, false), TelemetryConsent(), TelemetryConsent(false, true)), store.written)
        assertEquals(listOf("app_open" to mapOf("kind" to "cold")), vendor.events, "nothing after off")
        assertTrue(s.reportingConsent.value)
    }

    @Test
    fun `B-180-014 a build without the vendor never sends, with both on, and has no report code`() = runTest {
        val vendor = RecordingVendor()
        val s = service(vendor, usage = true, crashes = true, available = false)
        sendEverything(s)
        assertTrue(vendor.silent)
        assertTrue(vendor.collection.isEmpty(), "no vendor, no calls")
        assertNull(s.sendProblemReport("x"))
        assertNull(s.reportCode())
    }

    @Test
    fun `B-180-015 breadcrumbs stay in the app's own ring without crash consent`() = runTest {
        val vendor = RecordingVendor()
        val s = service(vendor)
        s.breadcrumb("conn.connect")
        assertTrue(vendor.crumbs.isEmpty())
        assertTrue(s.ring.lines().single().endsWith("crumb conn.connect"))
    }

    @Test
    fun `B-180-016 the early answer gates sending before the store opens, without touching the vendor`() = runTest {
        val vendor = RecordingVendor()
        val s = TelemetryService(vendor, available = true, early = TelemetryConsent(crashes = true), scope = this)
        s.reportCrash("java.lang.IllegalStateException", listOf("a.B.c:1"))
        assertEquals(1, vendor.crashes.size)
        assertTrue(vendor.collection.isEmpty(), "Firebase keeps its own last setting until the stored answers are adopted")
    }

    // ---- the problem report ----

    @Test
    fun `B-190-008 sending uploads the previewed text once and returns the code`() = runTest {
        val vendor = RecordingVendor()
        val s = service(vendor)
        assertEquals("abcdef01-1", s.sendProblemReport("the previewed text"))
        assertEquals("abcdef01-2", s.sendProblemReport("another"))
        assertEquals(listOf("the previewed text" to false, "another" to false), vendor.reports)
        assertEquals("abcdef01-2", s.reportCode())
        assertTrue(vendor.events.isEmpty() && vendor.crashes.isEmpty(), "nothing else with the switches off")
        assertEquals("abcdef01-2", s.healthKeys["report_code"])
    }

    @Test
    fun `B-190-009 without an install id from the vendor a local code is kept`() = runTest {
        val reports = MemoryTelemetryReportStore()
        val s = service(RecordingVendor(instanceId = null), reports = reports)
        val code = s.sendProblemReport("x")!!
        assertTrue(Regex("^[0-9a-f]{8}-1$").matches(code), code)
        assertTrue(s.reportCode()!!.startsWith(code.substring(0, 8)))
        assertEquals(code.substring(0, 8), reports.readLocalId())
    }

    private fun sendEverything(s: TelemetryService) {
        s.reportCrash("java.lang.IllegalStateException", listOf("a.B.c:1"))
        s.recordFailure("setup_step_failed", mapOf("step" to "node"))
        s.breadcrumb("conn.connect")
        s.setHealthKeys(mapOf("build_kind" to "sideload"))
        s.setUserProperties(mapOf("abi" to "arm64"))
        s.track(ProductEvent.ConnAttempt, mapOf("server_kind" to "phone", "result" to "ok"))
        s.performanceTrace(PerfSchema.CONN_CONNECT, 120, mapOf("server_kind" to "phone", "result" to "ok"))
    }

    @Test
    fun `B-190-010 the log ring keeps the newest lines, cut short, with the UTC time`() {
        val ring = AppLogRing(capacity = 3)
        for (i in 0 until 5) ring.add("line $i", kotlin.time.Instant.fromEpochSeconds(3_723))
        ring.add("x".repeat(400), kotlin.time.Instant.fromEpochSeconds(0))
        assertEquals(listOf("01:02:03 line 3", "01:02:03 line 4"), ring.lines().take(2))
        assertEquals(AppLogRing.MAX_LINE_LENGTH + "00:00:00 ".length + 1, ring.lines().last().length)
        assertFalse(ring.lines().any { "line 0" in it })
    }
}
