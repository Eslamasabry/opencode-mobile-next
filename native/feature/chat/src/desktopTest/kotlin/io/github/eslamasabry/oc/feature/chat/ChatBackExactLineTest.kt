package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.feature.chat.ui.ScrollPlaceStore
import io.github.eslamasabry.oc.feature.chat.ui.openChatFromNotification
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * B-407: Back returns to the exact line the person was reading. A page pushed over the chat drops it from composition
 * and rebuilds it from saved state (the Nav3 entry decorator's SaveableStateHolder); a conversation reopened from
 * Home is a new entry, so its place comes from the per-run store.
 */
@OptIn(ExperimentalTestApi::class)
class ChatBackExactLineTest {
    private object Words : ChatTranscriptWords {
        override val jumpToLatest: String = "Jump to latest"

        override fun workLine(calls: Int, failed: Int, state: WorkState): String = "$calls steps"

        override fun notice(kind: NoticeKind): String = "Notice"

        override fun footer(failed: Boolean): String = "More"

        override fun thought(firstLine: String): String = firstLine
    }

    private fun turns(count: Int) =
        stateOf(*(1..count).flatMap { turn(it, answer = "Answer number $it with a few words") }.toTypedArray())

    @Composable
    private fun Themed(content: @Composable () -> Unit) =
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
            content = content,
        )

    private val rows60 = turns(60)

    private fun TranscriptListState.firstKey(): Any? =
        lazy.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazy.firstVisibleItemIndex }?.key

    @Test
    fun `B-407 back to the same entry lands on the row and offset the person last saw`() =
        kitUiTest {
            ScrollPlaceStore.clear()
            var shown by mutableStateOf(true)
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider("chat") {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(rows60, Words, list, rememberRevealRegistry(), KitMotionLevel.Off, placeKey = "p/same")
                            }
                        }
                    }
                }
            }
            waitForIdle()
            list.following = false
            runOnIdle { list.lazy.requestScrollToItem(30, 9) }
            waitForIdle()
            val key = list.firstKey()
            val offset = list.lazy.firstVisibleItemScrollOffset
            shown = false
            waitForIdle()
            shown = true
            waitForIdle()
            assertEquals(key, list.firstKey())
            assertEquals(offset, list.lazy.firstVisibleItemScrollOffset)
        }

    @Test
    fun `B-407 a reader following the newest row lands where they were when rows arrived while away`() =
        kitUiTest {
            ScrollPlaceStore.clear()
            var shown by mutableStateOf(true)
            var rows by mutableStateOf(turns(20))
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider("chat") {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(rows, Words, list, rememberRevealRegistry(), KitMotionLevel.Off, placeKey = "p/follow")
                            }
                        }
                    }
                }
            }
            waitForIdle()
            assertTrue(list.following)
            val key = list.firstKey()
            assertTrue(key is String)
            shown = false
            waitForIdle()
            rows = turns(22)
            waitForIdle()
            shown = true
            waitForIdle()
            assertEquals(key, list.firstKey(), "the row they last saw is still the first one in view")
            assertNotEquals(0, list.lazy.firstVisibleItemIndex, "not pulled to the newest row")
            assertFalse(list.following, "so the list shows that more arrived below")
        }

    @Test
    fun `B-407 a conversation left scrolled up and opened again from Home returns to the same row and offset`() =
        kitUiTest {
            ScrollPlaceStore.clear()
            var entry by mutableStateOf("entry-1")
            var shown by mutableStateOf(true)
            var conversation by mutableStateOf("p/one")
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider(entry) {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(rows60, Words, list, rememberRevealRegistry(), KitMotionLevel.Off, placeKey = conversation)
                            }
                        }
                    }
                }
            }
            waitForIdle()
            list.following = false
            runOnIdle { list.lazy.requestScrollToItem(30, 9) }
            waitForIdle()
            val key = list.firstKey()
            val offset = list.lazy.firstVisibleItemScrollOffset
            assertNotEquals(0, list.lazy.firstVisibleItemIndex)
            // Home: the entry is gone; opening the conversation again is a new entry.
            shown = false
            waitForIdle()
            entry = "entry-2"
            shown = true
            waitForIdle()
            assertEquals(key, list.firstKey())
            assertEquals(offset, list.lazy.firstVisibleItemScrollOffset)
            assertFalse(list.following)
            // Another conversation has its own place: it opens on its newest row.
            shown = false
            waitForIdle()
            entry = "entry-3"
            conversation = "p/two"
            shown = true
            waitForIdle()
            assertEquals(0, list.lazy.firstVisibleItemIndex)
            assertTrue(list.following)
        }

    @Test
    fun `B-407 a conversation left while following opens on the newest row when opened again`() =
        kitUiTest {
            ScrollPlaceStore.clear()
            var entry by mutableStateOf("entry-1")
            var shown by mutableStateOf(true)
            var rows by mutableStateOf(turns(20))
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider(entry) {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(rows, Words, list, rememberRevealRegistry(), KitMotionLevel.Off, placeKey = "p/guard")
                            }
                        }
                    }
                }
            }
            waitForIdle()
            assertTrue(list.following)
            shown = false
            waitForIdle()
            rows = turns(22)
            entry = "entry-2"
            shown = true
            waitForIdle()
            assertEquals(0, list.lazy.firstVisibleItemIndex)
            assertTrue(list.following)
        }

    @Test
    fun `B-407 a reply notification opens a scrolled up conversation on the new reply`() =
        kitUiTest {
            ScrollPlaceStore.clear()
            var entry by mutableStateOf("entry-1")
            var shown by mutableStateOf(true)
            var rows by mutableStateOf(turns(20))
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider(entry) {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(
                                    rows,
                                    Words,
                                    list,
                                    rememberRevealRegistry(),
                                    KitMotionLevel.Off,
                                    placeKey = "p/notification",
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            list.following = false
            runOnIdle { list.lazy.requestScrollToItem(20, 9) }
            waitForIdle()
            assertNotEquals(0, list.lazy.firstVisibleItemIndex)
            val oldKey = list.firstKey()
            shown = false
            waitForIdle()
            rows = turns(22)
            val target = ConversationRef(ProfileId("p"), SessionId("notification"))
            // The same boundary the Android notification routes call; navigation opens a new entry.
            runOnIdle {
                openChatFromNotification(target) { ref ->
                    assertEquals(target, ref)
                    entry = "entry-2"
                    shown = true
                }
            }
            waitForIdle()
            assertEquals(
                0,
                list.lazy.firstVisibleItemIndex,
                "the notification opens the newest reply",
            )
            assertEquals(0, list.lazy.firstVisibleItemScrollOffset)
            assertNotEquals(oldKey, list.firstKey())
            assertTrue(list.following)
        }

    @Test
    fun `B-407 a reply notification for the visible conversation jumps to newest every time`() =
        kitUiTest {
            ScrollPlaceStore.clear()
            var rows by mutableStateOf(turns(20))
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    list = rememberTranscriptListState()
                    Box(Modifier.size(412.dp, 800.dp)) {
                        ChatTranscript(
                            rows,
                            Words,
                            list,
                            rememberRevealRegistry(),
                            KitMotionLevel.Off,
                            placeKey = "p/visible",
                        )
                    }
                }
            }
            waitForIdle()
            val target = ConversationRef(ProfileId("p"), SessionId("visible"))
            var opens = 0
            repeat(2) { reply ->
                list.following = false
                runOnIdle { list.lazy.requestScrollToItem(20, 9) }
                waitForIdle()
                assertNotEquals(0, list.lazy.firstVisibleItemIndex)
                rows = turns(21 + reply)
                waitForIdle()
                // A different conversation's notification must leave this reader alone.
                val oldKey = list.firstKey()
                runOnIdle { ScrollPlaceStore.forget("p/other") }
                waitForIdle()
                assertEquals(oldKey, list.firstKey())
                assertFalse(list.following)
                // openChat is a no-op when this conversation is already on top.
                runOnIdle {
                    openChatFromNotification(target) { ref ->
                        assertEquals(target, ref)
                        opens++
                    }
                }
                waitForIdle()
                assertEquals(reply + 1, opens)
                assertEquals(
                    0,
                    list.lazy.firstVisibleItemIndex,
                    "the visible conversation shows the new reply",
                )
                assertEquals(0, list.lazy.firstVisibleItemScrollOffset)
                assertTrue(list.following)
            }
        }
}
