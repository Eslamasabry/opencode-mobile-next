package io.github.eslamasabry.oc.feature.team.fleet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.fleet.GateKind
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import io.github.eslamasabry.oc.feature.team.ui.ageWords
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

private const val SEP = " · "

/** The clock the fleet pages age their times by: now, and again every half minute while shown. */
@Composable
public fun rememberFleetNow(): Long {
    var now by remember { mutableLongStateOf(io.github.eslamasabry.oc.core.common.CommonBindings.clock().now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = io.github.eslamasabry.oc.core.common.CommonBindings.clock().now().toEpochMilliseconds()
        }
    }
    return now
}

/**
 * The Gate sheet (Dart `showGateSheet` / `GateSheet`), as a page of the team: one question of a computer's team, or a
 * task that stopped. The ask is the title, "{kind} · {task} · {age}" sits under it, then the whole question, the
 * variant's body, the receipt of the last write, the actions and, last and folded, the ids and the host's own words.
 *
 * Each action is drawn only behind its capability; without one the sheet says where to answer instead of hiding the
 * question. The sheet's own back button is its one way out: no action repeats it.
 */
@Composable
public fun FleetGateSheet(ui: FleetGateUi, nowMs: Long, actions: FleetGateActions) {
    val title =
        when {
            ui.view != FleetGateView.Shown -> stringResource(Res.string.teamUiAgentNeedsYou)
            ui.stoppedTask != null -> stringResource(Res.string.teamUiGateRunStoppedTitle, ui.stoppedTask)
            else -> ui.ask
        }
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                subtitle = if (ui.view == FleetGateView.Shown) kicker(ui, nowMs) else null,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        loading = ui.view == FleetGateView.Waiting,
        loadingLabel = stringResource(Res.string.teamUiCardLoading),
        width = KitScreenWidth.Reading,
    ) {
        when (ui.view) {
            FleetGateView.Waiting -> Unit
            // Answered or closed on the host meanwhile: a state, not a form.
            FleetGateView.Gone ->
                KitStateView(KitStateKind.Calm, stringResource(Res.string.teamUiGateGone), Modifier.testTag("fleet-gate-gone"), icon = KitIcons.checkCircle)
            FleetGateView.Shown -> Body(ui, actions)
        }
    }
    KitConfirm(spec = confirmSpec(ui), onConfirm = actions::confirm, onCancel = actions::cancelConfirm)
}

/** The card's own header: what it is, the task it belongs to (unless the title names it) and its age. */
@Composable
private fun kicker(ui: FleetGateUi, nowMs: Long): String {
    val link =
        ui.link?.let {
            stringResource(
                when (it.kind) {
                    FleetGateLinkKind.Run -> Res.string.teamUiHomeGateLinkRun
                    FleetGateLinkKind.Work -> Res.string.teamUiHomeGateLinkWork
                    FleetGateLinkKind.Agent -> Res.string.teamUiHomeGateLinkAgent
                },
                it.name,
            )
        }
    return listOfNotNull(stringResource(kindWord(ui.kind)), link, ageWords(ui.createdAtMs, nowMs)).joinToString(SEP)
}

private fun kindWord(kind: GateKind): org.jetbrains.compose.resources.StringResource =
    when (kind) {
        GateKind.Choice -> Res.string.teamUiHomeGateKindChoice
        GateKind.Confirmation -> Res.string.teamUiHomeGateKindConfirmation
        GateKind.FreeText -> Res.string.teamUiHomeGateKindFreeText
        GateKind.GateBead -> Res.string.teamUiHomeGateKindGateBead
        GateKind.RunFailed -> Res.string.teamUiHomeGateKindRunFailed
        GateKind.ReviewReady -> Res.string.teamUiHomeGateKindReviewReady
        GateKind.Unknown -> Res.string.teamUiHomeGateKindUnknown
    }

