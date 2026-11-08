package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FolderProblem
import io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName
import io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderNameProblem
import io.github.eslamasabry.oc.engine.git.GitProblem
import io.github.eslamasabry.oc.engine.git.gitRemoteAddressProblem
import io.github.eslamasabry.oc.engine.git.isValidGitFolderName
import io.github.eslamasabry.oc.engine.git.suggestGitCloneFolder
import io.github.eslamasabry.oc.feature.projects.presentation.OpenFolderUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectActions
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectDialog
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectStep
import io.github.eslamasabry.oc.feature.projects.presentation.OpenProjectUi
import io.github.eslamasabry.oc.feature.projects.presentation.OpenServerStatus
import io.github.eslamasabry.oc.feature.projects.presentation.PathProblem
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectRules
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateView
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * B-182 "Open a project" (Flutter lib/ui/widgets/folder_browser.dart, goldens test/goldens/folder_browser_*). Kit
 * parts only. The bar's title is the page: "Where is the project?", "Projects on <server>", then the folder's name with
 * its path under it; the bar's back goes up one level. The folders are one panel; New folder, Clone a repository and
 * Type a path sit on their own panel under them; inside a folder "Open <name>" is the pinned primary.
 */
@Composable
public fun OpenProjectScreen(state: OpenProjectUi, actions: OpenProjectActions) {
    val folderName = state.path?.trimEnd('/')?.substringAfterLast('/')?.ifEmpty { state.path }.orEmpty()
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    when (state.step) {
                        OpenProjectStep.Servers -> stringResource(Res.string.openProjectWhereTitle)
                        OpenProjectStep.Projects -> stringResource(Res.string.p12ProjectsOnServer, state.serverName)
                        OpenProjectStep.Folder -> folderName
                    },
                subtitle = if (state.step == OpenProjectStep.Folder) state.path else null,
                exit = KitTopBarExit.Back,
                exitLabel =
                    stringResource(
                        if (state.step == OpenProjectStep.Folder) Res.string.folderBrowserUp else Res.string.kitTopBarBack
                    ),
                onExit = actions::back,
            )
        },
        bottom =
            if (state.step == OpenProjectStep.Folder && state.failure == null) {
                {
                    KitActionBlock(
                        primary =
                            KitAction(
                                stringResource(Res.string.folderBrowserOpen, folderName),
                                actions::openShown,
                                icon = KitIcons.folderOpen,
                                working = state.loading,
                            )
                    )
                }
            } else null,
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            when (state.step) {
                OpenProjectStep.Servers -> {
                    // Flutter's start page: the projects opened before come first, a tap opens one.
                    if (state.recent.isNotEmpty())
                        item(key = "recent") {
                            KitRowGroup(label = stringResource(Res.string.folderBrowserOpenedBefore), leadingTiles = true) {
                                state.recent.forEach { recent ->
                                    KitRow(
                                        title = recent.name,
                                        supporting = listOfNotNull(recent.serverName, recent.path).joinToString(" · "),
                                        supportingMaxLines = 2,
                                        leading = KitRowLeading.Glyph(KitIcons.history),
                                        trailing = KitRowTrailing.Chevron,
                                        modifier = Modifier.testTag("open-recent-${recent.profile.raw}-${recent.path}"),
                                        onClick = { actions.openRecent(recent.profile, recent.path) },
                                    )
                                }
                            }
                        }
                    item(key = "servers") { Servers(state, actions) }
                }
                OpenProjectStep.Projects,
                OpenProjectStep.Folder -> {
                    if (state.step == OpenProjectStep.Projects && state.openedBefore.isNotEmpty())
                        item(key = "opened") {
                            KitRowGroup(label = stringResource(Res.string.folderBrowserOpenedBefore), leadingTiles = true) {
                                state.openedBefore.forEach { folder ->
                                    KitRow(
                                        title = folder.name,
                                        supporting = folder.path,
                                        leading = KitRowLeading.Glyph(KitIcons.history),
                                        trailing = KitRowTrailing.Chevron,
                                        onClick = { actions.openFolder(folder.path) },
                                    )
                                }
                            }
                        }
                    item(key = "folders") { Folders(state, actions) }
                    item(key = "more") { MoreWays(state, actions) }
                }
            }
        }
        NewFolderDialog(state, actions)
        CloneDialog(state, actions)
        PathDialog(state, actions)
        MissingDialog(state, actions)
    }
}

