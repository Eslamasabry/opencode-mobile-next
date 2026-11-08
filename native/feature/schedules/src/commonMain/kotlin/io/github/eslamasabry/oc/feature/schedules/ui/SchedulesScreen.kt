package io.github.eslamasabry.oc.feature.schedules.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleRowUi
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesActions
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesUi
import io.github.eslamasabry.oc.feature.schedules.presentation.Unavailable
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitOpenSwitchRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

private const val DELETE_NAME_LENGTH =
    40 // F: lib/ui/screens/scheduling/scheduled_prompts_screen.dart:183

private const val NEW_TEXT_SCALE =
    1.6f // F: lib/ui/screens/scheduling/scheduled_prompts_screen.dart:344

/**
 * K01: calm rows, one "New scheduled prompt" at the thumb; every action is a visible row (nothing
 * hidden).
 */
@Composable
public fun SchedulesScreen(state: SchedulesUi, actions: SchedulesActions) {
    val words = scheduleWords()
    val enabled = if (state.busy) KitEnabled.Disabled(words.saving) else KitEnabled.Enabled
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = words.title,
                    subtitle = state.serverName,
                    exit = KitTopBarExit.Back,
                    exitLabel = words.back,
                    onExit = actions::back,
                )
            },
            loading = state.phase == SchedulesPhase.Loading,
            loadingLabel = words.loading,
            bottom =
                if (state.phase != SchedulesPhase.List && state.phase != SchedulesPhase.Empty) null
                else
                    ({
                        KitButton(
                            words.new,
                            actions::create,
                            Modifier.fillMaxWidth()
                                .padding(
                                    horizontal = LocalKitTokens.current.gutter.dp,
                                    vertical = Space.S2.dp,
                                ),
                            enabled = enabled,
                            icon =
                                if (LocalDensity.current.fontScale >= NEW_TEXT_SCALE) null
                                else KitIcons.add,
                        )
                    }),
            width = KitScreenWidth.Reading,
            edges = KitScreenEdges.Bleed,
        ) {
            when (state.phase) {
                SchedulesPhase.Loading -> Unit
                SchedulesPhase.Unavailable ->
                    KitStateView(
                        KitStateKind.Missing,
                        when (state.unavailable) {
                            Unavailable.Phone -> words.unavailablePhone
                            Unavailable.Storage -> words.unavailableStorage
                            else -> words.unavailableServer
                        },
                        body =
                            if (state.unavailable == Unavailable.Server) words.unavailableServerBody
                            else null,
                        primary = KitAction(words.tryAgain, actions::retry),
                    )
                SchedulesPhase.Empty ->
                    KitStateView(
                        KitStateKind.Calm,
                        if (state.error != null) stringResource(Res.string.scheduleSaveFailed)
                        else words.emptyTitle,
                        body = if (state.error != null) null else words.emptyBody,
                    )
                SchedulesPhase.List -> ScheduleList(state, actions, words, enabled)
            }
        }
        KitConfirm(
            spec =
                state.confirmDelete?.let {
                    KitConfirmSpec(
                        title =
                            state.rows
                                .firstOrNull { row -> row.id == it }
                                ?.prompt
                                ?.let { prompt ->
                                    stringResource(
                                        Res.string.scheduleDeleteNamedTitle,
                                        if (prompt.length <= DELETE_NAME_LENGTH) prompt
                                        else prompt.take(DELETE_NAME_LENGTH).trimEnd() + "…",
                                    )
                                } ?: words.deleteTitle,
                        confirmLabel = words.delete,
                        kind = KitConfirmKind.Destructive,
                        body = words.deleteBody,
                        cancelLabel = words.cancel,
                    )
                },
            onConfirm = actions::confirmDelete,
            onCancel = actions::cancelDelete,
        )
    }
}

