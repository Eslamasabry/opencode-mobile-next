package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgent
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetRun
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.FleetWork
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.engine.team.fleet.RunKind
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// ---- one task of a computer's team: its steps as a graph, and one step's sheet
// (Dart task_details_sheet.dart, work_sheet.dart, work_graph.dart over OrchestrationController)

/** The four stages a task moves through, each named by its outcome (Dart `TeamStage`): Planned, Working, In review, Merged. */
public enum class FleetStage { Waiting, Working, Reviewing, Done }

/** The one word for a task's state, with the merge named (Dart `teamRunStateWordFor`). */
public enum class FleetRunWord { Planning, Working, Waiting, Blocked, Failed, Cancelled, Completed, Merged, WaitingMerge, Unknown }

/** One step of the task, a node of its graph (Dart `WorkGraphNode`). */
public data class FleetStepNode(val id: String, val title: String, val state: WorkState, val dependsOn: List<String>)

public enum class FleetRunKind { Starting, Failed, Missing, Ready }

/** Technical details of a task, in Flutter's order. */
public enum class FleetRunFactKind { Kind, RunId, Formula, Project, RawState, LastError }

public data class FleetRunFact(val kind: FleetRunFactKind, val value: Redacted)

/** What the task shows of its Stop (Dart `teamChatStopTask` + its receipt). */
public data class FleetRunSent(val status: ReceiptStatus, val message: String? = null, val retryable: Boolean = false)

public data class FleetRunUi(
    val kind: FleetRunKind = FleetRunKind.Starting,
    val failure: FleetFailure? = null,
    val place: FleetPlace = FleetPlace.Computer,
    val notAnswering: Boolean = false,
    val stale: Boolean = false,
    val readAtMs: Long? = null,
    val id: String = "",
    val title: String = "",
    val word: FleetRunWord = FleetRunWord.Unknown,
    val state: RunState = RunState.Unknown,
    /** Steps done and in all (Dart `TeamRunProgress`); the line says it only for more than one step. */
    val done: Int = 0,
    val total: Int = 0,
    /** How long it has run, or took (Dart `_elapsed`). */
    val elapsedMs: Long? = null,
    val stage: FleetStage? = null,
    val steps: ImmutableList<FleetStepNode> = persistentListOf(),
    /** Questions that wait on the person for this task; answering is the gate sheet's. */
    val questions: ImmutableList<FleetAgentGate> = persistentListOf(),
    /** Stop task is on: the computer takes it and the task is still open. */
    val canStop: Boolean = false,
    val busy: Boolean = false,
    val sent: FleetRunSent? = null,
    val facts: ImmutableList<FleetRunFact> = persistentListOf(),
)

/** A step another step depends on, or that depends on it. [state] null: the host no longer lists it. */
public data class FleetWorkLink(val id: String, val title: String?, val state: WorkState?)

/** The agent working on a step. */
public data class FleetWorkOwner(val name: String, val agentId: String?, val title: FleetAgentTitle?, val state: io.github.eslamasabry.oc.engine.team.fleet.AgentState?)

public enum class FleetWorkFactKind { Branch, Id, RawState, Run, Parent, Project, Assignee, Session, Labels, DependsOn, ClosedReason }

public data class FleetWorkFact(val kind: FleetWorkFactKind, val value: Redacted)

/** The work sheet: what a step row or a graph node opens (Dart `WorkSheet`). */
public data class FleetWorkUi(
    val id: String = "",
    /** False: the step is gone from the host meanwhile (a designed state, not an empty sheet). */
    val found: Boolean = false,
    val title: String = "",
    /** The task it belongs to, in the person's words; null when it shares the step's title. */
    val task: String? = null,
    val state: WorkState = WorkState.Unknown,
    val owner: FleetWorkOwner? = null,
    val description: String? = null,
    val dependencies: ImmutableList<FleetWorkLink> = persistentListOf(),
    val blocking: ImmutableList<FleetWorkLink> = persistentListOf(),
    val createdAtMs: Long? = null,
    val updatedAtMs: Long? = null,
    val facts: ImmutableList<FleetWorkFact> = persistentListOf(),
)

/** Pure mapping from a computer's tasks to their page and sheet (tested without Compose). */
public object FleetRunPresent {
    private fun open(state: WorkState) = state != WorkState.Completed && state != WorkState.Cancelled

    private fun open(run: FleetRun) = run.state != RunState.Completed && run.state != RunState.Cancelled && run.state != RunState.Failed

