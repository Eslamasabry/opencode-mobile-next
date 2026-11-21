package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpRect
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.action.STOP_BUTTON_TAG
import io.github.eslamasabry.oc.ui.kit.request.KitHeld
import io.github.eslamasabry.oc.ui.kit.request.KitRequest
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
        // 420 dp: about 300 dp for the list, so Needs you (about 150 dp) fills at least 45 % of it and the Working group
        // above does not fit: the cut must fall on their boundary. (At 480 dp Needs you is under 45 % of the room, and
        // the rule rightly shows part of Working instead of a mostly empty list.)
        runDesktopComposeUiTest(412, 420) {
            val scene = Scene(K2.model(pinned = true))
            panel(scene, height = 420)
            waitForIdle()
            onNodeWithText("Needs you").assertIsDisplayed()
            onNodeWithText("Allow once").assertIsDisplayed()
            onNodeWithText("Pinned").assertIsNotDisplayed()
            onNodeWithText("Recent").assertIsNotDisplayed()
        }

    @Test
    fun `B-LAMP-K2-021 panel_topCutOnGroupBoundary - the list's top edge falls on a group's top`() =
        // 420 dp: about 300 dp for the list, so Needs you (about 150 dp) fills at least 45 % of it and the Working group
        // above does not fit: the cut must fall on their boundary. (At 480 dp Needs you is under 45 % of the room, and
        // the rule rightly shows part of Working instead of a mostly empty list.)
        runDesktopComposeUiTest(412, 420) {
            val scene = Scene(K2.model(pinned = true))
            panel(scene, height = 420)
            waitForIdle()
            val list = onNodeWithTag(LAMP_PANEL_LIST_TAG).getUnclippedBoundsInRoot()
            // A heading scrolled out above the list reports its bounds clipped to the list's top edge with no height:
            // only the headings in view count.
            val tops =
                onAllNodesWithTag(LAMP_PANEL_HEADING_TAG, useUnmergedTree = true).fetchSemanticsNodes()
                    .map { it.boundsInRoot }.filter { it.height > 0.5f }.map { it.top }
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
            onNodeWithContentDescription("Pin to top: create a file notes.txt with one line").performClick()
            onNodeWithContentDescription("Rename conversation: create a file notes.txt with one line").performClick()
            onNodeWithContentDescription("Hide from this list: create a file notes.txt with one line").performClick()
            // The bottom row (in view: the list opens at the bottom and Edit makes rows taller).
            assertEquals(listOf("place", "off", "pin:notes:true", "rename:notes", "hide:notes"), scene.events)
        }

    @Test
    fun `B-LAMP-K2-070 edit_rowTapOpensNothing - a row's tap opens its conversation outside Edit and does nothing in Edit`() =
        runDesktopComposeUiTest(412, 1000) {
            val scene = Scene(K2.model())
            panel(scene, height = 1000)
            waitForIdle()
            onNodeWithText("create a file hello.txt with one line").performClick()
            assertEquals(listOf("open:hello"), scene.events, "outside Edit the row opens")
            scene.events.clear()
            onNodeWithText("Edit").performClick()
            waitForIdle()
            onNodeWithText("create a file hello.txt with one line").performClick()
            onNodeWithText("create a file notes.txt with one line").performClick()
            waitForIdle()
            assertEquals(emptyList(), scene.events, "in Edit a row's tap opens nothing")
            onNodeWithContentDescription("Pin to top: create a file notes.txt with one line").performClick()
            assertEquals(listOf("pin:notes:true"), scene.events, "the row's own edit actions still work")
        }

    @Test
    fun `B-LAMP-K2-023 stop_iconButtonNamesTarget_holdsWithUndo - 48 dp to touch, Commit, then the draining receipt`() =
        runDesktopComposeUiTest(412, 1000) {
            val platform = FakeKitPlatform()
            val scene = Scene(K2.model())
            panel(scene, height = 1000, platform = platform)
            waitForIdle()
            val name = "Stop “create a file proof.txt with one line”"
            val b = onNodeWithContentDescription(name, useUnmergedTree = true).getUnclippedBoundsInRoot()
            near(48f, b.w(), "the stop touch box is 48 dp wide")
            near(48f, b.h(), "and 48 dp tall")
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
                // The label not chosen is measured but never placed.
                fun placed(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }
                val full = placed(LAMP_PANEL_ALL_TAG)
                val short = placed(LAMP_PANEL_ALL_SHORT_TAG)
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
    fun `B-LAMP-K2-026 the panel measured - header, 24 dp marks at the gutter, 48 dp stop, 56 dp footer, every style, mode, direction, width and text size`() {
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
                    near(48f, b.w(), "$what: stop is 48 dp to touch")
                    near(48f, b.h(), "$what: stop is 48 dp to touch")
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
            val scene = Scene(K2.model().copy(hidden = listOf(hidden), more = 3, editing = true))
            panel(scene, height = 1200)
            waitForIdle()
            onNodeWithText("3 more in All conversations").performClick()
            onNodeWithText("Hidden · 1").performClick()
            waitForIdle()
            onNodeWithText("Show again").performClick()
            assertEquals(listOf("all", "show:gone"), scene.events)
        }

    private fun longModel(hidden: List<KitLampRow> = emptyList()): KitLampPanelModel =
        K2.model().copy(
            sections = listOf(KitLampSection(KitLampGroup.Recent, "Recent", (1..25).map { K2.row("r$it", "Conversation number $it", KitLampRowMark.Quiet) })),
            hidden = hidden,
            all = 25,
        )

    @Test
    fun `B-LAMP-K2-030 the list starts below its header and nothing of the header is overlapped by a row`() {
        for (width in listOf(360, 412)) for (fontScale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(width, 800) {
                val scene = Scene(longModel())
                panel(scene, width = width, height = 800, fontScale = fontScale)
                waitForIdle()
                val header = onNodeWithText("You are in", substring = true).getUnclippedBoundsInRoot()
                val list = onNodeWithTag(LAMP_PANEL_LIST_TAG).getUnclippedBoundsInRoot()
                assertTrue(list.top.value >= header.bottom.value + 7.5f, "$width dp x$fontScale: list top ${list.top.value} must clear the header text bottom ${header.bottom.value} by a gap")
                val rows =
                    onAllNodesWithTag(LAMP_PANEL_HEADING_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
                assertTrue(rows.all { it.height < 0.5f || it.top >= list.top.value - 0.5f }, "nothing drawn above the list's top")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-031 a hidden conversation's way back is in view in Edit under 25 rows, and not a row of its own outside Edit`() {
        for (fontScale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(360, 800) {
                val scene = Scene(longModel(listOf(K2.row("gone", "Old experiment", KitLampRowMark.Quiet))).copy(editing = true))
                panel(scene, width = 360, height = 800, fontScale = fontScale)
                waitForIdle()
                onNodeWithText("Hidden · 1").assertIsDisplayed()
                onNodeWithText("Hidden · 1").performClick()
                waitForIdle()
                onNodeWithText("Show again").assertIsDisplayed()
                onNodeWithText("Show again").performClick()
                assertEquals(listOf("show:gone"), scene.events)
            }
            runDesktopComposeUiTest(360, 800) {
                val scene = Scene(longModel(listOf(K2.row("gone", "Old experiment", KitLampRowMark.Quiet))))
                panel(scene, width = 360, height = 800, fontScale = fontScale)
                waitForIdle()
                onAllNodesWithText("Hidden · 1").assertCountEquals(0)
            }
        }
    }
    private fun askingModel(count: Int): KitLampPanelModel =
        K2.model().copy(
            sections =
                listOf(
                    KitLampSection(
                        KitLampGroup.NeedsYou,
                        "Needs you",
                        (1..count).map {
                            KitLampRow(
                                "ask$it",
                                "Reply with exactly the word: base-$it",
                                KitLampRowMark.NeedsYou,
                                state = "for 8m",
                                stateTone = KitLampTone.Attention,
                                request = KitRequest("rq$it", "Asks to fetch: example.org"),
                            )
                        },
                    ),
                ),
            all = 3,
        )

    /** No section label straddles the list's top edge: each is fully in view or fully out of it. */
    private fun ComposeUiTest.assertNoLabelCutAtTop(what: String) {
        val top = onNodeWithTag(LAMP_PANEL_LIST_TAG).getUnclippedBoundsInRoot().top.value
        val labels = onAllNodesWithTag(LAMP_PANEL_HEADING_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(labels.isNotEmpty(), "$what: labels found")
        // The unclipped place and size: a node's boundsInRoot is already cut to the scroll viewport.
        for (n in labels) {
            val from = n.positionInRoot.y
            val to = from + n.size.height
            assertTrue(to <= top + 0.5f || from >= top - 0.5f, "$what: a label ($from..$to) is cut by the list's top $top")
        }
    }

    @Test
    fun `B-LAMP-K2-071 at 360 dp and text 1_3 with one request the section label is never half under the header`() {
        for (height in 380..560 step 10) {
            runDesktopComposeUiTest(360, height) {
                panel(Scene(askingModel(1)), width = 360, height = height, fontScale = 1.3f)
                waitForIdle()
                assertNoLabelCutAtTop("window $height dp")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-072 with three requests the resting scroll never leaves a label half in view`() {
        runDesktopComposeUiTest(360, 520) {
            panel(Scene(askingModel(3)), width = 360, height = 520, fontScale = 1.3f)
            waitForIdle()
            assertNoLabelCutAtTop("opening")
            for (step in 1..4) {
                onNodeWithTag(LAMP_PANEL_LIST_TAG).performTouchInput { swipeDown(startY = top + 40f * step, endY = bottom - 4f, durationMillis = 180) }
                waitForIdle()
                assertNoLabelCutAtTop("after a swipe down $step")
                onNodeWithTag(LAMP_PANEL_LIST_TAG).performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 40f * step, durationMillis = 180) }
                waitForIdle()
                assertNoLabelCutAtTop("after a swipe up $step")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-073 three requests at 1_3 grow the panel to the room it is given before the list scrolls`() {
        for (height in listOf(560, 640)) {
            runDesktopComposeUiTest(360, height) {
                panel(Scene(askingModel(3)), width = 360, height = height, fontScale = 1.3f)
                waitForIdle()
                val panelH = onNodeWithTag(LAMP_PANEL_TAG).getUnclippedBoundsInRoot().h()
                assertTrue(panelH >= height - 1f, "window $height dp: the panel takes the room ($panelH dp), not a part of it")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-074 requests that fit in the room are all in view and the list is not scrolled`() =
        runDesktopComposeUiTest(412, 800) {
            panel(Scene(askingModel(2)), width = 412, height = 800)
            waitForIdle()
            onNodeWithText("Needs you").assertIsDisplayed()
            onAllNodesWithText("Allow once").assertCountEquals(2)
            onAllNodesWithText("Allow once")[0].assertIsDisplayed()
            onAllNodesWithText("Allow once")[1].assertIsDisplayed()
        }

    /** The unclipped top (px in the root) of the first node showing [text]. */
    private fun ComposeUiTest.topOf(text: String): Float =
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first().positionInRoot.y

    @Test
    fun `B-LAMP-K2-075 the open panel rests at its top - the label and the first request whole under the header`() {
        for (count in listOf(1, 3)) for (fontScale in listOf(1f, 1.3f)) {
            runDesktopComposeUiTest(360, 520) {
                panel(Scene(askingModel(count)), width = 360, height = 520, fontScale = fontScale)
                waitForIdle()
                val what = "$count requests, text $fontScale"
                val list = onNodeWithTag(LAMP_PANEL_LIST_TAG).getUnclippedBoundsInRoot().top.value
                val header = onNodeWithText("You are in", substring = true).getUnclippedBoundsInRoot().bottom.value
                val label = topOf("Needs you")
                val title = topOf("Reply with exactly the word: base-1")
                assertTrue(label >= list - 0.5f, "$what: the label ($label) starts at or below the list top ($list)")
                assertTrue(title >= list - 0.5f && title >= header, "$what: the first request's title ($title) is not under the header ($header)")
            }
        }
    }

    @Test
    fun `B-LAMP-K2-076 a request that arrives while the panel is open does not move the rows the person is looking at`() {
        for (atTop in listOf(true, false)) {
            runDesktopComposeUiTest(360, 520) {
                val scene = Scene(askingModel(3))
                panel(scene, width = 360, height = 520, fontScale = 1.3f)
                waitForIdle()
                onNodeWithTag(LAMP_PANEL_LIST_TAG).performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 60f, durationMillis = 180) }
                waitForIdle()
                val watched = "Reply with exactly the word: base-2"
                val before = topOf(watched)
                val rows = scene.model.sections.single().rows
                val extra = rows.first().let { it.copy(key = "ask9", title = "Reply with exactly the word: base-9", request = KitRequest("rq9", "Asks to fetch: example.org")) }
                scene.model = scene.model.copy(sections = listOf(scene.model.sections.single().copy(rows = if (atTop) listOf(extra) + rows else rows + extra)))
                waitForIdle()
                near(before, topOf(watched), "the watched row stays where it was (arrival ${if (atTop) "above" else "below"})", tol = 1.5f)
            }
        }
    }
}