@Composable
private fun confirmSpec(ui: FleetGateUi): KitConfirmSpec? =
    when (ui.local.confirm) {
        null -> null
        FleetGateConfirm.Approve ->
            KitConfirmSpec(
                title = stringResource(Res.string.teamUiGateAnswerConfirmApproveTitle),
                confirmLabel = stringResource(Res.string.teamUiGateAnswerApprove),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.teamUiGateAnswerConfirmApproveBody),
                cancelLabel = stringResource(Res.string.kitConfirmCancel),
            )
        FleetGateConfirm.StopRun ->
            KitConfirmSpec(
                title = stringResource(Res.string.teamUiGateAnswerConfirmCancelRunTitle),
                confirmLabel = stringResource(Res.string.teamUiGateAnswerRunCancel),
                kind = KitConfirmKind.Stop,
                body =
                    ui.stoppedTask?.trim()?.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.teamUiGateAnswerConfirmCancelNamedBody, it) }
                        ?: stringResource(Res.string.teamUiGateAnswerConfirmCancelRunBody),
                cancelLabel = stringResource(Res.string.kitConfirmCancel),
            )
    }

@Composable
private fun Body(ui: FleetGateUi, actions: FleetGateActions) {
    val interactive = ui.canRespond && !ui.hideActions
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.S2.dp, bottom = Space.S6.dp).testTag("fleet-gate-body"),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        // The card's reassurance, kept for here, comes first.
        KitText(
            stringResource(
                when (ui.kind) {
                    GateKind.RunFailed -> Res.string.teamGateCardIfIgnoredFailed
                    GateKind.ReviewReady -> Res.string.teamGateCardIfIgnoredReview
                    else -> Res.string.teamGateCardIfIgnored
                },
            ),
            Modifier.testTag("fleet-gate-if-ignored"),
            role = TypeRole.Secondary, tone = TextTone.Secondary,
        )
        when (ui.kind) {
            GateKind.Choice -> {
                Prompt(ui)
                Choices(ui, interactive, actions)
            }
            GateKind.Confirmation -> {
                if (ui.destructive) {
                    KitNotice(
                        stringResource(Res.string.gateSheetDestructiveBody),
                        Modifier.testTag("fleet-gate-destructive"),
                        title = stringResource(Res.string.teamUiGateDestructive),
                        tone = StatusTone.Failure,
                        icon = KitIcons.warning,
                    )
                }
                Prompt(ui)
            }
            GateKind.FreeText -> {
                Prompt(ui)
                if (interactive) Composer(ui, actions)
            }
            GateKind.Unknown -> Prompt(ui)
            GateKind.GateBead, GateKind.ReviewReady -> Bead(ui)
            GateKind.RunFailed -> RunFailed(ui)
        }
        Receipt(ui, actions)
        if (!ui.answers && !ui.hideActions && ui.receipt == null) {
            KitNotice(hostLine(ui), Modifier.testTag("fleet-gate-answer-on-host"), icon = KitIcons.info)
        }
        Notes(ui)
        if (!ui.hideActions) Actions(ui, actions)
        // Details close the sheet, never above the actions.
        Details(ui)
    }
}

/** Where to act, per kind and host mode: answer, close or review. */
@Composable
private fun hostLine(ui: FleetGateUi): String =
    stringResource(
        when (ui.kind) {
            GateKind.GateBead -> if (ui.onPhone) Res.string.teamUiGateCloseOnHostPhone else Res.string.teamUiGateCloseOnHost
            GateKind.ReviewReady -> if (ui.onPhone) Res.string.teamUiGateReviewOnHostPhone else Res.string.teamUiGateReviewOnHost
            else -> if (ui.onPhone) Res.string.teamUiGateAnswerOnHostPhone else Res.string.teamUiGateAnswerOnHost
        },
    )

@Composable
private fun Prompt(ui: FleetGateUi) {
    ui.prompt?.let { KitText(it, Modifier.testTag("fleet-gate-prompt"), role = TypeRole.Body) }
}

