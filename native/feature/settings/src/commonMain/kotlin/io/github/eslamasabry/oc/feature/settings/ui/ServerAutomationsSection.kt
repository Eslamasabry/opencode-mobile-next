package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.feature.settings.presentation.AutomationsNotice
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsActionFailed
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDeleteBody
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDeleteFrom
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDeleteTitle
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDetailAction
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDetailExpression
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDetailLastRun
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDetailOutput
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDetailRuns
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsDetailSchedule
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsLoadFailed
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsNext
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsPause
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsResume
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsSection
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsStopAll
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsStopAllBody
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsStopAllTitle
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsStoppedAll
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsStoppedPartial
import io.github.eslamasabry.oc.ui.words.res.serverAutomationsSummaryPair
import io.github.eslamasabry.oc.ui.words.res.serverSettingsSaving
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * B-605: server-owned automations listed under one clear "Automations" section on the server page.
 * Hidden when the server does not support automations, disables them (403), or has none (unless a
 * transient error or pause notice is shown).
 */
@Composable
internal fun ServerAutomationsSection(state: ServerSettingsUi, actions: ServerSettingsActions) {
    if (!state.showAutomations) return
    val hasContent =
        state.automations.isNotEmpty() ||
            state.automationsLoadFailed ||
            state.automationsNotice != null
    if (!hasContent) return
    val serverName = state.facts?.name.orEmpty()
    KitSectionLabel(stringResource(Res.string.serverAutomationsSection))
    state.automationsNotice?.let { notice -> AutomationNoticeBanner(notice, serverName) }
    if (state.automationsLoadFailed) {
        KitNotice(
            message = stringResource(Res.string.serverAutomationsLoadFailed, serverName),
            actions =
                listOf(
                    KitLineAction(
                        stringResource(Res.string.kitTryAgain),
                        actions::refreshAutomations,
                    )
                ),
        )
    }
    if (state.automations.isNotEmpty()) {
        KitRowGroup(Modifier.testTag("server-automations-list")) {
            state.automations.forEach { item ->
                KitRow(
                    title = item.name,
                    supporting = automationRowSummary(item, state.nowMs, state.timeZoneId),
                    supportingMaxLines = ROW_SUPPORTING_LINES,
                    onClick = { actions.openAutomation(item.id) },
                    trailing = KitRowTrailing.Chevron,
                    modifier = Modifier.testTag("server-automation-row-${item.id}"),
                )
            }
        }
        if (state.activeAutomationsCount > 0) {
            KitButton(
                label = stringResource(Res.string.serverAutomationsStopAll, serverName),
                onClick = actions::askStopAllAutomations,
                modifier = Modifier.testTag("server-automations-stop-all"),
                role = KitButtonRole.Secondary,
                destructive = true,
                enabled =
                    if (state.automationsWorking)
                        KitEnabled.Disabled(stringResource(Res.string.serverSettingsSaving))
                    else KitEnabled.Enabled,
            )
        }
    }
}

/** Detail sheet and confirmations for server automations (B-605). */
@Composable
internal fun ServerAutomationsOverlays(state: ServerSettingsUi, actions: ServerSettingsActions) {
    val serverName = state.facts?.name.orEmpty()
    val selected = state.selectedAutomation
    if (selected != null) {
        AutomationDetailSheet(selected, serverName, state, actions)
    }
    val deleteTarget = state.deleteAutomationTarget
    KitConfirm(
        spec =
            deleteTarget?.let { target ->
                KitConfirmSpec(
                    title =
                        stringResource(
                            Res.string.serverAutomationsDeleteTitle,
                            target.name,
                            serverName,
                        ),
                    confirmLabel =
                        stringResource(
                            Res.string.serverAutomationsDeleteFrom,
                            target.name,
                            serverName,
                        ),
                    kind = KitConfirmKind.Destructive,
                    icon = KitIcons.delete,
                    body = stringResource(Res.string.serverAutomationsDeleteBody, serverName),
                )
            },
        onConfirm = actions::confirmDeleteAutomation,
        onCancel = actions::cancelDeleteAutomation,
    )
    KitConfirm(
        spec =
            if (state.stopAllAutomationsConfirm && state.activeAutomationsCount > 0)
                KitConfirmSpec(
                    title = stringResource(Res.string.serverAutomationsStopAllTitle, serverName),
                    confirmLabel = stringResource(Res.string.serverAutomationsStopAll, serverName),
                    kind = KitConfirmKind.Stop,
                    icon = KitIcons.pause,
                    body =
                        pluralStringResource(
                            Res.plurals.serverAutomationsStopAllBody,
                            state.activeAutomationsCount,
                            state.activeAutomationsCount,
                            serverName,
                        ),
                )
            else null,
        onConfirm = actions::confirmStopAllAutomations,
        onCancel = actions::cancelStopAllAutomations,
    )
}

@Composable
private fun AutomationNoticeBanner(notice: AutomationsNotice, serverName: String) {
    val message =
        when (notice) {
            is AutomationsNotice.StoppedAll ->
                pluralStringResource(
                    Res.plurals.serverAutomationsStoppedAll,
                    notice.stopped,
                    notice.stopped,
                    serverName,
                )
            is AutomationsNotice.StoppedPartial ->
                pluralStringResource(
                    Res.plurals.serverAutomationsStoppedPartial,
                    notice.stopped,
                    notice.stopped,
                    notice.total,
                    serverName,
                )
            AutomationsNotice.ActionFailed ->
                stringResource(Res.string.serverAutomationsActionFailed, serverName)
        }
    KitNotice(message, Modifier.testTag("server-automations-notice"))
}

