package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatListWords
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptList
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * F28 (the first send, 10114 J02): when the run starts, "Thinking…" was a separate row under the pending bubble, which
 * still said "Sending": for a moment both lines showed and the bubble rode up a line in one frame. There is ONE status
 * slot under the bubble: "Sending" and "Thinking…" take the same line in turn, and when the bubble settles into the
 * server's row the live line stands in the same place. The bubble never moves.
 */
@OptIn(ExperimentalTestApi::class)
class OneStatusSlotTest {
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

    private val ask = "Fix the flaky checkout test"
    private val pending = persistentListOf(PendingSend(localId = 1L, text = ask, state = PendingState.Sending))
    private val serverRows: List<TranscriptRow> = listOf(prompt("u1", ask))

    private fun ComposeUiTest.top(text: String): Float {
        val nodes = onAllNodesWithText(text, substring = true, useUnmergedTree = true)
        assertTrue(nodes.fetchSemanticsNodes().isNotEmpty(), "\"$text\" is drawn")
        return nodes[0].getBoundsInRoot().top.value
    }

    /** What a person sees as a status under the bubble: a line that says one of the words of the first send. */
    private fun ComposeUiTest.statusLines(): Int =
        listOf("Sending", "Thinking").sumOf {
            onAllNodesWithText(it, substring = true, useUnmergedTree = false).fetchSemanticsNodes().size
        }

    private fun scene(
        pendingNow: () -> kotlinx.collections.immutable.ImmutableList<PendingSend>,
        liveNow: () -> LiveTurnUi?,
        rowsNow: () -> List<TranscriptRow>,
    ): @androidx.compose.runtime.Composable () -> Unit = {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Full),
            localeTag = "en",
        ) {
            Box(Modifier.size(412.dp, 800.dp)) {
                KitStandaloneRedaction {
                    ChatTranscriptList(
                        rows = stateOf(*rowsNow().toTypedArray()).rows,
                        live = liveNow(),
                        pending = pendingNow(),
                        words = Words,
                        listWords = listWords,
                        list = rememberTranscriptListState(),
                        reveal = rememberRevealRegistry(),
                        motion = KitMotionLevel.Full,
                        onOpenMenu = null,
                        onRetrySend = {},
                    )
                }
            }
        }
    }

    @Test
    fun `B-CHAT-307 the pending bubble stays where it is when the run starts and when the server row takes over`() =
        runComposeUiTest {
            var live by mutableStateOf<LiveTurnUi?>(null)
            var sent by mutableStateOf(pending)
            var rows by mutableStateOf<List<TranscriptRow>>(emptyList())
            setContent { scene({ sent }, { live }, { rows })() }
            waitForIdle()
            val rest = top(ask)
            mainClock.autoAdvance = false
            // The run starts: "Thinking" takes the bubble's own status line.
            live = LiveTurnUi(stopping = false)
            repeat(14) { frame ->
                mainClock.advanceTimeByFrame()
                assertEquals(rest, top(ask), "the bubble's top moved in frame $frame of the run starting")
            }
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(rest, top(ask), "the bubble's top moved when the run started")
            // The server's copy replaces the bubble: the prompt stands in its place.
            mainClock.autoAdvance = false
            sent = persistentListOf()
            rows = serverRows
            repeat(14) { frame ->
                mainClock.advanceTimeByFrame()
                assertEquals(rest, top(ask), "the prompt is not where the bubble was in frame $frame of the hand-over")
            }
        }

    @Test
    fun `B-CHAT-308 there is exactly one status line under the bubble in every frame of the first send`() =
        runComposeUiTest {
            var live by mutableStateOf<LiveTurnUi?>(null)
            var sent by mutableStateOf(pending)
            var rows by mutableStateOf<List<TranscriptRow>>(emptyList())
            setContent { scene({ sent }, { live }, { rows })() }
            waitForIdle()
            assertEquals(1, statusLines(), "one status line while it says Sending")
            mainClock.autoAdvance = false
            live = LiveTurnUi(stopping = false)
            repeat(14) { frame ->
                mainClock.advanceTimeByFrame()
                assertEquals(1, statusLines(), "frame $frame of the run starting")
            }
            sent = persistentListOf()
            rows = serverRows
            repeat(14) { frame ->
                mainClock.advanceTimeByFrame()
                assertEquals(1, statusLines(), "frame $frame of the hand-over")
            }
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(1, statusLines(), "one status line once the server's row stands")
        }
}
