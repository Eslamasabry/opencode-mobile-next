package io.github.eslamasabry.oc.feature.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.usage.UsageFix
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class PlansControls(
    val collectors: ImmutableList<PlanCollectorUi> = persistentListOf(),
    val alertsAvailable: Boolean = false,
    val alertsOn: Boolean = false,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val ask: PlanCollectorAsk? = null,
)

/**
 * `PlansRoute`: reads every source when opened and on Refresh; one refresh at a time (a second tap
 * joins it). Keeps collector consent and sign-in actions attached to their exact targets (AGENT-2).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class PlansViewModel(
    private val source: PlansSource,
    private val nav: AppNavigator,
    private val clock: Clock,
    private val collector: CollectorControls,
) : ViewModel(), PlansActions {
    private val refreshing = MutableStateFlow(false)
    private val collectors = MutableStateFlow<List<CollectorTarget>>(emptyList())
    private val confirmation = MutableStateFlow<CollectorTarget?>(null)
    private val collectorFailed = MutableStateFlow(false)

    private val edits = MutableStateFlow(PlansUi())
    private val controls = MutableStateFlow(PlansControls())

    public val state: StateFlow<PlansUi> =
        combine(source.snapshot, refreshing, collectors, confirmation, collectorFailed) {
                snapshot,
                busy,
                targets,
                pending,
                failed ->
                plansUi(snapshot, busy, clock.now().toEpochMilliseconds())
                    .copy(
                        collectors = targets.toImmutableList(),
                        confirmCollector = pending,
                        collectorFailed = failed,
                    )
            }
            // LINUX-2: the phone's own collector setup.
            .combine(collector.state) { ui, c -> ui.copy(collector = c) }
            // STATE: the alert threshold and reset-notice editor.
            .combine(edits) { ui, edit ->
                ui.copy(
                    defaultThreshold = source.defaultThreshold.toInt(),
                    resetNotice = source.resetNotice,
                    editing = edit.editing,
                    editKey = edit.editKey,
                    thresholdInput = edit.thresholdInput,
                    saving = edit.saving,
                    failed = edit.failed,
                )
            }
            // AGENT-2: provider-usage collectors, usage alerts and per-card fixes.
            .combine(controls) { ui, control ->
                ui.copy(
                    cards =
                        ui.cards
                            .map { card ->
                                card.copy(
                                    canFix =
                                        when (card.fix) {
                                            UsageFix.TryAgain -> true
                                            UsageFix.SignIn ->
                                                card.providerId == "claude" &&
                                                    card.fixConnection?.isPhone == true &&
                                                    source.phoneSignInOwner() != null
                                            UsageFix.SetUp ->
                                                control.collectors.any {
                                                    it.profile.raw == card.fixConnection?.id
                                                }
                                            else -> false
                                        }
                                )
                            }
                            .toImmutableList(),
                    collectorRows = control.collectors,
                    alertsAvailable = control.alertsAvailable,
                    alertsOn = control.alertsOn,
                    actionBusy = control.busy,
                    actionFailed = control.failed,
                    ask = control.ask,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                plansUi(source.snapshot.value, false, clock.now().toEpochMilliseconds()),
            )

    init {
        refresh()
    }

    override fun editThreshold(key: String?) {
        val threshold =
            key?.let { wanted ->
                source.snapshot.value.subscriptions.firstOrNull { it.key == wanted }?.alertThreshold
            } ?: source.defaultThreshold
        edits.value =
            PlansUi(editing = true, editKey = key, thresholdInput = threshold.toInt().toString())
    }

    override fun thresholdChanged(value: String) {
        if (!edits.value.saving)
            edits.value = edits.value.copy(thresholdInput = value, failed = false)
    }

    override fun dismissEditor() {
        if (!edits.value.saving) edits.value = PlansUi()
    }

    override fun saveThreshold() {
        val value = edits.value.thresholdInput.toDoubleOrNull()
        if (value == null || !value.isFinite() || value <= 0 || value > 100) {
            edits.value = edits.value.copy(failed = true)
            return
        }
        save(value)
    }

    override fun useDefaultThreshold() {
        if (edits.value.editKey != null) save(null)
    }

    private fun save(value: Double?) {
        if (edits.value.saving) return
        val key = edits.value.editKey
        edits.value = edits.value.copy(saving = true)
        viewModelScope.launch {
            val saved = source.setThreshold(key, value)
            edits.value = if (saved) PlansUi() else edits.value.copy(saving = false, failed = true)
        }
    }

    override fun toggleResetNotice() {
        if (edits.value.saving) return
        edits.value = edits.value.copy(saving = true)
        viewModelScope.launch {
            val saved = source.setResetNotice(null, !source.resetNotice)
            edits.value = edits.value.copy(saving = false, failed = !saved)
        }
    }

    override fun back(): Unit = nav.back()

    override fun chooseCollector(target: CollectorTarget) {
        if (refreshing.value) return
        if (target.enabled) changeCollector(target, false) else confirmation.value = target
    }

    override fun cancelCollector() {
        confirmation.value = null
        controls.value = controls.value.copy(ask = null)
    }

    /** AGENT-2's pending ask (a usage collector) first, else the pending deployment target. */
    override fun confirmCollector() {
        if (controls.value.ask != null) {
            confirmUsageCollector()
            return
        }
        val target = confirmation.value ?: return
        confirmation.value = null
        changeCollector(target, true)
    }

    private fun changeCollector(target: CollectorTarget, enabled: Boolean) {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                collectorFailed.value = !source.setCollector(target, enabled)
                collectors.value = source.collectors()
                source.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }

    override fun setUpCollector() {
        viewModelScope.launch {
            collector.setUp()
            refresh()
        }
    }

    override fun turnOffCollector() {
        viewModelScope.launch {
            collector.turnOff()
            refresh()
        }
    }

    private fun syncControls() {
        controls.value =
            controls.value.copy(
                collectors = source.collectorRows().toImmutableList(),
                alertsAvailable = source.canAlert(),
                alertsOn = source.alertsOn(),
            )
    }

    override fun refresh() {
        if (refreshing.value || controls.value.busy) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                source.refresh()
                collectors.value = source.collectors()
                syncControls()
            } finally {
                refreshing.value = false
            }
        }
    }

    override fun setAlertsOn(enabled: Boolean) = perform { source.setAlertsOn(enabled) }

    override fun askCollector(profile: ProfileId) {
        if (controls.value.busy || refreshing.value) return
        val option = source.collectorRows().firstOrNull { it.profile == profile } ?: return
        controls.value =
            controls.value.copy(ask = PlanCollectorAsk(option, !option.enabled), failed = false)
    }

    private fun confirmUsageCollector() {
        val ask = controls.value.ask ?: return
        if (source.collectorRows().none { it == ask.collector }) {
            controls.value = controls.value.copy(ask = null, failed = true)
            return
        }
        controls.value = controls.value.copy(ask = null)
        perform {
            when {
                // A remote collector is withdrawn by revoking consent; only In-app Ubuntu runs
                // the local turn-off script (B-AGENT-314).
                !ask.enable && ask.collector.inApp -> source.turnOffCollector(ask.collector.profile)
                !ask.enable -> source.consentRemoteCollector(ask.collector.profile, false)
                ask.collector.inApp -> source.setUpCollector(ask.collector.profile)
                else -> source.consentRemoteCollector(ask.collector.profile, true)
            }
        }
    }

    private fun perform(operation: suspend () -> Outcome<Unit>) {
        if (controls.value.busy || refreshing.value) return
        controls.value = controls.value.copy(busy = true, failed = false)
        viewModelScope.launch {
            try {
                val result = operation()
                if (result is Outcome.Ok) source.refresh()
                syncControls()
                controls.value = controls.value.copy(failed = result is Outcome.Failed)
            } finally {
                controls.value = controls.value.copy(busy = false)
            }
        }
    }

    override fun fix(key: String) {
        val card = source.snapshot.value.subscriptions.firstOrNull { it.key == key } ?: return
        when (card.fix) {
            UsageFix.TryAgain -> refresh()
            UsageFix.SignIn -> {
                if (card.providerId != "claude" || card.fixConnection?.id != "phone") return
                val owner = source.phoneSignInOwner() ?: return
                nav.open(AgentsRoute.SignInTerminal(owner, "claude"))
            }
            UsageFix.SetUp -> card.fixConnection?.id?.let { askCollector(ProfileId(it)) }
            else -> Unit
        }
    }
}
