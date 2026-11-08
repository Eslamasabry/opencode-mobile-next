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
import io.github.eslamasabry.oc.feature.factsbook.presentation.SettingsViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.StatusViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.EventViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.RuleEditorViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.TriggersViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.PhoneNodeViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.NeedsYouViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.RemindersViewModel
import io.github.eslamasabry.oc.feature.factsbook.presentation.ReminderEditViewModel
import io.github.eslamasabry.oc.feature.factsbook.ui.NeedsYouScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.RemindersScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.ReminderEditScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.AboutYouScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.AreasScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.CaptureScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.ConnectScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.NotSavedScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.SearchScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.SettingsScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.StatusScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.EventScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.RuleEditorScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.TriggersScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.PhoneNodeScreen
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
        entry<FactsBookRoute.NeedsYou> {
            val vm = metroViewModel<NeedsYouViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            NeedsYouScreen(state, vm)
        }
        entry<FactsBookRoute.Reminders> {
            val vm = metroViewModel<RemindersViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            RemindersScreen(state, vm)
        }
        entry<FactsBookRoute.ReminderEdit> { route ->
            val vm = assistedMetroViewModel<ReminderEditViewModel, ReminderEditViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ReminderEditScreen(state, vm)
        }
        entry<FactsBookRoute.Connect> {
            val vm = metroViewModel<ConnectViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            ConnectScreen(state, vm)
        }
        entry<FactsBookRoute.Status> {
            val vm = metroViewModel<StatusViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            StatusScreen(state, vm)
        }
        entry<FactsBookRoute.Settings> {
            val vm = metroViewModel<SettingsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(state, vm)
        }
        entry<FactsBookRoute.Triggers> {
            val vm = metroViewModel<TriggersViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            TriggersScreen(state, vm)
        }
        entry<FactsBookRoute.Rule> { route ->
            val vm = assistedMetroViewModel<RuleEditorViewModel, RuleEditorViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            RuleEditorScreen(state, vm)
        }
        entry<FactsBookRoute.PhoneNode> {
            val vm = metroViewModel<PhoneNodeViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            PhoneNodeScreen(state, vm)
        }
        entry<FactsBookRoute.Event> { route ->
            val vm = assistedMetroViewModel<EventViewModel, EventViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            EventScreen(state, vm)
        }
    }
}
