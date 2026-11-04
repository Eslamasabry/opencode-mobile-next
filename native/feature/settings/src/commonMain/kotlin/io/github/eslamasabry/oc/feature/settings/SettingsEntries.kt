package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTarget
import io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTargets
import io.github.eslamasabry.oc.feature.settings.automation.AutomationSection
import io.github.eslamasabry.oc.feature.settings.automation.AutomationViewModel
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringSection
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.PermissionSettingsViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsHost
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsViewModel
import io.github.eslamasabry.oc.feature.settings.ui.PermissionSettingsScreen
import io.github.eslamasabry.oc.feature.settings.ui.SettingsCapabilityFlows
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
public class SettingsEntries(
    private val host: SettingsHost,
    private val arrivals: ShowMeTargets,
) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<SettingsRoute.Permissions> { route ->
            val vm =
                assistedMetroViewModel<
                    PermissionSettingsViewModel,
                    PermissionSettingsViewModel.Factory,
                > {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            PermissionSettingsScreen(state, vm)
        }
        entry<SettingsRoute.Notifications> {
            val vm = metroViewModel<SettingsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(vm) { vm.actions.onOpen(SettingsPage.Notifications) }
            KitBackGuard(
                enabled = state.page != SettingsPage.Hub || state.hub.query.isNotEmpty(),
                onBack = vm::back,
            )
            SettingsWithSections(state, vm.actions, vm)
        }
        entry<SettingsRoute.WhatsNew> {
            val vm = metroViewModel<SettingsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            // A standalone launch: Back and Close leave the route (the presenter's own exit).
            LaunchedEffect(vm) { vm.showWhatsNew() }
            KitBackGuard(
                enabled = state.page != SettingsPage.Hub || state.hub.query.isNotEmpty(),
                onBack = vm::back,
            )
            // The hub the person never opened is not flashed before What's new is on top.
            if (state.page != SettingsPage.Hub) SettingsWithSections(state, vm.actions, vm)
        }
        entry<SettingsRoute.Home> {
            val vm = metroViewModel<SettingsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            KitBackGuard(
                enabled = state.page != SettingsPage.Hub || state.hub.query.isNotEmpty(),
                onBack = vm::back,
            )
            SettingsWithSections(state, vm.actions, vm)
        }
        entry<SettingsRoute.Target> { target ->
            val vm = metroViewModel<SettingsViewModel>()
            LaunchedEffect(vm, target.rowTag) { vm.actions.onAnchor(target.rowTag) }
            val state by vm.state.collectAsStateWithLifecycle()
            KitBackGuard(enabled = state.page != SettingsPage.Hub, onBack = vm::back)
            SettingsWithSections(state, vm.actions, vm)
        }
        entry<SettingsRoute.ReportProblem> {
            val vm = metroViewModel<SettingsViewModel>()
            LaunchedEffect(vm) { vm.actions.onOpen(SettingsPage.ReportProblem) }
            val state by vm.state.collectAsStateWithLifecycle()
            KitBackGuard(
                enabled = state.page != SettingsPage.Hub || state.hub.query.isNotEmpty(),
                onBack = vm::back,
            )
            SettingsWithSections(state, vm.actions, vm)
        }
    }

    /**
     * Every Settings entry draws the same screen: the capability flows around it, the Monitoring and
     * Automation sections, and What's new › Show me's arrival on Appearance › Text size.
     */
    @Composable
    private fun SettingsWithSections(state: SettingsUi, actions: SettingsActions, vm: SettingsViewModel) {
        val automation = metroViewModel<AutomationViewModel>()
        val automationState by automation.state.collectAsStateWithLifecycle()
        val monitoring = metroViewModel<MonitoringViewModel>()
        val monitoringState by monitoring.state.collectAsStateWithLifecycle()
        val arrival by arrivals.active.collectAsStateWithLifecycle()
        DisposableEffect(Unit) { onDispose { arrivals.dismiss(ShowMeTarget.AppearanceText) } }
        LaunchedEffect(state.page) {
            if (state.page != SettingsPage.Appearance && arrival == ShowMeTarget.AppearanceText)
                arrivals.dismiss(ShowMeTarget.AppearanceText)
        }
        SettingsCapabilityFlows(host) {
            SettingsScreen(
                state,
                actions,
                onExit = vm::exit,
                monitoring = {
                    MonitoringSection(monitoringState, monitoring)
                    AutomationSection(automationState, automation)
                },
                showAppearanceArrival = arrival == ShowMeTarget.AppearanceText,
                dismissAppearanceArrival = { arrivals.dismiss(ShowMeTarget.AppearanceText) },
            )
        }
    }
}
