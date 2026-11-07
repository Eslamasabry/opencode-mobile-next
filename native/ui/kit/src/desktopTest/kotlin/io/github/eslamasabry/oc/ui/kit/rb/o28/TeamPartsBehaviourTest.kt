package io.github.eslamasabry.oc.ui.kit.rb.o28

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.team.KitTeamCard
import io.github.eslamasabry.oc.ui.kit.team.KitTeamItem
import io.github.eslamasabry.oc.ui.kit.team.KitTeamRow
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Order 28: what the team parts do on screen (team_parts.dart at 8302de537), behaviour rather than pixels. */
@OptIn(ExperimentalTestApi::class)
class TeamPartsBehaviourTest {
    private val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun `RB28-B01 a row that opens something runs its tap and draws the progress bar`() = runComposeUiTest {
        var taps = 0
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), platform = FakeKitPlatform()) {
                KitTeamRow("Reviewer", "Working", state = KitTeamState.Running, trailing = "See changes", onPressed = { taps++ }, completed = 2, total = 4)
            }
        }
        onNodeWithText("See changes", useUnmergedTree = true).assertExists()
        onNode(progress, useUnmergedTree = true).assertExists()
        onNodeWithText("Reviewer", useUnmergedTree = true).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `RB28-B02 an out-of-range count draws no progress bar and a plain row has no tap`() = runComposeUiTest {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), platform = FakeKitPlatform()) {
                KitTeamRow("Reviewer", "", completed = 5, total = 4)
            }
        }
        onNode(progress, useUnmergedTree = true).assertDoesNotExist()
        onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertDoesNotExist()
    }

    @Test
    fun `RB28-B03 a stale card hides its actions and a done card shows them`() = runComposeUiTest {
        var state by mutableStateOf(KitTeamState.Stale)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), platform = FakeKitPlatform()) {
                KitTeamCard("Review team", "Finished 5m ago", state, primary = KitAction("Apply fixes", {}))
            }
        }
        onNodeWithText("Apply fixes", useUnmergedTree = true).assertDoesNotExist()
        state = KitTeamState.Done
        waitForIdle()
        onNodeWithText("Apply fixes", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `RB28-B04 a needs-you card with rows draws in every style and both modes`() = runComposeUiTest {
        var style by mutableStateOf(Style.Solid)
        var mode by mutableStateOf(AppearanceMode.Light)
        var opened = 0
        setContent {
            KitTheme(KitAppearance(mode = mode, motion = MotionLevel.Off, style = style), platform = FakeKitPlatform()) {
                KitTeamCard(
                    "Review team",
                    "Needs you",
                    KitTeamState.NeedsYou,
                    summary = "Two findings wait for your choice.",
                    items = listOf(KitTeamItem("Null check", detail = "lib/a.dart", trailing = "Open", onPressed = { opened++ })),
                    secondary = KitAction("Dismiss", {}),
                )
            }
        }
        for (m in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
            for (s in Style.entries) {
                style = s
                mode = m
                waitForIdle()
                onNodeWithText("Needs you", useUnmergedTree = true).assertExists()
            }
        }
        onNodeWithText("Null check", useUnmergedTree = true).performClick()
        assertEquals(1, opened)
    }
}
