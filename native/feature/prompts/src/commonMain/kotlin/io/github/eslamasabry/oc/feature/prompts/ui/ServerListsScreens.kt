package io.github.eslamasabry.oc.feature.prompts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.integrations.catalog.SessionSkillFailure
import io.github.eslamasabry.oc.engine.integrations.catalog.SkillActivation
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandsActions
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandsUi
import io.github.eslamasabry.oc.feature.prompts.presentation.ListPhase
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskItem
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskPriority
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskRules
import io.github.eslamasabry.oc.feature.prompts.presentation.TaskState
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksActions
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiSlashCommandsAndAgents
import io.github.eslamasabry.oc.ui.words.res.chatUiTodos
import io.github.eslamasabry.oc.ui.words.res.externalAgentsNoTasksTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapNotOnHost
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.mobileTaskCancelled
import io.github.eslamasabry.oc.ui.words.res.mobileTaskCompleted
import io.github.eslamasabry.oc.ui.words.res.mobileTaskInProgress
import io.github.eslamasabry.oc.ui.words.res.mobileTaskPending
import io.github.eslamasabry.oc.ui.words.res.mobileTaskPriorityHigh
import io.github.eslamasabry.oc.ui.words.res.mobileTaskPriorityLow
import io.github.eslamasabry.oc.ui.words.res.mobileTaskPriorityMedium
import io.github.eslamasabry.oc.ui.words.res.mobileTasksNoUnfinished
import io.github.eslamasabry.oc.ui.words.res.mobileTasksProgress
import io.github.eslamasabry.oc.ui.words.res.mobileTasksUnfinished
import io.github.eslamasabry.oc.ui.words.res.runningWorkOffline
import io.github.eslamasabry.oc.ui.words.res.stateSkillsBusy
import io.github.eslamasabry.oc.ui.words.res.stateSkillsChanged
import io.github.eslamasabry.oc.ui.words.res.stateSkillsDone
import io.github.eslamasabry.oc.ui.words.res.stateSkillsStaged
import io.github.eslamasabry.oc.ui.words.res.stateSkillsTitle
import io.github.eslamasabry.oc.ui.words.res.stateSkillsUncertain
import io.github.eslamasabry.oc.ui.words.res.stateSkillsUnsupported
import io.github.eslamasabry.oc.ui.words.res.stateSkillsUse
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
private fun ListFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = onBack,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun notOnServer(feature: String, server: String): String =
    pluralStringResource(Res.plurals.kitCapNotOnHost, 1, 1, feature, server)

/**
 * Offline reads as the reconnect line; anything else as the could-not-load state with Details and
 * Try again.
 */
@Composable
private fun Failed(failure: Failure, title: String, onRetry: () -> Unit) {
    KitStateView(
        kind = KitStateKind.Error,
        title =
            if (failure is Failure.Network) stringResource(Res.string.runningWorkOffline)
            else title,
        failure = failure,
        onRetry = onRetry,
        modifier = Modifier.testTag("prompts-list-error"),
    )
}

/**
 * The server's slash commands. Tapping one puts `/name ` into the conversation's composer and goes
 * back there; the person adds anything else and sends it. A server that does not share its commands
 * says so in plain words.
 */
