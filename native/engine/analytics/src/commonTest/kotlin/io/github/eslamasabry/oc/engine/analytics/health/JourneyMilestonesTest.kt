package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.ProductUseFor
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The install-to-first-reply funnel as milestones, the session depth on `session_length`, the finer frame health and
 * the use-case answer (docs/native/analytics/next-focus-2026-11-10.md). Each test names the behaviour that would
 * disappear without the change.
 */
class JourneyMilestonesTest {
    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    private class Rig(val vendor: RecordingVendor, val service: TelemetryService, val clock: LongArray, val memory: MemoryJourneyMemory)

    private fun TestScope.rig(
        usage: Boolean = true,
        memory: MemoryJourneyMemory = MemoryJourneyMemory(),
        at: Long = 1_000_000L,
    ): Rig {
        val vendor = RecordingVendor()
        val clock = longArrayOf(at)
        val service = TelemetryService(vendor, available = true, scope = this, wallClockMs = { clock[0] })
        service.start(TelemetryConsent(usage = usage), NoStore, MemoryTelemetryReportStore())
        service.attachJourney(memory)
        return Rig(vendor, service, clock, memory)
    }

    private fun Rig.milestones(): List<Pair<String, String>> =
        vendor.events.filter { it.first == "journey_milestone" }.map { it.second.getValue("step") to it.second.getValue("after_range") }

    /** How the first message ended: the milestone events of that step, each without its `after_range`. */
    private fun Rig.endings(): List<Map<String, String>> =
        vendor.events
            .filter { it.first == "journey_milestone" && it.second["step"] == "first_prompt_ended" }
            .map { it.second - "after_range" }

    @Test
    fun `B-NEXT-001 the funnel is sent once per step, in the order it happens, with how long after the start`() = runTest {
        val r = rig()
        val t = r.service
        // The app being open now is the first milestone.
        assertEquals(listOf("opened" to "under_5m"), r.milestones())

        r.clock[0] += 2 * 60_000
        t.track(ProductEvent.ScreenView, mapOf("screen" to "setup"))
        r.clock[0] += 60_000
        t.track(ProductEvent.SetupStepReached, mapOf("step" to "linux", "attempt" to "1"))
        r.clock[0] += 12 * 60_000
        t.track(ProductEvent.SetupDone, mapOf("minutes_range" to "10_to_20", "server_kind" to "phone"))
        t.track(ProductEvent.ConnAttempt, mapOf("server_kind" to "phone", "result" to "ok", "ms_range" to "<500"))
        t.track(ProductEvent.ScreenView, mapOf("screen" to "chat"))
        r.clock[0] += 60_000
        t.track(ProductEvent.SendResult, mapOf("result" to "ok", "first_reply_range" to "2-10s"))
        t.track(ProductEvent.FirstReply, mapOf("server_kind" to "phone", "first_reply_range" to "2-10s"))

        assertEquals(
            listOf(
                "opened" to "under_5m", "setup_opened" to "under_5m", "setup_started" to "under_5m",
                "setup_done" to "5_to_30m", "server_ready" to "5_to_30m", "chat_opened" to "5_to_30m",
                "first_prompt" to "5_to_30m", "first_reply" to "5_to_30m",
            ),
            r.milestones(),
        )
        // The same events again, or later days, send nothing more.
        r.clock[0] += 3 * 24 * 3_600_000L
        t.track(ProductEvent.AppOpen, mapOf("kind" to "warm"))
        t.track(ProductEvent.FirstReply, mapOf("server_kind" to "phone", "first_reply_range" to "<2s"))
        t.track(ProductEvent.SendResult, mapOf("result" to "ok"))
        assertEquals(8, r.milestones().size)
    }

    @Test
    fun `B-NEXT-002 a failed connection is not a ready server, and a page that is not setup or chat is no milestone`() = runTest {
        val r = rig()
        r.service.track(ProductEvent.ConnAttempt, mapOf("server_kind" to "phone", "result" to "fail", "fail_kind" to "refused"))
        r.service.track(ProductEvent.ScreenView, mapOf("screen" to "settings"))
        r.service.track(ProductEvent.FeatureUsed, mapOf("feature" to "voice"))
        assertEquals(listOf("opened"), r.milestones().map { it.first })
        // A send that failed still counts as trying: the person reached the message box.
        r.service.track(ProductEvent.SendResult, mapOf("result" to "fail", "fail_kind" to "refused"))
        assertEquals(listOf("opened", "first_prompt", "first_prompt_ended"), r.milestones().map { it.first })
    }

