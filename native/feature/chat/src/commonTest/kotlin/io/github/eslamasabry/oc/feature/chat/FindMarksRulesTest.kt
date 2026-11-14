package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.FindHit
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDetailUi
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptFind
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptReadingUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.findFoldKeys
import io.github.eslamasabry.oc.feature.chat.ui.findScrollDelta
import kotlin.test.Test
import kotlin.test.assertEquals

/** A2-02: the rules behind the marks Find leaves in the transcript. */
class FindMarksRulesTest {
    @Test
    fun `A2-02 the current match is counted among the hits of its own row and text`() {
        val hits =
            listOf(
                FindHit("b:1", 0, 4),
                FindHit("b:1", 10, 14),
                FindHit("work:t", 0, 4, sourceId = "m/p1"),
                FindHit("b:2", 3, 7),
                FindHit("b:2", 30, 34),
            )
        assertEquals(0, TranscriptFind.ordinalOf(hits, 0))
        assertEquals(1, TranscriptFind.ordinalOf(hits, 1))
        assertEquals(0, TranscriptFind.ordinalOf(hits, 2))
        assertEquals(0, TranscriptFind.ordinalOf(hits, 3))
        assertEquals(1, TranscriptFind.ordinalOf(hits, 4))
        assertEquals(0, TranscriptFind.ordinalOf(hits, -1))
    }

    @Test
    fun `A2-02 a match inside a step opens the work line and that step, a message's own words open nothing`() {
        val facts = TurnFactsUi()
        assertEquals(listOf("work:t1", "step:p9"), findFoldKeys("work:t1", "m1/p9", "t1", facts))
        assertEquals(emptyList(), findFoldKeys("b:m1/p1:0", "b:m1/p1:0", "t1", facts))
        assertEquals(emptyList(), findFoldKeys("prompt:t1", "prompt:t1", "t1", facts))
    }

    @Test
    fun `A2-02 a match inside a thought opens the thought and the work line that holds it`() {
        val part = io.github.eslamasabry.oc.core.model.transcript.Part(
            "p3", "m1", "s", io.github.eslamasabry.oc.core.model.transcript.PartType.Reasoning, "reasoning", text = "hmm",
        )
        val detail = TranscriptDetailUi("detail:m1/p3", part)
        val facts = TurnFactsUi(reading = TranscriptReadingUi(details = mapOf("thought:m1/p3" to detail)))
        assertEquals(listOf("detail:m1/p3", "work:t1"), findFoldKeys("thought:m1/p3", "m1/p3", "t1", facts))
    }

    @Test
    fun `A2-02 a row that fits is centred between the bars, a taller one starts at the top edge`() {
        // Room 1000, a row 200 tall resting on the bottom edge (offset 0): move it up by 400.
        assertEquals(-400f, findScrollDelta(0, 200, 1000))
        // Already centred: no move.
        assertEquals(0f, findScrollDelta(400, 200, 1000))
        // Taller than the room: its top goes to the room's top edge.
        assertEquals(500f, findScrollDelta(0, 1500, 1000))
        assertEquals(0f, findScrollDelta(0, 1000, 1000))
    }
}
