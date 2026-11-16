package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.ProductScreen
import io.github.eslamasabry.oc.engine.analytics.product.productSchema
import io.github.eslamasabry.oc.engine.analytics.product.productValueIsSafe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * The owner's decisions 20 and 21 (2026-11-16) for analytics truth: the agent's first activity is its own number
 * (`activity_range`), an approval says where it was answered (`surface`), the chat's slow frames say what was going on
 * (`interaction`), and an install that agreed after its first reply sends its first reply AFTER agreeing, marked
 * `after_consent`, and nothing for the past.
 */
class DecisionFieldsTest {
    @AfterTest fun reset() = AppTelemetry.reset()

    private object NoStore : TelemetryConsentStore {
        override suspend fun write(consent: TelemetryConsent): Boolean = true
    }

    /** A store that keeps both flags, as the app's preferences do. */
    private class Flags : FirstReplyOnce {
        var seenFlag = false
        var sentFlag = false

        override fun seen() = seenFlag

        override fun mark() {
            seenFlag = true
        }

        override fun sent() = sentFlag

        override fun markSent() {
            sentFlag = true
        }
    }

    private class Rig(scope: TestScope, usage: Boolean = true, val flags: Flags = Flags()) {
        val vendor = RecordingVendor()
        var now = 50_000L
        val service = TelemetryService(vendor, available = true, scope = scope)

        init {
            service.start(TelemetryConsent(usage = usage), NoStore, MemoryTelemetryReportStore())
            AppTelemetry.install(service)
        }

        val journey = ChatJourney({ now }, HealthFlow(), flags)
        val meter = ApprovalMeter({ now }, HealthFlow()) { journey }

        fun all(name: String) = vendor.events.filter { it.first == name }.map { it.second }

        fun last(name: String) = all(name).last()

        /** One whole turn that shows a reply. */
        fun reply(session: String) {
            journey.sent(session, onPhone = true)
            journey.busy(session)
            journey.message(session, "m-$session", assistant = true)
            now += 3_000
            journey.text(session, "m-$session", streamed = true)
            journey.ended(session)
        }
    }

    // ---- activity_range ----

