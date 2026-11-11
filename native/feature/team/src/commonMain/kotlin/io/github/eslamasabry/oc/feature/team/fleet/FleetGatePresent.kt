package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.FleetGate
import io.github.eslamasabry.oc.engine.team.fleet.FleetReceipt
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// ---- the Gate sheet of a computer's team (Dart gate_sheet.dart over OrchestrationController, Gas City)

/** How a failed run failed (Dart `TeamFailureClass`), read from the host's error words. */
public enum class FleetFailureClass { Agent, Execution, Test, MergeConflict, Infrastructure, Dependency, Authentication, Context, Unknown }

/** Dart `teamClassifyFailure` and `teamFailureRecoverable`. */
public object FleetFailures {
    private val families: List<Pair<FleetFailureClass, Regex>> =
        listOf(
            FleetFailureClass.MergeConflict to Regex("merge conflict|\\bconflict(s|ed|ing)?\\b|unmerged|rebase", RegexOption.IGNORE_CASE),
            FleetFailureClass.Authentication to
                Regex(
                    "unauthori[sz]ed|unauthenticated|authentication|\\bauth\\b|\\b40[13]\\b|api[ _-]?key|credential|" +
                        "token (expired|invalid|missing)|invalid token|login|forbidden",
                    RegexOption.IGNORE_CASE,
                ),
            FleetFailureClass.Context to
                Regex(
                    "context (window|length|limit|size)|maximum context|too many tokens|token limit|max_tokens|prompt (is )?too long",
                    RegexOption.IGNORE_CASE,
                ),
            FleetFailureClass.Dependency to
                Regex(
                    "dependenc|module ?not ?found|cannot find (module|package)|no such module|unresolved import|import ?error|" +
                        "version solving|pub get|npm install|package .* not found|missing (package|library|module)|not installed",
                    RegexOption.IGNORE_CASE,
                ),
            FleetFailureClass.Test to Regex("\\btests?\\b|assert|expectation|pytest|jest|spec failed|\\d+ (failed|failing)", RegexOption.IGNORE_CASE),
            FleetFailureClass.Infrastructure to
                Regex(
                    "time[d ]?out|connection (refused|reset)|econnrefused|network|\\bdns\\b|unreachable|disk|out of memory|\\boom\\b|" +
                        "\\b50[023]\\b|\\b429\\b|rate limit|server error|unavailable|no space left",
                    RegexOption.IGNORE_CASE,
                ),
            FleetFailureClass.Execution to
                Regex(
                    "exit(ed)? (code|with|status)|non-?zero|command failed|build failed|compil|syntax error|exception|traceback|" +
                        "panic|segfault|permission denied|no such file",
                    RegexOption.IGNORE_CASE,
                ),
            FleetFailureClass.Agent to
                Regex("\\bagent\\b|crash|session (died|ended|lost)|harness|polecat|no response|stalled|stuck|nudge|gave up|abandoned", RegexOption.IGNORE_CASE),
        )

    /** The more specific families are tried first, so "tests failed after a merge conflict" is a merge conflict. */
    public fun classify(text: String?): FleetFailureClass {
        if (text.isNullOrBlank()) return FleetFailureClass.Unknown
        return families.firstOrNull { (_, pattern) -> pattern.containsMatchIn(text) }?.first ?: FleetFailureClass.Unknown
    }

    /** Whether sending the work again can recover it without a person changing something first; null when unknown. */
    public fun recoverable(cls: FleetFailureClass): Boolean? =
        when (cls) {
            FleetFailureClass.Agent, FleetFailureClass.Execution, FleetFailureClass.Infrastructure, FleetFailureClass.Context -> true
            FleetFailureClass.Test, FleetFailureClass.MergeConflict, FleetFailureClass.Dependency, FleetFailureClass.Authentication -> false
            FleetFailureClass.Unknown -> null
        }

    private val destructiveWords =
        Regex(
            "\\b(delete|remove|drop|discard|overwrite|reset|wipe|destroy|purge|erase|force[- ]push|rm -rf|revert|uninstall)\\b",
            RegexOption.IGNORE_CASE,
        )

    /** Dart `teamGateIsDestructive`, from the prompt's words (the wire gate carries no flag). */
    public fun destructive(gate: FleetGate): Boolean = destructiveWords.containsMatchIn("${gate.title} ${gate.prompt.orEmpty()}")
}

/** What the gate belongs to (Dart `teamGateLink`): its task, else its work item, else its agent. */
public enum class FleetGateLinkKind { Run, Work, Agent }

public data class FleetGateLink(val kind: FleetGateLinkKind, val name: String)

/** A work item named on the sheet (the failed task's open work, or what a bead gate unblocks). */
public data class FleetGateWork(val id: String, val title: String, val state: WorkState)

