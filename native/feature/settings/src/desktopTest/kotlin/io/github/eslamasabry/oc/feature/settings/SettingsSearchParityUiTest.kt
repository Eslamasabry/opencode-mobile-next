package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.settings.presentation.BackgroundState
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.PhraseChipsSetting
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSection
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi25
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi34
import io.github.eslamasabry.oc.ui.words.res.monitorDisclosure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Settings search and deep links on screen, over the real presenter; found by visible text (B-NAV-042/050/059/070). */
@OptIn(ExperimentalTestApi::class)
class SettingsSearchParityUiTest {
    private val saved: Locale = Locale.getDefault()
    private val phrases = Phrases()
    private val notifications = FakeNotifications(NotificationFacts(background = BackgroundState.Running))
    private val openers = RecordingOpeners()
    private lateinit var presenter: SettingsPresenter

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private class Phrases : PhraseChipsSetting {
        private val flow = MutableStateFlow(true)
        override val enabled: StateFlow<Boolean> = flow
        val writes = mutableListOf<Boolean>()

        override suspend fun setEnabled(on: Boolean): Outcome<Unit> {
            writes += on
            flow.value = on
            return Outcome.Ok(Unit)
        }
    }

    @Composable
    private fun Themed(locale: String, width: Int, height: Int, content: @Composable () -> Unit) {
        KitTheme(
            appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
            platform = DesktopKitPlatform(),
            localeTag = locale,
            stillFrame = true,
        ) {
            Box(Modifier.size(width.dp, height.dp).background(LocalKitColors.current.ground)) { content() }
        }
    }

    private fun ComposeUiTest.show(
        locale: String = "en",
        width: Int = 412,
        height: Int = 915,
        facts: SettingsFacts = phoneFacts(),
    ) {
        Locale.setDefault(Locale.forLanguageTag(locale))
        setContent {
            Themed(locale, width, height) {
                val scope = rememberCoroutineScope()
                val p = remember {
                    SettingsPresenter(
                        scope, MutableStateFlow(facts), FakeChoices(), openers,
                        notifications = notifications, phraseChips = phrases,
                    ).also { presenter = it }
                }
                val state by p.state.collectAsState()
                SettingsScreen(state, p)
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.search(text: String) {
        onNodeWithTag("settings-search").performClick()
        onNode(hasSetTextAction()).performTextInput(text)
        waitForIdle()
    }

    private fun ComposeUiTest.tap(text: String) {
        onNodeWithText(text).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `B-NAV-042 typing phrase finds Phrase suggestions, which opens Conversations at its switch unflipped`() =
        runComposeUiTest {
            show()
            search("phrase")
            tap("Phrase suggestions")
            assertEquals(SettingsPage.Group(SettingsGroupId.Conversations), presenter.state.value.page)
            onNodeWithTag("settings-phrase-chips").assertIsDisplayed().onChildren().filterToOne(isToggleable()).assertIsOn()
            assertTrue(phrases.writes.isEmpty())
        }

    @Test
    fun `B-NAV-042 the row reads in Arabic`() =
        runComposeUiTest {
            show(locale = "ar")
            presenter.onOpen(SettingsPage.Group(SettingsGroupId.Conversations)); waitForIdle()
            onNodeWithText("اقتراح العبارات").performScrollTo().assertExists()
        }

    @Test
    fun `B-NAV-050 searching background checks lands on the section where this phone runs them`() =
        runComposeUiTest {
            show(height = 480)
            search("background checks")
            onNodeWithText("Background").assertExists() // the Keep running row
            tap("Background checks")
            assertEquals(SettingsPage.Notifications, presenter.state.value.page)
            onNodeWithTag(SettingsSection.NotificationsBackground.tag).assertIsDisplayed()
            onNodeWithText("Stay connected in the background").assertIsDisplayed()
        }

    @Test
    fun `B-NAV-050 the background line is plain and short`() =
        runComposeUiTest {
            val seen = mutableMapOf<String, String>()
            Locale.setDefault(Locale.ENGLISH)
            setContent {
                Themed("en", 412, 300) {
                    seen["34"] = stringResource(Res.string.e7SettingsUi34)
                    seen["disclosure"] = stringResource(Res.string.monitorDisclosure)
                    seen["25"] = stringResource(Res.string.e7SettingsUi25)
                    KitText(seen.getValue("34"))
                }
            }
            waitForIdle()
            assertEquals("Android stops this after 6 hours a day. The app will tell you when it does.", seen["34"])
            assertFalse(seen.getValue("disclosure").contains("Keep live"))
            assertTrue(seen.getValue("disclosure").contains(seen.getValue("25")))
        }

    @Test
    fun `B-NAV-059 typing quiet finds Quiet hours and opens Notifications at it`() =
        runComposeUiTest {
            show(height = 480)
            search("quiet")
            onNodeWithTag("inside-notifications-quiet").assertTextContains("Notifications") // the page it sits on
            tap("Quiet hours")
            assertEquals(SettingsPage.Notifications, presenter.state.value.page)
            onNodeWithTag(SettingsSection.NotificationsQuiet.tag).assertIsDisplayed()
        }

    @Test
    fun `B-NAV-059 typing text size opens Appearance scrolled to Text size`() =
        runComposeUiTest {
            show(height = 480)
            search("text size")
            tap("Text size")
            assertEquals(SettingsPage.Appearance, presenter.state.value.page)
            onNodeWithTag(SettingsSection.AppearanceTextSize.tag).assertIsDisplayed()
        }

    @Test
    fun `B-NAV-070 a deep link opens the row in Arabic, mirrored`() =
        runComposeUiTest {
            show(locale = "ar", height = 400)
            presenter.onDeepLink("settings-about"); waitForIdle()
            assertEquals(SettingsPage.Group(SettingsGroupId.Help), presenter.state.value.page)
            onNodeWithTag("settings-about").assertIsDisplayed()
            val title = onNodeWithText("حول التطبيق").getBoundsInRoot()
            assertTrue(title.right > 300.dp, "the row's name sits at the right edge in Arabic: $title")
        }

    @Test
    fun `B-NAV-070 a deep link lands on its section at tablet width`() =
        runComposeUiTest {
            show(width = 1280, height = 400)
            presenter.onDeepLink("inside-notifications-background"); waitForIdle()
            assertEquals(SettingsPage.Notifications, presenter.state.value.page)
            onNodeWithTag(SettingsSection.NotificationsBackground.tag).assertIsDisplayed()
            presenter.onBack(); waitForIdle()
            presenter.onDeepLink("settings-language"); waitForIdle()
            onNodeWithTag("settings-language").assertIsDisplayed()
            onNodeWithText("Language").assertIsDisplayed()
        }
}
