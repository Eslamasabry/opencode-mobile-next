package io.github.eslamasabry.oc.feature.chat.header.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipActions
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuGroup
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusContribution
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.approvalModeAskTitle
import io.github.eslamasabry.oc.ui.words.res.approvalModeAutoTitle
import io.github.eslamasabry.oc.ui.words.res.approvalModeConfirmEverythingAction
import io.github.eslamasabry.oc.ui.words.res.approvalModeConfirmEverythingBody
import io.github.eslamasabry.oc.ui.words.res.approvalModeConfirmEverythingTitle
import io.github.eslamasabry.oc.ui.words.res.approvalModeEverythingTitle
import io.github.eslamasabry.oc.ui.words.res.approvalModeMenuLabel
import io.github.eslamasabry.oc.ui.words.res.chatChipAutoApproveOn
import io.github.eslamasabry.oc.ui.words.res.chatHeaderApprovalSaveFailed
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import org.jetbrains.compose.resources.stringResource

/** What the chat hands the composer row for the approval chip. */
@Immutable
public class ApprovalChipSlot(public val state: ApprovalChipUi, public val actions: ApprovalChipActions)

/**
 * The approval chip in the composer row, next to the model (Flutter composer status strip): "Ask first",
 * "Auto-approve on" or "Approve everything"; a tap opens the three modes, the current one checked. Only "Approve
 * everything" asks first. Hidden where the server never asks.
 */
@Composable
public fun ApprovalChip(slot: ApprovalChipSlot, modifier: Modifier = Modifier) {
    val state = slot.state
    val actions = slot.actions
    if (!state.offered) return
    val group = KitMenuGroup("modes")
    fun item(choice: ApprovalChoice, label: String) =
        KitMenuItem(label = label, onSelect = { actions.chooseApproval(choice) }, group = group, checked = state.choice == choice)
    val items =
        listOf(
            item(ApprovalChoice.Ask, stringResource(Res.string.approvalModeAskTitle)),
            item(ApprovalChoice.Auto, stringResource(Res.string.approvalModeAutoTitle)),
            item(ApprovalChoice.Everything, stringResource(Res.string.approvalModeEverythingTitle)),
        )
    KitMenu(
        expanded = state.menuOpen,
        items = items,
        onDismissRequest = actions::closeApprovalMenu,
        modifier = modifier,
        title = stringResource(Res.string.approvalModeMenuLabel),
    ) {
        KitChip(
            label =
                when (state.choice) {
                    ApprovalChoice.Ask -> stringResource(Res.string.approvalModeAskTitle)
                    ApprovalChoice.Auto -> stringResource(Res.string.chatChipAutoApproveOn)
                    ApprovalChoice.Everything -> stringResource(Res.string.approvalModeEverythingTitle)
                },
            kind = KitChipKind.Action,
            tone = if (state.choice == ApprovalChoice.Ask) KitChipTone.Neutral else KitChipTone.Active,
            onClick = actions::openApprovalMenu,
            modifier = Modifier.testTag("chat-approval-chip"),
        )
    }
    KitConfirm(
        spec =
            if (!state.confirmEverything) null
            else
                KitConfirmSpec(
                    title = stringResource(Res.string.approvalModeConfirmEverythingTitle),
                    confirmLabel = stringResource(Res.string.approvalModeConfirmEverythingAction),
                    kind = KitConfirmKind.Neutral,
                    body = stringResource(Res.string.approvalModeConfirmEverythingBody),
                ),
        onConfirm = actions::confirmEverything,
        onCancel = actions::cancelEverything,
    )
    KitStatusContribution(
        state.saveFailure?.let {
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.chatHeaderApprovalSaveFailed),
                id = "chat-approval-save-failed",
                onDismiss = actions::dismissApprovalFailure,
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
            )
        },
    )
}
