package io.github.eslamasabry.oc.feature.chat.requests.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.QuestionSheetUi
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
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitQuestionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestWaitingCaption
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatRequestAnswerRequired
import io.github.eslamasabry.oc.ui.words.res.chatRequestIfIgnored
import io.github.eslamasabry.oc.ui.words.res.chatRequestMoreWaiting
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherAnswer
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherField
import io.github.eslamasabry.oc.ui.words.res.chatStopAfterStepUndo
import io.github.eslamasabry.oc.ui.words.res.chatUiAlwaysAllow
import io.github.eslamasabry.oc.ui.words.res.invitation40KeepAsking
import io.github.eslamasabry.oc.ui.words.res.invitation40Question
import io.github.eslamasabry.oc.ui.words.res.invitation40SaveFailed
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitRequestAllowOnce
import io.github.eslamasabry.oc.ui.words.res.kitRequestAnswer
import io.github.eslamasabry.oc.ui.words.res.kitRequestChangeSize
import io.github.eslamasabry.oc.ui.words.res.kitRequestReject
import io.github.eslamasabry.oc.ui.words.res.kitRequestReplyEmptyReason
import io.github.eslamasabry.oc.ui.words.res.kitRequestSend
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The request slot above the composer (plan 09 §5.1, §6.4; plan 08 §5.8): at most one card, never a
 * modal, never taking focus from the composer. Waiting: the ask with Allow once / Reject in place
 * and Always allow as a quiet action where the server keeps grants (a tap opens the one confirm).
 * After a tap: the collapsed line "Allowed · Undo" for 3 s, then the receipt (Sending → the card
 * leaves / Not confirmed yet / Not accepted · Try again).
 *
 * [nowMs] is the screen's clock for the card's age ("Needs you · waiting 20 seconds"); null shows
 * no age. Motion (plan 08 §5.8: the card unfolds on arrival, the body folds into the line) waits
 * for `KitReveal` from the kit-chat owner; until then the slot changes in place.
 */
@Composable
public fun ChatRequestSlot(
    state: RequestSlotUi,
    actions: ChatRequestActions,
    nowMs: Long?,
    modifier: Modifier = Modifier,
) {
    val card = state.card ?: return
    key(card.id, card.captured) {
        LaunchedEffect(card.id) { actions.shown(card.id) }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            when (val phase = card.state) {
                CardState.Waiting -> {
                    val since = card.waitingSinceMs
                    KitRequestWaitingCaption(
                        waiting =
                            if (nowMs != null && since != null) waitedWords(nowMs - since) else null
                    )
                    when (card) {
                        is RequestCardUi.Permission -> {
                            PermissionWaiting(card, state.moreWaiting, actions)
                            state.invitationFailure?.let {
                                KitErrorNotice(
                                    stringResource(Res.string.invitation40SaveFailed),
                                    failure = it,
                                )
                            }
                            if (card.invitation)
                                KitNotice(
                                    message = stringResource(Res.string.invitation40Question),
                                    actions =
                                        listOf(
                                            KitLineAction(
                                                stringResource(Res.string.chatUiAlwaysAllow),
                                                {
                                                    actions.performReviewed(
                                                        card,
                                                        ReviewedRequestAction.AskAlways,
                                                    )
                                                },
                                            ),
                                            KitLineAction(
                                                stringResource(Res.string.invitation40KeepAsking),
                                                { actions.keepAsking(card.id) },
                                            ),
                                        ),
                                )
                        }
                        is RequestCardUi.Question ->
                            QuestionWaiting(card, state.moreWaiting, actions)
                    }
                }
                is CardState.Held -> HeldLine(card, answerWords(phase.words), actions)
                is CardState.Sending ->
                    AnsweredLine(answerWords(phase.words), KitReceiptState.Sending, null)
                is CardState.Sent ->
                    AnsweredLine(answerWords(phase.words), KitReceiptState.Sent, null)
                is CardState.NotAccepted ->
                    AnsweredLine(answerWords(phase.words), KitReceiptState.Refused(null)) {
                        actions.performReviewed(card, ReviewedRequestAction.Retry)
                    }
            }
        }
    }
    state.sheet?.let { QuestionSheet(it, actions) }
    val confirm = state.confirmAlways
    KitConfirm(
        spec =
            confirm?.let {
                KitConfirmSpec(
                    title = alwaysConfirmTitle(it.grant),
                    confirmLabel = stringResource(Res.string.chatUiAlwaysAllow),
                    body = alwaysConfirmBody(it.grant),
                    cancelLabel = stringResource(Res.string.kitConfirmCancel),
                )
            },
        onConfirm = { confirm?.let(actions::confirmAlwaysReviewed) },
        onCancel = actions::cancelAlwaysAllow,
    )
}