/** Where "Ask the team to fix it" goes: the agent holding the stuck work. */
public data class FleetGateAgent(val id: String, val name: String?)

/** "Send {work} to {agent} again": a retry of the stuck work, only where the failure can recover. */
public data class FleetGateRetry(val workId: String, val workTitle: String, val target: String, val targetName: String?)

/** The ids under Details. */
public enum class FleetGateValue { RequestId, SessionId, Kind, WorkId, RunId }

/** Which of the sheet's own questions is open (each asks first, as Flutter does). */
public enum class FleetGateConfirm { Approve, StopRun }

/** What the sheet remembers while it is open: the typed answer, the picked option, an open question, the last receipt. */
public data class FleetGateLocal(
    val draft: String = "",
    val selected: Int? = null,
    val confirm: FleetGateConfirm? = null,
    val sending: Boolean = false,
    val receipt: FleetReceipt? = null,
)

/** How the last write stands (Dart `KitReceipt` over a `MutationRecord`). */
public enum class FleetGateReceiptKind { Sending, Confirmed, NotConfirmed, Refused }

public enum class FleetGateView {
    /** The team has not been read yet. */
    Waiting,

    /** Answered or closed on the host meanwhile: a state, not a form. */
    Gone,
    Shown,
}

public data class FleetGateUi(
    val view: FleetGateView = FleetGateView.Waiting,
    val gateId: String = "",
    val kind: GateKind = GateKind.Unknown,
    /** The ask: the sheet's title unless a failed task names it ([stoppedTask]). */
    val ask: String = "",
    val stoppedTask: String? = null,
    val link: FleetGateLink? = null,
    val createdAtMs: Long? = null,
    /** The host's fuller words, when they say more than [ask]. */
    val prompt: String? = null,
    val choices: ImmutableList<String> = persistentListOf(),
    val destructive: Boolean = false,
    /** The team runs on this phone (the words say "in the host on this phone"). */
    val onPhone: Boolean = false,
    /** The team takes answers from the phone. */
    val canRespond: Boolean = false,
    val unblocks: ImmutableList<FleetGateWork> = persistentListOf(),
    val affected: ImmutableList<FleetGateWork> = persistentListOf(),
    val failure: FleetFailureClass = FleetFailureClass.Unknown,
    /** The host's error words: Details only. */
    val errorText: String = "",
    val fix: FleetGateAgent? = null,
    val retry: FleetGateRetry? = null,
    val cancelRunId: String? = null,
    val technical: ImmutableList<Pair<FleetGateValue, String>> = persistentListOf(),
    val local: FleetGateLocal = FleetGateLocal(),
    val receipt: FleetGateReceiptKind? = null,
    /** The host's refusal words: Details only. */
    val refusal: String? = null,
) {
    /** Something is on its way, or was settled: the actions rest (an unconfirmed one may have landed). */
    val busy: Boolean
        get() = local.sending

    val hideActions: Boolean
        get() = receipt == FleetGateReceiptKind.NotConfirmed || receipt == FleetGateReceiptKind.Confirmed

    /** This phone can act on the gate at all; when not the sheet says where to answer. */
    val answers: Boolean
        get() =
            when (kind) {
                GateKind.Choice, GateKind.Confirmation, GateKind.FreeText, GateKind.GateBead -> canRespond
                GateKind.RunFailed -> fix != null || retry != null || cancelRunId != null
                GateKind.ReviewReady, GateKind.Unknown -> false
            }
}

/** Pure mapping from a computer's team and the sheet's own state to the Gate sheet (tested without Compose). */
public object FleetGatePresent {
    private val stateOrder =
        listOf(
            WorkState.NeedsInput, WorkState.Blocked, WorkState.Waiting, WorkState.Working, WorkState.Ready, WorkState.Queued,
            WorkState.Review, WorkState.Completed, WorkState.Failed, WorkState.Cancelled, WorkState.Unknown,
        )

    private fun stuck(state: WorkState) = state == WorkState.Blocked || state == WorkState.NeedsInput || state == WorkState.Failed

    private fun open(state: WorkState) = state != WorkState.Completed && state != WorkState.Cancelled

    private fun link(snapshot: FleetSnapshot, gate: FleetGate): FleetGateLink? {
        snapshot.runs.firstOrNull { it.id == gate.runId }?.let { return FleetGateLink(FleetGateLinkKind.Run, it.title) }
        snapshot.work.firstOrNull { it.id == gate.workId }?.let { return FleetGateLink(FleetGateLinkKind.Work, it.title) }
        snapshot.agents.firstOrNull { gate.agentId != null && (it.id == gate.agentId || it.sessionId == gate.agentId) }
            ?.let { return FleetGateLink(FleetGateLinkKind.Agent, it.name) }
        return null
    }

