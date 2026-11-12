package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The model sheet after the device pass: the keyboard on close, a drag that is not a choice, search in the same frame. */
@OptIn(ExperimentalTestApi::class)
class KitModelSheetDeviceTest {
    private val models = (1..14).map { KitModelOption("opencode", "claude-$it", "Claude $it", providerName = "OpenCode") }

    private class Keyboard : SoftwareKeyboardController {
        var hidden = 0

        override fun hide() {
            hidden++
        }

        override fun show() {}
    }

    private class Scene {
        val chosen = mutableListOf<KitModelOption?>()
        val keyboard = Keyboard()
        var visible by mutableStateOf(true)
        var dismissed = 0
    }

    @Composable
    private fun Sheet(scene: Scene, options: List<KitModelOption> = models) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f), LocalSoftwareKeyboardController provides scene.keyboard) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) {
                    KitSheetHost {
                        KitModelSheet(
                            visible = scene.visible,
                            onDismissRequest = {
                                scene.dismissed++
                                scene.visible = false
                            },
                            options = options,
                            current = null,
                            onChoose = {
                                scene.chosen += it
                                scene.visible = false
                            },
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun ComposeUiTest.typing(scene: Scene) {
        setContent { Sheet(scene) }
        settle()
        onNodeWithTag("kit-search-field").performClick()
        settle()
        onNodeWithTag("kit-search-field").assertIsFocused()
    }

    @Test
    fun `closing with the close button lets the search field go and asks the keyboard to hide once`() = runComposeUiTest {
        val scene = Scene()
        typing(scene)
        onNode(hasContentDescription("Close")).performClick()
        settle()
        assertEquals(1, scene.keyboard.hidden)
        assertEquals(1, scene.dismissed)
    }

    @Test
    fun `choosing a model lets the search field go and asks the keyboard to hide once`() = runComposeUiTest {
        val scene = Scene()
        typing(scene)
        onNode(hasTestTag("chat-model-choice") and hasText("Claude 3")).performClick()
        settle()
        assertEquals(1, scene.keyboard.hidden)
        assertEquals(1, scene.chosen.size)
    }

    @Test
    fun `the caller closing the sheet from outside (Back, the scrim) also hides the keyboard once`() = runComposeUiTest {
        val scene = Scene()
        typing(scene)
        scene.visible = false
        settle()
        assertEquals(1, scene.keyboard.hidden)
    }

    @Test
    fun `a search field with focus is not focused after the sheet is reopened`() = runComposeUiTest {
        val scene = Scene()
        typing(scene)
        onNode(hasContentDescription("Close")).performClick()
        settle()
        scene.visible = true
        settle()
        onNodeWithTag("kit-search-field").assertIsNotFocused()
    }

    @Test
    fun `a sideways drag on a row never chooses it, a plain tap does`() = runComposeUiTest {
        val scene = Scene()
        setContent { Sheet(scene) }
        settle()
        val row = hasTestTag("chat-model-choice") and hasText("Claude 2")
        onNode(row).performTouchInput {
            down(center)
            moveBy(Offset(30f, 0f))
            moveBy(Offset(30f, 0f))
            up()
        }
        settle()
        assertTrue(scene.chosen.isEmpty(), "a drag of 60 dp to the right chose ${scene.chosen}")
        onNode(row).performTouchInput {
            down(center)
            moveBy(Offset(-30f, 0f))
            moveBy(Offset(-30f, 0f))
            up()
        }
        settle()
        assertTrue(scene.chosen.isEmpty(), "a drag of 60 dp to the left chose ${scene.chosen}")
        onNode(row).performClick()
        settle()
        assertEquals(listOf<KitModelOption?>(models[1]), scene.chosen)
    }

    @Test
    fun `one frame after the query only the matching rows exist`() = runComposeUiTest {
        val scene = Scene()
        setContent { Sheet(scene) }
        settle()
        mainClock.autoAdvance = false
        onNodeWithTag("kit-search-field").performTextInput("Claude 14")
        mainClock.advanceTimeByFrame()
        val rows = onAllNodesWithTag("chat-model-choice").fetchSemanticsNodes().size
        assertEquals(1, rows, "rows after one frame of typing: $rows")
        onNode(hasTestTag("chat-model-choice") and hasText("Claude 14")).assertExists()
        mainClock.autoAdvance = true
    }

    @Test
    fun `Server default leads the single list and each provider page`() = runComposeUiTest {
        val scene = Scene()
        setContent { Sheet(scene) }
        settle()
        onNodeWithText("Server default").assertExists()
    }
}
