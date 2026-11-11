package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgent
import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetGate
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetRun
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetStream
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunFactKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunPresent
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunWord
import io.github.eslamasabry.oc.feature.team.presentation.FleetStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetRunPresentTest {
    private val now = 1_800_000_000_000L
    private val config = TeamConfig(TeamProvider.GasCity, "http://dev-pc.tail1234.ts.net:8372", city = "main")
    private val redact: (String) -> Redacted = Redactor()::details

    private fun ready(snapshot: FleetSnapshot, capabilities: FleetCapabilities = FleetCapabilities.FRONT) =
        FleetState(phase = FleetPhase.Ready, capabilities = capabilities, snapshot = snapshot, hasData = true, stream = FleetStream.Live, refreshedAtMs = now)

    private val run = FleetRun("r-1", "Fix login", RunState.Working, startedAtMs = now - 3_600_000, updatedAtMs = now - 1_000, formula = "mol-fix")
    private val steps =
        listOf(
            FleetWork("w-1", "Write the test", WorkState.Completed, runId = "r-1"),
            FleetWork("w-2", "Fix the page", WorkState.Working, runId = "r-1", dependsOn = listOf("w-1"), assignee = "demo/gastown.furiosa"),
            FleetWork("w-3", "Review", WorkState.Waiting, runId = "r-1", dependsOn = listOf("w-2")),
            FleetWork("other", "Another task's step", WorkState.Working, runId = "r-2"),
        )

    private fun page(snapshot: FleetSnapshot, caps: FleetCapabilities = FleetCapabilities.FRONT, id: String = "r-1") =
        FleetRunPresent.run(config, ready(snapshot, caps), id, now, redact)

    @Test
    fun the_page_lists_only_its_tasks_steps_with_what_each_needs_and_how_far_it_is() {
        val ui = page(FleetSnapshot(runs = listOf(run), work = steps))
        assertEquals(FleetRunKind.Ready, ui.kind)
        assertEquals(listOf("w-1", "w-2", "w-3"), ui.steps.map { it.id })
        assertEquals(listOf("w-2"), ui.steps.last().dependsOn)
        assertEquals(1, ui.done)
        assertEquals(3, ui.total)
        assertEquals(3_600_000L, ui.elapsedMs)
        assertEquals(FleetRunWord.Working, ui.word)
        assertEquals(FleetStage.Working, ui.stage)
    }

    @Test
    fun a_task_with_no_listed_steps_counts_its_own_steps() {
        val ui = page(FleetSnapshot(runs = listOf(run.copy(stepCount = 5, completedSteps = 2))))
        assertEquals(2, ui.done)
        assertEquals(5, ui.total)
        assertTrue(ui.steps.isEmpty())
    }

    @Test
    fun a_finished_task_took_as_long_as_it_ran_and_a_completed_one_with_a_step_open_is_in_review() {
        val done = run.copy(state = RunState.Completed, merged = true, updatedAtMs = now - 600_000)
        val closed = steps.take(1)
        val ui = page(FleetSnapshot(runs = listOf(done), work = closed))
        assertEquals(3_000_000L, ui.elapsedMs)
        assertEquals(FleetStage.Done, ui.stage)
        assertEquals(FleetRunWord.Merged, ui.word)
        val review = page(FleetSnapshot(runs = listOf(done.copy(merged = false)), work = steps.take(2)))
        assertEquals(FleetStage.Reviewing, review.stage)
        assertEquals(FleetRunWord.WaitingMerge, review.word)
        assertNull(page(FleetSnapshot(runs = listOf(run.copy(state = RunState.Failed)))).stage)
    }

    @Test
    fun stop_task_is_offered_only_for_an_open_task_the_computer_lets_the_phone_stop() {
        val snapshot = FleetSnapshot(runs = listOf(run))
        assertTrue(page(snapshot).canStop)
        assertFalse(page(snapshot, FleetCapabilities.READ).canStop)
        assertFalse(page(FleetSnapshot(runs = listOf(run.copy(state = RunState.Completed)))).canStop)
        assertFalse(page(FleetSnapshot(runs = listOf(run.copy(state = RunState.Failed)))).canStop)
    }

    @Test
    fun the_questions_that_wait_on_this_task_are_on_its_page() {
        val gate = FleetGate("g", GateKind.Choice, "Keep drafts in SQLite?", runId = "r-1")
        val other = FleetGate("h", GateKind.Choice, "Elsewhere?", runId = "r-2")
        val review = FleetGate("i", GateKind.ReviewReady, "Ready to merge", runId = "r-1")
        val ui = page(FleetSnapshot(runs = listOf(run), gates = listOf(gate, other, review)))
        assertEquals(listOf("Keep drafts in SQLite?"), ui.questions.map { it.title })
    }

    @Test
    fun a_task_the_host_no_longer_lists_is_missing_and_nothing_read_yet_is_starting() {
        assertEquals(FleetRunKind.Missing, page(FleetSnapshot(), id = "gone").kind)
        assertEquals(FleetRunKind.Starting, FleetRunPresent.run(config, FleetState(phase = FleetPhase.Probing), "r-1", now, redact).kind)
        assertEquals(FleetRunKind.Failed, FleetRunPresent.run(config, FleetState(phase = FleetPhase.Failed), "r-1", now, redact).kind)
    }

    @Test
    fun the_host_words_of_a_failure_are_only_in_details() {
        val ui = page(FleetSnapshot(runs = listOf(run.copy(state = RunState.Failed, lastError = "exit status 1"))))
        assertEquals(FleetRunWord.Failed, ui.word)
        assertTrue(ui.facts.any { it.kind == FleetRunFactKind.LastError })
    }

    @Test
    fun the_work_sheet_names_the_owner_what_it_needs_and_what_waits_on_it() {
        val agent = FleetAgent("demo/gastown.furiosa", "demo/gastown.furiosa", AgentState.Working, pool = "gastown.polecat", currentWorkId = "w-2")
        val sheet = FleetRunPresent.work(ready(FleetSnapshot(runs = listOf(run), work = steps, agents = listOf(agent))), "w-2", redact)
        assertTrue(sheet.found)
        assertEquals("Fix login", sheet.task)
        assertEquals("furiosa", sheet.owner?.name)
        assertEquals("demo/gastown.furiosa", sheet.owner?.agentId)
        assertEquals(listOf("w-1"), sheet.dependencies.map { it.id })
        assertEquals(listOf("w-3"), sheet.blocking.map { it.id })
        assertEquals(WorkState.Completed, sheet.dependencies.single().state)
    }

    @Test
    fun a_step_in_a_one_step_task_does_not_repeat_its_title_and_an_unlisted_dependency_says_so() {
        val one = FleetWork("w", "Fix login", WorkState.Waiting, runId = "r-1", dependsOn = listOf("ghost"), assignee = "someone")
        val sheet = FleetRunPresent.work(ready(FleetSnapshot(runs = listOf(run), work = listOf(one))), "w", redact)
        assertNull(sheet.task)
        assertNull(sheet.dependencies.single().state)
        // An owner the host lists no agent for is still named, but has no page to open.
        assertEquals("someone", sheet.owner?.name)
        assertNull(sheet.owner?.agentId)
    }

    @Test
    fun a_step_that_left_the_host_is_a_designed_missing_sheet() {
        val sheet = FleetRunPresent.work(ready(FleetSnapshot(runs = listOf(run), work = steps)), "gone", redact)
        assertFalse(sheet.found)
        assertEquals("gone", sheet.id)
    }

    @Test
    fun the_shortest_name_of_an_owner_is_the_agents_own() {
        assertEquals("furiosa", FleetRunPresent.shortName("ocproof/gastown.furiosa"))
        assertEquals("solo", FleetRunPresent.shortName("solo"))
    }
}
