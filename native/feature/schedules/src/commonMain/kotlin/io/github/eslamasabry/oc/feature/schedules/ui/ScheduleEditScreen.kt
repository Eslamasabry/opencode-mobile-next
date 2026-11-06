package io.github.eslamasabry.oc.feature.schedules.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.ScheduleTime
import io.github.eslamasabry.oc.feature.schedules.presentation.EditIssue
import io.github.eslamasabry.oc.feature.schedules.presentation.EditPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.EditorFields
import io.github.eslamasabry.oc.feature.schedules.presentation.EditorRules
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditActions
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitWheel
import io.github.eslamasabry.oc.ui.kit.choice.KitWheelColumn
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.scheduleNeedFuture
import io.github.eslamasabry.oc.ui.words.res.scheduleThisConversation
import kotlinx.coroutines.flow.drop
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
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
                        else scheduleActionLabel(draft.fields(), state.nowMs, words),
                        { actions.save(draft.fields()) },
                        Modifier.testTag("schedule-save")
                            .fillMaxWidth()
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
    var targetOpen by remember { mutableStateOf(false) }
    val promptFocus = remember { FocusRequester() }
    LaunchedEffect(state.seed) { if (!state.editing) promptFocus.requestFocus() }
    Column(
        Modifier.fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp)
    ) {
        KitField(
            draft.prompt,
            stringResource(Res.string.scheduleAsk),
            placeholder = stringResource(Res.string.scheduleAskHint),
            kind = KitFieldKind.Multiline,
            multilineLines = 1..6, // F: lib/ui/screens/scheduling/schedule_sheet.dart:372-373
            focusRequester = promptFocus,
            error = if (state.issue == EditIssue.PromptRequired) words.issuePrompt else null,
            maxLength = 32_000, // F: lib/ui/screens/scheduling/schedule_sheet.dart:375
            enabled = enabled,
        )
        Spacer(Modifier.height(Space.S4.dp))
        KitSegmented(
            listOf(
                KitSegment(RecurrenceKind.Once, words.kindOnce),
                KitSegment(RecurrenceKind.Daily, words.kindDaily),
                KitSegment(RecurrenceKind.Weekdays, words.kindWeekdays),
                KitSegment(RecurrenceKind.Interval, stringResource(Res.string.scheduleCustom)),
            ),
            selected = draft.kind,
            semanticsLabel = stringResource(Res.string.scheduleRepeat),
            enabled = enabled,
            onChange = { draft.kind = it },
        )
        Spacer(Modifier.height(Space.S3.dp))
        ScheduleWheel(state, draft, words, enabled)
        val issue =
            when (state.issue) {
                EditIssue.FutureRequired -> stringResource(Res.string.scheduleNeedFuture)
                EditIssue.DateInvalid -> words.issueDate
                EditIssue.TimeInvalid -> words.issueTime
                EditIssue.HoursInvalid -> words.issueHours
                else -> null
            }
        (issue ?: state.error?.let(words::error))?.let {
            Spacer(Modifier.height(Space.S2.dp))
            KitText(it, role = TypeRole.Secondary)
        }
        Spacer(Modifier.height(Space.S3.dp))
        // The source seed fixes the project. Retain input only when the native entry supplied none.
        if (state.fields.project.isBlank()) {
            KitField(
                draft.project,
                words.project,
                kind = KitFieldKind.Path,
                error = if (state.issue == EditIssue.ProjectRequired) words.issueProject else null,
                enabled = enabled,
            )
        }
        KitRow(
            title =
                draft.project.text.toString().trimEnd('/').substringAfterLast('/').ifEmpty {
                    words.project
                },
            leading = KitRowLeading.Glyph(KitIcons.folderOpen),
            supporting =
                stringResource(
                    if (draft.thisConversation) Res.string.scheduleInThisConversation
                    else Res.string.scheduleNewConversation
                ),
            trailing =
                if (state.canChooseConversation) KitRowTrailing.Chevron else KitRowTrailing.None,
            onClick = if (state.canChooseConversation) ({ targetOpen = true }) else null,
            enabled = enabled.isEnabled,
            disabledReason = enabled.reason,
        )
        Spacer(Modifier.height(Space.S3.dp))
        KitText(
            stringResource(Res.string.scheduleClosedNote),
            role = TypeRole.Secondary,
            tone = TextTone.Tertiary,
        )
        KitSheet(
            visible = targetOpen,
            onDismissRequest = { targetOpen = false },
            title = stringResource(Res.string.scheduleTarget),
            height = KitSheetHeight.Content,
        ) {
            KitChoiceList(
                choices =
                    listOf(
                        KitChoice(
                            true,
                            stringResource(Res.string.scheduleThisConversation),
                            enabled = enabled,
                        ),
                        KitChoice(
                            false,
                            stringResource(Res.string.scheduleNewConversation),
                            enabled = enabled,
                        ),
                    ),
                selected = draft.thisConversation,
                semanticsLabel = words.conversation,
                onSelect = {
                    draft.thisConversation = it
                    targetOpen = false
                },
            )
        }
    }
}

