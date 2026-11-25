package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.feature.chat.ui.ScrollPlaceStore
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.LocalTranscriptAnchor
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptAnchor
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ChatHybridPlaceTest {
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
        CompositionLocalProvider(LocalTranscriptAnchor provides TranscriptAnchor.Hybrid) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
                content = content,
            )
        }

    private val rows60 = turns(60)

    private fun TranscriptListState.firstKey(): Any? =
        lazy.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazy.firstVisibleItemIndex }?.key

    private fun ComposeUiTest.pump(frames: Int = SETTLE_FRAMES) {
        repeat(frames) { mainClock.advanceTimeByFrame() }
    }

    @Test
    fun `STEPS-SCROLL-010 hybrid place taken while reading comes back on the same row and offset`() =
        kitUiTest {
            ScrollPlaceStore.clear()
            var shown by mutableStateOf(true)
            lateinit var list: TranscriptListState
            mainClock.autoAdvance = false
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider("chat") {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(
                                    rows60,
                                    Words,
                                    list,
                                    rememberRevealRegistry(),
                                    KitMotionLevel.Off,
                                    placeKey = "p/hybrid-same",
                                )
                            }
                        }
                    }
                }
            }
            pump()
            list.following = false
            pump()
            runOnIdle { list.lazy.requestScrollToItem(30, 9) }
            pump()
            assertTrue(list.isNormalOrder)
            val key = list.firstKey()
            val offset = list.lazy.firstVisibleItemScrollOffset
            shown = false
            pump()
            shown = true
            pump()
            assertTrue(list.isNormalOrder)
            assertEquals(key, list.firstKey())
            assertEquals(offset, list.lazy.firstVisibleItemScrollOffset)
        }

    @Test
    fun `STEPS-SCROLL-011 hybrid place taken following comes back across orders`() =
        kitUiTest {
            ScrollPlaceStore.clear()
            var shown by mutableStateOf(true)
            var rows by mutableStateOf(turns(20))
            lateinit var list: TranscriptListState
            mainClock.autoAdvance = false
            setContent {
                Themed {
                    val holder = rememberSaveableStateHolder()
                    if (shown) {
                        holder.SaveableStateProvider("chat") {
                            list = rememberTranscriptListState()
                            Box(Modifier.size(412.dp, 800.dp)) {
                                ChatTranscript(
                                    rows,
                                    Words,
                                    list,
                                    rememberRevealRegistry(),
                                    KitMotionLevel.Off,
                                    placeKey = "p/hybrid-follow",
                                )
                            }
                        }
                    }
                }
            }
            pump()
            assertTrue(list.following)
            val targetText = "Answer number 20 with a few words"
            val beforeBottom = onNodeWithText(targetText).fetchSemanticsNode().boundsInRoot.bottom
            shown = false
            pump()
            rows = turns(22)
            pump()
            shown = true
            pump()
            assertTrue(list.isNormalOrder, "expected normal order after returning to older rows")
            assertFalse(list.following, "expected following == false after newer rows arrived")
            val afterBottom = onNodeWithText(targetText).fetchSemanticsNode().boundsInRoot.bottom
            val delta = abs(afterBottom - beforeBottom)
            assertTrue(
                delta <= TOLERANCE_PX,
                "expected '$targetText' bottom $beforeBottom +/-$TOLERANCE_PX, got $afterBottom (delta=$delta)",
            )
        }

    private companion object {
        const val SETTLE_FRAMES = 20
        const val TOLERANCE_PX = 0.5f
    }
}
