package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.feature.settings.ui.AutomationDurationUnit
import io.github.eslamasabry.oc.feature.settings.ui.AutomationScheduleParser
import io.github.eslamasabry.oc.feature.settings.ui.ParsedCron
import io.github.eslamasabry.oc.feature.settings.ui.ParsedDuration
import io.github.eslamasabry.oc.feature.settings.ui.ParsedWallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutomationScheduleParserTest {
    @Test
    fun `B-AUTO-024 parses common cron shapes into plain-language models`() {
        assertEquals(ParsedCron.EveryMinute, AutomationScheduleParser.parseCron("* * * * *"))
        assertEquals(ParsedCron.EveryNMinutes(5), AutomationScheduleParser.parseCron("*/5 * * * *"))
        assertEquals(ParsedCron.EveryHour, AutomationScheduleParser.parseCron("0 * * * *"))
        assertEquals(ParsedCron.EveryHour, AutomationScheduleParser.parseCron("@hourly"))
        assertEquals(ParsedCron.HourlyAt(15), AutomationScheduleParser.parseCron("15 * * * *"))
        assertEquals(ParsedCron.EveryNHours(4), AutomationScheduleParser.parseCron("0 */4 * * *"))
        assertEquals(ParsedCron.Daily(3, 0), AutomationScheduleParser.parseCron("0 3 * * *"))
        assertEquals(ParsedCron.Daily(0, 0), AutomationScheduleParser.parseCron("@daily"))
        assertEquals(ParsedCron.Weekdays(9, 30), AutomationScheduleParser.parseCron("30 9 * * 1-5"))
        assertEquals(ParsedCron.Weekdays(8, 0), AutomationScheduleParser.parseCron("0 8 * * MON-FRI"))
        assertEquals(ParsedCron.Weekends(10, 0), AutomationScheduleParser.parseCron("0 10 * * 0,6"))
        assertEquals(ParsedCron.Weekends(10, 0), AutomationScheduleParser.parseCron("0 10 * * SAT,SUN"))
        assertEquals(ParsedCron.Weekly(0, 9, 0), AutomationScheduleParser.parseCron("0 9 * * 1"))
        assertEquals(ParsedCron.Weekly(4, 17, 30), AutomationScheduleParser.parseCron("30 17 * * FRI"))
        assertEquals(ParsedCron.Weekly(6, 0, 0), AutomationScheduleParser.parseCron("@weekly"))
        assertEquals(ParsedCron.Monthly(15, 6, 0), AutomationScheduleParser.parseCron("0 6 15 * *"))
        assertEquals(ParsedCron.Monthly(1, 0, 0), AutomationScheduleParser.parseCron("@monthly"))
        assertEquals(ParsedCron.Custom, AutomationScheduleParser.parseCron("0 3 1 6 *"))
        assertEquals(ParsedCron.Custom, AutomationScheduleParser.parseCron("not a cron"))
    }

    @Test
    fun `B-AUTO-024 parses every durations, relative at strings, and wall-clock at strings`() {
        assertEquals(
            ParsedDuration(15, AutomationDurationUnit.Seconds),
            AutomationScheduleParser.parseDuration("15s"),
        )
        assertEquals(
            ParsedDuration(5, AutomationDurationUnit.Minutes),
            AutomationScheduleParser.parseDuration("5 minutes"),
        )
        assertEquals(
            ParsedDuration(1, AutomationDurationUnit.Hours),
            AutomationScheduleParser.parseDuration("1h"),
        )
        assertEquals(
            ParsedDuration(2, AutomationDurationUnit.Days),
            AutomationScheduleParser.parseDuration("2 days"),
        )
        assertEquals(
            ParsedDuration(3, AutomationDurationUnit.Weeks),
            AutomationScheduleParser.parseDuration("3 weeks"),
        )
        assertNull(AutomationScheduleParser.parseDuration("bogus"))

        assertEquals(
            ParsedDuration(10, AutomationDurationUnit.Minutes),
            AutomationScheduleParser.parseRelativeAt("in 10 minutes"),
        )
        assertEquals(
            ParsedDuration(2, AutomationDurationUnit.Hours),
            AutomationScheduleParser.parseRelativeAt("in 2 hours"),
        )
        assertNull(AutomationScheduleParser.parseRelativeAt("2026-11-27T09:00"))

        assertEquals(
            ParsedWallClock("2026-11-27", 9, 0),
            AutomationScheduleParser.parseWallClock("2026-11-27T09:00"),
        )
        assertEquals(
            ParsedWallClock("2026-11-27", 14, 30),
            AutomationScheduleParser.parseWallClock("2026-11-27 14:30:00"),
        )
        assertNull(AutomationScheduleParser.parseWallClock("in 10 minutes"))
    }
}