@Composable
public fun CommandsScreen(state: CommandsUi, actions: CommandsActions) {
    val title = stringResource(Res.string.chatUiSlashCommandsAndAgents)
    ListFrame(title, actions::back) {
        when (val phase = state.phase) {
            ListPhase.Loading ->
                KitSkeletonRows(Modifier.testTag("prompts-list-loading"), count = 5)
            is ListPhase.Failed -> Failed(phase.failure, title, actions::retry)
            is ListPhase.NotOnServer ->
                KitStateView(kind = KitStateKind.Missing, title = notOnServer(title, phase.server))
            is ListPhase.Shown ->
                if (phase.items.isEmpty()) {
                    KitStateView(
                        kind = KitStateKind.Empty,
                        title = notOnServer(title, phase.server),
                    )
                } else {
                    for (command in phase.items) {
                        KitRow(
                            title = "/${command.name}",
                            supporting = command.description,
                            supportingMaxLines = 2,
                            onClick = { actions.choose(command.name) },
                            modifier = Modifier.testTag("command-${command.name}"),
                        )
                    }
                }
        }
        if (state.skillsLoading) KitSkeletonRows(count = 2)
        state.skillsFailure?.let {
            Failed(it, stringResource(Res.string.stateSkillsTitle), actions::retry)
        }
        if (state.skills.isNotEmpty())
            KitRowGroup(label = stringResource(Res.string.stateSkillsTitle)) {
                for (skill in state.skills) {
                    val id = skill.id ?: continue
                    KitRow(
                        title = skill.name,
                        supporting = stringResource(Res.string.stateSkillsUse),
                        onClick = { actions.activateSkill(id) },
                    )
                }
            }
        state.skillResult?.let { result ->
            val key =
                when (result) {
                    SkillActivation.Done -> Res.string.stateSkillsDone
                    is SkillActivation.Failed -> Res.string.stateSkillsUncertain
                    is SkillActivation.Refused ->
                        when (result.reason) {
                            SessionSkillFailure.Changed -> Res.string.stateSkillsChanged
                            SessionSkillFailure.Staged -> Res.string.stateSkillsStaged
                            SessionSkillFailure.Busy -> Res.string.stateSkillsBusy
                            SessionSkillFailure.Unsupported -> Res.string.stateSkillsUnsupported
                            SessionSkillFailure.Uncertain -> Res.string.stateSkillsUncertain
                        }
                }
            KitNotice(stringResource(key))
        }
    }
}

@Composable
private fun stateWords(state: TaskState): String =
    stringResource(
        when (state) {
            TaskState.Pending -> Res.string.mobileTaskPending
            TaskState.InProgress -> Res.string.mobileTaskInProgress
            TaskState.Completed -> Res.string.mobileTaskCompleted
            TaskState.Cancelled -> Res.string.mobileTaskCancelled
        }
    )

@Composable
private fun priorityWords(priority: TaskPriority): String =
    stringResource(
        when (priority) {
            TaskPriority.High -> Res.string.mobileTaskPriorityHigh
            TaskPriority.Medium -> Res.string.mobileTaskPriorityMedium
            TaskPriority.Low -> Res.string.mobileTaskPriorityLow
        }
    )

/**
 * The agent's task list (Flutter `MobileTaskView`): progress, "Show unfinished only", one row per
 * task.
 */
@Composable
public fun TasksScreen(state: TasksUi, actions: TasksActions) {
    val title = stringResource(Res.string.chatUiTodos)
    ListFrame(title, actions::back) {
        when (val phase = state.phase) {
            ListPhase.Loading ->
                KitSkeletonRows(Modifier.testTag("prompts-list-loading"), count = 5)
            is ListPhase.Failed -> Failed(phase.failure, title, actions::retry)
            is ListPhase.NotOnServer ->
                KitStateView(kind = KitStateKind.Missing, title = notOnServer(title, phase.server))
            is ListPhase.Shown ->
                if (phase.items.isEmpty()) {
                    KitStateView(
                        kind = KitStateKind.Empty,
                        title = stringResource(Res.string.externalAgentsNoTasksTitle),
                    )
                } else {
                    val (done, tracked) = TaskRules.progress(phase.items)
                    KitText(stringResource(Res.string.mobileTasksProgress, done, tracked))
                    KitSwitchRow(
                        title = stringResource(Res.string.mobileTasksUnfinished),
                        checked = state.unfinishedOnly,
                        onCheckedChange = { actions.toggleUnfinished() },
                    )
                    val visible = state.visible
                    if (visible.isEmpty())
                        KitText(stringResource(Res.string.mobileTasksNoUnfinished))
                    for ((index, task) in visible.withIndex()) TaskRow(index, task)
                }
        }
    }
}

@Composable
private fun TaskRow(index: Int, task: TaskItem) {
    val supporting =
        listOfNotNull(stateWords(task.state), task.priority?.let { priorityWords(it) })
            .joinToString(" · ")
    KitRow(
        title = task.content.ifBlank { stateWords(task.state) },
        supporting = supporting,
        supportingMaxLines = 1,
        modifier = Modifier.testTag("task-$index"),
    )
}
