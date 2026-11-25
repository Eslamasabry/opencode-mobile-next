package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.RetryBannerFacts
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
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
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * B-CHAT-547 (owner decision O2 = D, 2026-11-24): the docked "Jump to latest" row repeats the running reply's live
 * line at its start, in the live line's own place. A long history with a reply running rests at its newest end; the
 * person drags the list 140 dp up the conversation: the row's line stands exactly where the list's stood.
 *
 * The lead's two rules of 2026-11-25, proved on the chat's own transcript: one live line is on screen at a time (the
 * row carries the status only while the list's own line lies in the row's clear band), and the status it carries is
 * the short word the live line already has for the turn's state.
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

    /** A turn as the transcript sees it: the run, the notes about it, and the short status the docked row shows. */
    private data class State(val live: LiveTurnUi, val facts: TurnFactsUi, val status: String)

    /**
     * The transcript on a bare page (no chrome): the list rests on the page's bottom edge. [room]: the edge dock's
     * room under the newest content, px (the Undo bar's); [facts]: the notes about the running turn.
     */
    @Composable
    private fun Page(list: TranscriptListState, live: LiveTurnUi, facts: TurnFactsUi, room: Int) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                KitStandaloneRedaction {
                    ChatTranscriptList(
                        rows = history.rows,
                        live = live,
                        pending = persistentListOf(),
                        words = Words,
                        listWords = listWords,
                        list = list,
                        reveal = rememberRevealRegistry(),
                        motion = KitMotionLevel.Off,
                        onOpenMenu = null,
                        onRetrySend = {},
                        facts = facts,
                        extraBottomPx = room,
                    )
                }
            }
        }
    }

    /** The transcript with [state]'s turn running, over [room] px of the edge dock's room: then [body]. */
    private fun withTranscript(state: State, room: Int = 0, body: ComposeUiTest.(TranscriptListState) -> Unit) =
        kitUiTest(width = WIDTH, height = HEIGHT) {
            lateinit var list: TranscriptListState
            setContent {
                list = rememberTranscriptListState()
                Page(list, state.live, state.facts, room)
            }
            waitForIdle()
            body(list)
        }

    /** The list at its twelfth row, not following (a scroll from code keeps following, so it is turned off by hand). */
    private fun ComposeUiTest.readOlder(list: TranscriptListState) {
        onNodeWithTag("transcript-list").performScrollToIndex(12)
        runOnUiThread { list.following = false }
        waitForIdle()
    }

    private val running = State(LiveTurnUi(stopping = false), TurnFactsUi.Empty, "Thinking…")

    @Test
    fun `B-CHAT-547 the live line keeps its exact place when the person scrolls up`() =
        withTranscript(running) { list ->
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

    @Test
    fun `B-CHAT-547 frame by frame the row shows only the jump while a room under the live line keeps it on screen`() {
        // Nothing under the live line: it lies in the row's clear band and the row carries its status.
        // The edge dock's Undo bar takes room under it: the line rests above the band, on screen.
        for (room in listOf(0, ROOM_PX)) {
            withTranscript(running, room) { list ->
                mainClock.autoAdvance = false
                runOnUiThread { list.following = false }
                repeat(30) { frame ->
                    mainClock.advanceTimeByFrame()
                    assertOneLiveLine(room, frame)
                }
            }
        }
    }

    /** Frame [frame] of the row standing while the person does not follow: one live line, where [room] puts it. */
    private fun ComposeUiTest.assertOneLiveLine(room: Int, frame: Int) {
        val row = onNodeWithTag("jump-to-latest", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val own = onNodeWithTag("chat-live-line", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val carried = onAllNodesWithTag("transcript-dock-live", useUnmergedTree = true).fetchSemanticsNodes()
        val dot = onAllNodesWithTag("transcript-dock-dot", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(dot.isEmpty(), "room $room: the row shows the bare dot in frame $frame")
        if (room > 0) {
            assertTrue(own.bottom <= row.top, "room $room: the live line is not above the row: $own, $row")
            assertTrue(
                carried.isEmpty(),
                "room $room: frame $frame repeats the live status while the list's own line is on screen",
            )
        } else {
            assertTrue(
                carried.isNotEmpty(),
                "room $room: frame $frame carries no live status while the list's own line is cleared",
            )
        }
    }

    @Test
    fun `B-CHAT-546 the row's status is the short word the live line already has for the turn's state`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val retry = LiveNoticeUi.Retry(RetryBannerFacts(rateLimited = false, attempt = 2, secondsLeft = 5))
            val waiting = LiveNoticeUi.ToolWait(toolName = "read", waitedMs = 200_000)
            val states =
                listOf(
                    running,
                    // The list's own line says "Retrying 2 in 0:05".
                    State(LiveTurnUi(stopping = false), TurnFactsUi(live = retry), "Retrying…"),
                    // The list's own line says "Still waiting for read.".
                    State(LiveTurnUi(stopping = false), TurnFactsUi(live = waiting), "Waiting for you"),
                    State(LiveTurnUi(stopping = true), TurnFactsUi.Empty, "Stopping"),
                )
            for (state in states) withTranscript(state) { list -> assertStatusIs(list, state.status) }
        } finally {
            Locale.setDefault(before)
        }
    }

    /** Reading older rows: the docked row's status says exactly [status]. */
    private fun ComposeUiTest.assertStatusIs(list: TranscriptListState, status: String) {
        readOlder(list)
        val inTheRow = hasText(status) and hasAnyAncestor(hasTestTag("transcript-dock-live"))
        onNode(inTheRow, useUnmergedTree = true).assertExists()
    }

    private companion object {
        const val WIDTH = 412
        const val HEIGHT = 800

        /** The edge dock's room under the newest content, px: taller than the row, so the live line rests above it. */
        const val ROOM_PX = 96
    }
}
