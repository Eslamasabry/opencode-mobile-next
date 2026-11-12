package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamHostMode
import io.github.eslamasabry.oc.engine.team.TeamProvider
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetAddress
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.engine.team.fleet.FleetGate
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetRun
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// ---- the team page for a team on a computer (Dart TeamHomeScreen over OrchestrationController, Gas City)

/** What the page body shows (Dart `teamScreenState` + the data branch). */
public enum class FleetHomeKind {
    /** Looking for the team, nothing read yet. */
    Starting,

    /** The team could not be found at its address: [FleetHomeUi.failure] says why. */
    Failed,

    /** The demo fixture: the computer-team pages do not show it here. */
    Demo,

    /** The team's tasks. */
    Ready,
}

/** Where the team runs, as one phrase (Dart `teamHostPlace`). */
public sealed interface FleetPlace {
    public data object Phone : FleetPlace

    public data object Computer : FleetPlace

    public data class Named(val name: String) : FleetPlace
}

/** The mark a task row leads with (Dart `teamRunMark`). */
public enum class FleetMark { NeedsYou, Working, Waiting, Failed, Done, Stopped }

/** One task's row (Dart `TeamHomeScreen._body`'s `row`). */
public data class FleetTaskRow(
    val id: String,
    val title: String,
    val mark: FleetMark,
    val state: RunState,
    /** The question the row is ("Needs you · <question>"), when one waits on this task. */
    val question: String? = null,
    val questionAtMs: Long? = null,
    /** The gate behind [question] (or the run's failure): the row opens its Gate sheet. */
    val gateId: String? = null,
    /** Needs the person without a question of its own (a failed step, a gate bead). */
    val needsYou: Boolean = false,
    val done: Int? = null,
    val total: Int? = null,
    val merged: Boolean = false,
    val finishedAtMs: Long? = null,
) {
    val key: String
        get() = "run:$id"
}

/** A question not tied to a listed task: a row of its own at the top (Dart `looseGates`). */
public data class FleetQuestionRow(val id: String, val title: String, val atMs: Long?) {
    val key: String
        get() = "gate:$id"
}

/** The agents in one line (Dart `teamUiCardAgentsSummary`). */
public data class FleetAgentsLine(val total: Int, val working: Int, val waiting: Int, val idle: Int, val stopped: Int)

public data class FleetProjectRow(val id: String, val name: String, val paused: Boolean)

public data class FleetHomeUi(
    val kind: FleetHomeKind = FleetHomeKind.Starting,
    val failure: FleetFailure? = null,
    val place: FleetPlace = FleetPlace.Computer,
    /** The team does not answer (the probe failed, or its data is old): the subtitle says so. */
    val notAnswering: Boolean = false,
    /** Dart `isStale`: the rows are the last ones read, [readAtMs] is when. */
    val stale: Boolean = false,
    val readAtMs: Long? = null,
    /** The phone can only watch: no control is on (Dart `teamUiReadOnlyBody`). */
    val readOnly: Boolean = false,
    /** The team takes a task from the phone (Dart `canStart`: `controlMessage` and data read): "Give the team a task". */
    val canGiveTask: Boolean = false,
    /** Something waits for the person (each question opens its Gate page; a watch-only phone says where to answer). */
    val needsYou: Boolean = false,
    val questions: ImmutableList<FleetQuestionRow> = persistentListOf(),
    val rows: ImmutableList<FleetTaskRow> = persistentListOf(),
    /** Finished tasks past the first [DONE_SHOWN]. */
    val hiddenDone: Int = 0,
    val agents: FleetAgentsLine? = null,
    val projects: ImmutableList<FleetProjectRow> = persistentListOf(),
    /** Today's estimated spend for the whole team; null when the host reports none (unknown is never zero). */
    val costToday: Double? = null,
) {
    public companion object {
        /** Dart `teamHomeDoneShown`. */
        public const val DONE_SHOWN: Int = 3
    }
}