/** The options: a tap sends the answer; read-only rows when this phone cannot answer. */
@Composable
private fun Choices(ui: FleetGateUi, interactive: Boolean, actions: FleetGateActions) {
    if (ui.choices.isEmpty()) return
    val label = stringResource(Res.string.teamUiHomeGateOptions)
    if (!interactive) {
        KitRowGroup(Modifier.testTag("fleet-gate-options"), label = label) {
            ui.choices.forEachIndexed { i, choice -> KitRow(title = choice, titleMaxLines = 3, modifier = Modifier.testTag("fleet-gate-option-$i")) }
        }
        return
    }
    val sent = stringResource(Res.string.teamUiReceiptSent)
    KitChoiceList(
        choices =
            ui.choices.mapIndexed { i, choice ->
                KitChoice(i, choice, enabled = if (ui.busy) KitEnabled.Disabled(sent) else KitEnabled.Enabled)
            },
        selected = ui.local.selected,
        semanticsLabel = label,
        modifier = Modifier.testTag("fleet-gate-options"),
        sends = true,
        onSelect = actions::choose,
    )
}

/** The free-text answer: multi-line. */
@Composable
private fun Composer(ui: FleetGateUi, actions: FleetGateActions) {
    val field = remember { TextFieldState(ui.local.draft) }
    KitField(
        field,
        stringResource(Res.string.gateSheetAnswerLabel),
        Modifier.testTag("fleet-gate-composer"),
        kind = KitFieldKind.Multiline,
        placeholder = stringResource(Res.string.teamUiGateAnswerHint),
        enabled = if (ui.busy) KitEnabled.Disabled(stringResource(Res.string.teamUiReceiptSent)) else KitEnabled.Enabled,
        onTextChanged = actions::setDraft,
    )
}

/** A gate bead or a review that is ready: its description, and what the bead unblocks. */
@Composable
private fun Bead(ui: FleetGateUi) {
    val description = ui.prompt
    KitText(
        description ?: stringResource(Res.string.teamUiGateNoDescription),
        Modifier.testTag(if (description != null) "fleet-gate-description" else "fleet-gate-description-none"),
        role = if (description != null) TypeRole.Body else TypeRole.Secondary,
        tone = if (description != null) TextTone.Primary else TextTone.Secondary,
    )
    if (ui.kind == GateKind.GateBead) {
        if (ui.unblocks.isEmpty()) {
            KitText(stringResource(Res.string.teamUiGateUnblocksNone), Modifier.testTag("fleet-gate-unblocks-none"), role = TypeRole.Secondary, tone = TextTone.Secondary)
        } else {
            WorkRows(ui.unblocks, stringResource(Res.string.teamUiGateUnblocks), "fleet-gate-unblocks")
        }
    }
}

/** What went wrong in words and what to do, and the open work it holds up. The raw error is under Details. */
@Composable
private fun RunFailed(ui: FleetGateUi) {
    val (word, action) = failureWords(ui.failure)
    KitRowGroup(Modifier.testTag("fleet-gate-classification"), label = stringResource(Res.string.teamUiGateFailureClassification)) {
        KitRow(
            title = stringResource(word),
            supporting = stringResource(action),
            supportingMaxLines = 4,
            leading = KitRowLeading.Glyph(KitIcons.error),
        )
    }
    if (ui.affected.isEmpty()) {
        KitText(stringResource(Res.string.teamUiGateFailureAffectedNone), Modifier.testTag("fleet-gate-affected-none"), role = TypeRole.Secondary, tone = TextTone.Secondary)
    } else {
        WorkRows(ui.affected, stringResource(Res.string.teamUiGateFailureAffectedWork), "fleet-gate-affected")
    }
    if (ui.errorText.isEmpty()) {
        KitText(stringResource(Res.string.teamUiGateFailureErrorNone), Modifier.testTag("fleet-gate-error-none"), role = TypeRole.Secondary, tone = TextTone.Secondary)
    }
}

/** Work lines, read only here: a work item's own sheet is on its task's page. */
@Composable
private fun WorkRows(items: List<FleetGateWork>, label: String, tag: String) {
    KitRowGroup(Modifier.testTag(tag), label = label) {
        items.forEach { item ->
            KitRow(
                title = item.title,
                supporting = stringResource(workStateWord(item.state)),
                titleMaxLines = 2,
                modifier = Modifier.testTag("$tag-${item.id}"),
            )
        }
    }
}

