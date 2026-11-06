package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.core.model.WorktreeInfo
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceUi
import io.github.eslamasabry.oc.feature.projects.presentation.validWorktreeName
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Visible project checkout/worktree actions, arranged exclusively from the shared kit. */
@Composable
internal fun ProjectWorkspaceSheet(state: ProjectWorkspaceUi, actions: ProjectWorkspaceActions) {
    KitSheet(
        visible = state.visible,
        title = stringResource(Res.string.agentProjectWorkspace),
        closeLabel = stringResource(Res.string.kitSheetClose),
        onDismissRequest = actions::closeWorkspace,
        height = KitSheetHeight.Full,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            if (state.busy) WorkspaceSection("loading") { KitSkeletonRows(count = 2) }
            state.failure?.let { failure ->
                WorkspaceSection("failure") {
                    KitErrorNotice(
                        message = stringResource(Res.string.agentProjectReadFailed),
                        failure = failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        retry =
                            KitLineAction(
                                stringResource(Res.string.kitTryAgain),
                                actions::retryWorkspace,
                            ),
                    )
                }
            }
            if (state.canInspect)
                WorkspaceSection("copy-inspection") { WorktreeInspectionPane(state, actions) }
            state.health?.let { health ->
                WorkspaceSection("checkout-health") {
                    if (health.setupState == VersionControlSetupState.Absent)
                        KitNotice(stringResource(Res.string.agentProjectNoGit))
                    else {
                        KitRow(
                            title = stringResource(Res.string.agentProjectBranch),
                            supporting =
                                health.branch ?: stringResource(Res.string.agentProjectDetached),
                        )
                        health.defaultBranch?.let {
                            KitRow(
                                title = stringResource(Res.string.agentProjectBaseBranch),
                                supporting = it,
                            )
                        }
                    }
                }
            }
            if (state.canReadChanges) {
                WorkspaceSection("comparison") {
                    KitChipWrap {
                        for (mode in VcsDiffMode.entries) KitChip(
                            label =
                                stringResource(
                                    if (mode == VcsDiffMode.WorkingTree)
                                        Res.string.agentProjectWorkingTree
                                    else Res.string.agentProjectBranchChanges
                                ),
                            kind = KitChipKind.Action,
                            tone =
                                if (state.mode == mode) KitChipTone.Active else KitChipTone.Neutral,
                            selected = state.mode == mode,
                            onClick = { actions.chooseChanges(mode) },
                        )
                    }
                }
                val selected = state.changes.firstOrNull { it.file == state.selectedDiff }
                if (selected != null) {
                    WorkspaceSection("diff-back") {
                        KitButton(
                            stringResource(Res.string.agentProjectAllFiles),
                            actions::closeWorkspaceDiff,
                            role = KitButtonRole.Tertiary,
                        )
                        KitText(selected.file)
                    }
                    WorkspaceSection("diff") {
                        val text = selected.patch.orEmpty()
                        if (text.isEmpty())
                            KitNotice(stringResource(Res.string.agentProjectNoPatch))
                        else
                            KitCodeBlock(
                                source =
                                    text
                                        .lineSequence()
                                        .drop(state.diffPage * 200)
                                        .take(200)
                                        .joinToString("\n"),
                                kind = KitCodeKind.Output,
                                maxLines = 200,
                            )
                    }
                    WorkspaceSection("diff-pages") {
                        if (state.diffPage > 0)
                            KitButton(
                                stringResource(Res.string.agentProjectPreviousLines),
                                { actions.workspaceDiffPage(-1) },
                                role = KitButtonRole.Tertiary,
                            )
                        if (
                            selected.patch
                                .orEmpty()
                                .trimEnd('\n')
                                .lineSequence()
                                .drop((state.diffPage + 1) * 200)
                                .any()
                        )
                            KitButton(
                                stringResource(Res.string.agentProjectNextLines),
                                { actions.workspaceDiffPage(1) },
                                role = KitButtonRole.Tertiary,
                            )
                    }
                } else {
                    WorkspaceSection("changed-files") {
                        KitRowGroup(label = stringResource(Res.string.agentProjectChangedFiles)) {
                            if (!state.busy && state.failure == null && state.changes.isEmpty())
                                KitText(stringResource(Res.string.agentProjectNoChanges))
                            for (file in state.changes) KitRow(
                                title = file.file,
                                titleIsFileName = true,
                                supporting =
                                    stringResource(
                                        Res.string.agentProjectLineChanges,
                                        file.additions ?: 0,
                                        file.deletions ?: 0,
                                    ),
                                trailing = KitRowTrailing.Chevron,
                                onClick = { actions.openWorkspaceDiff(file.file) },
                            )
                        }
                    }
                }
            }
            if (state.canReadWorktrees) {
                WorkspaceSection("worktrees") {
                    KitRowGroup(label = stringResource(Res.string.agentProjectWorktrees)) {
                        if (!state.busy && state.failure == null && state.worktrees.isEmpty())
                            KitText(stringResource(Res.string.agentProjectNoWorktrees))
                        state.resetDone?.let {
                            KitNotice(stringResource(Res.string.e7LibraryResetToTheDefaultBranch, it))
                        }
                        for (tree in state.worktrees) WorktreeRow(tree, state, actions)
                    }
                }
                if (state.canCreate)
                    WorkspaceSection("create-worktree") {
                        val enabled =
                            if (state.busy)
                                KitEnabled.Disabled(stringResource(Res.string.agentProjectWorking))
                            else KitEnabled.Enabled
                        KitButton(
                            stringResource(Res.string.agentProjectNewWorktree),
                            actions::newNamedWorktree,
                            enabled = enabled,
                        )
                        KitButton(
                            stringResource(Res.string.agentProjectGenerateWorktree),
                            actions::createGeneratedWorktree,
                            role = KitButtonRole.Tertiary,
                            enabled = enabled,
                        )
                    }
            }
            WorkspaceSection("refresh") {
                KitButton(
                    stringResource(Res.string.agentProjectRefresh),
                    actions::retryWorkspace,
                    role = KitButtonRole.Tertiary,
                    working = state.busy,
                )
            }
        }
    }
    val invalidName = stringResource(Res.string.agentProjectNameInvalid)
    KitInputDialog(
        visible = state.visible && state.askingName,
        title = stringResource(Res.string.agentProjectNewWorktree),
        fieldLabel = stringResource(Res.string.agentProjectWorktreeName),
        confirmLabel = stringResource(Res.string.agentProjectCreate),
        validate = { if (validWorktreeName(it)) null else invalidName },
        operationKey = "project-worktree-name",
        onResult = actions::finishWorktreeName,
    )
    val resetting = state.resetting?.takeIf { state.visible && state.resetChanges != null }
    KitConfirm(
        spec =
            resetting?.let { tree ->
                val changes = state.resetChanges!!
                KitConfirmSpec(
                    title = stringResource(Res.string.e7LibraryReset, tree.name),
                    confirmLabel = stringResource(Res.string.e7LibraryResetWorktree),
                    kind = KitConfirmKind.Destructive,
                    body =
                        stringResource(
                            Res.string.e7LibraryThisPermanentlyDiscardsTrackedChangesAndDeletes
                        ),
                    icon = KitIcons.restart,
                    consequences =
                        if (changes.isEmpty()) emptyList()
                        else
                            listOf(
                                pluralStringResource(
                                    Res.plurals.e7LibraryChangedFilesDetected,
                                    changes.size,
                                    changes.size,
                                )
                            ) + changes.map { it.path },
                    // Typed name only when changes would be lost (Flutter worktrees_screen).
                    typedName = tree.name.takeIf { changes.isNotEmpty() },
                    details =
                        listOf(
                            KitTechnicalValue(stringResource(Res.string.worktreesFolder), tree.directory)
                        ),
                )
            },
        onConfirm = actions::confirmWorktreeReset,
        onCancel = actions::cancelWorktreeReset,
        working = state.busy,
    )
    val removing = state.removal?.takeIf { state.visible && state.removalChanges != null }
    KitConfirm(
        spec =
            removing?.let { tree ->
                KitConfirmSpec(
                    title = stringResource(Res.string.agentProjectRemoveQuestion, tree.name),
                    confirmLabel = stringResource(Res.string.agentProjectRemove),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.agentProjectRemoveBody, tree.directory),
                    consequences =
                        listOf(
                            stringResource(
                                Res.string.agentProjectRemoveChanges,
                                state.removalChanges!!.size,
                            )
                        ) + state.removalChanges.map { it.path },
                    typedName = tree.name,
                )
            },
        onConfirm = actions::confirmWorktreeRemoval,
        onCancel = actions::cancelWorktreeRemoval,
        working = state.busy,
    )
}

