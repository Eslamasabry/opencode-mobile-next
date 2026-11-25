package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.BackgroundWorkResult
import io.github.eslamasabry.oc.core.model.ManagedShell
import io.github.eslamasabry.oc.core.model.ManagedShellStatus
import io.github.eslamasabry.oc.engine.transcript.turnfacts.BackgroundTask
import io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundTaskName
import io.github.eslamasabry.oc.feature.chat.presentation.FactsClock
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel
import io.github.eslamasabry.oc.feature.chat.work.RunningWork
import io.github.eslamasabry.oc.feature.chat.work.ChatWorkUi
import io.github.eslamasabry.oc.feature.chat.work.ShellOutputUi
import io.github.eslamasabry.oc.feature.chat.work.WorkTimeout
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.log.KitLogPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputOperationDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.text.kitDurationWords
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import kotlin.time.Duration.Companion.milliseconds
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.backgroundWorkNoop
import io.github.eslamasabry.oc.ui.words.res.backgroundWorkPromoted
import io.github.eslamasabry.oc.ui.words.res.chatRunShellEmpty
import io.github.eslamasabry.oc.ui.words.res.chatRunShellHelper
import io.github.eslamasabry.oc.ui.words.res.chatRunShellHint
import io.github.eslamasabry.oc.ui.words.res.chatRunShellLabel
import io.github.eslamasabry.oc.ui.words.res.chatUiRunShellCommand
import io.github.eslamasabry.oc.ui.words.res.commandRun
import io.github.eslamasabry.oc.ui.words.res.chatUiShowAllSubagentSessions
import io.github.eslamasabry.oc.ui.words.res.kitBackgroundWorkGoToStep
import io.github.eslamasabry.oc.ui.words.res.kitConfirmFailed
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.runningWorkBackgroundAction
import io.github.eslamasabry.oc.ui.words.res.runningWorkBackgroundBody
import io.github.eslamasabry.oc.ui.words.res.runningWorkBackgroundUnavailable
import io.github.eslamasabry.oc.ui.words.res.runningWorkCommandState
import io.github.eslamasabry.oc.ui.words.res.runningWorkCommandsFailed
import io.github.eslamasabry.oc.ui.words.res.runningWorkEmptyBody
import io.github.eslamasabry.oc.ui.words.res.runningWorkEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.runningWorkMenu
import io.github.eslamasabry.oc.ui.words.res.runningWorkNotReported
import io.github.eslamasabry.oc.ui.words.res.runningWorkTasksNoStop
import io.github.eslamasabry.oc.ui.words.res.runningWorkTitle
import io.github.eslamasabry.oc.ui.words.res.sessionMenuSubagents
import io.github.eslamasabry.oc.ui.words.res.shellOutputLimitTitle
import io.github.eslamasabry.oc.ui.words.res.workBackgroundPending
import io.github.eslamasabry.oc.ui.words.res.workBackgroundRequested
import io.github.eslamasabry.oc.ui.words.res.workCount
import io.github.eslamasabry.oc.ui.words.res.workExitCode
import io.github.eslamasabry.oc.ui.words.res.workFinished
import io.github.eslamasabry.oc.ui.words.res.workMoreOutput
import io.github.eslamasabry.oc.ui.words.res.workNoFinalOutput
import io.github.eslamasabry.oc.ui.words.res.workNoOutput
import io.github.eslamasabry.oc.ui.words.res.workRestarted
import io.github.eslamasabry.oc.ui.words.res.workRetry
import io.github.eslamasabry.oc.ui.words.res.workRunning
import io.github.eslamasabry.oc.ui.words.res.workStop
import io.github.eslamasabry.oc.ui.words.res.workStopDescription
import io.github.eslamasabry.oc.ui.words.res.workStopTitle
import io.github.eslamasabry.oc.ui.words.res.workStopped
import io.github.eslamasabry.oc.ui.words.res.workTimedOut
import io.github.eslamasabry.oc.ui.words.res.workTimeout
import io.github.eslamasabry.oc.ui.words.res.workTimeoutDescription
import io.github.eslamasabry.oc.ui.words.res.workTimeoutFifteenMinutes
import io.github.eslamasabry.oc.ui.words.res.workTimeoutFiveMinutes
import io.github.eslamasabry.oc.ui.words.res.workTimeoutNone
import io.github.eslamasabry.oc.ui.words.res.workTimeoutOneHour
import io.github.eslamasabry.oc.ui.words.res.workTimeoutOneMinute
import io.github.eslamasabry.oc.ui.words.res.workTrimmed
import io.github.eslamasabry.oc.ui.words.res.workUnavailable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The conversation's "…" entry to its running work (owner 2026-11-14: "where is it?"): always there where the chat
 * has the sheet ([offered]), with the count while anything runs besides the reply; the sheet says when nothing runs
 * or when the server does not report it.
 */
