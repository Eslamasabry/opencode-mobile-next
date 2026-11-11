@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dock's touch area takes only what is the dock's (device QA 2026-11-11 sweep B; dock study, findings 1 and 2):
 * - a. an alert's self-opened swell is look-only: Send on the page, pressed while it shows, sends, and the swell closes;
 * - b. after Remove on a ring's card the area is the dock's own again at once: the next tap on the page reaches it;
 * - c. a thumb scroll that starts on the tucked band scrolls the list under it and leaves the dock where it is; a tap
 *   on the band still brings the dock out (and reaches nothing under it).
 */
class KitEdgeDockTouchAreaTest {
    private class Seen : KitDockIntents {
        val acts = mutableListOf<String>()
        var rings by mutableStateOf(RINGS)

        override fun onRemove(key: String) {
            acts += "remove $key"
            rings = rings.filter { it.key != key }
        }

        override fun onTuckChange(tucked: Boolean) {
            acts += "tuck $tucked"
        }

        override fun onPlacementChange(side: KitDockSide, verticalBias: Float) {
            acts += "place"
        }

        override fun onOpen(key: String) {
            acts += "open $key"
        }
    }

    /** A page under the dock: Send at its bottom start, far from the dock's strip. */
    private fun ComposeUiTest.pageWithSend(seen: Seen, state: KitEdgeDockState, motion: MotionLevel, sent: () -> Unit) {
        mainClock.autoAdvance = false
        setContent {
            KitTheme(KitAppearance(motion = motion), FakeKitPlatform(), "en", stillFrame = true) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.offset(16.dp, 820.dp).size(120.dp, 56.dp).clickable { sent() })
                    KitEdgeDock(KitDockModel(rings = seen.rings), seen, TestDockWords, insets = KitDockInsets(top = 24.dp, bottom = 48.dp), state = state)
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
    }

    private fun ComposeUiTest.tapSend() {
        onRoot().performTouchInput { click(Offset(76.dp.toPx(), 848.dp.toPx())) }
    }

    private fun alertTakesNoTap(motion: MotionLevel) =
        runDesktopComposeUiTest(W, H) {
            val seen = Seen()
            val state = KitEdgeDockState()
            var sent = 0
            pageWithSend(seen, state, motion) { sent++ }
            // Another conversation asks: its ring's pulse rises; the swell opens by itself for it.
            seen.rings = seen.rings.map { if (it.key == "two") it.copy(state = KitDockRingState.NeedsYou, pulse = 1) else it }
            mainClock.advanceTimeBy(500)
            assertTrue(runOnUiThread { state.swell != null }, "$motion: the alert opened the swell")
            tapSend()
            mainClock.advanceTimeBy(100)
            assertEquals(1, sent, "$motion: Send pressed during the alert sends")
            mainClock.advanceTimeBy(700)
            assertTrue(runOnUiThread { state.swell == null }, "$motion: the touch on the page closed the alert's swell")
            assertEquals(emptyList(), seen.acts, "$motion: the dock did nothing with that touch")
        }

    @Test
    fun `a - an alert's swell is look-only - Send pressed during it sends (full motion)`() = alertTakesNoTap(MotionLevel.Full)

    @Test
    fun `a - an alert's swell is look-only - Send pressed during it sends (reduced motion)`() = alertTakesNoTap(MotionLevel.Off)

