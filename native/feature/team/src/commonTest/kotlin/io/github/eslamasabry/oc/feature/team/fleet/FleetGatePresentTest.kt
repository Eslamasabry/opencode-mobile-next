package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.FleetAgent
import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetGate
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetReceipt
import io.github.eslamasabry.oc.engine.team.fleet.FleetRun
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetGatePresentTest {
    private fun fleet(snapshot: FleetSnapshot, caps: FleetCapabilities = FleetCapabilities.FRONT) =
        FleetState(phase = FleetPhase.Ready, capabilities = caps, snapshot = snapshot, hasData = true)

    private val agent = FleetAgent("rig/polecat-1", "polecat-1", AgentState.Working, sessionId = "s-1")

    private fun failedSnapshot(error: String): FleetSnapshot =
        FleetSnapshot(
            runs = listOf(FleetRun("r1", "Sync engine", RunState.Failed, lastError = error)),
            work =
                listOf(
                    FleetWork("w1", "Write sync", WorkState.Failed, runId = "r1", assignee = "rig/polecat-1"),
                    FleetWork("w2", "Docs", WorkState.Working, runId = "r1"),
                    FleetWork("w3", "Done step", WorkState.Completed, runId = "r1"),
                ),
            agents = listOf(agent),
            gates = listOf(FleetGate("run-failed:r1", GateKind.RunFailed, "Sync engine stopped", runId = "r1", prompt = error)),
        )

    @Test
    fun failures_are_classified_most_specific_first() {
        assertEquals(FleetFailureClass.MergeConflict, FleetFailures.classify("tests failed after a merge conflict"))
        assertEquals(FleetFailureClass.Authentication, FleetFailures.classify("HTTP 401 unauthorized"))
        assertEquals(FleetFailureClass.Infrastructure, FleetFailures.classify("connection refused"))
        assertEquals(FleetFailureClass.Unknown, FleetFailures.classify("   "))
        assertEquals(FleetFailureClass.Unknown, FleetFailures.classify(null))
    }

    @Test
    fun only_some_failures_can_recover_by_sending_the_work_again() {
        assertEquals(true, FleetFailures.recoverable(FleetFailureClass.Infrastructure))
        assertEquals(false, FleetFailures.recoverable(FleetFailureClass.Test))
        assertNull(FleetFailures.recoverable(FleetFailureClass.Unknown))
    }

    @Test
    fun nothing_read_yet_waits_and_a_gate_answered_elsewhere_is_gone() {
        assertEquals(FleetGateView.Waiting, FleetGatePresent.present(FleetState(), "g").view)
        assertEquals(FleetGateView.Gone, FleetGatePresent.present(fleet(FleetSnapshot()), "g").view)
    }

    @Test
    fun a_choice_shows_its_options_and_answers_only_behind_the_respond_capability() {
        val snapshot = FleetSnapshot(gates = listOf(FleetGate("g", GateKind.Choice, "Which branch?", choices = listOf("main", "dev"), prompt = "Which branch should I use?")))
        val on = FleetGatePresent.present(fleet(snapshot), "g")
        assertEquals(listOf("main", "dev"), on.choices)
        assertEquals("Which branch should I use?", on.prompt)
        assertTrue(on.canRespond && on.answers)
        val off = FleetGatePresent.present(fleet(snapshot, FleetCapabilities.READ), "g")
        assertFalse(off.canRespond || off.answers)
    }

    @Test
    fun a_prompt_that_repeats_the_title_is_said_once() {
        val snapshot = FleetSnapshot(gates = listOf(FleetGate("g", GateKind.FreeText, "Name the release", prompt = "Name the release")))
        assertNull(FleetGatePresent.present(fleet(snapshot), "g").prompt)
    }

    @Test
    fun a_destructive_confirmation_is_read_from_its_words() {
        val snapshot =
            FleetSnapshot(
                gates =
                    listOf(
                        FleetGate("a", GateKind.Confirmation, "Delete the build folder?"),
                        FleetGate("b", GateKind.Confirmation, "Run the linter?"),
                    ),
            )
        assertTrue(FleetGatePresent.present(fleet(snapshot), "a").destructive)
        assertFalse(FleetGatePresent.present(fleet(snapshot), "b").destructive)
    }

    @Test
    fun a_failed_task_is_titled_after_its_task_and_offers_fix_retry_and_stop() {
        val ui = FleetGatePresent.present(fleet(failedSnapshot("connection timed out")), "run-failed:r1")
        assertEquals("Sync engine", ui.stoppedTask)
        assertEquals(FleetFailureClass.Infrastructure, ui.failure)
        assertEquals(FleetGateAgent("rig/polecat-1", "polecat-1"), ui.fix)
        assertEquals(FleetGateRetry("w1", "Write sync", "rig/polecat-1", "polecat-1"), ui.retry)
        assertEquals("r1", ui.cancelRunId)
        // Stuck work first, finished work not listed.
        assertEquals(listOf("w1", "w2"), ui.affected.map { it.id })
        assertTrue(ui.errorText.contains("connection timed out"))
    }

    @Test
    fun a_failure_a_retry_cannot_recover_offers_no_retry() {
        val ui = FleetGatePresent.present(fleet(failedSnapshot("3 tests failed")), "run-failed:r1")
        assertEquals(FleetFailureClass.Test, ui.failure)
        assertNull(ui.retry)
        assertEquals("polecat-1", ui.fix?.name)
    }

    @Test
    fun each_failed_task_action_is_absent_without_its_capability() {
        val ui = FleetGatePresent.present(fleet(failedSnapshot("connection timed out"), FleetCapabilities.READ), "run-failed:r1")
        assertNull(ui.fix)
        assertNull(ui.retry)
        assertNull(ui.cancelRunId)
        assertFalse(ui.answers)
    }

    @Test
    fun a_bead_gate_lists_what_it_unblocks() {
        val snapshot =
            FleetSnapshot(
                work = listOf(FleetWork("w1", "Ship", WorkState.Blocked, dependsOn = listOf("b1")), FleetWork("w2", "Other", WorkState.Ready)),
                gates = listOf(FleetGate("bead:b1", GateKind.GateBead, "Approve the plan", workId = "b1")),
            )
        assertEquals(listOf("w1"), FleetGatePresent.present(fleet(snapshot), "bead:b1").unblocks.map { it.id })
    }

    @Test
    fun the_gate_names_its_task_work_or_agent() {
        val snapshot =
            FleetSnapshot(
                runs = listOf(FleetRun("r1", "Fix login", RunState.Working)),
                agents = listOf(agent),
                gates =
                    listOf(
                        FleetGate("a", GateKind.Choice, "q", runId = "r1"),
                        FleetGate("b", GateKind.Choice, "q", agentId = "s-1"),
                    ),
            )
        assertEquals(FleetGateLink(FleetGateLinkKind.Run, "Fix login"), FleetGatePresent.present(fleet(snapshot), "a").link)
        assertEquals(FleetGateLink(FleetGateLinkKind.Agent, "polecat-1"), FleetGatePresent.present(fleet(snapshot), "b").link)
    }

    @Test
    fun receipts_follow_what_came_back_and_a_refusal_keeps_the_hosts_words_for_details() {
        val gate = FleetSnapshot(gates = listOf(FleetGate("g", GateKind.Confirmation, "Run it?")))
        fun kind(local: FleetGateLocal) = FleetGatePresent.present(fleet(gate), "g", local)
        assertNull(kind(FleetGateLocal()).receipt)
        assertEquals(FleetGateReceiptKind.Sending, kind(FleetGateLocal(sending = true)).receipt)
        assertEquals(FleetGateReceiptKind.Confirmed, kind(FleetGateLocal(receipt = FleetReceipt("1", ReceiptStatus.Accepted))).receipt)
        assertEquals(FleetGateReceiptKind.NotConfirmed, kind(FleetGateLocal(receipt = FleetReceipt("1", ReceiptStatus.Pending, "no answer"))).receipt)
        val refused = kind(FleetGateLocal(receipt = FleetReceipt.rejected("1", "session not found")))
        assertEquals(FleetGateReceiptKind.Refused, refused.receipt)
        assertEquals("session not found", refused.refusal)
        assertFalse(refused.hideActions)
        // An unconfirmed or confirmed write hides the actions: the same answer may have landed.
        assertTrue(kind(FleetGateLocal(receipt = FleetReceipt("1", ReceiptStatus.Pending))).hideActions)
    }
}
