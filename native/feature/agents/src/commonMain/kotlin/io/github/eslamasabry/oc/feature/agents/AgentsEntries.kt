package io.github.eslamasabry.oc.feature.agents

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputerKind
import io.github.eslamasabry.oc.feature.agents.hub.AgentsViewModel
import io.github.eslamasabry.oc.feature.agents.presentation.PhoneSignInRouteViewModel
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.feature.agents.ui.PhoneSignInScreen
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.CapabilityAddComputerRoute
import io.github.eslamasabry.oc.ui.routes.CapabilityAgentAccountRoute
import io.github.eslamasabry.oc.ui.routes.CapabilityComputerKind
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

/** The Agents hub and the agent-owned sign-in terminal. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class AgentsEntries(private val arrivals: ShowMeTargets) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<CapabilityAddComputerRoute> { route ->
            val vm = metroViewModel<AgentsViewModel>()
            LifecycleResumeEffect(vm) {
                vm.refreshPhone()
                onPauseOrDispose {}
            }
            val state by vm.state.collectAsStateWithLifecycle()
            val hubState by vm.hubState.collectAsStateWithLifecycle()
            LaunchedEffect(route) {
                vm.selectComputerKind(
                    if (route.kind == CapabilityComputerKind.Codex) AgentComputerKind.Codex
                    else AgentComputerKind.Paseo
                )
                vm.startAdding()
            }
            AgentsScreen(state, vm, hubState, vm)
        }
        entry<CapabilityAgentAccountRoute> { route ->
            val vm = metroViewModel<AgentsViewModel>()
            LifecycleResumeEffect(vm) {
                vm.refreshPhone()
                onPauseOrDispose {}
            }
            val state by vm.state.collectAsStateWithLifecycle()
            val hubState by vm.hubState.collectAsStateWithLifecycle()
            LaunchedEffect(route) { vm.openAccount(route.profile) }
            AgentsScreen(state, vm, hubState, vm)
        }
        entry<AgentsRoute.SignInTerminal> { route ->
            val vm =
                assistedMetroViewModel<
                    PhoneSignInRouteViewModel,
                    PhoneSignInRouteViewModel.Factory,
                > {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            val pageHost by vm.pageHost.collectAsStateWithLifecycle()
            PhoneSignInScreen(state, pageHost, vm)
        }
        entry<AgentsRoute.Home> {
            val vm = metroViewModel<AgentsViewModel>()
            LifecycleResumeEffect(vm) {
                vm.refreshPhone()
                onPauseOrDispose {}
            }
            val state by vm.state.collectAsStateWithLifecycle()
            val hubState by vm.hubState.collectAsStateWithLifecycle()
            val arrival by arrivals.active.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose { arrivals.dismiss(ShowMeTarget.Agents) } }
            AgentsScreen(
                state,
                vm,
                hubState,
                vm,
                showArrival = arrival == ShowMeTarget.Agents,
                dismissArrival = { arrivals.dismiss(ShowMeTarget.Agents) },
            )
        }
    }
}
