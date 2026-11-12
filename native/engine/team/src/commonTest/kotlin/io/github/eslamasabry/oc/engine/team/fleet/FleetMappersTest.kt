package io.github.eslamasabry.oc.engine.team.fleet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetMappersTest {
    @Test
    fun the_address_rule_matches_flutter() {
        assertTrue(FleetAddress.allowed("https://team.example.com"))
        assertTrue(FleetAddress.allowed("http://127.0.0.1:8372"))
        assertTrue(FleetAddress.allowed("http://100.64.0.1:8372"))
        assertTrue(FleetAddress.allowed("http://dev-pc.tail1234.ts.net:8372/"))
        assertFalse(FleetAddress.allowed("http://100.128.0.1:8372"))
        assertFalse(FleetAddress.allowed("http://192.168.1.20:8372"))
        assertFalse(FleetAddress.allowed("http://user@127.0.0.1:8372"))
        assertFalse(FleetAddress.allowed("ftp://127.0.0.1"))
        assertTrue(FleetAddress.plainHttpRefused("http://10.0.0.2:8372"))
        assertFalse(FleetAddress.plainHttpRefused("https://10.0.0.2:8372"))
        assertEquals("http://127.0.0.1:8372", FleetAddress.normalize(" http://127.0.0.1:8372// "))
    }

    private fun work(id: String, state: WorkState, assignee: String? = null) = FleetWork(id, id, state, assigneeOwn = assignee)

    @Test
    fun a_batch_state_follows_its_work() {
        assertEquals(RunState.Waiting, FleetMap.runStateFromWork(emptyList()))
        assertEquals(RunState.Waiting, FleetMap.runStateFromWork(listOf(work("a", WorkState.Queued))))
        assertEquals(RunState.Working, FleetMap.runStateFromWork(listOf(work("a", WorkState.Queued, assignee = "app/polecat"))))
        assertEquals(RunState.Failed, FleetMap.runStateFromWork(listOf(work("a", WorkState.Working), work("b", WorkState.Failed))))
        assertEquals(RunState.Blocked, FleetMap.runStateFromWork(listOf(work("a", WorkState.NeedsInput), work("b", WorkState.Working))))
        assertEquals(RunState.Completed, FleetMap.runStateFromWork(listOf(work("a", WorkState.Completed), work("b", WorkState.Cancelled))))
        // Handed to the refinery: the merge is awaited, not work.
        assertEquals(RunState.Waiting, FleetMap.runStateFromWork(listOf(work("a", WorkState.Review, assignee = "app/refinery"))))
    }

    @Test
    fun an_automatic_batch_title_takes_its_work() {
        val tracked = listOf(work("a", WorkState.Working).copy(title = "Fix login"), work("b", WorkState.Queued).copy(title = "Add tests"))
        assertEquals("Fix login + 1 more", FleetMap.batchTitle("sling-a", tracked))
        assertEquals("Fix login", FleetMap.batchTitle("", tracked.take(1)))
        assertEquals("Release 2.2", FleetMap.batchTitle("Release 2.2", tracked))
    }

    @Test
    fun the_hosts_chores_are_upkeep() {
        fun run(title: String, target: String? = null) =
            WireRun(title, title, "active", null, null, null, target, null, null, null, tracksWork = false)
        assertTrue(FleetMap.upkeep(run("mol-witness-patrol")))
        assertTrue(FleetMap.upkeep(run("order:gate-sweep")))
        assertTrue(FleetMap.upkeep(run("wisp-1", target = "workflow")))
        assertFalse(FleetMap.upkeep(run("Ship the release")))
    }

    @Test
    fun gates_come_from_questions_review_beads_and_failed_runs() {
        val pending = listOf(WirePending("q-1", "confirm", "s-1", null, emptyList(), null))
        val review =
            WireBead("b-2", "Review the patch", "open", "task", null, "app/refinery", null, false, listOf("needs-review"), emptyList(), emptyMap(), null, null)
        val failed = FleetRun("r-1", "Ship", RunState.Failed, lastError = "boom")
        val chore = FleetRun("r-2", "mol-x-patrol", RunState.Failed, upkeep = true)
        val gates = FleetMap.gates(pending, listOf(review), listOf(failed, chore))
        assertEquals(listOf(GateKind.Confirmation, GateKind.ReviewReady, GateKind.RunFailed), gates.map { it.kind })
        assertEquals("Confirm to continue", gates[0].title)
        assertEquals("review:b-2", gates[1].id)
        assertEquals("r-1", gates[2].runId)
        assertEquals(2, FleetSnapshot(gates = gates).attentionCount)
    }

    @Test
    fun agent_state_table() {
        // A crash wins over everything.
        assertEquals(AgentState.Crashed, FleetMap.agentState("idle", "crashed", true, false, false, false, false))
        // The person's pause.
        assertEquals(AgentState.Stopped, FleetMap.agentState("idle", null, true, true, false, false, false))
        // Not running, nothing said: stopped.
        assertEquals(AgentState.Stopped, FleetMap.agentState(null, null, false, false, false, false, false))
        // Running with a question open waits for the person; parked in /waits is blocked.
        assertEquals(AgentState.Waiting, FleetMap.agentState("working", null, true, false, false, true, false))
        assertEquals(AgentState.Blocked, FleetMap.agentState("working", null, true, false, false, false, true))
        assertEquals(AgentState.Working, FleetMap.agentState("stopped", "active", true, false, false, false, false))
    }

    @Test
    fun stream_frames_mark_what_to_read_again() {
        val bead = FleetSignal.of(FleetFrame("event", "3", """{"type":"bead.updated","seq":3,"subject":"b-1"}"""))
        assertIs<FleetSignal.Changed>(bead)
        assertEquals(setOf(FleetScope.Work, FleetScope.Runs, FleetScope.Gates), bead.scopes)
        assertEquals(3L, bead.seq)
        val session = FleetSignal.of(FleetFrame(null, "4", """{"type":"session.stopped","session_id":"s-1"}"""))
        assertEquals(setOf(FleetScope.Agents), (session as FleetSignal.Changed).scopes)
        val pending = FleetSignal.of(FleetFrame("pending", null, """{"request_id":"q-1"}"""))
        assertEquals(setOf(FleetScope.Gates), (pending as FleetSignal.Changed).scopes)
        assertIs<FleetSignal.Heartbeat>(FleetSignal.of(FleetFrame(null, null, """{"timestamp":"2026-11-11T10:00:00Z"}""")))
        assertIs<FleetSignal.Other>(FleetSignal.of(FleetFrame(null, null, "not json")))
    }

    @Test
    fun a_front_receipt_and_a_problem_read_as_receipts() {
        val accepted =
            FleetGateway.receipt(
                "k",
                FleetReply.Answered(202, """{"status":"accepted","upstream_status":202,"body":{"request_id":"c-9"}}""", replayed = true),
            )
        assertEquals(ReceiptStatus.Accepted, accepted.status)
        assertEquals("c-9", accepted.correlationId)
        val retry =
            FleetGateway.receipt(
                "k",
                FleetReply.Answered(502, """{"type":"urn:opencode-mobile:front:upstream-unavailable","title":"Upstream down","status":502}"""),
            )
        assertEquals(ReceiptStatus.Pending, retry.status)
        assertTrue(retry.retryable)
        val lost = FleetGateway.receipt("k", FleetReply.Unreachable())
        assertEquals(ReceiptStatus.Pending, lost.status)
        assertFalse(lost.retryable)
        assertNull(FleetGateway.receipt("k", FleetReply.Answered(200, "{}")).message)
    }
}
