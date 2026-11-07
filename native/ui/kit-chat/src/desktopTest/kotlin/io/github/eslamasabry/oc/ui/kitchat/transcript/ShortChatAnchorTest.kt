package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * C-002 (parity audit 2026-11-07, Flutter chat_transcript.dart:153-155, a reversed list): a short conversation sits
 * on the composer with the empty space above it. Native hung it under the header (`Arrangement.Top` under
 * `reverseLayout`), leaving an empty band above the composer.
 */
@OptIn(ExperimentalTestApi::class)
class ShortChatAnchorTest {
    @Test
    fun `C-002 a short chat rests on the bottom edge, newest last, in reading order`() = runComposeUiTest {
        setContent {
            ChatTestTheme(dark = true) {
                KitTranscriptList(
                    rememberTranscriptListState(),
                    newestKey = "reply",
                    jumpLabel = "Jump to latest",
                    motion = KitMotionLevel.Off,
                    modifier = Modifier.width(400.dp).height(600.dp),
                ) {
                    item("reply") { KitText("Reply") }
                    item("prompt") { KitText("Prompt") }
                }
            }
        }
        val list = onNodeWithTag("transcript-list").getBoundsInRoot()
        val prompt = onNodeWithText("Prompt").getBoundsInRoot()
        val reply = onNodeWithText("Reply").getBoundsInRoot()
        assertTrue(prompt.bottom <= reply.top, "the prompt reads above its reply")
        // The list's bottom padding (S4 plus the screen chrome) is all that sits under the newest row.
        val under = list.bottom - reply.bottom
        assertTrue(under < 120.dp, "the newest row rests near the bottom (gap $under)")
        assertTrue(prompt.top - list.top > 400.dp, "the empty space is above the chat (gap ${prompt.top - list.top})")
    }
}
