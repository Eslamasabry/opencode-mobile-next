package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetMergeResult
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetReceipt
import io.github.eslamasabry.oc.engine.team.fleet.FleetRun
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetStream
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.MergeBoundary
import io.github.eslamasabry.oc.engine.team.fleet.MergeLine
import io.github.eslamasabry.oc.engine.team.fleet.MergeReadiness
import io.github.eslamasabry.oc.engine.team.fleet.MergeRequestInfo
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetMergePresentTest {
    private val now = 1_800_000_000_000L
    private val run = FleetRun("r-1", "Login page", RunState.Working)

    private fun work(vararg states: WorkState) = states.mapIndexed { i, s -> FleetWork("w$i", "Step $i", s, runId = "r-1", updatedAtMs = now - 60_000L * (i + 1)) }

    private fun fleet(
        caps: FleetCapabilities = FleetCapabilities.FRONT,
        items: List<FleetWork> = work(WorkState.Completed, WorkState.Review),
        live: Boolean = true,
    ) =
        FleetState(
            phase = FleetPhase.Ready,
            capabilities = caps,
            snapshot = FleetSnapshot(runs = listOf(run), work = items),
            hasData = true,
            stream = if (live) FleetStream.Live else FleetStream.Reconnecting,
            refreshedAtMs = now,
        )

    private fun readiness(
        ready: Boolean = true,
        boundaries: List<MergeBoundary> = emptyList(),
        request: MergeRequestInfo? = null,
        branches: List<String> = listOf("feature/login"),
        commit: String? = null,
        lines: List<MergeLine> = listOf(MergeLine("work", true, "5/5 work items"), MergeLine("tests", true)),
    ) = MergeReadiness("r-1", ready, targetBranch = "main", lines = lines, files = 6, additions = 312, deletions = 87, boundaries = boundaries, mergeRequest = request, branches = branches, mergeCommit = commit)

    private fun local(r: MergeReadiness?, vararg more: (FleetMergeLocal) -> FleetMergeLocal) =
        more.fold(FleetMergeLocal(read = r?.let { FleetMergeRead.Found(it) } ?: FleetMergeRead.Idle)) { acc, f -> f(acc) }

    private fun ui(l: FleetMergeLocal, f: FleetState = fleet()) = FleetMergePresent.section(f, "r-1", l, now)

    @Test
    fun the_section_is_absent_without_the_hosts_merge_roles() {
        assertEquals(FleetMergeKind.Hidden, ui(local(readiness()), fleet(FleetCapabilities.LOOPBACK)).kind)
        assertEquals(FleetMergeKind.Hidden, ui(local(readiness()), fleet(FleetCapabilities.READ)).kind)
    }

    @Test
    fun the_section_is_absent_while_work_is_open_so_the_host_is_not_asked() {
        val open = fleet(items = work(WorkState.Completed, WorkState.Working))
        assertFalse(FleetMergePresent.eligible(open, "r-1"))
        assertEquals(FleetMergeKind.Hidden, ui(local(readiness()), open).kind)
        // A failed or waiting step holds the merge back as well.
        assertFalse(FleetMergePresent.eligible(fleet(items = work(WorkState.Failed)), "r-1"))
        assertFalse(FleetMergePresent.eligible(fleet(items = work(WorkState.NeedsInput)), "r-1"))
    }

    @Test
    fun cancelled_steps_hold_nothing_back_and_a_run_without_steps_uses_its_own_counts() {
        assertTrue(FleetMergePresent.eligible(fleet(items = work(WorkState.Completed, WorkState.Cancelled)), "r-1"))
        assertFalse(FleetMergePresent.eligible(fleet(items = emptyList()), "r-1"))
        val counted = run.copy(stepCount = 4, completedSteps = 4)
        assertTrue(FleetMergePresent.workDone(counted, emptyList()))
        assertTrue(FleetMergePresent.workDone(run.copy(state = RunState.Completed), emptyList()))
    }

    @Test
    fun a_ready_task_is_the_one_question_with_merge_as_its_step() {
        val ui = ui(local(readiness()))
        assertEquals(FleetMergeKind.Ask, ui.kind)
        assertEquals("main", ui.branch)
        assertEquals(6, ui.files)
        assertEquals(2, ui.checks)
        assertTrue(ui.canMerge)
        assertFalse(ui.approveFirst)
        assertNull(ui.off)
        // How long it has waited is dated by the last step that finished.
        assertEquals(now - 60_000L, ui.readySinceMs)
    }

    @Test
    fun a_request_that_waits_for_approval_makes_approve_the_next_step_before_merge() {
        val request = MergeRequestInfo("mr-1")
        val waiting = ui(local(readiness(request = request)))
        assertTrue(waiting.approveFirst)
        assertTrue(waiting.canApprove)
        assertEquals("mr-1", waiting.requestId)
        val approved = ui(local(readiness(request = request.copy(approvedBy = "eslam"))))
        assertFalse(approved.approveFirst)
        assertEquals("eslam", approved.approvedBy)
        // The host's own answer counts at once, before the next read.
        val justApproved = ui(local(readiness(request = request), { it.copy(approve = FleetWrite(FleetWriteStatus.Confirmed)) }))
        assertFalse(justApproved.approveFirst)
        assertFalse(justApproved.canApprove)
    }

    @Test
    fun a_host_rule_that_blocks_turns_merge_off_and_says_which_rule() {
        val rule = MergeBoundary("require_approval", false, "Never merge without approval")
        val ui = ui(local(readiness(boundaries = listOf(rule))))
        assertEquals(FleetMergeKind.Ask, ui.kind)
        assertFalse(ui.canMerge)
        assertEquals(FleetMergeOff.Boundary("Never merge without approval"), ui.off)
        assertEquals("Never merge without approval", ui.blocking)
    }

    @Test
    fun a_team_that_is_not_answering_turns_the_step_off_with_that_reason() {
        val ui = ui(local(readiness()), fleet(live = false))
        assertFalse(ui.canMerge)
        assertEquals(FleetMergeOff.Stale, ui.off)
    }

    @Test
    fun a_task_that_is_not_ready_lists_its_checks_and_names_the_first_missing() {
        val lines = listOf(MergeLine("work", true), MergeLine("tests", false, "2 failed"), MergeLine("build", false, null, pending = true))
        val ui = ui(local(readiness(ready = false, lines = lines)))
        assertEquals(FleetMergeKind.NotReady, ui.kind)
        assertFalse(ui.canMerge)
        assertEquals("tests", ui.missing?.key)
        assertEquals(listOf(FleetMergeLineState.Done, FleetMergeLineState.Failed, FleetMergeLineState.Working), ui.lines.map { it.state })
        // A failed check shows its detail; a check still running shows none (the words say it runs).
        assertEquals("2 failed", ui.lines[1].detail)
        assertTrue(ui.lines[2].pending)
        assertNull(ui.lines[2].detail)
    }

    @Test
    fun the_work_items_line_always_shows_its_count_but_other_passed_checks_stay_quiet() {
        val lines = listOf(MergeLine("work", true, "5/5 work items"), MergeLine("tests", true, "18 passed"))
        val ui = ui(local(readiness(ready = false, lines = lines)))
        assertEquals("5/5 work items", ui.lines[0].detail)
        assertNull(ui.lines[1].detail)
    }

    @Test
    fun readiness_that_is_loading_or_unavailable_is_said_and_offers_no_dead_step() {
        val loading = ui(FleetMergeLocal(read = FleetMergeRead.Loading))
        assertEquals(FleetMergeKind.NotReady, loading.kind)
        assertTrue(loading.loading)
        assertFalse(loading.canMerge || loading.canApprove)
        assertEquals(FleetMergeUnavailable.NoRoles, ui(FleetMergeLocal(read = FleetMergeRead.NoRoles)).unavailable)
        assertEquals(FleetMergeUnavailable.Failed, ui(FleetMergeLocal(read = FleetMergeRead.Failed)).unavailable)
    }

    @Test
    fun a_confirmed_merge_is_one_line_with_where_it_landed() {
        val result = FleetMergeResult(FleetReceipt("r", ReceiptStatus.Accepted), mergeCommit = "4f9c2a1deadbeef", branch = "release")
        val merged = ui(local(readiness(), { it.copy(merge = FleetWrite(FleetWriteStatus.Confirmed), mergeResult = result) }))
        assertEquals(FleetMergeKind.Merged, merged.kind)
        assertEquals("release", merged.mergedBranch)
        assertEquals("4f9c2a1deadbeef", merged.mergedCommit)
        assertEquals("4f9c2a1", FleetMergePresent.shortCommit(merged.mergedCommit))
        assertFalse(merged.canMerge)
    }

    @Test
    fun a_task_whose_branches_are_already_on_the_target_reads_as_merged() {
        val already = ui(local(readiness(branches = emptyList(), commit = "abc1234567")))
        assertEquals(FleetMergeKind.Merged, already.kind)
        assertEquals("main", already.mergedBranch)
        assertEquals("abc1234567", already.mergedCommit)
    }

    @Test
    fun a_merge_in_flight_cannot_be_sent_twice_and_a_refused_one_can_be_tried_again() {
        val sending = ui(local(readiness(), { it.copy(merge = FleetWrite.Sending) }))
        assertTrue(sending.merging)
        assertFalse(sending.canMerge)
        val refused = ui(local(readiness(), { it.copy(merge = FleetWrite(FleetWriteStatus.Refused, "HTTP 500")) }))
        assertTrue(refused.canMerge)
        assertEquals(FleetWriteStatus.Refused, refused.mergeWrite?.status)
        assertNull(refused.refusedOn)
    }

    @Test
    fun a_refusal_on_a_host_rule_carries_the_rule_as_words_and_no_technical_text() {
        val result = FleetMergeResult(FleetReceipt.rejected("r", "Never merge without approval"), boundary = "Never merge without approval")
        val write = FleetMergePresent.write(result)
        assertEquals(FleetWriteStatus.Refused, write.status)
        assertNull(write.hostWords)
        val shown = ui(local(readiness(), { it.copy(merge = write, mergeResult = result) }))
        assertEquals("Never merge without approval", shown.refusedOn)
    }

    @Test
    fun the_hosts_own_words_of_a_plain_refusal_stay_for_details() {
        val write = FleetMergePresent.write(FleetMergeResult(FleetReceipt.rejected("r", "exit 1: conflict in a.kt")))
        assertEquals("exit 1: conflict in a.kt", write.hostWords)
        val maybe = FleetMergePresent.write(FleetMergeResult(FleetReceipt("r", ReceiptStatus.Pending, "no answer")))
        assertEquals(FleetWriteStatus.NotConfirmed, maybe.status)
    }

    @Test
    fun the_header_counts_a_ready_merge_that_waits_on_the_person_until_it_is_merged() {
        assertTrue(FleetMergePresent.waitsOnYou(fleet(), "r-1", local(readiness())))
        assertFalse(FleetMergePresent.waitsOnYou(fleet(), "r-1", local(readiness(ready = false))))
        assertFalse(FleetMergePresent.waitsOnYou(fleet(), "r-1", local(readiness(), { it.copy(merge = FleetWrite(FleetWriteStatus.Confirmed)) })))
        assertFalse(FleetMergePresent.waitsOnYou(fleet(FleetCapabilities.LOOPBACK), "r-1", local(readiness())))
    }

    @Test
    fun the_changes_sheet_lists_the_tasks_steps_with_the_ones_to_look_at_first() {
        val ui = ui(local(readiness()), fleet(items = work(WorkState.Completed, WorkState.Review, WorkState.Cancelled)))
        assertEquals(listOf(WorkState.Review, WorkState.Completed, WorkState.Cancelled), ui.work.map { it.state })
    }
}
