package io.github.eslamasabry.oc.feature.factsbook.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.Reminder
import io.github.eslamasabry.oc.engine.factsbook.ReminderRepeat
import io.github.eslamasabry.oc.engine.factsbook.ReminderTiming
import io.github.eslamasabry.oc.feature.factsbook.presentation.ReminderDraft
import io.github.eslamasabry.oc.feature.factsbook.presentation.ReminderEditActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.ReminderEditUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.RemindersActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.RemindersUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChip
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitWheel
import io.github.eslamasabry.oc.ui.kit.choice.KitWheelColumn
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenRefresh
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import io.github.eslamasabry.oc.ui.kit.text.kitClock
import io.github.eslamasabry.oc.ui.kit.text.kitDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import org.jetbrains.compose.resources.stringResource

@Composable
private fun pad() = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S2.dp)

private fun two(n: Int) = n.toString().padStart(2, '0')

private fun clockOf(at: LocalDateTime) = "${two(at.hour)}:${two(at.minute)}"

/** The wall-clock moment as an instant in this phone's zone, so the platform formatters show the same wall clock. */
private fun momentMs(at: LocalDateTime): Long = at.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

/** A time of day the way the phone shows it (12 or 24 hour); the plain HH:mm where no formatter exists. */
@Composable
private fun clockWords(at: LocalDateTime): String = kitClock(momentMs(at)) ?: clockOf(at)

/** A date in the app's language; the ISO date where no formatter exists. */
@Composable
private fun dateWords(at: LocalDateTime): String = kitDate(momentMs(at)) ?: at.date.toString()

/** Short weekday words, Monday first, in the app's language (the scheduled-prompt day names). */
@Composable
private fun weekdayWords(day: Int): String =
    stringResource(
        when (DayOfWeek(day)) {
            DayOfWeek.MONDAY -> Res.string.schedule40Monday
            DayOfWeek.TUESDAY -> Res.string.schedule40Tuesday
            DayOfWeek.WEDNESDAY -> Res.string.schedule40Wednesday
            DayOfWeek.THURSDAY -> Res.string.schedule40Thursday
            DayOfWeek.FRIDAY -> Res.string.schedule40Friday
            DayOfWeek.SATURDAY -> Res.string.schedule40Saturday
            else -> Res.string.schedule40Sunday
        }
    )

/** A reminder's timing in words (F: `timingWords`). */
@Composable
internal fun timingWords(timing: ReminderTiming): String {
    val time = clockWords(timing.at)
    return when (timing.repeat) {
        ReminderRepeat.Never -> "${dateWords(timing.at)}, $time"
        ReminderRepeat.Daily -> stringResource(Res.string.fbWhenDaily, time)
        ReminderRepeat.Weekly -> stringResource(Res.string.fbWhenWeeklyOn, timing.weekdays.sorted().map { weekdayWords(it) }.joinToString(", "), time)
        ReminderRepeat.Monthly ->
            if (timing.lastDay) stringResource(Res.string.fbWhenMonthLast, time) else stringResource(Res.string.fbWhenMonthlyOn, timing.dayOfMonth, time)
        ReminderRepeat.Yearly -> stringResource(Res.string.fbWhenYearlyOn, "${timing.at.month.ordinal + 1}/${timing.at.day}", time)
    }
}

@Composable
private fun repeatWords(repeat: ReminderRepeat): String =
    stringResource(
        when (repeat) {
            ReminderRepeat.Never -> Res.string.fbRepeatNever
            ReminderRepeat.Daily -> Res.string.fbRepeatDaily
            ReminderRepeat.Weekly -> Res.string.fbRepeatWeekly
            ReminderRepeat.Monthly -> Res.string.fbRepeatMonthly
            ReminderRepeat.Yearly -> Res.string.fbRepeatYearly
        }
    )

