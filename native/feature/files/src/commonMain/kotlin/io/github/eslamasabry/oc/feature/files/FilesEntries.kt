package io.github.eslamasabry.oc.feature.files

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.files.presentation.FileViewModel
import io.github.eslamasabry.oc.feature.files.presentation.FolderViewModel
import io.github.eslamasabry.oc.feature.files.ui.FileScreen
import io.github.eslamasabry.oc.feature.files.ui.FolderScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.FilesRoute

/** Project Files for a conversation: each folder and each file is its own entry (predictive back per level). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class FilesEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<FilesRoute.Folder> { route ->
            val vm = assistedMetroViewModel<FolderViewModel, FolderViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            FolderScreen(state, vm)
        }
        entry<FilesRoute.File> { route ->
            val vm = assistedMetroViewModel<FileViewModel, FileViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            FileScreen(state, vm)
        }
    }
}
