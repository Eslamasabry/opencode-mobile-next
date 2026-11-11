package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamAlertKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FleetAlertTrackerTest {
    private fun state(gates: List<FleetGate> = emptyList(), runs: List<FleetRun> = emptyList(), data: Boolean = true) =
        FleetState(phase = FleetPhase.Ready, snapshot = FleetSnapshot(runs = runs, gates = gates), hasData = data)

    private fun run(id: String, s: RunState) = FleetRun(id, id, s)
    private fun gate(id: String, kind: GateKind, runId: String? = null) = FleetGate(id, kind, id, runId = runId)

    @Test
    fun nothing_before_data_and_the_first_snapshot_is_only_the_baseline() {
        val t = FleetAlertTracker("p")
        assertTrue(t.observe(state(data = false)).isEmpty)
        assertTrue(t.observe(state(gates = listOf(gate("g0", GateKind.Choice)))).isEmpty)
        assertTrue(t.observe(state(gates = listOf(gate("g0", GateKind.Choice)))).isEmpty)
    }

    @Test
    fun a_new_question_opens_the_gate_page_and_is_settled_when_it_is_answered() {
        val t = FleetAlertTracker("p")
        t.observe(state())
        val diff = t.observe(state(gates = listOf(gate("g1", GateKind.Choice))))
        val a = diff.alerts.single()
        assertEquals(TeamAlertKind.Decision, a.kind)
        assertEquals("gate:g1", a.id)
        assertEquals("team:p:gate:g1", a.key)
        assertTrue(t.observe(state(gates = listOf(gate("g1", GateKind.Choice)))).isEmpty)
        assertEquals(listOf("team:p:gate:g1"), t.observe(state()).settled)
    }

    @Test
    fun a_failed_task_opens_the_task_page_and_a_review_the_gate_page() {
        val t = FleetAlertTracker("p")
        t.observe(state())
        val diff = t.observe(state(gates = listOf(gate("f1", GateKind.RunFailed, runId = "r1"), gate("v1", GateKind.ReviewReady), gate("b1", GateKind.GateBead))))
        assertEquals(listOf(TeamAlertKind.RunFailed to "run:r1", TeamAlertKind.Review to "gate:v1"), diff.alerts.map { it.kind to it.id })
    }

    @Test
    fun a_known_task_that_completes_is_announced_once_and_history_is_not() {
        val t = FleetAlertTracker("p")
        t.observe(state(runs = listOf(run("r1", RunState.Working), run("old", RunState.Completed))))
        val diff = t.observe(state(runs = listOf(run("r1", RunState.Completed), run("old", RunState.Completed), run("new", RunState.Completed))))
        val a = diff.alerts.single()
        assertEquals(TeamAlertKind.Completed, a.kind)
        assertEquals("run:r1", a.id)
        assertTrue(t.observe(state(runs = listOf(run("r1", RunState.Working)))).isEmpty)
        assertTrue(t.observe(state(runs = listOf(run("r1", RunState.Completed)))).isEmpty)
    }
}
