package io.github.eslamasabry.oc.feature.team.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.TeamTimelineEvent
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// ---- servers (Dart TeamProjectServers, servers_card.dart)

/** A task on a server, and whether it can move (Dart's Move rows). */
public data class ServerTaskUi(val id: String, val title: String, val status: String, val movable: Boolean)

/** One server lane of the project (Dart `KitServerLane`). */
public data class ServerUi(
    val id: String,
    val name: String,
    val online: Boolean,
    val running: Int,
    /** The project's own lane limit applies on this server too (Dart `_lanesHere`). */
    val lanes: Int,
    val laneCap: Int,
    val tasks: ImmutableList<ServerTaskUi>,
)

/** Why the phone's own server reads "Not reachable" (Dart `TeamPhoneServerProblem`). */
public enum class PhoneServerProblem { NotReady, NoAnswer }

/** A move asking where and with what note; [restartElsewhere] after the branch could not follow. */
public data class MoveUi(val taskId: String, val serverId: String, val note: String = "", val restartElsewhere: Boolean = false)

public data class TeamServersUi(
    val phase: TeamPhase = TeamPhase.Loading,
    val projectName: String? = null,
    val found: Boolean = true,
    val servers: ImmutableList<ServerUi> = persistentListOf(),
    val stale: Boolean = false,
    val demo: Boolean = false,
    val blocked: Boolean = false,
    val phoneProblem: PhoneServerProblem? = null,
    val busy: Boolean = false,
    val refusal: RefusalUi? = null,
    /** A server's lane limit being edited. */
    val editingLanes: String? = null,
    val moving: MoveUi? = null,
)

public interface TeamServersActions {
    public fun back()

    public fun retry()

    public fun openTask(taskId: String)

    public fun editLanes(serverId: String?)

    public fun saveLanes(serverId: String, lanes: Int)

    /** A Move row's choice: asks for the hand-off note next. */
    public fun chooseMove(taskId: String, serverId: String)

    /** The note, or null when the person backed out. */
    public fun moveWithNote(note: String?)

    /** Restart elsewhere, or wait for the server (false). */
    public fun restartElsewhere(yes: Boolean)

    public fun dismissRefusal()
}

public object TeamServersPresent {
    public fun present(state: TeamState, projectId: String): TeamServersUi {
        val w = state.workspace
        val p = w?.project(projectId)
        if (w == null || p == null) return TeamServersUi(state.phase, found = w == null)
        val can = state.capabilities
        return TeamServersUi(
            phase = state.phase,
            projectName = p.name,
            servers =
                w.servers.map { s ->
                    ServerUi(
                        id = s.id,
                        name = s.name,
                        online = s.online,
                        running = p.tasks.count { it.serverId == s.id && it.status == "running" },
                        lanes = if (p.settings.mode == "single") 1 else minOf(s.laneCap, p.settings.maxLanes),
                        laneCap = s.laneCap,
                        tasks =
                            p.tasks.filter { it.serverId == s.id }
                                .map { ServerTaskUi(it.id, it.title, it.status, can.placement && !it.finished) }
                                .toImmutableList(),
                    )
                }.toImmutableList(),
            stale = state.stale,
            demo = p.simulated,
            blocked = !w.simulated && !can.lanes,
            phoneProblem =
                if (w.simulated || w.servers.none { it.phone && !it.online }) null
                else if (!can.lanes) PhoneServerProblem.NotReady
                else PhoneServerProblem.NoAnswer,
            busy = state.busy,
            refusal = state.refusal?.let { RefusalUi(it, state.refusedAction.orEmpty()) },
        )
    }
}

@AssistedInject
public class TeamServersViewModel(
    @Assisted private val route: TeamRoute.Servers,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), TeamServersActions {
    private val profile = route.profileId.raw
    private val editing = MutableStateFlow<String?>(null)
    private val moving = MutableStateFlow<MoveUi?>(null)

    public val state: StateFlow<TeamServersUi> =
        combine(engine.polled(profile) { clock.now().toEpochMilliseconds() }, editing, moving) { (team, _), lanes, move ->
                TeamServersPresent.present(team, route.projectId).copy(editingLanes = lanes, moving = move)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamServersUi())

    override fun back(): Unit = nav.back()

    override fun retry() {
        viewModelScope.launch { engine.refresh(profile) }
    }

    override fun openTask(taskId: String) {
        nav.open(TeamRoute.Task(route.profileId, route.projectId, taskId))
    }

    override fun editLanes(serverId: String?) {
        editing.value = serverId
    }

    /** Dart's Max lanes: the server's lane limit, at the workspace revision the person saw. */
    override fun saveLanes(serverId: String, lanes: Int) {
        editing.value = null
        val w = engine.state(profile).value.workspace ?: return
        val server = w.servers.firstOrNull { it.id == serverId } ?: return
        if (lanes <= 0) return
        viewModelScope.launch { engine.execute(profile, TeamCommand.updateServer(w.revision, server.copy(laneCap = lanes))) }
    }

    override fun chooseMove(taskId: String, serverId: String) {
        val current = engine.state(profile).value.workspace?.project(route.projectId)?.tasks?.firstOrNull { it.id == taskId }
        if (current == null || current.serverId == serverId) return
        moving.value = MoveUi(taskId, serverId)
    }

    /**
     * Dart's move: the hand-off note, then `moveTask`; a branch that cannot follow (`branchUnavailable`,
     * `sharedRemoteRequired`) asks whether to restart it there.
     */
    override fun moveWithNote(note: String?) {
        val move = moving.value ?: return
        if (note == null) {
            moving.value = null
            return
        }
        val project = engine.state(profile).value.workspace?.project(route.projectId) ?: return
        viewModelScope.launch {
            val result = engine.execute(profile, TeamCommand.move(project, move.taskId, move.serverId, note))
            val code = (result as? Outcome.Ok)?.value?.code
            if (code == "branchUnavailable" || code == "sharedRemoteRequired") {
                engine.clearRefusal(profile)
                moving.value = move.copy(note = note, restartElsewhere = true)
            } else {
                moving.value = null
            }
        }
    }

    override fun restartElsewhere(yes: Boolean) {
        val move = moving.value ?: return
        moving.value = null
        if (!yes) return
        val project = engine.state(profile).value.workspace?.project(route.projectId) ?: return
        viewModelScope.launch { engine.execute(profile, TeamCommand.move(project, move.taskId, move.serverId, move.note, confirmed = true)) }
    }

    override fun dismissRefusal() {
        engine.clearRefusal(profile)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Servers): TeamServersViewModel
    }
}

