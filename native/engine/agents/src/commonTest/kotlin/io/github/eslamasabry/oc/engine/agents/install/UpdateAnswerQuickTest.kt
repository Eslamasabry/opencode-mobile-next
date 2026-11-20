package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FLICKER F14: the sheet's answer is not held behind the phone's slow version read (it spun for 6.4 s). */
class UpdateAnswerQuickTest {
    private class Host(private val gate: CompletableDeferred<Unit>?) : AgentChoiceHost {
        var reads = 0
        var slowFrom = Int.MAX_VALUE

        override suspend fun inventory(): AgentUpdateInventory {
            reads++
            if (reads >= slowFrom) gate?.await()
            return AgentUpdateInventory(
                items = listOf(AgentUpdateItem(
                    id = AgentId("claude"), name = "claude", fromVersion = "1", toVersion = "2",
                    downloadBytes = 1_000L, installedBytes = null, group = AgentUpdateGroup.Optional,
                    keepAllowed = true, removeAllowed = true, notKeptReason = null, choice = null, neededWhen = null,
                )),
                network = AgentUpdateNetwork.Unmetered,
                freeBytes = null,
            )
        }

        override suspend fun hasRoom(id: AgentId) = true
        override suspend fun busy() = false
        override suspend fun remove(id: AgentId): Outcome<RemovalResult> = error("not used")
    }

    @Test
    fun `B-DL-430 answering the sheet does not read the phone's versions again`() = runTest {
        val host = Host(null)
        val engine = AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), host)
        engine.refresh()
        assertEquals(1, host.reads)
        val answer = engine.decide(mapOf(AgentId("claude") to AgentUpdateChoice.Update))
        assertTrue(answer is Outcome.Ok, "answered: $answer")
        assertEquals(1, host.reads, "the answer must not run the slow read again")
    }

    @Test
    fun `B-DL-431 an answer is not queued behind a timer's read that is still running`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val host = Host(gate)
        val engine = AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), host)
        engine.refresh()
        host.slowFrom = 2
        val timer = async { engine.review(raise = false) }
        testScheduler.runCurrent()
        // The timer's read is in the middle of its slow script; the person taps the button now.
        val answer = async { engine.decide(mapOf(AgentId("claude") to AgentUpdateChoice.Update)) }
        testScheduler.runCurrent()
        assertTrue(answer.isCompleted, "the answer waited for the read")
        gate.complete(Unit)
        timer.await()
    }
}
