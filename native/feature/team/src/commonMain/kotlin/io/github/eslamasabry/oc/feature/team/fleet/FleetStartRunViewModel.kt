package io.github.eslamasabry.oc.feature.team.fleet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgentAction
import io.github.eslamasabry.oc.engine.team.fleet.FleetControl
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.feature.team.presentation.fleetState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Start a run page does (Dart `StartRunSheet` + `TeamStartBlocked`). */
public interface FleetStartRunActions {
    public fun back()

    public fun setObjective(text: String)

    public fun setTask(text: String)

    public fun setDetails(text: String)

    /** The planner form's project; null lets the planner choose. */
    public fun chooseProject(id: String?)

    public fun chooseDirectProject(id: String)

    public fun chooseSupervision(level: FleetSupervision)

    /** "Send to the Mayor": the objective and the supervision line go to the planner as one message. */
    public fun sendToPlanner()

    /** "Send to an agent": one task made in the project and given to its worker pool. */
    public fun sendDirect()

    /** Wake the switched-off planner, where the host takes agent controls. */
    public fun wake()

    /** Try again: read the team again; the form opens by itself once it can take a task. */
    public fun check()
}

/**
 * Start a run on a computer's team (Dart `showStartRunSheet`). Sending is one message to the planner (the supervisor
 * has no objective endpoint, so the objective and the supervision line are the message), or, where the planner is off
 * but the host creates and assigns work, one task made and given to a project's worker. Every write goes through
 * [io.github.eslamasabry.oc.engine.team.fleet.FleetEngine.control]; one that may have landed is never sent again by
 * itself, and what the person typed stays.
 */
@AssistedInject
public class FleetStartRunViewModel(
    @Assisted private val route: TeamRoute.StartRun,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
) : ViewModel(), FleetStartRunActions {
    private val profile = route.profileId.raw
    private val local = MutableStateFlow(FleetStartLocal(projectId = route.projectId, directProjectId = route.projectId))

    public val state: StateFlow<FleetStartUi> =
        combine(engine.fleetState(profile), local) { fleet, mine -> FleetStartPresent.present(fleet, mine) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetStartUi())

    override fun back(): Unit = nav.back()

    override fun setObjective(text: String) = local.update { it.copy(objective = text, showEmpty = false) }

    override fun setTask(text: String) = local.update { it.copy(task = text, showTaskEmpty = false) }

    override fun setDetails(text: String) = local.update { it.copy(details = text) }

    override fun chooseProject(id: String?) = local.update { if (it.sending) it else it.copy(projectId = id) }

    override fun chooseDirectProject(id: String) = local.update { if (it.sending) it else it.copy(directProjectId = id) }

    override fun chooseSupervision(level: FleetSupervision) = local.update { if (it.sending) it else it.copy(supervision = level) }

    override fun sendToPlanner() {
        val ui = state.value
        val mine = ui.local
        val plannerId = ui.plannerId
        if (ui.mode != FleetStartMode.Planner || plannerId == null || mine.sending) return
        if (FleetStartPresent.objectiveMissing(mine)) {
            local.update { it.copy(showEmpty = true) }
            return
        }
        local.update { it.copy(sending = true, refused = null, unconfirmed = false) }
        viewModelScope.launch {
            val fleet = engine.fleet
            try {
                if (fleet == null) return@launch
                if (ui.plannerAsleep) {
                    // No live session to message: wake the planner first. The host answers by agent id once it is awake.
                    local.update { it.copy(waking = true) }
                    fleet.control(profile, FleetControl.Agent(plannerId, FleetAgentAction.Resume))
                    local.update { it.copy(waking = false) }
                }
                val project = ui.projects.firstOrNull { it.id == mine.projectId }?.name
                val receipt =
                    fleet.control(profile, FleetControl.Message(plannerId, FleetStartPresent.planningMessage(mine.objective, mine.supervision, project)))
                when (receipt.status) {
                    // Refused: stay, say why, keep the words for another try.
                    ReceiptStatus.Rejected -> local.update { it.copy(refused = receipt.message?.trim().orEmpty()) }
                    // It may have landed: never sent again by itself; the words stay.
                    ReceiptStatus.Pending -> local.update { it.copy(unconfirmed = true) }
                    ReceiptStatus.Accepted -> nav.back()
                }
            } finally {
                local.update { it.copy(sending = false, waking = false) }
            }
        }
    }

    override fun sendDirect() {
        val ui = state.value
        val mine = ui.local
        val projectId = ui.directProjectId
        if (ui.mode != FleetStartMode.Direct || projectId == null || mine.sending) return
        if (FleetStartPresent.taskMissing(mine)) {
            local.update { it.copy(showTaskEmpty = true) }
            return
        }
        local.update { it.copy(sending = true, stage = FleetDirectStage.Creating, outcome = FleetDirectOutcome.None, directWords = null) }
        viewModelScope.launch {
            val fleet = engine.fleet
            try {
                if (fleet == null) return@launch
                val created = fleet.control(profile, FleetControl.CreateWork(mine.task.trim(), mine.details.trim().ifEmpty { null }, projectId))
                when (created.status) {
                    ReceiptStatus.Rejected -> {
                        local.update { it.copy(outcome = FleetDirectOutcome.CreateRefused, directWords = created.message?.trim().orEmpty()) }
                        return@launch
                    }
                    ReceiptStatus.Pending -> {
                        local.update { it.copy(outcome = FleetDirectOutcome.CreateUnconfirmed) }
                        return@launch
                    }
                    ReceiptStatus.Accepted -> Unit
                }
                val id = created.createdId
                if (id == null) {
                    // Made, but the host named no id to hand over: the board has it.
                    local.update { it.copy(outcome = FleetDirectOutcome.DispatchUnconfirmed) }
                    return@launch
                }
                local.update { it.copy(stage = FleetDirectStage.Sending) }
                val assigned = fleet.control(profile, FleetControl.Assign(id, FleetStartPresent.workerPool(projectId)))
                when (assigned.status) {
                    ReceiptStatus.Accepted -> nav.back()
                    ReceiptStatus.Rejected ->
                        local.update { it.copy(outcome = FleetDirectOutcome.AssignRefused, directWords = assigned.message?.trim().orEmpty()) }
                    ReceiptStatus.Pending -> local.update { it.copy(outcome = FleetDirectOutcome.DispatchUnconfirmed) }
                }
            } finally {
                local.update { it.copy(sending = false, stage = null) }
            }
        }
    }

    override fun wake() {
        val plannerId = state.value.plannerId ?: return
        if (local.value.wake.busy) return
        local.update { it.copy(wake = FleetWakeLocal(busy = true)) }
        viewModelScope.launch {
            val fleet = engine.fleet
            val wake =
                if (fleet == null) {
                    FleetWakeLocal(refused = "")
                } else {
                    val receipt = fleet.control(profile, FleetControl.Agent(plannerId, FleetAgentAction.Resume))
                    if (receipt.status == ReceiptStatus.Rejected) FleetWakeLocal(refused = receipt.message?.trim().orEmpty())
                    else {
                        fleet.refresh(profile)
                        FleetWakeLocal(asked = true)
                    }
                }
            local.update { it.copy(wake = wake) }
        }
    }

    override fun check() {
        if (local.value.wake.busy) return
        local.update { it.copy(wake = it.wake.copy(busy = true, stillOff = false)) }
        viewModelScope.launch {
            engine.fleet?.refresh(profile)
            local.update { it.copy(wake = it.wake.copy(busy = false, stillOff = state.value.mode == FleetStartMode.Blocked)) }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.StartRun): FleetStartRunViewModel
    }
}
