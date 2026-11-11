package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.engine.team.TeamHostMode

/*
 * The product models of a computer's team (Dart lib/orchestration/models/*.dart). Field names follow Dart; times are
 * epoch milliseconds. `lastError`, `prompt` and other host text are the host's own words: pages show them only under
 * Details.
 */

/** Dart `_normalize`: camelCase to snake_case, spaces and dashes to `_`, lower case. */
internal fun normalizeWord(value: String?): String {
    if (value == null) return ""
    val camel = Regex("([a-z0-9])([A-Z])").replace(value.trim()) { "${it.groupValues[1]}_${it.groupValues[2]}" }
    return Regex("[\\s-]+").replace(camel.lowercase(), "_")
}

/** Dart `RunState`. */
public enum class RunState {
    Planning, Working, Waiting, Blocked, Failed, Completed, Cancelled, Unknown;

    public companion object {
        public fun fromProvider(value: String?): RunState =
            when (normalizeWord(value)) {
                "planning", "pending", "queued", "plan" -> Planning
                "working", "running", "active", "in_progress" -> Working
                "waiting" -> Waiting
                "blocked" -> Blocked
                "failed", "error" -> Failed
                "completed", "complete", "done", "closed", "succeeded" -> Completed
                "cancelled", "canceled" -> Cancelled
                else -> Unknown
            }
    }
}

/** Dart `RunKind`: a formula run or a batch (convoy). */
public enum class RunKind { Formula, Batch, Unknown }

/** Dart `OrchestrationRun`. [upkeep] runs are the host's own chores and are never listed or counted. */
public data class FleetRun(
    val id: String,
    val title: String,
    val state: RunState,
    val rawState: String? = null,
    val kind: RunKind = RunKind.Unknown,
    val projectId: String? = null,
    val formula: String? = null,
    val stepCount: Int? = null,
    val completedSteps: Int? = null,
    val lastError: String? = null,
    val startedAtMs: Long? = null,
    val updatedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val merged: Boolean = false,
    val upkeep: Boolean = false,
) {
    val finished: Boolean
        get() = state == RunState.Completed || state == RunState.Cancelled
}

/** Dart `WorkState`. */
public enum class WorkState {
    Queued, Ready, Working, Waiting, Blocked, NeedsInput, Review, Failed, Completed, Cancelled, Unknown;

    public companion object {
        public fun fromProvider(value: String?): WorkState =
            when (normalizeWord(value)) {
                "queued", "open", "pending", "todo" -> Queued
                "ready" -> Ready
                "working", "in_progress", "active", "running" -> Working
                "waiting" -> Waiting
                "blocked" -> Blocked
                "needs_input", "needsinput", "input" -> NeedsInput
                "review", "needs_review", "in_review" -> Review
                "failed", "error" -> Failed
                "completed", "complete", "closed", "done" -> Completed
                "cancelled", "canceled" -> Cancelled
                else -> Unknown
            }

        /** Dart `WorkState.derive`: highest precedence first. */
        public fun derive(
            status: String?,
            isBlocked: Boolean = false,
            isReady: Boolean = false,
            sessionWaiting: Boolean = false,
            needsInput: Boolean = false,
            needsReview: Boolean = false,
            lastError: String? = null,
            closedReason: String? = null,
        ): WorkState {
            val base = fromProvider(status)
            if (base == Completed && normalizeWord(closedReason) == "cancelled") return Cancelled
            if (base == Cancelled || base == Completed) return base
            if (!lastError.isNullOrBlank()) return Failed
            if (isBlocked) return Blocked
            if (needsInput) return NeedsInput
            if (sessionWaiting) return Waiting
            if (needsReview) return Review
            if (base == Queued && isReady) return Ready
            return base
        }
    }
}

/** Dart `WorkItem` (a Gas City bead). [assigneeOwn] is the bead's own `assignee`, without the routed pool. */
public data class FleetWork(
    val id: String,
    val title: String,
    val state: WorkState,
    val rawState: String? = null,
    val projectId: String? = null,
    val runId: String? = null,
    val parentId: String? = null,
    val assignee: String? = null,
    val assigneeOwn: String? = null,
    val sessionId: String? = null,
    val isBlocked: Boolean = false,
    val labels: List<String> = emptyList(),
    val dependsOn: List<String> = emptyList(),
    val closedReason: String? = null,
    val description: String? = null,
    val branch: String? = null,
    val mergeResult: String? = null,
    val createdAtMs: Long? = null,
    val updatedAtMs: Long? = null,
)

