package io.github.eslamasabry.oc.feature.schedules.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleRowUi
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesActions
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesPhase
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesUi
import io.github.eslamasabry.oc.feature.schedules.presentation.Unavailable
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** K01: calm rows, one "New scheduled prompt" at the thumb; every action is a visible row (nothing hidden). */
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
                if (state.phase == SchedulesPhase.List) {
                    {
                        KitButton(
                            words.new,
                            actions::create,
                            Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S2.dp),
                        )
                    }
                } else {
                    null
                },
            width = KitScreenWidth.Reading,
        ) {
            when (state.phase) {
                SchedulesPhase.Loading -> KitStateView(KitStateKind.Loading, words.loading)
                SchedulesPhase.Unavailable ->
                    KitStateView(
                        KitStateKind.Missing,
                        when (state.unavailable) {
                            Unavailable.Phone -> words.unavailablePhone
                            Unavailable.Storage -> words.unavailableStorage
                            else -> words.unavailableServer
                        },
                        body = if (state.unavailable == Unavailable.Server) words.unavailableServerBody else null,
                        primary = KitAction(words.tryAgain, actions::retry),
                    )
                SchedulesPhase.Empty ->
                    KitStateView(
                        KitStateKind.Empty,
                        words.emptyTitle,
                        body = words.emptyBody,
                        primary = KitAction(words.new, actions::create),
                    )
                SchedulesPhase.List -> ScheduleList(state, actions, words, enabled)
            }
        }
        KitConfirm(
            spec =
                state.confirmDelete?.let {
                    KitConfirmSpec(
                        title = words.deleteTitle,
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
private fun ScheduleList(state: SchedulesUi, actions: SchedulesActions, words: ScheduleWords, enabled: KitEnabled) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = LocalKitTokens.current.gutter.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        state.error?.let { code -> item(key = "error") { KitErrorNotice(words.error(code)) } }
        if (state.started != null) item(key = "started") { KitNotice(words.started) }
        for (row in state.rows) {
            item(key = row.id) { ScheduleGroup(row, state.nowMs, state.highlight == row.id, actions, words, enabled) }
        }
        item(key = "digest") {
            KitRowGroup {
                KitSwitchRow(words.digest, state.digestOn, supporting = words.digestBody, enabled = enabled, onCheckedChange = actions::setDigest)
            }
        }
    }
}

@Composable
private fun ScheduleGroup(
    row: ScheduleRowUi,
    nowMs: Long,
    highlighted: Boolean,
    actions: SchedulesActions,
    words: ScheduleWords,
    enabled: KitEnabled,
) {
    KitRowGroup(
        destructive = {
            KitButton(words.delete, { actions.askDelete(row.id) }, role = KitButtonRole.Tertiary, destructive = true, enabled = enabled)
        },
    ) {
        KitRow(
            title = row.prompt,
            supporting = scheduleLine(row, nowMs, words),
            supportingMaxLines = 3,
            trailing = KitRowTrailing.Chevron,
            selected = highlighted,
            onClickLabel = words.edit,
            onClick = { actions.edit(row.id) },
        )
        KitSwitchRow(words.active, row.enabled, enabled = enabled, onCheckedChange = { actions.setEnabled(row.id, it) })
        KitRow(
            title = words.runNow,
            enabled = enabled.isEnabled,
            onClick = { actions.runNow(row.id) },
        )
        row.last?.let { last ->
            val session = last.sessionId
            KitRow(
                title = lastLine(last, nowMs, words),
                supporting = words.why(last.errorCode).ifEmpty { null },
                supportingMaxLines = 3,
                trailing = if (session != null) KitRowTrailing.Chevron else KitRowTrailing.None,
                onClickLabel = if (session != null) words.openConversation else null,
                onClick = session?.let { { actions.openConversation(it) } },
            )
        }
    }
}

/** The words for the app's current language (Arabic or English; other languages fall back to English for now). */
@Composable
public fun scheduleWords(): ScheduleWords = if (Locale.current.language == "ar") ScheduleWords.Arabic else ScheduleWords.English
