package io.github.eslamasabry.oc.feature.chat.requests.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYou
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouReason
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatRequestIfIgnored
import io.github.eslamasabry.oc.ui.words.res.chatRequestMoreWaiting
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherAnswer
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherField
import io.github.eslamasabry.oc.ui.words.res.chatStopAfterStepUndo
import io.github.eslamasabry.oc.ui.words.res.chatUiAlwaysAllow
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitRequestAllowOnce
import io.github.eslamasabry.oc.ui.words.res.kitRequestAnswer
import io.github.eslamasabry.oc.ui.words.res.kitRequestChooseOneReason
import io.github.eslamasabry.oc.ui.words.res.kitRequestReject
import io.github.eslamasabry.oc.ui.words.res.kitRequestReplyEmptyReason
import io.github.eslamasabry.oc.ui.words.res.kitRequestSend
import io.github.eslamasabry.oc.ui.words.res.kitRequestSendAnswers
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
    key(card.id) {
        LaunchedEffect(card.id) { actions.shown(card.id) }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            when (val phase = card.state) {
                CardState.Waiting -> {
                    val since = card.waitingSinceMs
                    KitNeedsYou(
                        KitNeedsYouReason.Decision,
                        waiting =
                            if (nowMs != null && since != null) waitedWords(nowMs - since)
                            else null,
                    )
                    when (card) {
                        is RequestCardUi.Permission ->
                            PermissionWaiting(card, state.moreWaiting, actions)
                        is RequestCardUi.Question ->
                            QuestionWaiting(card, state.moreWaiting, actions)
                    }
                }
                is CardState.Held -> HeldLine(card.id, answerWords(phase.words), actions)
                is CardState.Sending ->
                    AnsweredLine(answerWords(phase.words), KitReceiptState.Sending, null)
                is CardState.Sent ->
                    AnsweredLine(answerWords(phase.words), KitReceiptState.Sent, null)
                is CardState.NotAccepted ->
                    AnsweredLine(answerWords(phase.words), KitReceiptState.Refused(null)) {
                        actions.retry(card.id)
                    }
            }
        }
    }
    FullQuestionSheet(
        state.questionSheet,
        actions as? io.github.eslamasabry.oc.feature.chat.requests.presentation.QuestionSheetActions,
    )
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
        onConfirm = actions::confirmAlwaysAllow,
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
        subject = askSubject(card.ask.targets),
        allowOnceLabel = stringResource(Res.string.kitRequestAllowOnce),
        alwaysLabel = if (card.canAlwaysAllow) alwaysLabel(card.ask.always) else null,
        denyLabel = stringResource(Res.string.kitRequestReject),
        onAllowOnce = { actions.allowOnce(card.id) },
        onAlways = if (card.canAlwaysAllow) ({ actions.askAlwaysAllow(card.id) }) else null,
        onDeny = { actions.reject(card.id) },
    )
}

@Composable
private fun QuestionWaiting(
    card: RequestCardUi.Question,
    moreWaiting: Int,
    actions: ChatRequestActions,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        if (card.title.isNotBlank())
            KitText(card.title, role = TypeRole.Headline, kind = KitTextKind.Reading)
        KitText(card.question, role = TypeRole.Body, kind = KitTextKind.Reading)
        KitText(waitsLine(moreWaiting), role = TypeRole.Secondary, tone = TextTone.Secondary)
        if (!card.oneAnswer) {
            KitButton(
                stringResource(Res.string.kitRequestAnswer),
                onClick = { actions.openSheet(card.id) },
            )
            return@Column
        }
        card.options.forEach { option ->
            KitChoiceRow(
                title = option,
                selected = false,
                mark = KitChoiceMark.Radio,
                onClick = { actions.choose(card.id, option) },
            )
        }
        if (card.custom) {
            val other = card.other
            KitChoiceRow(
                title = stringResource(Res.string.chatRequestOtherAnswer),
                selected = other != null,
                onClick = { actions.openOther(card.id) },
            )
            if (other != null) OtherField(card.id, other.text, other.canSend, actions)
        }
    }
}

