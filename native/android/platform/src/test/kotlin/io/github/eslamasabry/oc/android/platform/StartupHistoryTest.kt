package io.github.eslamasabry.oc.android.platform

import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** S2-FIELD start-up history (plan 13 §3.6, WP-13.4). No catalog row covers it; names carry the work package. */
class StartupHistoryTest {
    private val ms = 1_000_000L
    private val nowEpoch = 1_800_000_000_000L
    private val elapsedNow = 500_000L * ms // 500 s since boot

    private fun raw(
        kind: StartKind = StartKind.Cold,
        activity: Boolean = true,
        launchMs: Long? = 100_000,
        forkMs: Long? = 100_010,
        firstFrameMs: Long? = 100_300,
        fullyDrawnMs: Long? = null,
    ) = RawStart(kind, activity, launchMs?.times(ms), forkMs?.times(ms), firstFrameMs?.times(ms), fullyDrawnMs?.times(ms))

    @Test
    fun `WP-13_4 FIELD an activity start becomes a record measured from the launch request`() {
        val r = StartupRules.recordOf(raw(fullyDrawnMs = 100_900), nowEpoch, elapsedNow)!!
        assertEquals(StartKind.Cold, r.kind)
        assertEquals(300, r.firstFrameMs)
        assertEquals(900, r.fullyDrawnMs)
        assertEquals(nowEpoch - 400_000, r.atEpochMs) // launched 400 s before now on the elapsed clock
    }

    @Test
    fun `WP-13_4 FIELD the fork time stands in when the launch time is missing`() {
        val r = StartupRules.recordOf(raw(launchMs = null), nowEpoch, elapsedNow)!!
        assertEquals(290, r.firstFrameMs)
    }

    @Test
    fun `WP-13_4 FIELD starts that are not a person opening the app are not counted`() {
        assertNull(StartupRules.recordOf(raw(activity = false), nowEpoch, elapsedNow), "service or broadcast start")
        assertNull(StartupRules.recordOf(raw(firstFrameMs = null), nowEpoch, elapsedNow), "never drew a frame")
        assertNull(StartupRules.recordOf(raw(launchMs = null, forkMs = null), nowEpoch, elapsedNow), "no origin")
    }

    @Test
    fun `WP-13_4 FIELD implausible timings are dropped instead of skewing the typical time`() {
        assertNull(StartupRules.recordOf(raw(firstFrameMs = 99_000), nowEpoch, elapsedNow), "frame before launch")
        assertNull(StartupRules.recordOf(raw(firstFrameMs = 100_000 + 61_000), nowEpoch, elapsedNow), "over a minute")
        assertNull(StartupRules.recordOf(raw(launchMs = 600_000, firstFrameMs = 600_300), nowEpoch, elapsedNow), "after now")
        val r = StartupRules.recordOf(raw(fullyDrawnMs = 100_200), nowEpoch, elapsedNow)!!
        assertNull(r.fullyDrawnMs, "fully drawn before the first frame is not kept")
    }

    @Test
    fun `WP-13_4 FIELD the typical time is the median of this week's starts`() {
        val day = 24 * 60 * 60 * 1000L
        val records = listOf(
            StartRecord(nowEpoch - 1 * day, StartKind.Cold, 400, null),
            StartRecord(nowEpoch - 2 * day, StartKind.Warm, 200, null),
            StartRecord(nowEpoch - 3 * day, StartKind.Hot, 100, null),
            StartRecord(nowEpoch - 8 * day, StartKind.Cold, 9_000, null), // last week: outside
        )
        val s = StartupRules.summarize(records, nowEpoch)
        assertEquals(200, s.typicalFirstFrameMs)
        assertEquals(3, s.starts)
        assertEquals(1, s.coldStarts)
        assertEquals(records[0], s.latest)
        assertEquals(300, StartupRules.summarize(records.take(2), nowEpoch).typicalFirstFrameMs, "even count: mean of middle two")
        assertEquals(StartupSummary.Empty, StartupRules.summarize(records.drop(3), nowEpoch))
    }

    private class FakeSource(override val supported: Boolean, var history: List<RawStart> = emptyList()) : StartInfoSource {
        var historyCalls = 0
        var listener: ((RawStart) -> Unit)? = null
        var closed = 0

        override fun history(max: Int): List<RawStart> {
            historyCalls++
            return history.take(max)
        }

        override fun onCurrentStart(executor: Executor, listener: (RawStart) -> Unit): AutoCloseable {
            this.listener = listener
            return AutoCloseable { closed++; this.listener = null }
        }

        override fun elapsedNowNs(): Long = 500_000L * 1_000_000L
    }

    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_800_000_000_000L)
    }

    @Test
    fun `WP-13_4 FIELD an older Android reports unsupported and reads nothing`() = runTest {
        val source = FakeSource(supported = false)
        val history = StartupHistory(source, clock, StandardTestDispatcher(testScheduler))
        history.start()
        assertEquals(StartupSummary.Unsupported, history.refresh())
        assertEquals(0, source.historyCalls)
        assertNull(source.listener)
        assertFalse(history.summary.value.supported)
    }

    @Test
    fun `WP-13_4 FIELD refresh reads the system history and this start is counted once`() = runTest {
        val source = FakeSource(supported = true, history = listOf(raw(), raw(kind = StartKind.Warm, launchMs = 200_000, forkMs = null, firstFrameMs = 200_100)))
        val history = StartupHistory(source, clock, StandardTestDispatcher(testScheduler))
        history.start()
        history.start() // repeats safely: one listener
        val s = history.refresh()
        assertEquals(2, s.starts)
        assertEquals(200, s.typicalFirstFrameMs)
        // The listener reports the newest start again (the same start the history already holds): no double count.
        source.listener!!.invoke(raw(kind = StartKind.Warm, launchMs = 200_000, forkMs = null, firstFrameMs = 200_100))
        assertEquals(2, history.summary.value.starts)
        // A start the history did not have yet is folded in at once.
        source.listener!!.invoke(raw(kind = StartKind.Hot, launchMs = 300_000, forkMs = null, firstFrameMs = 300_050))
        assertEquals(3, history.summary.value.starts)
        assertEquals(50, history.summary.value.latest?.firstFrameMs)
        history.close()
        history.close()
        assertEquals(1, source.closed)
        assertTrue(history.summary.value.supported)
    }
}
