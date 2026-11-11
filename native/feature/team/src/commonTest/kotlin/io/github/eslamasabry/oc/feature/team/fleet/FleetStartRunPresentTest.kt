package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgent
import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetProject
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetStartRunPresentTest {
    private fun fleet(snapshot: FleetSnapshot, caps: FleetCapabilities = FleetCapabilities.FRONT) =
        FleetState(phase = FleetPhase.Ready, capabilities = caps, snapshot = snapshot, hasData = true)

    private val awake = FleetAgent("gastown.mayor", "gastown.mayor", AgentState.Idle, sessionId = "s-1")
    private val asleep = FleetAgent("gastown.mayor", "gastown.mayor", AgentState.Stopped, rawState = "suspended", suspended = true)
    private val rig = FleetProject("rig-a", "Rig A")

    @Test
    fun the_planner_is_found_by_id_then_by_name() {
        assertEquals("gastown.mayor", FleetStartPresent.planner(listOf(FleetAgent("x", "x", AgentState.Idle), awake))?.id)
        assertEquals("city/mayor", FleetStartPresent.planner(listOf(FleetAgent("city/mayor", "city/mayor", AgentState.Idle)))?.id)
        assertNull(FleetStartPresent.planner(listOf(FleetAgent("x", "x", AgentState.Idle))))
    }

    @Test
    fun a_planner_that_is_suspended_or_stopped_is_off() {
        assertTrue(FleetStartPresent.plannerIsOff(asleep))
        assertTrue(FleetStartPresent.plannerIsOff(FleetAgent("m", "m", AgentState.Crashed)))
        assertFalse(FleetStartPresent.plannerIsOff(awake))
    }

    @Test
    fun an_awake_planner_gives_the_planner_form_with_its_projects() {
        val ui = FleetStartPresent.present(fleet(FleetSnapshot(agents = listOf(awake), projects = listOf(rig))))
        assertEquals(FleetStartMode.Planner, ui.mode)
        assertEquals("gastown.mayor", ui.plannerId)
        assertFalse(ui.plannerAsleep)
        assertEquals(listOf("rig-a"), ui.projects.map { it.id })
    }

    @Test
    fun a_planner_without_a_live_session_is_woken_before_the_message() {
        val noSession = awake.copy(sessionId = null)
        assertTrue(FleetStartPresent.present(fleet(FleetSnapshot(agents = listOf(noSession)))).plannerAsleep)
    }

    @Test
    fun nothing_read_yet_waits() {
        assertEquals(FleetStartMode.Waiting, FleetStartPresent.present(FleetState()).mode)
    }

    @Test
    fun a_switched_off_planner_where_the_host_creates_and_assigns_work_gives_the_direct_form() {
        val caps = FleetCapabilities.LOOPBACK
        val ui = FleetStartPresent.present(fleet(FleetSnapshot(agents = listOf(asleep), projects = listOf(rig)), caps))
        assertEquals(FleetStartMode.Direct, ui.mode)
        assertEquals("rig-a", ui.directProjectId)
    }

    @Test
    fun the_direct_task_goes_to_the_chosen_projects_worker_pool() {
        assertEquals("rig-a/gastown.polecat", FleetStartPresent.workerPool("rig-a"))
        val two = FleetSnapshot(agents = listOf(asleep), projects = listOf(rig, FleetProject("rig-b", "Rig B")))
        val ui = FleetStartPresent.present(fleet(two, FleetCapabilities.LOOPBACK), FleetStartLocal(directProjectId = "rig-b"))
        assertEquals("rig-b", ui.directProjectId)
    }

    @Test
    fun a_switched_off_planner_can_be_woken_only_where_the_host_takes_agent_controls() {
        val snapshot = FleetSnapshot(agents = listOf(asleep))
        val front = FleetStartPresent.present(fleet(snapshot))
        assertEquals(FleetStartMode.Blocked, front.mode)
        assertEquals(FleetBlockedWhy.PlannerOff, front.blocked)
        assertTrue(front.wakeable)
        val read = FleetStartPresent.present(fleet(snapshot, FleetCapabilities.READ))
        assertEquals(FleetBlockedWhy.PlannerOff, read.blocked)
        assertFalse(read.wakeable)
    }

    @Test
    fun no_planner_and_no_project_say_which_is_missing() {
        assertEquals(FleetBlockedWhy.NoPlanner, FleetStartPresent.present(fleet(FleetSnapshot())).blocked)
        val noProject = FleetStartPresent.present(fleet(FleetSnapshot(), FleetCapabilities.LOOPBACK))
        assertEquals(FleetBlockedWhy.NoProject, noProject.blocked)
    }

    @Test
    fun the_message_to_the_planner_carries_the_objective_the_project_and_the_supervision() {
        val text = FleetStartPresent.planningMessage("  Add dark mode ", FleetSupervision.Balanced, "Rig A")
        val lines = text.lines()
        assertEquals(FleetStartPresent.MARKER, lines[0])
        assertEquals("Objective: Add dark mode", lines[1])
        assertEquals("Project: Rig A", lines[2])
        assertTrue(lines[3].startsWith("Supervision: Balanced"))
        assertFalse(FleetStartPresent.planningMessage("x", FleetSupervision.High, null).contains("Project:"))
    }

    @Test
    fun a_task_that_exists_cannot_be_sent_again() {
        val ui = FleetStartUi(local = FleetStartLocal(outcome = FleetDirectOutcome.AssignRefused))
        assertTrue(ui.taskMade)
        assertFalse(FleetStartUi(local = FleetStartLocal(outcome = FleetDirectOutcome.CreateUnconfirmed)).taskMade)
    }

    @Test
    fun an_empty_objective_or_task_is_missing() {
        assertTrue(FleetStartPresent.objectiveMissing(FleetStartLocal(objective = "  ")))
        assertFalse(FleetStartPresent.taskMissing(FleetStartLocal(task = "Fix it")))
    }
}
