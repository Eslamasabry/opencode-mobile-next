package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.lamp.K2
import io.github.eslamasabry.oc.ui.kit.lamp.k2Host
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The step tree measured (LAMP-CRAFT §4a-b): 48 dp lines, the 52 / 75 dp columns mirrored in Arabic, the trunk and the
 * branches meeting each mark's ring (read off the picture), "+N more inside", the waiting child in place with its
 * answers, the fold (height only), the live timer once a second, nothing running at rest; five styles, both modes,
 * both directions, 360 and 412 dp, text at 1.0 and 1.3.
 */
@OptIn(ExperimentalTestApi::class)
class KitStepTreeTest {
    private class Scene(steps: List<KitTreeStep>) {
        var steps by mutableStateOf(steps)
        val opened = mutableListOf<Any>()
        val allowed = mutableListOf<Any>()
    }

    /** The conversation's own row as a header: its 24 dp mark at the gutter, its title and the fold chevron. */
    @Composable
    private fun KitStepTreeScope.Header(title: String) {
        Row(Modifier.fillMaxWidth().padding(start = StepTreeDims.GUTTER.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            KitTaskMark(KitTaskState.Working, Modifier.stepTreeRoot(), size = KitMarkSize.Row, progress = 0.4f)
            Box(Modifier.width(StepTreeDims.GAP.dp))
            Column(Modifier.weight(1f)) {
                KitText(title, role = TypeRole.RowTitle)
                if (folded) KitText("$count sub-agents", role = TypeRole.Caption)
            }
            FoldChevron()
        }
    }

    private fun ComposeUiTest.tree(
        scene: Scene,
        kind: KitStepTreeKind = KitStepTreeKind.Parallel,
        style: Style = Style.Solid,
        dark: Boolean = true,
        rtl: Boolean = false,
        width: Int = 412,
        fontScale: Float = 1f,
        density: Float = 1f,
        motion: MotionLevel = MotionLevel.Full,
        platform: FakeKitPlatform = FakeKitPlatform(),
    ) = k2Host(style, dark, rtl, motion, fontScale, density, platform, widthDp = width, heightDp = 900) {
        KitStepTree(
            kind,
            scene.steps,
            K2.steps,
            Modifier.width(width.dp),
            actions = KitStepTreeActions(onOpen = { scene.opened += it.key }, onAllow = { scene.allowed += it.key }),
        ) { Header("Deploy preview to staging") }
    }

    private fun ComposeUiTest.marks(): List<DpRect> =
        onAllNodesWithTag(STEP_MARK_TAG, useUnmergedTree = true).fetchSemanticsNodes().indices.map {
            onAllNodesWithTag(STEP_MARK_TAG, useUnmergedTree = true)[it].getUnclippedBoundsInRoot()
        }

    private fun near(expected: Float, actual: Float, what: String, tol: Float = 0.6f) =
        assertTrue(abs(expected - actual) < tol, "$what: expected $expected, was $actual")

    @Test
    fun `B-LAMP-K2-010 lines are 48 dp, children hang at 52 dp and grandchildren at 75 dp, mirrored in Arabic, every style, width and text size`() {
        for (style in K2.styles) for (dark in listOf(true, false)) for (rtl in listOf(false, true)) for (width in listOf(360, 412)) for (scale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(width, 900) {
                val scene = Scene(K2.deployChildren())
                tree(scene, style = style, dark = dark, rtl = rtl, width = width, fontScale = scale)
                waitForIdle()
                val what = "$style ${if (dark) "dark" else "light"} ${if (rtl) "rtl" else "ltr"} $width ${scale}x"
                val m = marks()
                // assets, build, lint, unit tests, docs, review: two levels shown, the third folded into "+2 more inside".
                assertEquals(6, m.size, "$what: six lines")
                val depths = listOf(1, 1, 2, 2, 1, 1)
                m.zip(depths).forEach { (r, d) ->
                    val indent = if (d == 1) StepTreeDims.CHILD_INDENT else StepTreeDims.GRAND_INDENT
                    near(11f, (r.right - r.left).value, "$what: an 11 dp mark")
                    if (!rtl) near(indent.toFloat(), r.left.value, "$what: depth $d mark from the start edge")
                    else near(width.toFloat() - indent, r.right.value, "$what: depth $d mark from the start (right) edge")
                }
                val rows = onAllNodesWithTag(STEP_ROW_TAG, useUnmergedTree = true)
                for (i in 0 until 6) {
                    val b = rows[i].getUnclippedBoundsInRoot()
                    val h = (b.bottom - b.top).value
                    if (scale == 1f) near(48f, h, "$what: line $i is 48 dp") else assertTrue(h >= 48f - 0.5f, "$what: line $i at least 48 dp at 1.3x")
                }
                onNodeWithText("+2 more inside").assertExists()
            }
        }
    }

    @Test
    fun `B-LAMP-K2-011 the trunk and every branch meet each mark's ring exactly, in both directions`() {
        for (rtl in listOf(false, true)) for (kind in KitStepTreeKind.entries) {
            val density = 3f
            runDesktopComposeUiTest(412 * 3, 900 * 3) {
                val scene = Scene(if (kind == KitStepTreeKind.Parallel) K2.deployChildren() else K2.checkoutSteps)
                tree(scene, kind = kind, rtl = rtl, density = density)
                waitForIdle()
                val image = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
                val ground = image[2, 2]
                fun ink(xDp: Float, yDp: Float): Float {
                    val c = image[(xDp * density).toInt(), (yDp * density).toInt()]
                    return sqrt((c.red - ground.red) * (c.red - ground.red) + (c.green - ground.green) * (c.green - ground.green) + (c.blue - ground.blue) * (c.blue - ground.blue)) * 255f
                }
                val what = "${if (rtl) "rtl" else "ltr"} $kind"
                val radius = 11f * TaskMarkRules.MINI_OUTER_SHARE
                val m = marks()
                for ((i, r) in m.withIndex()) {
                    val cx = (r.left.value + r.right.value) / 2f
                    val cy = (r.top.value + r.bottom.value) / 2f
                    if (kind == KitStepTreeKind.Parallel) {
                        // The branch comes in from the trunk's side along the centre line and ends on the ring.
                        val out = if (!rtl) -1f else 1f
                        assertTrue(ink(cx + out * (radius + 1f), cy) > LINE_INK, "$what mark $i: the branch reaches the ring (1 dp out)")
                        assertTrue(ink(cx + out * (radius + 0.3f), cy) > LINE_INK, "$what mark $i: no gap at the ring")
                    } else {
                        // The trunk comes in from above onto the top of the ring.
                        assertTrue(ink(cx, cy - radius - 1f) > LINE_INK, "$what step $i: the trunk reaches the ring's top (1 dp above)")
                    }
                }
            }
        }
    }

    @Test
    fun `B-LAMP-K2-012 a child that needs you stays in place, paused, with its answers, and Allow once reaches the caller`() =
        runDesktopComposeUiTest(412, 900) {
            val platform = FakeKitPlatform()
            val scene = Scene(K2.deployChildren(review = KitTreeStepState.NeedsYou))
            tree(scene, platform = platform)
            waitForIdle()
            onNodeWithText("Paused until you answer").assertExists()
            onNodeWithText("Asks to run: npm run e2e").assertExists()
            val m = marks()
            assertEquals(6, m.size, "the waiting child keeps its place: last, as it started")
            val paused = onNodeWithText("Paused until you answer").getUnclippedBoundsInRoot()
            assertTrue(paused.top >= m.last().bottom, "its words are under its own line")
            onNode(hasText("Allow once")).performClick()
            assertEquals(listOf<Any>("review"), scene.allowed)
            assertTrue(KitHaptic.Send in platform.haptics, "Allow once plays Send")
        }

    @Test
    fun `B-LAMP-K2-013 when every child is done the block folds itself (height only), and the chevron opens it again`() =
        runDesktopComposeUiTest(412, 900) {
            mainClock.autoAdvance = false
            val scene = Scene(K2.deployChildren())
            tree(scene)
            mainClock.advanceTimeBy(500)
            val open = onNodeWithTag(STEP_TREE_TAG).getUnclippedBoundsInRoot()
            scene.steps = scene.steps.map { it.copy(state = KitTreeStepState.Done, children = emptyList()) }
            mainClock.advanceTimeBy(80)
            val mid = onNodeWithTag(STEP_TREE_TAG).getUnclippedBoundsInRoot()
            mainClock.advanceTimeBy(600)
            val folded = onNodeWithTag(STEP_TREE_TAG).getUnclippedBoundsInRoot()
            assertTrue(mid.bottom < open.bottom && mid.bottom > folded.bottom, "the height moves between: open ${open.bottom}, mid ${mid.bottom}, folded ${folded.bottom}")
            near(open.left.value, folded.left.value, "only the height moves")
            onNodeWithText("4 sub-agents").assertExists()
            onNodeWithTag(STEP_TREE_FOLD_TAG, useUnmergedTree = true).performClick()
            mainClock.advanceTimeBy(600)
            val again = onNodeWithTag(STEP_TREE_TAG).getUnclippedBoundsInRoot()
            assertTrue(again.bottom > folded.bottom, "the chevron opens it again")
        }

    @Test
    fun `B-LAMP-K2-014 the live timer changes its text once a second, and the verb is in the line`() =
        runDesktopComposeUiTest(412, 900) {
            mainClock.autoAdvance = false
            val scene = Scene(K2.deployChildren(live = true))
            tree(scene)
            mainClock.advanceTimeBy(100)
            onNodeWithText("Reading 20s", useUnmergedTree = true).assertExists()
            mainClock.advanceTimeBy(1_000)
            onNodeWithText("Reading 21s", useUnmergedTree = true).assertExists()
            onNodeWithText("Testing 1:14", useUnmergedTree = true).assertExists()
            onNodeWithText("34s", useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-LAMP-K2-015 at rest the tree requests no frames`() {
        for (style in K2.styles) {
            runDesktopComposeUiTest(412, 900) {
                val scene = Scene(K2.deployChildren())
                tree(scene, style = style)
                waitForIdle()
                mainClock.advanceTimeBy(2_000)
                assertFalse(Recomposer.runningRecomposers.value.any { it.hasPendingWork }, "$style: a frame is still requested at rest")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-016 a narrow line drops its fact before its name, and a tap on a child opens the child`() {
        val long = K2.deployChildren().map { if (it.key == "docs") it.copy(name = "documentation for the whole release", fact = "CHANGELOG-and-the-migration-notes.md") else it }
        runDesktopComposeUiTest(360, 900) {
            val scene = Scene(long)
            tree(scene, width = 360)
            waitForIdle()
            onNodeWithText("documentation for the whole release", useUnmergedTree = true).assertExists()
            val fact = onAllNodes(hasText("CHANGELOG-and-the-migration-notes.md"), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(fact.isEmpty() || fact.all { !it.layoutInfo.isPlaced }, "the fact goes first on a narrow line")
            onAllNodesWithTag(STEP_ROW_TAG, useUnmergedTree = true)[0].performClick()
            assertEquals(listOf<Any>("assets"), scene.opened)
        }
    }

    private companion object {
        /** A pixel this far from the ground (0-255 per channel, Euclidean) is part of a line. */
        const val LINE_INK = 20f
    }
}
