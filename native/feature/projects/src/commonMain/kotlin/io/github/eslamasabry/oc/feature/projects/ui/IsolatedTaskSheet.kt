package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskPhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageUi
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.log.KitSelectableText
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

/** The visible copy-task flow uses existing kit parts; closing only stops waiting. */
@Composable
internal fun IsolatedTaskSheet(state: ProjectPageUi, actions: ProjectPageActions) {
    var options by remember { mutableStateOf(false) }
    val task = state.isolated
    val phase = task?.phase
    val name = task?.worktree?.name.orEmpty()
    KitSheet(
        visible = state.isolatedShown,
        onDismissRequest = actions::closeIsolatedTask,
        title = stringResource(Res.string.isolatedTaskTitle),
        subtitle = state.name,
        height = KitSheetHeight.Full,
        dismissible = task?.busy != true,
        draft = true,
    ) {
        if (state.isolatedLoading) KitSkeletonRows()
        state.isolatedFailure?.let {
            KitErrorNotice(stringResource(Res.string.isolatedTaskScopeChanged), failure = it)
        }
        if (phase == IsolatedTaskPhase.Form || phase == IsolatedTaskPhase.CreateFailed) {
            val text = remember(task) { TextFieldState(state.isolatedTask) }
            val copyName = remember(task) { TextFieldState(state.isolatedName) }
            LaunchedEffect(text) {
                snapshotFlow { text.text.toString() }.drop(1).collect(actions::isolatedDraftChanged)
            }
            LaunchedEffect(copyName) {
                snapshotFlow { copyName.text.toString() }
                    .drop(1)
                    .collect(actions::isolatedNameChanged)
            }
            KitText(stringResource(Res.string.isolatedTaskIntro), role = KitTextRole.Supporting)
            KitField(
                state = text,
                label = stringResource(Res.string.isolatedTaskPromptLabel),
                helper = stringResource(Res.string.isolatedTaskPromptHelper),
                kind = KitFieldKind.Multiline,
            )
            KitExpandRow(
                title = stringResource(Res.string.isolatedTaskOptions),
                expanded = options,
                onExpandedChange = { options = it },
            ) {
                KitField(
                    state = copyName,
                    label = stringResource(Res.string.isolatedTaskNameLabel),
                    helper = stringResource(Res.string.isolatedTaskNameHelper),
                )
            }
            if (state.isolatedDraftFailed)
                KitErrorNotice(stringResource(Res.string.p12NewDraftFailed))
            KitButton(stringResource(Res.string.isolatedTaskStart), actions::createIsolatedTask)
        } else if (phase != null) {
            val heading =
                when (phase) {
                    IsolatedTaskPhase.Creating -> stringResource(Res.string.isolatedTaskCreating)
                    IsolatedTaskPhase.Preparing ->
                        stringResource(Res.string.isolatedTaskPreparing, name)
                    IsolatedTaskPhase.Unconfirmed ->
                        stringResource(Res.string.isolatedTaskUnconfirmed, name)
                    IsolatedTaskPhase.SetupFailed ->
                        stringResource(Res.string.isolatedTaskFailed, name)
                    IsolatedTaskPhase.CreateUncertain ->
                        stringResource(Res.string.isolatedTaskCreateUnknown)
                    IsolatedTaskPhase.OpenUncertain ->
                        stringResource(Res.string.isolatedTaskOpenUnknown)
                    IsolatedTaskPhase.Opening ->
                        stringResource(Res.string.isolatedTaskOpening, name)
                    IsolatedTaskPhase.OpenFailed ->
                        stringResource(Res.string.isolatedTaskReadyIdle, name)
                    IsolatedTaskPhase.Sending ->
                        stringResource(Res.string.isolatedTaskSending, name)
                    IsolatedTaskPhase.Unsent -> stringResource(Res.string.isolatedTaskSendFailed)
                    IsolatedTaskPhase.Opened -> stringResource(Res.string.isolatedTaskOpened, name)
                    IsolatedTaskPhase.Cancelled -> stringResource(Res.string.isolatedTaskCancelled)
                    IsolatedTaskPhase.Removed ->
                        stringResource(Res.string.isolatedTaskRemoved, name)
                    IsolatedTaskPhase.ScopeChanged ->
                        stringResource(Res.string.isolatedTaskScopeChanged)
                    IsolatedTaskPhase.Form,
                    IsolatedTaskPhase.CreateFailed ->
                        stringResource(Res.string.isolatedTaskCreateFailed)
                }
            KitNotice(heading)
            if (
                phase in
                    setOf(
                        IsolatedTaskPhase.Creating,
                        IsolatedTaskPhase.Preparing,
                        IsolatedTaskPhase.Opening,
                        IsolatedTaskPhase.Sending,
                    )
            ) {
                val step =
                    when (phase) {
                        IsolatedTaskPhase.Creating -> 1
                        IsolatedTaskPhase.Preparing -> 2
                        else -> 3
                    }
                KitText(
                    stringResource(Res.string.isolatedTaskProgress, step),
                    role = KitTextRole.Supporting,
                )
                KitText(
                    stringResource(Res.string.isolatedTaskUsually),
                    role = KitTextRole.Supporting,
                )
            }
            when (phase) {
                IsolatedTaskPhase.Creating ->
                    KitText(stringResource(Res.string.isolatedTaskCreatingHint))
                IsolatedTaskPhase.Preparing ->
                    KitText(stringResource(Res.string.isolatedTaskPreparingHint))
                IsolatedTaskPhase.Unconfirmed -> {
                    KitText(stringResource(Res.string.isolatedTaskUnconfirmedHint))
                    KitButton(
                        stringResource(Res.string.isolatedTaskKeepWaiting),
                        actions::keepWaitingForCopy,
                    )
                    KitButton(
                        stringResource(Res.string.isolatedTaskStartAnyway),
                        actions::openCopyAnyway,
                        role = KitButtonRole.Secondary,
                    )
                }
                IsolatedTaskPhase.SetupFailed -> {
                    KitText(stringResource(Res.string.isolatedTaskFailedBody))
                    KitButton(
                        stringResource(Res.string.isolatedTaskStartAnyway),
                        actions::openCopyAnyway,
                    )
                    KitButton(
                        stringResource(Res.string.isolatedTaskRemove),
                        actions::requestRemoveCopy,
                        role = KitButtonRole.Secondary,
                    )
                }
                IsolatedTaskPhase.OpenFailed ->
                    KitButton(
                        stringResource(Res.string.isolatedTaskRetryOpen),
                        actions::openCopyAnyway,
                    )
                IsolatedTaskPhase.Unsent -> {
                    KitText(
                        stringResource(
                            if (task.sendUncertain) Res.string.isolatedTaskSendUnconfirmed
                            else if (task.draftKept) Res.string.isolatedTaskSendFailedBody
                            else Res.string.isolatedTaskSendFailedLost
                        )
                    )
                    if (!task.draftKept) KitSelectableText(state.isolatedTask)
                    KitButton(
                        stringResource(Res.string.isolatedTaskOpenConversation),
                        actions::openUnsentConversation,
                    )
                }
                IsolatedTaskPhase.OpenUncertain ->
                    if (task.ref != null)
                        KitButton(
                            stringResource(Res.string.isolatedTaskOpenConversation),
                            actions::openUnsentConversation,
                        )
                else -> Unit
            }
        }
        task?.failure?.let {
            KitErrorNotice(stringResource(Res.string.isolatedTaskOperationFailed), failure = it)
        }
        KitDetailsFold(
            label = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            text = state.isolatedDetails,
        )
        if (task?.busy != true)
            KitButton(
                stringResource(
                    if (
                        phase == IsolatedTaskPhase.Creating ||
                            phase == IsolatedTaskPhase.Preparing ||
                            phase == IsolatedTaskPhase.Unconfirmed
                    )
                        Res.string.isolatedTaskStopWaiting
                    else Res.string.isolatedTaskClose
                ),
                actions::closeIsolatedTask,
                role = KitButtonRole.Tertiary,
            )
    }
    KitConfirm(
        spec =
            if (state.confirmRemoveCopy)
                KitConfirmSpec(
                    title = stringResource(Res.string.isolatedTaskRemoveTitle, name),
                    confirmLabel = stringResource(Res.string.isolatedTaskRemove),
                    body = stringResource(Res.string.isolatedTaskRemoveBody),
                    kind = KitConfirmKind.Destructive,
                )
            else null,
        onConfirm = { actions.confirmRemoveCopy(true) },
        onCancel = { actions.confirmRemoveCopy(false) },
    )
}
