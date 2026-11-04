package io.github.eslamasabry.oc.ui.kitchat.turn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Dart `kit_since_test` › durations read in hours and days, never thousands of minutes (owner report, build 2055). */
class KitElapsedTest {
    /** The English strings of `kitSinceAge` and `kitDuration*` (strings_common.xml / strings_kit.xml). */
    private val en =
        object : KitElapsedWords {
            override fun minutes(minutes: Int) =
                when (minutes) {
                    0 -> "less than a minute"
                    1 -> "1 min"
                    else -> "$minutes min"
                }

            override fun hours(hours: Int) = "$hours h"

            override fun hoursMinutes(hours: Int, minutes: Int) = "$hours h $minutes min"

            override fun days(days: Int) = "$days d"

            override fun daysHours(days: Int, hours: Int) = "$days d $hours h"
        }

    @Test
    fun `B-KIT-033 durations read in hours and days, never thousands of minutes`() {
        assertEquals("less than a minute", en.say(Duration.ZERO))
        assertEquals("45 min", en.say(45.minutes))
        assertEquals("1 h", en.say(1.hours))
        assertEquals("3 h 20 min", en.say(3.hours + 20.minutes))
        assertEquals("2 d", en.say(2.days))
        assertEquals("1 d 21 h", en.say(2715.minutes))
        assertEquals("less than a minute", en.say((-5).minutes))
    }
}