/**
 * One worktree. A tap opens its actions (Open, Start in it is the project page, Copy folder path,
 * Reset); Remove stays the visible row action.
 */
@Composable
private fun WorktreeRow(
    tree: WorktreeInfo,
    state: ProjectWorkspaceUi,
    actions: ProjectWorkspaceActions,
) {
    var open by remember(tree.directory) { mutableStateOf(false) }
    val copy =
        rememberKitCopyAction(
            stringResource(Res.string.worktreesCopyFolder),
            stringResource(Res.string.s40iCopied),
            { tree.directory },
        )
    val working = stringResource(Res.string.agentProjectWorking)
    val held = if (state.busy) KitEnabled.Disabled(working) else KitEnabled.Enabled
    val menu = buildList {
        add(
            KitMenuItem(
                stringResource(Res.string.globalSessionsOpen),
                { actions.openWorktree(tree.directory) },
                icon = KitIcons.externalLink,
                enabled = held,
            )
        )
        add(
            KitMenuItem(
                stringResource(Res.string.worktreesCopyFolder),
                { copy.onClick?.invoke() },
                icon = KitIcons.copy,
            )
        )
        if (state.canReset && tree.ready)
            add(
                KitMenuItem(
                    stringResource(Res.string.e7LibraryReset2),
                    { actions.inspectWorktreeReset(tree.directory) },
                    icon = KitIcons.restart,
                    destructive = true,
                    enabled = held,
                )
            )
    }
    KitMenu(
        expanded = open,
        items = menu,
        title = stringResource(Res.string.e7LibraryWorktreeActions),
        onDismissRequest = { open = false },
    ) {
        KitRow(
            title = tree.name,
            leading = KitRowLeading.Glyph(KitIcons.branch),
            supporting =
                listOfNotNull(
                        tree.branch,
                        tree.directory,
                        stringResource(Res.string.agentProjectReady).takeIf { tree.ready },
                    )
                    .joinToString(" · "),
            supportingMaxLines = 3,
            onClickLabel = stringResource(Res.string.e7LibraryWorktreeActions),
            onClick = { open = true },
            trailing =
                KitRowTrailing.Action(
                    KitLineAction(
                        label = stringResource(Res.string.agentProjectRemove),
                        onClick = { actions.inspectWorktreeRemoval(tree.directory) },
                        enabled = !state.busy,
                        disabledReason = working,
                    )
                ),
        )
    }
}

/** Retains each sheet section's composition identity without adding another scrolling container. */
@Composable
private fun WorkspaceSection(id: String, content: @Composable ColumnScope.() -> Unit) {
    key(id) { Column(content = content) }
}
