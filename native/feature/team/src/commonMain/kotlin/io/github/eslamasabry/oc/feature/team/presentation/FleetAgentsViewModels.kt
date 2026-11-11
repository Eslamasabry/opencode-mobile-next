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
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgentAction
import io.github.eslamasabry.oc.engine.team.fleet.FleetControl
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.feature.team.fleet.FleetMergeController
import io.github.eslamasabry.oc.feature.team.fleet.FleetMergeUi
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a page keeps reading after it stops being shown (a rotation does not restart the stream). */
private const val STOP_AFTER_MS = 5_000L

/** How often the ages on a page move (they say minutes). */
private const val TICK_MS = 15_000L

private fun agedTicks(nowMs: () -> Long): Flow<Long> =
    flow {
        while (true) {
            emit(nowMs())
            kotlinx.coroutines.delay(TICK_MS)
        }
    }

// ---- the Agents page

public data class FleetAgentsState(val ui: FleetAgentsUi = FleetAgentsUi(), val nowMs: Long = 0)

public interface FleetAgentsActions {
    public fun back() {}

    public fun open(agentId: String) {}

    /** Read the team again now, or look for it again after it was not found. */
    public fun refresh() {}

    /** Team settings (where it runs, Turn off), from a team that cannot be found. */
    public fun openSettings() {}
}

/** The agents of a computer's team, most urgent first (Dart `TeamAgentsScreen` over the computer's own agents). */
@AssistedInject
public class FleetAgentsViewModel(
    @Assisted private val route: TeamRoute.FleetAgents,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), FleetAgentsActions {
    private val profile = route.profileId.raw
    private val fleet = engine.fleetState(profile).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), FleetState())

    public val state: StateFlow<FleetAgentsState> =
        combine(engine.state(profile), fleet, agedTicks { clock.now().toEpochMilliseconds() }) { team, fleet, now ->
                FleetAgentsState(
                    if (team.phase == TeamPhase.Computer) FleetAgentsPresent.list(team.config, fleet, now) else FleetAgentsUi(),
                    now,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), FleetAgentsState())

    override fun back(): Unit = nav.back()

    override fun open(agentId: String) {
        nav.open(TeamRoute.FleetAgent(route.profileId, agentId))
    }

    override fun refresh() {
        viewModelScope.launch { engine.fleet?.refresh(profile) }
    }

    override fun openSettings() {
        nav.open(TeamRoute.Settings(route.profileId))
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.FleetAgents): FleetAgentsViewModel
    }
}

// ---- one agent

/** What a destructive or heavy control asks before it goes (Dart `showKitConfirm`). */
public enum class FleetAgentAsk { Stop, ForceStop, Restart }

public data class FleetAgentState(
    val ui: FleetAgentUi = FleetAgentUi(),
    val nowMs: Long = 0,
    val asking: FleetAgentAsk? = null,
    /** Pause was accepted a moment ago: "Paused fox · Undo". */
    val undoPause: Boolean = false,
)

public interface FleetAgentActions {
    public fun back() {}

    public fun refresh() {}

    public fun openSettings() {}

    public fun pause() {}

    public fun nudge() {}

    /** Start it again: resume a stopped agent, restart one that crashed. */
    public fun start(action: FleetAgentAction) {}

    public fun ask(what: FleetAgentAsk) {}

    public fun confirm() {}

    public fun cancelAsk() {}

    /** Send the last control again; only offered when the host said nothing was stored. */
    public fun retry() {}

    public fun undoPause() {}

    public fun dismissUndo() {}

    /** The question waiting on this agent: its Gate page. */
    public fun openGate(gateId: String) {}
}

