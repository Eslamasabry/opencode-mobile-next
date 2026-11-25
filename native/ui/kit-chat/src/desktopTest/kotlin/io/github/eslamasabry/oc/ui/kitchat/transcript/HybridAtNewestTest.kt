package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.listScrollAxis(): ScrollAxisRange =
    onNodeWithTag("transcript-list", useUnmergedTree = true)
        .fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange]

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.a11yScroll(amountPx: Float, towardNewer: Boolean, context: String) {
    val range = listScrollAxis()
    val visual = if (towardNewer) amountPx else -amountPx
    val y = if (range.reverseScrolling) -visual else visual
    val canScroll = if (y > 0f) range.value() < range.maxValue() else range.value() > 0f
    assertTrue(
        canScroll,
        "$context: action unavailable (towardNewer=$towardNewer rev=${range.reverseScrolling} " +
            "val=${range.value()} max=${range.maxValue()})",
    )
    onNodeWithTag("transcript-list", useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, y) }
}

private fun TranscriptListState.topVisibleAge(): Int {
    val items = lazy.layoutInfo.visibleItemsInfo
    val top = if (isNormalOrder) items.first() else items.last()
    return top.key.toString().removePrefix("r").toInt()
}

private fun TranscriptListState.bottomVisibleAge(): Int {
    val items = lazy.layoutInfo.visibleItemsInfo
    val bottom = if (isNormalOrder) items.last() else items.first()
    return bottom.key.toString().removePrefix("r").toInt()
}