@Composable
private fun Servers(state: OpenProjectUi, actions: OpenProjectActions) {
    when {
        state.serversLoading -> KitSkeletonRows(count = 2)
        state.servers.isEmpty() ->
            KitStateView(
                kind = KitStateKind.Calm,
                title = stringResource(Res.string.p12NoServerTitle),
                body = stringResource(Res.string.p12NoServerBody),
                primary = KitAction(stringResource(Res.string.serversTitle), actions::openServers),
            )
        else ->
            KitRowGroup(label = stringResource(Res.string.openProjectWhereBody), leadingTiles = true) {
                state.servers.forEach { server ->
                    KitRow(
                        title = server.name,
                        leading = KitRowLeading.Glyph(if (server.onThisPhone) KitIcons.phone else KitIcons.server),
                        supporting =
                            stringResource(
                                when (server.status) {
                                    OpenServerStatus.Connected -> Res.string.openProjectStatusConnected
                                    OpenServerStatus.Connecting -> Res.string.openProjectStatusConnecting
                                    OpenServerStatus.NotConnected -> Res.string.openProjectStatusNotConnected
                                }
                            ),
                        trailing = KitRowTrailing.Chevron,
                        modifier = Modifier.testTag("open-server-${server.profile.raw}"),
                        onClick = { actions.chooseServer(server.profile) },
                    )
                }
            }
    }
}

@Composable
private fun Folders(state: OpenProjectUi, actions: OpenProjectActions) {
    val failure = state.failure
    val projects = state.step == OpenProjectStep.Projects
    when {
        state.loading && state.slow -> {
            // F: folder_browser.dart (KitSince after KitMotion.escalateAfter): the skeleton stays, a word says why.
            KitInlineStateView(
                title = stringResource(Res.string.folderBrowserSlowTitle),
                icon = KitIcons.folderOpen,
                body =
                    stringResource(
                        if (state.onThisPhone) Res.string.folderBrowserSlowBody else Res.string.openProjectSlowBody
                    ),
            )
            KitSkeletonRows(count = 3)
        }
        state.loading -> KitSkeletonRows(count = 4)
        failure != null -> FolderProblemView(failure, actions)
        state.folders.isEmpty() ->
            KitInlineStateView(
                title =
                    stringResource(if (projects) Res.string.folderBrowserNoProjectsTitle else Res.string.folderBrowserEmptyTitle),
                icon = KitIcons.folderOpen,
                body =
                    stringResource(
                        if (projects) Res.string.openProjectProjectsEmptyBody else Res.string.folderBrowserEmptyBody
                    ),
            )
        else ->
            KitRowGroup(
                label =
                    stringResource(
                        if (projects) Res.string.folderBrowserProjectsHere else Res.string.folderBrowserFoldersLabel
                    ),
                leadingTiles = true,
            ) {
                state.folders.forEach { folder -> FolderRow(folder, projects, actions) }
            }
    }
}

@Composable
private fun FolderRow(folder: OpenFolderUi, projects: Boolean, actions: OpenProjectActions) {
    // F: folder_browser.dart: a project says what it is ("Git repository", "OpenCode project"); its path under it.
    val mark =
        when {
            folder.git -> stringResource(Res.string.folderBrowserGit)
            folder.known -> stringResource(Res.string.folderBrowserProject)
            else -> null
        }
    KitRow(
        title = folder.name,
        supporting = if (projects) listOfNotNull(mark, folder.path).joinToString(" · ") else mark,
        supportingMaxLines = 2,
        leading = KitRowLeading.Glyph(if (folder.git) KitIcons.branch else KitIcons.folderOpen),
        trailing = KitRowTrailing.Chevron,
        modifier = Modifier.testTag("open-folder-${folder.path}"),
        onClick = { actions.enterFolder(folder.path) },
    )
}

@Composable
private fun FolderProblemView(failure: Failure, actions: OpenProjectActions) {
    val reason =
        when {
            failure is Failure.Folder ->
                when (failure.problem) {
                    FolderProblem.Missing -> Res.string.folderBrowserErrorMissing
                    FolderProblem.Denied -> Res.string.folderBrowserErrorDenied
                    FolderProblem.Linked -> Res.string.folderBrowserErrorLinked
                    FolderProblem.NotInstalled -> Res.string.folderBrowserErrorNotInstalled
                }
            failure is Failure.TimedOut -> Res.string.folderBrowserErrorTimedOut
            failure is Failure.Network || failure is Failure.ComputerDidNotAnswer -> Res.string.openProjectServerNotConnected
            else -> Res.string.folderBrowserErrorFailed
        }
    KitInlineStateView(
        title = stringResource(Res.string.folderBrowserErrorTitle),
        icon = KitIcons.warning,
        body = stringResource(reason),
        secondary = KitAction(stringResource(Res.string.folderBrowserRetry), actions::retry),
    )
    // Plain words above; what the server said only under Details.
    failure.details?.let {
        KitDetailsFold(stringResource(Res.string.kitDetails), stringResource(Res.string.kitCopyDetails), text = it)
    }
}