// ---- timeline (Dart TeamProjectTimeline, board_timeline.dart)

/** Dart's timeline filters: everything, decisions, merges, problems. */
public enum class TimelineFilter { All, Decisions, Merges, Problems }

/** One row: back-to-back rows of the same words, task and actor fold into one that says how many (Dart `_foldRepeats`). */
public data class TimelineRowUi(val text: String, val count: Int, val actor: String, val roleName: String?, val atMs: Long?, val taskId: String)

/** [day] is the local calendar day (ISO), null when the row has no time. */
public data class TimelineDayUi(val day: String?, val rows: ImmutableList<TimelineRowUi>)

public data class TeamTimelineUi(
    val phase: TeamPhase = TeamPhase.Loading,
    val projectName: String? = null,
    val found: Boolean = true,
    val demo: Boolean = false,
    val filter: TimelineFilter = TimelineFilter.All,
    val days: ImmutableList<TimelineDayUi> = persistentListOf(),
    val nowMs: Long = 0,
)

public interface TeamTimelineActions {
    public fun back()

    public fun filter(f: TimelineFilter)

    public fun openTask(taskId: String)
}

public object TeamTimelinePresent {
    private val PROBLEMS = setOf("failed", "problem", "stalled", "paused")

    private fun keep(e: TeamTimelineEvent, f: TimelineFilter): Boolean =
        when (f) {
            TimelineFilter.All -> true
            TimelineFilter.Decisions -> e.kind == "decision"
            TimelineFilter.Merges -> e.kind == "merge"
            TimelineFilter.Problems -> e.kind in PROBLEMS
        }

    /** Newest first, by local day, repeats folded. */
    public fun present(state: TeamState, projectId: String, filter: TimelineFilter, nowMs: Long, zone: TimeZone): TeamTimelineUi {
        val w = state.workspace
        val p = w?.project(projectId)
        if (w == null || p == null) return TeamTimelineUi(state.phase, found = w == null, filter = filter)
        val days = LinkedHashMap<String?, MutableList<TeamTimelineEvent>>()
        p.timeline.reversed().filter { keep(it, filter) }.forEach { e ->
            val day = TeamRules.epochMs(e.at)?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date.toString() }
            days.getOrPut(day) { mutableListOf() } += e
        }
        return TeamTimelineUi(
            phase = state.phase,
            projectName = p.name,
            demo = p.simulated,
            filter = filter,
            days =
                days.map { (day, events) ->
                    val rows = mutableListOf<TimelineRowUi>()
                    events.forEach { e ->
                        val last = rows.lastOrNull()
                        if (last != null && last.text == e.text && last.taskId == e.taskId && last.actor == e.actor) {
                            rows[rows.lastIndex] = last.copy(count = last.count + 1)
                        } else {
                            rows += TimelineRowUi(e.text, 1, e.actor, w.roles.firstOrNull { it.id == e.actor }?.name, TeamRules.epochMs(e.at), e.taskId)
                        }
                    }
                    TimelineDayUi(day, rows.toImmutableList())
                }.toImmutableList(),
            nowMs = nowMs,
        )
    }
}

@AssistedInject
public class TeamTimelineViewModel(
    @Assisted private val route: TeamRoute.Timeline,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), TeamTimelineActions {
    private val profile = route.profileId.raw
    private val filter = MutableStateFlow(TimelineFilter.All)

    public val state: StateFlow<TeamTimelineUi> =
        combine(engine.polled(profile) { clock.now().toEpochMilliseconds() }, filter) { (team, now), f ->
                TeamTimelinePresent.present(team, route.projectId, f, now, TimeZone.currentSystemDefault())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamTimelineUi())

    override fun back(): Unit = nav.back()

    override fun filter(f: TimelineFilter) {
        filter.value = f
    }

    override fun openTask(taskId: String) {
        nav.open(TeamRoute.Task(route.profileId, route.projectId, taskId))
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Timeline): TeamTimelineViewModel
    }
}
