package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import io.github.eslamasabry.oc.engine.analytics.product.productValueIsSafe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The journey events (docs/native/analytics/journeys-2026-11-09.md): the schema is closed, no free text can reach a
 * parameter, and the parameter names fit the GA custom dimensions (registered, plus the five the owner adds).
 */
class JourneyEventsTest {
    @AfterTest fun reset() = AppTelemetry.reset()

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    private fun TestScope.live(vendor: RecordingVendor) {
        val service = TelemetryService(vendor, available = true, scope = this)
        service.start(TelemetryConsent(usage = true, crashes = false), NoStore, MemoryTelemetryReportStore())
        AppTelemetry.install(service)
    }

    private val journeyEvents =
        listOf(
            ProductEvent.SetupStepReached, ProductEvent.LinuxSetupStep, ProductEvent.SetupDone,
            ProductEvent.SetupAbandoned, ProductEvent.SetupResumed, ProductEvent.FirstReply, ProductEvent.SendResult,
            ProductEvent.ChatTurn, ProductEvent.TurnStall, ProductEvent.Approval, ProductEvent.SessionLength,
        )

    /** GA custom dimensions registered on the property today (event scope). */
    private val registered =
        setOf(
            "agent", "battery_exempt", "compat_mode", "exit_kind", "fail_kind", "failed", "feature", "first_reply_range",
            "free_space_range", "janky_pct_range", "kind", "level", "minutes_range", "ms_range", "node_variant", "reason",
            "recovered", "refresh_hz", "result", "screen", "sent", "server_kind", "signal", "stage", "step",
        )

    /** The ones the owner is asked to add. */
    private val proposed = setOf("attempt", "outcome", "input", "tools_range", "follow_up")

    @Test
    fun `B-JOURNEY-001 every journey parameter is a closed set of safe words and fits a GA dimension`() {
        for (event in journeyEvents) {
            val params = productSchema.getValue(event.wire)
            for ((key, values) in params) {
                assertTrue(key in registered || key in proposed, "${event.wire}.$key has no GA dimension")
                assertTrue(values.isNotEmpty(), "${event.wire}.$key is open")
                assertTrue(values.size <= 40, "${event.wire}.$key has too many words to be a closed set")
                values.forEach { assertTrue(productValueIsSafe(it), "${event.wire}.$key=$it") }
            }
        }
    }

    @Test
    fun `B-JOURNEY-002 the fixed words of the brief are all there`() {
        assertEquals(
            setOf("network", "storage", "apt", "permission", "download", "checksum", "killed_by_system", "timeout", "unknown"),
            HealthWords.setupFailKinds,
        )
        assertEquals(setOf("ok", "fail", "skipped", "cancelled"), HealthWords.setupResults)
        assertEquals(
            setOf("done", "stopped_by_user", "error", "connection_lost", "approval_timeout"),
            HealthWords.turnOutcomes,
        )
        assertEquals(setOf("1", "2", "3+"), HealthRanges.attempts.toSet())
    }

    @Test
    fun `B-JOURNEY-003 no free text reaches a parameter, whatever a call site hands over`() = runTest {
        val vendor = RecordingVendor()
        live(vendor)
        val hostile = "my secret project /home/me/app https://example.com sk-123456"
        val t = AppTelemetry.current
        for (event in journeyEvents) {
            val params = productSchema.getValue(event.wire).keys.associateWith { hostile }
            t.track(event, params + mapOf("prompt" to hostile, "path" to hostile, "error" to hostile))
        }
        assertTrue(vendor.events.none { (_, props) -> props.isNotEmpty() }, vendor.events.toString())
        // The helpers reduce free-form inputs to words before they get that far.
        val flow = HealthFlow()
        flow.chatTurn(
            ChatTurnSummary(hostile, hostile, hostile, 1, 1, 1, hostile, hostile, hostile),
        )
        flow.approval(hostile, hostile, 1)
        flow.setupAbandoned(hostile, hostile, 1)
        flow.setupStepReached(hostile, 1)
        flow.linuxSetupStep("download", ok = false, elapsedMs = 1, failKind = hostile)
        for ((_, props) in vendor.events) props.values.forEach { assertTrue(productValueIsSafe(it) && it != hostile, it) }
        assertTrue(vendor.events.all { (_, props) -> props.values.all { it.length <= 40 } })
        // Each remaining value is one of the listed words.
        for ((name, props) in vendor.events) {
            val allowed = productSchema.getValue(name)
            props.forEach { (k, v) -> assertTrue(v in allowed.getValue(k), "$name.$k=$v") }
        }
    }

    @Test
    fun `B-JOURNEY-004 a custom agent name is never sent, only the built-in modes`() {
        assertEquals("build", HealthFlow.chatAgentWord("Build"))
        assertEquals("plan", HealthFlow.chatAgentWord("plan"))
        assertEquals("other", HealthFlow.chatAgentWord("acme-secret-agent"))
        assertEquals("other", HealthFlow.chatAgentWord(null))
    }

    @Test
    fun `B-JOURNEY-005 the duration ranges are the closed lists`() {
        assertEquals("<10s", HealthRanges.turnOf(9_999))
        assertEquals(">10m", HealthRanges.turnOf(601_000))
        assertEquals("0", HealthRanges.toolsOf(0))
        assertEquals(">30", HealthRanges.toolsOf(31))
        assertEquals("under_2", HealthRanges.setupTotalOf(90_000))
        assertEquals("over_40", HealthRanges.setupTotalOf(41 * 60_000L))
        assertEquals("5-15", HealthRanges.awayOf(5 * 60_000L))
        assertEquals(">1440", HealthRanges.awayOf(25 * 3_600_000L))
        assertEquals("3+", HealthRanges.attemptOf(9))
    }

    @Test
    fun `B-JOURNEY-006 a screen view always carries a screen word from the closed set`() = runTest {
        val vendor = RecordingVendor()
        live(vendor)
        val closed = io.github.eslamasabry.oc.engine.analytics.product.ProductScreen.entries.map { it.wire }.toSet()
        for (route in listOf("ChatRoute(session=ses_secret)", "ChatsRoute.Home", "PhoneRoute.ThisPhone", "Never.Seen.Before", "")) {
            AppTelemetry.current.track(
                ProductEvent.ScreenView,
                mapOf("screen" to io.github.eslamasabry.oc.engine.analytics.product.ProductScreen.of(route)),
            )
        }
        AppTelemetry.current.track(ProductEvent.ScreenView, mapOf("screen" to "/chat/ses_secret"))
        val views = vendor.events.filter { it.first == "screen_view" }
        assertEquals(6, views.size)
        assertTrue(views.take(5).all { it.second["screen"] in closed })
        assertTrue(views.last().second["screen"] == null)
    }
}
