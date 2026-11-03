package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Settings search over the real presenter and screen (B-NAV-058, B-NAV-059): what is typed finds the rows that exist
 * here, and tapping a result opens it — a page in place or the place outside Settings. Found by visible text and tags.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsSearchOpenUiTest {
    private val openers = RecordingOpeners()
    private lateinit var presenter: SettingsPresenter

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.show(facts: SettingsFacts) {
        setContent {
            SettingsThemed {
                val scope = rememberCoroutineScope()
                val p = remember { SettingsPresenter(scope, MutableStateFlow(facts), FakeChoices(), openers, FakeAnalytics()).also { presenter = it } }
                val state by p.state.collectAsState()
                SettingsScreen(state, p)
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.search(query: String) {
        onNodeWithTag("settings-search").performClick()
        onNode(hasSetTextAction()).performTextInput(query)
        waitForIdle()
    }

    @Test
    fun `B-NAV-059 typing a setting's name finds it and tapping the result opens its page`() =
        runComposeUiTest {
            show(phoneFacts())
            search("notif")
            onNodeWithText("Notifications").performScrollTo().performClick()
            waitForIdle()
            assertEquals(SettingsPage.Notifications, presenter.state.value.page)
        }

    @Test
    fun `B-NAV-059 a result that lives outside Settings is handed to the place that opens it`() =
        runComposeUiTest {
            show(phoneFacts())
            search("servers")
            onNodeWithTag("settings-servers").performScrollTo().performClick()
            waitForIdle()
            assertEquals(listOf(SettingsOutside.Servers), openers.opened)
        }

    @Test
    fun `B-NAV-058 on the phone with in-app Linux, This phone is found exactly once`() =
        runComposeUiTest {
            show(phoneFacts())
            search("termux")
            assertEquals(1, onAllNodesWithTag("settings-this-phone").fetchSemanticsNodes().size)
        }

    @Test
    fun `B-NAV-058 without in-app Linux the phone-only result is absent and the search says nothing matches`() =
        runComposeUiTest {
            show(SettingsFacts(platform = desktop, server = phoneFacts().server))
            search("termux")
            assertEquals(0, onAllNodesWithTag("settings-this-phone").fetchSemanticsNodes().size)
            onNodeWithText("Nothing matches termux").assertExists()
        }

    @Test
    fun `B-NAV-058 server tools are not found while no server can serve them`() =
        runComposeUiTest {
            show(phoneFacts(server = null))
            search("provider")
            assertEquals(0, onAllNodesWithTag("settings-providers").fetchSemanticsNodes().size)
        }
}
