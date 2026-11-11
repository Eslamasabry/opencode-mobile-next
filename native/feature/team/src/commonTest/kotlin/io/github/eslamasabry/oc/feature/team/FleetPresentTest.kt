package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamHostMode
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgent
import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.engine.team.fleet.FleetGate
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetProject
import io.github.eslamasabry.oc.engine.team.fleet.FleetRun
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetStream
import io.github.eslamasabry.oc.engine.team.fleet.FleetUsage
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import io.github.eslamasabry.oc.feature.team.presentation.FleetHomeKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetMark
import io.github.eslamasabry.oc.feature.team.presentation.FleetPlace
import io.github.eslamasabry.oc.feature.team.presentation.FleetPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamSettingsPresent
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetPresentTest {
    private val now = 1_800_000_000_000L
    private val config = TeamConfig(TeamProvider.GasCity, "http://dev-pc.tail1234.ts.net:8372", city = "main")

    private fun ready(snapshot: FleetSnapshot, capabilities: FleetCapabilities = FleetCapabilities.READ) =
        FleetState(
            phase = FleetPhase.Ready,
            capabilities = capabilities,
            snapshot = snapshot,
            hasData = true,
            stream = FleetStream.Live,
            refreshedAtMs = now,
        )

    @Test
    fun the_place_is_the_computers_name_never_an_address() {
        assertEquals(FleetPlace.Named("dev-pc.tail1234.ts.net"), FleetPresent.place(config, null))
        assertEquals(FleetPlace.Computer, FleetPresent.place(config.copy(url = "http://100.64.0.9:8372"), null))
        assertEquals(FleetPlace.Phone, FleetPresent.place(config.copy(hostMode = TeamHostMode.Phone), null))
    }

    @Test
    fun a_team_not_found_says_why_and_does_not_answer() {
        val ui = FleetPresent.home(config, FleetState(phase = FleetPhase.Failed, failure = FleetFailure.Unreachable), now)
        assertEquals(FleetHomeKind.Failed, ui.kind)
        assertEquals(FleetFailure.Unreachable, ui.failure)
        assertTrue(ui.notAnswering)
    }

    @Test
    fun nothing_read_yet_is_starting_and_the_demo_says_it_is_not_here() {
        assertEquals(FleetHomeKind.Starting, FleetPresent.home(config, FleetState(phase = FleetPhase.Probing), now).kind)
        assertEquals(FleetHomeKind.Demo, FleetPresent.home(config.copy(provider = TeamProvider.Fixture), FleetState(), now).kind)
    }

    @Test
    fun a_question_is_its_tasks_row_and_that_task_comes_first() {
        val snapshot =
            FleetSnapshot(
                runs =
                    listOf(
                        FleetRun("r-work", "Add tests", RunState.Working, updatedAtMs = now),
                        FleetRun("r-ask", "Fix login", RunState.Working, updatedAtMs = now - 60_000),
                        FleetRun("r-chore", "mol-x-patrol", RunState.Working, upkeep = true),
                    ),
                work = listOf(FleetWork("b-1", "Fix login", WorkState.NeedsInput, runId = "r-ask")),
                agents = listOf(FleetAgent("a", "app/polecat", AgentState.Waiting, sessionId = "s-1", currentWorkId = "b-1")),
                gates =
                    listOf(
                        FleetGate("q-1", GateKind.Choice, "Keep drafts in SQLite?", agentId = "s-1", createdAtMs = now),
                        FleetGate("q-2", GateKind.FreeText, "Which branch?"),
                        FleetGate("review:b-9", GateKind.ReviewReady, "Ready"),
                    ),
                projects = listOf(FleetProject("app", "app")),
                usage = FleetUsage(costUsdToday = 0.5),
            )
        val ui = FleetPresent.home(config, ready(snapshot), now)
        assertEquals(FleetHomeKind.Ready, ui.kind)
        assertEquals(listOf("r-ask", "r-work"), ui.rows.map { it.id })
        assertEquals("Keep drafts in SQLite?", ui.rows.first().question)
        assertEquals(FleetMark.NeedsYou, ui.rows.first().mark)
        assertEquals(FleetMark.Working, ui.rows.last().mark)
        // A question with no listed task is a row of its own; a review that is ready is not a question.
        assertEquals(listOf("q-2"), ui.questions.map { it.id })
        assertTrue(ui.needsYou)
        assertTrue(ui.readOnly)
        assertFalse(ui.stale)
        assertEquals(1, ui.agents?.waiting)
        assertEquals(0.5, ui.costToday)
        assertEquals(listOf("app"), ui.projects.map { it.id })
    }

    @Test
    fun finished_tasks_show_three_then_one_row_for_the_rest() {
        val runs = (1..5).map { FleetRun("d-$it", "Done $it", RunState.Completed, updatedAtMs = now - it * 1_000L) }
        val folded = FleetPresent.home(config, ready(FleetSnapshot(runs = runs)), now)
        assertEquals(3, folded.rows.size)
        assertEquals(2, folded.hiddenDone)
        val open = FleetPresent.home(config, ready(FleetSnapshot(runs = runs)), now, doneExpanded = true)
        assertEquals(5, open.rows.size)
        assertEquals(0, open.hiddenDone)
    }

    @Test
    fun old_data_or_a_dropped_stream_is_stale_and_unknown_spend_is_not_zero() {
        val ui = FleetPresent.home(config, ready(FleetSnapshot()).copy(stream = FleetStream.Reconnecting), now)
        assertTrue(ui.stale)
        assertTrue(ui.notAnswering)
        assertNull(ui.costToday)
        assertFalse(FleetPresent.home(config, ready(FleetSnapshot(), FleetCapabilities.FRONT), now).readOnly)
    }

    @Test
    fun team_settings_for_a_computers_team_hide_the_phone_parts() {
        val ui = TeamSettingsPresent.present(TeamState(TeamPhase.Computer, config), Redactor()::details, fleetVersion = "1.4.1")
        assertTrue(ui.computer)
        assertFalse(ui.canCheck)
        assertEquals("gascity", ui.host?.provider?.text)
        assertEquals("1.4.1", ui.host?.version?.text)
    }
}
