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
import io.github.eslamasabry.oc.engine.team.fleet.FleetControl
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetProjectAction
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.feature.team.presentation.fleetState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the project page does (Dart `TeamRigScreen`). */
public interface FleetRigActions {
    public fun back()

    /** Pause asks first; Resume goes at once. */
    public fun askPause()

    public fun askDelete()

    public fun resume()

    public fun confirm()

    public fun cancelConfirm()

    /** Try again after a write that was not confirmed or was refused: the same act on the same project. */
    public fun retryWrite()

    public fun setJob(jobId: String, enabled: Boolean)

    public fun retryJobs()

    /** Try again after the team could not be found. */
    public fun retryTeam()
}

/**
 * One project (a Gas City rig) of a computer's team on its own page (Dart `TeamRigScreen`): where it stands, pause it,
 * resume it, take it out of the team (each names the project; pause and delete ask first), its scheduled jobs as switches,
 * and the host's rules for it. The connection is the page's while it is open.
 */
@AssistedInject
public class FleetRigViewModel(
    @Assisted private val route: TeamRoute.FleetRig,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
) : ViewModel(), FleetRigActions {
    private val profile = route.profileId.raw
    private val local = MutableStateFlow(FleetRigLocal())

    private val fleet: StateFlow<FleetState> = engine.fleetState(profile).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetState())

    public val state: StateFlow<FleetRigUi> =
        combine(fleet, local) { team, mine -> FleetRigPresent.page(team, route.projectId, mine) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetRigUi())

    init {
        // Dart `initState`: once the team answers, the project's jobs and the host's rules for it are read.
        viewModelScope.launch {
            val ready = fleet.first { it.phase == FleetPhase.Ready && it.hasData }
            if (ready.capabilities.scheduledJobs) readJobs()
            // The policy is the host front's: a bare supervisor has none (the engine answers null).
            if (ready.capabilities.mergeReadiness) {
                val policy = engine.fleet?.policy(profile, route.projectId)
                local.update { it.copy(policy = policy) }
            }
        }
    }

    private suspend fun readJobs() {
        local.update { it.copy(jobsRead = FleetJobsRead.Loading) }
        engine.fleet?.loadJobs(profile)
        // A refusal keeps the last list: no list at all after the read is the failure the page words.
        local.update { it.copy(jobsRead = if (fleet.value.jobs == null) FleetJobsRead.Failed else FleetJobsRead.Idle) }
    }

    override fun back(): Unit = nav.back()

    override fun askPause() = local.update { if (it.busy) it else it.copy(confirming = FleetRigConfirm.Pause) }

    override fun askDelete() = local.update { if (it.busy) it else it.copy(confirming = FleetRigConfirm.Delete) }

    override fun cancelConfirm() = local.update { it.copy(confirming = null) }

    override fun resume() = send(FleetProjectAction.Resume)

    override fun confirm() {
        val asked = local.value.confirming ?: return
        local.update { it.copy(confirming = null) }
        send(if (asked == FleetRigConfirm.Pause) FleetProjectAction.Suspend else FleetProjectAction.Remove)
    }

    override fun retryWrite() {
        val action = local.value.lastAction ?: return
        send(action)
    }

    private fun send(action: FleetProjectAction) {
        var admitted = false
        local.update {
            admitted = !it.busy
            if (admitted) it.copy(busy = true, write = FleetWrite.Sending, lastAction = action) else it
        }
        if (!admitted) return
        viewModelScope.launch {
            val receipt = engine.fleet?.control(profile, FleetControl.Project(route.projectId, action))
            val write = receipt?.let(FleetWrite::of) ?: FleetWrite(FleetWriteStatus.Refused)
            local.update { it.copy(busy = false, write = write) }
            // A removed project has no page left to show.
            if (action == FleetProjectAction.Remove && receipt?.status == ReceiptStatus.Accepted) nav.back()
        }
    }

    override fun setJob(jobId: String, enabled: Boolean) {
        if (local.value.jobWrites[jobId] == FleetWrite.Sending) return
        local.update { it.copy(jobWrites = it.jobWrites + (jobId to FleetWrite.Sending)) }
        viewModelScope.launch {
            val receipt = engine.fleet?.control(profile, FleetControl.Job(jobId, enabled))
            val write = receipt?.let(FleetWrite::of) ?: FleetWrite(FleetWriteStatus.Refused)
            local.update { it.copy(jobWrites = it.jobWrites + (jobId to write)) }
        }
    }

    override fun retryJobs() {
        viewModelScope.launch { readJobs() }
    }

    override fun retryTeam() {
        viewModelScope.launch { engine.fleet?.refresh(profile) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.FleetRig): FleetRigViewModel
    }
}
