package io.github.eslamasabry.oc.feature.chat.requests.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestAnsweredRow
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestStack
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardRules
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotHold
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AnswerWords
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ReviewedRequestAction
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitAskLine
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.words.res.consentAlwaysAllowQuestion
import io.github.eslamasabry.oc.ui.words.res.consentAlwaysAllowFailed
import io.github.eslamasabry.oc.ui.words.res.consentAlwaysAllowDecline
import io.github.eslamasabry.oc.ui.words.res.consentAlwaysAllowTitle
import io.github.eslamasabry.oc.ui.words.res.chatUiAllMatchingRequests
import io.github.eslamasabry.oc.ui.words.res.chatUiInThisChat
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysScope
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitQuestionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestWaitingCaption
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.approvalAllow
import io.github.eslamasabry.oc.ui.words.res.approvalCopied
import io.github.eslamasabry.oc.ui.words.res.approvalCopyAsk
import io.github.eslamasabry.oc.ui.words.res.approvalDeny
import io.github.eslamasabry.oc.ui.words.res.approvalReplyBack
import io.github.eslamasabry.oc.ui.words.res.approvalReplyEmpty
import io.github.eslamasabry.oc.ui.words.res.approvalReplyJustDeny
import io.github.eslamasabry.oc.ui.words.res.approvalReplyLabel
import io.github.eslamasabry.oc.ui.words.res.approvalReplySend
import io.github.eslamasabry.oc.ui.kitchat.request.KitDenyReply
import io.github.eslamasabry.oc.ui.words.res.chatRequestIfIgnored
import io.github.eslamasabry.oc.ui.words.res.chatRequestCardLabel
import io.github.eslamasabry.oc.ui.words.res.chatRequestNoLongerNeeded
import io.github.eslamasabry.oc.ui.words.res.chatRequestNoLongerNeededWhy
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherAnswer
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherField
import io.github.eslamasabry.oc.ui.words.res.chatStopAfterStepUndo
import io.github.eslamasabry.oc.ui.words.res.chatUiAlwaysAllow
import io.github.eslamasabry.oc.ui.words.res.invitation40Question
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitRequestAnswer
import io.github.eslamasabry.oc.ui.words.res.kitRequestChangeSize
import io.github.eslamasabry.oc.ui.words.res.kitRequestReplyEmptyReason
import io.github.eslamasabry.oc.ui.words.res.kitRequestSend
import org.jetbrains.compose.resources.stringResource

/**
 * The request slot above the composer (plan 09 §5.1, §6.4; plan 08 §5.8; E06-S01): one card per
 * request of this conversation, in arrival order, each answered on its own in any order; never a
 * modal, never taking focus from the composer. Waiting: the ask with Allow once / Reject in place
 * and Always allow as a quiet action where the server keeps grants (a tap opens the one confirm).
 * After a tap: the collapsed line "Allowed · Undo" for 3 s, then the receipt (Sending → the card
 * leaves / Not confirmed yet / Not accepted · Try again). A card whose run was stopped says "No
 * longer needed" and leaves (E06-S03).
 *
 * [nowMs] is the screen's clock for the card's age ("Needs you · waiting 20 seconds"); null shows
 * no age. Cards unfold on arrival and fold away when they leave, and several cards scroll inside a
 * bounded slot so the composer stays on screen ([KitRequestStack]).
 */
@Composable
public fun ChatRequestSlot(
    state: RequestSlotUi,
    actions: ChatRequestActions,
    nowMs: Long?,
    modifier: Modifier = Modifier,
) {
    if (state.cards.isEmpty()) return
    val total = state.cards.size
    KitRequestStack(
        items = state.cards,
        key = { it.id },
        label = { index, card ->
            stringResource(
                Res.string.chatRequestCardLabel,
                Numbers.format(index + 1),
                Numbers.format(total),
                cardTarget(card),
            )
        },
        modifier = modifier,
    ) { card ->
        key(card.id, card.captured) { RequestCard(card, actions, nowMs) }
    }
    state.sheet?.let { ChatQuestionSheet(it, actions) }
    val confirm = state.confirmAlways
    KitConfirm(
        spec =
            confirm?.let {
                val request = it.captured?.request
                val patterns = request?.let { ask -> ask.always.ifEmpty { ask.patterns }.joinToString(", ") }
                val mask = rememberKitMask()
                KitConfirmSpec(
                    title = if (it.invitation) stringResource(Res.string.consentAlwaysAllowTitle) else alwaysConfirmTitle(it.grant),
                    confirmLabel = stringResource(Res.string.chatUiAlwaysAllow),
                    body = if (it.invitation && patterns != null) stringResource(
                        Res.string.chatRequestAlwaysScope,
                        patterns.takeIf { scope -> scope.isNotEmpty() }?.let { scope -> KitBidi.ltr(mask(scope)) }
                            ?: stringResource(Res.string.chatUiAllMatchingRequests),
                        stringResource(Res.string.chatUiInThisChat),
                    ) else alwaysConfirmBody(it.grant),
                    cancelLabel = stringResource(Res.string.kitConfirmCancel),
                )
            },
        onConfirm = { confirm?.let(actions::confirmAlwaysReviewed) },
        onCancel = actions::cancelAlwaysAllow,
    )
}

