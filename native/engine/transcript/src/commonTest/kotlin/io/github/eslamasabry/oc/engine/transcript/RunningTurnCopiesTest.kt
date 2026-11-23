package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * PB-19: while a run streams, every publish rebuilds the newest turn, but a step's or a thought's
 * unchanged text must not be copied again (the OOM of a long build printing). The counters pin it:
 * a running turn of 100 finished steps and 100 thoughts around a growing reply, then deltas that
 * touch none of their parts.
 */
class RunningTurnCopiesTest {
    private val counters = TranscriptCounters()
    private val model = TranscriptModel(S, counters)
    private var revision = 0L

    private fun derive(): TranscriptState = model.derive(++revision)

    /** The running turn the lane names: a prompt, a live reply, 100 finished steps, 100 thoughts. */
    private fun openRunningTurn() {
        model.apply(GatewayEvent.RunStarted(S))
        Ev.prompt("p", "Build it").forEach(model::apply)
        model.apply(Ev.assistant("a"))
        for (i in 0 until 100) {
            model.apply(
                GatewayEvent.PartUpserted(
                    Part(
                        "tool-$i",
                        "a",
                        S,
                        PartType.Tool,
                        toolName = "bash",
                        toolStatus = ToolStatus.Completed,
                        ended = true,
                        searchText = "x".repeat(10_000),
                    )
                )
            )
            model.apply(
                GatewayEvent.PartUpserted(
                    Part("reasoning-$i", "a", S, PartType.Reasoning, text = "Thinking $i\n" + "y".repeat(10_000))
                )
            )
        }
        model.apply(Ev.text("a", "t"))
        derive()
    }

    @Test
    fun `B-CHAT-096 PB-19 a running turn's steps and thoughts are copied once, not per publish`() {
        openRunningTurn()
        val searchBefore = counters.searchChars
        val thoughtsBefore = counters.thoughtChars
        repeat(50) {
            model.apply(Ev.delta("a", "t", "word "))
            derive()
        }
        assertEquals(
            0L,
            counters.searchChars - searchBefore,
            "search chars copied while only the reply's text grew",
        )
        assertEquals(
            0L,
            counters.thoughtChars - thoughtsBefore,
            "thought chars copied while only the reply's text grew",
        )
    }

    @Test
    fun `B-CHAT-096 PB-19 a changed step or thought is copied again and Find sees it`() {
        openRunningTurn()
        // One thought grows: only its text is copied again, and Find's entry carries the new words.
        val thoughts0 = counters.thoughtChars
        val search0 = counters.searchChars
        model.apply(Ev.delta("a", "reasoning-5", " mark-R"))
        val grown = derive()
        val thought = assertNotNull(grown.rows[grown.indexOf("thought:a/reasoning-5")] as? ThoughtRow)
        assertTrue(thought.text.endsWith("mark-R"), "the row shows the appended words")
        assertEquals(
            thought.text.length.toLong(),
            counters.thoughtChars - thoughts0,
            "thought chars copied for the one changed thought",
        )
        assertEquals(0L, counters.searchChars - search0, "a changed thought copies no search join")
        assertTrue(
            grown.searchText.first { it.sourceId == "a/reasoning-5" }.text.endsWith("mark-R"),
            "Find sees the thought's new words",
        )
        // One step is replaced: only its search text is joined again, and Find's entry carries it.
        val thoughts1 = counters.thoughtChars
        val search1 = counters.searchChars
        model.apply(
            GatewayEvent.PartUpserted(
                Part(
                    "tool-7",
                    "a",
                    S,
                    PartType.Tool,
                    toolName = "bash",
                    toolStatus = ToolStatus.Completed,
                    ended = true,
                    searchText = "z".repeat(5_000) + "mark-T",
                )
            )
        )
        val replaced = derive()
        assertEquals(5_006L, counters.searchChars - search1, "search chars copied for the one replaced step")
        assertEquals(0L, counters.thoughtChars - thoughts1, "a replaced step copies no thought")
        assertTrue(
            replaced.searchText.first { it.sourceId == "a/tool-7" }.text.endsWith("mark-T"),
            "Find sees the step's new words",
        )
    }
}
