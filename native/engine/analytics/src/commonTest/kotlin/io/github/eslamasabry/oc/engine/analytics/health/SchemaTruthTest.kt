package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import io.github.eslamasabry.oc.engine.analytics.product.productValueIsSafe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Analytics truth (intake 2026-11-16): the owner's limits hold. Analytics stays opt-in for every declared event; new
 * words go into existing dimensions only, each a fixed and safe word; the set of dimension names does not grow.
 */
class SchemaTruthTest {
    @AfterTest fun reset() = AppTelemetry.reset()

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    /** One valid value per listed property: the event as a call site would send it. */
    private fun sample(event: ProductEvent): Map<String, Any?> =
        productSchema.getValue(event.wire).mapValues { (_, values) -> values.first() }

    @Test
    fun `B-JOURNEY-090 with usage off not one declared event reaches the vendor`() = runTest {
        val vendor = RecordingVendor()
        val service = TelemetryService(vendor, available = true, scope = this)
        service.start(TelemetryConsent(usage = false, crashes = true), NoStore, MemoryTelemetryReportStore())
        for (event in ProductEvent.entries) service.track(event, sample(event))
        assertEquals(emptyList(), vendor.events, "usage off: nothing is counted, whatever the event")
        // The same calls with usage on do arrive, so the silence above is the consent gate, not a broken call.
        val on = RecordingVendor()
        val live = TelemetryService(on, available = true, scope = this)
        live.start(TelemetryConsent(usage = true, crashes = false), NoStore, MemoryTelemetryReportStore())
        for (event in ProductEvent.entries) live.track(event, sample(event))
        assertEquals(ProductEvent.entries.map { it.wire }, on.events.map { it.first })
    }

    @Test
    fun `B-JOURNEY-091 the new words are fixed and safe, and no dimension was added`() {
        val newWords = listOf(Triple("chat_turn", "outcome", "no_output"), Triple("approval", "result", "auto"), Triple("approval", "result", "stopped"))
        for ((event, key, word) in newWords) {
            assertTrue(word in productSchema.getValue(event).getValue(key), "$event.$key lists $word")
            assertTrue(productValueIsSafe(word), word)
        }
        // Every event property is a GA custom dimension; 36 are registered; the four above are asked of the owner (activity_range, surface, interaction, after_consent).
        val dimensions = productSchema.values.flatMap { it.keys }.toSet()
        assertEquals(
            setOf(
                "after_range", "agent", "attempt", "battery_exempt", "compat_mode", "count", "exit_kind", "fail_kind",
                "failed", "feature", "first_reply_range", "follow_up", "free_space_range", "frozen_range", "input",
                "janky_pct_range", "kind", "late_pct_range", "level", "minutes_range", "ms_range", "outcome", "reason",
                "recovered", "refresh_hz", "result", "screen", "seen_hz", "sent", "server_kind", "signal", "stage",
                "starter", "step", "tools_range", "turns_range",
                // 2026-11-16 decisions: three new fields, and the one that marks a first reply measured after consent.
                "activity_range", "surface", "interaction", "after_consent",
            ),
            dimensions,
        )
        assertEquals(40, dimensions.size)
        for (values in productSchema.values.flatMap { it.values }) for (v in values) assertTrue(productValueIsSafe(v), v)
    }
}