/** One agent of a computer's team (Dart `AgentScreen`): where it stands, what went wrong, Pause / Nudge / Restart / Stop / Force stop, live output. */
@AssistedInject
public class FleetAgentViewModel(
    @Assisted private val route: TeamRoute.FleetAgent,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
    private val redactor: Redactor,
) : ViewModel(), FleetAgentActions {
    private val profile = route.profileId.raw
    private val agentId = route.agentId
    private val fleet = engine.fleetState(profile).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), FleetState())

    private data class Local(
        val asking: FleetAgentAsk? = null,
        val busy: Boolean = false,
        val sent: FleetAgentSent? = null,
        val undoPause: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    /** The agent's session once the team names it; the output is followed for as long as the page is shown. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val feed: Flow<FleetOutputFeed> =
        fleet
            .map { f ->
                if (!f.hasData || !f.capabilities.agentOutput) null
                else f.snapshot.agents.firstOrNull { it.id == agentId || it.sessionId == agentId }?.sessionId
            }
            .distinctUntilChanged()
            .flatMapLatest { session ->
                val output = engine.fleet
                if (session == null || output == null) {
                    flowOf(FleetOutputFeed())
                } else {
                    flow {
                        var last = ""
                        emit(FleetOutputFeed())
                        output.agentOutput(profile, session).collect {
                            last = it
                            emit(FleetOutputFeed(it))
                        }
                        // The host no longer serves it: what was read stays, and the page says the session ended.
                        emit(FleetOutputFeed(last, ended = true))
                    }
                }
            }

    public val state: StateFlow<FleetAgentState> =
        combine(engine.state(profile), fleet, feed, local, agedTicks { clock.now().toEpochMilliseconds() }) { team, fleet, feed, mine, now ->
                FleetAgentState(
                    ui =
                        if (team.phase == TeamPhase.Computer) {
                            FleetAgentsPresent.agent(team.config, fleet, agentId, now, redactor::details, mine.sent, mine.busy, feed)
                        } else {
                            FleetAgentUi(FleetAgentKind.Missing, id = agentId)
                        },
                    nowMs = now,
                    asking = mine.asking,
                    undoPause = mine.undoPause,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), FleetAgentState())

    override fun back(): Unit = nav.back()

    override fun refresh() {
        viewModelScope.launch { engine.fleet?.refresh(profile) }
    }

    override fun openSettings() {
        nav.open(TeamRoute.Settings(route.profileId))
    }

    private fun send(action: FleetAgentAction) {
        val fleet = engine.fleet ?: return
        if (local.value.busy) return
        local.update { it.copy(busy = true, asking = null, undoPause = false) }
        viewModelScope.launch {
            val receipt = fleet.control(profile, FleetControl.Agent(agentId, action))
            local.update {
                it.copy(
                    busy = false,
                    sent = FleetAgentSent(action, receipt.status, receipt.message, receipt.retryable),
                    // A refused or unconfirmed request has nothing to undo (Dart `_pause`).
                    undoPause = action == FleetAgentAction.Pause && receipt.status == ReceiptStatus.Accepted,
                )
            }
        }
    }

    override fun pause() = send(FleetAgentAction.Pause)

    override fun nudge() = send(FleetAgentAction.Nudge)

    override fun start(action: FleetAgentAction) = send(action)

    override fun ask(what: FleetAgentAsk) = local.update { if (it.busy) it else it.copy(asking = what) }

    override fun cancelAsk() = local.update { it.copy(asking = null) }

    override fun confirm() {
        when (local.value.asking ?: return) {
            FleetAgentAsk.Stop -> send(FleetAgentAction.Stop)
            FleetAgentAsk.ForceStop -> send(FleetAgentAction.Kill)
            FleetAgentAsk.Restart -> send(FleetAgentAction.Restart)
        }
    }

    override fun retry() {
        send(local.value.sent?.takeIf { it.retryable }?.action ?: return)
    }

    override fun undoPause() = send(FleetAgentAction.Resume)

    override fun dismissUndo() = local.update { it.copy(undoPause = false) }

    override fun openGate(gateId: String) {
        nav.open(TeamRoute.Gate(route.profileId, gateId))
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.FleetAgent): FleetAgentViewModel
    }
}

// ---- one task

public data class FleetRunState(
    val ui: FleetRunUi = FleetRunUi(),
    val nowMs: Long = 0,
    val confirmingStop: Boolean = false,
    /** The step whose sheet is open, if any. */
    val work: FleetWorkUi? = null,
    /** The merge section at the end of the page (Dart `TeamMergeSection`): hidden until the task's work is done. */
    val merge: FleetMergeUi = FleetMergeUi(),
)

