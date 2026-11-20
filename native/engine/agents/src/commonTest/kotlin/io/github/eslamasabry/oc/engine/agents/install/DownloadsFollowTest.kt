package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The yes on mobile data (journey M1, 10114) is acted on at once and never lost behind a look that is running. */
class DownloadsFollowTest {
    private val claude = AgentId("claude")
    private val codex = AgentId("codex")

    private class Host : AgentUpdateHost {
        override suspend fun refresh() = Unit
        override val readDue: Boolean = false
        override suspend fun read() = Unit
        override val installed = listOf(AgentId("claude"), AgentId("codex"))
        override val behind = setOf(AgentId("claude"), AgentId("codex"))
        override val helperBehind = true
        override val pinKey = "pins-1"
        override val switchPending = false
        override val installProblem = false
        override val metered = true
        override fun updateAllowed(agent: AgentId) = true
    }

    private class Jobs(val gate: CompletableDeferred<Unit>) : AgentUpdateJobs {
        val started = mutableListOf<AgentId>()
        override val running = false
        override val failed = false
        override suspend fun update(agent: AgentId): Boolean {
            if (started.isEmpty()) gate.await()
            started += agent
            return true
        }
    }

    @Test
    fun `B-DL-432 a yes that arrives while a look runs is acted on right after that look`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val jobs = Jobs(gate)
        val lines = mutableListOf<String>()
        val updater = AgentAutoUpdater(Host(), jobs, log = { lines += it }) { false }
        updater.allowMeteredOnce()
        val first = launch { updater.changed() }
        runCurrent()
        // The look is inside Claude's start. The person says Download now again for the rest; its look finds the lock.
        updater.allowMeteredOnce()
        launch { updater.changed() }
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        first.join()
        // Not left for the next 30 s look: Codex starts at once, on the yes that came during the look.
        assertEquals(listOf(claude, codex), jobs.started)
        assertEquals(true, lines.any { it.startsWith("look again") })
    }
}
