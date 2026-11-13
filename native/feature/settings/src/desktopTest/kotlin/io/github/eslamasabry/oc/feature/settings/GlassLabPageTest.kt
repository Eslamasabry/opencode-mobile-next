package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.GlassLabPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.GlassLabSetting
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.ui.GlassLabPage
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Settings › Appearance › Glass lab on screen (2026-11-13): its controls are there and a slider moves the numbers. */
@OptIn(ExperimentalTestApi::class)
class GlassLabPageTest {
    private val canSetProgress = SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    private object Port : GlassLabSetting {
        override val stored: StateFlow<String?> = MutableStateFlow(null)
        override val build: String = "test"

        override fun save(text: String?) = Unit
    }

    @Test
    fun `B-GLASS-LAB-030 the lab shows its controls and a slider moves the glass numbers`() = runComposeUiTest {
        var lab: GlassLabPresenter? = null
        setContent {
            SettingsThemed(dark = true) {
                val scope = rememberCoroutineScope()
                val presenter = lab ?: GlassLabPresenter(scope, Port).also { lab = it }
                val ui by presenter.state.collectAsState()
                GlassLabPage(ui, presenter, onBack = {})
            }
        }
        waitForIdle()
        onNodeWithText("Use in the whole app").assertExists()
        onNodeWithText("Copy values").assertExists()
        onNodeWithText("Paste values").assertExists()
        onNodeWithText("Reset all").assertExists()
        onNodeWithText("Light refraction").assertExists()
        onNodeWithText("Clear liquid", useUnmergedTree = true).assertExists()
        onNodeWithText("Refraction strength").assertExists()
        onNodeWithTag("kit-tuning-drawer-handle").assertExists()
        // The preview is real content: the bar's title and the reply under it.
        onNodeWithText("Run the parser on Ubuntu").assertExists()
        // Linked: the bar's blur moves and every other kind by the same step.
        onNode(hasContentDescription("Blur") and canSetProgress).performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        waitForIdle()
        val t = lab!!.state.value.tuning
        assertEquals(8f, t.knobs(GlassPaneKind.Bar, true).blurDp)
        assertEquals(17f, t.knobs(GlassPaneKind.Menu, true).blurDp)
        // One kind: only the menu moves.
        onNodeWithText("Menu", useUnmergedTree = true).performClick()
        onNode(hasContentDescription("Refraction strength") and canSetProgress).performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        waitForIdle()
        assertEquals(90f, lab!!.state.value.tuning.knobs(GlassPaneKind.Menu, true).lensStrengthDp)
        assertEquals(GlassTuning.shipped(GlassPaneKind.Sheet, true).lensStrengthDp, lab!!.state.value.tuning.knobs(GlassPaneKind.Sheet, true).lensStrengthDp)
    }

    @Test
    fun `B-GLASS-LAB-031 Appearance offers the Glass lab row under Glass in a build that has it`() = runComposeUiTest {
        fun ui(lab: Boolean) =
            SettingsUi(
                stack = persistentListOf(SettingsPage.Appearance),
                appearance =
                    AppearanceUi(
                        packs = listOf(PackId.Opencode).toImmutableList(),
                        style = SurfaceStyle.Glass,
                        glassLab = lab,
                        guide = AppearanceGuideRecord(done = true),
                        question = AppearanceQuestion.Shape,
                        questions =
                            listOf(
                                AppearanceQuestion.Mode,
                                AppearanceQuestion.Theme,
                                AppearanceQuestion.Style,
                                AppearanceQuestion.Shape,
                                AppearanceQuestion.Text,
                                AppearanceQuestion.Motion,
                            ),
                    ),
            )
        val actions = RecordingActions()
        var offered by androidx.compose.runtime.mutableStateOf(true)
        setContent { SettingsThemed { SettingsScreen(ui(offered), actions) } }
        onNodeWithTag("appearance-glass-lab").performScrollTo().assertExists()
        onNodeWithTag("appearance-glass-lab").performClick()
        assertTrue("open:${SettingsPage.GlassLab}" in actions.calls, "${actions.calls}")
        offered = false
        waitForIdle()
        onNodeWithTag("appearance-glass-lab").assertDoesNotExist()
    }
}
