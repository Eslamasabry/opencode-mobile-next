package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMore
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceTiles
import io.github.eslamasabry.oc.ui.kit.choice.KitSliderStep
import io.github.eslamasabry.oc.ui.kit.choice.KitStepSlider
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** lib/ui/kit/kit_choice_tiles.dart and kit_step_slider.dart: taps, the busy rule, the "more" line, step targets. */
@OptIn(ExperimentalTestApi::class)
class KitChoiceTilesParityTest {
    @Composable
    private fun themed(content: @Composable () -> Unit) =
        KitTheme(KitAppearance(motion = MotionLevel.Off), DesktopKitPlatform(), stillFrame = true) { content() }

    private val choices = listOf(
        KitChoice("sonnet", "Default", supporting = "Claude Sonnet 5.5"),
        KitChoice("pickle", "Cheapest", supporting = "Big Pickle · Free"),
    )

    @Test
    fun `B-KIT-014 a tap on a tile chooses it and the more line opens the rest`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var more = 0
        setContent { themed { KitChoiceTiles(choices, "sonnet", { calls += it }, more = KitChoiceMore("All models (9)") { more++ }) } }
        onNodeWithContentDescription("Default. Claude Sonnet 5.5").assertIsSelected()
        onNodeWithContentDescription("Cheapest. Big Pickle · Free").performClick()
        onNodeWithText("All models (9)").performClick()
        assertEquals(listOf("pickle"), calls)
        assertEquals(1, more)
    }

    @Test
    fun `B-KIT-014 while a tile is applied every tile and the more line wait`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        var more = 0
        setContent { themed { KitChoiceTiles(choices, "sonnet", { calls += it }, busy = "pickle", more = KitChoiceMore("All models (9)") { more++ }) } }
        onNodeWithContentDescription("Default. Claude Sonnet 5.5").assertIsNotEnabled().performClick()
        onNodeWithText("All models (9)").performClick()
        assertEquals(emptyList(), calls)
        assertEquals(0, more)
        onNodeWithTag("kit-choice-tile-busy", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-KIT-014 a disabled tile leads its line with the reason`() = runComposeUiTest {
        val off = choices.map { it.copy(enabled = KitEnabled.Disabled("Saving the choice…")) }
        setContent { themed { KitChoiceTiles(off, "sonnet", {}) } }
        onNodeWithContentDescription("Cheapest. Saving the choice… · Big Pickle · Free")
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Saving the choice…"))
    }

    @Test
    fun `B-KIT-017 a tap on a step goes straight to it`() = runComposeUiTest {
        val selected = mutableStateOf("a")
        setContent {
            themed {
                KitStepSlider(
                    "Text size",
                    listOf(KitSliderStep("a", "Smallest"), KitSliderStep("b", "Small"), KitSliderStep("c", "Default")),
                    selected.value,
                    { selected.value = it },
                    Modifier.width(340.dp),
                )
            }
        }
        // The track sits between the two 33.6 dp ends; its last step is 24 dp in from the track's end.
        onNodeWithContentDescription("Text size").performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 34.dp.toPx() - 24.dp.toPx(), 24.dp.toPx())) }
        assertEquals("c", selected.value)
    }
}
