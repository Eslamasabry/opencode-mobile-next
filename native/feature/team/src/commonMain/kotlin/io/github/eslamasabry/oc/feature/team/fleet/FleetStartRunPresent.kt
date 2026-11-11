package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgent
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// ---- Start a run on a computer's team (Dart start_run_sheet.dart + state/team_planning.dart, Gas City)

/** Dart `TeamSupervision`: [line] is the English line the planner reads (server content is never translated). */
public enum class FleetSupervision(public val line: String) {
    High("High — ask before every decision, before state-changing tests and before any merge"),
    Balanced("Balanced — decide routine matters, ask before merges, on failures and on design choices"),
    Autonomous("Autonomous — work to completion inside the host boundaries; ask only when unable to continue"),
}

/** Why the team cannot take a task now (Dart `_Blocked`). */
public enum class FleetBlockedWhy { PlannerOff, NoPlanner, NoProject }

/** What the sheet is: the planner form, the direct task, or why neither. */
public enum class FleetStartMode { Waiting, Planner, Direct, Blocked }

/** What a direct task ended in when it did not simply go through (Dart `TeamDispatchPhase`, the ones that stay on the sheet). */
public enum class FleetDirectOutcome {
    None,

    /** The task was not made: edit it and send again. */
    CreateRefused,

    /** It may exist: no blind resend; the words are kept. */
    CreateUnconfirmed,

    /** It exists but could not be given to the project's worker; it stays on the board, given to no one. */
    AssignRefused,

    /** It exists; whether it reached the team is not known. */
    DispatchUnconfirmed,
}

/** The planner's wake, from the blocked state (Dart `_TeamStartBlockedState`). */
public data class FleetWakeLocal(
    val busy: Boolean = false,
    /** The host took the wake; the planner is not listed awake yet. */
    val asked: Boolean = false,
    /** A check found the team still unable. */
    val stillOff: Boolean = false,
    /** The host refused the wake: its words ('' when it gave none), Details only. */
    val refused: String? = null,
)

/** What the sheet remembers while it is open. */
public data class FleetStartLocal(
    val objective: String = "",
    val task: String = "",
    val details: String = "",
    /** The planner form's project; null lets the planner choose. */
    val projectId: String? = null,
    /** The direct task's project; null follows the first listed. */
    val directProjectId: String? = null,
    val supervision: FleetSupervision = FleetSupervision.High,
    val showEmpty: Boolean = false,
    val showTaskEmpty: Boolean = false,
    val sending: Boolean = false,
    val waking: Boolean = false,
    /** The task is being made / handed over (Dart `TeamDispatchPhase.creating`, `.sending`). */
    val stage: FleetDirectStage? = null,
    /** The planner's refusal of the last send: the host's words ('' when none), Details only. */
    val refused: String? = null,
    /** The message left this phone but nothing confirmed it. */
    val unconfirmed: Boolean = false,
    val outcome: FleetDirectOutcome = FleetDirectOutcome.None,
    /** The host's words for the direct task's problem: Details only. */
    val directWords: String? = null,
    val wake: FleetWakeLocal = FleetWakeLocal(),
)

public enum class FleetDirectStage { Creating, Sending }

public data class FleetStartProject(val id: String, val name: String)

public data class FleetStartUi(
    val mode: FleetStartMode = FleetStartMode.Waiting,
    val blocked: FleetBlockedWhy? = null,
    /** The planner is listed and this team takes agent controls: Wake it. */
    val wakeable: Boolean = false,
    val plannerId: String? = null,
    /** The planner has no live session yet, so a send wakes it first. */
    val plannerAsleep: Boolean = false,
    val projects: ImmutableList<FleetStartProject> = persistentListOf(),
    /** The direct task's project in effect: the chosen, else the first listed. */
    val directProjectId: String? = null,
    val local: FleetStartLocal = FleetStartLocal(),
) {
    /** A task that exists: sending the form again would make a second one. */
    val taskMade: Boolean
        get() = local.outcome == FleetDirectOutcome.AssignRefused || local.outcome == FleetDirectOutcome.DispatchUnconfirmed
}

