package io.github.eslamasabry.oc.engine.analytics.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import io.github.eslamasabry.oc.engine.analytics.health.MemoryTelemetryReportStore
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsent
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryConsentStore
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryService
import io.github.eslamasabry.oc.engine.analytics.health.TelemetryVendor
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The vendor side as the tests read it: events only. */
private class RecordingProductSink : TelemetryVendor {
    val events = mutableListOf<Pair<String, Map<String, String>>>()
    val collection = mutableListOf<TelemetryConsent>()

    override fun setCollection(usage: Boolean, crashes: Boolean) {
        collection += TelemetryConsent(usage, crashes)
    }

    override fun crash(category: String, frames: List<String>, fatal: Boolean) = Unit

    override fun failure(name: String, keys: Map<String, String>) = Unit

    override fun breadcrumb(name: String) = Unit

    override fun event(name: String, props: Map<String, String>) {
        events += name to props
    }

    override fun setKeys(keys: Map<String, String>) = Unit

    override fun setUserProperties(properties: Map<String, String>) = Unit

    override fun perfTrace(name: String, attributes: Map<String, String>, durationMs: Long) = Unit

    override suspend fun problemReport(text: String, collecting: Boolean) = Unit

    override suspend fun appInstanceId(): String? = null
}

private class StepClock(var ms: Long = 1_793_528_100_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
}

class ProductAnalyticsTest {
    private val sink = RecordingProductSink()
    private val scope = TestScope()

    private fun analytics(usage: Boolean = true, available: Boolean = true): TelemetryService =
        TelemetryService(sink, available, scope = scope).also {
            it.start(TelemetryConsent(usage = usage), NoStore, MemoryTelemetryReportStore())
            sink.collection.clear()
        }

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    @Test
    fun `B-180-001 the event list is Flutter 2_1_5's, name for name`() {
        assertEquals(
            listOf(
                "app_open", "setup_step_reached", "setup_step_failed", "setup_done", "first_reply",
                "connect_computer_chosen", "update_offered", "update_installed", "screen_view", "feature_used",
                "session_length",
            ),
            ProductEvent.entries.map { it.wire }.take(11),
        )
        assertEquals(ProductEvent.entries.map { it.wire }.toSet(), productSchema.keys)
        assertEquals(
            setOf(
                "conversations", "chat", "new_chat", "servers", "this_phone", "setup", "privacy", "appearance",
                "integrations", "settings", "files", "git", "review", "team", "usage", "agents", "about", "other",
            ),
            productSchema.getValue("screen_view").getValue("screen"),
        )
        assertEquals(
            setOf("armcap", "threads", "both", "none_worked", "other"),
            productSchema.getValue("setup_step_failed").getValue("node_variant"),
        )
        // Every listed value passes the last check, so nothing in the list is ever dropped by it.
        productSchema.values.flatMap { it.values }.flatten().forEach { assertTrue(productValueIsSafe(it), it) }
    }

    @Test
    fun `B-180-002 nothing reaches the vendor while the switch is off or the build has no vendor`() {
        analytics(usage = false).track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
        analytics(available = false).track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
        assertTrue(sink.events.isEmpty())

        analytics().track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
        assertEquals(listOf("app_open" to mapOf("kind" to "cold")), sink.events)
    }

    @Test
    fun `B-180-003 an unlisted key or value, free text, a path, a host or an address never leaves`() {
        analytics()
            .track(
                ProductEvent.SetupStepFailed,
                mapOf(
                    "step" to ProductSetupStep.Node,
                    "kind" to "segfault at /data/user/0/x",
                    "compat_mode" to true,
                    "node_variant" to ProductNodeVariant.NoneWorked,
                    "host" to "example.com",
                    "message" to "please fix my code",
                    "email" to "me@example.com",
                ),
            )
        assertEquals(
            listOf(
                "setup_step_failed" to mapOf("step" to "node", "compat_mode" to "true", "node_variant" to "none_worked")
            ),
            sink.events,
        )
        assertTrue(!productValueIsSafe("a.b") && !productValueIsSafe("a/b") && !productValueIsSafe("a@b"))
        // Flutter's last check: letters of either case (SIGILL), digits and `_ < > + -` only, 40 at most.
        assertTrue(productValueIsSafe("SIGILL") && productValueIsSafe("<500") && productValueIsSafe("4+"))
        assertTrue(!productValueIsSafe("a b") && !productValueIsSafe("x".repeat(41)) && !productValueIsSafe(""))
    }

