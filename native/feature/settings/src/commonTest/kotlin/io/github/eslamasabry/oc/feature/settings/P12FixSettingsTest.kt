package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.ErrorShape
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.LogEntry
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.settings.prefs.DevicePreferences
import io.github.eslamasabry.oc.engine.settings.prefs.LocaleChoice
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.HandledError
import io.github.eslamasabry.oc.feature.settings.presentation.HandledErrors
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationKeys
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChange
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalPrefs
import io.github.eslamasabry.oc.feature.settings.presentation.PreferenceMapping
import io.github.eslamasabry.oc.feature.settings.presentation.QuietHours
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P12-FIX-SETTINGS: the Codex second-eye findings on Settings, each from its catalog row, over the real presenter. */
@OptIn(ExperimentalCoroutinesApi::class)
class P12FixSettingsTest {
    private class Rig(val scope: TestScope, facts: SettingsFacts = phoneFacts(), prefs: PersonalPrefs = PersonalPrefs()) {
        val choices = FakeChoices(prefs)
        val notifications = FakeNotifications()
        val p = SettingsPresenter(scope.backgroundScope, MutableStateFlow(facts), choices, RecordingOpeners(), notifications = notifications)

        fun settle() = scope.runCurrent()
    }

    private fun rig(facts: SettingsFacts = phoneFacts(), prefs: PersonalPrefs = PersonalPrefs(), body: suspend Rig.() -> Unit) =
        runTest { Rig(this, facts, prefs).apply { settle() }.body() }

    // ---- #3 language ------------------------------------------------------------------------------------------

    @Test
    fun `B-L10N-008 every stored language reads as itself, never as System, and the picker lists every shipped one`() {
        for (choice in LocaleChoice.entries) {
            val language = PreferenceMapping.personal(DevicePreferences(locale = choice)).language
            assertEquals(choice.code, language.tag, "$choice")
        }
        assertEquals(listOf(null, "en", "ar", "es", "ja", "pt", "ru", "zh"), AppLanguage.entries.map { it.tag })
    }

    @Test
    fun `B-L10N-001 a migrated Spanish override can be cleared with Use system language`() =
        rig(prefs = PersonalPrefs(language = AppLanguage.Spanish)) {
            assertEquals(AppLanguage.Spanish, p.state.value.language)
            p.onLanguage(AppLanguage.System); settle()
            assertEquals(listOf<PersonalChange>(PersonalChange.Language(AppLanguage.System)), choices.writes)
            assertEquals(AppLanguage.System, p.state.value.language)
        }

    // ---- #4 quiet hours ---------------------------------------------------------------------------------------

    @Test
    fun `B-MIGR-005 saved quiet hours read back from the Flutter keys, and anything else reads Off`() {
        assertEquals(QuietHours(22 * 60, 8 * 60), NotificationKeys.quiet(1320, 480))
        assertNull(NotificationKeys.quiet(1320, null))
        assertNull(NotificationKeys.quiet(-1, 480))
        assertNull(NotificationKeys.quiet(1320, 1440))
        assertEquals("oc.notify.quietStart", NotificationKeys.QUIET_START)
        assertEquals("oc.notify.quietEnd", NotificationKeys.QUIET_END)
    }

    @Test
    fun `I05 quiet hours turn on at 2200-0800, their start and end change, and they turn off`() =
        rig {
            p.onOpen(SettingsPage.Notifications)
            p.onQuietHours(true); settle()
            assertEquals(QuietHours.Default, p.state.value.notifications?.quietHours)
            assertEquals(QuietHours(22 * 60, 8 * 60), QuietHours.Default)

            p.onOpen(SettingsPage.QuietTime(start = true))
            p.onQuietTime(start = true, minutes = 23 * 60 + 30); settle()
            assertEquals(QuietHours(23 * 60 + 30, 8 * 60), p.state.value.notifications?.quietHours)
            assertEquals(SettingsPage.Notifications, p.state.value.page, "a time choice returns to Notifications")

            p.onOpen(SettingsPage.QuietTime(start = false))
            p.onQuietTime(start = false, minutes = 23 * 60 + 30); settle()
            assertTrue(p.state.value.notifications!!.quietHours!!.allDay, "same start and end mean quiet all day")

            p.onQuietHours(false); settle()
            assertNull(p.state.value.notifications?.quietHours)
        }

