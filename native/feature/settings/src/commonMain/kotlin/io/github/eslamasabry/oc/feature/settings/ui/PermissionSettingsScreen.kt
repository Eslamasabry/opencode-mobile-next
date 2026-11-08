package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.eslamasabry.oc.engine.settings.consent.*
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Asked questions only, in need-of-an-answer order; every action stays pinned to the named saved
 * server.
 */
@Composable
public fun PermissionSettingsScreen(
    ui: PermissionSettingsUi,
    actions: PermissionSettingsViewModel,
) {
    LifecycleResumeEffect(actions) {
        actions.refresh()
        onPauseOrDispose {}
    }
    PermissionSettingsContent(
        ui,
        actions::back,
        actions::refresh,
        actions::answer,
        actions::askAgain,
        actions::grants,
        actions::confirm,
        actions::cancel,
    )
}

/** The state-only page keeps the same server-bound actions as the route's view model. */
@Composable
internal fun PermissionSettingsContent(
    ui: PermissionSettingsUi,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAnswer: (InFlowConsentKind) -> Unit,
    onAskAgain: () -> Unit,
    onGrants: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.settingsV3Permissions),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = onBack,
            )
        },
        width = KitScreenWidth.Reading,
        edges = KitScreenEdges.Bleed,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("permissions-automation")
                .padding(top = Space.S2.dp, bottom = Space.S6.dp),
            verticalArrangement = Arrangement.spacedBy(LocalKitTokens.current.sectionGap.dp),
        ) {
            if (ui.failed && ui.facts != null)
                KitNotice(stringResource(Res.string.settings40_answer_failed))
            val facts = ui.facts
            if (facts == null) {
                if (ui.failed) {
                    KitNotice(stringResource(Res.string.consentStorageFailed))
                    KitRow(title = stringResource(Res.string.kitTryAgain), onClick = onRefresh)
                } else KitSkeletonRows()
            } else {
                if (!facts.writable || facts.declined == null)
                    KitNotice(stringResource(Res.string.consentStorageFailed))
                val answers = facts.answers.filter { it.choice != ConsentChoice.Unseen }
                val declined = facts.declined ?: 0
                if (answers.isNotEmpty() || declined > 0)
                    KitRowGroup(
                        label = stringResource(Res.string.consentGroupLabel),
                        leadingTiles = true,
                    ) {
                        // Flutter automation_settings_screen.dart:527-533: unanswered, declined,
                        // declined offers, then allowed. Never display an unasked question.
                        answers
                            .filter { it.choice == ConsentChoice.Offered }
                            .forEach {
                                PermissionAnswer(it, facts.writable, ui.busy, onAnswer)
                            }
                        answers
                            .filter { it.choice == ConsentChoice.Denied }
                            .forEach {
                                PermissionAnswer(it, facts.writable, ui.busy, onAnswer)
                            }
                        if (declined > 0)
                            KitRow(
                                title = stringResource(Res.string.consentRowAlwaysAllow),
                                modifier = Modifier.testTag("automation-consent-always-allow"),
                                leading = KitRowLeading.Glyph(KitIcons.privacy),
                                supporting = stringResource(Res.string.consentWhyAlwaysAllow),
                                supportingMaxLines =
                                    3, // F: lib/ui/screens/automation_settings_screen.dart:519
                                trailing =
                                    KitRowTrailing.Value(
                                        pluralStringResource(
                                            Res.plurals.consentValueDeclinedCount,
                                            declined,
                                            declined,
                                        )
                                    ),
                                enabled = !ui.busy,
                                disabledReason = stringResource(Res.string.automationSaving),
                                onClick = onAskAgain,
                            )
                        answers
                            .filter { it.choice == ConsentChoice.Accepted }
                            .forEach {
                                PermissionAnswer(it, facts.writable, ui.busy, onAnswer)
                            }
                    }
                if (facts.savedGrants)
                    KitRowGroup(
                        label = stringResource(Res.string.automationWithoutAskingLabel),
                        leadingTiles = true,
                    ) {
                        KitRow(
                            title = stringResource(Res.string.e7LibraryAlwaysAllowedActions),
                            modifier = Modifier.testTag("automation-saved-permissions"),
                            leading = KitRowLeading.Glyph(KitIcons.privacy),
                            supporting = stringResource(Res.string.automationSavedRulesDetail),
                            supportingMaxLines =
                                2, // F: lib/ui/screens/automation_settings_screen.dart:360
                            trailing = KitRowTrailing.Chevron,
                            enabled = !ui.busy,
                            disabledReason = stringResource(Res.string.automationSaving),
                            onClick = onGrants,
                        )
                    }
                if (
                    facts.writable &&
                        facts.declined != null &&
                        answers.isEmpty() &&
                        declined == 0 &&
                        !facts.savedGrants
                )
                    KitGroupNote(stringResource(Res.string.settings40_no_answers))
            }
        }
        val kind = ui.confirm
        KitConfirm(
            spec =
                when {
                    ui.confirmAgain ->
                        KitConfirmSpec(
                            stringResource(Res.string.consentAlwaysAgainTitle),
                            stringResource(Res.string.consentAlwaysAgainConfirm),
                            body = stringResource(Res.string.consentAlwaysAgainBody),
                        )
                    kind != null ->
                        KitConfirmSpec(
                            stringResource(
                                when (kind) {
                                    InFlowConsentKind.BatteryExemption ->
                                        Res.string.consentBatteryTitle
                                    InFlowConsentKind.MakerAutoStart -> Res.string.consentMakerTitle
                                    InFlowConsentKind.NeedsYouNotifications ->
                                        Res.string.firstRunNotifyTitle
                                }
                            ),
                            stringResource(
                                when (kind) {
                                    InFlowConsentKind.BatteryExemption ->
                                        Res.string.consentBatteryAllow
                                    InFlowConsentKind.MakerAutoStart -> Res.string.consentMakerAllow
                                    InFlowConsentKind.NeedsYouNotifications ->
                                        Res.string.consentNeedsYouAllow
                                }
                            ),
                            body =
                                stringResource(
                                    when (kind) {
                                        InFlowConsentKind.BatteryExemption ->
                                            Res.string.consentBatteryBody
                                        InFlowConsentKind.MakerAutoStart ->
                                            Res.string.consentMakerBodyUnnamed
                                        InFlowConsentKind.NeedsYouNotifications ->
                                            Res.string.firstRunNotifyBody
                                    }
                                ),
                        )
                    else -> null
                },
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun PermissionAnswer(
    row: InFlowConsentRow,
    writable: Boolean,
    busy: Boolean,
    onAnswer: (InFlowConsentKind) -> Unit,
) {
    val accepted = row.choice == ConsentChoice.Accepted
    val tag =
        when (row.kind) {
            InFlowConsentKind.BatteryExemption -> "batteryExemption"
            InFlowConsentKind.MakerAutoStart -> "makerAutoStart"
            InFlowConsentKind.NeedsYouNotifications -> "needsYouNotifications"
        }
    KitRow(
        title = answerTitle(row.kind),
        modifier = Modifier.testTag("automation-consent-$tag"),
        leading =
            KitRowLeading.Glyph(
                when (row.kind) {
                    InFlowConsentKind.BatteryExemption -> KitIcons.batteryWarning
                    InFlowConsentKind.MakerAutoStart -> KitIcons.sync
                    InFlowConsentKind.NeedsYouNotifications -> KitIcons.notificationImportant
                }
            ),
        supporting =
            if (accepted)
                stringResource(
                    if (row.kind == InFlowConsentKind.NeedsYouNotifications)
                        Res.string.consentAllowedNeedsYou
                    else Res.string.consentAllowedSystem
                )
            else answerWhy(row.kind),
        supportingMaxLines = 3, // F: lib/ui/screens/automation_settings_screen.dart:491
        trailing =
            KitRowTrailing.Value(
                stringResource(
                    if (row.kind == InFlowConsentKind.NeedsYouNotifications) {
                        if (accepted) Res.string.settingsV3On else Res.string.settingsV3Off
                    } else
                        when (row.choice) {
                            ConsentChoice.Accepted -> Res.string.consentValueAllowed
                            ConsentChoice.Denied -> Res.string.consentValueDeclined
                            else -> Res.string.consentValueUnanswered
                        }
                )
            ),
        enabled = !busy && (accepted || writable),
        disabledReason =
            stringResource(
                if (busy) Res.string.automationSaving else Res.string.consentStorageFailed
            ),
        onClick = { onAnswer(row.kind) },
    )
}

@Composable
private fun answerTitle(kind: InFlowConsentKind): String =
    stringResource(
        when (kind) {
            InFlowConsentKind.BatteryExemption -> Res.string.consentRowBattery
            InFlowConsentKind.MakerAutoStart -> Res.string.consentRowMaker
            InFlowConsentKind.NeedsYouNotifications -> Res.string.consentRowNeedsYou
        }
    )

@Composable
private fun answerWhy(kind: InFlowConsentKind): String =
    stringResource(
        when (kind) {
            InFlowConsentKind.BatteryExemption -> Res.string.consentWhyBattery
            InFlowConsentKind.MakerAutoStart -> Res.string.consentWhyMaker
            InFlowConsentKind.NeedsYouNotifications -> Res.string.consentWhyNeedsYou
        }
    )
