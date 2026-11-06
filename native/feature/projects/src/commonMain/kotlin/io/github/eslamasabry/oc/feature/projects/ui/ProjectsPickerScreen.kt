package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName
import io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderNameProblem
import io.github.eslamasabry.oc.feature.projects.presentation.FolderRowUi
import io.github.eslamasabry.oc.feature.projects.presentation.PathProblem
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectRules
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputOperationDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateView
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.chatsFilterChatCount
import io.github.eslamasabry.oc.ui.words.res.chatsFilterRunningCount
import io.github.eslamasabry.oc.ui.words.res.e7ProjectProjectsEmpty
import io.github.eslamasabry.oc.ui.words.res.e7ProjectProjectsEmptyDetail
import io.github.eslamasabry.oc.ui.words.res.e7ProjectProjectsSearch
import io.github.eslamasabry.oc.ui.words.res.folderBrowserGit
import io.github.eslamasabry.oc.ui.words.res.folderBrowserHomeHere
import io.github.eslamasabry.oc.ui.words.res.folderBrowserOpenedBefore
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.p12NoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12OpenFolder
import io.github.eslamasabry.oc.ui.words.res.p12PathBadParts
import io.github.eslamasabry.oc.ui.words.res.p12PathNotAbsolute
import io.github.eslamasabry.oc.ui.words.res.p12PhoneFolder
import io.github.eslamasabry.oc.ui.words.res.p12PhoneFolderUnavailable
import io.github.eslamasabry.oc.ui.words.res.p12ProjectsLoadFailed
import io.github.eslamasabry.oc.ui.words.res.p12ProjectsOnServer
import io.github.eslamasabry.oc.ui.words.res.p12ProjectsTitle
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreate
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreateAction
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreateFailedTitle
import io.github.eslamasabry.oc.ui.words.res.projectFolderCreateHelper
import io.github.eslamasabry.oc.ui.words.res.projectFolderNameLabel
import io.github.eslamasabry.oc.ui.words.res.projectFolderNewProject
import io.github.eslamasabry.oc.ui.words.res.projectFolderOpen
import io.github.eslamasabry.oc.ui.words.res.projectFolderOpenAction
import io.github.eslamasabry.oc.ui.words.res.projectFolderOpenSubtitle
import io.github.eslamasabry.oc.ui.words.res.projectFolderPathLabel
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.stateFolderNameCharacters
import io.github.eslamasabry.oc.ui.words.res.stateFolderNameEmpty
import io.github.eslamasabry.oc.ui.words.res.stateFolderNameSegment
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Projects (07 §5 D15/D16). Kit parts only (K1). The obvious options come first as visible rows
 * (owner rule): New project, Open a folder on the server, Folder on this phone; then Opened before;
 * then this server's projects. A row that can't run here says why in its own line, never a dead
 * tap.
 */