private fun failureWords(cls: FleetFailureClass) =
    when (cls) {
        FleetFailureClass.Agent -> Res.string.teamUiGateFailureClassAgent to Res.string.teamUiGateFailureActionAgent
        FleetFailureClass.Execution -> Res.string.teamUiGateFailureClassExecution to Res.string.teamUiGateFailureActionExecution
        FleetFailureClass.Test -> Res.string.teamUiGateFailureClassTest to Res.string.teamUiGateFailureActionTest
        FleetFailureClass.MergeConflict -> Res.string.teamUiGateFailureClassMergeConflict to Res.string.teamUiGateFailureActionMergeConflict
        FleetFailureClass.Infrastructure -> Res.string.teamUiGateFailureClassInfrastructure to Res.string.teamUiGateFailureActionInfrastructure
        FleetFailureClass.Dependency -> Res.string.teamUiGateFailureClassDependency to Res.string.teamUiGateFailureActionDependency
        FleetFailureClass.Authentication -> Res.string.teamUiGateFailureClassAuthentication to Res.string.teamUiGateFailureActionAuthentication
        FleetFailureClass.Context -> Res.string.teamUiGateFailureClassContext to Res.string.teamUiGateFailureActionContext
        FleetFailureClass.Unknown -> Res.string.teamUiGateFailureClassUnknown to Res.string.teamUiGateFailureActionUnknown
    }

private fun workStateWord(state: WorkState): org.jetbrains.compose.resources.StringResource =
    when (state) {
        WorkState.Queued -> Res.string.teamUiWorkStateQueued
        WorkState.Ready -> Res.string.teamUiWorkStateReady
        WorkState.Working -> Res.string.teamUiWorkStateWorking
        WorkState.Waiting -> Res.string.teamUiWorkStateWaiting
        WorkState.Blocked -> Res.string.teamUiWorkStateBlocked
        WorkState.NeedsInput -> Res.string.teamUiWorkStateNeedsInput
        WorkState.Review -> Res.string.teamUiWorkStateReview
        WorkState.Failed -> Res.string.teamUiWorkStateFailed
        WorkState.Completed -> Res.string.teamUiWorkStateCompleted
        WorkState.Cancelled -> Res.string.teamUiWorkStateCancelled
        WorkState.Unknown -> Res.string.teamUiWorkStateUnknown
    }

/** The receipt of the last write, in place: sending, answered, not confirmed (Try again) or refused (Try again). */
@Composable
private fun Receipt(ui: FleetGateUi, actions: FleetGateActions) {
    val kind = ui.receipt ?: return
    KitReceipt(
        state =
            when (kind) {
                FleetGateReceiptKind.Sending -> KitReceiptState.Sending
                FleetGateReceiptKind.Confirmed -> KitReceiptState.Confirmed
                FleetGateReceiptKind.NotConfirmed -> KitReceiptState.NotConfirmed
                // The host's words are Details only, never the receipt's reason.
                FleetGateReceiptKind.Refused -> KitReceiptState.Refused(null)
            },
        modifier = Modifier.testTag("fleet-gate-receipt-${kind.name.lowercase()}"),
        onRetry = if (kind == FleetGateReceiptKind.NotConfirmed || kind == FleetGateReceiptKind.Refused) actions::tryAgain else null,
        label = stringResource(Res.string.teamUiReceiptAnswered),
    )
}

/** The lines that explain the actions below them. */
@Composable
private fun Notes(ui: FleetGateUi) {
    if (ui.hideActions || !ui.answers) return
    when (ui.kind) {
        GateKind.Choice, GateKind.FreeText, GateKind.Confirmation ->
            KitText(stringResource(Res.string.gateSheetAfterAnswer), Modifier.testTag("fleet-gate-note-after"), role = TypeRole.Secondary, tone = TextTone.Secondary)
        GateKind.RunFailed ->
            ui.fix?.let {
                KitText(
                    stringResource(Res.string.gateSheetFixItDetail, it.name ?: it.id),
                    Modifier.testTag("fleet-gate-note-fix"),
                    role = TypeRole.Secondary, tone = TextTone.Secondary,
                )
            }
        else -> Unit
    }
}

