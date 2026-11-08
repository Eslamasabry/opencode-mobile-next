package io.github.eslamasabry.oc.feature.factsbook

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
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.ConnectViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.SearchViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingViewModel
import io.github.eslamasabry.oc.feature.factsbook.ui.AboutYouScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.AreasScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.CaptureScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.ConnectScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.NotSavedScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.SearchScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.ThingScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute

/** The FactsBook destinations (F: factsbook_routes.dart). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class FactsBookEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<FactsBookRoute.AboutYou> {
            val vm = metroViewModel<AboutYouViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            AboutYouScreen(state, vm)
        }
        entry<FactsBookRoute.Capture> {
            val vm = metroViewModel<CaptureViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            CaptureScreen(state, vm)
        }
        entry<FactsBookRoute.NotSaved> {
            val vm = metroViewModel<NotSavedViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            NotSavedScreen(state, vm)
        }
        entry<FactsBookRoute.Thing> { route ->
            val vm = assistedMetroViewModel<ThingViewModel, ThingViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ThingScreen(state, vm)
        }
        entry<FactsBookRoute.Search> { route ->
            val vm = assistedMetroViewModel<SearchViewModel, SearchViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            SearchScreen(state, vm)
        }
        entry<FactsBookRoute.Areas> {
            val vm = metroViewModel<AreasViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            AreasScreen(state, vm)
        }
        entry<FactsBookRoute.Connect> {
            val vm = metroViewModel<ConnectViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            ConnectScreen(state, vm)
        }
    }
}
