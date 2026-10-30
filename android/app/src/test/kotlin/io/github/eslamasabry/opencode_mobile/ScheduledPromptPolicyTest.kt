package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledPromptPolicyTest {
    @Test fun onlyImmediatePromptAlarmUsesExpeditedWorkOnAndroid12OrLater() {
        assertTrue(ScheduledPromptPolicy.expediteAlarm(31, true, 0, false))
        assertTrue(ScheduledPromptPolicy.expediteAlarm(37, true, 0, false))
        assertFalse(ScheduledPromptPolicy.expediteAlarm(30, true, 0, false))
        assertFalse(ScheduledPromptPolicy.expediteAlarm(31, false, 0, false))
        assertFalse(ScheduledPromptPolicy.expediteAlarm(31, true, 1000, false))
        assertFalse(ScheduledPromptPolicy.expediteAlarm(31, true, 0, true))
    }
    @Test fun identityIncludesRevisionAndDueInstant() {
        assertEquals("daily:2:1000", ScheduledPromptPolicy.occurrence("daily", 2, 1000))
        val base = ScheduledPromptPolicy.occurrence("daily", 2, 1000)
        assertFalse(base == ScheduledPromptPolicy.occurrence("daily", 3, 1000))
        assertFalse(base == ScheduledPromptPolicy.occurrence("daily", 2, 2000))
    }
    @Test fun duplicateAlarmAndFallbackCannotClaimTwice() {
        assertTrue(ScheduledPromptPolicy.canClaim(2, 1000, 2, 1000, false))
        assertFalse(ScheduledPromptPolicy.canClaim(2, 1000, 2, 1000, true))
    }
    @Test fun pauseDeleteAndRevisionEditInvalidateOldAlarm() {
        assertFalse(ScheduledPromptPolicy.canClaim(null, null, 2, 1000, false))
        assertFalse(ScheduledPromptPolicy.canClaim(3, 1000, 2, 1000, false))
        assertFalse(ScheduledPromptPolicy.canClaim(2, 2000, 2, 1000, false))
    }
    @Test fun waitingForAnotherRunCannotExtendBudget() {
        assertEquals(480_000L, ScheduledPromptPolicy.remaining(100, 100))
        assertEquals(180_000L, ScheduledPromptPolicy.remaining(100, 300_100))
        assertEquals(0L, ScheduledPromptPolicy.remaining(100, 900_100))
        assertTrue(ScheduledPromptPolicy.DART_BUDGET_MS < ScheduledPromptPolicy.WORKER_BUDGET_MS)
        assertTrue(ScheduledPromptPolicy.WORKER_BUDGET_MS < 600_000L)
    }
    @Test fun weekdaysFollowNewTimezoneAndSkipWeekend() {
        val friday = java.time.Instant.parse("2026-10-16T09:00:00Z").toEpochMilli()
        val next = ScheduledPromptPolicy.nextWeekday(8, 0, friday, java.time.ZoneId.of("Asia/Dubai"))
        assertEquals("2026-10-19T04:00:00Z", java.time.Instant.ofEpochMilli(next).toString())
    }
    @Test fun weekdayWallClockStaysFixedAcrossDaylightSavingBoundary() {
        val friday = java.time.Instant.parse("2026-03-27T09:00:00Z").toEpochMilli()
        val next = ScheduledPromptPolicy.nextWeekday(8, 0, friday, java.time.ZoneId.of("Europe/London"))
        assertEquals("2026-03-30T07:00:00Z", java.time.Instant.ofEpochMilli(next).toString())
    }
    @Test fun dailyRunsOnWeekendsToo() {
        val friday = java.time.Instant.parse("2026-10-16T09:00:00Z").toEpochMilli()
        val next = ScheduledPromptPolicy.nextDaily(8, 0, friday, java.time.ZoneId.of("Asia/Dubai"))
        assertEquals("2026-10-17T04:00:00Z", java.time.Instant.ofEpochMilli(next).toString())
    }
    @Test fun dailyWallClockStaysFixedAcrossDaylightSavingBoundary() {
        // London leaves summer time early on 2026-10-25: 9:00 stays 9:00.
        val evening = java.time.Instant.parse("2026-10-24T18:00:00Z").toEpochMilli()
        val next = ScheduledPromptPolicy.nextDaily(9, 0, evening, java.time.ZoneId.of("Europe/London"))
        assertEquals("2026-10-25T09:00:00Z", java.time.Instant.ofEpochMilli(next).toString())
    }
    @Test fun rolledBackClockNeverAdmitsAnEarlyOccurrence() {
        assertFalse(ScheduledPromptPolicy.isDue(2000, 1000))
        assertTrue(ScheduledPromptPolicy.isDue(2000, 2000))
        assertTrue(ScheduledPromptPolicy.isDue(2000, 3000))
    }
    @Test fun rejectUnsafeRoutingIdentifiers() {
        assertTrue(ScheduledPromptPolicy.validId("profile-01.agents"))
        assertFalse(ScheduledPromptPolicy.validId(""))
        assertFalse(ScheduledPromptPolicy.validId("../profile"))
        assertFalse(ScheduledPromptPolicy.validId("profile:run"))
        assertFalse(ScheduledPromptPolicy.validId("a".repeat(129)))
    }
}
