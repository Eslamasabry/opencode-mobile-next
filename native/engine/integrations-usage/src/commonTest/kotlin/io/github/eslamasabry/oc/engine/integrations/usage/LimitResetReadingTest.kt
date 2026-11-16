package io.github.eslamasabry.oc.engine.integrations.usage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** signin-followups: the reset a limit sentence names, read against when the reply came. */
class LimitResetReadingTest {
    private val said = Instant.parse("2026-11-16T03:00:00Z").toEpochMilliseconds()

    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    @Test
    fun `SIGNIN-C-10 a dated reset in UTC`() {
        assertEquals(at("2026-11-20T01:00:00Z"), LimitResetReading.resetAt("You've hit your weekly limit · resets Nov 20, 1am (UTC)", said))
    }

    @Test
    fun `SIGNIN-C-11 a time with no date is the next such time in its zone`() {
        assertEquals(at("2026-11-16T14:30:00Z"), LimitResetReading.resetAt("You've hit your session limit · resets 3:30pm (Europe/Berlin)", said))
        // Already past in that zone today: tomorrow.
        assertEquals(at("2026-11-17T12:00:00Z"), LimitResetReading.resetAt("resets 12pm (UTC)", at("2026-11-16T13:00:00Z")))
    }

    @Test
    fun `SIGNIN-C-12 a date already gone this year is next year's`() {
        assertEquals(at("2027-01-05T09:00:00Z"), LimitResetReading.resetAt("resets Jan 5, 9am (UTC)", said))
    }

    @Test
    fun `SIGNIN-C-13 older epoch shape`() {
        assertEquals(1_763_600_400_000L, LimitResetReading.resetAt("Claude AI usage limit reached|1763600400", said))
    }

    @Test
    fun `SIGNIN-C-14 no readable reset is null`() {
        assertNull(LimitResetReading.resetAt("You've reached your usage limit", said))
        assertNull(LimitResetReading.resetAt("resets 3pm (Not/AZone)", said))
        assertNull(LimitResetReading.resetAt("resets Feb 31, 3pm (UTC)", said))
        assertNull(LimitResetReading.resetAt("resets 13pm (UTC)", said))
    }
}
