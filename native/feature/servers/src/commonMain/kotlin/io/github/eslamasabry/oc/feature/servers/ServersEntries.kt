package io.github.eslamasabry.oc.feature.servers

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.feature.servers.presentation.ServersViewModel
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute

/** Exactly one owner for the formerly unhandled New conversation route. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ServersEntries(
    private val navigator: AppNavigator,
    private val platform: PlatformCapabilities,
) : EntryInstaller {
    /** This phone is offered only where the built-in Linux exists (plan 07 §2.8). */
    private val thisPhone: (() -> Unit)?
        get() = if (platform.builtinLinux) ({ navigator.open(PhoneRoute.ThisPhone) }) else null

    override fun EntryProviderScope<NavKey>.install() {
        entry<ServersRoute.Home> {
            val vm =
                assistedMetroViewModel<ServersViewModel, ServersViewModel.Factory> { create(null) }
            val state by vm.state.collectAsStateWithLifecycle()
            ServersScreen(state, vm, onThisPhone = thisPhone)
        }
    }
}