    /** Dart `teamRunStage`, without the dispatch cycles the phone does not have: a step in review puts the task in review. */
    public fun stage(run: FleetRun, work: List<FleetWork>): FleetStage? =
        when (run.state) {
            RunState.Completed -> if (work.any { open(it.state) }) FleetStage.Reviewing else FleetStage.Done
            RunState.Failed, RunState.Cancelled -> null
            RunState.Working, RunState.Blocked, RunState.Waiting, RunState.Planning, RunState.Unknown ->
                when {
                    work.any { it.state == WorkState.Review } && work.none { it.state == WorkState.Working } -> FleetStage.Reviewing
                    run.state == RunState.Working || run.state == RunState.Blocked -> FleetStage.Working
                    else -> FleetStage.Waiting
                }
        }

    /** Dart `teamRunStateWordFor`: a completed task with a step still open is waiting for the merge; one whose work landed is merged. */
    public fun word(run: FleetRun, stage: FleetStage?): FleetRunWord =
        when {
            run.state == RunState.Completed && stage == FleetStage.Reviewing -> FleetRunWord.WaitingMerge
            run.state == RunState.Completed && run.merged -> FleetRunWord.Merged
            else ->
                when (run.state) {
                    RunState.Planning -> FleetRunWord.Planning
                    RunState.Working -> FleetRunWord.Working
                    RunState.Waiting -> FleetRunWord.Waiting
                    RunState.Blocked -> FleetRunWord.Blocked
                    RunState.Failed -> FleetRunWord.Failed
                    RunState.Cancelled -> FleetRunWord.Cancelled
                    RunState.Completed -> FleetRunWord.Completed
                    RunState.Unknown -> FleetRunWord.Unknown
                }
        }

    /** Dart `TeamRunProgress`: done and in all, from the task's steps when the host lists them, else its own counts. */
    public fun progress(run: FleetRun, work: List<FleetWork>): Pair<Int, Int> {
        if (work.isNotEmpty()) return work.count { it.state == WorkState.Completed } to work.size
        val total = run.stepCount ?: 0
        return (run.completedSteps ?: 0).coerceIn(0, total) to total
    }

    /** Dart `_elapsed`: since it started; for a task that ended, how long it took. */
    public fun elapsedMs(run: FleetRun, nowMs: Long): Long? {
        val started = run.startedAtMs ?: return null
        val end = if (open(run)) nowMs else run.updatedAtMs ?: nowMs
        return (end - started).coerceAtLeast(0)
    }

    private fun place(config: TeamConfig?, fleet: FleetState) = FleetPresent.place(config, fleet.host?.url)

    /** The task's own page (Dart Task details, as a page): where it stands, its steps as a graph, Stop task, Details. */
    public fun run(
        config: TeamConfig?,
        fleet: FleetState,
        runId: String,
        nowMs: Long,
        redact: (String) -> Redacted,
        sent: FleetRunSent? = null,
        busy: Boolean = false,
    ): FleetRunUi {
        val place = place(config, fleet)
        if (config?.provider == TeamProvider.Fixture) return FleetRunUi(FleetRunKind.Missing, place = place, id = runId)
        if (fleet.phase == FleetPhase.Failed && !fleet.hasData) {
            return FleetRunUi(FleetRunKind.Failed, failure = fleet.failure, place = place, notAnswering = true, id = runId)
        }
        if (!fleet.hasData) return FleetRunUi(FleetRunKind.Starting, place = place, id = runId)
        val snapshot = fleet.snapshot
        val stale = fleet.phase == FleetPhase.Failed || fleet.stale(nowMs)
        val run = snapshot.runs.firstOrNull { it.id == runId }
            ?: return FleetRunUi(FleetRunKind.Missing, place = place, notAnswering = stale, stale = stale, readAtMs = fleet.refreshedAtMs, id = runId)
        val work = snapshot.work.filter { it.runId == run.id }
        val (done, total) = progress(run, work)
        val stage = stage(run, work)
        val questions =
            snapshot.gates.filter { it.kind != GateKind.ReviewReady && FleetPresent.gateRun(snapshot, it) == run.id }.map { FleetAgentGate(it.id, it.title) }
        return FleetRunUi(
            kind = FleetRunKind.Ready,
            place = place,
            notAnswering = stale,
            stale = stale,
            readAtMs = fleet.refreshedAtMs,
            id = run.id,
            title = run.title,
            word = word(run, stage),
            state = run.state,
            done = done,
            total = total,
            elapsedMs = elapsedMs(run, nowMs),
            stage = stage,
            steps = work.map { FleetStepNode(it.id, it.title, it.state, it.dependsOn) }.toImmutableList(),
            questions = questions.toImmutableList(),
            canStop = fleet.capabilities.controlCancelRun && open(run),
            busy = busy,
            sent = sent,
            facts = runFacts(run, redact).toImmutableList(),
        )
    }

