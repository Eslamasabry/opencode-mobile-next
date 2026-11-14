package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.KeptTapRules
import io.github.eslamasabry.oc.feature.chats.presentation.KeptWait
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-333 with the background CPU rule (2026-11-14): a kept tap's wait is one timer per quiet stretch, not a loop that
 * wakes every step. An agent update pauses it (its quiet time so far still counts), and an ended tap stops it at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeptTapWaitTest {
    @Test
    fun `B-333 a quiet kept tap is spent at its limit, not a step later`() = runTest {
        val waits = MutableStateFlow(KeptWait.Quiet)
        val spent = async { KeptTapRules.limitSpent(waits, limitMs = 90_000, time = testTimeSource) }
        advanceTimeBy(89_999)
        assertFalse(spent.isCompleted)
        advanceTimeBy(2)
        assertTrue(spent.await())
    }

    @Test
    fun `B-333 an agent update pauses the wait and the quiet time before it still counts`() = runTest {
        val waits = MutableStateFlow(KeptWait.Quiet)
        val spent = async { KeptTapRules.limitSpent(waits, limitMs = 90_000, time = testTimeSource) }
        advanceTimeBy(60_000)
        waits.value = KeptWait.Updating
        runCurrent()
        advanceTimeBy(10 * 60_000)
        assertFalse(spent.isCompleted, "the update does not count")
        waits.value = KeptWait.Quiet
        runCurrent()
        advanceTimeBy(29_999)
        assertFalse(spent.isCompleted, "60 s before the update + 29.999 s after it")
        advanceTimeBy(2)
        assertTrue(spent.await())
    }

    @Test
    fun `B-333 a tap that ends stops the wait at once, with no timer left behind`() = runTest {
        val waits = MutableStateFlow(KeptWait.Quiet)
        val spent = async { KeptTapRules.limitSpent(waits, limitMs = 90_000, time = testTimeSource) }
        advanceTimeBy(5_000)
        waits.value = KeptWait.Ended
        runCurrent()
        assertTrue(spent.isCompleted)
        assertFalse(spent.await())
        // While updating it waits for as long as the update takes, and still ends at once with the tap.
        val updating = MutableStateFlow(KeptWait.Updating)
        val paused = async { KeptTapRules.limitSpent(updating, limitMs = 90_000, time = testTimeSource) }
        advanceTimeBy(24 * 60 * 60_000L)
        assertFalse(paused.isCompleted)
        updating.value = KeptWait.Ended
        runCurrent()
        assertFalse(paused.await())
    }
}