    @Test
    fun `b - after Remove on a ring's card the next tap on the page reaches it`() =
        runDesktopComposeUiTest(W, H) {
            val seen = Seen()
            val state = KitEdgeDockState()
            var sent = 0
            pageWithSend(seen, state, MotionLevel.Full) { sent++ }
            // Hold ring one until its card opens, and let go: the card stays (driven as the pointer loop drives it).
            val x = runOnUiThread { state.widthDp - DockDims.REST_W / 2f }
            runOnUiThread {
                state.computeFrame()
                state.gesture.down(DockZone.Ring, x, state.slotY(0), 0L, state.hits)
                state.onDown(DockZone.Ring)
                state.onFeedback(state.gesture.elapsed(DockRules().holdMs))
            }
            mainClock.advanceTimeBy(600)
            runOnUiThread { state.onOutcome(state.gesture.up(x, state.slotY(0), 0f, state.hits)) }
            mainClock.advanceTimeBy(600)
            // The card's Remove, by touch.
            val remove = onAllNodes(hasContentDescription(TestDockWords.actionOn(TestDockWords.remove, RINGS[0].name)), useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertTrue(remove.isNotEmpty(), "ring one's card is open with its Remove")
            onRoot().performTouchInput { click(remove.first().boundsInRoot.center) }
            // The very next frame, a tap on the page.
            mainClock.advanceTimeByFrame()
            tapSend()
            mainClock.advanceTimeBy(100)
            assertEquals(listOf("remove one"), seen.acts, "Remove ran, and the dock did nothing with the next tap")
            assertEquals(1, sent, "the tap right after Remove reached the page (the closing card keeps nothing open)")
        }

    /** A list of rows under a tucked dock. */
    private fun ComposeUiTest.tuckedOverList(seen: Seen, state: KitEdgeDockState, onRow: () -> Unit): LazyListState {
        var list: LazyListState? = null
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                Box(Modifier.fillMaxSize()) {
                    val l = rememberLazyListState()
                    list = l
                    LazyColumn(Modifier.fillMaxSize(), state = l) {
                        items(80) { Box(Modifier.fillMaxWidth().height(56.dp).clickable { onRow() }) }
                    }
                    KitEdgeDock(
                        KitDockModel(rings = seen.rings, tucked = true),
                        seen,
                        TestDockWords,
                        insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                        state = state,
                    )
                }
            }
        }
        waitForIdle()
        return list!!
    }

    /** The middle of the tucked band (dp, physical: the dock is on the right). */
    private fun ComposeUiTest.band(state: KitEdgeDockState): Pair<Float, Float> =
        runOnIdle { (state.widthDp - DockDims.NUB_TOUCH / 3f) to (state.top.value + state.column.height / 2f) }

    @Test
    fun `c - a thumb scroll that starts on the tucked band scrolls the list and leaves the dock where it is`() =
        runDesktopComposeUiTest(W, H) {
            val seen = Seen()
            val state = KitEdgeDockState()
            var rows = 0
            val list = tuckedOverList(seen, state) { rows++ }
            val (bx, by) = band(state)
            val topBefore = runOnIdle { state.top.value }
            onRoot().performTouchInput {
                swipe(Offset(bx.dp.toPx(), by.dp.toPx()), Offset(bx.dp.toPx(), (by - 300f).dp.toPx()), durationMillis = 400)
            }
            waitForIdle()
            assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0, "the list under the band scrolled")
            assertEquals(emptyList(), seen.acts, "the dock was not moved, untucked or saved")
            assertEquals(topBefore, runOnIdle { state.top.value }, "the dock stays where it was")
            assertEquals(0, rows, "the scroll clicked no row")
        }

    @Test
    fun `c - a tap on the tucked band still brings the dock out and reaches no row under it`() =
        runDesktopComposeUiTest(W, H) {
            val seen = Seen()
            val state = KitEdgeDockState()
            var rows = 0
            tuckedOverList(seen, state) { rows++ }
            val (bx, by) = band(state)
            onRoot().performTouchInput { click(Offset(bx.dp.toPx(), by.dp.toPx())) }
            waitForIdle()
            assertEquals(listOf("tuck false"), seen.acts, "the tap untucks")
            assertEquals(0, rows, "the row under the band")
        }

    @Test
    fun `c - a sideways drag in from the tucked band brings the dock out and does not scroll the list`() =
        runDesktopComposeUiTest(W, H) {
            val seen = Seen()
            val state = KitEdgeDockState()
            val list = tuckedOverList(seen, state) {}
            val (bx, by) = band(state)
            onRoot().performTouchInput {
                swipe(Offset(bx.dp.toPx(), by.dp.toPx()), Offset((bx - 120f).dp.toPx(), (by + 4f).dp.toPx()), durationMillis = 300)
            }
            waitForIdle()
            assertEquals(listOf("tuck false"), seen.acts, "the drag in untucks")
            assertEquals(0, list.firstVisibleItemIndex, "the list did not scroll")
            assertEquals(0, list.firstVisibleItemScrollOffset, "the list did not scroll")
        }

    private companion object {
        const val W = 412
        const val H = 915
        val RINGS =
            listOf(
                KitDockRing("one", "create a file one.txt", null, KitDockRingState.Idle),
                KitDockRing("two", "name three colours", null, KitDockRingState.Idle),
                KitDockRing("three", "tell a short joke", null, KitDockRingState.Idle),
            )
    }
}
