package io.github.eslamasabry.oc.feature.search

import androidx.compose.runtime.LaunchedEffect
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
import io.github.eslamasabry.oc.feature.search.presentation.ProjectHealthViewModel
import io.github.eslamasabry.oc.feature.search.presentation.SearchViewModel
import io.github.eslamasabry.oc.feature.search.presentation.ShortcutsViewModel
import io.github.eslamasabry.oc.feature.search.ui.ProjectHealthScreen
import io.github.eslamasabry.oc.feature.search.ui.SearchScreen
import io.github.eslamasabry.oc.feature.search.ui.ShortcutsScreen
import io.github.eslamasabry.oc.feature.search.web.WebSearchPanel
import io.github.eslamasabry.oc.feature.search.web.WebSearchViewModel
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.SearchRoute

/**
 * The EntryInstaller; the only file in the module that imports androidx.navigation3.runtime
 * (Konsist K1).
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class SearchEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<SearchRoute.ProjectHealth> { route ->
            val vm =
                assistedMetroViewModel<ProjectHealthViewModel, ProjectHealthViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            ProjectHealthScreen(state, vm)
        }
        entry<SearchRoute.Shortcuts> {
            val vm = metroViewModel<ShortcutsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            ShortcutsScreen(state, vm)
        }
        entry<SearchRoute.Conversations> { route ->
            val vm =
                assistedMetroViewModel<SearchViewModel, SearchViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            val web =
                assistedMetroViewModel<WebSearchViewModel, WebSearchViewModel.Factory> {
                    create(route)
                }
            val webState by web.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.server) { web.chooseServer(state.server) }
            SearchScreen(state, vm, webSearch = { WebSearchPanel(webState, web) })
        }
    }
}
