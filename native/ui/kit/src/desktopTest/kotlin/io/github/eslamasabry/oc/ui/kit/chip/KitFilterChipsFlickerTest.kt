package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.gate.flicker.FlickerMode
import io.github.eslamasabry.oc.ui.gate.flicker.FrameRecorder
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiOptions
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitLayoutMarks
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.math.roundToInt
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
        runComposeUiTest {
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
        val tag = "w=$widthDp style=$style scale=$fontScale"
        runComposeUiTest {
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
            assertTrue(cuesBefore.isNotEmpty(), "$tag: expected a +N cue")
            assertCuesInside(cuesBefore, slot, tag)
            onAllNodes(hasText("+", substring = true), useUnmergedTree = true).onLast().performClick()
            waitForIdle()
            mainClock.advanceTimeBy(SETTLE_MS * 2)
            waitForIdle()
            val line = onNodeWithTag("kit-filter-chips").fetchSemanticsNode().boundsInRoot
            val revealed =
                onAllNodes(hasText("All connections"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            assertTrue(revealed != null, "$tag: All connections missing after tap")
            val b = revealed.boundsInRoot
            assertTrue(
                b.left >= line.left - HALF_PX && b.right <= line.right + HALF_PX,
                "$tag: All connections $b not whole in $line",
            )
            val cuesAfter = onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
            assertCuesInside(cuesAfter, slot, "$tag after tap")
        }
    }

    @Test
    fun `B-KIT-CHIP-024 after tapping plus cue at 360dp row scrolls to end with edge cues and no single-frame mark`() {
        for (style in listOf(Style.Solid, Style.Glass)) {
            verifyTapScrollAndEdges(style, 1f, listOf("All projects", "All connections", "Running 1"))
            verifyTapScrollAndEdges(style, LARGE_FONT_SCALE, listOf("site", "All connections", "Running 1"))
        }
    }

    private fun verifyTapScrollAndEdges(style: Style, fontScale: Float, labels: List<String>) {
        val tag = "360dp style=$style scale=$fontScale"
        runDesktopComposeUiTest(PHONE_W_PX, PHONE_H_PX) {
            mainClock.autoAdvance = false
            setContent { phoneHeaderRow(style, fontScale, labels) }
            assertBeforeTap(tag)
            assertAfterTap(tag)
            assertAtScrollEnd(tag)
        }
    }

    @androidx.compose.runtime.Composable
    private fun phoneHeaderRow(style: Style, fontScale: Float, labels: List<String>) {
        CompositionLocalProvider(LocalDensity provides Density(PHONE_DENSITY, fontScale)) {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off, style = style),
                platform = FakeKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.fillMaxWidth().background(LocalKitColors.current.ground)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = Space.S4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KitChip(label = "This phone", kind = KitChipKind.Action, onClick = {})
                        Spacer(Modifier.width(Space.S2.dp))
                        Box(Modifier.height(DIVIDER_H_DP.dp)) { KitDivider(vertical = true) }
                        Spacer(Modifier.width(Space.S2.dp))
                        Box(Modifier.weight(1f).testTag("chip-slot")) {
                            KitFilterChips(bleed = true, restWhole = true) {
                                for ((idx, label) in labels.withIndex()) {
                                    KitChip(
                                        label = label,
                                        kind = KitChipKind.Action,
                                        opensMenu = idx < 2,
                                        onClick = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.assertBeforeTap(tag: String) {
        val beforeOneFrame = walkFramesForSingleFrameNodes(WALK_FRAMES)
        assertTrue(beforeOneFrame.isEmpty(), "$tag before tap 1-frame nodes: $beforeOneFrame")
        val slot = onNodeWithTag("chip-slot").fetchSemanticsNode().boundsInRoot
        val cuesBefore = onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(cuesBefore.isNotEmpty(), "$tag before tap: expected +N cue")
        assertCuesInside(cuesBefore, slot, "$tag before tap")
    }

    private fun ComposeUiTest.assertAfterTap(tag: String) {
        onAllNodes(hasText("+", substring = true), useUnmergedTree = true).onLast().performClick()
        val tapOneFrame = walkFramesForSingleFrameNodes(WALK_FRAMES)
        assertTrue(tapOneFrame.isEmpty(), "$tag after tap 1-frame nodes: $tapOneFrame")
        mainClock.advanceTimeBy(SETTLE_MS * 2)
        mainClock.advanceTimeByFrame()

        val lineAfter = onNodeWithTag("kit-filter-chips").fetchSemanticsNode().boundsInRoot
        val mid = onAllNodes(hasText("All connections"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        assertTrue(mid != null, "$tag after tap: All connections missing")
        val mb = mid.boundsInRoot
        assertTrue(
            mb.left >= lineAfter.left - EDGE_PX_SLACK && mb.right <= lineAfter.right + EDGE_PX_SLACK,
            "$tag after tap: All connections $mb not whole in $lineAfter",
        )
        val scrollerAfter = findScroller(onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes())
        val rangeAfter = scrollerAfter.config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(
            rangeAfter.value() > 0f && rangeAfter.value() < rangeAfter.maxValue(),
            "$tag after tap: expected scrollable mid position, got ${rangeAfter.value()}/${rangeAfter.maxValue()}",
        )
        val mapAfter = onRoot().captureToImage().toPixelMap()
        assertEdgeCleared(mapAfter, lineAfter, atStart = true, label = "$tag after tap start")
        assertEdgeCleared(mapAfter, lineAfter, atStart = false, label = "$tag after tap end")
    }

    private fun ComposeUiTest.assertAtScrollEnd(tag: String) {
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(SCROLL_TO_END_PX, 0f) }
        val scrollOneFrame = walkFramesForSingleFrameNodes(WALK_FRAMES)
        assertTrue(scrollOneFrame.isEmpty(), "$tag scroll-end 1-frame nodes: $scrollOneFrame")
        mainClock.advanceTimeBy(SETTLE_MS * 2)
        mainClock.advanceTimeByFrame()

        val cuesAtEnd = onAllNodes(hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(cuesAtEnd.isEmpty(), "$tag at scroll end: +N cue must not pop back in, got ${cuesAtEnd.size}")
        val lineEnd = onNodeWithTag("kit-filter-chips").fetchSemanticsNode().boundsInRoot
        val last = onAllNodes(hasText("Running 1"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        assertTrue(last != null, "$tag at scroll end: Running 1 missing")
        val lb = last.boundsInRoot
        assertTrue(
            lb.left >= lineEnd.left - EDGE_PX_SLACK && lb.right <= lineEnd.right + EDGE_PX_SLACK,
            "$tag at scroll end: Running 1 $lb not whole in $lineEnd",
        )
        assertTrue(
            lineEnd.right - lb.right <= END_TAIL_MAX_PX,
            "$tag at scroll end: Running 1 $lb should sit near right edge of $lineEnd",
        )
        val mapEnd = onRoot().captureToImage().toPixelMap()
        assertEdgeCleared(mapEnd, lineEnd, atStart = true, label = "$tag scroll end start")
    }

    private fun ComposeUiTest.walkFramesForSingleFrameNodes(frames: Int): Map<String, List<Int>> {
        val seen = LinkedHashMap<String, MutableList<Int>>()
        repeat(frames) { frame ->
            mainClock.advanceTimeByFrame()
            val roots = onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
            for (label in FrameRecorder.read(roots).nodes.map { it.label }.distinct()) {
                seen.getOrPut(label) { mutableListOf() }.add(frame)
            }
        }
        return seen.filterValues { it.size == 1 }
    }

    private fun assertEdgeCleared(
        map: PixelMap,
        line: androidx.compose.ui.geometry.Rect,
        atStart: Boolean,
        label: String,
    ) {
        val x = if (atStart) (line.left + EDGE_PROBE_PX).roundToInt() else (line.right - EDGE_PROBE_PX).roundToInt()
        val refY = (line.top + EDGE_REF_Y_PX).roundToInt().coerceIn(0, map.height - 1)
        val midY = ((line.top + line.bottom) / 2f).roundToInt().coerceIn(0, map.height - 1)
        val clampedX = x.coerceIn(0, map.width - 1)
        val ref = map[clampedX, refY]
        val sample = map[clampedX, midY]
        val diff = abs(sample.red - ref.red) + abs(sample.green - ref.green) + abs(sample.blue - ref.blue)
        assertTrue(
            diff < EDGE_CLEAR_DIFF,
            "$label: edge at x=$clampedX not cleared by fade (diff=$diff sample=$sample ref=$ref)",
        )
    }

    private fun assertCuesInside(cues: List<SemanticsNode>, slot: androidx.compose.ui.geometry.Rect, label: String) {
        for (cue in cues) {
            val b = cue.boundsInRoot
            assertTrue(
                b.left >= slot.left - HALF_PX && b.right <= slot.right + HALF_PX,
                "$label: cue $b overflows slot $slot",
            )
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
        const val PHONE_W_PX = 720
        const val PHONE_H_PX = 240
        const val PHONE_DENSITY = 2f
        const val DIVIDER_H_DP = 24
        const val WIDE_DP = 480
        const val MEDIUM_DP = 220
        const val NARROW_DP = 140
        const val NARROW_FLIP_DP = 120
        const val NARROW_LARGE_DP = 180
        const val LARGE_FONT_SCALE = 1.3f
        const val HALF_PX = 0.5f
        const val EDGE_PX_SLACK = 1f
        const val EDGE_PROBE_PX = 4f
        const val EDGE_REF_Y_PX = 2f
        const val EDGE_CLEAR_DIFF = 0.06f
        const val SCROLL_TO_END_PX = 10_000f
        const val END_TAIL_MAX_PX = 36f
        const val WALK_FRAMES = 12
        const val SETTLE_MS = 500L
    }
}
