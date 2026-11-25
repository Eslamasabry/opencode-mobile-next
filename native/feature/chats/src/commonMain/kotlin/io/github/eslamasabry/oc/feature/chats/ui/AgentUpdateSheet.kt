package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.feature.chats.presentation.AgentNeededRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentRemovalAskUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateActions
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateHomeUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.foundation.KitNoticeInView
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAgentsAction
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAgentsLine
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesMetered
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
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesReviewAction
import org.jetbrains.compose.resources.stringResource

/**
 * "Updates for your agents" (redrawn 2026-11-25): after an app update that moves the agents' pins, one sheet shows the
 * download total and any network notice first, then one unlabelled list with the needed parts first (the agent helper,
 * Node.js) and one switch per agent. The button names what it does; every removal is confirmed first with the Agents
 * page's own words. Kit parts only: no motion of its own, and every part keeps the kit's width caps.
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

/** The question: any failure notice, the total, any network line, one unlabelled list, and the Agents note. */
@Composable
private fun QuestionBody(shown: AgentUpdateSheetUi, actions: AgentUpdateActions) {
    // A refusal is said first, at the top, and scrolled into view: under the list it was out of sight, so the button
    // seemed to do nothing (10110, a refusal for room). The sheet stays open for a new choice.
    shown.failure?.let { failure ->
        KitNoticeInView(failure) { ways ->
            KitErrorNotice(message = failureWords(failure, shown), failure = failure, modifier = ways)
        }
    }
    Totals(shown)
    when (shown.network) {
        AgentUpdateNetwork.Metered -> KitGroupNote(stringResource(Res.string.agentUpdatesMetered))
        AgentUpdateNetwork.Unknown -> KitGroupNote(stringResource(Res.string.agentUpdatesNetworkUnknown))
        AgentUpdateNetwork.Unmetered -> Unit
    }
    if (shown.needed.isNotEmpty() || shown.optional.isNotEmpty()) {
        val hasTotal = shown.updating > 0 || shown.removing > 0 || shown.needed.isNotEmpty()
        val hasNetwork = shown.network != AgentUpdateNetwork.Unmetered
        if (hasTotal || hasNetwork) Spacer(Modifier.height(Space.S3.dp))
        KitRowGroup {
            shown.needed.forEach { row -> NeededRow(row, shown.neededWhy) }
            shown.optional.forEach { row -> OptionalRow(row, actions) }
        }
    }
    KitGroupNote(
        stringResource(Res.string.agentUpdatesAgentsLine),
        action = KitAction(stringResource(Res.string.agentUpdatesAgentsAction), actions::openAgentsFromUpdates),
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

/** One agent: its name, versions and size (or what keeping/removing does), and one switch. */
@Composable
private fun OptionalRow(row: AgentUpdateRowUi, actions: AgentUpdateActions) {
    val facts = versionAndSize(row.from, row.to, row.downloadBytes)
    val block = row.keepBlock
    if (block != null) {
        KitSwitchRow(
            title = KitBidi.auto(row.name),
            checked = true,
            enabled = KitEnabled.Disabled(keepBlockReason(block)),
            supportingMaxLines = 4,
            below = { KitText(facts, role = TypeRole.Secondary, tone = TextTone.Secondary) },
            onCheckedChange = { actions.chooseAgentUpdate(row.id, AgentUpdateChoice.Update) },
        )
        return
    }
    val updating = row.choice == AgentUpdateChoice.Update
    KitSwitchRow(
        title = KitBidi.auto(row.name),
        checked = updating,
        supporting = if (updating) facts else consequence(row),
        supportingMaxLines = 3,
        onCheckedChange = {
            actions.chooseAgentUpdate(row.id, if (it) AgentUpdateChoice.Update else AgentUpdateChoice.Keep)
        },
    )
}

/** A part the updates need: its name, versions, size and why it is needed, with no choice. */
@Composable
private fun NeededRow(row: AgentNeededRowUi, why: AgentNeedRule) {
    KitRow(
        title = KitBidi.auto(row.name),
        supporting = "${versionAndSize(row.from, row.to, row.downloadBytes)}\n${neededLine(why)}",
        supportingMaxLines = 4,
    )
}

/** The totals: what downloads (the Update choices and the shown Needed parts) and what Remove frees. */
@Composable
private fun Totals(sheet: AgentUpdateSheetUi) {
    if (sheet.updating > 0 || sheet.needed.isNotEmpty()) {
        KitText(
            stringResource(Res.string.agentUpdatesTotalDownload),
            role = TypeRole.Label,
            tone = TextTone.Secondary,
        )
        KitText(amount(sheet.downloadBytes, sheet.downloadKnown), role = TypeRole.LargeTitle)
    }
    if (sheet.removing > 0) {
        KitRowGroup {
            KitRow(
                stringResource(Res.string.agentUpdatesTotalFrees),
                trailing = KitRowTrailing.Value(amount(sheet.freesBytes, sheet.freesKnown), chevron = false),
            )
        }
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
            // Nothing is wrong: an answer is waiting. A calm mark: the update glyph, as the running update's line.
            tone = StatusTone.Neutral,
            icon = KitIcons.systemDownload,
            action = KitLineAction(stringResource(Res.string.agentUpdatesReviewAction), actions::reviewAgentUpdates),
        )