@Composable
private fun ScheduleList(
    state: SchedulesUi,
    actions: SchedulesActions,
    words: ScheduleWords,
    enabled: KitEnabled,
) {
    val rows =
        state.rows.sortedWith(
            compareBy<ScheduleRowUi> { row ->
                    when {
                        finished(row, state.nowMs) ->
                            2 // NV: Flutter finished rank, scheduled_prompts_screen.dart:116-122.
                        row.enabled -> 0
                        else -> 1
                    }
                }
                .thenBy { it.nextRunAtMs ?: Long.MAX_VALUE }
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
    ) {
        state.error?.let { code -> item(key = "error") { KitGroupNote(words.error(code)) } }
        if (state.started != null) item(key = "started") { KitNotice(words.started) }
        item(key = "schedules") {
            KitRowGroup {
                rows.forEach { row -> ScheduleRow(row, state, actions, words, enabled) }
            }
        }
        item(key = "digest") {
            Spacer(Modifier.height(Space.S5.dp))
            KitRowGroup {
                KitSwitchRow(
                    words.digest,
                    state.digestOn,
                    supporting = words.digestBody,
                    enabled = enabled,
                    onCheckedChange = actions::setDigest,
                )
            }
        }
    }
}

/** Flutter's one-time schedules stay finished after their scheduled moment. */
private fun finished(row: ScheduleRowUi, nowMs: Long): Boolean =
    (row.recurrence.kind == RecurrenceKind.Once &&
        row.recurrence.atMs?.let { it <= nowMs } == true) ||
        (row.enabled && row.nextRunAtMs == null)

@Composable
private fun ScheduleRow(
    row: ScheduleRowUi,
    state: SchedulesUi,
    actions: SchedulesActions,
    words: ScheduleWords,
    enabled: KitEnabled,
) {
    var menuOpen by remember(row.id) { mutableStateOf(false) }
    val ended = finished(row, state.nowMs)
    val last = row.last
    val onceAt = row.recurrence.atMs
    val request = remember(row.id) { BringIntoViewRequester() }
    LaunchedEffect(state.highlight) { if (state.highlight == row.id) request.bringIntoView() }
    val rowModifier = Modifier.testTag("schedule-${row.id}").bringIntoViewRequester(request)
    val rule =
        if (row.recurrence.kind == RecurrenceKind.Once && onceAt != null)
            moment(onceAt, state.nowMs, words)
        else repeatWords(row.recurrence, state.nowMs, words)
    val line =
        when {
            ended ->
                last?.let { lastLine(it, state.nowMs, words) }
                    ?: stringResource(Res.string.scheduleFinished)
            last?.status == RunStatus.Running -> stringResource(Res.string.scheduleSendingNow)
            !row.enabled -> "${words.paused} · $rule"
            else ->
                listOfNotNull(rule, last?.let { lastLine(it, state.nowMs, words) })
                    .joinToString(" · ")
        }
    if (ended) {
        KitRow(
            row.prompt,
            modifier = rowModifier,
            supporting = line,
            selected = state.highlight == row.id,
            enabled = enabled.isEnabled,
            disabledReason = enabled.reason,
            trailing =
                KitRowTrailing.Action(
                    KitLineAction(
                        words.delete,
                        { actions.askDelete(row.id) },
                        enabled = enabled.isEnabled,
                        disabledReason = enabled.reason,
                        icon = KitIcons.delete,
                    )
                ),
            onClick = { actions.edit(row.id) },
        )
    } else
        KitOpenSwitchRow(
            row.prompt,
            row.enabled,
            words.edit,
            { actions.edit(row.id) },
            { actions.setEnabled(row.id, it) },
            rowModifier,
            supporting = line,
            enabled = enabled,
            highlighted = state.highlight == row.id,
            actions = {
                val items =
                    listOfNotNull(
                        KitMenuItem(
                            words.runNow,
                            { actions.runNow(row.id) },
                            KitIcons.play,
                            enabled = enabled,
                        ),
                        last?.sessionId?.let { session ->
                            KitMenuItem(
                                words.openConversation,
                                { actions.openConversation(session) },
                                KitIcons.chat,
                                enabled = enabled,
                            )
                        },
                        KitMenuItem(
                            words.delete,
                            { actions.askDelete(row.id) },
                            KitIcons.delete,
                            destructive = true,
                            enabled = enabled,
                        ),
                    )
                KitMenu(menuOpen, items, { menuOpen = false }) {
                    KitIconButton(
                        KitIcons.more,
                        "${stringResource(Res.string.kitMore)} · ${row.prompt}",
                        { menuOpen = true },
                    )
                }
            },
        )
}

/** The production schedule copy is selected by the app locale's native resources. */
@Composable public fun scheduleWords(): ScheduleWords = resourceScheduleWords()
