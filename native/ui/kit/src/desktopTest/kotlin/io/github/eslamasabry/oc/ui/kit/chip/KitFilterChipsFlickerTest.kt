package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.FlickerMode
import io.github.eslamasabry.oc.ui.gate.flicker.FrameRecorder
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiOptions
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A rest-whole chip row decides which chips fit and whether a "+N" cue is needed before anything is drawn, so
 * neither a "+N" mark nor any chip appears for a single frame and disappears on wide or narrow widths.
 */
@OptIn(ExperimentalTestApi::class)
class KitFilterChipsFlickerTest {
    private val chips = listOf("All projects", "All connections", "Running")
    private val strict = KitUiOptions(mode = FlickerMode.Fail)

    private fun walkWidth(widthDp: Int, style: Style = Style.Solid, fontScale: Float = 1f) {
        val seenFrames = LinkedHashMap<String, MutableList<Int>>()
        kitUiTest(WINDOW_W, WINDOW_H, options = strict) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    KitTheme(
                        appearance = KitAppearance(motion = MotionLevel.Off, style = style),
                        platform = FakeKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        InWindow(WINDOW_W, WINDOW_H) {
                            Box(Modifier.width(widthDp.dp)) {
                                KitFilterChips(bleed = true, restWhole = true) {
                                    for (label in chips) {
                                        KitChip(
                                            label = label,
                                            kind = KitChipKind.Action,
                                            opensMenu = label == "All projects",
                                            onClick = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            repeat(WALK_FRAMES) { frame ->
                mainClock.advanceTimeByFrame()
                val roots = onAllNodes(isRoot(), useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                val labels = FrameRecorder.read(roots).nodes.map { it.label }.distinct()
                for (label in labels) {
                    seenFrames.getOrPut(label) { mutableListOf() }.add(frame)
                }
            }
            mainClock.autoAdvance = true
            waitForIdle()
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
        }
        val oneFrameNodes = seenFrames.filterValues { it.size == 1 }
        assertTrue(
            oneFrameNodes.isEmpty(),
            "width=${widthDp}dp style=$style scale=$fontScale showed node(s) for 1 frame only: $oneFrameNodes",
        )
    }

    @Test
    fun `B-KIT-CHIP-021 rest-whole chip row never shows a node for one frame on wide or narrow width`() {
        for (widthDp in listOf(WIDE_DP, MEDIUM_DP, NARROW_DP)) {
            walkWidth(widthDp, Style.Solid, 1f)
            walkWidth(widthDp, Style.Glass, 1f)
        }
        walkWidth(NARROW_LARGE_DP, Style.Solid, LARGE_FONT_SCALE)
    }

    private companion object {
        const val WINDOW_W = 520
        const val WINDOW_H = 200
        const val WIDE_DP = 480
        const val MEDIUM_DP = 220
        const val NARROW_DP = 140
        const val NARROW_LARGE_DP = 180
        const val LARGE_FONT_SCALE = 1.3f
        const val WALK_FRAMES = 12
        const val SETTLE_MS = 500L
    }
}
