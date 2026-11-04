package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectWorkspaceUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** An unknown read never becomes a clean copy or an exhaustive conversation count. */
@Composable
internal fun WorktreeInspectionPane(state: ProjectWorkspaceUi, actions: ProjectWorkspaceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitGroupNote(stringResource(Res.string.worktreeInspectionTitle))
        KitGroupNote(stringResource(Res.string.worktreeInspectionExactScope))
        if (state.inspectionBusy) KitSkeletonRows(count = 2)
        state.inspectionFailure?.let { failure ->
            KitErrorNotice(
                message = stringResource(Res.string.worktreeInspectionReadFailed),
                failure = failure,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                retry =
                    KitLineAction(
                        stringResource(Res.string.kitTryAgain),
                        {
                            state.inspectionScope?.let { actions.inspectCopy(it.directory) }
                                ?: actions.retryWorkspace()
                        },
                    ),
            )
        }
        KitChipWrap {
            for (copy in state.inspectionScopes) KitChip(
                label =
                    copy.directory.trimEnd('/').substringAfterLast('/').ifEmpty { copy.directory },
                kind = KitChipKind.Action,
                tone =
                    if (copy == state.inspectionScope) KitChipTone.Active else KitChipTone.Neutral,
                selected = copy == state.inspectionScope,
                onClick = { actions.inspectCopy(copy.directory) },
            )
        }
        state.inspectionScope?.let { selected ->
            KitText(selected.directory)
            KitGroupNote(
                selected.workspaceId?.let {
                    stringResource(Res.string.worktreeInspectionWorkspace, it)
                } ?: stringResource(Res.string.worktreeInspectionHostScope)
            )
            state.inspectionChanges?.let { files ->
                KitRowGroup(label = stringResource(Res.string.agentProjectChangedFiles)) {
                    if (files.isEmpty() && !state.inspectionBusy && state.inspectionFailure == null)
                        KitText(stringResource(Res.string.agentProjectNoChanges))
                    for (file in files) KitRow(
                        title = file.path,
                        titleIsFileName = true,
                        supporting =
                            stringResource(
                                Res.string.agentProjectLineChanges,
                                file.additions,
                                file.deletions,
                            ),
                    )
                }
            }
            KitRowGroup(label = stringResource(Res.string.worktreeInspectionConversations)) {
                if (
                    state.inspectionSessions.isEmpty() &&
                        !state.inspectionBusy &&
                        state.inspectionFailure == null
                )
                    KitText(
                        stringResource(
                            if (state.inspectionCursor != null)
                                Res.string.worktreeInspectionNoPageMatches
                            else Res.string.worktreeInspectionNoConversations
                        )
                    )
                for (session in state.inspectionSessions) KitRow(
                    title =
                        session.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.workspaceNewSession),
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.openCopyConversation(session.id.raw) },
                )
            }
            if (state.inspectionCursor != null)
                KitButton(
                    stringResource(Res.string.globalSessionsLoadMore),
                    actions::loadMoreCopyConversations,
                    role = KitButtonRole.Secondary,
                    working = state.inspectionBusy,
                )
        }
    }
}
