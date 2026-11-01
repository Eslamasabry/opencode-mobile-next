package io.github.eslamasabry.oc.core.common.pace

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Plan 13 §11.2 (WP-13.5): the one way to repeat work. Tests run on virtual time only.
@OptIn(ExperimentalCoroutinesApi::class)
class PacerTest {
    private val poll =
        TimerSpec("test.poll", Cadence(foreground = 5.seconds, hiddenWatching = 10.seconds, hiddenIdle = null), "keeps the test busy")

    private fun TestScope.ticksOf(visibility: MutableStateFlow<Visibility>, spec: TimerSpec = poll): MutableList<Long> {
        val seen = mutableListOf<Long>()
        val pacer = DefaultPacer(visibility, Random(1))
        backgroundScope.launch { pacer.ticks(spec).collect { seen += testScheduler.currentTime } }
        runCurrent()
        return seen
    }

    @Test
    fun `PB-42 a hidden-idle cadence under 60 s cannot be declared`() {
        assertFailsWith<IllegalArgumentException> { Cadence(5.seconds, 5.seconds, 59.seconds) }
        Cadence(5.seconds, 5.seconds, 60.seconds)
    }

    @Test
    fun `PB-42 jitter is a fraction between 0 and 0_5`() {
        assertFailsWith<IllegalArgumentException> { Cadence(5.seconds, null, null, jitter = 0.6) }
        assertFailsWith<IllegalArgumentException> { Cadence(5.seconds, null, null, jitter = -0.1) }
    }

    @Test
    fun `PB-42 timer table names every problem`() {
        val fast = TimerSpec("fast.watch", Cadence(1.seconds, 1.seconds, null), "x")
        val fastWithRow = TimerSpec("fast.row", Cadence(1.seconds, 1.seconds, null), "x", rows = listOf("B-CHAT-001"))
        val noReason = TimerSpec("no.reason", Cadence(1.seconds, null, null), " ")
        val problems = timerTableProblems(listOf(poll, fast, fastWithRow, noReason, poll))
        assertEquals(
            listOf(
                "fast.watch: hiddenWatching 1s is under 5 s and names no catalog row",
                "no.reason: reason is empty",
                "test.poll: declared twice",
            ),
            problems,
        )
        assertTrue(timerTableProblems(listOf(poll, fastWithRow)).isEmpty())
    }

    @Test
    fun `ticks follow the foreground cadence and the first comes after one period`() = runTest(StandardTestDispatcher()) {
        val seen = ticksOf(MutableStateFlow(Visibility.Foreground))
        advanceTimeBy(15_001)
        assertEquals(listOf(5_000L, 10_000L, 15_000L), seen)
    }

    @Test
    fun `no timer is armed while the cadence is null`() = runTest(StandardTestDispatcher()) {
        val visibility = MutableStateFlow(Visibility.HiddenIdle)
        val seen = ticksOf(visibility)
        advanceTimeBy(600_000)
        assertTrue(seen.isEmpty())
    }

    @Test
    fun `hidden watching uses its own cadence and a return to the foreground ticks at once`() =
        runTest(StandardTestDispatcher()) {
            val visibility = MutableStateFlow(Visibility.HiddenWatching)
            val seen = ticksOf(visibility)
            advanceTimeBy(10_001)
            assertEquals(listOf(10_000L), seen)
            advanceTimeBy(2_000) // t = 12 001
            visibility.value = Visibility.Foreground
            runCurrent()
            advanceTimeBy(5_001)
            assertEquals(listOf(10_000L, 12_001L, 17_001L), seen)
        }

    @Test
    fun `jitter stays inside the declared fraction`() = runTest(StandardTestDispatcher()) {
        val spec = TimerSpec("test.jitter", Cadence(10.seconds, null, null, jitter = 0.2), "x")
        val seen = ticksOf(MutableStateFlow(Visibility.Foreground), spec)
        advanceTimeBy(200_000)
        val gaps = (listOf(0L) + seen).zipWithNext { a, b -> (b - a).milliseconds }
        assertTrue(gaps.size >= 16)
        assertTrue(gaps.all { it in 8.seconds..12.seconds }, "gaps $gaps")
        assertTrue(gaps.toSet().size > 1, "jitter never varied")
    }

    @Test
    fun `PB-42 no hidden-idle timer fires faster than 60 s, jitter included (review w1-core-net MINOR 12)`() =
        runTest(StandardTestDispatcher()) {
            val spec = TimerSpec("test.idle", Cadence(10.seconds, null, hiddenIdle = 60.seconds, jitter = 0.5), "x")
            val seen = ticksOf(MutableStateFlow(Visibility.HiddenIdle), spec)
            advanceTimeBy(3_600_000)
            val gaps = (listOf(0L) + seen).zipWithNext { a, b -> (b - a).milliseconds }
            assertTrue(gaps.size >= 30)
            assertTrue(gaps.all { it >= 60.seconds }, "gaps $gaps")
            assertTrue(gaps.toSet().size > 1, "jitter above the floor still varies")
        }
}
