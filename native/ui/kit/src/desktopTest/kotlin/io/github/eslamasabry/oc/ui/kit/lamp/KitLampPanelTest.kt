package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.action.STOP_BUTTON_TAG
import io.github.eslamasabry.oc.ui.kit.request.KitHeld
import io.github.eslamasabry.oc.ui.kit.row.REORDER_HANDLE_TAG
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The edge light's list measured (LAMP-PLAN §5, §7 K2 tests; LAMP-CRAFT §4-5): opens at the bottom with the top cut on a
 * group boundary, Edit's pin · rename · hide and strip, Stop naming its target with its held receipt, the footer's
 * short label, TalkBack Move up / Move down, the 600 dp cap, nothing running at rest; five styles, both modes, both
 * directions, 360 and 412 dp, text at 1.0 and 1.3.
 */
@OptIn(ExperimentalTestApi::class)
class KitLampPanelTest {
    private class Scene(model: KitLampPanelModel) {
        var model by mutableStateOf(model)
        val events = mutableListOf<String>()
    }

    private fun ComposeUiTest.panel(
        scene: Scene,
        style: Style = Style.Solid,
        dark: Boolean = true,
        rtl: Boolean = false,
        width: Int = 412,
        height: Int = 800,
        fontScale: Float = 1f,
        platform: FakeKitPlatform = FakeKitPlatform(),
    ) = k2Host(style, dark, rtl, fontScale = fontScale, platform = platform, widthDp = width, heightDp = height) {
        KitLampPanel(
            scene.model,
            K2.panel,
            KitLampPanelActions(
                onOpen = { scene.events += "open:$it" },
                onStop = { scene.events += "stop:$it" },
                onEdit = { scene.model = scene.model.copy(editing = it) },
                onPin = { k, on -> scene.events += "pin:$k:$on" },
                onRename = { scene.events += "rename:$it" },
                onHide = { scene.events += "hide:$it" },
                onShowAgain = { scene.events += "show:$it" },
                onReorderPinned = { a, b -> scene.events += "move:$a>$b" },
                onAll = { scene.events += "all" },
                onNew = { scene.events += "new" },
                onMove = { scene.events += "place" },
                onTurnOff = { scene.events += "off" },
            ),
        )
    }

    private fun near(expected: Float, actual: Float, what: String, tol: Float = 0.6f) =
        assertTrue(abs(expected - actual) < tol, "$what: expected $expected, was $actual")

    private fun DpRect.w() = (right - left).value

    private fun DpRect.h() = (bottom - top).value

    @Test
    fun `B-LAMP-K2-020 panel_opensScrolledToBottom - Needs you by the thumb, the oldest group above the cut`() =
        runDesktopComposeUiTest(412, 480) {
            val scene = Scene(K2.model(pinned = true))
            panel(scene, height = 480)
            waitForIdle()
            onNodeWithText("Needs you").assertIsDisplayed()
            onNodeWithText("Allow once").assertIsDisplayed()
            onNodeWithText("Pinned").assertIsNotDisplayed()
            onNodeWithText("Recent").assertIsNotDisplayed()
        }

