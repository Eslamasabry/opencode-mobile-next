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
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Audit 1 (2026-11-14): after a send, for 6–15 s on a loaded phone, the new prompt lay under the previous answer's copy
 * and "…" icons, a step row sat on the bubble's line, and a list's end lay on another list's start ("60 sixty1 one").
 * A row on its way to a new place (the placement glide) or a leaving row fading out where it was lies over the row now
 * there. In the transcript a row appears where it belongs, below what is already there, and never lies over another,
 * on any frame, with animations at full.
 */
@OptIn(ExperimentalTestApi::class)
class Audit1RowsPlacedTest {
    private object Words : ChatTranscriptWords {
        override val jumpToLatest: String = "Jump to latest"

        override fun workLine(calls: Int, failed: Int, state: WorkState): String = "$calls steps"

        override fun notice(kind: NoticeKind): String = "Notice"

        override fun footer(failed: Boolean): String = "More"

        override fun thought(firstLine: String): String = firstLine
    }

    private val first: List<TranscriptRow> = turn(1) + turn(2) + listOf(prompt("u3", "ask 3"), block("u3", "a3", "p3", 0, "answer three"))

    /** Turn 3 as it was, and with a second part of its answer between the answer and the footer (a newer row). */
    private fun rows(second: Boolean): Array<TranscriptRow> =
        (first + listOfNotNull(if (second) block("u3", "a3", "p3b", 0, "second part") else null) + footer("u3")).toTypedArray()

    private fun ComposeUiTest.bounds(text: String): DpRect? {
        val nodes = onAllNodesWithText(text, substring = true, useUnmergedTree = true)
        return if (nodes.fetchSemanticsNodes().isEmpty()) null else nodes[0].getBoundsInRoot()
    }

    private fun ComposeUiTest.assertApart(what: String) {
        val answer = bounds("answer three") ?: error("$what: the answer is drawn")
        val second = bounds("second part") ?: return
        assertTrue(
            answer.bottom <= second.top + 0.5.dp || second.bottom <= answer.top + 0.5.dp,
            "$what: the rows lie over each other: answer $answer, second part $second",
        )
    }

    @Test
    fun `AUDIT1-03 a row that arrives or leaves never lies over its neighbour on any frame`() =
        runComposeUiTest {
            var state by mutableStateOf(stateOf(*rows(second = false)))
            setContent {
                KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Full), localeTag = "en") {
                    Box(Modifier.size(412.dp, 800.dp)) {
                        ChatTranscript(state, Words, rememberTranscriptListState(), rememberRevealRegistry(), KitMotionLevel.Full)
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            // A newer row arrives between the answer and the footer: the answer takes its new place above it.
            state = stateOf(*rows(second = true), revision = 2)
            repeat(12) { frame ->
                mainClock.advanceTimeByFrame()
                assertApart("arrival, frame $frame")
            }
            mainClock.autoAdvance = true
            waitForIdle()
            assertApart("arrival, settled")
            mainClock.autoAdvance = false
            // It leaves: no ghost of it lies where the answer comes down to.
            state = stateOf(*rows(second = false), revision = 3)
            repeat(12) { frame ->
                mainClock.advanceTimeByFrame()
                assertApart("removal, frame $frame")
            }
            mainClock.autoAdvance = true
            waitForIdle()
        }
}
