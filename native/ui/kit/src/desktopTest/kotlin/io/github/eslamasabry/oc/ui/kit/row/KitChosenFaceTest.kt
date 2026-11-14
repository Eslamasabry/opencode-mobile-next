package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cut audit 2, finding 3: in every picker sheet the chosen row's face ran through the row's own icon and its check
 * (it was inset from the row's box, which on a sheet's rail starts where the icon starts), and the hairline under it
 * touched it. Now the face spans the container (inset space2 from the sheet's edge), with the icon and the check inside
 * it, and no hairline is drawn beside a chosen row. Density 1: px = dp.
 */
@OptIn(ExperimentalTestApi::class)
class KitChosenFaceTest {
    private val width = 412
    private val height = 915

    @Composable
    private fun Themed(style: Style, mode: AppearanceMode = AppearanceMode.Light, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            KitTheme(KitAppearance(mode = mode, style = style, motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(width, height) { content() }
            }
        }
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    /** The server sheet: rows straight on the sheet's rail, the first one chosen or not. */
    private fun ComposeUiTest.serverSheet(style: Style, chosen: Boolean): Pair<PixelMap, androidx.compose.ui.unit.DpRect> {
        setContent {
            Themed(style) {
                KitSheetHost {
                    KitSheet(visible = true, onDismissRequest = {}, title = "Choose the server to search") {
                        KitRow(
                            "This phone",
                            modifier = Modifier.testTag("chosen"),
                            leading = KitRowLeading.Glyph(KitIcons.server, current = chosen),
                            selected = chosen,
                            trailing = KitRowTrailing.Check,
                            onClick = {},
                        )
                        KitRow("Paseo", leading = KitRowLeading.Glyph(KitIcons.server), onClick = {})
                    }
                }
            }
        }
        settle()
        val row = onNodeWithTag("chosen", useUnmergedTree = true).getUnclippedBoundsInRoot()
        return onRoot().captureToImage().toPixelMap() to row
    }

    private fun differs(a: PixelMap, b: PixelMap, x: Int, y: Int): Boolean {
        val p = a[x, y]
        val q = b[x, y]
        return abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue) > 0.01f
    }

    private fun faceSpanOnSheet(style: Style) {
        val (plain, _) = runComposeUiTestFor { serverSheet(style, chosen = false) }
        val (chosen, row) = runComposeUiTestFor { serverSheet(style, chosen = true) }
        // A line through the row's top band, under the face's top inset and above the icon and the words.
        val y = (row.top.value + ChosenFaceRules.TOP_INSET_DP + 3).toInt()
        val xs = (0 until width).filter { differs(plain, chosen, it, y) }
        assertTrue(xs.isNotEmpty(), "$style: the chosen row shows a face")
        val left = xs.first()
        val right = xs.last()
        // The rail is 20 dp: the lead icon starts there and the check ends there. The face holds both with room.
        val rail = 20
        assertTrue(left >= ChosenFaceRules.SIDE_INSET_DP - 1, "$style: the face keeps off the sheet's edge (left $left)")
        assertTrue(left <= rail - 8, "$style: the face starts before the icon, with room (left $left, icon at $rail)")
        assertTrue(right <= width - ChosenFaceRules.SIDE_INSET_DP, "$style: the face keeps off the sheet's end (right $right)")
        assertTrue(right >= width - rail + 8, "$style: the face ends after the check, with room (right $right)")
    }

    private fun <T> runComposeUiTestFor(block: ComposeUiTest.() -> T): T {
        var out: T? = null
        runComposeUiTest { out = block() }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    @Test
    fun `B-ROW-CHOSEN-001 Solid the chosen face holds the row's icon and check inside it`() = faceSpanOnSheet(Style.Solid)

    @Test
    fun `B-ROW-CHOSEN-002 Glass the chosen face holds the row's icon and check inside it`() = faceSpanOnSheet(Style.Glass)

    @Test
    fun `B-ROW-CHOSEN-003 Soft the chosen face is the same shape, never a band to the sheet's edges`() = faceSpanOnSheet(Style.Soft)

    @Test
    fun `B-ROW-CHOSEN-004 no hairline is drawn beside a chosen row in a list`() = runComposeUiTest {
        var ground = androidx.compose.ui.graphics.Color.Unspecified
        setContent {
            Themed(Style.Solid) {
                ground = LocalKitColors.current.ground
                Column(Modifier.fillMaxSize().background(ground)) {
                    KitRowGroup {
                        KitRow("Alpha", Modifier.testTag("r0"), onClick = {})
                        KitRow("Beta", Modifier.testTag("r1"), selected = true, onClick = {})
                        KitRow("Gamma", Modifier.testTag("r2"), onClick = {})
                        KitRow("Delta", Modifier.testTag("r3"), onClick = {})
                    }
                }
            }
        }
        settle()
        val map = onRoot().captureToImage().toPixelMap()
        fun lineAt(tag: String): Boolean {
            val bottom = onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value.toInt()
            val x = width - 40
            return (bottom - 1..bottom + 1).any { y ->
                val p = map[x, y]
                abs(p.red - ground.red) + abs(p.green - ground.green) + abs(p.blue - ground.blue) > 0.01f
            }
        }
        assertTrue(!lineAt("r0"), "no hairline above the chosen row")
        assertTrue(!lineAt("r1"), "no hairline under the chosen row")
        assertTrue(lineAt("r2"), "the hairline between two resting rows stays")
    }
}