/** The variant's actions in the one hierarchy, each only behind its capability. */
@Composable
private fun Actions(ui: FleetGateUi, actions: FleetGateActions) {
    val sent = stringResource(Res.string.teamUiReceiptSent)
    val enabled = if (ui.busy) KitEnabled.Disabled(sent) else KitEnabled.Enabled
    when (ui.kind) {
        GateKind.FreeText -> {
            if (!ui.canRespond) return
            val needs = stringResource(Res.string.gateSheetSendNeedsText)
            val empty = ui.local.draft.isBlank()
            KitActionBlock(
                Modifier.testTag("fleet-gate-actions"),
                primary =
                    KitAction(
                        stringResource(Res.string.teamUiGateAnswerSend),
                        actions::sendText,
                        enabled = if (ui.busy) KitEnabled.Disabled(sent) else if (empty) KitEnabled.Disabled(needs) else KitEnabled.Enabled,
                        icon = KitIcons.send,
                        working = ui.busy,
                    ),
            )
        }
        GateKind.Confirmation -> {
            if (!ui.canRespond) return
            KitActionBlock(
                Modifier.testTag("fleet-gate-actions"),
                primary = KitAction(stringResource(Res.string.teamUiGateAnswerApprove), actions::approve, enabled = enabled, destructive = ui.destructive),
                // Deny sends at once: saying no loses nothing.
                secondary = KitAction(stringResource(Res.string.teamUiGateAnswerDeny), actions::deny, enabled = enabled),
            )
        }
        GateKind.RunFailed -> RunActions(ui, actions, enabled)
        // A gate bead is closed on the host: the host answers only a pending interaction (Flutter's resolver refuses a
        // bead too), so the page says where instead of offering a Mark done that cannot land.
        GateKind.Choice, GateKind.GateBead, GateKind.ReviewReady, GateKind.Unknown -> Unit
    }
}

/** A doable primary on failure: Ask the team to fix it. Then send the work again (only where a retry can recover it), and Stop work last. */
@Composable
private fun RunActions(ui: FleetGateUi, actions: FleetGateActions, enabled: KitEnabled) {
    val fix = ui.fix
    val retry = ui.retry
    val request =
        if (fix == null) ""
        else {
            val error = ui.errorText.trim()
            val cause = error.ifEmpty { stringResource(failureWords(ui.failure).first) }
            stringResource(Res.string.gateSheetFixRequest, ui.affected.firstOrNull()?.title ?: ui.ask, cause)
        }
    KitActionBlock(
        Modifier.testTag("fleet-gate-actions"),
        primary =
            fix?.let { KitAction(stringResource(Res.string.gateSheetFixIt), { actions.fix(request) }, enabled = enabled, icon = KitIcons.chat) },
        // The button names what it sends and to whom.
        secondary =
            retry?.let {
                KitAction(
                    stringResource(Res.string.teamUiGateAnswerRunRetry, it.workTitle, it.targetName ?: it.target),
                    actions::retry,
                    enabled = enabled,
                )
            },
        tertiary =
            listOfNotNull(
                ui.cancelRunId?.let { KitAction(stringResource(Res.string.teamUiGateAnswerRunCancel), actions::askStopRun, enabled = enabled, destructive = true) },
            ),
    )
}

/** Request id, session id, provider kind, the ids it names; the refusal's and the error's words, once, folded. */
@Composable
private fun Details(ui: FleetGateUi) {
    val redactor = LocalKitRedactor.current
    val text = listOfNotNull(ui.errorText.takeIf { it.isNotEmpty() }, ui.refusal).joinToString("\n\n")
    KitDetailsFold(
        label = stringResource(Res.string.teamUiTechnicalDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        modifier = Modifier.testTag("fleet-gate-technical"),
        values =
            ui.technical.map { (key, value) ->
                KitTechnicalValue(
                    stringResource(
                        when (key) {
                            FleetGateValue.RequestId -> Res.string.teamUiGateLabelRequestId
                            FleetGateValue.SessionId -> Res.string.teamUiGateLabelSessionId
                            FleetGateValue.Kind -> Res.string.teamUiGateLabelKind
                            FleetGateValue.WorkId -> Res.string.teamUiGateLabelWorkId
                            FleetGateValue.RunId -> Res.string.teamUiGateLabelRunId
                        },
                    ),
                    redactor.details(value),
                )
            },
        text = text.takeIf { it.isNotEmpty() }?.let { redactor.details(it) },
    )
}
