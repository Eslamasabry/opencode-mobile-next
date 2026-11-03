package io.github.eslamasabry.oc.feature.review

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.review.presentation.ChangesViewModel
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffViewModel
import io.github.eslamasabry.oc.feature.review.presentation.StagedViewModel
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereViewModel
import io.github.eslamasabry.oc.feature.review.ui.ChangesScreen
import io.github.eslamasabry.oc.feature.review.ui.FileDiffScreen
import io.github.eslamasabry.oc.feature.review.ui.StagedScreen
import io.github.eslamasabry.oc.feature.review.ui.UndoFromHereScreen
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.ReviewRoute

/**
 * The review pages' entries (unit P12-REVIEW): one ViewModel per entry. The diff page reads through the chat
 * environment's redactor, as the chat's own diffs do.
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ReviewEntries(private val environment: KitChatEnvironment) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ReviewRoute.Changes> { route ->
            val vm = assistedMetroViewModel<ChangesViewModel, ChangesViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ChangesScreen(state, vm)
        }
        entry<ReviewRoute.File> { route ->
            val vm = assistedMetroViewModel<FileDiffViewModel, FileDiffViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            environment.Provide { FileDiffScreen(state, vm::back, vm::retry) }
        }
        entry<ReviewRoute.UndoFromHere> { route ->
            val vm = assistedMetroViewModel<UndoFromHereViewModel, UndoFromHereViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            UndoFromHereScreen(state, vm)
        }
        entry<ReviewRoute.Staged> { route ->
            val vm = assistedMetroViewModel<StagedViewModel, StagedViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            StagedScreen(state, vm)
        }
    }
}
