package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.common.pace.Visibility
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one reconnect schedule (plan 04 §6.2 N4-D6, extended by plan 13 amendment A2; PB-51, PB-53). */
class BackoffPolicyTest {
    private val hidden = Visibility.HiddenWatching

    @Test
    fun `N4-D6 OpenCode 2 policy is 1 2 4 8 16 16 s with no jitter`() {
        val r = Random(1)
        val waits = (1..7).map { BackoffPolicy.OpenCode2.delayMs(it, r) }
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 16_000, 16_000), waits)
    }

    @Test
    fun `N4-D6 the presets keep 04's numbers`() {
        assertEquals(BackoffPolicy(500, 16_000, true, null, 30_000, 6, 45_000), BackoffPolicy.OpenCode2)
        assertEquals(BackoffPolicy(500, 30_000, false, 0.5..1.5, 30_000, Int.MAX_VALUE, 90_000), BackoffPolicy.GasCity)
        listOf(BackoffPolicy.OpenCode2, BackoffPolicy.GasCity).forEach {
            assertEquals(2, it.hiddenAfterAttempt)
            assertEquals(60_000L..120_000L, it.hiddenRangeMs)
        }
    }

    @Test
    fun `N4-D6 Gas City policy is jittered and capped at 30 s`() {
        val r = Random(5)
        repeat(1_000) { attempt ->
            val d = BackoffPolicy.GasCity.delayMs(1 + attempt % 20, r)
            assertTrue(d in 250L..30_000L, "$d")
        }
    }

    @Test
    fun `PB-51 foreground visibility is 04's formula unchanged`() {
        listOf(BackoffPolicy.OpenCode2, BackoffPolicy.GasCity).forEach { policy ->
            (1..12).forEach { attempt ->
                val plain = policy.delayMs(attempt, Random(attempt))
                assertEquals(plain, policy.delayMs(attempt, Random(attempt), Visibility.Foreground))
            }
        }
    }

    @Test
    fun `PB-51 hidden retries 1 s 2 s then 60-120 s`() {
        val r = Random(3)
        assertEquals(listOf(1_000L, 2_000), (1..2).map { BackoffPolicy.OpenCode2.delayMs(it, r, hidden) })
        val later = (0 until 2_000).map { BackoffPolicy.OpenCode2.delayMs(3 + it % 9, r, hidden)!! }
        assertTrue(later.all { it in 60_000L..120_000L }, "$later")
        // Jittered: the draws spread over the range rather than sitting on one value (no synchronized radio wakes).
        assertTrue(later.min() < 70_000 && later.max() > 110_000, "${later.min()}..${later.max()}")
    }

    @Test
    fun `PB-51 Gas City hidden pace is the same 60-120 s after two quick tries`() {
        val r = Random(11)
        assertTrue(BackoffPolicy.GasCity.delayMs(1, r, hidden)!! in 250L..750L)
        repeat(500) { assertTrue(BackoffPolicy.GasCity.delayMs(3 + it, r, hidden)!! in 60_000L..120_000L) }
    }

    @Test
    fun `PB-53 hidden idle dials nothing`() {
        val r = Random(1)
        (1..20).forEach {
            assertNull(BackoffPolicy.OpenCode2.delayMs(it, r, Visibility.HiddenIdle))
            assertNull(BackoffPolicy.GasCity.delayMs(it, r, Visibility.HiddenIdle))
        }
    }

    @Test
    fun `PB-51 a policy refuses a hidden range that would wake the radio more than once a minute`() {
        assertFailsWith<IllegalArgumentException> { BackoffPolicy.OpenCode2.copy(hiddenRangeMs = 30_000L..120_000L) }
        assertFailsWith<IllegalArgumentException> { BackoffPolicy.OpenCode2.copy(hiddenRangeMs = 120_000L..60_000L) }
    }
}