@Composable
private fun PermissionWaiting(
    card: RequestCardUi.Permission,
    moreWaiting: Int,
    actions: ChatRequestActions,
) {
    KitPermissionCard(
        title = askQuestion(card.ask),
        summary = waitsLine(moreWaiting),
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
        allowOnceLabel = stringResource(Res.string.kitRequestAllowOnce),
        alwaysLabel =
            if (card.canAlwaysAllow && !card.invitation) alwaysLabel(card.ask.always) else null,
        denyLabel = stringResource(Res.string.kitRequestReject),
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
    moreWaiting: Int,
    actions: ChatRequestActions,
) {
    KitQuestionCard(
        title = card.title,
        question = card.question,
        summary = waitsLine(moreWaiting),
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

/**
 * Every prompt and every offered answer, selected locally and sent through the same guarded request
 * lane.
 */
@Composable
private fun QuestionSheet(state: QuestionSheetUi, actions: ChatRequestActions) {
    val id = state.question.id
    KitSheet(
        visible = true,
        onDismissRequest = { actions.closeSheet(id) },
        title = stringResource(Res.string.kitRequestAnswer),
        height =
            if (state.question.prompts.size > 1) KitSheetHeight.Full else KitSheetHeight.Content,
        primary =
            KitAction(
                stringResource(Res.string.kitRequestSend),
                onClick = { actions.sendSheet(id) },
                enabled =
                    if (state.canSend) KitEnabled.Enabled
                    else KitEnabled.Disabled(stringResource(Res.string.chatRequestAnswerRequired)),
            ),
    ) {
        state.question.prompts.forEachIndexed { index, prompt ->
            key(id, index) {
                val draft = state.drafts[index]
                if (prompt.title.isNotBlank()) KitText(prompt.title, role = TypeRole.Headline)
                KitText(prompt.question, role = TypeRole.Body, kind = KitTextKind.Reading)
                prompt.choices.forEach { choice ->
                    KitChoiceRow(
                        title = choice.label,
                        supporting = choice.description.takeIf { it.isNotBlank() },
                        selected = choice.label in draft.chosen,
                        mark = if (prompt.multiple) KitChoiceMark.Check else KitChoiceMark.Radio,
                        onClick = { actions.chooseInSheet(id, index, choice.label) },
                    )
                }
                if (prompt.custom) {
                    if (!prompt.multiple) {
                        KitChoiceRow(
                            title = stringResource(Res.string.chatRequestOtherAnswer),
                            selected = draft.ownOpen,
                            mark = KitChoiceMark.Radio,
                            onClick = { actions.editSheetOther(id, index, draft.own) },
                        )
                    }
                    if (prompt.multiple || draft.ownOpen) {
                        val words = rememberTextFieldState(draft.own)
                        LaunchedEffect(id, index, words) {
                            var last = words.text.toString()
                            snapshotFlow { words.text.toString() }
                                .collect {
                                    if (it != last) {
                                        last = it
                                        actions.editSheetOther(id, index, it)
                                    }
                                }
                        }
                        KitField(words, label = stringResource(Res.string.chatRequestOtherField))
                    }
                }
            }
        }
    }
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

/** "The agent waits until you answer. Nothing is lost." plus how many more wait. */
@Composable
private fun waitsLine(moreWaiting: Int): String {
    val waits = stringResource(Res.string.chatRequestIfIgnored)
    return if (moreWaiting > 0)
        "$waits ${pluralStringResource(Res.plurals.chatRequestMoreWaiting, moreWaiting, Numbers.format(moreWaiting))}"
    else waits
}
