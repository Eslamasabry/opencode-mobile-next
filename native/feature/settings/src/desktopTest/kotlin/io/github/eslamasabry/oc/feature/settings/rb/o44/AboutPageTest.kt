package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class AboutPageTest {
    @Test
    fun `B-NAV-072 About shows independence and opens the actual bundled document`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent {
                SettingsThemed {
                    SettingsScreen(
                        SettingsUi(
                            stack = persistentListOf(SettingsPage.About),
                            about = AboutFacts("2.1.1", "58"),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("OpenCode Mobile 2.1.1+58").assertExists()
            onNodeWithContentDescription("Copy version").assertExists()
            onNodeWithText("OpenCode Mobile is an independent community project.", substring = true)
                .assertExists()
            onNodeWithText("About this build").assertExists()
            onNodeWithText("Open source").assertExists()
            onNodeWithText("Bundled components").performScrollTo().performClick()
            assertEquals(listOf("open:Notices"), actions.calls)
        }

    @Test
    fun `B-NAV-072 update check holds during work and available update invokes its action`() =
        runComposeUiTest {
            val actions = RecordingActions()
            val state =
                mutableStateOf(
                    SettingsUi(
                        stack = persistentListOf(SettingsPage.About),
                        updates = UpdateFacts("2.1.1", "58"),
                    )
                )
            setContent { SettingsThemed { SettingsScreen(state.value, actions) } }
            onNodeWithText("Check for updates").performClick()
            assertEquals(listOf("check"), actions.calls)
            runOnIdle {
                state.value =
                    state.value.copy(
                        updates = state.value.updates!!.copy(state = UpdateState.Checking)
                    )
            }
            onNodeWithText("Check for updates").assertIsNotEnabled()
            runOnIdle {
                state.value =
                    state.value.copy(
                        updates =
                            state.value.updates!!.copy(state = UpdateState.Available("2.1.2", "20"))
                    )
            }
            onNodeWithTag("about-check-updates").performClick()
            assertEquals(listOf("check", "act"), actions.calls)
        }
}
