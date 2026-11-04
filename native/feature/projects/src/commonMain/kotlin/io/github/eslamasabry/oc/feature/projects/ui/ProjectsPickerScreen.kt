package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import io.github.eslamasabry.oc.ui.kit.KitButton
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
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
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
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.p12NewProjectUnavailable
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
import io.github.eslamasabry.oc.ui.words.res.stateFolderCreating
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
    }
}

/** Inside the KitScreen body, so the dialog finds the screen's KitSheetHost. */
@Composable
private fun PathDialog(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    val notAbsolute = stringResource(Res.string.p12PathNotAbsolute)
    val badParts = stringResource(Res.string.p12PathBadParts)
    val home = stringResource(Res.string.folderBrowserHomeHere)
    KitInputDialog(
        visible = state.askingPath,
        title = stringResource(Res.string.projectFolderOpen),
        fieldLabel = stringResource(Res.string.projectFolderPathLabel),
        confirmLabel = stringResource(Res.string.projectFolderOpenAction),
        initial = state.pathStart,
        kind = KitFieldKind.Path,
        validate = { typed ->
            when (ProjectRules.pathProblem(typed)) {
                PathProblem.NotAbsolute -> notAbsolute
                PathProblem.BadParts -> badParts
                PathProblem.HomeOrRoot -> home
                null -> null
            }
        },
        operationKey = "open-folder",
        onResult = actions::finishPath,
    )
}

@Composable
private fun StartRows(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    KitRowGroup {
        // OpenCode 2 has no folder-making call; Flutter offered New project only for servers it ran
        // on the phone.
        KitRow(
            title = stringResource(Res.string.projectFolderNewProject),
            enabled = state.canCreate,
            disabledReason =
                if (state.canCreate) null else stringResource(Res.string.p12NewProjectUnavailable),
            supporting =
                if (state.canCreate)
                    stringResource(Res.string.projectFolderCreateHelper, "/root/projects")
                else null,
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
        KitRow(
            title = stringResource(Res.string.p12PhoneFolder),
            enabled = false,
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
            else -> state.projects.forEach { ProjectRow(it, actions) }
        }
    }
}

@Composable
private fun ProjectRow(project: FolderRowUi, actions: ProjectsPickerActions) {
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
    )
}

@Composable
private fun CreateDialog(state: ProjectsPickerUi, actions: ProjectsPickerActions) {
    val problem =
        when (ProjectFolderName.problem(state.createName)) {
            ProjectFolderNameProblem.Empty -> stringResource(Res.string.stateFolderNameEmpty)
            ProjectFolderNameProblem.NotSingleSegment ->
                stringResource(Res.string.stateFolderNameSegment)
            ProjectFolderNameProblem.InvalidCharacters ->
                stringResource(Res.string.stateFolderNameCharacters)
            null -> null
        }
    val working = stringResource(Res.string.stateFolderCreating)
    KitSheet(
        visible = state.askingCreate,
        onDismissRequest = actions::closeCreate,
        title = stringResource(Res.string.projectFolderCreate),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitNotice(stringResource(Res.string.projectFolderCreateHelper, "/root/projects"))
            val field = rememberTextFieldState(state.createName)
            KitField(
                state = field,
                label = stringResource(Res.string.projectFolderNameLabel),
                error = problem,
                onTextChanged = actions::changeCreateName,
                enabled =
                    if (state.creatingFolder) KitEnabled.Disabled(working) else KitEnabled.Enabled,
            )
            state.folderFailure?.let {
                KitErrorNotice(
                    message = stringResource(Res.string.projectFolderCreateFailedTitle),
                    failure = it,
                )
            }
            KitButton(
                label = stringResource(Res.string.projectFolderCreateAction),
                onClick = actions::createFolder,
                working = state.creatingFolder,
                enabled =
                    if (state.creatingFolder || problem != null)
                        KitEnabled.Disabled(if (state.creatingFolder) working else problem!!)
                    else KitEnabled.Enabled,
            )
        }
    }
}
