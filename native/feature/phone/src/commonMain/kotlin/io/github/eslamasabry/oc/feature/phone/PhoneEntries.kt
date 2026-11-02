package io.github.eslamasabry.oc.feature.phone

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningViewModel
import io.github.eslamasabry.oc.feature.phone.presentation.RunningViewModel
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneViewModel
import io.github.eslamasabry.oc.feature.phone.ui.KeepRunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.RunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.ThisPhoneScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PhoneRoute

/** The EntryInstaller (S3-PHONEUI request R1); the ONLY file in the module that imports androidx.navigation3.runtime. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class PhoneEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<PhoneRoute.ThisPhone> {
            val vm = metroViewModel<ThisPhoneViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            ThisPhoneScreen(state, vm)
        }
        entry<PhoneRoute.Running> {
            val vm = metroViewModel<RunningViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            RunningScreen(state, vm)
        }
        entry<PhoneRoute.KeepRunning> {
            val vm = metroViewModel<KeepRunningViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            KeepRunningScreen(state, vm)
        }
    }
}
