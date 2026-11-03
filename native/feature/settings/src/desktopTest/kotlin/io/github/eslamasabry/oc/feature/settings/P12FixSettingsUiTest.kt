package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalPrefs
import io.github.eslamasabry.oc.feature.settings.presentation.QuietHours
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** P12-FIX-SETTINGS screens over the real presenter, found by visible text (Codex review settings #2–#5). */
@OptIn(ExperimentalTestApi::class)
class P12FixSettingsUiTest {
    private lateinit var presenter: SettingsPresenter
    private val notifications = FakeNotifications()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.show(choices: FakeChoices = FakeChoices(), facts: SettingsFacts = phoneFacts()) {
        setContent {
            SettingsThemed {
                val scope = rememberCoroutineScope()
                val p = remember {
                    SettingsPresenter(scope, MutableStateFlow(facts), choices, RecordingOpeners(), notifications = notifications)
                        .also { presenter = it }
                }
                val state by p.state.collectAsState()
                SettingsScreen(state, p)
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.tap(text: String) {
        onNodeWithText(text).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `B-L10N-008 the Language page lists every shipped language and shows a saved Spanish as chosen`() =
        runComposeUiTest {
            val choices = FakeChoices(PersonalPrefs(language = AppLanguage.Spanish))
            show(choices)
            presenter.onOpen(SettingsPage.Language); waitForIdle()
            listOf("Use system language", "English", "العربية", "Español", "日本語", "Português (Brasil)", "Русский", "简体中文")
                .forEach { onNodeWithText(it).performScrollTo().assertExists() }
            tap("Use system language")
            assertEquals(AppLanguage.System, choices.value.value.language)
        }

    @Test
    fun `I05 Quiet hours is a switch, and when on its start opens a time list and a tap saves it`() =
        runComposeUiTest {
            show()
            presenter.onOpen(SettingsPage.Notifications); waitForIdle()
            onNodeWithText("Quiet hours start").assertDoesNotExist()
            tap("Quiet hours")
            onNodeWithText("22:00").performScrollTo().assertExists()
            onNodeWithText("08:00").performScrollTo().assertExists()
            tap("Quiet hours start")
            tap("23:30")
            assertEquals(QuietHours(23 * 60 + 30, 8 * 60), notifications.flow.value.quietHours)
            onNodeWithText("23:30").performScrollTo().assertExists() // back on Notifications, the new start shown
            tap("Quiet hours")
            assertNull(notifications.flow.value.quietHours)
        }

    @Test
    fun `02 3_7 Appearance has no Style choice where the app does not draw styles`() =
        runComposeUiTest {
            show(facts = phoneFacts().copy(surfaceStyles = false))
            presenter.onOpen(SettingsPage.Appearance); waitForIdle()
            onNodeWithText("Brutal").assertDoesNotExist()
            onNodeWithText("Paper").assertDoesNotExist()
        }
}
