package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
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
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            if (state.busy) item(key = "loading") { KitSkeletonRows(count = 2) }
            state.failure?.let { failure ->
                item(key = "failure") {
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
                item(key = "copy-inspection") { WorktreeInspectionPane(state, actions) }
            state.health?.let { health ->
                item(key = "checkout-health") {
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
                item(key = "comparison") {
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
                    item(key = "diff-back") {
                        KitButton(
                            stringResource(Res.string.agentProjectAllFiles),
                            actions::closeWorkspaceDiff,
                            role = KitButtonRole.Tertiary,
                        )
                        KitText(selected.file)
                    }
                    item(key = "diff") {
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
                    item(key = "diff-pages") {
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
                    item(key = "changed-files") {
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
                item(key = "worktrees") {
                    KitRowGroup(label = stringResource(Res.string.agentProjectWorktrees)) {
                        if (!state.busy && state.failure == null && state.worktrees.isEmpty())
                            KitText(stringResource(Res.string.agentProjectNoWorktrees))
                        for (tree in state.worktrees) KitRow(
                            title = tree.name,
                            supporting =
                                listOfNotNull(
                                        tree.branch,
                                        tree.directory,
                                        stringResource(Res.string.agentProjectReady).takeIf {
                                            tree.ready
                                        },
                                    )
                                    .joinToString(" · "),
                            supportingMaxLines = 3,
                            trailing =
                                KitRowTrailing.Action(
                                    KitLineAction(
                                        label = stringResource(Res.string.agentProjectRemove),
                                        onClick = {
                                            actions.inspectWorktreeRemoval(tree.directory)
                                        },
                                        enabled = !state.busy,
                                        disabledReason =
                                            stringResource(Res.string.agentProjectWorking),
                                    )
                                ),
                        )
                    }
                }
                if (state.canCreate)
                    item(key = "create-worktree") {
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
            item(key = "refresh") {
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
    val removing =
        state.removal?.takeIf { state.visible && state.removalChanges != null && !state.busy }
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
    )
}
