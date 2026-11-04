package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxLocalStorage
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeUi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.linuxLocalClose
import io.github.eslamasabry.oc.ui.words.res.linuxLocalCreate
import io.github.eslamasabry.oc.ui.words.res.linuxLocalCreatePath
import io.github.eslamasabry.oc.ui.words.res.linuxLocalEmpty
import io.github.eslamasabry.oc.ui.words.res.linuxLocalFolder
import io.github.eslamasabry.oc.ui.words.res.linuxLocalFolderFailed
import io.github.eslamasabry.oc.ui.words.res.linuxLocalFreed
import io.github.eslamasabry.oc.ui.words.res.linuxLocalGit
import io.github.eslamasabry.oc.ui.words.res.linuxLocalInvalidPath
import io.github.eslamasabry.oc.ui.words.res.linuxLocalMeasure
import io.github.eslamasabry.oc.ui.words.res.linuxLocalParent
import io.github.eslamasabry.oc.ui.words.res.linuxLocalProjects
import io.github.eslamasabry.oc.ui.words.res.linuxLocalProjectsDetail
import io.github.eslamasabry.oc.ui.words.res.linuxLocalReading
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoveAll
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoveKeep
import org.jetbrains.compose.resources.stringResource

/**
 * Always reachable even when the disposable runtime was removed; these rows never start a server.
 */
@Composable
internal fun LinuxLocalStoragePanel(state: LinuxHomeUi, actions: LinuxHomeActions) {
    KitRowGroup {
        if (state.ready)
            KitRow(
                title = stringResource(Res.string.linuxLocalCreate),
                enabled = !state.working && !state.job.running,
                onClick = actions::askCreateProject,
            )
        KitRow(
            title = stringResource(Res.string.linuxLocalProjects),
            supporting = stringResource(Res.string.linuxLocalProjectsDetail),
            supportingMaxLines = 2,
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.browse(LinuxLocalStorage.PROJECTS) },
        )
        KitRow(
            title = stringResource(Res.string.linuxLocalMeasure),
            onClick = actions::measureRemoval,
            enabled = !state.working && !state.job.running,
        )
    }
    state.folderPath?.let { path ->
        KitRowGroup(label = path) {
            LinuxLocalStorage.parent(path)?.let { parent ->
                KitRow(
                    title = stringResource(Res.string.linuxLocalParent),
                    onClick = { actions.browse(parent) },
                )
            }
            KitRow(
                title = stringResource(Res.string.linuxLocalClose),
                onClick = actions::closeFolders,
            )
            state.folders.forEach { folder ->
                KitRow(
                    title = folder.name,
                    supporting =
                        stringResource(
                            if (folder.git) Res.string.linuxLocalGit
                            else Res.string.linuxLocalFolder
                        ),
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.browse(folder.path) },
                )
            }
        }
        when {
            state.folderLoading -> KitNotice(message = stringResource(Res.string.linuxLocalReading))
            state.folderFailed ->
                KitNotice(
                    message = stringResource(Res.string.linuxLocalFolderFailed),
                    actions =
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.commonRetry),
                                { actions.browse(path) },
                            )
                        ),
                )
            state.folders.isEmpty() ->
                KitNotice(message = stringResource(Res.string.linuxLocalEmpty))
        }
    }
    state.removalSize?.let { measured ->
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.linuxLocalRemoveKeep),
                supporting =
                    stringResource(Res.string.linuxLocalFreed, size(measured.freed(false))),
                onClick = { actions.askRemove(false) },
            )
            KitRow(
                title = stringResource(Res.string.linuxLocalRemoveAll),
                supporting = stringResource(Res.string.linuxLocalFreed, size(measured.freed(true))),
                onClick = { actions.askRemove(true) },
            )
        }
    }
    val invalid = stringResource(Res.string.linuxLocalInvalidPath)
    KitInputDialog(
        visible = state.askingCreateProject,
        title = stringResource(Res.string.linuxLocalCreate),
        fieldLabel = stringResource(Res.string.linuxLocalCreatePath),
        confirmLabel = stringResource(Res.string.linuxLocalCreate),
        initial = (state.folderPath ?: LinuxLocalStorage.PROJECTS).trimEnd('/') + "/",
        validate = {
            if (
                LinuxLocalStorage.normalize(it) == null ||
                    it.endsWith('/') ||
                    it.contains('\n') ||
                    it.contains('\r')
            )
                invalid
            else null
        },
        onResult = actions::finishCreateProject,
    )
}
