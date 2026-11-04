package io.github.eslamasabry.oc.feature.agents

import androidx.compose.runtime.DisposableEffect
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
import io.github.eslamasabry.oc.feature.agents.hub.AgentsViewModel
import io.github.eslamasabry.oc.feature.agents.presentation.PhoneSignInRouteViewModel
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.feature.agents.ui.PhoneSignInScreen
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

/**
 * The module's only navigation3 import (Konsist K1). The sign-in terminal joins when its host
 * binding exists.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class AgentsEntries(private val arrivals: ShowMeTargets) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<AgentsRoute.SignInTerminal> { route ->
            val vm =
                assistedMetroViewModel<
                    PhoneSignInRouteViewModel,
                    PhoneSignInRouteViewModel.Factory,
                > {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            val host by vm.pageHost.collectAsStateWithLifecycle()
            PhoneSignInScreen(state, host, vm)
        }
        entry<AgentsRoute.Home> {
            val vm = metroViewModel<AgentsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            val arrival by arrivals.active.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose { arrivals.dismiss(ShowMeTarget.Agents) } }
            AgentsScreen(
                state,
                vm,
                arrival == ShowMeTarget.Agents,
                { arrivals.dismiss(ShowMeTarget.Agents) },
            )
        }
    }
}
