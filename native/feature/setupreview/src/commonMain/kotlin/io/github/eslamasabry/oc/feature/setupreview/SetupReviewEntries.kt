package io.github.eslamasabry.oc.feature.setupreview

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.setupreview.presentation.SetupReviewViewModel
import io.github.eslamasabry.oc.feature.setupreview.ui.SetupReviewScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.SetupReviewRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class SetupReviewEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<SetupReviewRoute> { route ->
            val vm =
                assistedMetroViewModel<SetupReviewViewModel, SetupReviewViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            SetupReviewScreen(state, vm)
        }
    }
}