public interface FleetRunActions {
    public fun back() {}

    public fun refresh() {}

    public fun openSettings() {}

    /** A step row or graph node: its sheet. A dependency inside the sheet replaces it. */
    public fun openStep(workId: String) {}

    public fun closeStep() {}

    /** A work item named in the merge section's Review changes. */
    public fun openMergeWork(workId: String) {}

    /** A question waiting on this task: its Gate page. */
    public fun openGate(gateId: String) {}

    /** The agent on a step: its page. */
    public fun openAgent(agentId: String) {}

    public fun askStop() {}

    public fun confirmStop() {}

    public fun cancelStop() {}

    public fun retryStop() {}
}

/** One task of a computer's team (Dart Task details): its state, its steps as a graph, Stop task. */
@AssistedInject
public class FleetRunViewModel(
    @Assisted private val route: TeamRoute.FleetRun,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
    private val redactor: Redactor,
) : ViewModel(), FleetRunActions {
    private val profile = route.profileId.raw
    private val fleet = engine.fleetState(profile).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), FleetState())

    private data class Local(val confirming: Boolean = false, val busy: Boolean = false, val sent: FleetRunSent? = null, val step: String? = null)

    private val local = MutableStateFlow(Local())

    /** Approve, then merge into main, once every step is done (team-eg's section, on this page's lifetime). */
    public val merge: FleetMergeController? =
        engine.fleet?.let { FleetMergeController(it, profile, route.runId, fleet, viewModelScope) { clock.now().toEpochMilliseconds() } }

    public val state: StateFlow<FleetRunState> =
        combine(
                engine.state(profile),
                fleet,
                local,
                agedTicks { clock.now().toEpochMilliseconds() },
                merge?.state ?: flowOf(FleetMergeUi()),
            ) { team, fleet, mine, now, merging ->
                val computer = team.phase == TeamPhase.Computer
                FleetRunState(
                    ui =
                        if (computer) FleetRunPresent.run(team.config, fleet, route.runId, now, redactor::details, mine.sent, mine.busy)
                        else FleetRunUi(FleetRunKind.Missing, id = route.runId),
                    nowMs = now,
                    confirmingStop = mine.confirming,
                    work = mine.step?.takeIf { computer }?.let { FleetRunPresent.work(fleet, it, redactor::details) },
                    merge = if (computer) merging else FleetMergeUi(),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), FleetRunState())

    override fun back(): Unit = nav.back()

    override fun refresh() {
        viewModelScope.launch { engine.fleet?.refresh(profile) }
        merge?.reload()
    }

    override fun openSettings() {
        nav.open(TeamRoute.Settings(route.profileId))
    }

    override fun openStep(workId: String) = local.update { it.copy(step = workId) }

    /** A work item from the merge section's Review changes: that sheet closes and the step's own sheet opens. */
    override fun openMergeWork(workId: String) {
        merge?.closeChanges()
        openStep(workId)
    }

    override fun closeStep() = local.update { it.copy(step = null) }

    override fun openGate(gateId: String) {
        nav.open(TeamRoute.Gate(route.profileId, gateId))
    }

    override fun openAgent(agentId: String) {
        local.update { it.copy(step = null) }
        nav.open(TeamRoute.FleetAgent(route.profileId, agentId))
    }

    override fun askStop() = local.update { if (it.busy) it else it.copy(confirming = true) }

    override fun cancelStop() = local.update { it.copy(confirming = false) }

    override fun confirmStop() {
        if (!local.value.confirming) return
        stop()
    }

    override fun retryStop() {
        if (local.value.sent?.retryable == true) stop()
    }

    private fun stop() {
        val fleet = engine.fleet ?: return
        if (local.value.busy) return
        local.update { it.copy(busy = true, confirming = false) }
        viewModelScope.launch {
            val receipt = fleet.control(profile, FleetControl.CancelRun(route.runId))
            local.update { it.copy(busy = false, sent = FleetRunSent(receipt.status, receipt.message, receipt.retryable)) }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.FleetRun): FleetRunViewModel
    }
}