/**
 * One request's card. The stack makes it one TalkBack group that starts with its place and what it
 * asks about ("Request 2 of 4: Run npm test"), so cards are never confused with one another.
 */
@Composable
private fun RequestCard(card: RequestCardUi, actions: ChatRequestActions, nowMs: Long?) {
    LaunchedEffect(card.id) { actions.shown(card.id) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        when (val phase = card.state) {
            CardState.Waiting -> {
                val since = card.waitingSinceMs
                KitRequestWaitingCaption(
                    waiting =
                        if (nowMs != null && since != null) waitedWords(nowMs - since) else null
                )
                when (card) {
                    is RequestCardUi.Permission -> {
                        PermissionWaiting(card, actions)
                        card.invitationFailure?.let {
                            KitErrorNotice(
                                stringResource(Res.string.consentAlwaysAllowFailed),
                                failure = it,
                            )
                        }
                        if (card.invitation) {
                            val request = card.captured?.request
                            val mask = rememberKitMask()
                            val what = request?.let {
                                it.always.ifEmpty { it.patterns }.joinToString(", ").ifEmpty { it.permission }
                            }
                            KitAskLine(
                                icon = KitIcons.privacy,
                                question = if (what != null) stringResource(Res.string.consentAlwaysAllowQuestion, KitBidi.ltr(mask(what)))
                                    else stringResource(Res.string.invitation40Question),
                                accept = KitAction(stringResource(Res.string.chatUiAlwaysAllow), {
                                    actions.performReviewed(card, ReviewedRequestAction.AskAlways)
                                }),
                                decline = KitAction(stringResource(Res.string.consentAlwaysAllowDecline), {
                                    actions.keepAsking(card.id)
                                }),
                            )
                        }
                    }
                    is RequestCardUi.Question -> QuestionWaiting(card, actions)
                }
            }
            is CardState.Held -> HeldLine(card, receiptWords(card, phase.words), actions)
            is CardState.Sending ->
                AnsweredLine(receiptWords(card, phase.words), KitReceiptState.Sending, null)
            is CardState.Sent ->
                AnsweredLine(receiptWords(card, phase.words), KitReceiptState.Sent, null)
            is CardState.NotAccepted ->
                AnsweredLine(receiptWords(card, phase.words), KitReceiptState.Refused(null)) {
                    actions.performReviewed(card, ReviewedRequestAction.Retry)
                }
            CardState.NoLongerNeeded -> NoLongerNeededLine(card)
        }
    }
}

/** What a card asks about, in a few words: the ask and its target, or the question. */
@Composable
private fun cardTarget(card: RequestCardUi): String {
    val mask = rememberKitMask()
    return when (card) {
        is RequestCardUi.Permission -> {
            val what = askWhat(card.ask)
            val question = askQuestion(card.ask)
            if (what != null && what !in question) "$question, ${KitBidi.ltr(mask(what))}" else question
        }
        is RequestCardUi.Question -> card.question.ifBlank { card.title }
    }
}

/**
 * E06-S03: the run that asked was stopped. No answers, no "Needs your decision": the ask in small
 * words and "No longer needed · The run stopped, so nothing was sent." until the card leaves.
 */
@Composable
private fun NoLongerNeededLine(card: RequestCardUi) {
    val title =
        when (card) {
            is RequestCardUi.Permission -> askQuestion(card.ask)
            is RequestCardUi.Question -> card.question.ifBlank { card.title }
        }
    KitRequestAnsweredRow(
        icon = if (card is RequestCardUi.Question) KitIcons.question else KitIcons.permissions,
        title = title,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        KitText(
            "${stringResource(Res.string.chatRequestNoLongerNeeded)} · ${stringResource(Res.string.chatRequestNoLongerNeededWhy)}",
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
        )
    }
}

@Composable
private fun receiptWords(card: RequestCardUi, words: AnswerWords): String =
    answerLine(words, (card as? RequestCardUi.Permission)?.ask, rememberKitMask())

