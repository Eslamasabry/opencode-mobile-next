package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
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
import kotlin.test.assertTrue

/**
 * B-530: smooth follow-scroll on rapid turn arrivals in a long chat instead of a 1-frame snap-up.
 * Covers rapid burst arrivals while following (0 JUMP / flicker findings), scrolled-up immobility
 * (owner decision #90), and immediate cancellation on user drag (`DragInteraction.Start`).
 */
@OptIn(ExperimentalTestApi::class)
class SmoothFollowScrollTest {
    private object Words : ChatTranscriptWords {
        override val jumpToLatest: String = "Jump to latest"

        override fun workLine(calls: Int, failed: Int, state: WorkState): String = "$calls steps"

        override fun notice(kind: NoticeKind): String = "Notice"

        override fun footer(failed: Boolean): String = "More"

        override fun thought(firstLine: String): String = firstLine
    }

    private fun turns(count: Int, revision: Long = count.toLong()): TranscriptState =
        stateOf(
            *(1..count).flatMap { n ->
                turn(
                    n,
                    ask = "Question number $n about the build pipeline",
                    answer = "Answer number $n describing the smooth transcript follow behaviour.",
                )
            }.toTypedArray(),
            revision = revision,
        )

    @Composable
    private fun Scene(
        state: TranscriptState,
        motion: KitMotionLevel = KitMotionLevel.Full,
        level: MotionLevel = MotionLevel.Full,
        onState: (TranscriptListState) -> Unit,
    ) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = level),
            localeTag = "en",
        ) {
            val list = rememberTranscriptListState()
            onState(list)
            Box(Modifier.size(360.dp, 780.dp)) {
                ChatTranscript(state, Words, list, rememberRevealRegistry(), motion)
            }
        }
    }

    private fun TranscriptListState.firstKey(): Any? =
        lazy.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazy.firstVisibleItemIndex }?.key

    private fun ComposeUiTest.dragTowardOlder(totalPx: Float) {
        onNodeWithTag("transcript-list").performTouchInput {
            down(center)
            moveBy(Offset(0f, viewConfiguration.touchSlop))
            repeat(10) { moveBy(Offset(0f, totalPx / 10f)) }
            advanceEventTime(200)
            up()
        }
    }

    @Test
    fun `B-530 rapid turn arrivals while following glide smoothly without 1-frame jump flicker`() =
        kitUiTest(360, 780) {
            var state by mutableStateOf(turns(20))
            lateinit var list: TranscriptListState
            setContent { Scene(state) { list = it } }
            waitForIdle()
            assertTrue(list.following)
            assertEquals(0, list.lazy.firstVisibleItemIndex)

            mainClock.autoAdvance = false
            repeat(6) { mainClock.advanceTimeByFrame() }

            // 15 rapid turn arrivals: first 5 spaced by 4 frames, then 10 in a tight 2-frame burst.
            for (n in 21..35) {
                state = turns(n, revision = n.toLong())
                val frames = if (n <= 25) 4 else 2
                repeat(frames) { mainClock.advanceTimeByFrame() }
            }
            repeat(80) { mainClock.advanceTimeByFrame() }

            mainClock.autoAdvance = true
            waitForIdle()
            assertTrue(list.following, "remains following after rapid turn arrivals")
            assertEquals(0, list.lazy.firstVisibleItemIndex, "settled on newest item")
            assertEquals(0, list.lazy.firstVisibleItemScrollOffset, "settled at offset 0")
            onNodeWithText("Answer number 35", substring = true, useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-530 scrolled-up reader stays completely still when new turns arrive`() =
        kitUiTest(360, 780) {
            var state by mutableStateOf(turns(30))
            lateinit var list: TranscriptListState
            setContent { Scene(state) { list = it } }
            waitForIdle()

            dragTowardOlder(420f)
            waitForIdle()
            assertFalse(list.following, "dragged into history turns following off")
            assertTrue(list.lazy.firstVisibleItemIndex > 0, "scrolled away from newest row")

            val anchoredKey = list.firstKey()
            val anchoredOffset = list.lazy.firstVisibleItemScrollOffset
            val visibleAnswer =
                list.lazy.layoutInfo.visibleItemsInfo
                    .firstNotNullOf { info ->
                        val key = info.key as? String ?: return@firstNotNullOf null
                        val num = key.removePrefix("b:a").substringBefore('/').toIntOrNull()
                        if (key.startsWith("b:a") && num != null) "Answer number $num" else null
                    }
            val beforeBounds =
                onNodeWithText(visibleAnswer, substring = true, useUnmergedTree = true).getBoundsInRoot()

            mainClock.autoAdvance = false
            for (n in 31..35) {
                state = turns(n, revision = n.toLong())
                repeat(4) { frame ->
                    mainClock.advanceTimeByFrame()
                    assertFalse(list.following, "still reading history on turn $n frame $frame")
                    assertEquals(anchoredKey, list.firstKey(), "anchored row key moved on turn $n frame $frame")
                    assertEquals(
                        anchoredOffset,
                        list.lazy.firstVisibleItemScrollOffset,
                        "anchored offset moved on turn $n frame $frame",
                    )
                    val now =
                        onNodeWithText(visibleAnswer, substring = true, useUnmergedTree = true).getBoundsInRoot()
                    assertEquals(beforeBounds.top, now.top, "visible row moved on turn $n frame $frame")
                }
            }
            repeat(20) { mainClock.advanceTimeByFrame() }
            assertEquals(anchoredKey, list.firstKey(), "anchored row key kept after settle")
            assertEquals(anchoredOffset, list.lazy.firstVisibleItemScrollOffset, "offset kept after settle")
            mainClock.autoAdvance = true
            waitForIdle()
        }

    @Test
    fun `B-530 user drag cancels in-flight smooth follow-scroll immediately`() =
        kitUiTest(360, 780) {
            var state by mutableStateOf(turns(25))
            lateinit var list: TranscriptListState
            setContent { Scene(state) { list = it } }
            waitForIdle()
            assertTrue(list.following)

            mainClock.autoAdvance = false
            repeat(4) { mainClock.advanceTimeByFrame() }

            // Prepend a burst of turns to kick off smooth follow-scroll, then drag mid-flight.
            state = turns(30, revision = 30)
            repeat(2) { mainClock.advanceTimeByFrame() }
            assertTrue(
                list.lazy.firstVisibleItemIndex > 0,
                "prior newest row is still gliding smoothly rather than snapped in 1 frame",
            )

            dragTowardOlder(240f)
            mainClock.advanceTimeByFrame()
            assertFalse(list.following, "DragInteraction.Start turned following off immediately")

            val pinnedKey = list.firstKey()
            val pinnedOffset = list.lazy.firstVisibleItemScrollOffset
            assertTrue(list.lazy.firstVisibleItemIndex > 0, "reader stayed up in the list")

            // More turns arrive after the cancelled follow-scroll: nothing moves.
            state = turns(33, revision = 33)
            repeat(40) { mainClock.advanceTimeByFrame() }
            assertFalse(list.following, "cancelled follow-scroll never re-engaged")
            assertEquals(pinnedKey, list.firstKey(), "pinned row stayed put after cancel")
            assertEquals(pinnedOffset, list.lazy.firstVisibleItemScrollOffset, "offset stayed put after cancel")

            mainClock.autoAdvance = true
            waitForIdle()
        }
}
