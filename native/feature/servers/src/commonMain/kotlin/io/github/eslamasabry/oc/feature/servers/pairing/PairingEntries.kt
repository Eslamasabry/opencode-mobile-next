package io.github.eslamasabry.oc.feature.servers.pairing

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PairingRoute

/** The pairing scanner's page (plan 07 E03). Reached from the server form's "Scan code". */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class PairingEntries(private val slot: QrScannerSlot) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<PairingRoute.Scanner> {
            val vm = metroViewModel<PairingScannerViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            PairingScannerScreen(state, vm, slot, vm::accepts)
        }
    }
}
