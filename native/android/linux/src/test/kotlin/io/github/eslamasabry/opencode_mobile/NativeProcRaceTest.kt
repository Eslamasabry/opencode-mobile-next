package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NativeProcRaceTest {
    // Linux errno values: ENOENT 2, ESRCH 3, EIO 5, EACCES 13.
    private class Errno(val errno: Int) : Exception("errno")
    private fun errnoOf(error: Throwable) = (error as? Errno)?.errno
    private fun gone(error: Throwable) = NativeProcRace.processGone(error, ::errnoOf, 3, 2)

    @Test fun aProcessThatExitedDuringTheReadIsGone() {
        // Android wraps the read's ErrnoException in an IOException.
        assertTrue(gone(IOException("read failed", Errno(3))))
        assertTrue(gone(IOException("open failed", Errno(2))))
    }

    @Test fun otherReadFailuresAreNotProofOfExit() {
        assertFalse(gone(IOException("read failed", Errno(13))))
        assertFalse(gone(IOException("read failed", Errno(5))))
        assertFalse(gone(IOException("no errno")))
        assertFalse(gone(IllegalArgumentException("Failed requirement.")))
    }

    @Test fun settleReturnsTheFirstGoodReadAndPausesBetweenTries() {
        var reads = 0
        var pauses = 0
        val value = NativeProcRace.settle(4, { pauses++ }) {
            reads++
            require(reads >= 3) { "ownershipUnknown" }
            "plan"
        }
        assertEquals("plan", value)
        assertEquals(3, reads)
        assertEquals(2, pauses)
    }

    @Test fun aFailureThatStaysIsThrownUnchanged() {
        val last = IllegalArgumentException("ownershipUnknown")
        var reads = 0
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            NativeProcRace.settle(4, {}) { reads++; throw last }
        }
        assertSame(last, thrown)
        assertEquals(4, reads)
    }

    @Test fun theMonitorStopsTheInstallerOnlyAfterConsecutiveFailures() {
        val strikes = NativeProcRace.Strikes(limit = 3)
        assertFalse(strikes.failed())
        assertFalse(strikes.failed())
        strikes.passed() // A good read in between: the race resolved.
        assertFalse(strikes.failed())
        assertFalse(strikes.failed())
        assertTrue(strikes.failed())
        assertEquals(3, strikes.count)
    }

    @Test fun theDefaultMonitorToleranceStaysBrief() {
        // About half a second at the 100 ms poll: an unknown process that
        // stays is still stopped almost at once.
        assertTrue(NativeProcRace.MONITOR_ATTEMPTS in 2..10)
        assertTrue(NativeProcRace.PLAN_ATTEMPTS in 2..10)
    }

    @Test fun anInterruptionKeepsItsCauseForTheOwnershipClassifier() {
        val cause = IllegalArgumentException("ownershipUnknown")
        val interrupted = InstallerInterruptedException("update failure", cause)
        assertTrue(NativeRuntimeOwnership.isUnknownFailure(interrupted))
        assertTrue(interrupted is IllegalStateException)
    }
}