@Composable
private fun PermissionWaiting(
    card: RequestCardUi.Permission,
    actions: ChatRequestActions,
) {
    val mask = rememberKitMask()
    val always = card.canAlwaysAllow && !card.invitation
    val request = card.captured?.request
    val copies = RequestCardRules.copiesTargets(card.ask)
    KitPermissionCard(
        // P-013: a file named by its one target line is not named again in the question.
        title = askQuestion(if (RequestCardRules.questionOmitsName(card.ask)) card.ask.copy(name = null) else card.ask),
        toolLabel = if (RequestCardRules.showsToolLine(card.ask)) askToolLine(card.ask) else null,
        copyLabel = if (copies) stringResource(Res.string.approvalCopyAsk) else null,
        copiedLabel = if (copies) stringResource(Res.string.approvalCopied) else null,
        denyReply =
            KitDenyReply(
                fieldLabel = stringResource(Res.string.approvalReplyLabel),
                sendLabel = stringResource(Res.string.approvalReplySend),
                justDenyLabel = stringResource(Res.string.approvalReplyJustDeny),
                backLabel = stringResource(Res.string.approvalReplyBack),
                emptyReason = stringResource(Res.string.approvalReplyEmpty),
                onSend = { actions.performReviewed(card, ReviewedRequestAction.RejectWith(it)) },
            ),
        summary = stringResource(Res.string.chatRequestIfIgnored),
        subject = null,
        targets =
            card.ask.targets.map { target ->
                val added = target.added
                val removed = target.removed
                KitRequestTarget(
                    target.text,
                    if (added != null && removed != null)
                        stringResource(Res.string.kitRequestChangeSize, added, removed)
                    else null,
                )
            },
        allowOnceLabel = stringResource(Res.string.approvalAllow),
        alwaysLabel =
            if (always)
                alwaysExactLabel(
                    card.ask.always,
                    request?.always?.ifEmpty { request.patterns }.orEmpty(),
                    mask,
                )
            else null,
        denyLabel = stringResource(Res.string.approvalDeny),
        onAllowOnce = { actions.performReviewed(card, ReviewedRequestAction.AllowOnce) },
        onAlways =
            if (card.canAlwaysAllow && !card.invitation)
                ({ actions.performReviewed(card, ReviewedRequestAction.AskAlways) })
            else null,
        onDeny = { actions.performReviewed(card, ReviewedRequestAction.Reject) },
    )
}

@Composable
private fun QuestionWaiting(
    card: RequestCardUi.Question,
    actions: ChatRequestActions,
) {
    KitQuestionCard(
        title = card.title,
        question = card.question,
        summary = stringResource(Res.string.chatRequestIfIgnored),
        choices = card.options,
        onChoose = { actions.performReviewed(card, ReviewedRequestAction.Choose(it)) },
        answerLabel = stringResource(Res.string.kitRequestAnswer),
        onDetails = { actions.performReviewed(card, ReviewedRequestAction.OpenSheet) },
        inPlace = card.oneAnswer,
        other =
            if (!card.custom) null
            else
                ({
                    val other = card.other
                    KitChoiceRow(
                        title = stringResource(Res.string.chatRequestOtherAnswer),
                        selected = other != null,
                        onClick = { actions.performReviewed(card, ReviewedRequestAction.OpenOther) },
                    )
                    if (other != null) OtherField(card, other.text, other.canSend, actions)
                }),
    )
}

/** "Something else": the typed answer is kept as the request's draft by the model. */
@Composable
private fun OtherField(
    card: RequestCardUi.Question,
    text: String,
    canSend: Boolean,
    actions: ChatRequestActions,
) {
    val field = rememberTextFieldState(text)
    LaunchedEffect(card.captured) {
        snapshotFlow { field.text.toString() }
            .collect { actions.performReviewed(card, ReviewedRequestAction.EditOther(it)) }
    }
    KitField(
        field,
        label = stringResource(Res.string.chatRequestOtherField),
        onSubmit = { actions.performReviewed(card, ReviewedRequestAction.SendOther) },
    )
    KitButton(
        stringResource(Res.string.kitRequestSend),
        onClick = { actions.performReviewed(card, ReviewedRequestAction.SendOther) },
        enabled =
            if (canSend) KitEnabled.Enabled
            else KitEnabled.Disabled(stringResource(Res.string.kitRequestReplyEmptyReason)),
    )
}

/** Inside the hold: "Allowed · Undo" (plan 08 `HELD_ANSWER`). */
@Composable
private fun HeldLine(card: RequestCardUi, words: String, actions: ChatRequestActions) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitText(words, Modifier.weight(1f), role = TypeRole.Body, kind = KitTextKind.Frame)
        KitButton(
            stringResource(Res.string.chatStopAfterStepUndo),
            onClick = { actions.performReviewed(card, ReviewedRequestAction.Undo) },
            role = KitButtonRole.Tertiary,
        )
    }
}

/** After the hold: the answer and its receipt ("Sent" is never "Done"). */
@Composable
private fun AnsweredLine(words: String, receipt: KitReceiptState, onRetry: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitText(words, Modifier.weight(1f), role = TypeRole.Body, kind = KitTextKind.Frame)
        KitReceipt(receipt, onRetry = onRetry)
    }
}