    @Test
    fun `B-NEXT-003 the late steps of a long wait read as a long time, in closed words`() {
        val words = listOf(0L, 299_999, 300_000, 1_800_000, 7_200_000, 86_400_000, 259_200_000, 9_999_999_999, -5)
            .map(HealthRanges::sinceStartOf)
        assertEquals(
            listOf("under_5m", "under_5m", "5_to_30m", "30m_to_2h", "2h_to_1d", "1_to_3d", "over_3d", "over_3d", "under_5m"),
            words,
        )
        assertTrue(words.all { it in HealthRanges.sinceStart })
    }

    @Test
    fun `B-NEXT-004 a restart remembers the funnel, so nothing is sent twice and the clock does not restart`() = runTest {
        val memory = MemoryJourneyMemory()
        val first = rig(memory = memory)
        first.clock[0] += 60_000
        first.service.track(ProductEvent.SetupStepReached, mapOf("step" to "linux", "attempt" to "1"))
        assertEquals(listOf("opened", "setup_started"), first.milestones().map { it.first })

        val second = rig(memory = memory, at = 1_000_000L + 6 * 3_600_000L)
        // Opened is already known; nothing is repeated, but the stage property is set again for the new run.
        assertEquals(emptyList(), second.milestones())
        assertEquals(listOf(mapOf("journey_stage" to "setup_started")), second.vendor.properties)
        second.service.track(ProductEvent.FirstReply, mapOf("server_kind" to "phone", "first_reply_range" to "2-10s"))
        // Six hours after the first run began, not after this run began.
        assertEquals(listOf("first_reply" to "2h_to_1d"), second.milestones())
    }

    @Test
    fun `B-NEXT-005 the stage property is the furthest step reached, never an earlier one reached late`() = runTest {
        val r = rig()
        r.service.track(ProductEvent.FirstReply, mapOf("server_kind" to "computer", "first_reply_range" to "<2s"))
        r.service.track(ProductEvent.ScreenView, mapOf("screen" to "setup")) // a returning person opens setup again
        assertEquals("first_reply", r.vendor.properties.last().getValue("journey_stage"))
        assertTrue(r.vendor.properties.all { it["journey_stage"] in Milestone.entries.map(Milestone::wire) })
    }

    @Test
    fun `B-NEXT-006 nothing is kept or sent while usage is off, and agreeing later starts the clock then`() = runTest {
        val memory = MemoryJourneyMemory()
        val off = rig(usage = false, memory = memory)
        off.service.track(ProductEvent.SetupDone, mapOf("minutes_range" to "under_2", "server_kind" to "phone"))
        assertTrue(off.vendor.events.isEmpty() && off.vendor.properties.isEmpty())
        assertNull(memory.load(), "nothing was remembered about a person who did not agree")

        off.service.setConsent(usage = true, crashes = false)
        off.service.track(ProductEvent.AppOpen, mapOf("kind" to "cold"))
        assertEquals(listOf("opened" to "under_5m"), off.milestones())
    }

    @Test
    fun `B-NEXT-007 the memory holds words and one number, and garbage reads as nothing`() {
        val saved = JourneySaved(1234, setOf(Milestone.ChatOpened, Milestone.Opened))
        assertEquals("1234;opened,chat_opened", JourneyCodec.encode(saved))
        assertEquals(saved, JourneyCodec.decode(JourneyCodec.encode(saved)))
        for (bad in listOf(null, "", "x;opened", "1;nonsense", "1;opened;extra", "a/b;c")) assertNull(JourneyCodec.decode(bad), "$bad")
    }

