package io.github.eslamasabry.oc.feature.schedules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.schedules.MissedRunPolicy
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.feature.schedules.presentation.EditIssue
import io.github.eslamasabry.oc.feature.schedules.presentation.EditPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.EditorFields
import io.github.eslamasabry.oc.feature.schedules.presentation.ModelChoice
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditActions
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * K02 as a page: the prompt, project, repeat, time, missed-run choice and model, with one main action. The typed
 * fields live only in this composition and survive a refused save (the view model hands them back).
 * TODO(kit): the K4 date and time wheels do not exist yet; time and date are typed fields with plain examples.
 */
@Composable
public fun ScheduleEditScreen(state: ScheduleEditUi, actions: ScheduleEditActions) {
    val words = scheduleWords()
    KitScreen(
        topBar = {
            KitTopBar(
                title = if (state.editing) words.editTitle else words.new,
                exit = KitTopBarExit.Close,
                exitLabel = words.back,
                onExit = actions::back,
            )
        },
        loading = state.phase == EditPhase.Loading,
        loadingLabel = words.loading,
        width = KitScreenWidth.Reading,
    ) {
        when (state.phase) {
            EditPhase.Loading -> KitStateView(KitStateKind.Loading, words.loading)
            EditPhase.Unavailable -> KitStateView(KitStateKind.Missing, if (state.editing) words.unavailableEdit else words.unavailableServer)
            EditPhase.Ready -> EditorForm(state, actions, words)
        }
    }
}

@Composable
private fun EditorForm(state: ScheduleEditUi, actions: ScheduleEditActions, words: ScheduleWords) {
    // Re-seeded only when the view model loads new starting values, never on recomposition.
    val start = state.fields
    val prompt = remember(state.seed) { TextFieldState(start.prompt) }
    val project = remember(state.seed) { TextFieldState(start.project) }
    val time = remember(state.seed) { TextFieldState(start.time) }
    val date = remember(state.seed) { TextFieldState(start.date) }
    val hours = remember(state.seed) { TextFieldState(start.hours) }
    var kind by remember(state.seed) { mutableStateOf(start.kind) }
    var missed by remember(state.seed) { mutableStateOf(start.missed) }
    var model by remember(state.seed) { mutableStateOf(start.model) }
    val enabled = if (state.saving) KitEnabled.Disabled(words.saving) else KitEnabled.Enabled
    Column(
        Modifier.fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        state.error?.let { KitErrorNotice(words.error(it)) }
        KitField(
            prompt,
            words.prompt,
            kind = KitFieldKind.Multiline,
            helper = words.promptHelp,
            error = if (state.issue == EditIssue.PromptRequired) words.issuePrompt else null,
            maxLength = 32_000,
            enabled = enabled,
        )
        KitField(
            project,
            words.project,
            kind = KitFieldKind.Path,
            placeholder = "/home/me/project",
            error = if (state.issue == EditIssue.ProjectRequired) words.issueProject else null,
            enabled = enabled,
        )
        KitSectionLabel(words.repeat)
        KitSegmented(
            listOf(
                KitSegment(RecurrenceKind.Once, words.kindOnce),
                KitSegment(RecurrenceKind.Daily, words.kindDaily),
                KitSegment(RecurrenceKind.Weekdays, words.kindWeekdays),
                KitSegment(RecurrenceKind.Interval, words.kindInterval),
            ),
            selected = kind,
            semanticsLabel = words.repeat,
            enabled = enabled,
            onChange = { kind = it },
        )
        if (kind == RecurrenceKind.Once) {
            KitField(
                date,
                words.date,
                kind = KitFieldKind.Mono,
                error = if (state.issue == EditIssue.DateInvalid) words.issueDate else null,
                enabled = enabled,
            )
        }
        KitField(
            time,
            words.time,
            kind = KitFieldKind.Mono,
            helper = if (kind == RecurrenceKind.Interval) words.firstAt else null,
            error = if (state.issue == EditIssue.TimeInvalid) words.issueTime else null,
            enabled = enabled,
        )
        if (kind == RecurrenceKind.Interval) {
            KitField(
                hours,
                words.hours,
                kind = KitFieldKind.Number,
                error = if (state.issue == EditIssue.HoursInvalid) words.issueHours else null,
                enabled = enabled,
            )
        }
        KitSectionLabel(words.missed)
        KitChoiceList(
            listOf(KitChoice(MissedRunPolicy.CatchUp, words.missedCatchUp), KitChoice(MissedRunPolicy.Skip, words.missedSkip)),
            selected = missed,
            semanticsLabel = words.missed,
            onSelect = { missed = it },
        )
        if (state.models.isNotEmpty() || model != null) {
            KitSectionLabel(words.model)
            val choices =
                listOf(KitChoice<ModelChoice?>(null, words.modelDefault)) +
                    (state.models + listOfNotNull(model).filter { it !in state.models }).map { KitChoice<ModelChoice?>(it, it.label) }
            KitChoiceList(choices, selected = model, semanticsLabel = words.model, onSelect = { model = it })
        }
        KitRowGroup(label = words.conversation) {
            KitRow(if (state.continuesConversation) words.continuesTitle(state.conversationTitle) else words.newEachTime)
        }
        KitButton(
            if (state.saving) words.saving else words.save,
            {
                actions.save(
                    EditorFields(
                        prompt = prompt.text.toString(),
                        project = project.text.toString(),
                        kind = kind,
                        time = time.text.toString(),
                        date = date.text.toString(),
                        hours = hours.text.toString(),
                        missed = missed,
                        model = model,
                    ),
                )
            },
            Modifier.fillMaxWidth(),
            enabled = enabled,
            working = state.saving,
        )
    }
}
