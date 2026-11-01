package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleStopPolicyTest {
    private fun idle(policy: IdleStopPolicy, now: Long) = policy.observe(now, foreground = false, workBusy = false)
    private fun assertCancelled(observation: IdleStopPolicy.Observation) {
        assertNull(observation.deadlineMillis)
        assertFalse(observation.stopDue)
    }

    @Test fun defaultIsDisabledEvenAfterMoreThanFiveIdleMinutes() {
        val policy = IdleStopPolicy()
        assertCancelled(idle(policy, 0))
        assertCancelled(idle(policy, 600_000))
    }

    @Test fun fiveMinuteDeadlineIsDueAtExactBoundaryAndRemainsDue() {
        val policy = IdleStopPolicy(enabled = true)
        assertEquals(IdleStopPolicy.Observation(300_000L, false), idle(policy, 0))
        assertEquals(IdleStopPolicy.Observation(300_000L, false), idle(policy, 299_999))
        assertEquals(IdleStopPolicy.Observation(300_000L, true), idle(policy, 300_000))
        assertEquals(IdleStopPolicy.Observation(300_000L, true), idle(policy, 300_001))
    }

    @Test fun repeatedConfirmedIdleDoesNotPostponeTheDeadline() {
        val policy = IdleStopPolicy(true, 1)
        val deadline = idle(policy, 1_000).deadlineMillis
        for (now in listOf(1_000L, 2_000L, 15_000L, 60_999L)) {
            assertEquals(deadline, idle(policy, now).deadlineMillis)
        }
        assertTrue(idle(policy, 61_000).stopDue)
    }

    @Test fun foregroundCancelsDueStopAndNextBackgroundIntervalStartsFresh() {
        val policy = IdleStopPolicy(true, 1)
        idle(policy, 0)
        assertCancelled(policy.observe(60_000, foreground = true, workBusy = false))
        assertEquals(IdleStopPolicy.Observation(120_001L, false), idle(policy, 60_001))
    }

    @Test fun knownBusyCancelsDueStopAndRequiresANewIdleInterval() {
        val policy = IdleStopPolicy(true, 1)
        idle(policy, 0)
        assertCancelled(policy.observe(60_000, foreground = false, workBusy = true))
        assertEquals(IdleStopPolicy.Observation(120_001L, false), idle(policy, 60_001))
    }

    @Test fun unknownWorkCannotStartOrKeepAnIdleDeadline() {
        val policy = IdleStopPolicy(true, 1)
        assertCancelled(policy.observe(0, foreground = false, workBusy = null))
        idle(policy, 1)
        assertCancelled(policy.observe(60_001, foreground = false, workBusy = null))
        assertEquals(IdleStopPolicy.Observation(120_002L, false), idle(policy, 60_002))
    }

    @Test fun disablingDropsDeadlineAndReenablingRequiresANewInterval() {
        val policy = IdleStopPolicy(true, 1)
        idle(policy, 0)
        policy.configure(false, 1)
        assertCancelled(idle(policy, 60_000))
        policy.configure(true, 1)
        assertEquals(IdleStopPolicy.Observation(120_001L, false), idle(policy, 60_001))
    }

    @Test fun configurationChangeRestartsDeadlineRatherThanReusingPreviousIdle() {
        val policy = IdleStopPolicy(true, 1)
        idle(policy, 0)
        policy.configure(true, 2)
        assertEquals(IdleStopPolicy.Observation(150_000L, false), idle(policy, 30_000))
        assertFalse(idle(policy, 60_000).stopDue)
        assertTrue(idle(policy, 150_000).stopDue)
    }

    @Test fun evenIdenticalConfigurationResetsDeadline() {
        val policy = IdleStopPolicy(true, 1)
        idle(policy, 0)
        policy.configure(true, 1)
        assertEquals(IdleStopPolicy.Observation(120_000L, false), idle(policy, 60_000))
    }

    @Test fun constructorAndConfigurationRejectMinutesOutsideBothBounds() {
        for (minutes in listOf(Int.MIN_VALUE, 0, 61, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { IdleStopPolicy(true, minutes) }
            val policy = IdleStopPolicy(true, 1)
            idle(policy, 0)
            assertThrows(IllegalArgumentException::class.java) { policy.configure(false, minutes) }
            assertEquals(IdleStopPolicy.Observation(60_000L, true), idle(policy, 60_000))
        }
    }

    @Test fun minimumAndMaximumMinutesAreAcceptedWithTheirActualDurations() {
        assertEquals(IdleStopPolicy.Observation(60_000L, false), idle(IdleStopPolicy(true, 1), 0))
        val policy = IdleStopPolicy(true, 60)
        assertEquals(IdleStopPolicy.Observation(3_600_000L, false), idle(policy, 0))
        assertFalse(idle(policy, 3_599_999).stopDue)
        assertTrue(idle(policy, 3_600_000).stopDue)
    }

    @Test fun negativeTimeCannotStartAndCancelsAnExistingDeadline() {
        val policy = IdleStopPolicy(true, 1)
        assertCancelled(idle(policy, -1))
        idle(policy, 0)
        assertCancelled(idle(policy, Long.MIN_VALUE))
        assertEquals(IdleStopPolicy.Observation(60_001L, false), idle(policy, 1))
    }

    @Test fun backwardTimeCancelsAndCannotReadmitUntilTheClockCatchesUp() {
        val policy = IdleStopPolicy(true, 1)
        idle(policy, 100_000)
        assertCancelled(idle(policy, 99_000))
        assertCancelled(idle(policy, 99_999))
        assertEquals(IdleStopPolicy.Observation(160_000L, false), idle(policy, 100_000))
        assertTrue(idle(policy, 160_000).stopDue)
    }

    @Test fun configurationCannotForgetAClockReversal() {
        val policy = IdleStopPolicy(true, 1)
        idle(policy, 100_000)
        policy.configure(true, 1)
        assertCancelled(idle(policy, 99_999))
        assertEquals(IdleStopPolicy.Observation(160_000L, false), idle(policy, 100_000))
    }

    @Test fun overflowingDeadlineRefusesInsteadOfStoppingImmediately() {
        val policy = IdleStopPolicy(true, 1)
        assertCancelled(idle(policy, Long.MAX_VALUE - 59_999L))
        assertCancelled(idle(policy, Long.MAX_VALUE))
    }

    @Test fun largestRepresentableDeadlineStillWaitsUntilItsBoundary() {
        val policy = IdleStopPolicy(true, 1)
        assertEquals(IdleStopPolicy.Observation(Long.MAX_VALUE, false), idle(policy, Long.MAX_VALUE - 60_000L))
        assertFalse(idle(policy, Long.MAX_VALUE - 1L).stopDue)
        assertTrue(idle(policy, Long.MAX_VALUE).stopDue)
    }
}
