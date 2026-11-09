package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
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
    fun `B-KIT-017 the Colours tab holds mode, theme and accent, and the tour leads to the next tab`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent {
                SettingsThemed { SettingsScreen(appearance(AppearanceQuestion.Theme), actions) }
            }
            // Owner 2026-11-10: one page with tabs; the tour is the same page with a bar at its foot.
            onNodeWithTag("appearance-tabs").assertExists()
            onNodeWithTag("appearance-preview").assertExists()
            onNodeWithTag("appearance-mode").assertExists()
            onNodeWithTag("appearance-accent").assertExists()
            onNodeWithTag("appearance-pack-Nord").performScrollTo().performClick()
            assertEquals(listOf("pack:Nord"), actions.calls)
            onNodeWithText("Next: Style").assertExists()
            onNodeWithText("1 of 4").assertExists()
        }

    @Test
    fun `B-KIT-017 text size slider calls the existing named preference action`() =
        runComposeUiTest {
            val sizes = mutableListOf<AppTextSize>()
            val actions =
                object : SettingsActions by RecordingActions() {
                    override fun onTextSize(size: AppTextSize) {
                        sizes += size
                    }
                }
            setContent {
                SettingsThemed { SettingsScreen(appearance(AppearanceQuestion.Text), actions) }
            }
            onNodeWithTag("appearance-text-size").performSemanticsAction(
                SemanticsActions.SetProgress
            ) {
                it(AppTextSize.Large.ordinal.toFloat())
            }
            assertEquals(listOf(AppTextSize.Large), sizes)
            onNodeWithText("Applies to the whole app, on top of your phone's text size.")
                .assertExists()
        }

    @Test
    fun `B-KIT-017 a question asked for opens on its tab, and Motion selects the named preference`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent {
                SettingsThemed { SettingsScreen(appearance(AppearanceQuestion.Motion), actions) }
            }
            // Text & motion is the last tab: the mode control of the first tab is not on it.
            onNodeWithTag("appearance-mode").assertDoesNotExist()
            onNodeWithTag("appearance-text-size").assertExists()
            onNodeWithTag("appearance-motion").assertExists()
            onNodeWithText("Calm").performScrollTo().performClick()
            assertEquals(listOf("motion:Calm"), actions.calls)
        }

    @Test
    fun `B-KIT-017 the Style tab holds the style cards and both shape knobs`() = runComposeUiTest {
        setContent {
            SettingsThemed {
                SettingsScreen(appearance(AppearanceQuestion.Shape), RecordingActions())
            }
        }
        onNodeWithTag("appearance-style-Solid").assertExists()
        onNodeWithTag("appearance-corners").assertExists()
        onNodeWithTag("appearance-spacing").assertExists()
        onNodeWithTag("appearance-mode").assertDoesNotExist()
    }
}
