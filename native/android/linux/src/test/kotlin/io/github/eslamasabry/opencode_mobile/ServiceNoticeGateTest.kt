package io.github.eslamasabry.opencode_mobile

import io.github.eslamasabry.opencode_mobile.ServiceNoticeGate.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceNoticeGateTest {
    private fun gate() = ServiceNoticeGate(windowMs = 5_000)

    @Test fun theFirstCallStartsTheService() {
        assertEquals(Decision.Send, gate().decide("Running", serviceShown = false, nowMs = 0))
    }

    @Test fun aStormOfCallsWithTheSameWordsStartsItOnce() {
        val g = gate()
        var starts = 0
        for (t in 0 until 800 step 1) {
            val ms = t * 100L
            if (g.decide("Running", serviceShown = starts > 0, nowMs = ms) == Decision.Send) {
                starts++; g.sent("Running", ms)
            }
        }
        assertEquals(1, starts)
    }

    @Test fun changedWordsWaitOutTheWindowThenGoOnceWithTheLatest() {
        val g = gate()
        g.sent("A", 0)
        assertEquals(Decision.Wait(3_000), g.decide("B", true, 2_000))
        assertEquals(Decision.Wait(1_000), g.decide("C", true, 4_000))
        assertEquals(Decision.Send, g.decide("C", true, 5_000))
        g.sent("C", 5_000)
        assertEquals(Decision.Skip, g.decide("C", true, 5_100))
    }

    @Test fun aServiceThatIsNotShowingIsAlwaysStarted() {
        val g = gate()
        g.sent("A", 0)
        assertEquals(Decision.Send, g.decide("A", serviceShown = false, nowMs = 10))
    }

    @Test fun afterAStopTheNextCallStartsAgain() {
        val g = gate()
        g.sent("A", 0)
        g.reset()
        assertEquals(Decision.Send, g.decide("A", true, 100))
    }

    @Test fun aClockThatWentBackDoesNotFreezeTheRow() {
        val g = gate()
        g.sent("A", 10_000)
        assertEquals(Decision.Send, g.decide("B", true, 5_000))
    }
}
