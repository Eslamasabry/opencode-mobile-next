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
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalSetBy
import io.github.eslamasabry.oc.feature.chat.nudges.permissionWords
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.approvalModeAskDetail
import io.github.eslamasabry.oc.ui.words.res.approvalModeAutoDetail
import io.github.eslamasabry.oc.ui.words.res.approvalModeEverythingDetail
import io.github.eslamasabry.oc.ui.words.res.approvalsSheetFootnote
import io.github.eslamasabry.oc.ui.words.res.approvalsSheetHistory
import io.github.eslamasabry.oc.ui.words.res.approvalsSheetSetByServer
import io.github.eslamasabry.oc.ui.words.res.approvalsSheetSubagents
import io.github.eslamasabry.oc.ui.words.res.approvalsUiAutoApproved
import io.github.eslamasabry.oc.ui.words.res.approvalsUiFollowParent
import io.github.eslamasabry.oc.ui.words.res.approvalsUiInheritedFrom
import io.github.eslamasabry.oc.ui.words.res.kitCopy
import io.github.eslamasabry.oc.ui.kit.row.KitLeadingSwitchRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
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
import io.github.eslamasabry.oc.ui.words.res.approvalModeSettings
import io.github.eslamasabry.oc.ui.words.res.chatStripAutoApprove
import io.github.eslamasabry.oc.ui.words.res.composerSettingApprovalDetail
import io.github.eslamasabry.oc.ui.words.res.chatChipAutoApproveOn
import io.github.eslamasabry.oc.ui.words.res.chatHeaderApprovalSaveFailed
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import org.jetbrains.compose.resources.stringResource

/** What the chat hands the composer row for the approval chip. */
@Immutable
public class ApprovalChipSlot(public val state: ApprovalChipUi, public val actions: ApprovalChipActions)

/**
 * The approval chip at the start of the strip above the message box (Flutter composer status strip): "Ask first" or
 * "Auto-approve on"; a tap opens the three modes, the current one checked. Only "Approve
 * everything" asks first. Hidden where the server never asks.
 */
