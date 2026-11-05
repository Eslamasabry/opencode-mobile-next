package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalTestApi::class)
class AppearancePageTest {
    private fun appearance(question: AppearanceQuestion? = null, done: Boolean = false) =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Appearance),
            appearance =
                AppearanceUi(
                    packs = listOf(PackId.Opencode, PackId.Nord).toImmutableList(),
                    guide = AppearanceGuideRecord(done = done),
                    question = question,
                    questions =
                        listOf(
                            AppearanceQuestion.Mode,
                            AppearanceQuestion.Theme,
                            AppearanceQuestion.AccentColour,
                            AppearanceQuestion.Style,
                            AppearanceQuestion.Shape,
                            AppearanceQuestion.Text,
                            AppearanceQuestion.Motion,
                        ),
                ),
        )

    @Test
    fun `B-KIT-017 returning visit shows controls without the tour rail`() = runComposeUiTest {
        setContent {
            SettingsThemed { SettingsScreen(appearance(done = true), RecordingActions()) }
        }
        onNodeWithTag("appearance-mode").assertExists()
        onNodeWithTag("guide-item-Colours").assertDoesNotExist()
        onNodeWithText("Start with Colours").assertDoesNotExist()
    }

    @Test
    fun `B-KIT-017 guided theme changes the named pack without showing accent controls`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent {
                SettingsThemed { SettingsScreen(appearance(AppearanceQuestion.Theme), actions) }
            }
            onNodeWithTag("appearance-accent").assertDoesNotExist()
            onNodeWithText("The colours of every page").assertExists()
            onNodeWithTag("appearance-pack-Nord").performClick()
            assertEquals(listOf("pack:Nord"), actions.calls)
            onNodeWithText("Next: Accent").assertExists()
        }

    @Test
    fun `B-KIT-017 Shape shows both knobs without the style grid`() = runComposeUiTest {
        setContent {
            SettingsThemed {
                SettingsScreen(appearance(AppearanceQuestion.Shape), RecordingActions())
            }
        }
        onNodeWithTag("appearance-corners").assertExists()
        onNodeWithTag("appearance-spacing").assertExists()
        onNodeWithTag("appearance-style-Solid").assertDoesNotExist()
    }
}
