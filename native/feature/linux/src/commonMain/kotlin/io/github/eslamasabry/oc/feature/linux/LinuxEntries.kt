package io.github.eslamasabry.oc.feature.linux

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
import io.github.eslamasabry.oc.feature.linux.presentation.KeptServicesViewModel
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxBackupsViewModel
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeViewModel
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPackagesViewModel
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsViewModel
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServicesViewModel
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxStorageViewModel
import io.github.eslamasabry.oc.feature.linux.ui.KeptServicesScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxBackupsScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxHomeScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxPackagesScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxPortsScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxServicesScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxStorageScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.LinuxRoute

/**
 * The EntryInstaller; the ONLY file in the module that imports androidx.navigation3.runtime
 * (Konsist K1).
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class LinuxEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<LinuxRoute.Services> { route ->
            val vm =
                assistedMetroViewModel<LinuxServicesViewModel, LinuxServicesViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            LinuxServicesScreen(state, vm)
        }
        entry<LinuxRoute.Backups> {
            val vm = metroViewModel<LinuxBackupsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            LinuxBackupsScreen(state, vm)
        }
        entry<LinuxRoute.KeptServices> {
            val vm = metroViewModel<KeptServicesViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            KeptServicesScreen(state, vm)
        }
        entry<LinuxRoute.Home> {
            val vm = metroViewModel<LinuxHomeViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            LinuxHomeScreen(state, vm)
        }
        entry<LinuxRoute.Ports> {
            val vm = metroViewModel<LinuxPortsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            LinuxPortsScreen(state, vm)
        }
        entry<LinuxRoute.Storage> {
            val vm = metroViewModel<LinuxStorageViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            LinuxStorageScreen(state, vm)
        }
        entry<LinuxRoute.Packages> {
            val vm = metroViewModel<LinuxPackagesViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            LinuxPackagesScreen(state, vm)
        }
    }
}