/** Reminders (F: reminders_screen.dart). */
@Composable
public fun RemindersScreen(state: RemindersUi, actions: RemindersActions) {
    val title = stringResource(Res.string.fbRemindersTitle)
    val reminders = state.reminders
    KitScreen(
        topBar = {
            KitTopBar(title, exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = actions::back)
        },
        width = KitScreenWidth.Reading,
        loading = state.loading && reminders != null,
        loadingLabel = title,
        edges = KitScreenEdges.Bleed,
        refresh = KitScreenRefresh(refreshing = false, onRefresh = actions::reload, enabled = reminders != null),
    ) {
        when {
            reminders == null && state.error == null -> KitSkeletonRows()
            reminders == null ->
                KitStateView(
                    KitStateKind.Error,
                    stringResource(Res.string.fbErrorTitle),
                    body = failureWords(state.error) + (failureDetails(state.error)?.let { "\n\n$it" } ?: ""),
                    primary = KitAction(stringResource(Res.string.commonRetry), actions::reload),
                )
            else ->
                LazyColumn(Modifier.fillMaxSize()) {
                    state.notice?.let { notice ->
                        item("notice") {
                            KitNotice(
                                failureWords(notice),
                                pad(),
                                tone = StatusTone.Attention,
                                dismiss = KitLineAction(stringResource(Res.string.fbDismiss), actions::dismiss),
                            )
                        }
                    }
                    if (state.canAdd) {
                        item("add") {
                            KitRowGroup {
                                KitRow(
                                    title = stringResource(Res.string.fbRemindersAdd),
                                    leading = KitRowLeading.Glyph(KitIcons.add, tile = true),
                                    titleAccent = true,
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = actions::add,
                                )
                            }
                        }
                    }
                    if (reminders.isEmpty()) {
                        item("empty") {
                            KitStateView(
                                KitStateKind.Empty,
                                stringResource(Res.string.fbRemindersEmptyTitle),
                                body = stringResource(Res.string.fbRemindersEmptyBody),
                                icon = KitIcons.clock,
                            )
                        }
                    }
                    val groups =
                        listOf(
                            Res.string.fbRemindersActive to reminders.filter { !it.paused && !it.done },
                            Res.string.fbRemindersPaused to reminders.filter { it.paused && !it.done },
                            Res.string.fbRemindersDone to reminders.filter { it.done },
                        )
                    groups.forEach { (label, list) ->
                        if (list.isNotEmpty()) {
                            item(label.key) {
                                KitRowGroup(label = stringResource(label)) { list.forEach { ReminderRow(it, state, actions) } }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder, state: RemindersUi, actions: RemindersActions) {
    val zone =
        when (r.schedule.zone) {
            "vault" -> stringResource(Res.string.fbReminderZoneHome)
            "floating" -> stringResource(Res.string.fbReminderZoneFloating)
            else -> r.schedule.zone
        }
    val timing = ReminderTiming.fromWhen(r.schedule.`when`)
    val words = if (timing == null) stringResource(Res.string.fbWhenCustom) else timingWords(timing)
    val lines =
        listOfNotNull(
            "$words · $zone",
            if (!r.done && !r.paused && state.cannotRing) stringResource(Res.string.fbReminderNotSounded) else null,
        )
    val name = bidiName(r.what)
    val working = r.id in state.working
    KitRow(
        title = name,
        supporting = lines.joinToString("\n"),
        supportingMaxLines = 3,
        titleMaxLines = 2,
        leading = KitRowLeading.Glyph(if (r.paused) KitIcons.pause else KitIcons.clock),
        trailing =
            if (r.done) KitRowTrailing.None
            else
                KitRowTrailing.Action(
                    KitLineAction(
                        stringResource(if (r.paused) Res.string.fbReminderResume else Res.string.fbReminderPause, name),
                        { actions.togglePause(r) },
                        enabled = !working,
                        disabledReason = if (working) stringResource(Res.string.fbRemindersTitle) else null,
                    )
                ),
        onClick = if (timing != null && !r.done && !working) ({ actions.edit(r) }) else null,
    )
}

/** Check this reminder (F: reminder_check_sheet.dart), as a page that slides up. */
@Composable
public fun ReminderEditScreen(state: ReminderEditUi, actions: ReminderEditActions) {
    val title = stringResource(Res.string.fbReminderTitle)
    val draft = state.draft
    val appTag = LocalKitLocaleTag.current
    LaunchedEffect(appTag) { actions.appLanguage(appTag) }
    KitScreen(
        topBar = {
            KitTopBar(title, exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = actions::back)
        },
        width = KitScreenWidth.Reading,
        loading = state.working,
        loadingLabel = title,
        bottom =
            if (draft == null) null
            else
                ({
                    KitActionBlock(
                        Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S2.dp),
                        primary =
                            KitAction(
                                stringResource(Res.string.fbReminderSave),
                                actions::save,
                                enabled =
                                    if (draft.what.isBlank()) KitEnabled.Disabled(stringResource(Res.string.fbReminderNeedsTitle))
                                    else KitEnabled.Enabled,
                                working = state.working,
                            ),
                        secondary =
                            if (state.fromCapture) KitAction(stringResource(Res.string.fbReminderChange), actions::changeWritten) else null,
                    )
                }),
        edges = KitScreenEdges.Bleed,
    ) {
        if (draft == null) {
            if (state.error == null) KitSkeletonRows()
            else KitStateView(KitStateKind.Missing, stringResource(Res.string.fbErrorTitle), body = failureWords(state.error))
            return@KitScreen
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item("form") { DraftForm(draft, state, actions) }
        }
    }
}

@Composable
private fun DraftForm(draft: ReminderDraft, state: ReminderEditUi, actions: ReminderEditActions) {
    val what = rememberTextFieldState(draft.what)
    LaunchedEffect(what) { snapshotFlow { what.text.toString() }.collect { if (it != draft.what) actions.edit(draft.copy(what = it)) } }
    Column(pad(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        state.wrote?.let { KitText(stringResource(Res.string.fbReminderWrote, bidiName(it)), role = TypeRole.Secondary, tone = TextTone.Secondary) }
        KitText(stringResource(Res.string.fbReminderHelp), role = TypeRole.Secondary, tone = TextTone.Secondary)
        if ("date" in state.ambiguities) KitNotice(stringResource(Res.string.fbReminderAmbiguousDate))
        if ("time" in state.ambiguities) KitNotice(stringResource(Res.string.fbReminderAmbiguousTime))
        when {
            state.mismatch -> KitNotice(stringResource(Res.string.fbReminderMismatch), tone = StatusTone.Attention)
            state.error != null -> KitNotice(failureWords(state.error), tone = StatusTone.Attention)
        }
        KitField(
            what,
            stringResource(Res.string.fbReminderWhat),
            placeholder = stringResource(Res.string.fbReminderWhatHint),
            error = if (state.needsTitle) stringResource(Res.string.fbReminderNeedsTitle) else null,
        )
        // When it will fire, above the detail rows: with the keyboard up the person still sees it (F: summary).
        KitText(bidiName(timingWords(draft.timing)), role = TypeRole.Body)
    }
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.fbReminderRepeat),
            trailing =
                KitRowTrailing.Menu(
                    repeatWords(draft.timing.repeat),
                    ReminderRepeat.entries.map { repeat ->
                        KitMenuItem(repeatWords(repeat), { actions.edit(draft.copy(timing = draft.timing.withRepeat(repeat))) }, checked = repeat == draft.timing.repeat)
                    },
                ),
        )
    }
    Column(pad(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        WhenWheel(draft, actions)
        if (draft.timing.repeat == ReminderRepeat.Weekly) {
            KitFilterChips(bleed = true) {
                (1..7).forEach { day ->
                    val on = day in draft.timing.weekdays
                    KitFilterChip(weekdayWords(day), selected = on, onPressed = {
                        val next = if (on) draft.timing.weekdays - day else draft.timing.weekdays + day
                        if (next.isNotEmpty()) actions.edit(draft.copy(timing = draft.timing.copy(weekdays = next)))
                    })
                }
            }
        }
        KitSegmented(
            segments =
                listOf(
                    KitSegment("vault", stringResource(Res.string.fbReminderZoneHome)),
                    KitSegment("floating", stringResource(Res.string.fbReminderZoneFloating)),
                ),
            selected = if (draft.zone == "floating") "floating" else "vault",
            semanticsLabel = stringResource(Res.string.fbReminderZone),
            onChange = { actions.edit(draft.copy(zone = it)) },
        )
        KitText(
            stringResource(if (draft.zone == "floating") Res.string.fbReminderZoneFloatingDetail else Res.string.fbReminderZoneHomeDetail),
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
        )
        KitSegmented(
            segments = listOf(KitSegment("en", "English"), KitSegment("ar", "العربية")),
            selected = draft.locale,
            semanticsLabel = stringResource(Res.string.fbReminderLocale),
            onChange = { actions.edit(draft.copy(locale = it)) },
        )
    }
    KitGroupNote(stringResource(Res.string.fbReminderClockNote))
    if (state.cannotRing) {
        Column(pad()) { KitNotice(stringResource(Res.string.fbReminderNotSoundedYet)) }
    }
}

/** Day and time for a one-time reminder; the clock time (and day of month or date) for a repeating one. */
@Composable
private fun WhenWheel(draft: ReminderDraft, actions: ReminderEditActions) {
    val timing = draft.timing
    val minutes = timing.at.hour * 60 + timing.at.minute
    val times = ((0 until 24 * 60 step 15) + minutes).distinct().sorted()
    fun at(date: LocalDate, value: Int) = LocalDateTime(date.year, date.month, date.day, value / 60, value % 60)
    val time =
        KitWheelColumn(
            times,
            minutes,
            { "${two(it / 60)}:${two(it % 60)}" },
            { value -> actions.edit(draft.copy(timing = timing.copy(at = at(timing.at.date, value)))) },
            stringResource(Res.string.fbReminderTime),
        )
    when (timing.repeat) {
        ReminderRepeat.Never, ReminderRepeat.Yearly -> {
            val start = timing.at.date
            val days = ((-365 until 5 * 366).map { start.plus(DatePeriod(days = it)) } + start).distinct().sorted()
            KitWheel(
                listOf(
                    KitWheelColumn(
                        days,
                        start,
                        { if (timing.repeat == ReminderRepeat.Yearly) "${it.month.ordinal + 1}/${it.day}" else it.toString() },
                        { date -> actions.edit(draft.copy(timing = timing.copy(at = at(date, minutes)))) },
                        stringResource(Res.string.fbReminderDate),
                        flex = 2,
                    ),
                    time,
                )
            )
        }
        ReminderRepeat.Monthly -> {
            val last = 0
            val lastWords = stringResource(Res.string.fbReminderLastDay)
            KitWheel(
                listOf(
                    KitWheelColumn(
                        (1..31).toList() + last,
                        if (timing.lastDay) last else timing.dayOfMonth,
                        { if (it == last) lastWords else it.toString() },
                        { day -> actions.edit(draft.copy(timing = if (day == last) timing.copy(lastDay = true) else timing.copy(lastDay = false, dayOfMonth = day))) },
                        stringResource(Res.string.fbReminderDayOfMonth),
                    ),
                    time,
                )
            )
        }
        else -> KitWheel(listOf(time))
    }
}

/** A repeat change keeps the clock time; a new weekly one starts on the day it was set for. */
private fun ReminderTiming.withRepeat(repeat: ReminderRepeat): ReminderTiming =
    when (repeat) {
        ReminderRepeat.Weekly -> copy(repeat = repeat, weekdays = weekdays.ifEmpty { setOf(at.dayOfWeek.ordinal + 1) })
        ReminderRepeat.Monthly -> copy(repeat = repeat, dayOfMonth = at.day)
        else -> copy(repeat = repeat)
    }

