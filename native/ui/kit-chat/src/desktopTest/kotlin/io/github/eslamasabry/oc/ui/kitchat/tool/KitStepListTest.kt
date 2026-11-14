package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.request.KitRequestWords
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeWords
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStep
import io.github.eslamasabry.oc.ui.kit.status.KitTreeStepState
import io.github.eslamasabry.oc.ui.kit.status.STEP_MARK_TAG
import io.github.eslamasabry.oc.ui.kit.status.StepTone
import io.github.eslamasabry.oc.ui.kit.status.StepSeg
import io.github.eslamasabry.oc.ui.kitchat.ChatFakePlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D1 "steps only" (owner 2026-11-14, pictures d1-dark / d1-steps-open): a turn's steps with no summary line. One thin
 * rail meets every step's glyph; each row is the 32 dp step line; older steps fold behind one first row that names what
 * matters inside; a thought carries its own glyph; sub-agents at work together are one row whose children hang on the
 * shared step tree (trunk and branches); Arabic mirrors it; 1.3x text keeps it whole; Brutal and Paper draw the rail in
 * their own ink.
 */
@OptIn(ExperimentalTestApi::class)
class KitStepListTest {
    private fun step(id: String, kind: KitToolKind = KitToolKind.Read, title: String = "Read", subject: String? = "app.js", status: KitToolStatus = KitToolStatus.Done) =
        KitToolView(id, kind, title, subject, null, status, "0.2s", null, null, "Done")

    private val words =
        KitStepTreeWords(
            working = "Working",
            notStarted = "Not started",
            needsYou = "Waiting for you",
            paused = "Paused until you answer",
            denied = "You declined this",
            failed = "Failed",
            moreInside = { "+$it more inside" },
            more = { "+$it more" },
            duration = { "${it}s" },
            stop = { "Stop $it" },
            spoken = { s, said -> "${s.name}, sub-agent, $said" },
            unfold = "Show sub-agents",
            fold = "Hide sub-agents",
            request = KitRequestWords("Allow once", "Deny", "Answer…", { "Allow once: $it" }, { "Deny: $it" }, { "Answer: $it" }),
        )

    private val children =
        listOf(
            KitTreeStep("c1", "Find every file that reads rivers", KitTreeStepState.Done, seconds = 12),
            KitTreeStep("c2", "Compare the old copies", KitTreeStepState.Working),
        )

    /** The page at [density] px per dp, its ground painted so the rail's ink can be read off the picture. */
    private fun ComposeUiTest.host(
        style: Style = Style.Solid,
        rtl: Boolean = false,
        density: Float = 1f,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Dark, style = style, motion = MotionLevel.Off),
                    platform = ChatFakePlatform(),
                    localeTag = if (rtl) "ar" else "en",
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

    private fun ComposeUiTest.nodes(): List<DpRect> {
        val all = onAllNodesWithTag("kit-step-node", useUnmergedTree = true)
        return all.fetchSemanticsNodes().indices.map { all[it].getUnclippedBoundsInRoot() }.sortedBy { it.top.value }
    }

    /** How far the pixel at ([xDp], [yDp]) stands from the black ground, 0..441. */
    private fun ComposeUiTest.inkAt(density: Float): (Float, Float) -> Float {
        val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
        return { x, y ->
            val c = image[(x * density).toInt().coerceIn(0, image.width - 1), (y * density).toInt().coerceIn(0, image.height - 1)]
            sqrt(c.red * c.red + c.green * c.green + c.blue * c.blue) * 255f
        }
    }

    @Test
    fun `D1-LIST-001 the rail runs from each glyph's bottom to the next glyph's top and never through a glyph, both directions`() {
        for (rtl in listOf(false, true)) {
            val density = 3f
            runDesktopComposeUiTest(412 * 3, 700 * 3) {
                host(rtl = rtl, density = density) {
                    KitStepList {
                        KitToolRow(step("a"), opened = false, onToggle = {})
                        KitToolRow(step("b", KitToolKind.Edit, "Edited"), opened = false, onToggle = {})
                        KitToolRow(step("c", KitToolKind.Shell, "Ran", "npm test"), opened = false, onToggle = {})
                    }
                }
                waitForIdle()
                val n = nodes()
                assertEquals(3, n.size, "three glyphs on the rail")
                val ink = inkAt(density)
                val what = if (rtl) "rtl" else "ltr"
                for (i in 0 until 2) {
                    val x = (n[i].left.value + n[i].right.value) / 2f
                    assertTrue(ink(x, n[i].bottom.value + 1f) > LINE_INK, "$what: the rail leaves glyph $i at its bottom")
                    assertTrue(ink(x, (n[i].bottom.value + n[i + 1].top.value) / 2f) > LINE_INK, "$what: the rail between glyph $i and ${i + 1}")
                    assertTrue(ink(x, n[i + 1].top.value - 1f) > LINE_INK, "$what: the rail meets glyph ${i + 1} at its top")
                    assertTrue(ink(x, n[i].bottom.value - 2f) < LINE_INK, "$what: no rail inside glyph $i's box under its icon")
                }
                val last = n.last()
                val x = (last.left.value + last.right.value) / 2f
                assertTrue(ink(x, last.bottom.value + 6f) < LINE_INK, "$what: nothing runs on past the last glyph")
                assertTrue(ink(x, n.first().top.value - 4f) < LINE_INK, "$what: nothing runs above the first glyph")
                // The glyphs sit on the reply's 16 dp edge (the start, the right in Arabic).
                for (r in n) {
                    if (!rtl) assertEquals(16f, r.left.value, 0.6f, "ltr glyph on the 16 dp edge")
                    else assertEquals(412f - 16f, r.right.value, 0.6f, "rtl glyph on the right 16 dp edge")
                }
            }
        }
    }

