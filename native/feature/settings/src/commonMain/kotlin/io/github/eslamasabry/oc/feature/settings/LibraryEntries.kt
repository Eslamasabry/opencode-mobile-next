package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.LibraryViewModel
import io.github.eslamasabry.oc.feature.settings.ui.LibraryScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.*

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class LibraryEntries(private val environment: KitChatEnvironment) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<LibraryRoute> { route ->
            val vm =
                assistedMetroViewModel<LibraryViewModel, LibraryViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            KitBackGuard(state.sheet != null || state.link != null, vm::back)
            environment.Provide { LibraryScreen(state, vm) }
        }
    }
}
