package io.github.eslamasabry.oc.engine.factsbook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

/** F: test/domain/factsbook/reminder_guess_test.dart. 2026-11-08 is a Sunday. */
class ReminderGuessTest {
    private val now = LocalDateTime(2026, 11, 8, 10, 0)

    @Test
    fun `B-FB-083 a day and time in the words fill the check, and the first sentence is the title`() {
        val g = ReminderGuess.from("Dentist on Tuesday at 3:30 pm. Bring the card", now)
        assertEquals("Dentist on Tuesday at 3:30 pm", g.what)
        assertEquals(LocalDateTime(2026, 11, 10, 15, 30), g.at)
        assertEquals(emptyList(), g.ambiguities)
        assertEquals(ReminderRepeat.Never, g.repeat)
    }

    @Test
    fun `B-FB-083 words without a day or time start tomorrow at 09 00 and say what is not known`() {
        val g = ReminderGuess.from("Call the bank", now)
        assertEquals(LocalDateTime(2026, 11, 9, 9, 0), g.at)
        assertEquals(listOf("date", "time"), g.ambiguities)
    }

    @Test
    fun `B-FB-083 today's weekday and next weekday are a week ahead and repeats are read from the words`() {
        assertEquals(LocalDateTime(2026, 11, 15, 9, 0), ReminderGuess.from("Gym sunday", now).at)
        assertEquals(LocalDateTime(2026, 11, 16, 0, 0), ReminderGuess.from("next monday at 12 am lunch", now).at)
        val weekly = ReminderGuess.from("Gym every Monday at 7", now)
        assertEquals(ReminderRepeat.Weekly, weekly.repeat)
        assertEquals(setOf(1), weekly.timing.weekdays)
        assertEquals(ReminderRepeat.Monthly, ReminderGuess.from("Pay rent monthly", now).repeat)
        assertEquals(ReminderRepeat.Daily, ReminderGuess.from("Pills every morning", now).repeat)
        assertEquals(ReminderRepeat.Yearly, ReminderGuess.from("Her birthday every year", now).repeat)
    }
}
