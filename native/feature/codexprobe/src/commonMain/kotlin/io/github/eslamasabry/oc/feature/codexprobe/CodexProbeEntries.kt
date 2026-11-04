package io.github.eslamasabry.oc.feature.codexprobe

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.ui.routes.CodexProbeRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class CodexProbeEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<CodexProbeRoute> {
            val vm = metroViewModel<CodexProbeViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            CodexProbeScreen(state, vm)
        }
    }
}
