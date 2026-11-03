package io.github.eslamasabry.oc.feature.about

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.about.presentation.NoticesViewModel
import io.github.eslamasabry.oc.feature.about.presentation.TipsViewModel
import io.github.eslamasabry.oc.feature.about.ui.NoticesScreen
import io.github.eslamasabry.oc.feature.about.ui.TipsScreen
import io.github.eslamasabry.oc.ui.routes.AboutRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

/** The EntryInstaller; the only file in the module that imports androidx.navigation3.runtime (Konsist K1). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class AboutEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<AboutRoute.Tips> {
            val vm = metroViewModel<TipsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            TipsScreen(state, vm)
        }
        entry<AboutRoute.Notices> {
            val vm = metroViewModel<NoticesViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            NoticesScreen(state, vm)
        }
    }
}
