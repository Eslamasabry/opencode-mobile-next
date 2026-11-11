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
import io.github.eslamasabry.oc.engine.team.fleet.FleetAnswer
import io.github.eslamasabry.oc.engine.team.fleet.FleetControl
import io.github.eslamasabry.oc.feature.team.presentation.FleetPlace
import io.github.eslamasabry.oc.feature.team.presentation.FleetPresent
import io.github.eslamasabry.oc.feature.team.presentation.fleetState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Gate sheet does (Dart `GateSheet` over `OrchestrationController`). */
public interface FleetGateActions {
    public fun back()

    /** A tap on an option sends it (Dart: one choice acts on tap). */
    public fun choose(index: Int)

    public fun setDraft(text: String)

    /** Sends the typed answer. */
    public fun sendText()

    /** Deny sends at once; Approve asks first when the prompt is destructive. */
    public fun approve()

    public fun deny()

    /** "Ask the team to fix it": [request] is the error put as a message to the worker. */
    public fun fix(request: String)

    /** "Send {work} to {agent} again". */
    public fun retry()

    public fun askStopRun()

    public fun confirm()

    public fun cancelConfirm()

    /** Try again after a not-confirmed or refused write: a new write under a new key, only ever after a tap. */
    public fun tryAgain()
}

/** How long "Answered" stays before the sheet closes itself (Dart `gateSheetAnsweredBeat`). */
internal const val ANSWERED_BEAT_MS: Long = 900

/**
 * The Gate sheet for one question of a computer's team (Dart `showGateSheet`). Every write goes through
 * [io.github.eslamasabry.oc.engine.team.fleet.FleetEngine.control] under the gate's own id; an answer reads the team
 * again first, as Flutter re-reads the pending list, so the session is the one the host lists now. The sheet shows the
 * receipt in place; a write that may have landed is never sent again by itself.
 */
@AssistedInject
public class FleetGateViewModel(
    @Assisted private val route: TeamRoute.Gate,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
) : ViewModel(), FleetGateActions {
    private val profile = route.profileId.raw
    private val local = MutableStateFlow(FleetGateLocal())

    /** The last write, for Try again. */
    private var last: FleetControl? = null

    public val state: StateFlow<FleetGateUi> =
        combine(engine.fleetState(profile), local, engine.state(profile)) { fleet, mine, team ->
                FleetGatePresent.present(fleet, route.gateId, mine, FleetPresent.place(team.config, fleet.host?.url) == FleetPlace.Phone)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FleetGateUi(gateId = route.gateId))

    override fun back(): Unit = nav.back()

    private fun send(control: FleetControl) {
        if (local.value.sending) return
        last = control
        local.update { it.copy(sending = true, receipt = null, confirm = null) }
        viewModelScope.launch {
            val fleet = engine.fleet
            if (fleet == null) {
                local.update { it.copy(sending = false) }
                return@launch
            }
            // Dart re-reads the pending list before an answer: the session comes from what the host lists now.
            if (control is FleetControl.Answer) fleet.refresh(profile)
            val receipt = fleet.control(profile, control)
            local.update { it.copy(sending = false, receipt = receipt, draft = if (receipt.accepted && control is FleetControl.Answer) "" else it.draft) }
            if (receipt.accepted) {
                delay(ANSWERED_BEAT_MS)
                nav.back()
            }
        }
    }

    private fun answer(answer: FleetAnswer) = send(FleetControl.Answer(route.gateId, answer))

    override fun choose(index: Int) {
        val option = state.value.choices.getOrNull(index) ?: return
        if (state.value.busy) return
        local.update { it.copy(selected = index) }
        answer(FleetAnswer.Choice(option))
    }

    override fun setDraft(text: String) = local.update { it.copy(draft = text) }

    override fun sendText() {
        val text = local.value.draft.trim()
        if (text.isNotEmpty()) answer(FleetAnswer.Text(text))
    }

    override fun approve() {
        if (state.value.busy) return
        if (state.value.destructive) local.update { it.copy(confirm = FleetGateConfirm.Approve) } else answer(FleetAnswer.Confirm(true))
    }

    override fun deny() = answer(FleetAnswer.Confirm(false))

    override fun fix(request: String) {
        val agent = state.value.fix ?: return
        send(FleetControl.Message(agent.id, request))
    }

    override fun retry() {
        val retry = state.value.retry ?: return
        send(FleetControl.Assign(retry.workId, retry.target))
    }

    override fun askStopRun() {
        if (state.value.cancelRunId != null && !state.value.busy) local.update { it.copy(confirm = FleetGateConfirm.StopRun) }
    }

    override fun confirm() {
        val ask = local.value.confirm ?: return
        local.update { it.copy(confirm = null) }
        when (ask) {
            FleetGateConfirm.Approve -> answer(FleetAnswer.Confirm(true))
            FleetGateConfirm.StopRun -> state.value.cancelRunId?.let { send(FleetControl.CancelRun(it)) }
        }
    }

    override fun cancelConfirm() = local.update { it.copy(confirm = null) }

    override fun tryAgain() {
        send(last ?: return)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Gate): FleetGateViewModel
    }
}