@Composable
public fun ApprovalChip(slot: ApprovalChipSlot, modifier: Modifier = Modifier) {
    val state = slot.state
    val actions = slot.actions
    if (!state.offered) return
    androidx.compose.foundation.layout.Box(modifier) {
        // Flutter `_AutoApprovalIndicator` (approvals_sheet.dart:257-330), at the start of the strip above the
        // message box: an action chip with the shield, reading "Ask first" while asking and "Auto-approve on" (the
        // kit's selected check) for either automatic mode; the menu says which one.
        val words =
            when (state.choice) {
                ApprovalChoice.Ask -> stringResource(Res.string.approvalModeAskTitle)
                ApprovalChoice.Auto,
                ApprovalChoice.Everything -> stringResource(Res.string.chatChipAutoApproveOn)
            }
        // On the glass slab (owner 2026-11-10) the chip is a quiet part of the message box's own row; on a phone it
        // is the shield alone, tinted while approvals are automatic, so the model's name keeps the room. The menu and
        // the screen reader still say the words.
        if (io.github.eslamasabry.oc.ui.kitchat.composer.LocalKitComposerSlab.current)
            io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerChip(
                label = words,
                onClick = actions::openApprovalMenu,
                modifier = Modifier.testTag("chat-approval-chip"),
                icon = if (state.choice != ApprovalChoice.Ask) KitIcons.shieldFilled else KitIcons.shield,
                opensPicker = false,
                active = state.choice != ApprovalChoice.Ask,
                wordless =
                    io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow.current.cls ==
                        io.github.eslamasabry.oc.ui.design.layout.WindowClass.Compact,
            )
        else
        KitChip(
            label = words,
            kind = KitChipKind.Action,
            icon = KitIcons.shield,
            selected = if (state.choice != ApprovalChoice.Ask) true else null,
            onClick = actions::openApprovalMenu,
            modifier = Modifier.testTag("chat-approval-chip"),
        )
    }
    ApprovalSheet(state, actions)
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

/**
 * The approval rows of the composer's "+" sheet (Flutter `_composerSettings`, chat_composer_region.dart:134-160):
 * "Auto-approve" with its switch (on for either automatic mode; off asks first), then "Approval settings…", which
 * opens the three modes. Nothing where the server never asks. [close] closes the sheet before the modes open.
 */
@Composable
public fun ApprovalSettingsRows(slot: ApprovalChipSlot, close: () -> Unit) {
    val state = slot.state
    val actions = slot.actions
    if (!state.offered) return
    KitLeadingSwitchRow(
        title = stringResource(Res.string.chatStripAutoApprove),
        leading = KitRowLeading.Glyph(KitIcons.shield),
        checked = state.choice != ApprovalChoice.Ask,
        onCheckedChange = { on -> actions.chooseApproval(if (on) ApprovalChoice.Auto else ApprovalChoice.Ask) },
        supporting = stringResource(Res.string.composerSettingApprovalDetail),
        supportingMaxLines = 2, // F: lib/ui/screens/chat/composer_tools.dart:142
        modifier = Modifier.testTag("composer-setting-approval"),
    )
    KitRow(
        title = stringResource(Res.string.approvalModeSettings),
        leading = KitRowLeading.Glyph(KitIcons.permissions),
        onClick = {
            close()
            actions.openApprovalMenu()
        },
        modifier = Modifier.testTag("composer-setting-approval-settings"),
    )
}

/**
 * C2 (Flutter `approvals_sheet.dart`): the three modes with a sentence each, where the mode comes from, the switch that
 * lets sub-agents follow an automatic conversation, "Follow parent again", the record of what was approved on the
 * person's behalf (last five), and the note that the server's own rules still apply.
 */
@Composable
private fun ApprovalSheet(state: ApprovalChipUi, actions: ApprovalChipActions) {
    val asks = stringResource(Res.string.approvalModeAskTitle)
    val auto = stringResource(Res.string.approvalModeAutoTitle)
    val everything = stringResource(Res.string.approvalModeEverythingTitle)
    val askDetail = stringResource(Res.string.approvalModeAskDetail)
    val autoDetail = stringResource(Res.string.approvalModeAutoDetail)
    val everythingDetail = stringResource(Res.string.approvalModeEverythingDetail)
    KitSheet(
        visible = state.menuOpen,
        onDismissRequest = actions::closeApprovalMenu,
        title = stringResource(Res.string.approvalModeMenuLabel),
        icon = KitIcons.shield,
    ) {
        Column(Modifier.fillMaxWidth().testTag("approvals-sheet"), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitChoiceList(
                choices =
                    listOf(
                        KitChoice(ApprovalChoice.Ask, asks, askDetail),
                        KitChoice(ApprovalChoice.Auto, auto, autoDetail),
                        KitChoice(ApprovalChoice.Everything, everything, everythingDetail),
                    ),
                selected = state.choice,
                semanticsLabel = stringResource(Res.string.approvalModeMenuLabel),
                onSelect = actions::chooseApproval,
            )
            when (state.setBy) {
                ApprovalSetBy.Parent ->
                    KitText(
                        stringResource(Res.string.approvalsUiInheritedFrom),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        modifier = Modifier.testTag("approvals-set-by"),
                    )
                ApprovalSetBy.Server ->
                    KitText(
                        stringResource(Res.string.approvalsSheetSetByServer),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                        modifier = Modifier.testTag("approvals-set-by"),
                    )
                ApprovalSetBy.Itself -> Unit
            }
            if (state.choice == ApprovalChoice.Auto) {
                KitSwitchRow(
                    title = stringResource(Res.string.approvalsSheetSubagents),
                    checked = state.inheritToChildren,
                    onCheckedChange = actions::setInheritToChildren,
                    modifier = Modifier.testTag("approvals-inherit-switch"),
                )
            }
            if (state.canFollowParent) {
                KitButton(
                    stringResource(Res.string.approvalsUiFollowParent),
                    onClick = actions::followParent,
                    role = KitButtonRole.Tertiary,
                    modifier = Modifier.testTag("approvals-follow-parent"),
                )
            }
            if (state.approvedPermissions.isNotEmpty()) {
                KitDetailsFold(
                    label = stringResource(Res.string.approvalsSheetHistory),
                    copyLabel = stringResource(Res.string.kitCopy),
                    modifier = Modifier.testTag("approvals-record"),
                    child = {
                        Column {
                            state.approvedPermissions.forEach { permission ->
                                KitRow(
                                    title = stringResource(Res.string.approvalsUiAutoApproved, permissionWords(permission)),
                                )
                            }
                        }
                    },
                )
            }
            if (state.choice != ApprovalChoice.Ask) {
                KitNotice(
                    stringResource(Res.string.approvalsSheetFootnote),
                    liveRegion = false,
                    modifier = Modifier.testTag("approvals-rules-note"),
                )
            }
        }
    }
}