    @Test
    fun `B-NEXT-008 the milestone event and the stage property admit only their closed words`() = runTest {
        val r = rig()
        r.service.track(ProductEvent.JourneyMilestone, mapOf("step" to "my secret project", "after_range" to "/data/user/0/app"))
        r.service.setUserProperties(mapOf("journey_stage" to "https://example.com"))
        assertEquals(emptyList(), r.vendor.properties.filter { it["journey_stage"] == "https://example.com" })
        assertTrue(r.vendor.events.none { (n, p) -> n == "journey_milestone" && p.values.any { it.contains("secret") || it.contains("/") } })
        assertEquals(Milestone.entries.map { it.wire }.toSet(), productSchema.getValue("journey_milestone").getValue("step"))
    }

    // ---- session depth ----

    @Test
    fun `B-NEXT-010 answered messages are counted per visit and reset when the visit ends`() = runTest {
        val r = rig()
        val t = r.service
        assertEquals(0, t.takeSessionTurns())
        repeat(3) { t.track(ProductEvent.SendResult, mapOf("result" to "ok", "first_reply_range" to "2-10s")) }
        t.track(ProductEvent.SendResult, mapOf("result" to "fail", "fail_kind" to "refused"))
        t.track(ProductEvent.SendResult, mapOf("result" to "queued"))
        assertEquals(3, t.takeSessionTurns())
        assertEquals(0, t.takeSessionTurns(), "taking resets")
    }

    @Test
    fun `B-NEXT-011 a visit reports how many messages it held, only while usage is on`() = runTest {
        val r = rig()
        val clock = object : kotlin.time.Clock {
            override fun now() = kotlin.time.Instant.fromEpochMilliseconds(r.clock[0])
        }
        val life = io.github.eslamasabry.oc.engine.analytics.product.ProductLifecycle(r.service, clock)
        repeat(7) { r.service.track(ProductEvent.SendResult, mapOf("result" to "ok")) }
        r.clock[0] += 3 * 60_000
        life.onBackground()
        assertEquals(
            mapOf("minutes_range" to "1_to_5", "turns_range" to "6-20"),
            r.vendor.events.last { it.first == "session_length" }.second,
        )
        // The next visit starts from zero.
        life.onForeground()
        life.onBackground()
        assertEquals("0", r.vendor.events.last { it.first == "session_length" }.second.getValue("turns_range"))
    }

    // ---- frame health ----

    @Test
    fun `B-NEXT-020 a page past 15 percent janky is no longer one bucket`() {
        assertEquals(listOf("<1", "1-5", "5-15", "15-30", ">30"), HealthRanges.jankPct)
        assertEquals(
            listOf("<1", "1-5", "5-15", "15-30", ">30"),
            listOf(0.5, 4.9, 14.9, 16.0, 30.0).map(HealthRanges::jankPctOf),
        )
        assertEquals("15-30", HealthRanges.jankPctOf(16.0))
        assertEquals(">30", HealthRanges.jankPctOf(80.0))
    }

    @Test
    fun `B-NEXT-021 frozen frames are counted per page and sent with the jank event`() = runTest {
        val vendor = RecordingVendor()
        val service = TelemetryService(vendor, available = true, scope = this)
        service.start(TelemetryConsent(usage = true), NoStore, MemoryTelemetryReportStore())
        val jank = JankMeter(
            report = { screen, frames -> service.track(ProductEvent.Jank, mapOf("screen" to screen) + frames.params()) },
        )
        jank.start()
        jank.screen(io.github.eslamasabry.oc.engine.analytics.product.ProductScreen.Chat)
        jank.onFrames(total = 60, slow = 30, frozen = 0)
        jank.onFrames(total = 40, slow = 20, frozen = 4)
        jank.endSession()
        val sent = vendor.events.single { it.first == "jank" }.second
        assertEquals("chat", sent["screen"])
        assertEquals(">30", sent["janky_pct_range"])
        assertEquals("3-10", sent["frozen_range"])
        assertEquals(listOf("0", "1-2", "3-10", ">10"), listOf(0, 2, 3, 11).map(HealthRanges::frozenOf))
    }

    // ---- the use-case answer ----

