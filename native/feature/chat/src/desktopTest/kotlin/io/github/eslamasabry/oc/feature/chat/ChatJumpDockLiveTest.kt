package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatListWords
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptList
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.turn.LIVE_LINE_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * B-CHAT-547 (owner decision O2 = D, 2026-11-24): the docked "Jump to latest" row repeats the running reply's live
 * line at its start, in the live line's own place. A long history with a reply running rests at its newest end; the
 * person drags the list 140 dp up the conversation: the row's line stands exactly where the list's stood.
 */
@OptIn(ExperimentalTestApi::class)
class ChatJumpDockLiveTest {
    private object Words : ChatTranscriptWords {
        override val jumpToLatest: String = "Jump to latest"

        override fun workLine(calls: Int, failed: Int, state: WorkState): String = "$calls steps"

        override fun notice(kind: NoticeKind): String = "Notice"

        override fun footer(failed: Boolean): String = "More"

        override fun thought(firstLine: String): String = firstLine
    }

    private val listWords =
        ChatListWords(
            messageActions = "Message actions",
            working = "Working",
            stopping = "Stopping",
            sending = "Sending",
            notSent = "Not sent",
            unconfirmed = "Not confirmed",
            tryAgain = "Try again",
        )

    private val history =
        stateOf(*(1..60).flatMap { turn(it, answer = "Answer number $it with a few words") }.toTypedArray())

    /** The transcript on a bare page (no chrome): the list rests on the page's bottom edge. */
    @Composable
    private fun Page(list: TranscriptListState) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.size(412.dp, 800.dp)) {
                KitStandaloneRedaction {
                    ChatTranscriptList(
                        rows = history.rows,
                        live = LiveTurnUi(stopping = false),
                        pending = persistentListOf(),
                        words = Words,
                        listWords = listWords,
                        list = list,
                        reveal = rememberRevealRegistry(),
                        motion = KitMotionLevel.Off,
                        onOpenMenu = null,
                        onRetrySend = {},
                    )
                }
            }
        }
    }

    @Test
    fun `B-CHAT-547 the live line keeps its exact place when the person scrolls up`() =
        kitUiTest(width = 412, height = 800) {
            lateinit var list: TranscriptListState
            setContent {
                list = rememberTranscriptListState()
                Page(list)
            }
            waitForIdle()
            assertTrue(list.following, "the list rests at its newest end")
            // The live line at rest: one line says the live words.
            val rest = onNodeWithTag(LIVE_LINE_TAG).fetchSemanticsNode().boundsInRoot
            // The person reads older rows: the finger moves down and the list goes 140 dp up the conversation, slowly;
            // it rests before the finger lifts, so nothing flings.
            onNodeWithTag("transcript-list").performTouchInput {
                down(center)
                moveBy(Offset(0f, viewConfiguration.touchSlop))
                repeat(14) { moveBy(Offset(0f, 10.dp.toPx())) }
                advanceEventTime(300)
                up()
            }
            waitForIdle()
            assertFalse(list.following, "the person is reading older rows")
            val dock = onNodeWithTag("transcript-dock-live", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(rest.top, dock.top, 1f, "the live line's top: $rest rested, $dock in the row")
            assertEquals(rest.left, dock.left, 1f, "the live line's start: $rest rested, $dock in the row")
        }
}
