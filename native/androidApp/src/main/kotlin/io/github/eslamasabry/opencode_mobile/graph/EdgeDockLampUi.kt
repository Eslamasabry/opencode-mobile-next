package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.feature.chats.lamp.LampAnswerUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampGroupKind
import io.github.eslamasabry.oc.feature.chats.lamp.LampListController
import io.github.eslamasabry.oc.feature.chats.lamp.LampNotice
import io.github.eslamasabry.oc.feature.chats.lamp.LampRequestUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampStopUi
import io.github.eslamasabry.oc.feature.chats.lamp.LampUi
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPanel
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPanelActions
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPanelHost
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPanelWords
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.kit.modal.KitInputFormDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitToasts
import io.github.eslamasabry.oc.ui.kit.request.KitRequestWords
import io.github.eslamasabry.oc.ui.kit.row.KitReorderWords
import io.github.eslamasabry.oc.ui.kit.status.KitStepTreeWords
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The words of the list, from the string keys, with "N sub-agents" for each N the list shows. */
@Composable
internal fun rememberLampTexts(ui: LampUi): LampTexts {
    val words = LampWord.entries.associateWith { stringResource(lampWordKey(it)) }
    val counts = ui.groups.flatMap { it.rows }.mapNotNull { it.tree?.count }.distinct()
    val folded = counts.associateWith { pluralStringResource(Res.plurals.edgeLightSubAgentsFolded, it, it) }
    return remember(words, folded) { LampTexts(words, folded) }
}

