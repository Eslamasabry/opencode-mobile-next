package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.install.AgentKeepBlock
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.feature.chats.presentation.AgentNeededRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentRemovalAskUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateActions
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateHomeUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitContentRow
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitNoticeInView
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAgentsAction
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAgentsLine
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesChoiceLabel
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesKeepHelperTooNew
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesKeepNotSupported
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesLinuxAction
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesLinuxLine
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesMetered
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNeededAlways
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNeededLabel
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNeededWhy
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNetworkUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNotRemoved
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesReady
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesShortSpace
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesTotalDownload
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesTotalFrees
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveAction
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveBody
import io.github.eslamasabry.oc.ui.words.res.agentsRemoveTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotNow
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.chatsHomeDone
import io.github.eslamasabry.oc.ui.words.res.localAgentRowOptional
import io.github.eslamasabry.oc.ui.words.res.nativePendingReview
import org.jetbrains.compose.resources.stringResource

/**
 * "Updates for your agents" (owner 2026-11-19): after an app update that moves the agents' pins, one sheet asks what
 * to do with each agent: Update, Keep the version it has, or Remove it. The parts the updates need (the agent helper,
 * Node.js) are listed with their sizes and no choice, only while at least one agent is set to Update. The button names
 * what it does; every removal is confirmed first with the Agents page's own words. Kit parts only: no motion of its
 * own (the kit's sheet and segmented control follow Motion off and Saver), and every part keeps the kit's width caps.
 */