    @Test
    fun `B-NEXT-030 the survey leaves as one of four words, never the picks`() {
        assertEquals(ProductUseFor.Everyday, ProductUseFor.of(everyday = true, coding = false))
        assertEquals(ProductUseFor.Coding, ProductUseFor.of(everyday = false, coding = true))
        assertEquals(ProductUseFor.Both, ProductUseFor.of(everyday = true, coding = true))
        assertEquals(ProductUseFor.Looking, ProductUseFor.of(everyday = false, coding = false))
        assertEquals(setOf("everyday", "coding", "both", "looking"), productSchema.getValue("use_for").getValue("kind"))
    }

    @Test
    fun `B-NEXT-031 the answer is sent the first time and again only when it changes, and only with usage on`() = runTest {
        val on = rig()
        on.service.useFor(ProductUseFor.Both)
        on.service.useFor(ProductUseFor.Both, previous = "both")
        on.service.useFor(ProductUseFor.Coding, previous = "both")
        assertEquals(
            listOf(mapOf("kind" to "both"), mapOf("kind" to "coding")),
            on.vendor.events.filter { it.first == "use_for" }.map { it.second },
        )
        val off = rig(usage = false)
        off.service.useFor(ProductUseFor.Everyday)
        assertTrue(off.vendor.events.isEmpty())
    }

    // ---- feature counters and starters ----

    @Test
    fun `B-NEXT-060 a starter chip is sent as its kind's closed word, never its text`() = runTest {
        val r = rig()
        for (name in listOf("FindBug", "ListFolder", "AddTests", "SomethingAddedLater", "my private project", null)) {
            if (name != null) r.service.starterUsed(name)
        }
        assertEquals(
            listOf("find_bug", "list_folder", "add_tests", "other", "other"),
            r.vendor.events.filter { it.first == "starter_used" }.map { it.second.getValue("starter") },
        )
        assertEquals(
            io.github.eslamasabry.oc.engine.analytics.product.ProductStarter.entries.map { it.wire }.toSet(),
            productSchema.getValue("starter_used").getValue("starter"),
        )
        val off = rig(usage = false)
        off.service.starterUsed("FindBug")
        assertTrue(off.vendor.events.isEmpty())
    }

    @Test
    fun `B-NEXT-061 every starter kind the composer offers has its own word`() {
        val names = listOf("ListFolder", "FindBug", "BuildWebPage", "PythonScript", "NodeProject", "Readme", "ExplainProject", "WhatChanged", "AddTests")
        val words = names.map { io.github.eslamasabry.oc.engine.analytics.product.ProductStarter.of(it).wire }
        assertEquals(names.size, words.toSet().size)
        assertTrue("other" !in words)
    }

    @Test
    fun `B-NEXT-062 only the review page counts as a feature just by being opened`() {
        val review = io.github.eslamasabry.oc.engine.analytics.product.ProductScreen.Review
        assertEquals(
            io.github.eslamasabry.oc.engine.analytics.product.ProductFeature.ReviewOpen,
            io.github.eslamasabry.oc.engine.analytics.product.ProductFeature.openedBy(review),
        )
        for (s in io.github.eslamasabry.oc.engine.analytics.product.ProductScreen.entries.filter { it != review })
            assertNull(io.github.eslamasabry.oc.engine.analytics.product.ProductFeature.openedBy(s), s.name)
    }

    // ---- how the first message ended ----

    @Test
    fun `B-NEXT-070 a first message that got a reply records the ending done`() = runTest {
        val r = rig()
        r.service.track(ProductEvent.SendResult, mapOf("result" to "ok"))
        r.service.track(ProductEvent.FirstReply, mapOf("server_kind" to "phone", "first_reply_range" to "2-10s"))
        r.service.track(ProductEvent.ChatTurn, mapOf("outcome" to "done", "server_kind" to "phone"))
        assertEquals(listOf(mapOf("step" to "first_prompt_ended", "outcome" to "done")), r.endings())
        // The ending comes after the reply it belongs to, and the stage stays the furthest step.
        assertEquals(
            listOf("first_prompt", "first_reply", "first_prompt_ended"),
            r.milestones().takeLast(3).map { it.first },
        )
        assertEquals("first_reply", r.vendor.properties.last().getValue("journey_stage"))
    }

