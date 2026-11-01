package io.github.eslamasabry.oc.engine.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnalyticsSchemaTest {
    @Test
    fun `B-ANLY-004 event names are unique snake_case and at most 40 characters`() {
        val names = AnalyticsSchema.events.map { it.name }
        assertEquals(names.size, names.toSet().size, "duplicate event names")
        names.forEach { assertTrue(Regex("^[a-z][a-z0-9_]{0,39}$").matches(it), "bad event name $it") }
    }

    @Test
    fun `B-ANLY-004 every event class is registered and produces exactly its spec`() {
        assertEquals(AnalyticsSchema.events.size, EventSamples.all.size, "a registered spec has no sample, or a sample no spec")
        EventSamples.all.forEach { event ->
            assertSame(EventSamples.specOf(event), event.spec)
            assertSame(event.spec, AnalyticsSchema.spec(event.spec.name))
            assertEquals(ScrubVerdict.Clean, PrivacyScrubber.checkTyped(event), event.spec.name)
            assertEquals(event.spec.props.map { it.key }.toSet(), event.props().keys)
        }
        assertEquals(AnalyticsSchema.events.map { it.name }.toSet(), EventSamples.all.map { it.spec.name }.toSet())
    }

    @Test
    fun `B-ANLY-004 property keys are snake_case and unique within an event`() {
        AnalyticsSchema.events.forEach { spec ->
            val keys = spec.props.map { it.key }
            assertEquals(keys.size, keys.toSet().size, "duplicate key in ${spec.name}")
            keys.forEach { assertTrue(Regex("^[a-z][a-z0-9_]{0,39}$").matches(it), "bad key $it in ${spec.name}") }
        }
    }

    @Test
    fun `B-ANLY-004 every name key and allowed value passes the leak detector`() {
        AnalyticsSchema.events.forEach { spec ->
            assertNull(LeakDetector.detect(spec.name), spec.name)
            spec.props.forEach { prop ->
                assertNull(LeakDetector.detect(prop.key), "${spec.name}.${prop.key}")
                prop.allowed.forEach { assertNull(LeakDetector.detect(it), "${spec.name}.${prop.key}=$it") }
                if (prop.kind != PropKind.Version) assertTrue(prop.allowed.isNotEmpty(), "${spec.name}.${prop.key} has no values")
            }
        }
    }

    @Test
    fun `B-ANLY-004 BASIC holds the owner's minimal set and DETAILED the usage set`() {
        val basic = AnalyticsSchema.tier(AnalyticsTier.Basic).map { it.name }.toSet()
        val detailed = AnalyticsSchema.tier(AnalyticsTier.Detailed).map { it.name }.toSet()
        assertEquals(
            setOf(
                "app_start", "app_updated", "migration_done", "backend_connected", "update_result", "crash_seen",
                "freeze_seen", "error_seen", "daily_use", "server_kinds", "agent_kinds",
            ),
            basic,
        )
        assertEquals(setOf("screen_open", "feature_used", "flow_step", "action_timed", "setting_changed"), detailed)
    }

    @Test
    fun `B-ANLY-015 buckets cover their edges`() {
        assertEquals(CountBucket.Zero, CountBucket.of(-3))
        assertEquals(CountBucket.One, CountBucket.of(1))
        assertEquals(CountBucket.TwoToFive, CountBucket.of(5))
        assertEquals(CountBucket.SixToTwenty, CountBucket.of(6))
        assertEquals(CountBucket.MoreThanTwenty, CountBucket.of(21))
        assertEquals(DurationBucket.Under100Ms, DurationBucket.ofMillis(99))
        assertEquals(DurationBucket.Under300Ms, DurationBucket.ofMillis(100))
        assertEquals(DurationBucket.Under60S, DurationBucket.ofMillis(59_999))
        assertEquals(DurationBucket.Longer, DurationBucket.ofMillis(60_000))
    }
}