/** Pure mapping from a computer's team to its page (tested without Compose). */
public object FleetPresent {
    /** Dart `teamComputerName`: the address's host name, never an IP address or localhost. */
    public fun place(config: TeamConfig?, hostUrl: String?): FleetPlace {
        if (config?.hostMode == TeamHostMode.Phone) return FleetPlace.Phone
        val name = (hostUrl ?: config?.url)?.let(FleetAddress::host) ?: return FleetPlace.Computer
        val lower = name.lowercase()
        if (lower == "localhost" || lower.endsWith(".localhost")) return FleetPlace.Computer
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(name) || ':' in name) return FleetPlace.Computer
        return FleetPlace.Named(name)
    }

    /** Dart `teamGateRunId`: the gate's run, else its work's run, else the run of the work its agent holds. */
    public fun gateRun(snapshot: FleetSnapshot, gate: FleetGate): String? {
        gate.runId?.let { return it }
        fun runOfWork(id: String?) = id?.let { w -> snapshot.work.firstOrNull { it.id == w }?.runId }
        runOfWork(gate.workId)?.let { return it }
        val agent = snapshot.agents.firstOrNull { it.id == gate.agentId || it.sessionId == gate.agentId } ?: return null
        return runOfWork(agent.currentWorkId)
    }

    /** Dart `teamRunRank`: needs you or failed, working, planning, blocked, waiting, unknown, cancelled, done. */
    private fun rank(run: FleetRun, gated: Set<String>): Int =
        if (run.id in gated || run.state == RunState.Failed) 0
        else
            when (run.state) {
                RunState.Working -> 1
                RunState.Planning -> 2
                RunState.Blocked -> 3
                RunState.Waiting -> 4
                RunState.Unknown -> 5
                RunState.Cancelled -> 6
                RunState.Failed -> 0
                RunState.Completed -> 7
            }

    private fun mark(run: FleetRun, needsYou: Boolean): FleetMark =
        if (needsYou) FleetMark.NeedsYou
        else
            when (run.state) {
                RunState.Failed -> FleetMark.Failed
                RunState.Completed -> FleetMark.Done
                RunState.Cancelled -> FleetMark.Stopped
                RunState.Working, RunState.Planning -> FleetMark.Working
                RunState.Waiting, RunState.Blocked, RunState.Unknown -> FleetMark.Waiting
            }

    public fun home(config: TeamConfig?, fleet: FleetState, nowMs: Long, doneExpanded: Boolean = false): FleetHomeUi {
        val place = place(config, fleet.host?.url)
        if (config?.provider == TeamProvider.Fixture) return FleetHomeUi(FleetHomeKind.Demo, place = place)
        if (fleet.phase == FleetPhase.Failed && !fleet.hasData) {
            return FleetHomeUi(FleetHomeKind.Failed, failure = fleet.failure, place = place, notAnswering = true)
        }
        if (!fleet.hasData) return FleetHomeUi(FleetHomeKind.Starting, place = place)
        val snapshot = fleet.snapshot
        val stale = fleet.phase == FleetPhase.Failed || fleet.stale(nowMs)
        // What needs the person (a review that is ready only informs), most recent first.
        val gates = snapshot.gates.filter { it.kind != GateKind.ReviewReady }.sortedByDescending { it.createdAtMs ?: Long.MIN_VALUE }
        val runs = snapshot.visibleRuns
        val listed = runs.map { it.id }.toSet()
        val gateOfRun = LinkedHashMap<String, FleetGate>()
        val loose = ArrayList<FleetGate>()
        val gated = HashSet<String>()
        for (gate in gates) {
            val run = gateRun(snapshot, gate)
            run?.let(gated::add)
            if (run != null && run in listed && run !in gateOfRun) gateOfRun[run] = gate else loose += gate
        }
        val ordered =
            runs.sortedWith(
                compareBy<FleetRun> { rank(it, gated) }.thenByDescending { it.updatedAtMs ?: it.startedAtMs ?: Long.MIN_VALUE },
            )
        val rows =
            ordered.map { run ->
                val gate = gateOfRun[run.id]
                val needsYou = gate != null || run.id in gated
                FleetTaskRow(
                    id = run.id,
                    title = run.title,
                    mark = mark(run, needsYou),
                    state = run.state,
                    question = gate?.title,
                    questionAtMs = gate?.createdAtMs,
                    gateId = gate?.id,
                    needsYou = needsYou,
                    done = run.completedSteps,
                    total = run.stepCount,
                    merged = run.merged,
                    finishedAtMs = run.finishedAtMs ?: run.updatedAtMs.takeIf { run.finished },
                )
            }
        val open = rows.filter { it.state != RunState.Completed && it.state != RunState.Cancelled }
        val done = rows.filter { it.state == RunState.Completed || it.state == RunState.Cancelled }
        val shown = if (doneExpanded) done else done.take(FleetHomeUi.DONE_SHOWN)
        val agents = snapshot.agents
        return FleetHomeUi(
            kind = FleetHomeKind.Ready,
            place = place,
            notAnswering = stale,
            stale = stale,
            readAtMs = fleet.refreshedAtMs,
            readOnly = !fleet.capabilities.anyControl,
            canGiveTask = fleet.capabilities.controlMessage,
            needsYou = gates.isNotEmpty(),
            questions = loose.map { FleetQuestionRow(it.id, it.title, it.createdAtMs) }.toImmutableList(),
            rows = (open + shown).toImmutableList(),
            hiddenDone = done.size - shown.size,
            agents =
                agents.takeIf { it.isNotEmpty() }?.let { a ->
                    FleetAgentsLine(
                        total = a.size,
                        working = a.count { it.state == AgentState.Working },
                        waiting = a.count { it.state == AgentState.Waiting || it.state == AgentState.Blocked },
                        idle = a.count { it.state == AgentState.Idle },
                        stopped = a.count { it.state == AgentState.Stopped || it.state == AgentState.Crashed },
                    )
                },
            projects = snapshot.projects.map { FleetProjectRow(it.id, it.name, it.suspended) }.toImmutableList(),
            costToday = snapshot.usage?.costUsdToday,
        )
    }
}
