package io.github.eslamasabry.oc.feature.background.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Background page can do. Each name says its one target. */
public interface BackgroundActions {
    public fun onBack()

    /** The "Stay connected in the background" switch. */
    public fun onSwitch(on: Boolean)

    /** Restart after Android stopped it (the status row and the time-limit notice). */
    public fun onRestart()

    public fun onResume()

    public fun onDismissPause()

    public fun onAllowBattery()

    /** Keep running settings (Settings › This phone › Keep running). */
    public fun onOpenKeepRunning()
}

/**
 * Settings › Notifications › Background. Commands run in the app scope: leaving the page never cancels a start the
 * person asked for. A switch that did not end where the person put it says why in plain words (Flutter
 * `_toggleBackground`), never Android's own text.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class BackgroundViewModel(
    private val live: BackgroundLive,
    private val appScope: AppCoroutineScope,
    private val nav: AppNavigator,
) : ViewModel(), BackgroundActions {
    private val failure = MutableStateFlow<SwitchFailure?>(null)

    init {
        live.refresh()
    }

    public val state: StateFlow<BackgroundUi> =
        combine(live.facts, failure) { f, e -> backgroundUi(f, e) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), backgroundUi(live.facts.value, null))

    override fun onBack(): Unit = nav.back()

    override fun onSwitch(on: Boolean) {
        if (live.facts.value.busy) return
        failure.value = null
        appScope.launch { failure.value = switchFailure(on, live.setEnabled(on)) }
    }

    override fun onRestart(): Unit = onSwitch(true)

    override fun onResume() {
        appScope.launch { live.resume() }
    }

    override fun onDismissPause() {
        appScope.launch { live.dismissPause() }
    }

    override fun onAllowBattery() {
        appScope.launch { live.allowUnrestrictedBattery() }
    }

    override fun onOpenKeepRunning() {
        nav.open(PhoneRoute.KeepRunning)
    }
}

/** The plain-words reason for a switch that did not land (null = it landed). */
public fun switchFailure(wanted: Boolean, result: Outcome<Boolean>): SwitchFailure? =
    when (result) {
        is Outcome.Ok -> if (result.value == wanted) null else if (wanted) SwitchFailure.NotEnabled else SwitchFailure.NotDisabled
        is Outcome.Failed ->
            when ((result.failure as? Failure.Refused)?.reason) {
                RefusalReason.NotificationsBlocked -> SwitchFailure.NotificationsDenied
                null -> if (wanted) SwitchFailure.NotEnabled else SwitchFailure.NotDisabled
                else -> SwitchFailure.Unavailable
            }
    }