    @Test
    fun `D1-LIST-002 the rail is the step tree's quiet sequence - straight, glyph to glyph, mirrored`() {
        val spans = listOf(0f..20f, 32f..52f, 64f..84f)
        val ltr = stepListRail(spans, x = 10f, width = 400f, rtl = false, bend = 16f)
        assertEquals(2, ltr.size)
        assertTrue(ltr.all { it.tone == StepTone.Guide }, "never progress")
        assertEquals(10f, ltr[0].from.x)
        assertEquals(20f, ltr[0].from.y)
        assertEquals(32f, (ltr[0].segs.single() as StepSeg.Line).to.y)
        assertEquals(52f, ltr[1].from.y)
        assertEquals(64f, ltr[1].end.y)
        val rtl = stepListRail(spans, x = 10f, width = 400f, rtl = true, bend = 16f)
        assertTrue(rtl.all { it.from.x == 390f && it.end.x == 390f }, "mirrored on the right")
        assertTrue(stepListRail(listOf(0f..20f), 10f, 400f, false, 16f).isEmpty(), "one step has no rail")
    }

    @Test
    fun `D1-LIST-003 every row is one 32 dp line, the fold row too, and a folded group is its row only`() =
        runDesktopComposeUiTest(412, 700) {
            host {
                KitStepList(fold = KitStepFoldLine("Show 5 earlier steps", "1 declined · 1 failed", open = false, onToggle = {}), earlier = { KitToolRow(step("e"), false, {}) }) {
                    KitToolRow(step("a"), opened = false, onToggle = {})
                    KitStepFold("Thought", expanded = false, onToggle = {}, icon = KitIcons.idea) { KitText("words") }
                    KitStepGroup("2 sub-agents", "1 done · 1 working", children, words)
                }
            }
            waitForIdle()
            val fold = onNodeWithTag(STEP_FOLD_ROW_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(32f, (fold.bottom - fold.top).value, 0.6f, "the fold row is one line")
            val line = onAllNodesWithTag("kit-step-line", useUnmergedTree = true)
            assertEquals(1, line.fetchSemanticsNodes().size, "the earlier step is not drawn while folded")
            val l = line[0].getUnclippedBoundsInRoot()
            assertEquals(32f, (l.bottom - l.top).value, 0.6f, "a step is one line")
            val group = onNodeWithTag(STEP_GROUP_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(32f, (group.bottom - group.top).value, 0.6f, "a folded group is its one row")
            // Four glyphs: the fold, the step, the thought (its own glyph, not a dot), the group; one column.
            val n = nodes()
            assertEquals(4, n.size)
            assertTrue(n.all { abs(it.left.value - n[0].left.value) < 0.6f }, "one glyph column: $n")
            for (i in 1 until n.size) assertTrue(n[i].top >= n[i - 1].bottom, "no glyph overlaps the one above")
        }

    @Test
    fun `D1-LIST-004 the fold row names what lies inside and opens to the earlier steps`() =
        runDesktopComposeUiTest(412, 700) {
            var open by mutableStateOf(false)
            host {
                KitStepList(
                    fold = KitStepFoldLine(if (open) "Hide earlier steps" else "Show 2 earlier steps", if (open) null else "1 declined · 1 failed", open, onToggle = { open = !open }),
                    earlier = {
                        KitToolRow(step("e1", status = KitToolStatus.Failed), false, {})
                        KitToolRow(step("e2", KitToolKind.Shell, "Ran", "rm a.txt", KitToolStatus.Stopped), false, {})
                    },
                ) {
                    KitToolRow(step("a"), opened = false, onToggle = {})
                }
            }
            waitForIdle()
            onNodeWithText("Show 2 earlier steps").assertExists()
            onNodeWithText("1 declined · 1 failed").assertExists()
            assertEquals(1, onAllNodesWithTag("kit-step-line", useUnmergedTree = true).fetchSemanticsNodes().size)
            onNodeWithText("Show 2 earlier steps").performClick()
            waitForIdle()
            onNodeWithText("Hide earlier steps").assertExists()
            assertEquals(3, onAllNodesWithTag("kit-step-line", useUnmergedTree = true).fetchSemanticsNodes().size, "opened, every step")
        }

    @Test
    fun `D1-LIST-005 a group's children hang on a trunk with a branch to each, mirrored in Arabic`() {
        for (rtl in listOf(false, true)) {
            val density = 3f
            runDesktopComposeUiTest(412 * 3, 700 * 3) {
                host(rtl = rtl, density = density) {
                    KitStepList {
                        KitToolRow(step("a"), opened = false, onToggle = {})
                        KitStepGroup("2 sub-agents", "1 done · 1 working", children, words, opensItself = true)
                    }
                }
                waitForIdle()
                val what = if (rtl) "rtl" else "ltr"
                val n = nodes()
                assertEquals(2, n.size, "$what: the step and the group on the rail")
                val group = n[1]
                val trunkX = (group.left.value + group.right.value) / 2f
                val marksAll = onAllNodesWithTag(STEP_MARK_TAG, useUnmergedTree = true)
                val marks = marksAll.fetchSemanticsNodes().indices.map { marksAll[it].getUnclippedBoundsInRoot() }
                assertEquals(2, marks.size, "$what: two children")
                // The children's marks stand just past the steps' text column (16 + 20 + 12 + 4 = 52 dp from the start).
                for (m in marks) {
                    if (!rtl) assertEquals(52f, m.left.value, 0.6f, "$what: child mark")
                    else assertEquals(412f - 52f, m.right.value, 0.6f, "$what: child mark")
                }
                val ink = inkAt(density)
                val first = marks.first()
                val firstY = (first.top.value + first.bottom.value) / 2f
                assertTrue(ink(trunkX, (group.bottom.value + firstY) / 2f) > LINE_INK, "$what: the trunk runs down from the group's glyph")
                for (m in marks) {
                    val cy = (m.top.value + m.bottom.value) / 2f
                    val near = if (!rtl) m.left.value else m.right.value
                    val mid = (trunkX + near) / 2f
                    assertTrue(ink(mid, cy) > LINE_INK, "$what: a branch reaches the child at $cy")
                }
            }
        }
    }

    @Test
    fun `D1-LIST-006 at 1_3x text the rows grow, the glyphs keep their column and nothing overlaps`() =
        runDesktopComposeUiTest(412, 900) {
            host(fontScale = 1.3f) {
                KitStepList(fold = KitStepFoldLine("Show 5 earlier steps", "1 declined · 1 failed", open = false, onToggle = {})) {
                    KitToolRow(step("a"), opened = false, onToggle = {})
                    KitToolRow(step("b", KitToolKind.Shell, "Ran", "npm test"), opened = false, onToggle = {})
                    KitStepGroup("2 sub-agents", "1 done · 1 working", children, words)
                }
            }
            waitForIdle()
            onNodeWithText("Show 5 earlier steps").assertExists()
            onNodeWithText("1 declined · 1 failed").assertExists()
            val n = nodes()
            assertEquals(4, n.size)
            assertTrue(n.all { abs(it.left.value - 16f) < 0.6f }, "the glyphs stay on the 16 dp edge: $n")
            for (i in 1 until n.size) assertTrue(n[i].top >= n[i - 1].bottom, "no glyph overlaps the one above at 1.3x")
            val fold = onNodeWithTag(STEP_FOLD_ROW_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue((fold.bottom - fold.top).value >= 32f - 0.5f, "the fold row grows with the words, never shrinks")
        }

    @Test
    fun `D1-LIST-007 Brutal draws the rail in heavier ink than Paper, and both inked rails are stronger than Solid's guide`() {
        val density = 3f
        fun run(style: Style): Pair<Int, Float> {
            var width = 0
            var strongest = 0f
            runDesktopComposeUiTest(412 * 3, 400 * 3) {
                host(style = style, density = density) {
                    KitStepList {
                        KitToolRow(step("a"), opened = false, onToggle = {})
                        KitToolRow(step("b"), opened = false, onToggle = {})
                    }
                }
                waitForIdle()
                val n = nodes()
                val y = (n[0].bottom.value + n[1].top.value) / 2f
                val cx = (n[0].left.value + n[0].right.value) / 2f
                val ink = inkAt(density)
                // Count the inked pixels across the rail, one px at a time.
                var x = cx - 4f
                while (x <= cx + 4f) {
                    val v = ink(x, y)
                    if (v > LINE_INK) width++
                    if (v > strongest) strongest = v
                    x += 1f / density
                }
            }
            return width to strongest
        }
        val solid = run(Style.Solid)
        val brutal = run(Style.Brutal)
        val paper = run(Style.Paper)
        assertTrue(brutal.first > paper.first, "Brutal's 2 dp is wider than Paper's 1 dp: ${brutal.first} vs ${paper.first}")
        assertTrue(brutal.second > solid.second, "Brutal's ink is stronger than Solid's quiet guide: ${brutal.second} vs ${solid.second}")
        assertTrue(paper.second > solid.second, "Paper's rule is ink, stronger than Solid's guide: ${paper.second} vs ${solid.second}")
    }

    private companion object {
        /** A pixel this far from the ground carries the rail's ink. */
        const val LINE_INK = 18f
    }
}
