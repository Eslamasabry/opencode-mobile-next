package io.github.eslamasabry.oc.feature.schedules

import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import io.github.eslamasabry.oc.feature.schedules.presentation.EditIssue
import io.github.eslamasabry.oc.feature.schedules.presentation.EditorFields
import io.github.eslamasabry.oc.feature.schedules.presentation.EditorRules
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleRowUi
import io.github.eslamasabry.oc.feature.schedules.ui.ScheduleWords
import io.github.eslamasabry.oc.feature.schedules.ui.moment
import io.github.eslamasabry.oc.feature.schedules.ui.repeatWords
import io.github.eslamasabry.oc.feature.schedules.ui.scheduleLine
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScheduleWordsTest {
    private val t0 = 1_793_606_400_000L // 2026-11-02T08:00:00Z, a Monday
    private val hour = 3_600_000L
    private val en = ScheduleWords.English
    private val ar = ScheduleWords.Arabic

    @Test
    fun `B-L10N-014 schedules read in plain words in English and Arabic, never cron text`() {
        assertEquals("Every day at 09:00", repeatWords(Recurrence.daily(9, 0), t0, en))
        assertEquals("Weekdays at 07:30", repeatWords(Recurrence.weekdays(7, 30), t0, en))
        assertEquals("Every 6 hours", repeatWords(Recurrence.interval(6, t0), t0, en))
        assertEquals("Every hour", repeatWords(Recurrence.interval(1, t0), t0, en))
        assertEquals("كل يوم الساعة 09:00", repeatWords(Recurrence.daily(9, 0), t0, ar))
        assertEquals("كل 6 ساعات", repeatWords(Recurrence.interval(6, t0), t0, ar))
        for (words in listOf(en, ar)) {
            val text = repeatWords(Recurrence.weekdays(9, 0), t0, words)
            assertFalse(text.contains("*") || text.contains("MON-FRI") || text.contains("0 9"))
        }
    }

    @Test
    fun `next run words are relative to today in the device zone`() {
        val utc = TimeZone.UTC
        assertEquals("Today 09:00", moment(t0 + hour, t0, en, utc))
        assertEquals("Tomorrow 09:00", moment(t0 + 25 * hour, t0, en, utc))
        assertEquals("Thu 09:00", moment(t0 + 73 * hour, t0, en, utc))
        assertEquals("2026-11-20 09:00", moment(t0 + 18 * 24 * hour + hour, t0, en, utc))
        val row = ScheduleRowUi("a", "p", Recurrence.daily(9, 0), null, false, true, null)
        assertTrue(scheduleLine(row, t0, en).contains("Paused"))
        assertTrue(scheduleLine(row, t0, en).contains("Continues one conversation"))
    }

    @Test
    fun `B-NAV-055 each scheduled-run failure says what happened and what to do next`() {
        val codes =
            listOf(
                "needs_you", "auth_required", "agent_failed", "timeout", "uncertain_delivery", "interrupted", "busy",
                "stale_target", "wrong_project", "missed", "unsupported", "certificate_invalid", "cleartext_unconfirmed", "unavailable",
            )
        for (words in listOf(en, ar)) {
            for (code in codes) {
                val line = words.why(code)
                assertTrue(line.isNotBlank(), code)
                assertFalse(line.contains("_"), "no raw code in $code")
            }
        }
        assertNotEquals(en.why("needs_you"), en.why("auth_required"))
        assertTrue(en.why("uncertain_delivery").contains("not sent again"))
        assertEquals(RunStatus.Failed, RunStatus.of("failed"))
    }

    @Test
    fun `editor rules turn typed fields into a recurrence or one plain issue`() {
        val utc = TimeZone.UTC
        val base = EditorFields(prompt = "Hi", project = "/w", time = "9:05")
        assertEquals(Recurrence.daily(9, 5), EditorRules.recurrence(base, t0, utc).first)
        assertEquals(EditIssue.ProjectRequired, EditorRules.recurrence(base.copy(project = ""), t0, utc).second)
        assertEquals(EditIssue.TimeInvalid, EditorRules.recurrence(base.copy(time = "25:00"), t0, utc).second)
        assertEquals(EditIssue.DateInvalid, EditorRules.recurrence(base.copy(kind = RecurrenceKind.Once, date = "tomorrow"), t0, utc).second)
        assertEquals(t0 + 24 * hour + 65 * 60_000L, EditorRules.recurrence(base.copy(kind = RecurrenceKind.Once, date = "2026-11-03"), t0, utc).first!!.atMs)
        assertEquals(EditIssue.HoursInvalid, EditorRules.recurrence(base.copy(kind = RecurrenceKind.Interval, hours = "0"), t0, utc).second)
        assertEquals(Recurrence.interval(4, t0 + 65 * 60_000L), EditorRules.recurrence(base.copy(kind = RecurrenceKind.Interval, hours = "4"), t0, utc).first)
    }
}
