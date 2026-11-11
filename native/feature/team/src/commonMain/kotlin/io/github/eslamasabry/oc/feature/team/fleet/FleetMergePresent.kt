package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.FleetEngine
import io.github.eslamasabry.oc.engine.team.fleet.FleetMergeResult
import io.github.eslamasabry.oc.engine.team.fleet.FleetRun
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.MergeChange
import io.github.eslamasabry.oc.engine.team.fleet.MergeReadiness
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// ---- the task's merge gate on a computer's team (Dart `TeamMergeSection`, TEAM-205, 02-ux §8a)

/** What the section is: nothing, the calm "not ready" panel, the one question card, or the one quiet "merged" line. */
public enum class FleetMergeKind { Hidden, NotReady, Ask, Merged }

public enum class FleetMergeLineState { Done, Working, Failed }

/** One readiness line: a check the host ran. [detail] is shown only for a failed check and for the work-items line. */
public data class FleetMergeLineUi(val key: String, val state: FleetMergeLineState, val detail: String?, val pending: Boolean)

/** Why the act is off, in a form the screen words (Dart `offReason`). */
public sealed interface FleetMergeOff {
    /** The team is not answering: the view may be old. */
    public data object Stale : FleetMergeOff

    /** A host rule blocks the merge; [text] is the host's wording. */
    public data class Boundary(val text: String) : FleetMergeOff
}

/** Why there is no readiness to show. */
public enum class FleetMergeUnavailable { NoRoles, Failed }

public data class FleetMergeWorkRow(val id: String, val title: String, val state: WorkState)

public data class FleetMergeUi(
    val kind: FleetMergeKind = FleetMergeKind.Hidden,
    val loading: Boolean = false,
    val unavailable: FleetMergeUnavailable? = null,
    /** The branch a merge lands on (`main` when the host named none). */
    val branch: String = "main",
    val files: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val checks: Int = 0,
    val lines: ImmutableList<FleetMergeLineUi> = persistentListOf(),
    val requestId: String? = null,
    val approvedBy: String? = null,
    /** The next step is Approve (the request still waits for it), then Merge. */
    val approveFirst: Boolean = false,
    val canApprove: Boolean = false,
    val canMerge: Boolean = false,
    val approving: Boolean = false,
    val merging: Boolean = false,
    /** Why the next step is off, or null when it is on. */
    val off: FleetMergeOff? = null,
    /** The first check that is not ok. */
    val missing: FleetMergeLineUi? = null,
    /** The first host rule that blocks a merge. */
    val blocking: String? = null,
    val approveWrite: FleetWrite? = null,
    val mergeWrite: FleetWrite? = null,
    /** The host refused the merge on one of its rules: already words. */
    val refusedOn: String? = null,
    val mergedBranch: String = "",
    /** The merge commit, in full; the screen shortens it. */
    val mergedCommit: String? = null,
    val readySinceMs: Long? = null,
    val changes: ImmutableList<MergeChange> = persistentListOf(),
    val work: ImmutableList<FleetMergeWorkRow> = persistentListOf(),
    val changesOpen: Boolean = false,
)

/** Reading the merge readiness (Dart `mergeReadinessFor` / `Error` / `Loading`). */
public sealed interface FleetMergeRead {
    public data object Idle : FleetMergeRead

    public data object Loading : FleetMergeRead

    public data class Found(val readiness: MergeReadiness) : FleetMergeRead

    public data object NoRoles : FleetMergeRead

    public data object Failed : FleetMergeRead
}

/** What the section remembers beside the engine's state. */
public data class FleetMergeLocal(
    val read: FleetMergeRead = FleetMergeRead.Idle,
    val approve: FleetWrite? = null,
    val merge: FleetWrite? = null,
    val mergeResult: FleetMergeResult? = null,
    val changesOpen: Boolean = false,
)

/** Pure mapping from a computer's team and the host's readiness document to the merge section (tested without Compose). */
public object FleetMergePresent {
    /**
     * Dart `teamMergeWorkDone`: every work item of [run] is done or review-ready (cancelled items hold nothing back);
     * without tracked items the run's own step counts or completed state decide.
     */
    public fun workDone(run: FleetRun, work: List<FleetWork>): Boolean {
        var any = false
        for (item in work) {
            if (item.runId != run.id) continue
            any = true
            when (item.state) {
                WorkState.Completed, WorkState.Review, WorkState.Cancelled -> Unit
                else -> return false
            }
        }
        if (any) return true
        if (run.state == RunState.Completed) return true
        val total = run.stepCount ?: 0
        return total > 0 && (run.completedSteps ?: 0) >= total
    }

    /**
     * Dart `teamMergeEligible`: the host asks the person to merge (its front's merge roles) and the run's work is done.
     * A team on this phone merges by itself: no gate there.
     */
    public fun eligible(fleet: FleetState, runId: String): Boolean {
        if (!fleet.capabilities.mergeReadiness) return false
        val run = fleet.snapshot.runs.firstOrNull { it.id == runId } ?: return false
        return workDone(run, fleet.snapshot.work)
    }

