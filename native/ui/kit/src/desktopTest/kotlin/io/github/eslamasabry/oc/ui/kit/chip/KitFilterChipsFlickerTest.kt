package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitLayoutMarks
import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun `B-KIT-CHIP-022 rest-whole mark refreshes when width flips between whole and plain scroll`() {
        var widthDp by mutableIntStateOf(MEDIUM_DP)
        kitUiTest(WINDOW_W, WINDOW_H, options = strict) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    KitTheme(
                        appearance = KitAppearance(motion = MotionLevel.Off, style = Style.Solid),
                        platform = FakeKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        InWindow(WINDOW_W, WINDOW_H) {
                            Box(Modifier.width(widthDp.dp).testTag("chip-slot")) {
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
            waitForIdle()
            val scroller = { findScroller(onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()) }
            assertTrue(KitLayoutMarks.RestsWhole in scroller().config, "at ${MEDIUM_DP}dp RestsWhole must be set")
            assertTrue(
                onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                "at ${MEDIUM_DP}dp a +N cue must be shown",
            )

            widthDp = NARROW_FLIP_DP
            waitForIdle()
            assertFalse(
                KitLayoutMarks.RestsWhole in scroller().config,
                "at ${NARROW_FLIP_DP}dp RestsWhole must clear when falling back to plain scroll",
            )
            assertTrue(
                onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
                "at ${NARROW_FLIP_DP}dp no +N cue may remain",
            )

            widthDp = MEDIUM_DP
            waitForIdle()
            assertTrue(KitLayoutMarks.RestsWhole in scroller().config, "back at ${MEDIUM_DP}dp RestsWhole must return")
        }
    }

    @Test
    fun `B-KIT-CHIP-023 plus cue fits within row width across styles and tap brings hidden chip in whole`() {
        for (style in listOf(Style.Solid, Style.Glass)) {
            for (scale in listOf(1f, LARGE_FONT_SCALE)) {
                verifyCueFitsAndTaps(MEDIUM_DP, style, scale)
            }
            for (tightWidth in listOf(180, 190, 200)) {
                verifyCueFitsAndTaps(tightWidth, style, 1f)
            }
        }
    }

    private fun verifyCueFitsAndTaps(widthDp: Int, style: Style, fontScale: Float) {
        kitUiTest(WINDOW_W, WINDOW_H, options = strict) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    KitTheme(
                        appearance = KitAppearance(motion = MotionLevel.Off, style = style),
                        platform = FakeKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        InWindow(WINDOW_W, WINDOW_H) {
                            Box(Modifier.width(widthDp.dp).testTag("chip-slot")) {
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
            waitForIdle()
            val slot = onNodeWithTag("chip-slot").fetchSemanticsNode().boundsInRoot
            val cuesBefore = onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(cuesBefore.isNotEmpty(), "w=$widthDp style=$style scale=$fontScale: expected a +N cue")
            for (cue in cuesBefore) {
                val b = cue.boundsInRoot
                assertTrue(
                    b.left >= slot.left - HALF_PX && b.right <= slot.right + HALF_PX,
                    "w=$widthDp style=$style scale=$fontScale: cue $b overflows slot $slot",
                )
            }
            onAllNodes(hasText("+", substring = true), useUnmergedTree = true).onLast().performClick()
            waitForIdle()
            mainClock.advanceTimeBy(SETTLE_MS * 2)
            waitForIdle()
            val line = onNodeWithTag("kit-filter-chips").fetchSemanticsNode().boundsInRoot
            val revealed =
                onAllNodes(hasText("All connections"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            assertTrue(revealed != null, "w=$widthDp style=$style scale=$fontScale: All connections missing after tap")
            val b = revealed.boundsInRoot
            assertTrue(
                b.left >= line.left - HALF_PX && b.right <= line.right + HALF_PX,
                "w=$widthDp style=$style scale=$fontScale: All connections $b not whole in $line",
            )
            val cuesAfter = onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            for (cue in cuesAfter) {
                val cb = cue.boundsInRoot
                assertTrue(
                    cb.left >= slot.left - HALF_PX && cb.right <= slot.right + HALF_PX,
                    "w=$widthDp style=$style scale=$fontScale after tap: cue $cb overflows slot $slot",
                )
            }
        }
    }

    private fun findScroller(roots: List<SemanticsNode>): SemanticsNode {
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (SemanticsProperties.HorizontalScrollAxisRange in node.config) return node
            queue.addAll(node.children)
        }
        error("Horizontal scroller node not found")
    }

    private companion object {
        const val WINDOW_W = 520
        const val WINDOW_H = 200
        const val WIDE_DP = 480
        const val MEDIUM_DP = 220
        const val NARROW_DP = 140
        const val NARROW_FLIP_DP = 120
        const val NARROW_LARGE_DP = 180
        const val LARGE_FONT_SCALE = 1.3f
        const val HALF_PX = 0.5f
        const val WALK_FRAMES = 12
        const val SETTLE_MS = 500L
    }
}
