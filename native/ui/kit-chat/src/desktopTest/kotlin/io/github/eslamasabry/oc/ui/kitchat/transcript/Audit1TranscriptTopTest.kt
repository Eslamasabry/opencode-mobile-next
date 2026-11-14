@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit 1 (2026-11-14), the top of the transcript and its follow:
 * - "N earlier messages" was a pill floating under the bar; the way to earlier messages is now the transcript's own
 *   first (top) row, 48 dp tall, and reading back to it asks for them once by itself, the reader's place kept.
 * - k6: "Jump to latest" showed right after a send with the newest message in view: a drag that ended at the newest
 *   message never resumed following, so the send landed below the rows in view.
 */
class Audit1TranscriptTopTest {
    private fun ComposeUiTest.scene(
        rows: () -> List<String>,
        newest: () -> String,
        list: Array<TranscriptListState?>,
        older: Boolean = false,
        onOlder: () -> Unit = {},
        reached: () -> Unit = {},
    ) {
        setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                Box(Modifier.size(412.dp, 600.dp)) {
                    val state = rememberTranscriptListState()
                    list[0] = state
                    KitTranscriptList(state, newestKey = newest(), jumpLabel = "Jump to latest", motion = KitMotionLevel.Off, onReachOldest = if (older) reached else null) {
                        items(rows(), key = { it }) { Box(Modifier.fillMaxWidth().height(40.dp).testTag(it)) { KitText(it) } }
                        if (older) item(key = "earlier") { KitEarlierMessages("Show earlier messages", onOlder, loading = false) }
                    }
                }
            }
        }
    }

    /** A finger on the list moving [dy] px in steps, resting before it lifts (no fling). */
    private fun ComposeUiTest.drag(vararg dy: Float) {
        onNodeWithTag("transcript-list").performTouchInput {
            down(center)
            for (d in dy) repeat(10) { moveBy(Offset(0f, d / 10f)) }
            advanceEventTime(300)
            up()
        }
        waitForIdle()
    }

    @Test
    fun `AUDIT1-04 a drag that ends at the newest message follows it again, so a send lands in view`() = runComposeUiTest {
        var rows by mutableStateOf((0 until 60).map { "m$it" })
        val list = arrayOfNulls<TranscriptListState>(1)
        scene({ rows }, { rows.first() }, list)
        waitForIdle()
        // Read back (the finger moves down: older messages come down from the top), then come back to the newest.
        drag(600f, -900f)
        val state = list[0]!!
        assertTrue(state.atLiveEdge, "back at the newest message")
        assertTrue(state.following, "a drag that ended at the newest message follows it again")
        // The person sends: a new newest row.
        rows = listOf("sent") + rows
        waitForIdle()
        assertEquals(0, state.lazy.firstVisibleItemIndex, "the sent message is the newest row in view")
        assertFalse(state.awayFromLatest, "no Jump to latest")
        onNodeWithTag("jump-to-latest").assertDoesNotExist()
    }

    @Test
    fun `AUDIT1-01 the way to earlier messages is the top row, and reading back to it asks for them once, keeping the place`() =
        runComposeUiTest {
            var rows by mutableStateOf((0 until 30).map { "m$it" })
            var reached = 0
            var tapped = 0
            val list = arrayOfNulls<TranscriptListState>(1)
            scene({ rows }, { "m0" }, list, older = true, onOlder = { tapped++ }, reached = { reached++ })
            waitForIdle()
            assertEquals(0, reached, "at the newest message nothing is asked for")
            // Read back to the top of what is loaded.
            drag(3000f)
            val row = onNodeWithTag(EARLIER_MESSAGES_TAG).getBoundsInRoot()
            assertTrue(row.bottom - row.top >= 48.dp, "a 48 dp target: $row")
            val oldest = onNodeWithTag("m29").getBoundsInRoot()
            assertTrue(row.bottom <= oldest.top + 0.5.dp, "the row is the transcript's first, above its oldest message: $row over $oldest")
            assertEquals(1, reached, "reaching it asks for the earlier messages once")
            onNodeWithTag(EARLIER_MESSAGES_TAG).performClick()
            assertEquals(1, tapped)
            // The earlier messages arrive above: the row being read stays exactly where it was.
            val state = list[0]!!
            val key = state.lazy.firstVisibleItemIndex
            val offset = state.lazy.firstVisibleItemScrollOffset
            val before = onNodeWithTag("m20").getBoundsInRoot()
            rows = rows + (30 until 50).map { "m$it" }
            waitForIdle()
            assertEquals(before, onNodeWithTag("m20").getBoundsInRoot(), "the place is kept")
            assertEquals(key, state.lazy.firstVisibleItemIndex)
            assertEquals(offset, state.lazy.firstVisibleItemScrollOffset)
        }
}
