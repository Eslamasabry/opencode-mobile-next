package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChange
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalPrefs
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateState
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Settings screens over the real presenter and fake ports, on the desktop JVM; found by visible text. */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenUiTest {
    private val facts = MutableStateFlow(phoneFacts())
    private var choices = FakeChoices()
    private var analytics = FakeAnalytics()
    private val notifications = FakeNotifications()
    private val updates = FakeUpdates(UpdateFacts(current = "3.0.0", build = "10001"))
    private val openers = RecordingOpeners()
    private lateinit var presenter: SettingsPresenter

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.show() {
        setContent {
            SettingsThemed {
                val scope = rememberCoroutineScope()
                val p =
                    remember {
                        SettingsPresenter(scope, facts, choices, openers, analytics, notifications = notifications, updates = updates)
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
    fun `B-NAV-068 the hub shows search, the five groups and the server line`() =
        runComposeUiTest {
            show()
            onNodeWithText("Search settings").assertExists()
            for (group in listOf("Server", "Agent", "Conversations", "This app", "Help")) onNodeWithText(group).assertExists()
            onNodeWithText("laptop · Connected").assertExists()
        }

    @Test
    fun `SCREENS-NAV-91 This app, Appearance, then Calm motion applies at once`() =
        runComposeUiTest {
            show()
            tap("This app")
            tap("Appearance")
            onNodeWithText("Light or dark?").assertExists()
            onNodeWithText("Graphite").assertExists()
            tap("Calm")
            assertEquals(MotionLevel.Calm, choices.value.value.motion)
            onNodeWithText("Drawings appear, nothing keeps moving and nothing celebrates").assertExists()
        }

    @Test
    fun `B-KIT-061 See all lists every pack and a pick applies`() =
        runComposeUiTest {
            show()
            presenter.onOpen(SettingsPage.Appearance); waitForIdle()
            tap("See all 31 themes")
            tap("Coffee")
            assertTrue(choices.writes.any { it is PersonalChange.Pack })
        }

    @Test
    fun `B-KIT-008 in Saver the styles say Saver holds them`() =
        runComposeUiTest {
            choices = FakeChoices(PersonalPrefs(mode = AppearanceMode.Saver))
            show()
            presenter.onOpen(SettingsPage.Appearance); waitForIdle()
            onNodeWithTag("appearance-styles").performScrollTo()
            tap("Brutal")
            assertEquals(emptyList(), choices.writes)
        }

    @Test
    fun `B-NAV-071 typing a typo in search finds Appearance and opens it`() =
        runComposeUiTest {
            show()
            onNodeWithTag("settings-search").performClick()
            onNode(hasSetTextAction()).performTextInput("apperance")
            waitForIdle()
            tap("Appearance")
            assertEquals(SettingsPage.Appearance, presenter.state.value.page)
        }

    @Test
    fun `plan 07 I05 blocked notifications lead with the fix, which opens Android's settings`() =
        runComposeUiTest {
            notifications.flow.value = NotificationFacts(blocked = true)
            show()
            presenter.onOpen(SettingsPage.Notifications); waitForIdle()
            onNodeWithText("Notifications are off for this app").assertExists()
            tap("Turn on notifications in Android")
            assertEquals(listOf(SettingsOutside.AndroidNotificationSettings), openers.opened)
        }

    @Test
    fun `plan 07 I20 an available update offers Update and Check now`() =
        runComposeUiTest {
            updates.flow.value = UpdateFacts("3.0.0", "10001", state = UpdateState.Available("3.1.0", "41"))
            show()
            presenter.onOpen(SettingsPage.Updates); waitForIdle()
            onNodeWithText("3.1.0 is ready · 41 MB").assertExists()
            tap("Update")
            assertEquals(listOf("act"), updates.calls)
        }

    @Test
    fun `B-SEC-191 Privacy says nothing is sent yet and offers no switch`() =
        runComposeUiTest {
            show()
            presenter.onOpen(SettingsPage.Privacy); waitForIdle()
            onNodeWithText("What we collect").assertExists()
            onNodeWithText(PENDING_NOT_SENT).assertExists()
            onNodeWithText("Share detailed usage").assertDoesNotExist()
        }

    @Test
    fun `B-SEC-191 What we collect lists the events and the switch turns detailed usage on`() =
        runComposeUiTest {
            analytics = FakeAnalytics(RemoteSink)
            show()
            presenter.onOpen(SettingsPage.Privacy); waitForIdle()
            tap("What we collect")
            onNodeWithText("Sent to collect.invalid").assertExists()
            onNodeWithText("The app opened, and how fast").performScrollTo().assertExists()
            tap("Share detailed usage")
            assertEquals(listOf(true), analytics.asked)
        }

    private companion object {
        const val PENDING_NOT_SENT = "Not sent yet. Basic reports wait on this phone, at most 90 days, until a service is chosen."
    }
}