private fun lampWordKey(word: LampWord): StringResource =
    when (word) {
        LampWord.Title -> Res.string.edgeLightTitle
        LampWord.Edit -> Res.string.edgeLightEdit
        LampWord.Done -> Res.string.edgeLightDone
        LampWord.All -> Res.string.edgeLightAllConversations
        LampWord.AllShort -> Res.string.edgeLightAllShort
        LampWord.NewConversation -> Res.string.edgeLightNew
        LampWord.Hidden -> Res.string.edgeLightHiddenShowAgain
        LampWord.ShowAgain -> Res.string.edgeLightShowAgain
        LampWord.More -> Res.string.edgeLightMoreInAll
        LampWord.Empty -> Res.string.edgeLightEmpty
        LampWord.Pin -> Res.string.edgeLightPin
        LampWord.Unpin -> Res.string.edgeLightUnpin
        LampWord.Rename -> Res.string.edgeLightRename
        LampWord.Hide -> Res.string.edgeLightHide
        LampWord.Stop -> Res.string.edgeLightStop
        LampWord.Untitled -> Res.string.edgeLightUntitled
        LampWord.GroupPinned -> Res.string.edgeLightGroupPinned
        LampWord.GroupNeedsYou -> Res.string.edgeLightGroupNeedsYou
        LampWord.GroupFailed -> Res.string.edgeLightGroupFailed
        LampWord.GroupWorking -> Res.string.edgeLightGroupWorking
        LampWord.GroupDone -> Res.string.edgeLightGroupDone
        LampWord.GroupRecent -> Res.string.edgeLightGroupRecent
        LampWord.TasksDone -> Res.string.edgeLightTasksDone
        LampWord.For -> Res.string.edgeLightFor
        LampWord.Thinking -> Res.string.edgeLightThinking
        LampWord.Working -> Res.string.edgeLightWorking
        LampWord.DoneWord -> Res.string.edgeLightDoneWord
        LampWord.IdleWord -> Res.string.edgeLightIdleWord
        LampWord.FailedWord -> Res.string.edgeLightFailedWord
        LampWord.WaitingMixed -> Res.string.edgeLightWaitingMixed
        LampWord.WaitingToFinish -> Res.string.edgeLightWaitingToFinish
        LampWord.PausedAtStep -> Res.string.edgeLightPausedAtStep
        LampWord.PausedUntilAnswer -> Res.string.edgeLightPausedUntilAnswer
        LampWord.SubAgentOf -> Res.string.edgeLightSubAgentOf
        LampWord.MoreSubAgents -> Res.string.edgeLightMoreSubAgents
        LampWord.MoreInside -> Res.string.edgeLightMoreInside
        LampWord.AsksRun -> Res.string.edgeLightAsksRun
        LampWord.AsksEdit -> Res.string.edgeLightAsksEdit
        LampWord.AsksRead -> Res.string.edgeLightAsksRead
        LampWord.AsksFolder -> Res.string.edgeLightAsksFolder
        LampWord.AsksPermission -> Res.string.edgeLightAsksPermission
        LampWord.AsksQuestion -> Res.string.edgeLightAsksQuestion
        LampWord.AsksQuestionBare -> Res.string.edgeLightAsksQuestionBare
        LampWord.AsksForm -> Res.string.edgeLightAsksForm
        LampWord.AsksFormBare -> Res.string.edgeLightAsksFormBare
        LampWord.Answer -> Res.string.edgeLightAnswer
        LampWord.Allowed -> Res.string.edgeLightAllowed
        LampWord.Denied -> Res.string.edgeLightDenied
        LampWord.Stopping -> Res.string.edgeLightStopping
        LampWord.Stopped -> Res.string.edgeLightStopped
        LampWord.Reorder -> Res.string.edgeLightReorder
        LampWord.MoveUp -> Res.string.edgeLightMoveUp
        LampWord.MoveDown -> Res.string.edgeLightMoveDown
        LampWord.YouAreIn -> Res.string.edgeLightYouAreIn
        LampWord.FactOf -> Res.string.edgeLightFactOf
        LampWord.ChildLabel -> Res.string.edgeLightChildLabel
        LampWord.ChildLabelFact -> Res.string.edgeLightChildLabelFact
        LampWord.AllowOnce -> Res.string.kitRequestAllowOnce
        LampWord.Deny -> Res.string.approvalDeny
        LampWord.ActionOn -> Res.string.edgeLightActionOn
        LampWord.NotStarted -> Res.string.edgeLightNotStarted
        LampWord.ShowSubAgents -> Res.string.edgeLightShowSubAgents
        LampWord.HideSubAgents -> Res.string.edgeLightHideSubAgents
        LampWord.AgeNow -> Res.string.edgeLightAgeNow
        LampWord.AgeMinutes -> Res.string.edgeLightAgeMinutes
        LampWord.AgeHours -> Res.string.edgeLightAgeHours
        LampWord.AgeDays -> Res.string.edgeLightAgeDays
        LampWord.EdgeLight -> Res.string.dockModeEdgeLight
        LampWord.Move -> Res.string.edgeLightMove
        LampWord.TurnOff -> Res.string.edgeLightTurnOff
        LampWord.PlaceTitle -> Res.string.edgeLightPlaceTitle
        LampWord.PlaceBody -> Res.string.edgeLightPlaceBody
        LampWord.Left -> Res.string.edgeLightSideLeft
        LampWord.Right -> Res.string.edgeLightSideRight
        LampWord.High -> Res.string.edgeLightHeightHigh
        LampWord.Middle -> Res.string.edgeLightHeightMiddle
        LampWord.Low -> Res.string.edgeLightHeightLow
        LampWord.OffNotice -> Res.string.edgeLightOffNotice
        LampWord.RenameFailed -> Res.string.edgeLightRenameFailed
        LampWord.StopFailed -> Res.string.dockStopFailed
        LampWord.AnswerFailed -> Res.string.dockAnswerFailed
        LampWord.HiddenNotice -> Res.string.edgeLightHiddenNotice
        LampWord.RenameTitle -> Res.string.chatUiRenameSession
        LampWord.Cancel -> Res.string.kitConfirmCancel
        LampWord.RenameConfirm -> Res.string.chatUiRename
    }