/** Dart `AgentState`. */
public enum class AgentState {
    Working, Idle, Waiting, Blocked, Stopped, Crashed, Unknown;

    public companion object {
        public fun fromProvider(value: String?): AgentState =
            when (normalizeWord(value)) {
                "working", "active", "running", "busy" -> Working
                "idle" -> Idle
                "waiting" -> Waiting
                "blocked" -> Blocked
                "stopped", "suspended", "paused", "exited" -> Stopped
                "crashed", "error", "failed" -> Crashed
                else -> Unknown
            }
    }
}

/**
 * Dart `OrchestrationAgent`. [hasAgentEntry] is false for a pool instance known only from its session (its id is the
 * session id): the agent routes do not exist for it, only the session's.
 */
public data class FleetAgent(
    val id: String,
    val name: String,
    val state: AgentState,
    val rawState: String? = null,
    val sessionId: String? = null,
    val sessionName: String? = null,
    val pool: String? = null,
    val pack: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val harness: String? = null,
    val currentWorkId: String? = null,
    val lastActivityMs: Long? = null,
    val contextPercent: Int? = null,
    val workDir: String? = null,
    val branch: String? = null,
    val sessionStartedAtMs: Long? = null,
    val suspended: Boolean = false,
    val unavailableReason: String? = null,
    val hasAgentEntry: Boolean = true,
)

/** Dart `GateKind`. */
public enum class GateKind { Choice, Confirmation, FreeText, GateBead, RunFailed, ReviewReady, Unknown;

    /** Dart `attentionCount`: everything but a review that is ready needs the person. */
    public val needsPerson: Boolean
        get() = this != ReviewReady

    public companion object {
        public fun fromProvider(value: String?): GateKind =
            when (normalizeWord(value)) {
                "choice", "select", "option" -> Choice
                "confirmation", "confirm", "yes_no", "approval", "tool_approval" -> Confirmation
                "free_text", "freetext", "text", "input" -> FreeText
                "gate_bead", "gate", "bead" -> GateBead
                "run_failed", "failed" -> RunFailed
                "review_ready", "review", "needs_review" -> ReviewReady
                else -> Unknown
            }
    }
}

/** Dart `OrchestrationGate`: something the team waits on. [title] may be the host's question itself. */
public data class FleetGate(
    val id: String,
    val kind: GateKind,
    val title: String,
    val rawKind: String? = null,
    val prompt: String? = null,
    val workId: String? = null,
    val runId: String? = null,
    val agentId: String? = null,
    val choices: List<String> = emptyList(),
    val createdAtMs: Long? = null,
)

/** Dart `OrchestrationProject` (a Gas City rig). */
public data class FleetProject(val id: String, val name: String, val directory: String? = null, val suspended: Boolean = false)

/** Dart `ScheduledJobTrigger`. */
public enum class FleetJobTrigger { Cron, Interval, Event, Manual, Webhook, Condition, Other }

/** Dart `ScheduledJob` (a Gas City order). */
public data class FleetJob(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: FleetJobTrigger,
    val projectId: String? = null,
    val schedule: String? = null,
    val interval: String? = null,
    val onEvent: String? = null,
    val description: String? = null,
)

/** Dart `OrchestrationUsage`, the part a page shows: counts and today's estimated cost (never billing). */
public data class FleetUsage(
    val activeAgents: Int? = null,
    val runsInProgress: Int? = null,
    val workOpen: Int? = null,
    val workReady: Int? = null,
    val workInProgress: Int? = null,
    val inputTokensToday: Long? = null,
    val outputTokensToday: Long? = null,
    val costUsdToday: Double? = null,
    val estimated: Boolean = true,
)

/** Dart `OrchestrationHostIdentity`: never carries a secret. */
public data class FleetHost(
    val url: String,
    val hostMode: TeamHostMode,
    val city: String? = null,
    val version: String? = null,
    /** The host front answered; writes go through it with an `Idempotency-Key`. */
    val front: Boolean = false,
)