    @Test
    fun `B-180-004 screen views carry the page kind only, never a route argument`() {
        val a = analytics()
        for (route in listOf("ChatRoute(profile=p_secret, session=ses_123)", "ServersRoute.Edit", "PhoneRoute.ThisPhone", "SomethingNew"))
            a.track(ProductEvent.ScreenView, mapOf("screen" to ProductScreen.of(route)))
        assertEquals(
            listOf("chat", "servers", "this_phone", "other"),
            sink.events.map { it.second.getValue("screen") },
        )
        assertTrue(sink.events.all { it.first == "screen_view" })
    }

    @Test
    fun `B-180-005 the vendor follows the switch, and adopting Flutter's answer at start applies it once`() = runTest {
        val a = TelemetryService(sink, available = true, scope = this)
        a.start(TelemetryConsent(usage = true), NoStore, MemoryTelemetryReportStore())
        a.setConsent(usage = false, crashes = false)
        a.setConsent(usage = true, crashes = false)
        assertEquals(
            listOf(TelemetryConsent(usage = true), TelemetryConsent(), TelemetryConsent(usage = true)),
            sink.collection,
        )
        sink.collection.clear()
        TelemetryService(sink, available = false, scope = this).setConsent(usage = true, crashes = true)
        assertEquals(emptyList(), sink.collection, "no vendor, no calls")
    }

    @Test
    fun `B-180-006 cold and warm opens, session length and an installed update, as Flutter counts them`() {
        val clock = StepClock()
        val lifecycle = ProductLifecycle(analytics(), clock)
        // Breadcrumbs and update_result are crash-side or health events: this test reads the product events.
        assertEquals("2.2.0+80", lifecycle.coldStart("2.2.0+80", previousVersion = "2.1.5+70"))
        clock.ms += 7.minutes.inWholeMilliseconds
        lifecycle.onBackground()
        lifecycle.onBackground() // a second pause is not a second session
        lifecycle.onForeground()
        lifecycle.onForeground()
        assertEquals(
            listOf(
                "app_open" to mapOf("kind" to "cold"),
                "update_installed" to emptyMap(),
                "update_result" to mapOf("kind" to "in_app", "result" to "installed"),
                "session_length" to mapOf("minutes" to "5_to_15"),
                "app_open" to mapOf("kind" to "warm"),
            ),
            sink.events,
        )
        sink.events.clear()
        assertNull(lifecycle.coldStart("2.2.0+80", previousVersion = "2.2.0+80"))
        assertEquals("2.2.0+80", lifecycle.coldStart("2.2.0+80", previousVersion = null))
        assertEquals(listOf("app_open", "app_open"), sink.events.map { it.first }, "a first start is no update")
    }

    @Test
    fun `B-180-007 buckets and failure kinds match Flutter`() {
        assertEquals("under_2", ProductBuckets.minutesOf(1.minutes))
        assertEquals("over_40", ProductBuckets.minutesOf(41.minutes))
        assertEquals("unknown", ProductBuckets.secondsOf(null))
        assertEquals("5_to_15", ProductBuckets.secondsOf(6.seconds))
        assertEquals("over_60", ProductBuckets.sessionMinutesOf(61.minutes))
        assertEquals(ProductFailureKind.Crash, ProductFailureKind.of(exit = 139))
        assertEquals(ProductFailureKind.Timeout, ProductFailureKind.of(error = "timed out"))
        assertEquals(ProductFailureKind.Space, ProductFailureKind.of(error = "ENOSPC"))
        assertEquals(ProductFailureKind.Network, ProductFailureKind.of(error = "Could not resolve host"))
        assertEquals(ProductFailureKind.Other, ProductFailureKind.of(exit = 1))
    }

    @Test
    fun `B-180-008 the question is asked once, never without a vendor, never when already on`() {
        assertEquals(ConsentQuestion.Show, ConsentQuestion.decide(asked = false, available = true, consented = false))
        assertEquals(ConsentQuestion.Skip, ConsentQuestion.decide(asked = true, available = true, consented = false))
        assertEquals(ConsentQuestion.Skip, ConsentQuestion.decide(asked = false, available = false, consented = false))
        assertEquals(ConsentQuestion.MarkOnly, ConsentQuestion.decide(asked = false, available = true, consented = true))
    }
}
