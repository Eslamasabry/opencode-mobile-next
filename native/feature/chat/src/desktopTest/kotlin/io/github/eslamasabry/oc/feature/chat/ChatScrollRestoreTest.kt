package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P12-CHAT-FEEL: a reader's place survives rotation and coming back to the chat (the list state is saveable and the
 * app's Nav3 entries keep saveable state). A reader who scrolled up stays there and is not pulled to the bottom; a
 * reader at the bottom stays following.
 */
@OptIn(ExperimentalTestApi::class)
class ChatScrollRestoreTest {
    private object Words : ChatTranscriptWords {
        override val jumpToLatest: String = "Jump to latest"

        override fun workLine(calls: Int, failed: Int, state: WorkState): String = "$calls steps"

        override fun notice(kind: NoticeKind): String = "Notice"

        override fun footer(failed: Boolean): String = "More"

        override fun thought(firstLine: String): String = firstLine
    }

    private val state = stateOf(*(1..60).flatMap { turn(it, answer = "Answer number $it with a few words") }.toTypedArray())

    @Test
    fun `09-7_3 a reader's place and follow choice survive leaving the chat and coming back`() =
        runComposeUiTest {
            // The Nav3 entry decorator keeps each entry's saveable state in a SaveableStateHolder; rotation saves
            // the same registry into the Activity's bundle.
            var shown by mutableStateOf(true)
            lateinit var list: TranscriptListState
            setContent {
                KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider("chat") {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(state, Words, list, rememberRevealRegistry(), KitMotionLevel.Off)
                            }
                        }
                    }
                }
            }
            waitForIdle()
            assertTrue(list.following)
            list.following = false
            runOnIdle { list.lazy.requestScrollToItem(40, 12) }
            waitForIdle()
            val index = list.lazy.firstVisibleItemIndex
            assertTrue(index >= 40)
            val before = list
            shown = false
            waitForIdle()
            shown = true
            waitForIdle()
            assertTrue(list !== before, "a new list state, restored from saved state")
            assertEquals(index, list.lazy.firstVisibleItemIndex, "the reader is where they were")
            assertFalse(list.following, "and is not pulled to the newest reply")
        }
}
