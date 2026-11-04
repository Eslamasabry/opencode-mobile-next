package io.github.eslamasabry.oc.feature.git

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.git.presentation.GitViewModel
import io.github.eslamasabry.oc.feature.git.servertools.GitServerToolsPanel
import io.github.eslamasabry.oc.feature.git.servertools.GitServerToolsViewModel
import io.github.eslamasabry.oc.feature.git.ui.GitScreen
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.GitRoute

/**
 * The Git page's entry (07 D09): one GitViewModel per GitRoute entry. The diff view needs the app's
 * redactor.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class GitEntries(private val environment: KitChatEnvironment) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<GitRoute> { route ->
            val vm = assistedMetroViewModel<GitViewModel, GitViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            val tools =
                assistedMetroViewModel<GitServerToolsViewModel, GitServerToolsViewModel.Factory> {
                    create(route)
                }
            val toolsState by tools.state.collectAsStateWithLifecycle()
            environment.Provide { GitScreen(state, vm) { GitServerToolsPanel(toolsState, tools) } }
        }
    }
}
