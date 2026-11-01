package io.github.eslamasabry.oc.engine.diagnostics.crash

import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.diagnostics.CrashCategory
import io.github.eslamasabry.oc.engine.diagnostics.RecordingSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class CrashLoopGuardTest {
    private val clock = TestClock()
    private val signals = RecordingSignals()
    private val guard = CrashLoopGuard(clock, signals)

    private fun crashingLaunch(record: CrashLoopRecord, after: Int = 2): Pair<LaunchMode, CrashLoopRecord> {
        val (mode, launched) = guard.onLaunch(record)
        clock.advance(after.seconds)
        return mode to guard.onCrash(launched)
    }

    @Test
    fun `B-SEC-045 three launches that crash within 10 s open safe mode once`() {
        var record = CrashLoopRecord()
        repeat(3) {
            val (mode, next) = crashingLaunch(record)
            assertEquals(LaunchMode.Normal, mode)
            record = next
        }
        val (mode, fresh) = guard.onLaunch(record)
        assertEquals(LaunchMode.SafeMode, mode)
        assertEquals(0, fresh.earlyCrashes)
        assertEquals(listOf(CrashCategory.StartupLoop to true), signals.crashes)
        assertEquals(LaunchMode.Normal, guard.onLaunch(fresh).first)
    }

    @Test
    fun `B-SEC-045 a late crash or a stable launch breaks the streak`() {
        var record = crashingLaunch(CrashLoopRecord()).second
        record = crashingLaunch(record).second
        record = crashingLaunch(record, after = 11).second
        assertEquals(0, record.earlyCrashes)
        record = crashingLaunch(record).second
        record = crashingLaunch(record).second
        record = guard.onStable(guard.onLaunch(record).second)
        assertEquals(LaunchMode.Normal, guard.onLaunch(record).first)
        assertEquals(emptyList(), signals.crashes)
    }
}