@Composable
private fun AutomationDetailSheet(
    item: ServerAutomation,
    serverName: String,
    state: ServerSettingsUi,
    actions: ServerSettingsActions,
) {
    val enabled =
        if (state.automationsWorking)
            KitEnabled.Disabled(stringResource(Res.string.serverSettingsSaving))
        else KitEnabled.Enabled
    val deleteAction =
        KitAction(
            label = stringResource(Res.string.serverAutomationsDeleteFrom, item.name, serverName),
            onClick = { actions.askDeleteAutomation(item.id) },
            enabled = enabled,
            icon = KitIcons.delete,
            destructive = true,
        )
    KitSheet(
        visible = true,
        onDismissRequest = actions::closeAutomation,
        title = item.name,
        subtitle = automationStatusLabel(item.status),
        closeLabel = stringResource(Res.string.kitSheetClose),
        primary = automationPrimaryAction(item, serverName, enabled, actions),
        tertiary = listOf(deleteAction),
        loading = state.automationsWorking,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            if (state.automationSheetFailed) {
                KitNotice(
                    message = stringResource(Res.string.serverAutomationsActionFailed, serverName),
                    modifier = Modifier.testTag("server-automation-sheet-error"),
                )
            }
            AutomationDetailFacts(item, state.nowMs, state.timeZoneId)
            AutomationActionBody(item.action)
            val cronExpr =
                (item.trigger as? AutomationTrigger.Cron)?.expression?.trim()?.takeIf {
                    it.isNotEmpty()
                }
            if (cronExpr != null) {
                KitDetailsFold(
                    label = stringResource(Res.string.serverAutomationsDetailExpression),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    notes = listOf(cronExpr),
                )
            }
            val output = item.lastOutput
            if (output != null) {
                KitDetailsFold(
                    label = stringResource(Res.string.serverAutomationsDetailOutput),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    text = output,
                )
            }
        }
    }
}

@Composable
private fun automationPrimaryAction(
    item: ServerAutomation,
    serverName: String,
    enabled: KitEnabled,
    actions: ServerSettingsActions,
): KitAction? =
    when (item.status) {
        AutomationStatus.Active ->
            KitAction(
                label = stringResource(Res.string.serverAutomationsPause, item.name, serverName),
                onClick = { actions.pauseAutomation(item.id) },
                enabled = enabled,
                icon = KitIcons.pause,
            )
        AutomationStatus.Paused ->
            KitAction(
                label = stringResource(Res.string.serverAutomationsResume, item.name, serverName),
                onClick = { actions.resumeAutomation(item.id) },
                enabled = enabled,
                icon = KitIcons.play,
            )
        AutomationStatus.Done,
        AutomationStatus.Unknown -> null
    }

@Composable
private fun AutomationDetailFacts(
    item: ServerAutomation,
    nowMs: Long,
    fallbackZoneId: String?,
) {
    val zoneId = item.timeZone ?: fallbackZoneId
    val triggerText = automationTriggerDescription(item.trigger, nowMs, zoneId)
    val nextText =
        item.nextDueAtMs
            ?.takeIf { item.status == AutomationStatus.Active }
            ?.let {
                stringResource(
                    Res.string.serverAutomationsNext,
                    formatAutomationMoment(it, nowMs, zoneId),
                )
            }
    val scheduleText =
        when {
            triggerText != null && nextText != null ->
                stringResource(Res.string.serverAutomationsSummaryPair, triggerText, nextText)
            triggerText != null -> triggerText
            nextText != null -> nextText
            else -> ""
        }
    KitRowGroup {
        if (scheduleText.isNotEmpty()) {
            KitRow(
                title = stringResource(Res.string.serverAutomationsDetailSchedule),
                supporting = scheduleText,
                supportingMaxLines = ROW_SUPPORTING_LINES,
            )
        }
        KitRow(
            title = stringResource(Res.string.serverAutomationsDetailAction),
            supporting = automationActionKindLabel(item.action),
        )
        KitRow(
            title = stringResource(Res.string.serverAutomationsDetailLastRun),
            supporting = automationLastRunDescription(item, nowMs, zoneId),
            supportingMaxLines = 2,
        )
        if (item.runCount > 0) {
            KitRow(
                title = stringResource(Res.string.serverAutomationsDetailRuns),
                supporting = Numbers.digits(item.runCount.toString()),
            )
        }
    }
}

@Composable
private fun AutomationActionBody(action: AutomationAction) {
    when (action) {
        is AutomationAction.Prompt ->
            if (action.text.isNotBlank()) {
                KitText(action.text, role = TypeRole.Secondary, tone = TextTone.Secondary)
            }
        is AutomationAction.Shell ->
            if (action.command.isNotBlank()) {
                KitCodeBlock(source = action.command, kind = KitCodeKind.Command)
            }
        is AutomationAction.Code ->
            if (action.source.isNotBlank()) {
                KitCodeBlock(source = action.source, kind = KitCodeKind.Code)
            }
        AutomationAction.Unknown -> Unit
    }
}

private const val ROW_SUPPORTING_LINES = 3
