package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** E20-S20: the composer's "+" tray copies Flutter's `_ComposerTray` (one row, a word under each, shared fit). */
@OptIn(ExperimentalTestApi::class)
class KitComposerTrayTest {
    private fun actions(picked: MutableList<String> = mutableListOf(), blocked: String? = null) =
        listOf(
            KitComposerTrayAction("Photos", KitIcons.images, { picked += "Photos" }, blocked, "tray-photos"),
            KitComposerTrayAction("Camera", KitIcons.camera, { picked += "Camera" }, blocked, "tray-camera"),
            KitComposerTrayAction("File", KitIcons.attach, { picked += "File" }, blocked, "tray-file"),
            KitComposerTrayAction("More", KitIcons.more, { picked += "More" }, testTag = "tray-more"),
        )

    @Test
    fun `E20-S20 one fit factor shrinks every word only when the widest does not fit`() {
        assertEquals(1f, kitTrayLabelFit(widest = 40f, room = 60f))
        assertEquals(1f, kitTrayLabelFit(widest = 0f, room = 60f))
        assertEquals(0.5f, kitTrayLabelFit(widest = 120f, room = 60f))
    }

    @Test
    fun `E20-S20 the tray is one row of four round buttons, each with its word, and a pick runs`() = runComposeUiTest {
        val picked = mutableListOf<String>()
        setContent {
            MotionTheme(level = MotionLevel.Off) {
                Box(Modifier.width(360.dp)) { KitComposerTray(open = true, actions = actions(picked)) }
            }
        }
        val tops = listOf("tray-photos", "tray-camera", "tray-file", "tray-more").map {
            onNodeWithTag(it).getBoundsInRoot().top
        }
        assertEquals(1, tops.distinct().size, "all four on one row: $tops")
        onNodeWithText("Camera").performClick()
        assertEquals(listOf("Camera"), picked)
    }

    @Test
    fun `E20-S20 at large text on a narrow row the words shrink together and none is cut`() = runComposeUiTest {
        // 200 dp leaves each word 42 dp: "Camera" at the frame cap (1.3x) is wider, so without the shared fit it
        // would end in "…".
        setContent {
            MotionTheme(level = MotionLevel.Off) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(Modifier.width(200.dp)) { KitComposerTray(open = true, actions = actions()) }
                }
            }
        }
        val sizes =
            listOf("Photos", "Camera", "File", "More").map { word ->
                val layout = textLayout(word)
                assertFalse(layout.isLineEllipsized(0), "$word is whole")
                assertFalse(layout.hasVisualOverflow, "$word fits")
                layout.layoutInput.style.fontSize
            }
        assertEquals(1, sizes.distinct().size, "every word shares one size: $sizes")
    }

    @Test
    fun `E20-S20 a blocked button is off and says why, More stays on`() = runComposeUiTest {
        val picked = mutableListOf<String>()
        setContent {
            MotionTheme(level = MotionLevel.Off) {
                Box(Modifier.width(360.dp)) {
                    KitComposerTray(
                        open = true,
                        actions = actions(picked, blocked = "Available when the current run finishes"),
                    )
                }
            }
        }
        onNodeWithTag("tray-photos").assertIsNotEnabled()
        onNodeWithTag("tray-more").performClick()
        assertEquals(listOf("More"), picked)
    }

    @Test
    fun `E20-S20 the tray folds away when closed and the plus turns into a cross`() = runComposeUiTest {
        var open by mutableStateOf(true)
        setContent {
            MotionTheme(level = MotionLevel.Off) {
                Box(Modifier.width(360.dp)) {
                    KitComposerTray(open = open, actions = actions())
                    KitComposerAdd(label = if (open) "Close" else "Attach and more", onClick = { open = !open }, open = open)
                }
            }
        }
        onNodeWithTag("kit-composer-tray").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.InvisibleToUser))
        onNodeWithTag("kit-composer-add-open", useUnmergedTree = true).assertExists()
        open = false
        waitForIdle()
        onNodeWithTag("kit-composer-tray").assertDoesNotExist()
        onNodeWithTag("kit-composer-add", useUnmergedTree = true).assertExists()
    }

    private fun SemanticsNodeInteractionsProvider.textLayout(word: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithText(word, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action!!
            .invoke(results)
        return results.single()
    }
}