/** New folder, Clone a repository, Type a path: always visible rows, never a hidden menu. */
@Composable
private fun MoreWays(state: OpenProjectUi, actions: OpenProjectActions) {
    val where = state.makeIn
    Spacer(Modifier.height(Space.S1.dp))
    KitRowGroup(leadingTiles = true) {
        if (state.canMake && where != null)
            KitRow(
                title = stringResource(Res.string.openProjectNewFolder),
                supporting = stringResource(Res.string.openProjectNewFolderBody, where),
                supportingMaxLines = 2,
                leading = KitRowLeading.Glyph(KitIcons.folderAdd),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::askNewFolder,
            )
        if (state.canClone && where != null)
            KitRow(
                title = stringResource(Res.string.gitCloneRow),
                supporting = stringResource(Res.string.openProjectCloneBody, where),
                supportingMaxLines = 2,
                leading = KitRowLeading.Glyph(KitIcons.download),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::askClone,
            )
        KitRow(
            title = stringResource(Res.string.openProjectTypePath),
            supporting = stringResource(Res.string.openProjectTypePathBody),
            leading = KitRowLeading.Glyph(KitIcons.edit),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::askPath,
        )
    }
}

@Composable
private fun DialogFailure(state: OpenProjectUi, message: String) {
    val failure = state.dialogFailure ?: return
    Spacer(Modifier.height(Space.S3.dp))
    KitErrorNotice(
        message = message,
        failure = failure,
        detailsLabel = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
    )
}

@Composable
private fun NewFolderDialog(state: OpenProjectUi, actions: OpenProjectActions) {
    if (state.dialog != OpenProjectDialog.NewFolder) return
    val where = state.makeIn ?: return
    val name = rememberTextFieldState()
    var submitted by remember { mutableStateOf(false) }
    val problem =
        when (ProjectFolderName.problem(name.text.toString())) {
            ProjectFolderNameProblem.Empty -> stringResource(Res.string.stateFolderNameEmpty)
            ProjectFolderNameProblem.NotSingleSegment -> stringResource(Res.string.stateFolderNameSegment)
            ProjectFolderNameProblem.InvalidCharacters -> stringResource(Res.string.stateFolderNameCharacters)
            null -> null
        }
    fun submit() {
        submitted = true
        val now = name.text.toString()
        if (ProjectFolderName.problem(now) == null) actions.createFolder(now)
    }
    val typed = name.text.toString().trim()
    KitSheet(
        visible = true,
        title = stringResource(Res.string.openProjectNewFolder),
        onDismissRequest = actions::closeDialog,
        primary =
            KitAction(
                stringResource(Res.string.folderBrowserCreateAndOpen),
                ::submit,
                icon = KitIcons.folderAdd,
                working = state.working,
            ),
    ) {
        KitField(
            name,
            stringResource(Res.string.gitCloneFolderLabel),
            helper =
                stringResource(
                    Res.string.folderBrowserNewProjectCreates,
                    "${where.trimEnd('/')}/${typed.ifEmpty { "…" }}",
                ),
            error = if (submitted) problem else null,
            modifier = Modifier.testTag("open-new-folder-name"),
            imeAction = ImeAction.Done,
            onTextChanged = { submitted = false },
            onSubmit = ::submit,
        )
        DialogFailure(state, stringResource(Res.string.projectFolderCreateFailedTitle))
    }
}

