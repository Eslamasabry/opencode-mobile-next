package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.feature.chat.ui.FOLDS_SAVED_PER_KIND
import io.github.eslamasabry.oc.feature.chat.ui.FindJump
import io.github.eslamasabry.oc.feature.chat.ui.rememberTranscriptFolds
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptList
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The conversation switcher's Back rule: you return to what you left, exactly as you left it. Each page is dropped
 * from composition when left and rebuilt from saved state (the Nav3 entry decorator's SaveableStateHolder).
 */
@OptIn(ExperimentalTestApi::class)
class ChatPlaceKeptTest {
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

    private fun TranscriptListState.firstKey(): Any? =
        lazy.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazy.firstVisibleItemIndex }?.key

    @Test
    fun `PLACE-1 rows that arrive below while the person is away do not move what they were reading`() =
        runComposeUiTest {
            var shown by mutableStateOf(true)
            var rows by mutableStateOf(turns(60))
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider("chat") {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(rows, Words, list, rememberRevealRegistry(), KitMotionLevel.Off)
                            }
                        }
                    }
                }
            }
            waitForIdle()
            list.following = false
            runOnIdle { list.lazy.requestScrollToItem(40, 12) }
            waitForIdle()
            val key = list.firstKey()
            val offset = list.lazy.firstVisibleItemScrollOffset
            assertTrue(key is String)
            shown = false
            waitForIdle()
            // Three more turns land below while the person is on another page.
            rows = turns(63)
            waitForIdle()
            shown = true
            waitForIdle()
            assertEquals(key, list.firstKey(), "the same row is at the top of the view")
            assertEquals(offset, list.lazy.firstVisibleItemScrollOffset, "and at the same offset into it")
            assertFalse(list.following, "the person is not pulled to the newest message")
            assertNotEquals(0, list.lazy.firstVisibleItemIndex)
        }

    @Test
    fun `PLACE-1 a person who was at the bottom still follows the newest message on return`() =
        runComposeUiTest {
            var shown by mutableStateOf(true)
            var rows by mutableStateOf(turns(60))
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider("chat") {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(rows, Words, list, rememberRevealRegistry(), KitMotionLevel.Off)
                            }
                        }
                    }
                }
            }
            waitForIdle()
            assertTrue(list.following)
            shown = false
            waitForIdle()
            rows = turns(63)
            waitForIdle()
            shown = true
            waitForIdle()
            assertTrue(list.following)
            assertEquals(0, list.lazy.firstVisibleItemIndex, "the newest message is on the bottom edge")
        }

    private fun find(index: Int, current: Int, query: String = "a") =
        FindUi(query, count = 5, current = current, currentKey = "k$index", currentIndex = index, partial = false)

    @Test
    fun `PLACE-2 coming back with Find open leaves the view; moving to another hit or changing the query jumps`() =
        runComposeUiTest {
            var shown by mutableStateOf(true)
            var found by mutableStateOf(find(50, 0))
            lateinit var list: TranscriptListState
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider("chat") {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 400.dp)) {
                                KitTranscriptList(list, "n", "Jump", KitMotionLevel.Off) {
                                    items(120, key = { "k$it" }) { Box(Modifier.height(40.dp)) }
                                }
                            }
                            FindJump(found, loaded = true, itemsBelowRows = 0, list = list)
                        }
                    }
                }
            }
            waitForIdle()
            assertTrue(list.lazy.firstVisibleItemIndex in 40..60, "the first hit was brought into view")
            // The person scrolls on, then leaves and comes back with Find still open.
            runOnIdle { list.lazy.requestScrollToItem(15) }
            waitForIdle()
            shown = false
            waitForIdle()
            shown = true
            waitForIdle()
            assertEquals(15, list.lazy.firstVisibleItemIndex, "no jump back to the hit")
            // Another hit: jump.
            found = find(80, 1)
            waitForIdle()
            assertTrue(list.lazy.firstVisibleItemIndex in 70..90)
            // Same hit position, new query: jump again.
            runOnIdle { list.lazy.requestScrollToItem(15) }
            waitForIdle()
            found = find(80, 1, query = "ab")
            waitForIdle()
            assertTrue(list.lazy.firstVisibleItemIndex in 70..90)
        }

    @Test
    fun `PLACE-3 what the person opened stays open after leaving and coming back, and the saved set is bounded`() =
        runComposeUiTest {
            var shown by mutableStateOf(true)
            lateinit var folds: io.github.eslamasabry.oc.feature.chat.ui.TranscriptFolds
            setContent {
                val holder = rememberSaveableStateHolder()
                if (shown) holder.SaveableStateProvider("chat") { folds = rememberTranscriptFolds() }
            }
            waitForIdle()
            runOnIdle {
                folds.open["work:turn-1"] = true
                folds.open["thought:m1/p1"] = true
                folds.full["step:call-1"] = true
                folds.earlier["work:turn-1"] = 3
                for (i in 0 until FOLDS_SAVED_PER_KIND + 100) folds.open["old:$i"] = false
            }
            shown = false
            waitForIdle()
            shown = true
            waitForIdle()
            assertEquals(true, folds.open["work:turn-1"])
            assertEquals(true, folds.open["thought:m1/p1"])
            assertEquals(true, folds.full["step:call-1"])
            assertEquals(3, folds.earlier["work:turn-1"])
            assertEquals(FOLDS_SAVED_PER_KIND, folds.open.size, "the saved set never grows without bound")
        }
}
