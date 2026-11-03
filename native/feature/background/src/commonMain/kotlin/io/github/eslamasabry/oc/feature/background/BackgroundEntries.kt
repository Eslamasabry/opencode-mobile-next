package io.github.eslamasabry.oc.feature.background

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundViewModel
import io.github.eslamasabry.oc.feature.background.ui.BackgroundScreen
import io.github.eslamasabry.oc.ui.routes.BackgroundRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import kotlin.time.Clock

/** The EntryInstaller; the only file in the module that imports androidx.navigation3.runtime (Konsist K1). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class BackgroundEntries(private val clock: Clock) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<BackgroundRoute.Home> {
            val vm = metroViewModel<BackgroundViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            BackgroundScreen(state, vm, now = clock.now())
        }
    }
}