@Composable
private fun CloneDialog(state: OpenProjectUi, actions: OpenProjectActions) {
    if (state.dialog != OpenProjectDialog.Clone) return
    val where = state.makeIn ?: return
    val address = rememberTextFieldState()
    val folder = rememberTextFieldState()
    var folderEdited by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val url = address.text.toString().trim()
    // The folder name follows the address until the person types their own (Flutter git clone row).
    val name = if (folderEdited) folder.text.toString().trim() else suggestGitCloneFolder(url)
    val addressProblem =
        when (gitRemoteAddressProblem(url)) {
            null -> null
            GitProblem.RemoteAddressHasPassword -> stringResource(Res.string.gitProblemRemoteAddressHasPassword)
            else -> stringResource(Res.string.gitProblemInvalidRemoteUrl)
        }
    val nameProblem = if (isValidGitFolderName(name)) null else stringResource(Res.string.gitProblemInvalidFolderName)
    fun submit() {
        submitted = true
        val nowUrl = address.text.toString().trim()
        val nowName = if (folderEdited) folder.text.toString().trim() else suggestGitCloneFolder(nowUrl)
        if (gitRemoteAddressProblem(nowUrl) == null && isValidGitFolderName(nowName))
            actions.cloneRepository(nowUrl, nowName)
    }
    KitSheet(
        visible = true,
        title = stringResource(Res.string.gitCloneRow),
        onDismissRequest = actions::closeDialog,
        primary =
            KitAction(stringResource(Res.string.gitCloneButton), ::submit, icon = KitIcons.download, working = state.working),
    ) {
        KitField(
            address,
            stringResource(Res.string.gitCloneAddressLabel),
            kind = KitFieldKind.Path,
            placeholder = "https://github.com/owner/name.git",
            error = if (submitted) addressProblem else null,
            modifier = Modifier.testTag("open-clone-address"),
            imeAction = ImeAction.Next,
            onTextChanged = { submitted = false },
        )
        Spacer(Modifier.height(Space.S3.dp))
        KitField(
            folder,
            stringResource(Res.string.gitCloneFolderLabel),
            placeholder = suggestGitCloneFolder(url).ifEmpty { null },
            helper = stringResource(Res.string.gitCloneInto, "${where.trimEnd('/')}/${name.ifEmpty { "…" }}"),
            error = if (submitted) nameProblem else null,
            modifier = Modifier.testTag("open-clone-folder"),
            imeAction = ImeAction.Done,
            onTextChanged = {
                folderEdited = folder.text.isNotEmpty()
                submitted = false
            },
            onSubmit = ::submit,
        )
        DialogFailure(state, stringResource(Res.string.openProjectCloneFailed))
    }
}

/** Type a path, starting at the folder shown (Flutter "Enter a path" with startPath). */
@Composable
private fun PathDialog(state: OpenProjectUi, actions: OpenProjectActions) {
    if (state.dialog != OpenProjectDialog.Path) return
    val path = rememberTextFieldState(state.pathStart)
    var submitted by remember { mutableStateOf(false) }
    val problem =
        when (ProjectRules.pathProblem(path.text.toString().trim())) {
            PathProblem.NotAbsolute -> stringResource(Res.string.p12PathNotAbsolute)
            PathProblem.BadParts -> stringResource(Res.string.p12PathBadParts)
            PathProblem.HomeOrRoot -> stringResource(Res.string.folderBrowserHomeHere)
            null -> null
        }
    fun submit() {
        submitted = true
        val now = path.text.toString().trim()
        if (ProjectRules.pathProblem(now) == null) actions.finishPath(now)
    }
    KitSheet(
        visible = true,
        title = stringResource(Res.string.openProjectTypePath),
        onDismissRequest = { actions.finishPath(null) },
        primary = KitAction(stringResource(Res.string.projectFolderOpenAction), ::submit, icon = KitIcons.folderOpen),
    ) {
        KitField(
            path,
            stringResource(Res.string.projectFolderPathLabel),
            kind = KitFieldKind.Path,
            placeholder = stringResource(Res.string.projectFolderPathHint, "/root/projects"),
            helper = stringResource(Res.string.projectFolderOpenMessage),
            error = if (submitted) problem else null,
            modifier = Modifier.testTag("open-folder-path"),
            imeAction = ImeAction.Done,
            onTextChanged = { submitted = false },
            onSubmit = ::submit,
        )
    }
}

/** "Create this folder?" for a typed path that is not there (Flutter project_folder_actions). */
@Composable
private fun MissingDialog(state: OpenProjectUi, actions: OpenProjectActions) {
    val path = state.missingPath
    KitConfirm(
        spec =
            path?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.projectFolderMissingTitle),
                    confirmLabel = stringResource(Res.string.projectFolderCreateIt),
                    body = stringResource(Res.string.projectFolderMissing),
                    icon = KitIcons.folderAdd,
                    details =
                        listOf(
                            KitTechnicalValue(
                                stringResource(Res.string.projectFolderPathLabel),
                                LocalKitRedactor.current.details(it),
                            )
                        ),
                )
            },
        onConfirm = actions::createMissing,
        onCancel = actions::dismissMissing,
        working = state.working,
        failed = state.dialogFailure != null,
    )
}
