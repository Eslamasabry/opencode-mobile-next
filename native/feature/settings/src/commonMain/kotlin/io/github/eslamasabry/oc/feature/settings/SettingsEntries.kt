package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringSection
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsViewModel
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.SettingsRoute

/**
 * The Settings route; the ONLY file in the module that imports androidx.navigation3.runtime
 * (Konsist K1). The hub's pages are one route: system Back (predictive) walks them first, then the
 * shell pops Settings.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class SettingsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<SettingsRoute.Home> {
            val vm = metroViewModel<SettingsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            val monitoring = metroViewModel<MonitoringViewModel>()
            val monitoringState by monitoring.state.collectAsStateWithLifecycle()
            KitBackGuard(
                enabled = state.page != SettingsPage.Hub || state.hub.query.isNotEmpty(),
                onBack = vm::back,
            )
            SettingsScreen(
                state,
                vm.actions,
                onExit = vm::exit,
                monitoring = { MonitoringSection(monitoringState, monitoring) },
            )
        }
        entry<SettingsRoute.Target> { target ->
            val vm = metroViewModel<SettingsViewModel>()
            LaunchedEffect(vm, target.rowTag) {
                vm.actions.onAnchor(target.rowTag)
            }
            val state by vm.state.collectAsStateWithLifecycle()
            val monitoring = metroViewModel<MonitoringViewModel>()
            val monitoringState by monitoring.state.collectAsStateWithLifecycle()
            KitBackGuard(enabled = state.page != SettingsPage.Hub, onBack = vm::back)
            SettingsScreen(
                state,
                vm.actions,
                onExit = vm::exit,
                monitoring = { MonitoringSection(monitoringState, monitoring) },
            )
        }
        entry<SettingsRoute.ReportProblem> {
            val vm = metroViewModel<SettingsViewModel>()
            LaunchedEffect(vm) { vm.actions.onOpen(SettingsPage.ReportProblem) }
            val state by vm.state.collectAsStateWithLifecycle()
            val monitoring = metroViewModel<MonitoringViewModel>()
            val monitoringState by monitoring.state.collectAsStateWithLifecycle()
            KitBackGuard(
                enabled = state.page != SettingsPage.Hub || state.hub.query.isNotEmpty(),
                onBack = vm::back,
            )
            SettingsScreen(
                state,
                vm.actions,
                onExit = vm::exit,
                monitoring = { MonitoringSection(monitoringState, monitoring) },
            )
        }
    }
}