@OptIn(ExperimentalTestApi::class)
class HybridAtNewestTest {
    private fun runGesture(returnGapDp: Float?, leaveDp: Float = LEAVE_DP): Boolean {
        var result = false
        runComposeUiTest {
            lateinit var state: TranscriptListState
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalTranscriptAnchor provides TranscriptAnchor.Hybrid) {
                    ChatTestTheme(dark = true) {
                        state = rememberTranscriptListState()
                        KitTranscriptList(
                            state = state,
                            newestKey = "r0",
                            jumpLabel = "Jump to latest",
                            motion = KitMotionLevel.Off,
                            modifier = Modifier.width(VIEWPORT_W_DP.dp).height(VIEWPORT_H_DP.dp),
                            contentPadding = PaddingValues(bottom = BOTTOM_PAD_DP.dp),
                        ) {
                            item(key = "r0") { Box(Modifier.fillMaxWidth().height(NEWEST_ROW_DP.dp)) }
                            items(count = OLDER_ROW_COUNT, key = { "r${it + 1}" }) {
                                Box(Modifier.fillMaxWidth().height(OLDER_ROW_DP.dp))
                            }
                        }
                    }
                }
            }
            repeat(INITIAL_FRAMES) { mainClock.advanceTimeByFrame() }
            assertTrue(state.following, "expected following == true before drag")
            onNodeWithTag("transcript-list").performTouchInput {
                val slop = viewConfiguration.touchSlop
                val start = Offset(centerX, top + height * 0.2f)
                down(start)
                moveBy(Offset(0f, slop))
                moveBy(Offset(0f, leaveDp.dp.toPx()))
                if (returnGapDp != null) {
                    moveBy(Offset(0f, -(leaveDp - returnGapDp).dp.toPx()))
                }
                advanceEventTime(HOLD_MS)
                up()
            }
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            result = state.following
        }
        return result
    }

    @Test
    fun `STEPS-SCROLL-009 hybrid newest row 40 dp under the line still counts as at newest`() {
        assertTrue(
            runGesture(returnGapDp = 40f),
            "expected following == true when returning to 40 dp under the line",
        )
    }

    @Test
    fun `STEPS-SCROLL-009b hybrid 56 dp under the line is away`() {
        assertFalse(
            runGesture(returnGapDp = 56f),
            "expected following == false when returning to 56 dp under the line",
        )
    }

    @Test
    fun `STEPS-SCROLL-009c hybrid 40 dp upward drag leaves following`() {
        assertFalse(
            runGesture(returnGapDp = null, leaveDp = 40f),
            "expected following == false after a 40 dp upward drag",
        )
    }

    private fun runA11yScenario(
        anchor: TranscriptAnchor,
        motion: KitMotionLevel = KitMotionLevel.Off,
        block: ComposeUiTest.(TranscriptListState) -> Unit,
    ) = runComposeUiTest {
        lateinit var state: TranscriptListState
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalTranscriptAnchor provides anchor) {
                ChatTestTheme(dark = true) {
                    state = rememberTranscriptListState()
                    KitTranscriptList(
                        state = state,
                        newestKey = "r0",
                        jumpLabel = "Jump to latest",
                        motion = motion,
                        modifier = Modifier.width(VIEWPORT_W_DP.dp).height(VIEWPORT_H_DP.dp),
                        contentPadding = PaddingValues(bottom = BOTTOM_PAD_DP.dp),
                    ) {
                        item(key = "r0") { Box(Modifier.fillMaxWidth().height(NEWEST_ROW_DP.dp)) }
                        items(count = OLDER_ROW_COUNT, key = { "r${it + 1}" }) {
                            Box(Modifier.fillMaxWidth().height(OLDER_ROW_DP.dp))
                        }
                    }
                }
            }
        }
        repeat(INITIAL_FRAMES) { mainClock.advanceTimeByFrame() }
        block(state)
    }

    @Test
    fun `STEPS-SCROLL-013 hybrid screen reader scroll up and down across both orders`() =
        runA11yScenario(TranscriptAnchor.Hybrid) { state ->
            assertTrue(state.following, "starts following")
            assertFalse(state.isNormalOrder, "starts reversed")
            val startAxis = listScrollAxis()
            assertTrue(startAxis.reverseScrolling, "reversed order reports reverseScrolling=true")
            assertTrue(startAxis.value() == 0f && startAxis.maxValue() > 0f, "at newest with older rows above")

            val age0 = state.topVisibleAge()
            a11yScroll(A11Y_STEP_PX, towardNewer = false, context = "hybrid following up")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            val age1 = state.topVisibleAge()
            assertTrue(age1 > age0, "scroll up from following moves toward older ($age0 -> $age1)")
            assertFalse(state.following, "scroll up from following enters reading")
            assertTrue(state.isNormalOrder, "reading flips to normal order")
            val readAxis = listScrollAxis()
            assertFalse(readAxis.reverseScrolling, "normal order reports reverseScrolling=false")
            assertTrue(readAxis.value() > 0f && readAxis.value() < readAxis.maxValue(), "can scroll both ways")

            a11yScroll(A11Y_STEP_PX, towardNewer = false, context = "hybrid reading up")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            val age2 = state.topVisibleAge()
            assertTrue(age2 > age1, "scroll up while reading moves toward older ($age1 -> $age2)")
            assertFalse(state.following, "stays reading after scrolling further up")

            a11yScroll(A11Y_PARTIAL_PX, towardNewer = true, context = "hybrid reading partial down")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            val age3 = state.topVisibleAge()
            assertTrue(age3 < age2, "scroll down while reading moves toward newer ($age2 -> $age3)")
            assertFalse(state.following, "partial scroll down stays reading")

            a11yScroll(A11Y_RETURN_PX, towardNewer = true, context = "hybrid reading down to newest")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            assertEquals(0, state.bottomVisibleAge(), "returned to newest row r0")
            assertTrue(state.following, "scrolling down to newest resumes following")
            assertFalse(state.isNormalOrder, "following flips back to reversed order")
            assertTrue(listScrollAxis().reverseScrolling, "reversed order again reports reverseScrolling=true")
        }

    @Test
    fun `STEPS-SCROLL-014 hybrid screen reader small scroll up leaves following like a finger drag`() =
        runA11yScenario(TranscriptAnchor.Hybrid) { state ->
            assertTrue(state.following, "starts following")
            assertTrue(listScrollAxis().reverseScrolling, "starts reversed")
            a11yScroll(SMALL_SCROLL_PX, towardNewer = false, context = "hybrid small up")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            assertFalse(state.following, "small scroll up leaves following")
            assertTrue(state.isNormalOrder, "reading flips to normal order")
            assertFalse(listScrollAxis().reverseScrolling, "normal order reports reverseScrolling=false")
        }

    @Test
    fun `STEPS-SCROLL-015 today screen reader scroll up and down in reversed order`() =
        runA11yScenario(TranscriptAnchor.Today) { state ->
            assertTrue(state.following, "today starts following")
            assertFalse(state.isNormalOrder, "today stays reversed")
            val startAxis = listScrollAxis()
            assertTrue(startAxis.reverseScrolling, "today reports reverseScrolling=true")
            assertTrue(startAxis.value() == 0f && startAxis.maxValue() > 0f, "today at newest with older above")

            val age0 = state.topVisibleAge()
            a11yScroll(A11Y_STEP_PX, towardNewer = false, context = "today following up")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            val age1 = state.topVisibleAge()
            assertTrue(age1 > age0, "today scroll up moves toward older ($age0 -> $age1)")
            assertFalse(state.following, "today scroll up leaves following")
            assertTrue(listScrollAxis().reverseScrolling, "today keeps reverseScrolling=true")

            a11yScroll(A11Y_STEP_PX, towardNewer = false, context = "today reading up")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            val age2 = state.topVisibleAge()
            assertTrue(age2 > age1, "today scroll up in reading moves toward older ($age1 -> $age2)")
            assertFalse(state.following, "today stays reading")

            a11yScroll(A11Y_PARTIAL_PX, towardNewer = true, context = "today reading partial down")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            val age3 = state.topVisibleAge()
            assertTrue(age3 < age2, "today scroll down moves toward newer ($age2 -> $age3)")
            assertFalse(state.following, "today partial down stays reading")

            a11yScroll(A11Y_RETURN_PX, towardNewer = true, context = "today reading down to newest")
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            assertEquals(0, state.bottomVisibleAge(), "today returned to newest row r0")
            assertTrue(state.following, "today resumes following at newest")
        }

    @Test
    fun `STEPS-SCROLL-016 today and hybrid screen reader scrollToIndex leaves following and returns`() {
        runA11yScenario(TranscriptAnchor.Today) { state ->
            val age0 = state.topVisibleAge()
            onNodeWithTag("transcript-list", useUnmergedTree = true).performScrollToIndex(A11Y_JUMP_INDEX)
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            assertTrue(state.topVisibleAge() > age0, "today scrollToIndex moves toward older")
            assertFalse(state.following, "today scrollToIndex leaves following")
            onNodeWithTag("transcript-list", useUnmergedTree = true).performScrollToIndex(0)
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            assertEquals(0, state.bottomVisibleAge(), "today scrollToIndex(0) returns to newest")
            assertTrue(state.following, "today scrollToIndex(0) resumes following")
        }
        runA11yScenario(TranscriptAnchor.Hybrid) { state ->
            val age0 = state.topVisibleAge()
            onNodeWithTag("transcript-list", useUnmergedTree = true).performScrollToIndex(A11Y_JUMP_INDEX)
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            assertTrue(state.topVisibleAge() > age0, "hybrid scrollToIndex moves toward older")
            assertFalse(state.following, "hybrid scrollToIndex leaves following")
            assertTrue(state.isNormalOrder, "hybrid flips to normal order")
            assertFalse(listScrollAxis().reverseScrolling, "hybrid reading axis reverseScrolling=false")
            onNodeWithTag("transcript-list", useUnmergedTree = true).performScrollToIndex(OLDER_ROW_COUNT)
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            assertEquals(0, state.bottomVisibleAge(), "hybrid scrollToIndex(last) returns to newest")
            assertTrue(state.following, "hybrid scrollToIndex(last) resumes following")
            assertFalse(state.isNormalOrder, "hybrid flips back to reversed order")
            assertTrue(listScrollAxis().reverseScrolling, "hybrid following axis reverseScrolling=true")
        }
    }

    private companion object {
        const val VIEWPORT_W_DP = 360
        const val VIEWPORT_H_DP = 800
        const val BOTTOM_PAD_DP = 120
        const val NEWEST_ROW_DP = 20
        const val OLDER_ROW_DP = 100
        const val OLDER_ROW_COUNT = 40
        const val LEAVE_DP = 200f
        const val INITIAL_FRAMES = 5
        const val SETTLE_FRAMES = 60
        const val HOLD_MS = 200L
        const val A11Y_STEP_PX = 480f
        const val A11Y_PARTIAL_PX = 240f
        const val A11Y_RETURN_PX = 2400f
        const val SMALL_SCROLL_PX = 40f
        const val A11Y_JUMP_INDEX = 10
    }
}