    /** The receipt of a write (Dart `MutationRecord.status`): what came back, in the sheet's own terms. */
    public fun receiptKind(local: FleetGateLocal): FleetGateReceiptKind? =
        when {
            local.sending -> FleetGateReceiptKind.Sending
            local.receipt == null -> null
            else ->
                when (local.receipt.status) {
                    ReceiptStatus.Accepted -> FleetGateReceiptKind.Confirmed
                    // Pending: it may have landed. Never re-sent by itself; only a person's Try again.
                    ReceiptStatus.Pending -> FleetGateReceiptKind.NotConfirmed
                    ReceiptStatus.Rejected -> FleetGateReceiptKind.Refused
                }
        }

    public fun present(fleet: FleetState, gateId: String, local: FleetGateLocal = FleetGateLocal(), onPhone: Boolean = false): FleetGateUi {
        if (!fleet.hasData) return FleetGateUi(FleetGateView.Waiting, gateId = gateId, local = local)
        val snapshot = fleet.snapshot
        val gate = snapshot.gates.firstOrNull { it.id == gateId } ?: return FleetGateUi(FleetGateView.Gone, gateId = gateId, local = local)
        val caps = fleet.capabilities
        val run = gate.runId?.let { id -> snapshot.runs.firstOrNull { it.id == id } }
        val stopped = if (gate.kind == GateKind.RunFailed) run?.title else null
        val prompt = gate.prompt?.trim()?.takeIf { it.isNotEmpty() && it != gate.title }
        val errorText =
            if (gate.kind == GateKind.RunFailed) listOfNotNull(gate.prompt?.trim(), run?.lastError?.trim()).filter { it.isNotEmpty() }.distinct().joinToString(" ")
            else ""
        val failure = FleetFailures.classify(errorText)
        val affected =
            if (gate.kind != GateKind.RunFailed || gate.runId == null) emptyList()
            else
                snapshot.work
                    .filter { it.runId == gate.runId && open(it.state) }
                    .sortedWith(
                        compareByDescending<FleetWork> { stuck(it.state) }
                            .thenBy { stateOrder.indexOf(it.state) },
                    )
        val unblocks =
            if (gate.kind != GateKind.GateBead || gate.workId == null) emptyList()
            else snapshot.work.filter { it.id != gate.workId && gate.workId in it.dependsOn }
        // The agent a failed task's controls go to: the stuck work's assignee, else the gate's, else any open work's.
        val stuckWork = affected.firstOrNull { stuck(it.state) } ?: affected.firstOrNull()
        val agentId = stuckWork?.assignee ?: gate.agentId ?: affected.firstNotNullOfOrNull { it.assignee }
        fun nameOf(id: String?) = id?.let { a -> snapshot.agents.firstOrNull { it.id == a || it.sessionId == a }?.name }
        val failed = gate.kind == GateKind.RunFailed
        val fix = if (failed && caps.controlMessage && agentId != null) FleetGateAgent(agentId, nameOf(agentId)) else null
        // A failure a retry cannot recover offers no retry: something needs changing first, which "fix it" asks for.
        val retry =
            if (failed && caps.controlAssign && stuckWork != null && agentId != null && FleetFailures.recoverable(failure) != false) {
                FleetGateRetry(stuckWork.id, stuckWork.title, agentId, nameOf(agentId))
            } else {
                null
            }
        val cancel = if (failed && caps.controlCancelRun) gate.runId else null
        val technical =
            buildList {
                add(FleetGateValue.RequestId to gate.id)
                gate.agentId?.takeIf { it.isNotEmpty() }?.let { add(FleetGateValue.SessionId to it) }
                gate.rawKind?.takeIf { it.isNotEmpty() }?.let { add(FleetGateValue.Kind to it) }
                gate.workId?.let { add(FleetGateValue.WorkId to it) }
                gate.runId?.let { add(FleetGateValue.RunId to it) }
            }
        val receipt = receiptKind(local)
        return FleetGateUi(
            view = FleetGateView.Shown,
            gateId = gate.id,
            kind = gate.kind,
            ask = gate.title,
            stoppedTask = stopped,
            link = if (stopped != null) null else link(snapshot, gate),
            createdAtMs = gate.createdAtMs,
            prompt = prompt,
            choices = gate.choices.toImmutableList(),
            destructive = gate.kind == GateKind.Confirmation && FleetFailures.destructive(gate),
            onPhone = onPhone,
            canRespond = caps.controlRespond,
            unblocks = unblocks.map { FleetGateWork(it.id, it.title, it.state) }.toImmutableList(),
            affected = affected.map { FleetGateWork(it.id, it.title, it.state) }.toImmutableList(),
            failure = failure,
            errorText = errorText,
            fix = fix,
            retry = retry,
            cancelRunId = cancel,
            technical = technical.toImmutableList(),
            local = local,
            receipt = receipt,
            refusal = local.receipt?.takeIf { it.status == ReceiptStatus.Rejected }?.message?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