@Composable
internal fun runningWorkAction(offered: Boolean, running: Int, onOpen: () -> Unit): KitLineAction? {
    if (!offered) return null
    return KitLineAction(
        if (running > 0) stringResource(Res.string.workCount, running) else stringResource(Res.string.runningWorkMenu),
        onOpen,
        testTag = "chat-work-open",
        icon = KitIcons.branch,
    )
}

/**
 * Flutter `running_work_sheet.dart` for the commands this conversation started: the list, one
 * command's output with Stop command and Change timeout, and the "Run shell command" dialog. The
 * server's text appears only in the masked log; every other sentence is the app's own.
 */
@Suppress("LongParameterList")
@Composable
internal fun ChatWorkSheet(
    state: ChatWorkUi,
    visible: Boolean,
    model: ChatWorkModel,
    onDismiss: () -> Unit,
    /** Work the agent's stream says goes on in the background (Claude Code's background commands), oldest first. */
    tasks: List<BackgroundTask> = emptyList(),
    clock: FactsClock = FactsClock.Still,
    /** The server keeps sub-agent conversations: the sheet links to them. */
    subagents: Boolean = false,
    onShowSubagents: (() -> Unit)? = null,
    /** Go to the step that started [BackgroundTask] (the sheet closes first). */
    onShowTask: (BackgroundTask) -> Unit = {},
) {
    val output = state.output
    val workTasks = WorkListTasks(tasks, clock, onShowTask)
    KitSheet(
        visible = visible,
        onDismissRequest = { if (output != null) model.closeOutput() else onDismiss() },
        title =
            output?.shell?.command?.lineSequence()?.firstOrNull().orEmpty().ifBlank {
                stringResource(Res.string.runningWorkTitle)
            },
        subtitle = output?.shell?.let { stateWords(it) },
    ) {
        Column(
            Modifier.fillMaxWidth().testTag("chat-work-sheet"),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.actionFailed)
                KitNotice(
                    message = stringResource(Res.string.kitConfirmFailed),
                    tone = StatusTone.Failure,
                    dismiss =
                        KitLineAction(
                            stringResource(Res.string.kitSheetDismiss),
                            model::dismissActionFailure,
                        ),
                )
            if (output == null) WorkList(state, model, workTasks, subagents, onShowSubagents)
            else ShellOutput(output, state, model)
        }
    }
    KitConfirm(
        spec =
            state.confirmStop?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.workStopTitle),
                    confirmLabel = stringResource(Res.string.workStop),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.workStopDescription),
                )
            },
        onConfirm = model::confirmStop,
        onCancel = { model.askStop(null) },
    )
}

private class WorkListTasks(
    val tasks: List<BackgroundTask>,
    val clock: FactsClock,
    val onShowTask: (BackgroundTask) -> Unit,
) {
    @Composable
    fun TaskRows() {
        val nowMs = clock.read()
        KitRowGroup {
            tasks.forEach { task ->
                KitRow(
                    title = task.name,
                    supporting = stringResource(Res.string.kitBackgroundWorkGoToStep),
                    meta = elapsedWords(task.sinceMs, nowMs),
                    leading = KitRowLeading.Glyph(KitIcons.terminal),
                    modifier = Modifier.testTag("chat-work-task-${task.callId}"),
                    onClick = { onShowTask(task) },
                )
            }
        }
        // The stream reports this work but the server has no way to stop one piece of it on its own.
        KitNotice(
            message = stringResource(Res.string.runningWorkTasksNoStop),
            tone = StatusTone.Neutral,
            modifier = Modifier.testTag("chat-work-tasks-no-stop"),
        )
    }

    @Composable
    fun ShellRows(state: ChatWorkUi, model: ChatWorkModel) {
        when {
            !state.supported ->
                if (tasks.isEmpty()) EmptyWorkState()
            state.failed && state.shells.isEmpty() ->
                KitNotice(
                    message = stringResource(Res.string.runningWorkCommandsFailed),
                    tone = StatusTone.Failure,
                    actions = listOf(KitLineAction(stringResource(Res.string.workRetry), model::refresh)),
                )
            state.loading && state.shells.isEmpty() ->
                KitNotice(message = stringResource(Res.string.workRunning), tone = StatusTone.Progress)
            state.shells.isEmpty() ->
                if (tasks.isEmpty()) EmptyWorkState()
            else ->
                KitRowGroup {
                    state.shells.forEach { shell ->
                        // A plain name on the row; the command itself is the opened row's title.
                        KitRow(
                            title = backgroundTaskName(shell.command),
                            supporting = stringResource(Res.string.runningWorkCommandState, stateWords(shell)),
                            leading = KitRowLeading.Glyph(KitIcons.terminal),
                            modifier = Modifier.testTag("chat-work-row-${shell.id}"),
                            onClick = { model.openOutput(shell.id) },
                        )
                    }
                }
        }
    }

    @Composable
    private fun EmptyWorkState() {
        KitStateView(
            kind = KitStateKind.Empty,
            title = stringResource(Res.string.runningWorkEmptyTitle),
            body = stringResource(Res.string.runningWorkEmptyBody),
            icon = KitIcons.terminal,
            modifier = Modifier.testTag("chat-work-empty"),
        )
    }
}