    @Test
    fun `B-JOURNEY-100 the agent's first activity is its own number, apart from the first visible reply`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true)
        r.journey.busy("s1")
        r.journey.message("s1", "m1", assistant = true)
        r.now += 1_000
        r.journey.part("s1", "m1", "reasoning") // the agent is working: activity, nothing to see
        r.now += 24_000
        r.journey.text("s1", "m1", streamed = true) // the first thing the person sees, 25 s after Send
        r.journey.ended("s1")
        val turn = r.last("chat_turn")
        assertEquals("<2s", turn.getValue("activity_range"))
        assertEquals("10-30s", turn.getValue("first_reply_range"))
    }

    @Test
    fun `B-JOURNEY-101 a reasoning-only turn shows activity but no first reply, a silent one shows neither`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true)
        r.journey.busy("s1")
        r.journey.message("s1", "m1", assistant = true)
        r.now += 5_000
        r.journey.part("s1", "m1", "reasoning")
        r.journey.ended("s1")
        assertEquals("2-10s", r.last("chat_turn").getValue("activity_range"))
        assertEquals("none", r.last("chat_turn").getValue("first_reply_range"))
        r.journey.sent("s2", onPhone = true)
        r.journey.busy("s2")
        r.now += 10_000
        r.journey.ended("s2")
        assertEquals("none", r.last("chat_turn").getValue("activity_range"))
    }

    @Test
    fun `B-JOURNEY-102 the person's own prompt part is not the agent's activity`() = runTest {
        val r = Rig(this)
        r.journey.sent("s1", onPhone = true)
        r.journey.busy("s1")
        r.journey.message("s1", "u1", assistant = false)
        r.now += 1_000
        r.journey.part("s1", "u1", "text")
        r.journey.part("s1", "u1", "file")
        r.journey.ended("s1")
        assertEquals("none", r.last("chat_turn").getValue("activity_range"))
    }

    // ---- surface ----

    @Test
    fun `B-JOURNEY-103 an answer says where it was given, the chat by default`() = runTest {
        val r = Rig(this)
        r.meter.asked("a1", "s1", "run")
        r.meter.answered("a1", "allowed")
        assertEquals("chat", r.last("approval").getValue("surface"))
        r.meter.asked("a2", "s1", "edit")
        r.meter.surface("a2", "notification")
        r.meter.answered("a2", "denied")
        assertEquals("notification", r.last("approval").getValue("surface"))
        r.meter.asked("a3", "s1", "run")
        r.meter.surface("a3", "home")
        r.meter.answered("a3", "always")
        assertEquals("home", r.last("approval").getValue("surface"))
        // A hint for a send that did not go is taken back.
        r.meter.asked("a4", "s1", "run")
        r.meter.surface("a4", "home")
        r.meter.surfaceUnused("a4")
        r.meter.answered("a4", "allowed")
        assertEquals("chat", r.last("approval").getValue("surface"))
    }

    @Test
    fun `B-JOURNEY-104 a widget answer counts at once as the widget's, and the request leaving adds nothing`() = runTest {
        val r = Rig(this)
        r.meter.asked("a1", "s1", "run")
        r.meter.answeredOn("a1", "denied", "widget")
        r.meter.gone("a1")
        assertEquals(1, r.all("approval").size)
        assertEquals("denied", r.last("approval").getValue("result"))
        assertEquals("widget", r.last("approval").getValue("surface"))
    }

    @Test
    fun `B-JOURNEY-105 auto, stopped and expired carry no surface, because no person answered`() = runTest {
        val r = Rig(this)
        r.meter.asked("a1", "s1", "run")
        r.meter.surface("a1", "home")
        r.meter.answered("a1", "auto")
        r.meter.asked("a2", "s1", "run")
        r.meter.stopped("a2")
        r.meter.asked("a3", "s1", "run")
        r.meter.gone("a3")
        val approvals = r.all("approval")
        assertEquals(listOf("auto", "stopped", "expired"), approvals.map { it["result"] })
        for (a in approvals) assertFalse("surface" in a, "no surface on ${a["result"]}")
    }

    // ---- first_reply after consent ----

    @Test
    fun `B-JOURNEY-106 an install that agreed before its first reply sends it, after_consent no`() = runTest {
        val r = Rig(this)
        r.reply("s1")
        r.reply("s2")
        assertEquals(1, r.all("first_reply").size)
        assertEquals("no", r.last("first_reply").getValue("after_consent"))
        assertTrue(r.flags.sentFlag)
    }

    @Test
    fun `B-JOURNEY-107 an install that agreed after its first reply sends nothing for the past, then its next reply, after_consent yes`() = runTest {
        val r = Rig(this, usage = false)
        r.reply("s1") // seen with usage off: nothing is sent, and nothing is kept to send later
        assertEquals(emptyList(), r.all("first_reply"))
        assertTrue(r.flags.seenFlag)
        assertFalse(r.flags.sentFlag, "nothing was sent, so the install has not sent its first reply")
        r.service.setConsent(usage = true, crashes = false)
        assertEquals(emptyList(), r.all("first_reply"), "agreeing sends nothing about the past")
        r.reply("s2")
        assertEquals(1, r.all("first_reply").size)
        assertEquals("yes", r.last("first_reply").getValue("after_consent"))
        r.reply("s3")
        assertEquals(1, r.all("first_reply").size, "once per install")
    }

    // ---- interaction ----

    @Test
    fun `B-JOURNEY-108 the chat's slow frames are reported per interaction, other pages as one`() {
        val sent = mutableListOf<Pair<ProductScreen, JankReport>>()
        var word: String? = "streaming"
        val meter = JankMeter(report = { s, r -> sent += s to r }, interaction = { word })
        meter.start()
        meter.screen(ProductScreen.Chat)
        meter.onFrames(total = 40, slow = 40) // while a reply streams
        word = "scrolling"
        meter.onFrames(total = 40, slow = 0)
        word = "keyboard"
        meter.onFrames(total = 10, slow = 10) // too few on its own
        meter.screen(ProductScreen.Settings)
        meter.onFrames(total = 40, slow = 4)
        meter.endSession()
        val byInteraction = sent.filter { it.first == ProductScreen.Chat }.associate { it.second.interaction to it.second.pctRange }
        assertEquals(mapOf<String?, String>("streaming" to ">30", "scrolling" to "<1"), byInteraction)
        assertNull(sent.single { it.first == ProductScreen.Settings }.second.interaction, "only the chat is split")
        assertEquals("streaming", sent.first { it.first == ProductScreen.Chat }.second.params()["interaction"])
        assertFalse("interaction" in sent.single { it.first == ProductScreen.Settings }.second.params())
    }

    @Test
    fun `B-JOURNEY-109 the interaction word follows menu, keyboard, scrolling, streaming, idle in that order`() {
        var t = 0L
        var streaming = false
        var menu = false
        var keyboard = false
        val i = ChatInteraction({ t }, { streaming }, { menu }, { keyboard })
        assertEquals("idle", i.word())
        streaming = true
        assertEquals("streaming", i.word())
        i.scrolled()
        assertEquals("scrolling", i.word())
        t += ChatInteraction.SCROLL_TAIL_MS + 1
        assertEquals("streaming", i.word(), "the scroll ended")
        i.scrolled()
        keyboard = true
        assertEquals("keyboard", i.word())
        menu = true
        assertEquals("menu", i.word())
        for (w in listOf("idle", "streaming", "scrolling", "menu", "keyboard")) assertTrue(w in HealthWords.interactions)
    }

    // ---- words ----

    @Test
    fun `B-JOURNEY-110 the new fields hold fixed safe words, each on the event that sends it`() {
        assertEquals(setOf("chat", "home", "notification", "widget"), productSchema.getValue("approval").getValue("surface"))
        assertEquals(setOf("streaming", "scrolling", "menu", "keyboard", "idle"), productSchema.getValue("jank").getValue("interaction"))
        assertEquals(HealthRanges.firstReply.toSet(), productSchema.getValue("chat_turn").getValue("activity_range"))
        assertEquals(setOf("yes", "no"), productSchema.getValue("first_reply").getValue("after_consent"))
        for ((event, key) in listOf("approval" to "surface", "jank" to "interaction", "chat_turn" to "activity_range", "first_reply" to "after_consent")) {
            for (v in productSchema.getValue(event).getValue(key)) assertTrue(productValueIsSafe(v), "$event.$key=$v")
        }
        assertTrue(ProductEvent.Jank.wire in productSchema)
    }
}