@Composable
private fun ScheduleWheel(
    state: ScheduleEditUi,
    draft: EditorDraft,
    words: ScheduleWords,
    enabled: KitEnabled,
) {
    if (draft.kind == RecurrenceKind.Interval) {
        val hours =
            draft.hours.text.toString().toIntOrNull()
                ?: 3 // F: lib/ui/screens/scheduling/schedule_sheet.dart:147
        val values =
            ((1..24) + hours)
                .distinct()
                .sorted() // F: lib/ui/screens/scheduling/schedule_sheet.dart:470
        val labels = values.associateWith { value ->
            org.jetbrains.compose.resources.pluralStringResource(
                Res.plurals.scheduleRuleHours,
                value,
                value,
            )
        }
        KitWheel(
            listOf(
                KitWheelColumn(
                    values,
                    hours,
                    { labels.getValue(it) },
                    { draft.hours.edit { replace(0, length, it.toString()) } },
                    stringResource(Res.string.scheduleWheelHowOften),
                    testTag = "schedule-wheel-hours",
                )
            ),
            enabled = enabled,
        )
        return
    }
    val time =
        EditorRules.time(draft.time.text.toString())
            ?: (9 to 0) // F: lib/ui/screens/scheduling/schedule_sheet.dart:134
    val minutes =
        time.first * 60 + time.second // F: lib/ui/screens/scheduling/schedule_sheet.dart:144
    val times =
        ((0 until 24 * 60 step 15) + minutes)
            .distinct()
            .sorted() // F: lib/ui/screens/scheduling/schedule_sheet.dart:483-486
    val platform = LocalKitPlatform.current
    val labels = times.associateWith { value ->
        val hour = value / 60 // F: lib/ui/screens/scheduling/schedule_sheet.dart:492
        val minute = value % 60 // F: lib/ui/screens/scheduling/schedule_sheet.dart:492
        platform.messageTime(ScheduleTime.todayAt(state.nowMs, hour, minute), state.nowMs)
            ?: clockValue(value)
    }
    val timeColumn =
        KitWheelColumn(
            times,
            minutes,
            { labels.getValue(it) },
            { value -> draft.time.edit { replace(0, length, clockValue(value)) } },
            stringResource(Res.string.scheduleTime),
            flex =
                if (draft.kind == RecurrenceKind.Once) 2
                else 1, // F: lib/ui/screens/scheduling/schedule_sheet.dart:528
            testTag = "schedule-wheel-time",
        )
    if (draft.kind != RecurrenceKind.Once) {
        KitWheel(listOf(timeColumn), enabled = enabled)
        return
    }
    val today = LocalDate.parse(ScheduleTime.today(state.nowMs))
    val selected = draft.date.text.toString().ifBlank { today.toString() }
    val days =
        ((0 until 60).map { LocalDate.fromEpochDays(today.toEpochDays() + it).toString() } +
                selected) // F: lib/ui/screens/scheduling/schedule_sheet.dart:501-505
            .distinct()
            .sorted()
    val todayLabel = stringResource(Res.string.scheduleWheelToday)
    val tomorrowLabel = stringResource(Res.string.scheduleWheelTomorrow)
    val tomorrow = LocalDate.fromEpochDays(today.toEpochDays() + 1).toString()
    KitWheel(
        listOf(
            KitWheelColumn(
                days,
                selected,
                { date ->
                    when (date) {
                        today.toString() -> todayLabel
                        tomorrow -> tomorrowLabel
                        else -> date
                    }
                },
                { value -> draft.date.edit { replace(0, length, value) } },
                stringResource(Res.string.scheduleWheelDay),
                flex = 3, // F: lib/ui/screens/scheduling/schedule_sheet.dart:510
                testTag = "schedule-wheel-day",
            ),
            timeColumn,
        ),
        enabled = enabled,
    )
}

private fun clockValue(minutes: Int): String =
    "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}" // NV: ISO editor HH:mm format

@Composable
private fun scheduleActionLabel(fields: EditorFields, nowMs: Long, words: ScheduleWords): String {
    if (fields.kind == RecurrenceKind.Interval) {
        val hours = fields.hours.toIntOrNull() ?: return words.save
        return pluralStringResource(Res.plurals.scheduleActionHours, hours, hours)
    }
    val time =
        io.github.eslamasabry.oc.feature.schedules.presentation.EditorRules.time(fields.time)
            ?: return words.save
    val clock =
        "${time.first.toString().padStart(2, '0')}:${time.second.toString().padStart(2, '0')}"
    val whenText =
        if (fields.kind == RecurrenceKind.Once) {
            io.github.eslamasabry.oc.engine.schedules.ScheduleTime.at(
                    fields.date,
                    time.first,
                    time.second,
                )
                ?.let { moment(it, nowMs, words) } ?: return words.save
        } else clock
    return stringResource(Res.string.scheduleActionWhen, whenText)
}
