package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.eslamasabry.oc.engine.settings.consent.*
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
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
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.settingsV3Permissions),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)
        ) {
            if (ui.failed) KitNotice(stringResource(Res.string.settings40_answer_failed))
            val facts = ui.facts
            if (facts == null) {
                KitNotice(stringResource(Res.string.consentStorageFailed))
                KitRow(
                    title = stringResource(Res.string.kitTryAgain),
                    onClick = actions::refresh,
                )
            } else {
                KitGroupNote(facts.name)
                if (!facts.writable || facts.declined == null)
                    KitNotice(stringResource(Res.string.consentStorageFailed))
                if (facts.answers.isNotEmpty())
                    KitRowGroup(label = stringResource(Res.string.consentGroupLabel)) {
                        facts.answers.forEach { row ->
                            val accepted = row.choice == ConsentChoice.Accepted
                            KitRow(
                                title = answerTitle(row.kind),
                                supporting =
                                    if (accepted)
                                        stringResource(
                                            if (row.kind == InFlowConsentKind.NeedsYouNotifications)
                                                Res.string.consentAllowedNeedsYou
                                            else Res.string.consentAllowedSystem
                                        )
                                    else answerWhy(row.kind),
                                supportingMaxLines = 3,
                                trailing =
                                    KitRowTrailing.Value(
                                        stringResource(
                                            when (row.choice) {
                                                ConsentChoice.Accepted ->
                                                    Res.string.consentValueAllowed
                                                ConsentChoice.Denied ->
                                                    Res.string.consentValueDeclined
                                                else -> Res.string.consentValueUnanswered
                                            }
                                        )
                                    ),
                                enabled = !ui.busy && (accepted || facts.writable),
                                disabledReason = stringResource(Res.string.consentSaveFailed),
                                onClick = { actions.answer(row.kind) },
                            )
                        }
                    }
                val declined = facts.declined ?: 0
                if (declined > 0)
                    KitRowGroup {
                        KitRow(
                            title = stringResource(Res.string.consentRowAlwaysAllow),
                            supporting = stringResource(Res.string.consentWhyAlwaysAllow),
                            supportingMaxLines = 3,
                            trailing =
                                KitRowTrailing.Value(
                                    pluralStringResource(
                                        Res.plurals.consentValueDeclinedCount,
                                        declined,
                                        declined,
                                    )
                                ),
                            enabled = !ui.busy,
                            onClick = actions::askAgain,
                        )
                    }
                if (facts.savedGrants)
                    KitRowGroup(label = stringResource(Res.string.automationWithoutAskingLabel)) {
                        KitRow(
                            title = stringResource(Res.string.e7LibraryAlwaysAllowedActions),
                            supporting = stringResource(Res.string.automationSavedRulesDetail),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::grants,
                        )
                    }
                if (
                    facts.writable &&
                        facts.declined != null &&
                        facts.answers.isEmpty() &&
                        declined == 0 &&
                        !facts.savedGrants
                )
                    KitGroupNote(stringResource(Res.string.settings40_no_answers))
            }
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
                                InFlowConsentKind.BatteryExemption -> Res.string.consentBatteryTitle
                                InFlowConsentKind.MakerAutoStart -> Res.string.consentMakerTitle
                                InFlowConsentKind.NeedsYouNotifications ->
                                    Res.string.firstRunNotifyTitle
                            }
                        ),
                        stringResource(
                            when (kind) {
                                InFlowConsentKind.BatteryExemption -> Res.string.consentBatteryAllow
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
        onConfirm = actions::confirm,
        onCancel = actions::cancel,
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
