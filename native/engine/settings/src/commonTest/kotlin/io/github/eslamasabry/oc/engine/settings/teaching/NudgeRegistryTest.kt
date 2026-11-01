package io.github.eslamasabry.oc.engine.settings.teaching

import app.cash.turbine.test
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.settings.consent.TestKv
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NudgeRegistryTest {
    private val clock = TestClock(Instant.parse("2026-09-19T10:00:00Z"))

    private fun TestScope.kv(stored: Map<String, Any> = emptyMap(), firstReplySeen: Boolean = true) =
        TestKv((if (firstReplySeen) mapOf("oc.nudges.firstReplySeen" to true) else emptyMap()) + stored, UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.registry(kv: TestKv = kv()) = NudgeRegistry(kv, kv.lanes, clock, backgroundScope)

    // ---- B-NAV-038: the registry

    @Test
    fun `B-NAV-038 a nudge fires once only`() = runTest {
        val registry = registry()
        assertTrue(registry.offer(NudgeId.Compact, "a"))
        assertEquals(NudgeId.Compact, registry.activeFor("a")?.id)
        registry.dismiss(NudgeId.Compact)
        assertNull(registry.active.value)
        assertFalse(registry.offer(NudgeId.Compact, "a"))
        assertNull(registry.active.value)
    }

    @Test
    fun `B-NAV-038 what was shown survives a reload`() = runTest {
        val kv = kv()
        val first = registry(kv)
        assertTrue(first.offer(NudgeId.ReviewChanges, "a"))
        first.dismiss(NudgeId.ReviewChanges)
        assertEquals("""{"review-changes":{"shownAt":"2026-09-19T10:00:00.000Z","dismissed":true}}""", kv.string("oc.nudges.shown"))
        val reloaded = registry(kv)
        val record = reloaded.record(NudgeId.ReviewChanges)
        assertEquals(clock.now, record?.shownAt)
        assertEquals(true, record?.dismissed)
        assertFalse(reloaded.offer(NudgeId.ReviewChanges, "a"))
        assertNull(reloaded.active.value, "a reload never resurrects a card")
    }

    @Test
    fun `B-NAV-038 a shown but never dismissed nudge still does not return`() = runTest {
        val kv = kv()
        val first = registry(kv)
        assertTrue(first.offer(NudgeId.LeaveAndBeTold, "a"))
        first.release(NudgeId.LeaveAndBeTold)
        runCurrent()
        val reloaded = registry(kv)
        assertEquals(false, reloaded.record(NudgeId.LeaveAndBeTold)?.dismissed)
        assertFalse(reloaded.offer(NudgeId.LeaveAndBeTold, "a"))
    }

    @Test
    fun `B-NAV-038 dismissed stays dismissed`() = runTest {
        val registry = registry()
        registry.offer(NudgeId.Approvals, "a", detail = "edit")
        registry.dismiss(NudgeId.Approvals)
        assertEquals(true, registry.record(NudgeId.Approvals)?.dismissed)
        assertFalse(registry.offer(NudgeId.Approvals, "a"))
        assertFalse(registry.offer(NudgeId.Approvals, "b"))
        assertNull(registry.activeFor("a"))
    }

    @Test
    fun `B-NAV-038 never two at once, and the refused one is not consumed`() = runTest {
        val registry = registry()
        assertTrue(registry.offer(NudgeId.Compact, "a"))
        assertFalse(registry.offer(NudgeId.ReviewChanges, "a"))
        assertFalse(registry.offer(NudgeId.PinConversations, NudgeRegistry.WORK_SCOPE))
        assertEquals(NudgeId.Compact, registry.active.value?.id)
        assertFalse(registry.wasShown(NudgeId.ReviewChanges))
        registry.dismiss(NudgeId.Compact)
        assertTrue(registry.offer(NudgeId.ReviewChanges, "a"))
    }

    @Test
    fun `B-NAV-038 nothing is offered before the first reply`() = runTest {
        val registry = registry(kv(firstReplySeen = false))
        assertFalse(registry.offer(NudgeId.Compact, "a"))
        assertFalse(registry.wasShown(NudgeId.Compact))
        registry.markFirstReplySeen()
        assertTrue(registry.offer(NudgeId.Compact, "a"))
    }

    @Test
    fun `B-NAV-038 reset lets every nudge fire again`() = runTest {
        val kv = kv()
        val registry = registry(kv)
        registry.offer(NudgeId.Compact, "a")
        registry.dismiss(NudgeId.Compact)
        registry.offer(NudgeId.ReviewChanges, "a")
        registry.active.test {
            assertEquals(NudgeId.ReviewChanges, awaitItem()?.id)
            registry.reset()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        assertNull(kv.get("oc.nudges.shown"))
        assertTrue(registry.firstReplySeen)
        assertTrue(registry.offer(NudgeId.Compact, "a"))
    }

    @Test
    fun `B-NAV-038 a nudge only renders at the place it was offered`() = runTest {
        val registry = registry()
        registry.offer(NudgeId.Compact, "a")
        assertNull(registry.activeFor("b"))
        registry.releaseScope("b")
        assertEquals(NudgeId.Compact, registry.active.value?.id)
        registry.releaseScope("a")
        assertNull(registry.active.value)
    }

    @Test
    fun `B-NAV-038 unreadable stored state means nothing was shown`() = runTest {
        val registry = registry(kv(mapOf("oc.nudges.shown" to "not json")))
        assertTrue(registry.offer(NudgeId.Compact, "a"))
    }

    @Test
    fun `B-NAV-038 the second project is counted without storing its path`() = runTest {
        val kv = kv()
        val registry = registry(kv)
        registry.noteProjectUsed("p", "/work/app")
        registry.noteProjectUsed("p", "/work/app")
        assertFalse(registry.secondProjectUsed)
        registry.noteProjectUsed("p", "")
        assertFalse(registry.secondProjectUsed)
        registry.noteProjectUsed("p", "/work/site")
        assertTrue(registry.secondProjectUsed)
        registry.noteProjectUsed("p", "/work/third")
        val stored = (kv.get("oc.nudges.projects") as io.github.eslamasabry.oc.core.data.kv.PrefValue.StrList).value
        assertEquals(2, stored.size)
        assertFalse(stored.joinToString("").contains("work"))
        assertTrue(stored.all { it.length == 16 })
    }

    // ---- B-NAV-039: conversation triggers

    private val idle = ConversationNudgeFacts(busy = false, hasAssistantReply = true)

    private fun TestScope.watcher(registry: NudgeRegistry) = ConversationNudgeWatcher(registry, "s", backgroundScope)

    @Test
    fun `B-NAV-039 the first reply is recorded quietly and its own moment stays quiet`() = runTest {
        val registry = registry(kv(firstReplySeen = false))
        val watcher = watcher(registry)
        watcher.observe(ConversationNudgeFacts(busy = true, hasAssistantReply = false))
        runCurrent()
        assertFalse(registry.firstReplySeen)
        watcher.observe(ConversationNudgeFacts(busy = false, hasAssistantReply = true, runChangedFiles = true, reviewAvailable = true))
        runCurrent()
        assertTrue(registry.firstReplySeen)
        assertNull(registry.active.value)
        assertFalse(registry.wasShown(NudgeId.ReviewChanges))
    }

    @Test
    fun `B-NAV-039 approvals on the third request of one kind, once it is answered`() = runTest {
        val registry = registry()
        val watcher = watcher(registry)
        fun asking(vararg pending: Pair<String, String>) = ConversationNudgeFacts(
            busy = true,
            hasAssistantReply = true,
            pendingPermissions = persistentMapOf(*pending),
            approvalsAvailable = true,
            suppressed = pending.isNotEmpty(),
        )
        watcher.observe(asking("r1" to "edit"))
        watcher.observe(asking("r1" to "edit")) // the same request again is not a second request
        watcher.observe(asking())
        watcher.observe(asking("r2" to "edit", "r3" to "bash"))
        watcher.observe(asking())
        assertNull(registry.active.value)
        watcher.observe(asking("r4" to "edit"))
        assertNull(registry.active.value, "the request card comes first")
        watcher.observe(asking())
        assertEquals(NudgeId.Approvals, registry.activeFor("s")?.id)
        assertEquals("edit", registry.activeFor("s")?.detail)
    }

    @Test
    fun `B-NAV-039 no approvals tip where approvals are already automatic`() = runTest {
        val registry = registry()
        val watcher = watcher(registry)
        for (id in listOf("r1", "r2", "r3")) {
            watcher.observe(ConversationNudgeFacts(busy = true, hasAssistantReply = true, pendingPermissions = persistentMapOf(id to "edit")))
        }
        watcher.observe(ConversationNudgeFacts(busy = true, hasAssistantReply = true))
        assertNull(registry.active.value)
        assertFalse(registry.wasShown(NudgeId.Approvals))
    }

    @Test
    fun `B-NAV-039 review after a run that changed files finishes`() = runTest {
        val registry = registry()
        val watcher = watcher(registry)
        val changed = ConversationNudgeFacts(busy = false, hasAssistantReply = true, runChangedFiles = true, reviewAvailable = true)
        watcher.observe(changed) // opening a finished conversation is not a run finishing
        assertNull(registry.active.value)
        watcher.observe(ConversationNudgeFacts(busy = true, hasAssistantReply = true))
        watcher.observe(changed)
        assertEquals(NudgeId.ReviewChanges, registry.activeFor("s")?.id)
    }

    @Test
    fun `B-NAV-039 no review without changes or without a review`() = runTest {
        val registry = registry()
        val watcher = watcher(registry)
        val busy = ConversationNudgeFacts(busy = true, hasAssistantReply = true)
        watcher.observe(busy)
        watcher.observe(idle)
        watcher.observe(busy)
        watcher.observe(ConversationNudgeFacts(busy = false, hasAssistantReply = true, runChangedFiles = true))
        assertFalse(registry.wasShown(NudgeId.ReviewChanges))
    }

    @Test
    fun `B-NAV-039 leave after a minute of waiting with notifications ready`() = runTest {
        val registry = registry()
        val watcher = watcher(registry)
        val waiting = ConversationNudgeFacts(busy = true, hasAssistantReply = true, finishedRunNotificationsReady = true)
        watcher.observe(waiting)
        advanceTimeBy(30.seconds)
        watcher.observe(waiting) // does not restart the wait
        advanceTimeBy(29.seconds)
        runCurrent()
        assertNull(registry.active.value)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(NudgeId.LeaveAndBeTold, registry.activeFor("s")?.id)
        // The run ends: the sentence is no longer true, so the card leaves.
        watcher.observe(idle)
        assertNull(registry.active.value)
        assertEquals(false, registry.record(NudgeId.LeaveAndBeTold)?.dismissed)
    }

    @Test
    fun `B-NAV-039 no leave tip when a notification could not reach them`() = runTest {
        val registry = registry()
        watcher(registry).observe(ConversationNudgeFacts(busy = true, hasAssistantReply = true))
        advanceTimeBy(61.seconds)
        runCurrent()
        assertFalse(registry.wasShown(NudgeId.LeaveAndBeTold))
    }

    @Test
    fun `B-NAV-039 a run that ends early cancels the wait`() = runTest {
        val registry = registry()
        val watcher = watcher(registry)
        watcher.observe(ConversationNudgeFacts(busy = true, hasAssistantReply = true, finishedRunNotificationsReady = true))
        watcher.observe(idle)
        advanceTimeBy(61.seconds)
        runCurrent()
        assertNull(registry.active.value)
        assertFalse(registry.wasShown(NudgeId.LeaveAndBeTold))
    }

    @Test
    fun `B-NAV-039 compact from 80 percent where compacting exists`() = runTest {
        val registry = registry()
        val watcher = watcher(registry)
        fun at(usage: Double?, available: Boolean = true) =
            ConversationNudgeFacts(busy = false, hasAssistantReply = true, contextUsage = usage, compactAvailable = available)
        watcher.observe(at(0.79))
        watcher.observe(at(null))
        watcher.observe(at(0.95, available = false))
        assertNull(registry.active.value)
        watcher.observe(at(0.826))
        assertEquals(NudgeId.Compact, registry.activeFor("s")?.id)
        assertEquals("83", registry.activeFor("s")?.detail)
        watcher.observe(at(0.2)) // compacted elsewhere
        assertNull(registry.active.value)
    }

    @Test
    fun `B-NAV-039 another card above the composer holds every tip back`() = runTest {
        val registry = registry()
        watcher(registry).observe(
            ConversationNudgeFacts(busy = false, hasAssistantReply = true, contextUsage = 0.9, compactAvailable = true, suppressed = true),
        )
        assertFalse(registry.wasShown(NudgeId.Compact))
    }

    @Test
    fun `B-NAV-039 closing the conversation frees the slot`() = runTest {
        val registry = registry()
        val watcher = watcher(registry)
        watcher.observe(ConversationNudgeFacts(busy = false, hasAssistantReply = true, contextUsage = 0.9, compactAvailable = true))
        assertNotNull(registry.active.value)
        watcher.dispose()
        assertNull(registry.active.value)
        assertTrue(registry.offer(NudgeId.PinConversations, NudgeRegistry.WORK_SCOPE))
    }
}
