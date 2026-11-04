package io.github.eslamasabry.oc.feature.settings

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
import io.github.eslamasabry.oc.feature.settings.presentation.PermissionSettingsViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsViewModel
import io.github.eslamasabry.oc.feature.settings.ui.PermissionSettingsScreen
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
public class SettingsEntries(private val arrivals: ShowMeTargets) : EntryInstaller {
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
            SettingsScreen(state, vm.actions, onExit = vm::exit)
        }
        entry<SettingsRoute.WhatsNew> {
            val vm = metroViewModel<SettingsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            val arrival by arrivals.active.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose { arrivals.dismiss(ShowMeTarget.AppearanceText) } }
            LaunchedEffect(state.page) {
                if (state.page != SettingsPage.Appearance && arrival == ShowMeTarget.AppearanceText)
                    arrivals.dismiss(ShowMeTarget.AppearanceText)
            }
            LaunchedEffect(vm) { vm.actions.onOpen(SettingsPage.WhatsNew) }
            val actions =
                object :
                    io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions by vm
                        .actions {
                    override fun onNewsShow() {
                        if (state.newsStep < 2) vm.exit()
                        vm.actions.onNewsShow()
                    }

                    override fun onBack(): Boolean {
                        if (state.stack.size <= 2) {
                            vm.exit()
                            return true
                        }
                        return vm.actions.onBack()
                    }
                }
            KitBackGuard(enabled = true, onBack = { actions.onBack() })
            if (state.page != SettingsPage.Hub)
                SettingsScreen(
                    state,
                    actions,
                    onExit = vm::exit,
                    showAppearanceArrival = arrival == ShowMeTarget.AppearanceText,
                    dismissAppearanceArrival = { arrivals.dismiss(ShowMeTarget.AppearanceText) },
                )
        }
        entry<SettingsRoute.Home> {
            val vm = metroViewModel<SettingsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            val arrival by arrivals.active.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose { arrivals.dismiss(ShowMeTarget.AppearanceText) } }
            LaunchedEffect(state.page) {
                if (state.page != SettingsPage.Appearance && arrival == ShowMeTarget.AppearanceText)
                    arrivals.dismiss(ShowMeTarget.AppearanceText)
            }
            KitBackGuard(
                enabled = state.page != SettingsPage.Hub || state.hub.query.isNotEmpty(),
                onBack = vm::back,
            )
            SettingsScreen(
                state,
                vm.actions,
                onExit = vm::exit,
                showAppearanceArrival = arrival == ShowMeTarget.AppearanceText,
                dismissAppearanceArrival = { arrivals.dismiss(ShowMeTarget.AppearanceText) },
            )
        }
    }
}