    @Test
    fun `B-NEXT-071 a first message to a gone model records error and model_not_found`() = runTest {
        val r = rig()
        r.service.track(ProductEvent.SendResult, mapOf("result" to "ok"))
        r.service.track(ProductEvent.ChatTurn, mapOf("outcome" to "error", "fail_kind" to "model_not_found"))
        // The turn's kind words and nothing else: never what the model said.
        val ending = mapOf("step" to "first_prompt_ended", "outcome" to "error", "fail_kind" to "model_not_found")
        assertEquals(listOf(ending), r.endings())
        assertEquals("first_prompt_ended", r.vendor.properties.last().getValue("journey_stage"))
    }

    @Test
    fun `B-NEXT-072 a refused first send records send_failed with its kind`() = runTest {
        val r = rig()
        r.service.track(ProductEvent.SendResult, mapOf("result" to "fail", "fail_kind" to "model_error"))
        val ending = mapOf("step" to "first_prompt_ended", "outcome" to "send_failed", "fail_kind" to "model_error")
        assertEquals(listOf(ending), r.endings())
        // A turn that follows a refused send is not the first message's ending.
        r.service.track(ProductEvent.ChatTurn, mapOf("outcome" to "done", "server_kind" to "phone"))
        assertEquals(listOf(ending), r.endings())
    }

    @Test
    fun `B-NEXT-073 only the first message's ending is sent, once per install`() = runTest {
        val r = rig()
        val t = r.service
        // A turn that ends before this install sent anything is not the first message's.
        t.track(ProductEvent.ChatTurn, mapOf("outcome" to "done", "server_kind" to "phone"))
        assertEquals(emptyList(), r.endings())
        t.track(ProductEvent.SendResult, mapOf("result" to "ok"))
        t.track(ProductEvent.ChatTurn, mapOf("outcome" to "stopped_by_user", "server_kind" to "phone"))
        val one = listOf(mapOf("step" to "first_prompt_ended", "outcome" to "stopped_by_user"))
        assertEquals(one, r.endings())
        // A second message's ending is not the first's.
        t.track(ProductEvent.SendResult, mapOf("result" to "ok"))
        t.track(ProductEvent.ChatTurn, mapOf("outcome" to "no_output", "server_kind" to "phone"))
        assertEquals(one, r.endings())
        // An upgrade whose saved funnel already holds its first send never sends an ending.
        val upgraded = rig(memory = MemoryJourneyMemory("1000000;opened,first_prompt"))
        upgraded.service.track(ProductEvent.ChatTurn, mapOf("outcome" to "done", "server_kind" to "phone"))
        assertEquals(emptyList(), upgraded.endings())
        // Usage off: nothing leaves the phone.
        val off = rig(usage = false)
        off.service.track(ProductEvent.SendResult, mapOf("result" to "ok"))
        off.service.track(ProductEvent.ChatTurn, mapOf("outcome" to "done", "server_kind" to "phone"))
        assertEquals(emptyList(), off.endings())
    }

    @Test
    fun `B-NEXT-074 the ending is kind words only`() = runTest {
        val r = rig()
        // A word that is not a kind rides on both events; it may not reach an ending.
        val words = mapOf("prompt" to "my secret project")
        r.service.track(ProductEvent.SendResult, mapOf("result" to "ok") + words)
        r.service.track(ProductEvent.ChatTurn, mapOf("outcome" to "error", "fail_kind" to "model_not_found") + words)
        val kindKeys = setOf("step", "after_range", "outcome", "fail_kind")
        val sent = r.vendor.events.filter { it.first == "journey_milestone" }.map { it.second }
        assertTrue(sent.isNotEmpty())
        for (props in sent) assertTrue(props.keys.all { it in kindKeys }, props.keys.toString())
        assertTrue(sent.none { it.values.contains("my secret project") })
        // The schema is the last door: exactly these four names, and `outcome` is the turn's words plus send_failed.
        val schema = productSchema.getValue("journey_milestone")
        assertEquals(kindKeys, schema.keys)
        assertEquals(HealthWords.turnOutcomes + "send_failed", schema.getValue("outcome"))
    }
}
