package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupViewModel
import io.github.eslamasabry.oc.feature.phonesetup.ui.PhoneSetupScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute

/** The EntryInstaller; the ONLY file in the module that imports androidx.navigation3.runtime (Konsist K1). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class PhoneSetupEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<PhoneSetupRoute.Setup> {
            val vm = metroViewModel<PhoneSetupViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            PhoneSetupScreen(state, vm)
        }
    }
}