/** The panel's kit words over [t]; [ui] says which sub-agent belongs to which conversation for TalkBack. */
internal fun lampPanelWords(t: LampTexts, ui: LampUi): KitLampPanelWords {
    val parentOf = ui.groups.flatMap { it.rows }.flatMap { row -> row.tree?.children.orEmpty().map { it.key to (row.title ?: t[LampWord.Untitled]) } }.toMap()
    val request =
        KitRequestWords(
            allowOnce = t[LampWord.AllowOnce],
            deny = t[LampWord.Deny],
            answer = t[LampWord.Answer],
            allowOn = { t.fmt(LampWord.ActionOn, t[LampWord.AllowOnce], it) },
            denyOn = { t.fmt(LampWord.ActionOn, t[LampWord.Deny], it) },
            answerOn = { t.fmt(LampWord.ActionOn, t[LampWord.Answer], it) },
        )
    return KitLampPanelWords(
        title = t[LampWord.Title],
        edit = t[LampWord.Edit],
        done = t[LampWord.Done],
        all = { t.fmt(LampWord.All, t.count(it)) },
        allShort = { t.fmt(LampWord.AllShort, t.count(it)) },
        newConversation = t[LampWord.NewConversation],
        hidden = { t.fmt(LampWord.Hidden, t.count(it)) },
        showAgain = t[LampWord.ShowAgain],
        more = { t.fmt(LampWord.More, t.count(it)) },
        empty = t[LampWord.Empty],
        pin = { t.fmt(LampWord.Pin, it) },
        unpin = { t.fmt(LampWord.Unpin, it) },
        rename = { t.fmt(LampWord.Rename, it) },
        hide = { t.fmt(LampWord.Hide, it) },
        stop = { t.fmt(LampWord.Stop, it) },
        subAgents = { t.folded[it] ?: t.count(it) },
        edgeLight = t[LampWord.EdgeLight],
        move = t[LampWord.Move],
        turnOff = t[LampWord.TurnOff],
        reorder = KitReorderWords(handle = { t.fmt(LampWord.Reorder, it) }, moveUp = t[LampWord.MoveUp], moveDown = t[LampWord.MoveDown]),
        steps =
            KitStepTreeWords(
                working = t[LampWord.Working],
                notStarted = t[LampWord.NotStarted],
                needsYou = t[LampWord.GroupNeedsYou],
                paused = t[LampWord.PausedUntilAnswer],
                denied = t[LampWord.Denied],
                failed = t[LampWord.FailedWord],
                moreInside = { t.fmt(LampWord.MoreInside, t.count(it)) },
                more = { t.fmt(LampWord.MoreSubAgents, t.count(it)) },
                duration = { seconds -> if (seconds < 60) "${t.count(seconds.toInt())}s" else "${t.count((seconds / 60).toInt())}:${(seconds % 60).toString().padStart(2, '0')}" },
                stop = { t.fmt(LampWord.Stop, it) },
                spoken = { step, said ->
                    val parent = parentOf[step.key] ?: ""
                    step.fact?.let { t.fmt(LampWord.ChildLabelFact, step.name, parent, said, it) } ?: t.fmt(LampWord.ChildLabel, step.name, parent, said)
                },
                unfold = t[LampWord.ShowSubAgents],
                fold = t[LampWord.HideSubAgents],
                request = request,
            ),
        request = request,
    )
}

/**
 * The panel, grown out of the light: the list drawn by the kit from the rows presenter, every act connected to the
 * list's controller (open and Back, Allow once / Deny with Undo, Answer…, Stop with Undo, Edit mode: pin, rename, hide,
 * reorder, Move…, Turn off, New conversation, All conversations). Notes after an act come as toasts and the hide's Undo bar.
 */
