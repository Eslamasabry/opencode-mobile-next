package io.github.eslamasabry.oc.feature.schedules.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import io.github.eslamasabry.oc.ui.kit.frame.kitImePadding
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
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleStarter
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
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
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
        loading = state.phase == EditPhase.Loading || state.updating,
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
        edges = KitScreenEdges.Bleed,
    ) {
        when (state.phase) {
            EditPhase.Loading -> KitStateView(KitStateKind.Loading, words.loading)
            EditPhase.Unavailable ->
                KitStateView(
                    KitStateKind.Missing,
                    if (state.editing) words.unavailableEdit else words.unavailableServer,
                )
            EditPhase.Ready -> EditorForm(state, draft, words, actions)
        }
    }
}

/**
 * The typed fields of one editor visit; they live only in this composition and survive a refused
 * save.
 */
private class EditorDraft(start: EditorFields) {
    val topic = TextFieldState(start.topic)
    var starter by mutableStateOf(start.starter)
    var assistant by mutableStateOf(start.assistant)
    var webReads by mutableStateOf(start.webReads)

    /** The project left the Assistant folder and the switch went off for that: the row says why until it is turned on again. */
    var webReadsMoved by mutableStateOf(false)

    /** The person left the Assistant folder to pick a project: the path field shows and stays while they type. */
    var choosingProject by mutableStateOf(false)
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
            starter = starter,
            topic = topic.text.toString(),
            assistant = assistant,
            webReads = webReads,
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
private fun EditorForm(state: ScheduleEditUi, draft: EditorDraft, words: ScheduleWords, actions: ScheduleEditActions) {
    val enabled = if (state.saving) KitEnabled.Disabled(words.saving) else KitEnabled.Enabled
    var targetOpen by remember { mutableStateOf(false) }
    val promptFocus = remember { FocusRequester() }
    val topicFocus = remember { FocusRequester() }
    val newsTemplate = stringResource(Res.string.scheduleNewsPrompt)
    LaunchedEffect(state.seed) {
        if (state.editing) return@LaunchedEffect
        if (draft.starter == ScheduleStarter.News && draft.topic.text.isEmpty()) topicFocus.requestFocus()
        else promptFocus.requestFocus()
    }
    // B-440: leaving the Assistant folder turns the web-read switch off, with its reason; the person may turn it on again.
    LaunchedEffect(draft) {
        var was = EditorRules.inAssistant(draft.fields())
        snapshotFlow { EditorRules.inAssistant(draft.fields()) }
            .collect { now ->
                if (was && !now && draft.webReads) {
                    draft.webReads = false
                    draft.webReadsMoved = true
                }
                was = now
            }
    }
    // B-440: typing the topic rewrites the prompt it filled, until the person edits that prompt themselves.
    LaunchedEffect(draft, newsTemplate) {
        var before = draft.topic.text.toString()
        snapshotFlow { draft.topic.text.toString() }
            .drop(1)
            .collect { topic ->
                if (draft.starter == ScheduleStarter.News) {
                    val filled = EditorRules.newsPrompt(before, newsTemplate)
                    val now = draft.prompt.text.toString()
                    if (now.isBlank() || now == filled) {
                        val next = EditorRules.newsPrompt(topic, newsTemplate)
                        draft.prompt.edit { replace(0, length, next) }
                    }
                }
                before = topic
            }
    }
    Column(
        Modifier.fillMaxSize()
            .kitImePadding()
            .verticalScroll(kitPageScrollState())
            .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp)
    ) {
        // B-440: only a NEW schedule offers a starter; a saved one is edited as it is.
        if (!state.editing) {
            KitText(stringResource(Res.string.scheduleStarterLabel), role = TypeRole.Secondary)
            Spacer(Modifier.height(Space.S2.dp))
            KitSegmented(
                listOf(
                    KitSegment(ScheduleStarter.Blank, stringResource(Res.string.scheduleStarterBlank)),
                    KitSegment(ScheduleStarter.News, stringResource(Res.string.scheduleStarterNews)),
                ),
                selected = draft.starter,
                semanticsLabel = stringResource(Res.string.scheduleStarterLabel),
                enabled = enabled,
                onChange = { actions.chooseStarter(it, newsTemplate) },
            )
            Spacer(Modifier.height(Space.S3.dp))
            if (draft.starter == ScheduleStarter.News) {
                KitField(
                    draft.topic,
                    stringResource(Res.string.scheduleStarterNewsTopic),
                    placeholder = stringResource(Res.string.scheduleStarterNewsHint),
                    kind = KitFieldKind.Text,
                    focusRequester = topicFocus,
                    error =
                        if (state.issue == EditIssue.PromptRequired && draft.topic.text.isBlank()) words.issuePrompt
                        else null,
                    maxLength = 200,
                    enabled = enabled,
                )
                Spacer(Modifier.height(Space.S4.dp))
            }
        }
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
                EditIssue.AssistantFolderFailed -> stringResource(Res.string.scheduleAssistantFailed)
                else -> null
            }
        (issue ?: state.error?.let(words::error))?.let {
            Spacer(Modifier.height(Space.S2.dp))
            KitText(it, role = TypeRole.Secondary)
        }
        Spacer(Modifier.height(Space.S3.dp))
        // The source seed fixes the project. Retain input only when the native entry supplied none.
        if (draft.starter == ScheduleStarter.News && !draft.assistant && !state.canMakeFolder && !state.updating) {
            // This server cannot make the folder: say so, and ask for a project (never a quiet pick of the newest one).
            KitText(stringResource(Res.string.scheduleAssistantCannot), role = TypeRole.Secondary)
            Spacer(Modifier.height(Space.S2.dp))
        }
        if (!draft.assistant && (state.fields.project.isBlank() || draft.choosingProject)) {
            KitField(
                draft.project,
                words.project,
                kind = KitFieldKind.Path,
                error = if (state.issue == EditIssue.ProjectRequired) words.issueProject else null,
                enabled = enabled,
            )
        }
        // With no project picked yet the path field above is the only "Project folder": a second, empty-named row would repeat it.
        if (draft.assistant || draft.project.text.isNotBlank())
        KitRow(
            title =
                if (draft.assistant) stringResource(Res.string.scheduleAssistantTitle)
                else
                    draft.project.text.toString().trimEnd('/').substringAfterLast('/').ifEmpty {
                        words.project
                    },
            leading = KitRowLeading.Glyph(KitIcons.folderOpen),
            supporting =
                if (draft.assistant) stringResource(Res.string.scheduleAssistantNote)
                else
                    stringResource(
                        if (draft.thisConversation) Res.string.scheduleInThisConversation
                        else Res.string.scheduleNewConversation
                    ),
            supportingMaxLines = if (draft.assistant) 3 else 1,
            trailing =
                if (draft.assistant || state.canChooseConversation) KitRowTrailing.Chevron
                else KitRowTrailing.None,
            onClick =
                if (draft.assistant) {
                    {
                        // The person picks a project themselves: the path field takes over.
                        draft.assistant = false
                        draft.choosingProject = true
                        draft.project.edit { replace(0, length, "") }
                    }
                } else if (state.canChooseConversation) ({ targetOpen = true }) else null,
            enabled = enabled.isEnabled,
            disabledReason = enabled.reason,
        )
        // B-440: a run of this schedule may read the web without asking (searches and page reads only, once each).
        if (!state.updating || state.canReadWeb) {
            Spacer(Modifier.height(Space.S3.dp))
            val noServer = stringResource(Res.string.scheduleWebReadsNoServer)
            KitRowGroup {
            KitSwitchRow(
                stringResource(Res.string.scheduleWebReadsLabel),
                draft.webReads && state.canReadWeb,
                supporting =
                    when {
                        !state.canReadWeb -> null
                        draft.webReadsMoved && !draft.webReads -> stringResource(Res.string.scheduleWebReadsMoved)
                        else -> stringResource(Res.string.scheduleWebReadsNote)
                    },
                enabled = if (state.canReadWeb) enabled else KitEnabled.Disabled(noServer),
                supportingMaxLines = 4,
                onCheckedChange = {
                    draft.webReads = it
                    if (it) draft.webReadsMoved = false
                },
            )
            }
        }
        // B-NAV-055 (Flutter schedule_sheet.dart `schedule-last-run`): what the last run did, and
        // for a failure what happened and what to do next.
        state.lastRun?.let { run ->
            KitRow(
                title = lastRunText(run, state.nowMs, words, rememberClockOf(state.nowMs)),
                leading = KitRowLeading.Glyph(KitIcons.clock),
                supporting =
                    run.errorCode?.let(words::why)
                        ?: run.sessionId?.let { words.openConversation },
                supportingMaxLines = 3,
                trailing =
                    if (run.sessionId != null) KitRowTrailing.Chevron else KitRowTrailing.None,
                onClick = run.sessionId?.let { id -> { actions.openConversation(id) } },
            )
        }
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
    // The wheel's own clock words (the phone's 12 or 24 hour style), so the button, the row and the wheel agree.
    val clock = rememberClockOf(nowMs)(time.first, time.second)
    val whenText =
        if (fields.kind == RecurrenceKind.Once) {
            io.github.eslamasabry.oc.engine.schedules.ScheduleTime.at(
                    fields.date,
                    time.first,
                    time.second,
                )
                ?.let { at ->
                    // The wheel's own clock words (it formats a time of day the same way).
                    val wheelClock =
                        LocalKitPlatform.current.messageTime(
                            io.github.eslamasabry.oc.engine.schedules.ScheduleTime.todayAt(nowMs, time.first, time.second),
                            nowMs,
                        )
                    moment(at, nowMs, words, clockWords = wheelClock)
                } ?: return words.save
        } else clock
    return stringResource(Res.string.scheduleActionWhen, whenText)
}