/** Pure rules and mapping for Start a run (tested without Compose). */
public object FleetStartPresent {
    /** The planner every Gas Town city configures. */
    public const val PLANNER_ID: String = "gastown.mayor"

    /** The worker pool every rig configures; a task slung at it is picked up by the next free polecat. */
    public const val WORKER_POOL: String = "gastown.polecat"

    /** First line of every Start-a-run message. */
    public const val MARKER: String = "[OpenCode Mobile · Start a run]"

    /** Dart `teamWorkerPoolId`: the sling target of the rig [projectId]. */
    public fun workerPool(projectId: String): String = "$projectId/$WORKER_POOL"

    /** Dart `teamPlannerAgent`: by id, then by name; null when the host lists none. */
    public fun planner(agents: List<FleetAgent>): FleetAgent? =
        agents.firstOrNull { it.id == PLANNER_ID }
            ?: agents.firstOrNull { it.name == PLANNER_ID || it.name.endsWith(".mayor") || it.id.endsWith(".mayor") }

    /** Dart `teamPlannerIsOff`: suspended or stopped on the host. */
    public fun plannerIsOff(planner: FleetAgent): Boolean {
        val raw = planner.rawState?.lowercase()?.trim()
        if (raw == "suspended" || raw == "stopped") return true
        return planner.suspended || planner.state == AgentState.Stopped || planner.state == AgentState.Crashed
    }

    /** Dart `composeTeamPlanningMessage`. */
    public fun planningMessage(objective: String, supervision: FleetSupervision, projectName: String?): String =
        buildList {
                add(MARKER)
                add("Objective: ${objective.trim()}")
                if (!projectName.isNullOrBlank()) add("Project: ${projectName.trim()}")
                add("Supervision: ${supervision.line}")
                add("")
                add("Please plan this objective into beads and start a run for it. Keep the objective text in the run title so the phone can find it.")
            }
            .joinToString("\n")

    public fun present(fleet: FleetState, local: FleetStartLocal = FleetStartLocal()): FleetStartUi {
        if (!fleet.hasData) return FleetStartUi(local = local)
        val snapshot = fleet.snapshot
        val caps = fleet.capabilities
        val planner = planner(snapshot.agents)
        val off = planner == null || plannerIsOff(planner)
        val projects = snapshot.projects.map { FleetStartProject(it.id, it.name) }.toImmutableList()
        // The planner is off, but this host both creates and assigns work and lists a project: a task goes straight to a worker.
        val direct = off && caps.controlCreateWork && caps.controlAssign && projects.isNotEmpty()
        val directProject = projects.firstOrNull { it.id == local.directProjectId }?.id ?: projects.firstOrNull()?.id
        val base = FleetStartUi(projects = projects, directProjectId = directProject, local = local)
        return when {
            direct -> base.copy(mode = FleetStartMode.Direct)
            off -> {
                val why =
                    if (caps.controlCreateWork && caps.controlAssign && projects.isEmpty()) FleetBlockedWhy.NoProject
                    else if (planner == null) FleetBlockedWhy.NoPlanner
                    else FleetBlockedWhy.PlannerOff
                base.copy(
                    mode = FleetStartMode.Blocked,
                    blocked = why,
                    wakeable = why == FleetBlockedWhy.PlannerOff && caps.controlAgent,
                    plannerId = planner?.id,
                )
            }
            else ->
                base.copy(
                    mode = FleetStartMode.Planner,
                    plannerId = planner?.id,
                    plannerAsleep = planner?.sessionId.isNullOrEmpty(),
                )
        }
    }

    /** The planner form can send: an objective is written. */
    public fun objectiveMissing(local: FleetStartLocal): Boolean = local.objective.isBlank()

    public fun taskMissing(local: FleetStartLocal): Boolean = local.task.isBlank()
}