    private fun runFacts(run: FleetRun, redact: (String) -> Redacted): List<FleetRunFact> {
        val out = ArrayList<FleetRunFact>()

        fun add(kind: FleetRunFactKind, value: String?) {
            if (!value.isNullOrBlank()) out += FleetRunFact(kind, redact(value))
        }
        add(
            FleetRunFactKind.Kind,
            when (run.kind) {
                RunKind.Batch -> "convoy"
                RunKind.Formula -> "formula"
                RunKind.Unknown -> null
            },
        )
        add(FleetRunFactKind.RunId, run.id)
        add(FleetRunFactKind.Formula, run.formula)
        add(FleetRunFactKind.Project, run.projectId)
        add(FleetRunFactKind.RawState, run.rawState)
        add(FleetRunFactKind.LastError, run.lastError)
        return out
    }

    /** Dart `workOwnerName`: the agent on the step (by work id), else the assignee matched to an agent, else the assignee as sent. */
    private fun ownerOf(snapshot: FleetSnapshot, work: FleetWork): FleetAgent? {
        snapshot.agents.firstOrNull { it.currentWorkId == work.id }?.let { return it }
        val assignee = work.assignee?.takeIf { it.isNotEmpty() } ?: return null
        return snapshot.agents.firstOrNull { it.id == assignee || it.name == assignee || it.sessionId == assignee || it.sessionName == assignee }
    }

    /** Dart `workOwnerShortName`: the agent's own name ("furiosa" for "ocproof/gastown.furiosa"); the full handle stays in Details. */
    public fun shortName(owner: String): String = owner.substringAfterLast('/').substringAfterLast('.').trim().ifEmpty { owner }

    /** The step's sheet. */
    public fun work(fleet: FleetState, workId: String, redact: (String) -> Redacted): FleetWorkUi {
        val snapshot = fleet.snapshot
        val item = snapshot.work.firstOrNull { it.id == workId } ?: return FleetWorkUi(id = workId)
        val run = item.runId?.let { id -> snapshot.runs.firstOrNull { it.id == id } }
        // A one-step task shares its step's title: the title says it once.
        val task = if (run != null) run.title.takeIf { it != item.title } else item.projectId?.trim()?.takeIf { it.isNotEmpty() }
        val byId = snapshot.work.associateBy { it.id }
        val titles = FleetAgentsPresent.titles(snapshot.agents)
        val agent = ownerOf(snapshot, item)
        val owner =
            when {
                agent != null -> FleetWorkOwner(shortName(agent.name), agent.id, titles[agent.id], agent.state)
                !item.assignee.isNullOrEmpty() -> FleetWorkOwner(shortName(item.assignee!!), null, null, null)
                else -> null
            }
        fun link(id: String) = byId[id].let { FleetWorkLink(id, it?.title, it?.state) }
        val dependencies = item.dependsOn.distinct().filter { it != item.id }.map(::link)
        val blocking = snapshot.work.filter { it.id != item.id && item.id in it.dependsOn }.map { FleetWorkLink(it.id, it.title, it.state) }
        return FleetWorkUi(
            id = item.id,
            found = true,
            title = item.title,
            task = task,
            state = item.state,
            owner = owner,
            description = item.description?.trim()?.takeIf { it.isNotEmpty() },
            dependencies = dependencies.toImmutableList(),
            blocking = blocking.toImmutableList(),
            createdAtMs = item.createdAtMs,
            updatedAtMs = item.updatedAtMs,
            facts = workFacts(item, dependencies, redact).toImmutableList(),
        )
    }

    private fun workFacts(item: FleetWork, dependencies: List<FleetWorkLink>, redact: (String) -> Redacted): List<FleetWorkFact> {
        val out = ArrayList<FleetWorkFact>()

        fun add(kind: FleetWorkFactKind, value: String?) {
            if (!value.isNullOrBlank()) out += FleetWorkFact(kind, redact(value))
        }
        add(FleetWorkFactKind.Branch, item.branch)
        add(FleetWorkFactKind.Id, item.id)
        add(FleetWorkFactKind.RawState, item.rawState)
        add(FleetWorkFactKind.Run, item.runId)
        add(FleetWorkFactKind.Parent, item.parentId)
        add(FleetWorkFactKind.Project, item.projectId)
        add(FleetWorkFactKind.Assignee, item.assignee)
        add(FleetWorkFactKind.Session, item.sessionId)
        add(FleetWorkFactKind.Labels, item.labels.joinToString(", "))
        add(FleetWorkFactKind.DependsOn, dependencies.joinToString(", ") { it.id })
        add(FleetWorkFactKind.ClosedReason, item.closedReason)
        return out
    }
}