@Composable
internal fun AgentUpdateSheet(sheet: AgentUpdateSheetUi, actions: AgentUpdateActions) {
    // The rows stay drawn while the sheet slides away after the answer.
    val last = remember { arrayOfNulls<AgentUpdateSheetUi>(1) }
    if (sheet.visible) last[0] = sheet
    val shown = if (sheet.visible) sheet else last[0] ?: sheet
    // After the answer, a removal that did not happen is told here with its reason, never left silent.
    val result = shown.refused.isNotEmpty()
    // What is chosen does not fit: the button is off and says why right at it (pinned, never scrolled away); Keep or
    // Remove choices that make it fit turn it on again. On 10110 it stayed on and a tap seemed to do nothing.
    val noRoom =
        shown.short?.let {
            val needs = PhoneSizes.text(it.neededBytes)
            KitEnabled.Disabled(stringResource(Res.string.agentUpdatesShortSpace, needs, PhoneSizes.text(it.freeBytes)))
        }
    KitSheet(
        visible = sheet.visible,
        onDismissRequest = if (result) actions::closeAgentUpdateResult else actions::agentUpdatesNotNow,
        title = stringResource(Res.string.agentUpdatesTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
        primary =
            if (result) KitAction(stringResource(Res.string.chatsHomeDone), actions::closeAgentUpdateResult)
            else
                KitAction(
                    agentUpdatesButton(shown),
                    actions::proceedAgentUpdates,
                    enabled = noRoom ?: KitEnabled.Enabled,
                    working = shown.working,
                ),
        secondary =
            if (result) null else KitAction(stringResource(Res.string.appUpdateNotNow), actions::agentUpdatesNotNow),
    ) {
        Column(Modifier.testTag("agent-updates-sheet")) {
            if (result) ResultBody(shown, actions) else QuestionBody(shown, actions)
        }
    }
    RemovalConfirm(sheet.asking.takeIf { sheet.visible }, actions)
}

/** The question: the Optional agents with their choices, the Needed parts, the totals and the lines under them. */
@Composable
private fun QuestionBody(shown: AgentUpdateSheetUi, actions: AgentUpdateActions) {
    // A refusal is said first, at the top, and scrolled into view: under the list it was out of sight, so the button
    // seemed to do nothing (10110, a refusal for room). The sheet stays open for a new choice.
    shown.failure?.let { failure ->
        KitNoticeInView(failure) { ways ->
            KitErrorNotice(message = failureWords(failure, shown), failure = failure, modifier = ways)
        }
    }
    if (shown.optional.isNotEmpty())
        KitRowGroup(label = stringResource(Res.string.localAgentRowOptional)) {
            shown.optional.forEach { row -> OptionalRow(row, actions) }
        }
    if (shown.needed.isNotEmpty())
        KitRowGroup(
            label = stringResource(Res.string.agentUpdatesNeededLabel),
            note =
                stringResource(
                    if (shown.neededWhy == AgentNeedRule.Always) Res.string.agentUpdatesNeededAlways
                    else Res.string.agentUpdatesNeededWhy
                ),
        ) {
            shown.needed.forEach { row -> NeededRow(row) }
        }
    Totals(shown)
    when (shown.network) {
        AgentUpdateNetwork.Metered -> KitGroupNote(stringResource(Res.string.agentUpdatesMetered))
        AgentUpdateNetwork.Unknown -> KitGroupNote(stringResource(Res.string.agentUpdatesNetworkUnknown))
        AgentUpdateNetwork.Unmetered -> Unit
    }
    KitGroupNote(
        stringResource(Res.string.agentUpdatesAgentsLine),
        action = KitAction(stringResource(Res.string.agentUpdatesAgentsAction), actions::openAgentsFromUpdates),
    )
    KitGroupNote(
        stringResource(Res.string.agentUpdatesLinuxLine),
        action = KitAction(stringResource(Res.string.agentUpdatesLinuxAction), actions::openLinuxPackages),
    )
}

/** The answer's result: each removal that did not happen, by name, with the Agents page's own reason words. */
@Composable
private fun ResultBody(shown: AgentUpdateSheetUi, actions: AgentUpdateActions) {
    shown.refused.forEach { refusal ->
        KitErrorNotice(
            message = refusalWords(refusal.why),
            title = stringResource(Res.string.agentUpdatesNotRemoved, KitBidi.auto(refusal.name)),
        )
    }
    KitGroupNote(
        stringResource(Res.string.agentUpdatesAgentsLine),
        action = KitAction(stringResource(Res.string.agentUpdatesAgentsAction), actions::openAgentsFromUpdates),
    )
}

/** One agent: its name, from → to, the choice (the kit stacks it into rows when a word does not fit), what it does. */
@Composable
private fun OptionalRow(row: AgentUpdateRowUi, actions: AgentUpdateActions) {
    KitContentRow(title = KitBidi.auto(row.name), supporting = versions(row.from, row.to)) {
        Column {
            if (row.choices.size >= 2) {
                val segments = row.choices.map { KitSegment(it, choiceWord(it)) }
                KitSegmented(
                    segments = segments,
                    selected = row.choice,
                    semanticsLabel = stringResource(Res.string.agentUpdatesChoiceLabel, row.name),
                    onChange = { actions.chooseAgentUpdate(row.id, it) },
                )
            }
            KitText(consequence(row), role = TypeRole.Secondary, tone = TextTone.Secondary)
            row.keepBlock?.let {
                KitText(
                    stringResource(
                        when (it) {
                            AgentKeepBlock.HelperTooNew -> Res.string.agentUpdatesKeepHelperTooNew
                            AgentKeepBlock.NotSupportedAnyMore -> Res.string.agentUpdatesKeepNotSupported
                        }
                    ),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
            }
        }
    }
}

/** A part the updates need: its name, from → to and its download, no choice. */
@Composable
private fun NeededRow(row: AgentNeededRowUi) {
    KitRow(
        title = KitBidi.auto(row.name),
        supporting = "${versions(row.from, row.to)}\n${downloads(row.downloadBytes)}",
        supportingMaxLines = 3,
    )
}

/** The totals: what downloads (the Update choices and the shown Needed parts) and what Remove frees. */
@Composable
private fun Totals(sheet: AgentUpdateSheetUi) {
    if (sheet.updating == 0 && sheet.removing == 0 && sheet.needed.isEmpty()) return
    KitRowGroup {
        if (sheet.updating > 0 || sheet.needed.isNotEmpty())
            KitRow(
                stringResource(Res.string.agentUpdatesTotalDownload),
                trailing = KitRowTrailing.Value(amount(sheet.downloadBytes, sheet.downloadKnown), chevron = false),
            )
        if (sheet.removing > 0)
            KitRow(
                stringResource(Res.string.agentUpdatesTotalFrees),
                trailing = KitRowTrailing.Value(amount(sheet.freesBytes, sheet.freesKnown), chevron = false),
            )
    }
}

/** The confirm before a removal: the Agents page's own words, and what it frees. */
@Composable
private fun RemovalConfirm(ask: AgentRemovalAskUi?, actions: AgentUpdateActions) {
    KitConfirm(
        spec =
            ask?.let {
                val name = KitBidi.auto(it.name)
                KitConfirmSpec(
                    title = stringResource(Res.string.agentsRemoveTitle, name),
                    body = if (it.keepsAccountsAndConversations) stringResource(Res.string.agentsRemoveBody) else null,
                    consequences = listOf(frees(it.freesBytes)),
                    confirmLabel = stringResource(Res.string.agentsRemoveAction, name),
                    kind = KitConfirmKind.Destructive,
                    cancelLabel = stringResource(Res.string.kitConfirmCancel),
                )
            },
        onConfirm = actions::confirmAgentRemoval,
        onCancel = actions::cancelAgentRemoval,
    )
}

/** Home's one line for the agents' update choice: "Agent updates ready" with Review until it is answered. */
@Composable
internal fun agentUpdateStatus(updates: AgentUpdateHomeUi, actions: AgentUpdateActions): KitStatus? =
    if (!updates.pending || updates.sheet.visible) null
    else
        KitStatus(
            kind = KitStatusKind.Update,
            id = "agent-updates:review",
            message = stringResource(Res.string.agentUpdatesReady),
            // Nothing is wrong: an answer is waiting. A calm mark.
            tone = StatusTone.Neutral,
            action = KitLineAction(stringResource(Res.string.nativePendingReview), actions::reviewAgentUpdates),
        )