@Composable
internal fun EdgeLightPanelLayer(
    port: AppEdgeDockPort,
    controller: LampListController,
    open: Boolean,
    side: KitLampSide,
    anchor: () -> Rect,
    top: Dp,
    bottom: Dp,
    onClose: () -> Unit,
    onMove: () -> Unit,
) {
    val ui by controller.ui.collectAsStateWithLifecycle()
    val t = rememberLampTexts(ui)
    val index = remember(ui) { LampIndex(ui) }
    val model = remember(ui, t) { LampPanelMap.model(ui, t) }
    val words = remember(ui, t) { lampPanelWords(t, ui) }
    val toasts = LocalKitToasts.current
    var renaming by remember { mutableStateOf<ConversationRef?>(null) }
    val renameField = rememberTextFieldState()

    // A failure or the Turn off note is told once, in a toast, and cleared.
    val notice = ui.notice
    LaunchedEffect(notice) {
        val said =
            when (notice) {
                is LampNotice.StopFailed -> t.fmt(LampWord.StopFailed, notice.title ?: t[LampWord.Untitled])
                is LampNotice.AnswerFailed -> t.fmt(LampWord.AnswerFailed, notice.title ?: t[LampWord.Untitled])
                is LampNotice.RenameFailed -> t.fmt(LampWord.RenameFailed, notice.title ?: t[LampWord.Untitled])
                LampNotice.TurnedOff -> t[LampWord.OffNotice]
                else -> null
            }
        if (said != null) {
            toasts?.show(said)
            controller.dismissNotice()
        }
    }

    fun openConversation(ref: ConversationRef) {
        // The page switched to says "Reply in ‹title›" and, the first three times, where Back goes (the conversation
        // being left); told right before the switch so a fresh page and a kept one both read it.
        port.arriving(ref, ui.openTitle)
        controller.open(ref)
        onClose()
    }

    fun requestOf(key: Any, stepKey: Any?): LampRequestUi? =
        if (stepKey != null) index.child(stepKey)?.second?.request else index.row(key)?.request

    val actions =
        remember(index, ui, t) {
            KitLampPanelActions(
                onOpen = { key -> index.row(key)?.let { openConversation(it.ref) } },
                onOpenStep = { _, step -> index.child(step.key)?.let { openConversation(it.second.ref) } },
                onStop = { key -> index.row(key)?.let { controller.stop(it.ref) } },
                onStopStep = { _, step -> index.child(step.key)?.let { controller.stop(it.second.ref) } },
                onAllow = { key, step -> requestOf(key, step?.key)?.let { controller.allowOnce(it.owner, it.requestId) } },
                onDeny = { key, step -> requestOf(key, step?.key)?.let { controller.deny(it.owner, it.requestId) } },
                onAnswer = { key, step ->
                    requestOf(key, step?.key)?.let {
                        port.arriving(it.owner, ui.openTitle)
                        controller.answer(it.owner, it.requestId)
                        onClose()
                    }
                },
                onUndo = { key, step ->
                    val request = requestOf(key, step?.key)
                    if (request != null && request.answer is LampAnswerUi.Held) {
                        controller.undoAnswer(request.requestId)
                    } else {
                        val ref = if (step != null) index.child(step.key)?.second?.ref else index.row(key)?.ref
                        val stopping = if (step != null) index.child(step.key)?.second?.stop else index.row(key)?.stop
                        if (ref != null && stopping == LampStopUi.Holding) controller.undoStop(ref)
                    }
                },
                onEdit = { on -> if (on) controller.edit() else controller.done() },
                onPin = { key, pinned -> index.row(key)?.let { if (pinned) controller.pin(it.ref) else controller.unpin(it.ref) } },
                onRename = { key ->
                    index.row(key)?.let {
                        renameField.setTextAndPlaceCursorAtEnd(it.title.orEmpty())
                        renaming = it.ref
                    }
                },
                onHide = { key -> index.row(key)?.let { controller.hide(it.ref) } },
                onShowAgain = { key -> index.hidden(key)?.let { controller.restore(it) } },
                onReorderPinned = { from, to ->
                    val pinned = ui.groups.firstOrNull { it.kind == LampGroupKind.Pinned }?.rows.orEmpty().map { it.ref }
                    if (from in pinned.indices && to in pinned.indices) controller.reorder(pinned[from], pinned[to])
                },
                onAll = {
                    controller.allConversations()
                    onClose()
                },
                onNew = {
                    controller.newConversation()
                    onClose()
                },
                onMove = onMove,
                onTurnOff = { controller.turnOff() },
            )
        }

    Box(Modifier.fillMaxSize()) {
        KitLampPanelHost(
            open = open,
            anchorInWindow = anchor,
            side = side,
            onClose = onClose,
            modifier = Modifier.padding(top = top, bottom = bottom),
        ) {
            KitLampPanel(model, words, actions)
        }
        val hidden = ui.notice as? LampNotice.Hidden
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottom)) {
            KitUndoBar(
                offer = hidden?.let { KitUndoOffer(it.ref.toString(), t.fmt(LampWord.HiddenNotice, it.title ?: t[LampWord.Untitled])) },
                onUndo = { hidden?.let { controller.restore(it.ref) } },
                onTimeout = { controller.dismissNotice() },
            )
        }
        renaming?.let { ref ->
            KitInputFormDialog(
                state = renameField,
                title = t[LampWord.RenameTitle],
                fieldLabel = t[LampWord.RenameTitle],
                confirmLabel = t[LampWord.RenameConfirm],
                cancelLabel = t[LampWord.Cancel],
                onSave = {
                    controller.rename(ref, renameField.text.toString())
                    renaming = null
                },
                onDismissRequest = { renaming = null },
            )
        }
    }
}

