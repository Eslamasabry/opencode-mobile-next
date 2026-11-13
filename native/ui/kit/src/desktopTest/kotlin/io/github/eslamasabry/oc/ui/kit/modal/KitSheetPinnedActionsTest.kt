package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A sheet with pinned actions (the permission Details sheet is the case that showed it): the two answers sit side by
 * side in the card's order when asked, a short body gives a short sheet, and a body that runs on below the pinned
 * block fades out above it instead of ending in a hard cut.
 */
@OptIn(ExperimentalTestApi::class)
class KitSheetPinnedActionsTest {
    private val red = Color(0xFFFF0000)

    @Composable
    private fun Sheet(
        height: KitSheetHeight,
        answerPair: Boolean,
        fontScale: Float = 1f,
        body: @Composable ColumnScope.() -> Unit,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) {
                    KitSheetHost {
                        KitSheet(
                            visible = true,
                            onDismissRequest = {},
                            title = "Edit c2.txt?",
                            height = height,
                            primary = KitAction("Allow", {}),
                            secondary = KitAction("Deny", {}),
                            answerPair = answerPair,
                            body = body,
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

    private fun ComposeUiTest.box(text: String) = onNodeWithText(text).getUnclippedBoundsInRoot()

    private val short: @Composable ColumnScope.() -> Unit = { KitText("One line") }

    @Test
    fun `answerPair puts Deny left of Allow on one line, equal widths, as the card does`() = runComposeUiTest {
        setContent { Sheet(KitSheetHeight.Half, answerPair = true, body = short) }
        settle()
        val deny = onNodeWithText("Deny").getUnclippedBoundsInRoot()
        val allow = onNodeWithText("Allow").getUnclippedBoundsInRoot()
        assertTrue(deny.left < allow.left, "Deny ${deny.left} should sit left of Allow ${allow.left}")
        assertEquals(deny.top.value, allow.top.value, 1f, "one line")
        assertTrue(deny.right <= allow.left, "side by side, not overlapping")
    }

    @Test
    fun `without answerPair a sheet still stacks Allow above Deny`() = runComposeUiTest {
        setContent { Sheet(KitSheetHeight.Half, answerPair = false, body = short) }
        settle()
        assertTrue(box("Allow").top < box("Deny").top)
    }

    @Test
    fun `answerPair under a large font stacks, Deny above Allow as the card does`() = runComposeUiTest {
        setContent { Sheet(KitSheetHeight.Half, answerPair = true, fontScale = 2f, body = short) }
        settle()
        assertTrue(box("Deny").top < box("Allow").top, "stacked, Deny first")
    }

    @Test
    fun `a Content sheet with a short body is short and a tall body keeps the buttons on screen`() = runComposeUiTest {
        setContent { Sheet(KitSheetHeight.Content, answerPair = true, body = short) }
        settle()
        assertTrue(box("Edit c2.txt?").top.value > 560f, "short body: sheet sits low, not half the screen")
    }

    @Test
    fun `a Content sheet with a tall body scrolls under its cap with the buttons pinned in view`() = runComposeUiTest {
        setContent {
            Sheet(KitSheetHeight.Content, answerPair = true) {
                Box(Modifier.fillMaxWidth().height(3000.dp).testTag("slab"))
            }
        }
        settle()
        assertTrue(box("Allow").bottom.value <= 915f, "Allow is on screen")
        assertTrue(box("Edit c2.txt?").top.value > 0f, "the sheet stays under the cap")
    }

    /** The red channel of the rendered window at [x], [y] (density 1: px = dp). */
    private fun ComposeUiTest.redAt(x: Int, y: Int): Float {
        val map = onRoot().captureToImage().toPixelMap()
        val c = map[x, y]
        // Red over a dark sheet: how much of the slab shows (red minus the other channels).
        return c.red - (c.green + c.blue) / 2f
    }

    @Test
    fun `a body that continues under the pinned buttons fades out above them`() = runComposeUiTest {
        setContent {
            Sheet(KitSheetHeight.Half, answerPair = true) {
                Box(Modifier.fillMaxWidth().height(3000.dp).background(red))
            }
        }
        settle()
        val edge = (box("Allow").top.value - 8f).toInt() // the scrolling body ends 8 dp above the buttons
        assertTrue(redAt(206, edge - 100) > 0.8f, "mid body is whole")
        assertTrue(redAt(206, edge - 2) < 0.3f, "the last rows before the buttons are faded out")
    }

    @Test
    fun `a body that fits is not faded`() = runComposeUiTest {
        setContent {
            Sheet(KitSheetHeight.Half, answerPair = true) {
                Box(Modifier.fillMaxWidth().height(120.dp).background(red).testTag("slab"))
            }
        }
        settle()
        val bottom = onNodeWithTag("slab").getUnclippedBoundsInRoot().bottom.value.toInt()
        assertTrue(redAt(206, bottom - 2) > 0.8f, "nothing hidden, nothing faded")
    }
}