    @Test
    fun `B-LAMP-K2-021 panel_topCutOnGroupBoundary - the list's top edge falls on a group's top`() =
        runDesktopComposeUiTest(412, 480) {
            val scene = Scene(K2.model(pinned = true))
            panel(scene, height = 480)
            waitForIdle()
            val list = onNodeWithTag(LAMP_PANEL_LIST_TAG).getUnclippedBoundsInRoot()
            val tops = onAllNodesWithTag(LAMP_PANEL_HEADING_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.top }
            val first = tops.filter { it >= list.top.value - 0.5f }.minOrNull() ?: error("no heading in view")
            near(list.top.value + 12f, first, "the first group in view starts at the list's top (its heading 12 dp below)")
            onNodeWithText("Edit").assertIsDisplayed()
            onNodeWithText("All conversations · 7", useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-LAMP-K2-022 edit_pinToggle_rename_hide - Edit turns every row's end into pin, rename and hide, and unfolds the strip`() =
        runDesktopComposeUiTest(412, 1000) {
            val scene = Scene(K2.model())
            panel(scene, height = 1000)
            waitForIdle()
            onNodeWithContentDescription("Stop “create a file proof.txt with one line”").assertExists()
            onNodeWithText("Edit").performClick()
            waitForIdle()
            onNodeWithText("Done").assertExists()
            for (title in listOf("create a file hello.txt with one line", "create a file proof.txt with one line", "create a file notes.txt with one line")) {
                onNodeWithContentDescription("Pin to top: $title").assertExists()
                onNodeWithContentDescription("Rename conversation: $title").assertExists()
                onNodeWithContentDescription("Hide from this list: $title").assertExists()
            }
            onNodeWithContentDescription("Stop “create a file proof.txt with one line”").assertDoesNotExist()
            onNodeWithText("Edge light").assertExists()
            onNodeWithText("Move…").performClick()
            onNodeWithText("Turn off").performClick()
            onNodeWithContentDescription("Pin to top: create a file hello.txt with one line").performClick()
            onNodeWithContentDescription("Rename conversation: create a file hello.txt with one line").performClick()
            onNodeWithContentDescription("Hide from this list: create a file hello.txt with one line").performClick()
            assertEquals(listOf("place", "off", "pin:hello:true", "rename:hello", "hide:hello"), scene.events)
        }

    @Test
    fun `B-LAMP-K2-023 stop_iconButtonNamesTarget_holdsWithUndo - 44 dp, Commit, then the draining receipt`() =
        runDesktopComposeUiTest(412, 1000) {
            val platform = FakeKitPlatform()
            val scene = Scene(K2.model())
            panel(scene, height = 1000, platform = platform)
            waitForIdle()
            val name = "Stop “create a file proof.txt with one line”"
            val b = onNodeWithContentDescription(name, useUnmergedTree = true).getUnclippedBoundsInRoot()
            near(44f, b.w(), "the stop touch box is 44 dp wide")
            near(44f, b.h(), "and 44 dp tall")
            onNodeWithContentDescription(name).performClick()
            assertEquals(listOf("stop:proof"), scene.events)
            assertTrue(KitHaptic.Commit in platform.haptics, "Stop plays Commit")
            // From here the clock moves only when the test says, so the 3 s hold is still running when it looks.
            mainClock.autoAdvance = false
            scene.model =
                scene.model.copy(
                    sections = scene.model.sections.map { s ->
                        s.copy(rows = s.rows.map { if (it.key == "proof") it.copy(stopping = KitHeld("stop-proof", "Stopped “create a file proof.txt with one line”", allowed = false)) else it })
                    },
                )
            mainClock.advanceTimeBy(500)
            onNode(hasContentDescription("Stopped “create a file proof.txt with one line”", substring = true), useUnmergedTree = true).assertExists()
            onNodeWithContentDescription(name).assertDoesNotExist()
            onNode(hasText("Undo")).assertExists()
        }

    @Test
    fun `B-LAMP-K2-024 footer_shortLabelWhenNarrow - All conversations · 7 shortens to All · 7 on a narrow panel`() {
        for ((width, long) in listOf(360 to false, 600 to true)) {
            runDesktopComposeUiTest(width, 800) {
                val scene = Scene(K2.model())
                panel(scene, width = width)
                waitForIdle()
                val full = onNodeWithTag(LAMP_PANEL_ALL_TAG, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.isPlaced
                val short = onNodeWithTag(LAMP_PANEL_ALL_SHORT_TAG, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.isPlaced
                assertEquals(long, full, "$width: the long label")
                assertEquals(!long, short, "$width: the short label")
                onNodeWithText("New conversation").performClick()
                assertEquals(listOf("new"), scene.events)
            }
        }
    }

    @Test
    fun `B-LAMP-K2-025 talkback_customActions_moveUpDown - a pinned row's handle moves it one place`() =
        runDesktopComposeUiTest(412, 1200) {
            val scene = Scene(K2.model(editing = true, pinned = true))
            panel(scene, height = 1200)
            waitForIdle()
            val handle = onNode(hasContentDescription("Reorder Write the release notes"), useUnmergedTree = true).fetchSemanticsNode()
            val actions = handle.config[SemanticsActions.CustomActions]
            assertEquals(listOf("Move down"), actions.map { it.label }, "the first pin can only move down")
            actions.first().action()
            assertEquals(listOf("move:0>1"), scene.events)
            val second = onNode(hasContentDescription("Reorder Bank API migration"), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            assertEquals(listOf("Move up"), second.map { it.label })
            assertEquals(2, onAllNodesWithTag(REORDER_HANDLE_TAG, useUnmergedTree = true).fetchSemanticsNodes().size, "only pinned rows have a handle")
        }

    @Test
    fun `B-LAMP-K2-026 the panel measured - header, 24 dp marks at the gutter, 44 dp stop, 56 dp footer, every style, mode, direction, width and text size`() {
        for (style in K2.styles) for (dark in listOf(true, false)) for (rtl in listOf(false, true)) for (width in listOf(360, 412)) for (scale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(width, 1400) {
                val scene = Scene(K2.model())
                panel(scene, style, dark, rtl, width, 1400, scale)
                waitForIdle()
                val what = "$style ${if (dark) "dark" else "light"} ${if (rtl) "rtl" else "ltr"} $width ${scale}x"
                val p = onNodeWithTag(LAMP_PANEL_TAG).getUnclippedBoundsInRoot()
                near(width.toFloat(), p.w(), "$what: the panel fills a phone's width")
                val marks = onAllNodesWithTag(LAMP_ROW_MARK_TAG, useUnmergedTree = true)
                val n = marks.fetchSemanticsNodes().size
                assertEquals(5, n, "$what: five conversations")
                for (i in 0 until n) {
                    val m = marks[i].getUnclippedBoundsInRoot()
                    near(24f, m.w(), "$what: a 24 dp mark")
                    if (!rtl) near(16f, m.left.value, "$what: the mark 16 dp from the start edge")
                    else near(width - 16f, m.right.value, "$what: the mark 16 dp from the start (right) edge")
                }
                val stops = onAllNodesWithTag(STOP_BUTTON_TAG, useUnmergedTree = true)
                val s = stops.fetchSemanticsNodes().size
                assertTrue(s >= 2, "$what: the running rows carry stop")
                for (i in 0 until s) {
                    val b = stops[i].getUnclippedBoundsInRoot()
                    near(44f, b.w(), "$what: stop is 44 dp")
                    near(44f, b.h(), "$what: stop is 44 dp")
                }
                val list = onNodeWithTag(LAMP_PANEL_LIST_TAG).getUnclippedBoundsInRoot()
                assertTrue(list.top.value - p.top.value >= 60f - 0.5f, "$what: the header is at least 60 dp")
                assertTrue(p.bottom.value - list.bottom.value >= 56f - 0.5f, "$what: the footer row is at least 56 dp")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-027 the panel never grows past 600 dp`() =
        runDesktopComposeUiTest(900, 800) {
            val scene = Scene(K2.model())
            panel(scene, width = 900)
            waitForIdle()
            near(600f, onNodeWithTag(LAMP_PANEL_TAG).getUnclippedBoundsInRoot().w(), "the panel's cap")
        }

    @Test
    fun `B-LAMP-K2-028 at rest the panel requests no frames`() {
        for (style in K2.styles) {
            runDesktopComposeUiTest(412, 900) {
                val scene = Scene(K2.model())
                panel(scene, style, height = 900)
                waitForIdle()
                mainClock.advanceTimeBy(2_000)
                assertFalse(Recomposer.runningRecomposers.value.any { it.hasPendingWork }, "$style: a frame is still requested at rest")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-029 hidden ones fold at the top with Show again, and the rest count toward All conversations`() =
        runDesktopComposeUiTest(412, 1200) {
            val hidden = K2.row("gone", "Old experiment", KitLampRowMark.Quiet)
            val scene = Scene(K2.model().copy(hidden = listOf(hidden), more = 3))
            panel(scene, height = 1200)
            waitForIdle()
            onNodeWithText("3 more in All conversations").performClick()
            onNodeWithText("Hidden · 1").performClick()
            waitForIdle()
            onNodeWithText("Show again").performClick()
            assertEquals(listOf("all", "show:gone"), scene.events)
        }
}
