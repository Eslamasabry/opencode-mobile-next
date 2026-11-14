@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips
import kotlin.test.Test
import kotlin.test.assertTrue

/** Audit 2026-11-14, the block above the message box: the phrase row folds for Find and while a reply runs, and ends in its fade inside the gutter. */
class KitBottomRowAuditTest {
    private val phrases = listOf("list this folder", "say hi", "create a file c10.txt with one line", "run the tests again")

    @androidx.compose.runtime.Composable
    private fun Chips(away: Boolean = false) =
        KitPhraseChips(phrases, "Your phrases", { it }, { it }, "Hide", {}, {}, Modifier.testTag("chips"), away = away)

    @Test
    fun `B-FRAME-BOTTOM-01 while Find is open the phrase row folds away and comes back when it closes`() = runComposeUiTest {
        var find by mutableStateOf(false)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                Box(Modifier.size(412.dp, 800.dp)) {
                    KitScreen(page = true, overlayChrome = true, bottom = {
                        Column {
                            if (find) {
                                KitFoldPhraseRow(true)
                                KitText("Find row")
                            }
                            Chips()
                            KitText("Message box")
                        }
                    }) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        onNodeWithText("say hi", useUnmergedTree = true).assertExists()
        find = true
        waitForIdle()
        onNodeWithText("say hi", useUnmergedTree = true).assertDoesNotExist()
        find = false
        waitForIdle()
        onNodeWithText("say hi", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-FRAME-BOTTOM-02 the phrase row is away while its host says so (a message sending, a reply running)`() = runComposeUiTest {
        var away by mutableStateOf(true)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") { Chips(away = away) }
        }
        onNodeWithText("say hi", useUnmergedTree = true).assertDoesNotExist()
        away = false
        waitForIdle()
        onNodeWithText("say hi", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `B-FRAME-BOTTOM-03 a phrase row on the block's gutter draws nothing past its end`() = runComposeUiTest {
        // Soft: chips with a shadow, whose room used to widen the line's clip past the block's end by its reach.
        setContent {
            KitTheme(KitAppearance(style = Style.Soft, motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                Box(Modifier.width(360.dp).background(Color.Black)) {
                    Box(Modifier.padding(end = 40.dp).testTag("block")) { Chips() }
                }
            }
        }
        waitForIdle()
        val block = onNodeWithTag("block").getBoundsInRoot()
        val root = onRoot().getBoundsInRoot()
        val map = onRoot().captureToImage().toPixelMap()
        val scale = map.width / (root.right - root.left).value
        val from = ((block.right.value + 1f) * scale).toInt()
        val to = (((block.right + 30.dp).value) * scale).toInt().coerceAtMost(map.width - 1)
        var drawn = 0
        for (y in 0 until map.height) for (x in from..to) {
            val c = map[x, y]
            if (c.red + c.green + c.blue > .06f) drawn++
        }
        assertTrue(drawn == 0, "$drawn pixels of the chips drawn past the block's end into its gutter")
    }
}
