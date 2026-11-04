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
 * Reads every source once and keeps collector consent and sign-in actions attached to their exact
 * targets.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class PlansViewModel(
    private val source: PlansSource,
    private val nav: AppNavigator,
    private val clock: Clock,
) : ViewModel(), PlansActions {
    private val refreshing = MutableStateFlow(false)
    private val controls = MutableStateFlow(PlansControls())

    public val state: StateFlow<PlansUi> =
        combine(source.snapshot, refreshing, controls) { snapshot, busy, control ->
                val ui = plansUi(snapshot, busy, clock.now().toEpochMilliseconds())
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
                    collectors = control.collectors,
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

    override fun back(): Unit = nav.back()

    private fun syncControls() {
        controls.value =
            controls.value.copy(
                collectors = source.collectors().toImmutableList(),
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
                syncControls()
            } finally {
                refreshing.value = false
            }
        }
    }

    override fun setAlertsOn(enabled: Boolean) = perform { source.setAlertsOn(enabled) }

    override fun askCollector(profile: ProfileId) {
        if (controls.value.busy || refreshing.value) return
        val option = source.collectors().firstOrNull { it.profile == profile } ?: return
        controls.value =
            controls.value.copy(ask = PlanCollectorAsk(option, !option.enabled), failed = false)
    }

    override fun cancelCollector() {
        controls.value = controls.value.copy(ask = null)
    }

    override fun confirmCollector() {
        val ask = controls.value.ask ?: return
        if (source.collectors().none { it == ask.collector }) {
            controls.value = controls.value.copy(ask = null, failed = true)
            return
        }
        controls.value = controls.value.copy(ask = null)
        perform {
            when {
                !ask.enable -> source.turnOffCollector(ask.collector.profile)
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
