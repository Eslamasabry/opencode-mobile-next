package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import io.github.eslamasabry.oc.ui.kit.row.KIT_CONTAINER_GAP_DP
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitStackScope
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cut audit 2. Finding 21: the Mode and Motion controls showed one lone hairline between two resting segments, on one
 * side of the chosen one only, moving with the choice: the track draws no seams. Finding 17: the Glass look control
 * touched the Glass lab card under it (about 1 px): a segmented control reports its span to its stack, so the card
 * under it keeps the kit's container gap. Density 1: px = dp.
 */
@OptIn(ExperimentalTestApi::class)
class KitSegmentedSeamGapTest {
    @Composable
    private fun Themed(style: Style = Style.Solid, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            KitTheme(KitAppearance(mode = AppearanceMode.Light, style = style, motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                InWindow(412, 915) { content() }
            }
        }
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private val modes = listOf(
        KitSegment("auto", "Auto", KitIcons.check),
        KitSegment("light", "Light", KitIcons.check),
        KitSegment("dark", "Dark", KitIcons.check),
        KitSegment("saver", "Saver", KitIcons.check),
    )

    @Test
    fun `B-SEG-SEAM-001 no lone hairline between two resting segments beside the chosen one`() = runComposeUiTest {
        setContent {
            Themed {
                Column(Modifier.fillMaxWidth()) {
                    KitSegmented(modes, "light", "Mode", Modifier.testTag("seg"), tall = true, onChange = {})
                }
            }
        }
        settle()
        val seg = onNodeWithTag("seg").getUnclippedBoundsInRoot()
        val map = onRoot().captureToImage().toPixelMap()
        // A line near the track's top, across Dark and Saver (Light chosen): the old seam stood at their meeting edge.
        val y = (seg.top.value + 4f).toInt()
        val from = (seg.left.value + seg.width.value * 0.55f).toInt()
        val to = (seg.left.value + seg.width.value * 0.9f).toInt()
        val first = map[from, y]
        val odd = (from..to).filter { x ->
            val p = map[x, y]
            abs(p.red - first.red) + abs(p.green - first.green) + abs(p.blue - first.blue) > 0.03f
        }
        assertTrue(odd.isEmpty(), "the track between Dark and Saver is one even surface (odd pixels at $odd)")
    }

    @Test
    fun `B-SEG-GAP-001 a card set right under a segmented control keeps the container gap`() = runComposeUiTest {
        setContent {
            Themed(Style.Glass) {
                KitStackScope {
                    Column(Modifier.fillMaxWidth()) {
                        KitSegmented(modes.take(3), "light", "Glass look", Modifier.testTag("seg"), onChange = {})
                        KitRowGroup {
                            KitRow("Glass lab", Modifier.testTag("lab"), supporting = "Tune every glass number", onClick = {})
                        }
                    }
                }
            }
        }
        settle()
        val seg = onNodeWithTag("seg").getUnclippedBoundsInRoot()
        val lab = onNodeWithTag("lab", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val gap = lab.top.value - seg.bottom.value
        assertTrue(gap >= KIT_CONTAINER_GAP_DP - 1f, "the card keeps the container gap under the control (gap $gap)")
    }
}
