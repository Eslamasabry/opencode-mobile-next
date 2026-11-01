package io.github.eslamasabry.oc.engine.settings.teaching

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.settings.consent.TestKv
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TipControllerTest {
    private val a = TipId("a")
    private val b = TipId("b")
    private val c = TipId("c")
    private val clock = TestClock(Instant.parse("2026-10-21T09:00:00Z"))

    private fun TestScope.kv(initial: Map<String, Any> = emptyMap()) = TestKv(initial, UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.tips(kv: TestKv = kv()) = TipController(kv, kv.lanes, clock, backgroundScope)

    @Test
    fun `B-NAV-038 a tip shows at most twice, a quiet gap apart`() = runTest {
        val tips = tips()
        assertTrue(tips.offer(a))
        tips.dismiss(a)
        assertFalse(tips.offer(a), "cooldown")
        clock.advance(TipController.COOLDOWN)
        assertTrue(tips.offer(a))
        tips.dismiss(a)
        clock.advance(TipController.COOLDOWN)
        assertFalse(tips.offer(a), "shown twice")
        assertEquals(2, tips.record(a).count)
    }

    @Test
    fun `B-NAV-038 one tip at a time and a higher priority takes the place`() = runTest {
        val tips = tips()
        assertTrue(tips.offer(a, priority = 1))
        assertFalse(tips.offer(b, priority = 1))
        assertEquals(0, tips.record(b).count, "a refusal consumes nothing")
        assertTrue(tips.offer(c, priority = 2))
        assertEquals(c, tips.active.value?.id)
        assertTrue(tips.offer(c, priority = 2), "asked again while it shows: still showing")
        assertEquals(1, tips.record(c).count)
    }

    @Test
    fun `B-NAV-038 a tip never shows again once used`() = runTest {
        val tips = tips()
        tips.markUsed(a)
        assertFalse(tips.offer(a))
        assertFalse(tips.due(a))
    }

    @Test
    fun `B-NAV-038 no tip while a question waits, the keyboard covers the anchor or onboarding runs`() = runTest {
        val tips = tips()
        for (moment in listOf(TipMoment(questionWaiting = true), TipMoment(keyboardOverAnchor = true), TipMoment(onboarding = true))) {
            assertFalse(tips.offer(a, moment = moment))
        }
        assertEquals(0, tips.record(a).count)
    }

    @Test
    fun `B-NAV-038 one new tip per screen visit`() = runTest {
        val tips = tips()
        val visit = TipVisit()
        assertTrue(tips.offer(a, visit = visit))
        tips.dismiss(a)
        clock.advance(TipController.COOLDOWN)
        assertFalse(tips.offer(b, visit = visit))
        assertTrue(tips.offer(b, visit = TipVisit()))
    }

    @Test
    fun `B-NAV-038 unreadable tip state means nothing was shown`() = runTest {
        val tips = tips(kv(mapOf("oc.tips" to "not json")))
        assertTrue(tips.offer(a))
    }

    @Test
    fun `B-STATE-142 tip state is stored app-wide as oc tips and read back`() = runTest {
        val kv = kv()
        val tips = tips(kv)
        tips.offer(a)
        tips.markUsed(b)
        runCurrent()
        val saved = kv.string("oc.tips")!!
        assertEquals("""{"a":{"count":1,"last":"2026-10-21T09:00:00.000Z"},"b":{"count":0,"done":true}}""", saved)
        val again = tips(kv)
        assertEquals(1, again.record(a).count)
        assertEquals(Instant.parse("2026-10-21T09:00:00Z"), again.record(a).lastShown)
        assertTrue(again.record(b).done)
        assertTrue(kv.keys("oc.tips").none { it != "oc.tips" }, "app-wide: no per-profile key")
    }

    @Test
    fun `B-STATE-142 the Flutter oc tips value is read unchanged`() = runTest {
        val flutter = """{"stop-after-step":{"count":2,"last":"2026-10-20T08:15:30.123456Z"},"make-it-yours":{"count":1,"done":true},"x":5}"""
        val tips = tips(kv(mapOf("oc.tips" to flutter)))
        assertFalse(tips.due(TeachingIds.StopAfterStep))
        assertEquals(Instant.parse("2026-10-20T08:15:30.123456Z"), tips.record(TeachingIds.StopAfterStep).lastShown)
        assertTrue(tips.makeItYoursAnswered())
        assertEquals(TipRecord(), tips.record(TipId("x")))
    }

    @Test
    fun `B-STATE-142 a replay counts nothing and Show tips again resets all`() = runTest {
        val kv = kv()
        val tips = tips(kv)
        tips.replay(a)
        assertTrue(tips.isActive(a))
        assertEquals(true, tips.active.value?.replay)
        assertEquals(0, tips.record(a).count)
        assertFalse(tips.offer(a), "a replay is not its moment")
        tips.markUsed(b)
        runCurrent()
        assertTrue(kv.string("oc.tips") != null)
        assertEquals(Outcome.Ok(Unit), tips.reset())
        runCurrent()
        assertNull(tips.active.value)
        assertFalse(tips.record(b).done)
        assertNull(kv.get("oc.tips"))
    }

    @Test
    fun `B-NAV-038 registry entries carry their own count and priority`() = runTest {
        val tips = tips()
        val registry = TeachingRegistry.defaults
        assertTrue(tips.offer(registry, TeachingIds.SendLater))
        assertTrue(tips.offer(registry, TeachingIds.StopAfterStep), "priority 2 replaces priority 1")
        tips.dismiss(TeachingIds.StopAfterStep)
        clock.advance(TipController.COOLDOWN)
        assertTrue(tips.offer(registry, TeachingIds.MakeItYours))
        tips.dismiss(TeachingIds.MakeItYours)
        clock.advance(TipController.COOLDOWN)
        assertFalse(tips.offer(registry, TeachingIds.MakeItYours), "offered once")
        assertFalse(tips.offer(registry, TipId("unregistered")))
    }

    @Test
    fun `B-L10N-020 tip and tutorial ids are unique and tutorials have 2 to 4 steps`() {
        val registry = TeachingRegistry.defaults
        assertEquals(emptyList<String>(), registry.problems())
        val ids = registry.tips.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(4, registry.entry(TeachingIds.MakeItYours)!!.steps.size)

        val tour = registry.entry(TeachingIds.MakeItYours)!!
        val broken = TeachingRegistry(
            listOf(
                tour,
                tour.copy(steps = tour.steps.subList(0, 1).let { persistentListOf(it[0]) }),
                registry.entry(TeachingIds.HoldMic)!!.copy(id = TipId("other"), messageKey = "undeclared", maxCount = 3),
            ),
        )
        val problems = broken.problems()
        assertTrue(problems.any { "not unique" in it }, "$problems")
        assertTrue(problems.any { "2 to 4 steps" in it }, "$problems")
        assertTrue(problems.any { "message key is not declared" in it }, "$problems")
        assertTrue(problems.any { "maxCount" in it }, "$problems")
    }

    @Test
    fun `B-L10N-020 every tip and tutorial declares the copy keys it shows`() {
        for (tip in TeachingRegistry.defaults.tips) {
            assertTrue(tip.titleKey in tip.copyKeys, tip.id.raw)
            assertTrue(tip.messageKey in tip.copyKeys, tip.id.raw)
            for (step in tip.steps) assertTrue(step.messageKey in tip.copyKeys, tip.id.raw)
        }
    }
}
