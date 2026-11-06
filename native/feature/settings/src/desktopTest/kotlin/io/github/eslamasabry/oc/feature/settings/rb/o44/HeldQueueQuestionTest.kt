package io.github.eslamasabry.oc.feature.settings.rb.o44

import io.github.eslamasabry.oc.feature.settings.presentation.LocalDataFacts
import io.github.eslamasabry.oc.feature.settings.presentation.LocalDataKind
import io.github.eslamasabry.oc.feature.settings.presentation.localDataQuestion
import kotlin.test.Test
import kotlin.test.assertEquals

/** N-48 (E20-S01): Clear queued prompts while a delivery needs review asks once, then discards. */
class HeldQueueQuestionTest {
    @Test
    fun `B-STATE-080 with a prompt kept for review the one question deletes them all`() {
        assertEquals(
            LocalDataKind.QueueHeld,
            localDataQuestion(LocalDataKind.Queue, LocalDataFacts(queued = 2, held = 1)),
        )
    }

    @Test
    fun `B-STATE-080 with nothing kept for review only unsent prompts are asked about`() {
        assertEquals(LocalDataKind.Queue, localDataQuestion(LocalDataKind.Queue, LocalDataFacts(queued = 2)))
        assertEquals(LocalDataKind.Queue, localDataQuestion(LocalDataKind.Queue, null), "facts not read yet")
        assertEquals(
            LocalDataKind.Drafts,
            localDataQuestion(LocalDataKind.Drafts, LocalDataFacts(queued = 2, held = 2)),
            "drafts are never widened",
        )
    }
}