/**
 * The list: what runs (the stream's background work, then the server's commands), the way to the sub-agents, then
 * what the server can do with a running reply. Each place stays and says so where this server cannot do it.
 */
@Composable
private fun WorkList(
    state: ChatWorkUi,
    model: ChatWorkModel,
    workTasks: WorkListTasks,
    subagents: Boolean,
    onShowSubagents: (() -> Unit)?,
) {
    if (!RunningWork.reported(state.supported, subagents, workTasks.tasks))
        KitNotice(
            message = stringResource(Res.string.runningWorkNotReported),
            tone = StatusTone.Neutral,
            modifier = Modifier.testTag("chat-work-not-reported"),
        )
    else WorkListRows(state, model, workTasks)
    if (subagents && onShowSubagents != null)
        KitRowGroup {
            KitRow(
                title = stringResource(Res.string.sessionMenuSubagents),
                supporting = stringResource(Res.string.chatUiShowAllSubagentSessions),
                leading = KitRowLeading.Glyph(KitIcons.branch),
                modifier = Modifier.testTag("chat-work-subagents"),
                onClick = onShowSubagents,
            )
        }
    BackgroundNotice(state, model)
}

/** Flutter `running-work-background`: what moving the work frees, then the act, then what the server said. */
@Composable
private fun BackgroundNotice(state: ChatWorkUi, model: ChatWorkModel) {
    if (state.backgroundSupported == false) {
        KitNotice(
            message = stringResource(Res.string.runningWorkBackgroundUnavailable),
            tone = StatusTone.Neutral,
            modifier = Modifier.testTag("running-work-background-unavailable"),
        )
        return
    }
    if (!state.backgroundNotice) return
    val result = state.backgroundResult
    if (result == null)
        KitNotice(
            message = stringResource(Res.string.runningWorkBackgroundBody),
            tone = StatusTone.Neutral,
            actions =
                listOf(
                    KitLineAction(
                        stringResource(
                            if (state.backgrounding) Res.string.workBackgroundPending
                            else Res.string.runningWorkBackgroundAction
                        ),
                        { if (state.canBackground && !state.backgrounding) model.runInBackground() },
                    )
                ),
            modifier = Modifier.testTag("running-work-background"),
        )
    else
        KitNotice(
            message =
                stringResource(
                    when (result) {
                        BackgroundWorkResult.Promoted -> Res.string.backgroundWorkPromoted
                        BackgroundWorkResult.Unchanged -> Res.string.backgroundWorkNoop
                        BackgroundWorkResult.Requested -> Res.string.workBackgroundRequested
                    }
                ),
            tone = StatusTone.Ok,
            modifier = Modifier.testTag("running-work-background"),
        )
}

@Composable
private fun WorkListRows(
    state: ChatWorkUi,
    model: ChatWorkModel,
    workTasks: WorkListTasks,
) {
    if (workTasks.tasks.isNotEmpty()) {
        workTasks.TaskRows()
        if (state.shells.isEmpty()) return
    }
    workTasks.ShellRows(state, model)
}

/** "12 min", "1 h 5 min", "under a minute": how long work started at [sinceMs] has run. */
@Composable
private fun elapsedWords(sinceMs: Long, nowMs: Long): String =
    kitDurationWords((nowMs - sinceMs).coerceAtLeast(0L).milliseconds)

private const val MINUTE_MS = 60_000L
private const val HOUR_MINUTES = 60

@Composable
private fun stateWords(shell: ManagedShell): String =
    stringResource(
        when (shell.status) {
            ManagedShellStatus.Running -> Res.string.workRunning
            ManagedShellStatus.Timeout -> Res.string.workTimedOut
            ManagedShellStatus.Killed -> Res.string.workStopped
            ManagedShellStatus.Exited,
            ManagedShellStatus.Unknown -> Res.string.workFinished
        }
    )