/** "Something else": the typed answer is kept as the request's draft by the model. */
@Composable
private fun OtherField(id: RequestId, text: String, canSend: Boolean, actions: ChatRequestActions) {
    val field = rememberTextFieldState(text)
    LaunchedEffect(id) {
        snapshotFlow { field.text.toString() }.collect { actions.editOther(id, it) }
    }
    KitField(
        field,
        label = stringResource(Res.string.chatRequestOtherField),
        onSubmit = { actions.sendOther(id) },
    )
    KitButton(
        stringResource(Res.string.kitRequestSend),
        onClick = { actions.sendOther(id) },
        enabled =
            if (canSend) KitEnabled.Enabled
            else KitEnabled.Disabled(stringResource(Res.string.kitRequestReplyEmptyReason)),
    )
}

/** Inside the hold: "Allowed · Undo" (plan 08 `HELD_ANSWER`). */
@Composable
private fun HeldLine(id: RequestId, words: String, actions: ChatRequestActions) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitText(words, Modifier.weight(1f), role = TypeRole.Body, kind = KitTextKind.Frame)
        KitButton(
            stringResource(Res.string.chatStopAfterStepUndo),
            onClick = { actions.undo(id) },
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

/** Multi-prompt, optional and long-option questions have a reachable, draft-preserving sheet. */
@Composable
private fun FullQuestionSheet(
    state: io.github.eslamasabry.oc.feature.chat.requests.presentation.QuestionSheetUi?,
    actions: io.github.eslamasabry.oc.feature.chat.requests.presentation.QuestionSheetActions?,
) {
    if (actions == null) return
    val question = state?.question
    val blocked =
        state?.let {
            io.github.eslamasabry.oc.engine.requests.questions.QuestionSheet.block(
                it.question,
                it.drafts,
            ) != null
        } ?: true
    val reason = stringResource(Res.string.kitRequestChooseOneReason)
    io.github.eslamasabry.oc.ui.kit.modal.KitSheet(
        visible = state != null,
        title = stringResource(Res.string.kitRequestAnswer),
        onDismissRequest = actions::closeSheet,
        primary =
            io.github.eslamasabry.oc.ui.kit.KitAction(
                label = stringResource(Res.string.kitRequestSendAnswers),
                onClick = { question?.let { actions.sendSheet(it.id) } },
                enabled = KitEnabled.of(!blocked, reason),
            ),
        secondary =
            io.github.eslamasabry.oc.ui.kit.KitAction(
                stringResource(Res.string.kitConfirmCancel),
                actions::closeSheet,
            ),
    ) {
        if (state != null)
            state.question.prompts.forEachIndexed { index, prompt ->
                val draft =
                    state.drafts.getOrNull(index)
                        ?: io.github.eslamasabry.oc.engine.requests.questions.PromptDraft()
                val id = state.question.id
                key(id, index) {
                    if (prompt.title.isNotBlank()) KitText(prompt.title, role = TypeRole.Headline)
                    KitText(prompt.question, role = TypeRole.Body)
                    prompt.choices.forEach { choice ->
                        KitChoiceRow(
                            title = choice.label,
                            supporting = choice.description.takeIf { it.isNotBlank() },
                            selected = choice.label in draft.chosen,
                            mark =
                                if (prompt.multiple) KitChoiceMark.Check else KitChoiceMark.Radio,
                            onClick = { actions.chooseSheet(id, index, choice.label) },
                        )
                    }
                    if (prompt.custom) {
                        val field = rememberTextFieldState(draft.own)
                        val currentDraft by rememberUpdatedState(draft)
                        // Choosing an offered answer clears a single-answer custom draft in the
                        // engine. Keep the
                        // visible field in agreement without turning that programmatic clear into a
                        // custom answer.
                        LaunchedEffect(draft.chosen, draft.ownOpen) {
                            if (
                                !prompt.multiple &&
                                    !draft.ownOpen &&
                                    field.text.toString() != draft.own
                            ) {
                                field.edit { replace(0, length, draft.own) }
                            }
                        }
                        LaunchedEffect(id, index) {
                            snapshotFlow { field.text.toString() }
                                .collect { now ->
                                    if (now != currentDraft.own) actions.writeSheet(id, index, now)
                                }
                        }
                        KitField(field, label = stringResource(Res.string.chatRequestOtherField))
                    }
                }
            }
    }
}