/** Dart `OrchestrationCapabilities`, the Gas City switches. Every control is drawn only when its flag is on. */
public data class FleetCapabilities(
    val read: Boolean = false,
    val agentOutput: Boolean = false,
    val controlRespond: Boolean = false,
    val controlMessage: Boolean = false,
    val controlAgent: Boolean = false,
    val controlCancelRun: Boolean = false,
    val controlAssign: Boolean = false,
    val controlCreateWork: Boolean = false,
    val controlProject: Boolean = false,
    val controlProjectRemove: Boolean = false,
    val controlAgentKill: Boolean = false,
    val scheduledJobs: Boolean = false,
    val controlScheduledJobs: Boolean = false,
    val mergeReadiness: Boolean = false,
    val phoneHost: Boolean = false,
) {
    /** Any write is possible (Dart `anyControl`). */
    val anyControl: Boolean
        get() =
            controlRespond || controlMessage || controlAgent || controlCancelRun || controlAssign || controlCreateWork ||
                controlProject || controlProjectRemove || controlAgentKill || controlScheduledJobs

    public companion object {
        public val NONE: FleetCapabilities = FleetCapabilities()

        /** Dart `gascityRead`: reads, the stream and the scheduled jobs list; no writes. */
        public val READ: FleetCapabilities = FleetCapabilities(read = true, agentOutput = true, scheduledJobs = true)

        /** Dart `gascityFront`: every control through the host front, and its merge roles. */
        public val FRONT: FleetCapabilities =
            READ.copy(
                controlRespond = true, controlMessage = true, controlAgent = true, controlCancelRun = true,
                controlAssign = true, controlProject = true, controlProjectRemove = true, controlAgentKill = true,
                controlScheduledJobs = true, mergeReadiness = true,
            )

        /** Dart `gascityLoopback`: the team runs on this phone; controls straight to the supervisor, no merge roles. */
        public val LOOPBACK: FleetCapabilities =
            READ.copy(
                controlRespond = true, controlMessage = true, controlAgent = true, controlCancelRun = true,
                controlAssign = true, controlCreateWork = true, controlProject = true, controlAgentKill = true,
                controlScheduledJobs = true, phoneHost = true,
            )
    }
}

/** Everything one read of the team gave (Dart `OrchestrationSnapshot`, the scopes a page shows). */
public data class FleetSnapshot(
    val projects: List<FleetProject> = emptyList(),
    val runs: List<FleetRun> = emptyList(),
    val work: List<FleetWork> = emptyList(),
    val agents: List<FleetAgent> = emptyList(),
    val gates: List<FleetGate> = emptyList(),
    val usage: FleetUsage? = null,
) {
    /** Runs a person sees: the host's chores left out (Dart `teamVisibleRuns`). */
    val visibleRuns: List<FleetRun>
        get() = runs.filter { !it.upkeep }

    /** Dart `attentionCount`. */
    val attentionCount: Int
        get() = gates.count { it.kind.needsPerson }
}

/** A person's answer to a gate (Dart `GateResponse`). */
public sealed interface FleetAnswer {
    public data class Choice(val option: String) : FleetAnswer

    public data class Confirm(val yes: Boolean) : FleetAnswer

    public data class Text(val text: String) : FleetAnswer
}

/** Dart `AgentControlAction` (`start` is `Resume`). */
public enum class FleetAgentAction { Nudge, Pause, Resume, Stop, Restart, Kill }

/** Dart `ProjectControlAction`. */
public enum class FleetProjectAction { Suspend, Resume, Remove }

/** One write a person asks of the team (Dart `OrchestrationControlGateway` + `OrchestrationRigGateway`). */
public sealed interface FleetControl {
    public data class Answer(val gateId: String, val answer: FleetAnswer) : FleetControl

    public data class Message(val agentId: String, val text: String) : FleetControl

    public data class Agent(val agentId: String, val action: FleetAgentAction) : FleetControl

    public data class CancelRun(val runId: String) : FleetControl

    public data class Assign(val workId: String, val agentId: String) : FleetControl

    public data class CreateWork(val title: String, val description: String? = null, val projectId: String? = null) : FleetControl

    public data class Project(val projectId: String, val action: FleetProjectAction) : FleetControl

    public data class Job(val jobId: String, val enabled: Boolean) : FleetControl
}

/** Dart `MutationReceiptStatus` (timed-out is [Pending] whose result never came). */
public enum class ReceiptStatus { Pending, Accepted, Rejected }

/**
 * What came back from one write (Dart `MutationReceipt`). [message] is the host's words, for Details only.
 * [retryable]: the front said nothing was stored. [createdId]: the id a create answered with.
 */
public data class FleetReceipt(
    val requestId: String,
    val status: ReceiptStatus,
    val message: String? = null,
    val retryable: Boolean = false,
    val correlationId: String? = null,
    val hostRequestId: String? = null,
    val createdId: String? = null,
) {
    val accepted: Boolean
        get() = status == ReceiptStatus.Accepted

    public companion object {
        public const val FRONT_REQUIRED: String = "front required"

        public fun rejected(requestId: String, message: String): FleetReceipt = FleetReceipt(requestId, ReceiptStatus.Rejected, message)
    }
}