    /** Dart `teamMergeWaitsOnYou`: eligible, the host says ready, and it is not merged yet (the header counts it). */
    public fun waitsOnYou(fleet: FleetState, runId: String, local: FleetMergeLocal): Boolean {
        if (!eligible(fleet, runId)) return false
        val readiness = (local.read as? FleetMergeRead.Found)?.readiness ?: return false
        if (!readiness.ready || readiness.alreadyMerged) return false
        return local.merge?.status != FleetWriteStatus.Confirmed
    }

    public fun section(fleet: FleetState, runId: String, local: FleetMergeLocal, nowMs: Long): FleetMergeUi {
        if (!eligible(fleet, runId)) return FleetMergeUi()
        val readiness = (local.read as? FleetMergeRead.Found)?.readiness
        val request = readiness?.mergeRequest
        val merged = readiness?.alreadyMerged == true || local.merge?.status == FleetWriteStatus.Confirmed
        val stale = fleet.stale(nowMs)
        val merging = local.merge?.status == FleetWriteStatus.Sending
        val approving = local.approve?.status == FleetWriteStatus.Sending
        val approved = request?.isApproved == true || local.approve?.status == FleetWriteStatus.Confirmed
        val canMerge = readiness != null && readiness.canMerge && !merged && !merging && !stale
        val canApprove = request != null && !approved && !approving && !stale && !merged
        val approveFirst = request != null && !approved && !merged
        val blocking = readiness?.firstBlocking
        val nextIsOn = if (approveFirst) canApprove else canMerge
        val off: FleetMergeOff? =
            when {
                nextIsOn -> null
                !approveFirst && !stale && blocking != null -> FleetMergeOff.Boundary(blocking.text)
                else -> FleetMergeOff.Stale
            }
        val lines =
            readiness?.lines.orEmpty().map { l ->
                FleetMergeLineUi(
                    key = l.key,
                    state = if (l.ok) FleetMergeLineState.Done else if (l.pending) FleetMergeLineState.Working else FleetMergeLineState.Failed,
                    // Dart `_Lines`: the detail of a failed check, and the work-items count whatever its state.
                    detail = l.detail?.takeIf { !l.pending && it.isNotEmpty() && (!l.ok || l.key == "work") },
                    pending = l.pending,
                )
            }
        val result = local.mergeResult
        val kind =
            when {
                merged -> FleetMergeKind.Merged
                readiness != null && readiness.ready -> FleetMergeKind.Ask
                else -> FleetMergeKind.NotReady
            }
        return FleetMergeUi(
            kind = kind,
            loading = readiness == null && local.read is FleetMergeRead.Loading,
            unavailable =
                if (readiness != null) null
                else
                    when (local.read) {
                        FleetMergeRead.NoRoles -> FleetMergeUnavailable.NoRoles
                        FleetMergeRead.Failed -> FleetMergeUnavailable.Failed
                        else -> null
                    },
            branch = readiness?.targetBranch ?: "main",
            files = readiness?.files ?: 0,
            additions = readiness?.additions ?: 0,
            deletions = readiness?.deletions ?: 0,
            checks = lines.size,
            lines = lines.toImmutableList(),
            requestId = request?.id,
            approvedBy = request?.approvedBy,
            approveFirst = approveFirst,
            canApprove = canApprove,
            canMerge = canMerge,
            approving = approving,
            merging = merging,
            off = off,
            missing = lines.firstOrNull { it.state != FleetMergeLineState.Done },
            blocking = blocking?.text,
            approveWrite = local.approve,
            mergeWrite = local.merge,
            refusedOn = if (local.merge?.status == FleetWriteStatus.Refused) result?.boundary else null,
            mergedBranch = (if (local.merge?.status == FleetWriteStatus.Confirmed) result?.branch else null) ?: readiness?.targetBranch.orEmpty(),
            mergedCommit = (if (local.merge?.status == FleetWriteStatus.Confirmed) result?.mergeCommit else null) ?: readiness?.mergeCommit,
            readySinceMs = fleet.snapshot.work.filter { it.runId == runId }.mapNotNull { it.updatedAtMs }.maxOrNull(),
            changes = readiness?.changes.orEmpty().toImmutableList(),
            work = fleet.snapshot.work.filter { it.runId == runId }.sortedBy { it.state.ordinal }.map { FleetMergeWorkRow(it.id, it.title, it.state) }.toImmutableList(),
            changesOpen = local.changesOpen,
        )
    }

    /** Dart `_shortCommit`. */
    public fun shortCommit(sha: String?): String = if (sha == null) "" else if (sha.length > 7) sha.substring(0, 7) else sha

    /** The write of a merge or approval answer: the status for the page, and Details words only for a non-boundary refusal. */
    public fun write(result: FleetMergeResult): FleetWrite {
        val base = FleetWrite.of(result.receipt)
        // A boundary is already words; only the host's own technical text goes under Details.
        return if (result.boundary != null || result.receipt.status == ReceiptStatus.Accepted) base.copy(hostWords = null) else base
    }
}

internal fun FleetEngine.MergeRead.toRead(): FleetMergeRead =
    when (this) {
        is FleetEngine.MergeRead.Found -> FleetMergeRead.Found(readiness)
        FleetEngine.MergeRead.NoRoles -> FleetMergeRead.NoRoles
        FleetEngine.MergeRead.Failed -> FleetMergeRead.Failed
    }