@Composable
public fun ProjectsPickerScreen(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.p12ProjectsTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        search =
            if (state.showSearch) {
                {
                    KitSearchField(
                        label = stringResource(Res.string.e7ProjectProjectsSearch),
                        onSearch = actions::search,
                    )
                }
            } else {
                null
            },
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            when (state.phase) {
                ProjectsPhase.NoServer ->
                    item(key = "no-server") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.p12NoServerTitle),
                            body = stringResource(Res.string.p12NoServerBody),
                            primary =
                                KitAction(
                                    stringResource(Res.string.serversTitle),
                                    actions::openServers,
                                ),
                        )
                    }
                ProjectsPhase.Ready -> {
                    if (state.servers.isNotEmpty()) {
                        item(key = "servers") {
                            KitChipWrap {
                                state.servers.forEach { server ->
                                    KitChip(
                                        label = server.name,
                                        kind = KitChipKind.Action,
                                        tone =
                                            if (server.selected) KitChipTone.Active
                                            else KitChipTone.Neutral,
                                        selected = server.selected,
                                        onClick = { actions.chooseServer(server.profile) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "start") { StartRows(state, actions) }
                    if (state.canFindPhoneProjects)
                        item(key = "find-phone") {
                            KitRowGroup(label = stringResource(Res.string.linuxFindProjects)) {
                                KitRow(
                                    title =
                                        stringResource(
                                            if (state.findingPhoneProjects)
                                                Res.string.linuxStopFinding
                                            else Res.string.linuxFindProjects
                                        ),
                                    supporting = stringResource(Res.string.linuxFindProjectsBody),
                                    supportingMaxLines = 2,
                                    onClick =
                                        if (state.findingPhoneProjects) actions::cancelPhoneScan
                                        else actions::findPhoneProjects,
                                )
                                state.scanEnd?.let { end ->
                                    KitRow(
                                        title =
                                            stringResource(
                                                if (
                                                    end ==
                                                        io.github.eslamasabry.oc.engine.files
                                                            .ProjectScanEnd
                                                            .Completed
                                                )
                                                    Res.string.linuxFindProjectsDone
                                                else Res.string.linuxFindProjectsLimited
                                            )
                                    )
                                }
                                state.phoneProjects.forEach { ProjectRow(it, actions) }
                            }
                        }
                    if (state.openedBefore.isNotEmpty()) {
                        item(key = "opened") {
                            KitRowGroup(
                                label = stringResource(Res.string.folderBrowserOpenedBefore)
                            ) {
                                state.openedBefore.forEach { folder ->
                                    KitRow(
                                        title = folder.name,
                                        supporting = folder.directory,
                                        trailing = KitRowTrailing.Chevron,
                                        onClick = { actions.openProject(folder.directory) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "projects") { ServerProjects(state, actions) }
                }
            }
        }
        PathDialog(state, actions)
        CreateDialog(state, actions)
        MissingFolderDialog(state, actions)
        RenameDialog(state, actions)
        PhoneFolderSheet(state, actions)
    }
}

/** Inside the KitScreen body, so the dialog finds the screen's KitSheetHost. */
@Composable
private fun PathDialog(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    if (!state.askingPath) return
    val path = rememberTextFieldState(state.pathStart)
    var submitted by remember { mutableStateOf(false) }
    val typed = path.text.toString().trim()
    val problem =
        when (ProjectRules.pathProblem(typed)) {
            PathProblem.NotAbsolute -> stringResource(Res.string.p12PathNotAbsolute)
            PathProblem.BadParts -> stringResource(Res.string.p12PathBadParts)
            PathProblem.HomeOrRoot -> stringResource(Res.string.folderBrowserHomeHere)
            null -> null
        }
    fun submit() {
        submitted = true
        if (problem == null) actions.finishPath(typed)
    }
    val recent = state.openedBefore.distinctBy { it.directory }
    val projects =
        state.projects
            .filter { project ->
                recent.none { it.directory == project.directory } &&
                    ProjectRules.pathProblem(project.directory) == null
            }
            .distinctBy { it.directory }
            .sortedBy { it.directory }
    KitSheet(
        visible = true,
        title = stringResource(Res.string.projectFolderOpen),
        onDismissRequest = { actions.finishPath(null) },
        primary =
            KitAction(
                stringResource(Res.string.projectFolderOpenAction),
                ::submit,
                icon = KitIcons.folderOpen,
            ),
    ) {
        if (recent.isNotEmpty() || projects.isNotEmpty()) {
            KitSectionLabel(stringResource(Res.string.folderBrowserFoldersLabel))
            KitRowGroup(leadingTiles = true) {
                for ((folder, icon) in
                    recent.map { it to KitIcons.history } +
                        projects.map { it to KitIcons.projects }) {
                    KitRow(
                        folder.name,
                        supporting = folder.directory,
                        leading = KitRowLeading.Glyph(icon),
                        trailing = KitRowTrailing.Chevron,
                        modifier = Modifier.testTag("remote-folder-${folder.directory}"),
                        onClick = { actions.finishPath(folder.directory) },
                    )
                }
            }
            Spacer(Modifier.height(Space.S4.dp))
        }
        KitField(
            path,
            stringResource(Res.string.projectFolderPathLabel),
            kind = KitFieldKind.Path,
            placeholder = stringResource(Res.string.projectFolderPathHint, "/root/projects"),
            helper = stringResource(Res.string.projectFolderOpenMessage),
            error = if (submitted) problem else null,
            modifier = Modifier.testTag("open-folder-path"),
            onTextChanged = { submitted = false },
            onSubmit = ::submit,
        )
    }
}

@Composable
private fun StartRows(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    KitRowGroup {
        // Absent, never greyed: a server that cannot make folders simply has no New project row
        // (Flutter: ProjectFolderActions.canCreate).
        if (state.canCreate)
            KitRow(
                title = stringResource(Res.string.projectFolderNewProject),
                supporting = stringResource(Res.string.projectFolderCreateHelper, "/root/projects"),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::askCreate,
                supportingMaxLines = 3,
            )
        KitRow(
            title = stringResource(Res.string.p12OpenFolder),
            supporting = stringResource(Res.string.projectFolderOpenSubtitle),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::askPath,
        )
        if (state.canBrowseBuiltin) {
            KitRow(
                title = stringResource(Res.string.secBuiltinFolders),
                supporting = stringResource(Res.string.secBuiltinFoldersBody),
                supportingMaxLines = 2,
                trailing = KitRowTrailing.Chevron,
                onClick = actions::browseBuiltin,
            )
        }
        KitRow(
            title = stringResource(Res.string.p12PhoneFolder),
            enabled = state.canBrowsePhone,
            trailing = KitRowTrailing.Chevron,
            onClick = actions::browsePhone,
            disabledReason = stringResource(Res.string.p12PhoneFolderUnavailable),
            supportingMaxLines = 2,
        )
    }
}

@Composable
private fun ServerProjects(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    KitRowGroup(label = stringResource(Res.string.p12ProjectsOnServer, state.serverName)) {
        val failure = state.projectsFailure
        when {
            state.projectsLoading -> KitSkeletonRows(count = 3)
            failure != null ->
                KitErrorNotice(
                    message = stringResource(Res.string.p12ProjectsLoadFailed),
                    failure = failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::retry),
                )
            state.projects.isEmpty() ->
                KitStateView(
                    kind = KitStateKind.Empty,
                    title = stringResource(Res.string.e7ProjectProjectsEmpty),
                    body = stringResource(Res.string.e7ProjectProjectsEmptyDetail),
                )
            else -> state.projects.forEach { ProjectRow(it, actions, state.canRename) }
        }
    }
}

@Composable
private fun ProjectRow(
    project: FolderRowUi,
    actions: ProjectsPickerActions,
    canRename: Boolean = false,
) {
    val parts = buildList {
        add(
            pluralStringResource(
                Res.plurals.chatsFilterChatCount,
                project.conversations,
                project.conversations,
            )
        )
        if (project.running > 0)
            add(stringResource(Res.string.chatsFilterRunningCount, project.running))
        project.kind?.let { kind ->
            add(
                stringResource(
                    when (kind) {
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Dart ->
                            Res.string.linuxProjectDart
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Node ->
                            Res.string.linuxProjectNode
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Python ->
                            Res.string.linuxProjectPython
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Rust ->
                            Res.string.linuxProjectRust
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Go ->
                            Res.string.linuxProjectGo
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Java ->
                            Res.string.linuxProjectJava
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Ruby ->
                            Res.string.linuxProjectRuby
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Php ->
                            Res.string.linuxProjectPhp
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Dotnet ->
                            Res.string.linuxProjectDotnet
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Cpp ->
                            Res.string.linuxProjectCpp
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Git ->
                            Res.string.folderBrowserGit
                        io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Folder ->
                            Res.string.linuxProjectFolder
                    }
                )
            )
        }
        if (
            project.isGit == true &&
                project.kind != io.github.eslamasabry.oc.engine.files.PhoneProjectKind.Git
        )
            add(stringResource(Res.string.folderBrowserGit))
        add(project.directory)
    }
    KitRow(
        title = project.name,
        supporting = parts.joinToString(" · "),
        supportingMaxLines = 2,
        trailing = KitRowTrailing.Chevron,
        onClick = { actions.openProject(project.directory) },
        // Rename is a long-press (Flutter), told to TalkBack as a custom action; only where the
        // server keeps names.
        onLongClick = if (canRename && project.id != null) ({ actions.askRename(project) }) else null,
        onLongClickLabel =
            if (canRename && project.id != null)
                stringResource(Res.string.e7ProjectProjectRenameAction, project.name)
            else null,
    )
}

/** "Create this folder?" when a typed path is not there yet (Flutter project_folder_actions). */
@Composable
private fun MissingFolderDialog(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    val path = state.missingPath
    var kept by remember { mutableStateOf<String?>(null) }
    if (path != null) kept = path
    val shown = path ?: kept
    KitConfirm(
        spec =
            if (path == null || shown == null) null
            else
                KitConfirmSpec(
                    title = stringResource(Res.string.projectFolderMissingTitle),
                    confirmLabel = stringResource(Res.string.projectFolderCreateIt),
                    body = stringResource(Res.string.projectFolderMissing),
                    icon = KitIcons.folderAdd,
                    details = listOf(KitTechnicalValue(stringResource(Res.string.projectFolderPathLabel), shown)),
                ),
        onConfirm = actions::createMissing,
        onCancel = actions::dismissMissing,
        working = state.creatingMissing,
        failed = state.missingFailure != null,
    )
}

@Composable
private fun RenameDialog(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    val project = state.renaming ?: return
    val failed = stringResource(Res.string.e7ProjectProjectRenameFailed, "")
    KitInputOperationDialog(
        visible = true,
        title = stringResource(Res.string.e7ProjectProjectRenameTitle),
        fieldLabel = stringResource(Res.string.e7ProjectProjectNameLabel),
        confirmLabel = stringResource(Res.string.e7ProjectProjectSave),
        cancelLabel = stringResource(Res.string.projectFolderCancel),
        initial = project.name,
        helper = stringResource(Res.string.e7ProjectProjectNameHint),
        working = state.renamingWorking,
        error = state.renameFailure?.let { failed.trimEnd(' ', ':') },
        details = {
            state.renameFailure?.details?.let {
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    text = it,
                )
            }
        },
        onSubmit = actions::renameProject,
        onDismissRequest = actions::closeRename,
    )
}

@Composable
private fun CreateDialog(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    if (!state.askingCreate) return
    val empty = stringResource(Res.string.stateFolderNameEmpty)
    val segment = stringResource(Res.string.stateFolderNameSegment)
    val characters = stringResource(Res.string.stateFolderNameCharacters)
    val failed = stringResource(Res.string.projectFolderCreateFailedTitle)
    KitInputOperationDialog(
        visible = true,
        title = stringResource(Res.string.projectFolderCreate),
        fieldLabel = stringResource(Res.string.projectFolderNameLabel),
        confirmLabel = stringResource(Res.string.projectFolderCreateAction),
        cancelLabel = stringResource(Res.string.projectFolderCancel),
        initial = state.createName,
        hint = stringResource(Res.string.projectFolderNameHint),
        helper = stringResource(Res.string.projectFolderCreateHelper, "/root/projects"),
        working = state.creatingFolder,
        error = state.folderFailure?.let { failed },
        validate = { name ->
            when (ProjectFolderName.problem(name)) {
                ProjectFolderNameProblem.Empty -> empty
                ProjectFolderNameProblem.NotSingleSegment -> segment
                ProjectFolderNameProblem.InvalidCharacters -> characters
                null -> null
            }
        },
        details = {
            state.folderFailure?.details?.let {
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    text = it,
                )
            }
        },
        onTextChanged = actions::changeCreateName,
        onSubmit = { name ->
            // A supplied suggestion and a last-keystroke IME submit both use the visible name.
            actions.changeCreateName(name)
            actions.createFolder()
        },
        onDismissRequest = actions::closeCreate,
    )
}

/**
 * An existing kit sheet keeps the browser in the project journey, without a second navigation
 * stack.
 */
@Composable
private fun PhoneFolderSheet(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    val path = state.phonePath ?: return
    val folderName = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
    val canOpen = !state.phoneLoading && state.phoneFailure == null && state.phoneParent != null
    val held =
        if (state.phoneLoading) KitEnabled.Disabled(stringResource(Res.string.andrStorageWorking))
        else KitEnabled.Enabled
    KitSheet(
        visible = true,
        onDismissRequest = actions::closePhoneFolders,
        title =
            if (!state.browsingBuiltin && state.phoneParent == null)
                stringResource(Res.string.folderBrowserInternalStorage)
            else folderName,
        subtitle =
            stringResource(
                if (state.browsingBuiltin) Res.string.folderBrowserPlaceProjects
                else Res.string.folderBrowserPlacePhone
            ),
        leading =
            KitAction(
                stringResource(
                    if (state.phoneParent == null) Res.string.kitTopBarBack
                    else Res.string.folderBrowserUp
                ),
                {
                    state.phoneParent?.let(actions::enterPhoneFolder) ?: actions.closePhoneFolders()
                },
                enabled = held,
            ),
        height = KitSheetHeight.Content,
        primary =
            if (!canOpen) null
            else
                KitAction(
                    stringResource(Res.string.folderBrowserOpen, folderName),
                    actions::usePhoneFolder,
                    icon = KitIcons.folderOpen,
                ),
    ) {
        when {
            state.phoneLoading ->
                KitSkeletonRows(count = 4) // F: lib/ui/widgets/folder_browser.dart:1122
            state.phoneFailure != null -> {
                val failure = state.phoneFailure
                val message =
                    when ((failure as? Failure.Folder)?.problem) {
                        FolderProblem.Missing -> Res.string.folderBrowserErrorMissing
                        FolderProblem.Denied -> Res.string.folderBrowserErrorDenied
                        FolderProblem.Linked -> Res.string.folderBrowserErrorLinked
                        FolderProblem.NotInstalled -> Res.string.folderBrowserErrorNotInstalled
                        else -> Res.string.folderBrowserErrorFailed
                    }
                KitInlineStateView(
                    title = stringResource(Res.string.folderBrowserErrorTitle),
                    icon = KitIcons.warning,
                    body = stringResource(message),
                    secondary =
                        KitAction(
                            stringResource(Res.string.folderBrowserRetry),
                            { actions.enterPhoneFolder(path) },
                        ),
                )
                failure.details?.let {
                    KitDetailsFold(
                        stringResource(Res.string.kitDetails),
                        stringResource(Res.string.kitCopyDetails),
                        text = it,
                    )
                }
                if (
                    !state.browsingBuiltin &&
                        failure is Failure.Folder &&
                        failure.problem == FolderProblem.Denied
                )
                    io.github.eslamasabry.oc.ui.kit.KitButton(
                        stringResource(Res.string.andrStorageAccess),
                        actions::allowPhoneFolderAccess,
                        role = io.github.eslamasabry.oc.ui.kit.KitButtonRole.Tertiary,
                    )
            }
            state.phoneFolders.isEmpty() ->
                KitInlineStateView(
                    title = stringResource(Res.string.folderBrowserEmptyTitle),
                    icon = KitIcons.folderOpen,
                    body =
                        stringResource(
                            if (state.browsingBuiltin) Res.string.folderBrowserEmptyBody
                            else Res.string.folderBrowserEmptyPhoneBody
                        ),
                )
            else ->
                KitRowGroup(leadingTiles = true) {
                    state.phoneFolders.forEach { folder ->
                        KitRow(
                            title = folder.name,
                            supporting = folder.hint,
                            leading = KitRowLeading.Glyph(KitIcons.folderOpen),
                            trailing = KitRowTrailing.Chevron,
                            modifier = Modifier.testTag("phone-folder-${folder.path}"),
                            onClick = { actions.enterPhoneFolder(folder.path) },
                        )
                    }
                }
        }
    }
}