@Composable
private fun ShellOutput(output: ShellOutputUi, state: ChatWorkUi, model: ChatWorkModel) {
    val mask = rememberKitMask()
    val running = output.shell.status == ManagedShellStatus.Running && !output.gone
    if (output.shell.status == ManagedShellStatus.Exited)
        output.shell.exitCode?.let {
            KitNotice(
                message = stringResource(Res.string.workExitCode, it),
                tone = StatusTone.Neutral,
            )
        }
    if (output.gone)
        KitNotice(message = stringResource(Res.string.workRestarted), tone = StatusTone.Attention)
    else if (output.failed)
        KitNotice(
            message = stringResource(Res.string.workUnavailable),
            tone = StatusTone.Failure,
            actions =
                listOf(KitLineAction(stringResource(Res.string.workRetry), model::refreshOutput)),
        )
    if (output.truncated)
        KitNotice(message = stringResource(Res.string.workTrimmed), tone = StatusTone.Neutral)
    if (running) {
        // Two acts kept apart (Flutter KitActionStack): changing the limit is ordinary, Stop ends it.
        KitButton(
            stringResource(Res.string.workTimeout),
            { model.chooseTimeout(output.shell.id) },
            role = KitButtonRole.Secondary,
            icon = KitIcons.clock,
            modifier = Modifier.testTag("shell-output-limit"),
        )
        if (state.timeoutFor == output.shell.id) {
            KitNotice(
                title = stringResource(Res.string.shellOutputLimitTitle),
                message = stringResource(Res.string.workTimeoutDescription),
                tone = StatusTone.Neutral,
            )
            KitRowGroup {
                WorkTimeout.entries.forEach { choice ->
                    KitRow(
                        title = stringResource(timeoutWords(choice)),
                        modifier = Modifier.testTag("shell-timeout-${choice.name}"),
                        onClick = { model.setTimeout(choice) },
                    )
                }
            }
        }
        KitButton(
            stringResource(Res.string.workStop),
            { model.askStop(output.shell.id) },
            role = KitButtonRole.Secondary,
            icon = KitIcons.stopCircle,
            destructive = true,
            modifier = Modifier.testTag("shell-output-stop"),
        )
    }
    if (output.text.isEmpty() && !output.loading && !output.failed && !output.gone)
        KitNotice(
            message =
                stringResource(
                    if (running) Res.string.workNoOutput else Res.string.workNoFinalOutput
                ),
            tone = StatusTone.Neutral,
        )
    else if (output.text.isNotEmpty())
        KitLogPanel(
            text = output.text,
            mask = mask,
            onRefresh = model::refreshOutput,
            modifier = Modifier.testTag("shell-output-text"),
        )
    if (output.truncated && !output.gone)
        KitButton(
            stringResource(Res.string.workMoreOutput),
            model::refreshOutput,
            role = KitButtonRole.Tertiary,
            modifier = Modifier.testTag("shell-output-more"),
        )
}

private fun timeoutWords(choice: WorkTimeout) =
    when (choice) {
        WorkTimeout.OneMinute -> Res.string.workTimeoutOneMinute
        WorkTimeout.FiveMinutes -> Res.string.workTimeoutFiveMinutes
        WorkTimeout.FifteenMinutes -> Res.string.workTimeoutFifteenMinutes
        WorkTimeout.OneHour -> Res.string.workTimeoutOneHour
        WorkTimeout.None -> Res.string.workTimeoutNone
    }

/** "Run shell command" (map chat-run-shell-dialog): the command stays when it fails. */
@Composable
internal fun ChatShellDialog(state: ChatWorkUi, model: ChatWorkModel) {
    val dialog = state.dialog
    KitInputOperationDialog(
        visible = dialog != null,
        title = stringResource(Res.string.chatUiRunShellCommand),
        fieldLabel = stringResource(Res.string.chatRunShellLabel),
        confirmLabel = stringResource(Res.string.commandRun),
        initial = dialog?.initial.orEmpty(),
        onSubmit = model::runShell,
        onDismissRequest = model::closeShellDialog,
        hint = stringResource(Res.string.chatRunShellHint),
        helper = stringResource(Res.string.chatRunShellHelper),
        error =
            when {
                dialog?.empty == true -> stringResource(Res.string.chatRunShellEmpty)
                dialog?.failed == true -> stringResource(Res.string.kitConfirmFailed)
                else -> null
            },
        working = dialog?.running == true,
    )
}
