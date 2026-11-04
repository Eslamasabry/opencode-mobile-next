package io.github.eslamasabry.oc.feature.phone.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.pace.Pacer
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/*
 * Thin Metro ViewModels (plan 03 §13.7): each hands viewModelScope to its plain state holder, whose rules are tested
 * on virtual time. The sources and PhoneNavigator are bound by the runtime wiring (S3-RTWIRE) and the entry installer
 * once PhoneRoute exists (report: requests R1, R2).
 */

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ThisPhoneViewModel(
    source: PhoneServerSource,
    settings: KeepAliveSettings,
    nav: PhoneNavigator,
    clock: Clock,
    pacer: Pacer,
    details: Set<PhoneStateDetails>,
    phoneStorage: PhoneStorageReadings = NoPhoneStorageReadings(),
    exits: PhoneExitNotices,
    processes: PhoneProcessSource,
    private val migration: PhoneTermuxMigration,
    /** The details readings (memory, history import, installed tools, log); not [details]. */
    phoneDetails: PhoneDetailsSource,
) : ViewModel(), ThisPhoneActions, ThisPhoneMigrationActions {
    private val model =
        ThisPhoneModel(
            source,
            settings,
            nav,
            viewModelScope,
            clock,
            pacer,
            details = details,
            phoneStorage = phoneStorage,
            exits = exits,
            processes = processes,
            phoneDetails = phoneDetails,
        )
    public val state: StateFlow<ThisPhoneUi> =
        combine(model.state, migration.migration) { ui, offers ->
                ui.copy(termuxMigration = offers)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                model.state.value.copy(termuxMigration = migration.migration.value),
            )

    override fun fixTermux(profileId: String) {
        viewModelScope.launch { migration.fix(profileId) }
    }

    override fun openTermuxMigration(profileId: String) {
        migration.open(profileId)
    }

    override fun act(act: PhoneAct): Unit = model.act(act)

    override fun dismissRecovery(): Unit = model.dismissRecovery()

    override fun refreshStorage(): Unit = model.refreshStorage()

    override fun stopServer(): Unit = model.stopServer()

    override fun askRestart(): Unit = model.askRestart()

    override fun askUpdate(): Unit = model.askUpdate()

    override fun confirm(): Unit = model.confirm()

    override fun dismiss(): Unit = model.dismiss()

    override fun measureStorage(): Unit = model.measureStorage()

    override fun openRunning(): Unit = model.openRunning()

    override fun openKeepRunning(): Unit = model.openKeepRunning()

    override fun openLinux(): Unit = model.openLinux()

    override fun openTerminal(): Unit = model.openTerminal()

    override fun connect(): Unit = model.connect()

    override fun refreshDetails(): Unit = model.refreshDetails()

    override fun showDetails(expanded: Boolean): Unit = model.showDetails(expanded)

    override fun showInstalled(expanded: Boolean): Unit = model.showInstalled(expanded)

    override fun back(): Unit = model.back()

    override fun shown(visible: Boolean) {
        model.shown(visible)
        if (visible) viewModelScope.launch { migration.refresh() }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class RunningViewModel(
    source: PhoneProcessSource,
    limit: ChildLimitSource,
    nav: PhoneNavigator,
    clock: Clock,
    pacer: Pacer,
) : ViewModel(), RunningActions {
    private val model = RunningModel(source, limit, nav, viewModelScope, clock, pacer)
    public val state: StateFlow<RunningUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun retry(): Unit = model.retry()

    override fun details(pid: Int): Unit = model.details(pid)

    override fun askStop(pid: Int): Unit = model.askStop(pid)

    override fun confirmStop(): Unit = model.confirmStop()

    override fun dismiss(): Unit = model.dismiss()

    override fun openChildLimitSettings(): Unit = model.openChildLimitSettings()

    override fun hideChildLimit(): Unit = model.hideChildLimit()

    override fun back(): Unit = model.back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class KeepRunningViewModel(
    settings: KeepAliveSettings,
    nav: PhoneNavigator,
    thermal: ThermalSettings,
) : ViewModel(), ThermalKeepRunningActions {
    private val model = KeepRunningModel(settings, nav, viewModelScope, thermal)
    public val state: StateFlow<KeepRunningUi> = model.state

    override fun resumed(): Unit = model.resumed()

    override fun open(step: KeepAliveStepKind): Unit = model.open(step)

    override fun setThermalEnabled(value: Boolean): Unit = model.setThermalEnabled(value)

    override fun back(): Unit = model.back()
}
