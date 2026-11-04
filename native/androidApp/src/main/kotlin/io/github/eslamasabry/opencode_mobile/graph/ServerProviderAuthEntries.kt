package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInViewModel
import io.github.eslamasabry.oc.feature.plans.ui.PlanSignInScreen
import io.github.eslamasabry.oc.ui.routes.*

/** Reuses the same credential/OAuth owner, constrained to the server this settings page names. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
class ServerProviderAuthEntries(private val nav: AppNavigator) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ServerProviderAuthRoute> { route ->
            val vm = metroViewModel<PlanSignInViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            var began by remember { mutableStateOf(false) }
            LaunchedEffect(route) { vm.open(route.provider, route.profile.raw, pinned = true) }
            LaunchedEffect(state.active) {
                if (state.active) began = true else if (began) nav.back()
            }
            PlanSignInScreen(state, vm)
        }
    }
}
