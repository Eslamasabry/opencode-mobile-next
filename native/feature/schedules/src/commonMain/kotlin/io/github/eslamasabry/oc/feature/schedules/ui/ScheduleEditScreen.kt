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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.scheduleNeedFuture
import io.github.eslamasabry.oc.ui.words.res.scheduleThisConversation
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.stringResource

/**
 * K02 as a page: the prompt, project, repeat, time, missed-run choice and model, with one main
 * action. The typed fields are reported to the view model as they change (rotation keeps them; the
 * prompt is kept as a draft).
 */
@Composable
public fun ScheduleEditScreen(state: ScheduleEditUi, actions: ScheduleEditActions) {
    val words = scheduleWords()
    // Re-seeded only when the view model loads new starting values, never on recomposition.
    val draft = remember(state.seed) { EditorDraft(state.fields) }
    // Every change goes to the view model, so rotation or leaving the editor never loses it
    // (B-STATE-024).
    LaunchedEffect(draft) { snapshotFlow { draft.fields() }.drop(1).collect(actions::edited) }
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
        // The one main action stays at the thumb, visible without scrolling (screen standard).
        bottom =
            if (state.phase != EditPhase.Ready) null
            else
                ({
                    KitButton(
                        if (state.saving) words.saving
                        else scheduleActionLabel(draft.fields(), words),
                        { actions.save(draft.fields()) },
                        Modifier.fillMaxWidth()
                            .padding(
                                horizontal = LocalKitTokens.current.gutter.dp,
                                vertical = Space.S2.dp,
                            ),
                        enabled =
                            if (state.saving) KitEnabled.Disabled(words.saving)
                            else KitEnabled.Enabled,
                        working = state.saving,
                    )
                }),
        width = KitScreenWidth.Reading,
    ) {
        when (state.phase) {
            EditPhase.Loading -> KitStateView(KitStateKind.Loading, words.loading)
            EditPhase.Unavailable ->
                KitStateView(
                    KitStateKind.Missing,
                    if (state.editing) words.unavailableEdit else words.unavailableServer,
                )
            EditPhase.Ready -> EditorForm(state, draft, words)
        }
    }
}

/**
 * The typed fields of one editor visit; they live only in this composition and survive a refused
 * save.
 */
private class EditorDraft(start: EditorFields) {
    val prompt = TextFieldState(start.prompt)
    val project = TextFieldState(start.project)
    val time = TextFieldState(start.time)
    val date = TextFieldState(start.date)
    val hours = TextFieldState(start.hours)
    var kind by mutableStateOf(start.kind)
    var missed by mutableStateOf(start.missed)
    var model by mutableStateOf(start.model)
    var thisConversation by mutableStateOf(start.thisConversation)

    fun fields(): EditorFields =
        EditorFields(
            prompt = prompt.text.toString(),
            project = project.text.toString(),
            kind = kind,
            time = time.text.toString(),
            date = date.text.toString(),
            hours = hours.text.toString(),
            missed = missed,
            model = model,
            thisConversation = thisConversation,
        )
}

@Composable
private fun EditorForm(state: ScheduleEditUi, draft: EditorDraft, words: ScheduleWords) {
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
            draft.prompt,
            words.prompt,
            kind = KitFieldKind.Multiline,
            helper = words.promptHelp,
            error = if (state.issue == EditIssue.PromptRequired) words.issuePrompt else null,
            maxLength = 32_000,
            enabled = enabled,
        )
        KitField(
            draft.project,
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
            selected = draft.kind,
            semanticsLabel = words.repeat,
            enabled = enabled,
            onChange = { draft.kind = it },
        )
        if (draft.kind == RecurrenceKind.Once) {
            KitField(
                draft.date,
                words.date,
                kind = KitFieldKind.Mono,
                error = if (state.issue == EditIssue.DateInvalid) words.issueDate else null,
                enabled = enabled,
            )
        }
        if (draft.kind != RecurrenceKind.Interval) {
            KitField(
                draft.time,
                words.time,
                kind = KitFieldKind.Mono,
                error = if (state.issue == EditIssue.TimeInvalid) words.issueTime else null,
                enabled = enabled,
            )
        }
        if (draft.kind == RecurrenceKind.Interval) {
            KitField(
                draft.hours,
                words.hours,
                kind = KitFieldKind.Number,
                error = if (state.issue == EditIssue.HoursInvalid) words.issueHours else null,
                enabled = enabled,
            )
        }
        KitSectionLabel(words.missed)
        KitChoiceList(
            listOf(
                KitChoice(MissedRunPolicy.CatchUp, words.missedCatchUp),
                KitChoice(MissedRunPolicy.Skip, words.missedSkip),
            ),
            selected = draft.missed,
            semanticsLabel = words.missed,
            onSelect = { draft.missed = it },
        )
        if (state.models.isNotEmpty() || draft.model != null) {
            KitSectionLabel(words.model)
            val choices =
                listOf(KitChoice<ModelChoice?>(null, words.modelDefault)) +
                    (state.models + listOfNotNull(draft.model).filter { it !in state.models }).map {
                        KitChoice<ModelChoice?>(it, it.label)
                    }
            KitChoiceList(
                choices,
                selected = draft.model,
                semanticsLabel = words.model,
                onSelect = { draft.model = it },
            )
        }
        KitRowGroup(label = words.conversation) {
            if (state.canChooseConversation) {
                KitChoiceList(
                    choices =
                        listOf(
                            KitChoice(
                                true,
                                org.jetbrains.compose.resources.stringResource(
                                    io.github.eslamasabry.oc.ui.words.res.Res.string
                                        .scheduleThisConversation
                                ),
                            ),
                            KitChoice(false, words.newEachTime),
                        ),
                    selected = draft.thisConversation,
                    semanticsLabel = words.conversation,
                    onSelect = { draft.thisConversation = it },
                )
            } else KitRow(words.newEachTime)
        }
        if (state.issue == EditIssue.FutureRequired) {
            io.github.eslamasabry.oc.ui.kit.KitText(
                org.jetbrains.compose.resources.stringResource(
                    io.github.eslamasabry.oc.ui.words.res.Res.string.scheduleNeedFuture
                )
            )
        }
        io.github.eslamasabry.oc.ui.kit.KitText(
            org.jetbrains.compose.resources.stringResource(
                io.github.eslamasabry.oc.ui.words.res.Res.string.scheduleClosedNote
            )
        )
    }
}

@Composable
private fun scheduleActionLabel(fields: EditorFields, words: ScheduleWords): String {
    if (fields.kind == RecurrenceKind.Interval) {
        val hours = fields.hours.toIntOrNull() ?: return words.save
        return stringResource(Res.string.scheduleActionHours, hours)
    }
    val time =
        io.github.eslamasabry.oc.feature.schedules.presentation.EditorRules.time(fields.time)
            ?: return words.save
    val clock =
        "${time.first.toString().padStart(2, '0')}:${time.second.toString().padStart(2, '0')}"
    val whenText =
        if (fields.kind == RecurrenceKind.Once) {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            io.github.eslamasabry.oc.engine.schedules.ScheduleTime.at(
                    fields.date,
                    time.first,
                    time.second,
                )
                ?.let { moment(it, now, words) } ?: return words.save
        } else clock
    return stringResource(Res.string.scheduleActionWhen, whenText)
}
