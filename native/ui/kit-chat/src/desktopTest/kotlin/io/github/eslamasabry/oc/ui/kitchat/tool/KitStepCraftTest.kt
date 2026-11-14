package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kitchat.ChatFakePlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-11-14 on the steps of a long turn: "clipping flashing", "when collapsed it takes a huge space". A folded
 * run of steps is exactly one row; it opens in place on the same rail with its chevron; while a fold moves no row is
 * cut at full ink by its edge (a row the edge crosses is faint); a step that arrives grows in, so the rows below move
 * smoothly and never jump; a step that finishes keeps its row and is never faded in again.
 */
@OptIn(ExperimentalTestApi::class)
class KitStepCraftTest {
    private fun view(id: String, title: String, status: KitToolStatus = KitToolStatus.Done, kind: KitToolKind = KitToolKind.Shell) =
        KitToolView(id, kind, title, null, null, status, "1.2s", null, null, if (status == KitToolStatus.Running) "Running" else "Done")

    private fun ComposeUiTest.host(motion: MotionLevel = MotionLevel.Off, content: @Composable () -> Unit) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Dark, motion = motion),
                    platform = ChatFakePlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                        Box(Modifier.size(412.dp, 700.dp).background(Color.Black)) {
                            Box(Modifier.padding(horizontal = 16.dp)) { content() }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `STEPS-CRAFT-020 a folded run is its one row with its chevron, and the next row starts right under it`() =
        runDesktopComposeUiTest(412, 700) {
            host {
                KitStepList {
                    KitStepItem {
                        KitToolRow(view("r", "Ran 6 commands"), opened = false, onToggle = {}, modifier = Modifier.testTag("run"), group = true)
                        for (i in 0 until 6) KitStepItem(visible = false, order = i) { KitToolRow(view("c$i", "Ran tests"), false, {}) }
                    }
                    KitStepItem { KitToolRow(view("n", "Edited player.dart", kind = KitToolKind.Edit), false, {}, modifier = Modifier.testTag("next")) }
                }
            }
            waitForIdle()
            val run = onNodeWithTag("run", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val next = onNodeWithTag("next", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(32f, (run.bottom - run.top).value, 0.6f, "a folded run is one 32 dp row: $run")
            assertEquals(run.bottom.value, next.top.value, 0.6f, "the next row starts right under it, no kept height")
            onNodeWithTag(STEP_RUN_CHEVRON_TAG, useUnmergedTree = true).assertExists()
            assertEquals(2, onAllNodesWithTag("kit-step-node", useUnmergedTree = true).fetchSemanticsNodes().size, "folded steps are not drawn")
        }

    @Test
    fun `STEPS-CRAFT-021 a run opens in place, its steps on the same rail column as every row`() =
        runDesktopComposeUiTest(412, 700) {
            var open by mutableStateOf(false)
            host {
                KitStepList {
                    KitStepItem {
                        KitToolRow(view("r", "Ran 3 commands"), opened = open, onToggle = { open = !open }, modifier = Modifier.testTag("run"), group = true)
                        for (i in 0 until 3) KitStepItem(visible = open, order = i) { KitToolRow(view("c$i", "Ran tests"), false, {}) }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("run", useUnmergedTree = true).performClick()
            waitForIdle()
            val all = onAllNodesWithTag("kit-step-node", useUnmergedTree = true)
            val nodes = all.fetchSemanticsNodes().indices.map { all[it].getUnclippedBoundsInRoot() }.sortedBy { it.top.value }
            assertEquals(4, nodes.size, "the run and its three steps")
            assertTrue(nodes.all { abs(it.left.value - nodes[0].left.value) < 0.6f }, "one glyph column: $nodes")
            for (i in 1 until nodes.size) assertTrue(nodes[i].top >= nodes[i - 1].bottom, "rows stack, none overlaps")
        }

    /** The colour of row [i] (rows are told apart by hue, so adjacent rows never merge). */
    /** The rail's glyph column (the kit's large icon size). */
    private val KitIconSizeLarge = io.github.eslamasabry.oc.ui.kit.icon.KitIconSize.Large.dp

    private val hues = listOf(Color(1f, 0f, 0f), Color(0f, 1f, 0f), Color(0f, 0f, 1f), Color(1f, 1f, 0f))

    @Test
    fun `STEPS-CRAFT-022 at 0, 25, 50, 75 and 100 percent of the fold's motion no row is cut at full ink`() =
        runDesktopComposeUiTest(412, 400) {
            var open by mutableStateOf(false)
            mainClock.autoAdvance = false
            host(motion = MotionLevel.Full) {
                KitStepList {
                    for (i in hues.indices)
                        key(i) {
                            KitStepItem(visible = open, order = i) {
                                RailNode(box = 32.dp, top = 0.dp, node = { Box(Modifier.size(20.dp).background(Color.White)) }) {
                                    Box(Modifier.fillMaxWidth().height(32.dp).background(hues[i]))
                                }
                            }
                        }
                }
            }
            mainClock.advanceTimeByFrame()
            open = true
            val total = MotionTokens.STANDARD + STEP_STAGGER_MS * (hues.size - 1)
            var at = 0L
            for (share in listOf(0f, .25f, .5f, .75f, 1f)) {
                val target = (total * share).toLong() + if (share >= 1f) 64 else 0
                mainClock.advanceTimeBy(maxOf(16L, target - at))
                at = maxOf(at + 16, target)
                val rows = rowsOnScreen()
                for ((i, seen) in rows) {
                    val (height, ink) = seen
                    // A row's words and glyph sit in its middle 20 dp: a row cut by 4 dp or more is cut through them, and
                    // must be faint there, never at full ink.
                    if (height in 1..28) assertTrue(ink < 0.9f, "$share: row $i is cut to $height dp at ink $ink")
                }
                if (share >= 1f) {
                    assertEquals(hues.indices.toSet(), rows.keys, "every row shows at the end")
                    for ((i, seen) in rows) assertTrue(seen.first >= 31 && seen.second > 0.97f, "row $i settles whole: $seen")
                }
            }
        }

    @Test
    fun `STEPS-CRAFT-024 while the fold opens a row travels with its glyph, never words without their node`() =
        runDesktopComposeUiTest(412, 400) {
            var open by mutableStateOf(false)
            mainClock.autoAdvance = false
            host(motion = MotionLevel.Full) {
                KitStepList {
                    for (i in hues.indices)
                        key(i) {
                            KitStepItem(visible = open, order = i) {
                                RailNode(box = 32.dp, top = 0.dp, node = { Box(Modifier.size(20.dp).background(Color.White)) }) {
                                    Box(Modifier.fillMaxWidth().height(32.dp).background(hues[i]))
                                }
                            }
                        }
                }
            }
            mainClock.advanceTimeByFrame()
            open = true
            val total = MotionTokens.STANDARD + STEP_STAGGER_MS * (hues.size - 1)
            var at = 0L
            var seenFaint = 0
            for (share in listOf(.25f, .5f, .75f, 1f)) {
                val target = (total * share).toLong() + if (share >= 1f) 64 else 0
                mainClock.advanceTimeBy(maxOf(16L, target - at))
                at = maxOf(at + 16, target)
                val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
                // The node column: the list's start (16) plus half the rail's glyph column.
                val nodeX = 16 + KitIconSizeLarge / 2
                val spans = HashMap<Int, IntRange>()
                for (y in 0 until image.height) {
                    val c = image[200, y]
                    val strongest = maxOf(c.red, c.green, c.blue)
                    if (strongest < 0.3f) continue
                    val i = hues.indices.minByOrNull { h -> abs(hues[h].red - c.red / strongest) + abs(hues[h].green - c.green / strongest) + abs(hues[h].blue - c.blue / strongest) } ?: continue
                    val r = spans[i]
                    spans[i] = if (r == null) y..y else minOf(r.first, y)..maxOf(r.last, y)
                }
                for ((i, ys) in spans) {
                    val words = ys.maxOf { y -> image[200, y].let { maxOf(it.red, it.green, it.blue) } }
                    val node = ys.maxOf { y -> image[nodeX, y].let { minOf(it.red, it.green, it.blue) } }
                    if (words < 0.97f) seenFaint++
                    assertTrue(node >= words * 0.8f, "$share: row $i shows its words at $words but its glyph at $node")
                }
            }
            assertTrue(seenFaint > 0, "the frames caught rows part-way in")
        }

    /** Each coloured row's visible height (px = dp here) and its strongest ink (0..1), read down the content column. */
    private fun ComposeUiTest.rowsOnScreen(): Map<Int, Pair<Int, Float>> {
        val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
        val x = 200
        val heights = HashMap<Int, Int>()
        val inks = HashMap<Int, Float>()
        for (y in 0 until image.height) {
            val c = image[x, y]
            val strongest = maxOf(c.red, c.green, c.blue)
            if (strongest < 0.04f) continue
            val r = c.red / strongest
            val g = c.green / strongest
            val b = c.blue / strongest
            val i = hues.indices.minByOrNull { h -> abs(hues[h].red - r) + abs(hues[h].green - g) + abs(hues[h].blue - b) } ?: continue
            heights[i] = (heights[i] ?: 0) + 1
            inks[i] = maxOf(inks[i] ?: 0f, strongest)
        }
        return heights.keys.associateWith { (heights[it] ?: 0) to (inks[it] ?: 0f) }
    }

    @Test
    fun `STEPS-CRAFT-023 a step that arrives grows in and the list below it never jumps, a step that finishes keeps its row`() =
        runDesktopComposeUiTest(412, 400) {
            var count by mutableIntStateOf(1)
            var status by mutableStateOf(KitToolStatus.Running)
            mainClock.autoAdvance = false
            host(motion = MotionLevel.Full) {
                KitStepList(Modifier.testTag("list")) {
                    for (i in 0 until count)
                        key("s$i") {
                            KitStepItem {
                                KitToolRow(
                                    view("s$i", if (i == 0) "Ran tests" else "Edited player.dart", if (i == 0) status else KitToolStatus.Running),
                                    opened = false,
                                    onToggle = {},
                                    modifier = Modifier.testTag("row$i"),
                                )
                            }
                        }
                }
            }
            mainClock.advanceTimeBy(400)
            val first = onNodeWithTag("row0", useUnmergedTree = true).fetchSemanticsNode().id
            // The step finishes: its row is the same node, and it is not faded in again.
            status = KitToolStatus.Done
            mainClock.advanceTimeByFrame()
            assertEquals(first, onNodeWithTag("row0", useUnmergedTree = true).fetchSemanticsNode().id, "the finished step keeps its row")
            val h0 = onNodeWithTag("list", useUnmergedTree = true).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
            // A new step arrives: the list grows a little each frame, never by a whole row at once.
            count = 2
            var last = h0
            var frames = 0
            while (frames < 30) {
                mainClock.advanceTimeByFrame()
                val h = onNodeWithTag("list", useUnmergedTree = true).getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
                assertTrue(h - last < 24f, "frame $frames: the list jumped from $last to $h")
                assertTrue(h >= last - 0.5f, "frame $frames: the list never shrinks while a step arrives")
                last = h
                frames++
            }
            assertEquals(h0 + 32f, last, 0.6f, "the arrived step is one whole row at the end")
            assertEquals(first, onNodeWithTag("row0", useUnmergedTree = true).fetchSemanticsNode().id, "the row above kept its node")
        }

    @Test
    fun `STEPS-CRAFT-025 a run's own steps stand one step in, the rail bending in from the run and back out`() =
        runDesktopComposeUiTest(412, 700) {
            host {
                KitStepList {
                    KitStepItem {
                        val run = view("r", "Ran 2 commands")
                        KitToolRow(run, opened = true, onToggle = {}, modifier = Modifier.testTag("run"), group = true)
                        for (i in 0 until 2)
                            KitStepItem(visible = true, order = i, nested = true) {
                                KitToolRow(view("c$i", "Ran tests"), false, {}, modifier = Modifier.testTag("c$i"))
                            }
                    }
                    KitStepItem {
                        val next = view("n", "Edited player.dart", kind = KitToolKind.Edit)
                        KitToolRow(next, false, {}, modifier = Modifier.testTag("next"))
                    }
                }
            }
            waitForIdle()
            val all = onAllNodesWithTag("kit-step-node", useUnmergedTree = true)
            val nodes =
                all.fetchSemanticsNodes().indices.map { all[it].getUnclippedBoundsInRoot() }.sortedBy { it.top.value }
            assertEquals(4, nodes.size, "the run, its two steps and the next row")
            val inset = STEP_RUN_INSET.toFloat()
            assertEquals(nodes[0].left.value + inset, nodes[1].left.value, 0.6f, "the run's first step stands in")
            assertEquals(nodes[1].left.value, nodes[2].left.value, 0.6f, "its steps share their own column")
            assertEquals(nodes[0].left.value, nodes[3].left.value, 0.6f, "the next row is back on the rail")
            val run = onNodeWithTag("run", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val c0 = onNodeWithTag("c0", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(run.left.value + inset, c0.left.value, 0.6f, "and so do its words")
            assertEquals(run.right.value, c0.right.value, 0.6f, "its time keeps the rows' one end edge")
            // The rail: from the run's node it bends to the steps' column, runs down it, and bends back out.
            val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
            fun ink(x: Float, y: Float): Float {
                val c = image[x.toInt().coerceIn(0, image.width - 1), y.toInt().coerceIn(0, image.height - 1)]
                return maxOf(c.red, c.green, c.blue) * 255f
            }
            fun across(y: Float, from: Float, to: Float): Boolean =
                (from.toInt()..to.toInt()).any { ink(it.toFloat(), y) > LINE_INK_CRAFT }
            fun mid(a: Int, b: Int) = (nodes[a].bottom.value + nodes[b].top.value) / 2f
            val x0 = (nodes[0].left.value + nodes[0].right.value) / 2f
            val x1 = (nodes[1].left.value + nodes[1].right.value) / 2f
            assertTrue(across(mid(0, 1), x0 - 1f, x1 + 1f), "the rail bends from the run to its steps")
            assertTrue(ink(x1, mid(1, 2)) > LINE_INK_CRAFT, "the steps' own column has the rail")
            assertTrue(across(mid(2, 3), x0 - 1f, x1 + 1f), "it bends back out to the next row")
            assertTrue(ink(x0, mid(1, 2)) < LINE_INK_CRAFT, "no second rail runs past the steps")
        }

    @Test
    fun `STEPS-CRAFT-026 a running step's glyph keeps clear of the light's outline, a still one fills its node`() =
        runDesktopComposeUiTest(412, 700) {
            host {
                KitStepList {
                    KitToolRow(view("a", "Searched files", kind = KitToolKind.Search), false, {})
                    KitToolRow(view("b", "Searching files", KitToolStatus.Running, KitToolKind.Search), false, {})
                }
            }
            waitForIdle()
            val glyphs = onAllNodesWithTag(STEP_GLYPH_TAG, useUnmergedTree = true)
            val sizes =
                glyphs.fetchSemanticsNodes().indices
                    .map { glyphs[it].getUnclippedBoundsInRoot() }
                    .sortedBy { it.top.value }
                    .map { (it.right - it.left).value }
            assertEquals(2, sizes.size)
            assertEquals(22f, sizes[0], 0.6f, "a still glyph is drawn at 22 dp in its 24 dp node")
            assertEquals(18f, sizes[1], 0.6f, "in the light's outline 18 dp: a magnifier's handle clears the corner")
        }

    private companion object {
        /** A pixel this strong on the black ground is ink (the rail is the guide's quiet ink). */
        const val LINE_INK_CRAFT = 30f
    }
}
