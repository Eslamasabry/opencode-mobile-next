package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.*
import org.junit.Test

class NativeIdleHeartbeatTest {
    @Test fun coldAndClearedEvidenceNeverEstablishIdle() {
        val heartbeat = NativeIdleHeartbeat()
        assertNull(heartbeat.busy("server", 0))
        assertTrue(heartbeat.observe("server", false, 1))
        assertEquals(false, heartbeat.busy("server", 1))
        heartbeat.clear()
        assertNull(heartbeat.busy("server", 1))
    }

    @Test fun defaultIdleEvidenceIsFreshOnlyBeforeFortyFiveSecondBoundary() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 1_000)
        assertEquals(false, heartbeat.busy("server", 1_000))
        assertEquals(false, heartbeat.busy("server", 45_999))
        assertNull(heartbeat.busy("server", 46_000))
        assertNull(heartbeat.busy("server", 46_001))
    }

    @Test fun knownBusyIsPositiveEvidenceUntilItAlsoExpires() {
        val heartbeat = NativeIdleHeartbeat(100)
        assertTrue(heartbeat.observe("server", true, 10))
        assertEquals(true, heartbeat.busy("server", 109))
        assertNull(heartbeat.busy("server", 110))
    }

    @Test fun onlyExactOwnerCanReadEvidenceAndWrongOwnerDoesNotEraseIt() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 0)
        assertNull(heartbeat.busy("other", 1))
        assertNull(heartbeat.busy("Server", 2))
        assertEquals(false, heartbeat.busy("server", 2))
    }

    @Test fun switchingObservationOwnerReplacesThePriorOwnersEvidence() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 0)
        heartbeat.observe("other", true, 1)
        assertNull(heartbeat.busy("server", 1))
        assertEquals(true, heartbeat.busy("other", 1))
    }

    @Test fun unknownObservationErasesPriorKnownIdleWithoutInventingWorkTruth() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 0)
        assertTrue(heartbeat.observe("server", null, 1))
        assertNull(heartbeat.busy("server", 1))
        assertNull(heartbeat.busy("server", 45_000))
        assertTrue(heartbeat.observe("server", false, 45_001))
        assertEquals(false, heartbeat.busy("server", 45_001))
    }

    @Test fun malformedObservationOwnerClearsPreviouslyKnownIdle() {
        for (owner in listOf("", "server/other", "server.alias", "a".repeat(81))) {
            val heartbeat = NativeIdleHeartbeat()
            heartbeat.observe("server", false, 0)
            assertFalse(heartbeat.observe(owner, false, 1))
            assertNull(heartbeat.busy("server", 1))
        }
        val heartbeat = NativeIdleHeartbeat()
        assertTrue(heartbeat.observe("a_-".repeat(26) + "aa", false, 0))
    }

    @Test fun malformedQueryCannotReadAnotherOwnersIdleEvidence() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 0)
        for (owner in listOf("", "server/other", "a".repeat(81))) assertNull(heartbeat.busy(owner, 1))
        assertEquals(false, heartbeat.busy("server", 1))
    }

    @Test fun negativeObserveAndQueryBothClearEvidence() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 0)
        assertFalse(heartbeat.observe("server", false, -1))
        assertNull(heartbeat.busy("server", 0))
        heartbeat.observe("server", false, 0)
        assertNull(heartbeat.busy("server", Long.MIN_VALUE))
        assertNull(heartbeat.busy("server", 0))
    }

    @Test fun backwardObservationRefusesUntilHighWaterCatchesUp() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 100)
        assertFalse(heartbeat.observe("server", false, 99))
        assertFalse(heartbeat.observe("server", false, 99))
        assertNull(heartbeat.busy("server", 100))
        assertTrue(heartbeat.observe("server", false, 100))
        assertEquals(false, heartbeat.busy("server", 100))
    }

    @Test fun backwardQueryClearsRatherThanResurrectsFreshIdle() {
        val heartbeat = NativeIdleHeartbeat(100)
        heartbeat.observe("server", false, 100)
        assertEquals(false, heartbeat.busy("server", 150))
        assertNull(heartbeat.busy("server", 149))
        assertNull(heartbeat.busy("server", 150))
        assertFalse(heartbeat.observe("server", false, 149))
        assertTrue(heartbeat.observe("server", false, 150))
        assertEquals(false, heartbeat.busy("server", 150))
    }

    @Test fun expiredIdleCannotBeRecoveredByReadingAnOlderTimestamp() {
        val heartbeat = NativeIdleHeartbeat(100)
        heartbeat.observe("server", false, 0)
        assertNull(heartbeat.busy("server", 100))
        assertNull(heartbeat.busy("server", 99))
        assertNull(heartbeat.busy("server", 100))
    }

    @Test fun clearRetainsTheTimeHighWaterAndCannotReadmitAnOlderSample() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 100)
        heartbeat.busy("server", 200)
        heartbeat.clear()
        assertFalse(heartbeat.observe("server", false, 199))
        assertNull(heartbeat.busy("server", 200))
        assertTrue(heartbeat.observe("server", false, 200))
        assertEquals(false, heartbeat.busy("server", 200))
    }

    @Test fun invalidOwnerObservationStillCannotForgetLaterTimeEvidence() {
        val heartbeat = NativeIdleHeartbeat()
        heartbeat.observe("server", false, 0)
        assertFalse(heartbeat.observe("bad/owner", false, 200))
        assertFalse(heartbeat.observe("server", false, 199))
        assertTrue(heartbeat.observe("server", false, 200))
    }

    @Test fun renewalMovesFreshnessBoundaryOnlyForTheNewCapturedObservation() {
        val heartbeat = NativeIdleHeartbeat(100)
        heartbeat.observe("server", false, 0)
        assertEquals(false, heartbeat.busy("server", 99))
        heartbeat.observe("server", false, 99)
        assertEquals(false, heartbeat.busy("server", 198))
        assertNull(heartbeat.busy("server", 199))
    }

    @Test fun maximumClockAndDurationNeverOverflowIntoOptimisticIdle() {
        val heartbeat = NativeIdleHeartbeat(Long.MAX_VALUE)
        heartbeat.observe("server", false, 0)
        assertEquals(false, heartbeat.busy("server", Long.MAX_VALUE - 1))
        assertNull(heartbeat.busy("server", Long.MAX_VALUE))
        val nearEnd = NativeIdleHeartbeat(10)
        nearEnd.observe("server", true, Long.MAX_VALUE - 9)
        assertEquals(true, nearEnd.busy("server", Long.MAX_VALUE))
        assertFalse(nearEnd.observe("server", false, Long.MIN_VALUE))
        assertNull(nearEnd.busy("server", Long.MAX_VALUE))
    }

    @Test fun freshnessMustBeStrictlyPositive() {
        for (value in listOf(Long.MIN_VALUE, -1L, 0L)) {
            assertThrows(IllegalArgumentException::class.java) { NativeIdleHeartbeat(value) }
        }
        val shortest = NativeIdleHeartbeat(1)
        shortest.observe("server", false, 0)
        assertEquals(false, shortest.busy("server", 0))
        assertNull(shortest.busy("server", 1))
    }
}