    @Test
    fun `I05 the time list offers every half hour and keeps a saved time between them`() {
        val times = QuietHours.choices(22 * 60 + 15)
        assertEquals(49, times.size)
        assertTrue(22 * 60 + 15 in times)
        assertEquals(times.sorted(), times)
        assertEquals(48, QuietHours.choices(8 * 60).size)
    }

    // ---- #8 permission refresh --------------------------------------------------------------------------------

    @Test
    fun `I05 Android's notification permission is read again on opening the page and on returning to it`() =
        rig {
            p.onOpen(SettingsPage.Notifications); settle()
            assertFalse(p.state.value.notifications!!.blocked)
            notifications.androidBlocked = true // turned off in Android's settings meanwhile
            p.onResume(); settle()
            assertTrue(p.state.value.notifications!!.blocked, "the blocked notice and its fix appear on return")
            notifications.androidBlocked = false
            p.onResume(); settle()
            assertFalse(p.state.value.notifications!!.blocked)
        }

    @Test
    fun `I05 resuming elsewhere in Settings does not touch Android's permission`() =
        rig {
            p.onResume()
            assertEquals(0, notifications.refreshes)
        }

    // ---- #2 and #5: a choice must change something ------------------------------------------------------------

    @Test
    fun `02 3_7 Style is not offered where the app does not draw surface styles`() {
        runTest {
            val drawn = Rig(this)
            drawn.settle()
            assertTrue(drawn.p.state.value.appearance.styles)
            val notDrawn = Rig(this, phoneFacts().copy(surfaceStyles = false))
            notDrawn.settle()
            assertFalse(notDrawn.p.state.value.appearance.styles)
        }
    }

    @Test
    fun `I10 Show reasoning and Show timestamps exist only where the conversation follows them`() {
        val follows = SettingsStructure.rows(SettingsGroupId.Conversations, phoneFacts(), reasoning = false, timestamps = false)
        assertTrue(follows.any { it.id == SettingsRowId.ShowReasoning } && follows.any { it.id == SettingsRowId.ShowTimestamps })
        val ignores = phoneFacts().copy(transcriptDisplay = false)
        val rows = SettingsStructure.rows(SettingsGroupId.Conversations, ignores, reasoning = false, timestamps = false)
        assertFalse(rows.any { it.id == SettingsRowId.ShowReasoning || it.id == SettingsRowId.ShowTimestamps })
        assertFalse(SettingsRowId.ShowReasoning in SettingsStructure.searchable(ignores), "absent rows stay out of search")
    }

    // ---- #6 Report a problem has a producer -------------------------------------------------------------------

    @Test
    fun `I18 warnings and errors the app logs become handled errors, info lines do not`() {
        assertNull(HandledErrors.fromLog(LogEntry(Severity.Info, "App", "graph ready", null)))
        assertEquals(HandledError("Requests", "a reply was not accepted"), HandledErrors.fromLog(LogEntry(Severity.Warn, "Requests", "a reply was not accepted", null)))
        assertEquals(
            HandledError("scope", "uncaught failure in scope (IllegalStateException)"),
            HandledErrors.fromLog(LogEntry(Severity.Error, "scope", "uncaught failure in scope", ErrorShape("IllegalStateException", emptyList()))),
        )
    }

    @Test
    fun `I18 a connection that stops or drops is kept once, by its failure kind, and recovery is not an error`() {
        val stopped = StreamStatus.Stopped(Failure.SignIn(SignInWhy.entries.first()))
        assertEquals(HandledError("connection", "connection stopped: SignIn"), HandledErrors.fromStream(StreamStatus.Connected, stopped))
        assertNull(HandledErrors.fromStream(stopped, stopped))
        val lost = StreamStatus.Disconnected(attempt = 6, nextAttemptAtMs = null)
        assertEquals(HandledError("connection", "connection lost after 6 tries"), HandledErrors.fromStream(StreamStatus.Reconnecting(5, null), lost))
        assertNull(HandledErrors.fromStream(lost, StreamStatus.Disconnected(7, null)))
        assertNull(HandledErrors.fromStream(lost, StreamStatus.Connected))
        assertNull(HandledErrors.fromStream(null, StreamStatus.Reconnecting(1, null)))
    }

}
