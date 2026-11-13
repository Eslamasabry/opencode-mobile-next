package io.github.eslamasabry.oc.feature.chat.requests.ui

import androidx.compose.foundation.layout.padding
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestStack
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestStackRules
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
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
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
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
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysInProject
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitQuestionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestAnsweredRow
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestTarget
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestWaitingCaption
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitRequestAllowOnce
import io.github.eslamasabry.oc.ui.words.res.approvalCopied
import io.github.eslamasabry.oc.ui.words.res.approvalCopyAsk
import io.github.eslamasabry.oc.ui.words.res.approvalDeny
import io.github.eslamasabry.oc.ui.words.res.chatRequestIfIgnored
import io.github.eslamasabry.oc.ui.words.res.chatRequestCardLabel
import io.github.eslamasabry.oc.ui.words.res.chatRequestNoLongerNeeded
import io.github.eslamasabry.oc.ui.words.res.chatRequestNoLongerNeededEnding
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherAnswer
import io.github.eslamasabry.oc.ui.words.res.chatRequestOtherField
import io.github.eslamasabry.oc.ui.words.res.chatStopAfterStepUndo
import io.github.eslamasabry.oc.ui.words.res.chatUiAlwaysAllow
import io.github.eslamasabry.oc.ui.words.res.invitation40Question
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceDismissDetail
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceDismissRequest
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitRequestAnswer
import io.github.eslamasabry.oc.ui.words.res.kitRequestChangeSize
import io.github.eslamasabry.oc.ui.words.res.kitRequestReplyEmptyReason
import io.github.eslamasabry.oc.ui.words.res.kitRequestSend
import io.github.eslamasabry.oc.feature.chat.requests.presentation.PermissionDetails
import io.github.eslamasabry.oc.feature.chat.requests.presentation.PermissionChangeUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AlwaysScope
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AlwaysScopes
import io.github.eslamasabry.oc.ui.words.res.approvalAlwaysCoversWords
import io.github.eslamasabry.oc.ui.words.res.approvalAlwaysPattern
import io.github.eslamasabry.oc.ui.words.res.approvalNoteDenyOnly
import io.github.eslamasabry.oc.ui.words.res.approvalScopeAnyFileChange
import io.github.eslamasabry.oc.ui.words.res.approvalScopeCommandFamily
import io.github.eslamasabry.oc.ui.words.res.approvalScopeEverything
import io.github.eslamasabry.oc.ui.words.res.approvalScopeFileChangesMatching
import io.github.eslamasabry.oc.ui.words.res.approvalScopeMatching
import io.github.eslamasabry.oc.ui.words.res.chatRequestAlwaysAnyCommandWhat
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyCommand
import io.github.eslamasabry.oc.engine.requests.ask.AskView
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffPreview
import io.github.eslamasabry.oc.ui.kitchat.rememberKitRedactedCopyAction
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestChange
import io.github.eslamasabry.oc.ui.words.res.approvalChangeMissing
import io.github.eslamasabry.oc.ui.words.res.approvalChangeOf
import io.github.eslamasabry.oc.ui.words.res.approvalCopyChange
import io.github.eslamasabry.oc.ui.words.res.kitSuggestionsShowAll
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.feature.chat.requests.presentation.PermissionFact
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.words.res.chatRequestDetailAsks
import io.github.eslamasabry.oc.ui.words.res.chatRequestDetailHelper
import io.github.eslamasabry.oc.ui.words.res.chatRequestDetailPatterns
import io.github.eslamasabry.oc.ui.words.res.chatRequestDetailRange
import io.github.eslamasabry.oc.ui.words.res.chatRequestDetailRunsIn
import io.github.eslamasabry.oc.ui.words.res.chatRequestDetailTool
import io.github.eslamasabry.oc.ui.words.res.chatRequestReadFrom
import io.github.eslamasabry.oc.ui.words.res.chatRequestReadLines
import io.github.eslamasabry.oc.ui.words.res.chatUiTellTheAgentWhyOrWhatTo
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import org.jetbrains.compose.resources.pluralStringResource
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
    // P-009: the slot stays composed while its last card folds away (fade + collapse, instant under
    // reduced motion), and a card that arrives right after another left unfolds in the same stack
    // instead of swapping in.
    val held = remember { RequestSlotHold() }
    val cards = held.cards(state.cards)
    // P-103: the first waiting card in full, the others one line each until the person opens one.
    var opened by remember { mutableStateOf(emptySet<RequestId>()) }
    // S28 story 4: the card whose Details sheet is open (screen state, never a route).
    var details by remember { mutableStateOf<RequestId?>(null) }
    val open = RequestStackRules.stillOpened(opened, cards)
    val folded = RequestStackRules.folded(cards, open, state.focused)
    KitReveal(visible = state.cards.isNotEmpty(), modifier = modifier) {
        val total = cards.size
        KitRequestStack(
            items = cards,
            key = { it.id },
            label = { index, card ->
                stringResource(
                    Res.string.chatRequestCardLabel,
                    Numbers.format(index + 1),
                    Numbers.format(total),
                    cardTarget(card),
                )
            },
        ) { card ->
            // The row and the card cross-fade (QUICK; instant under reduced motion); the size never animates.
            KitSwap(card.id in folded, alignment = Alignment.TopStart) { isFolded ->
                if (isFolded) FoldedRequestRow(card) { opened = open + card.id }
                else key(card.id, card.captured) { RequestCard(card, actions, nowMs) { details = card.id } }
            }
        }
    }
    state.sheet?.let { ChatQuestionSheet(it, actions) }
    // The sheet closes itself when its request leaves the slot (answered here or elsewhere).
    val detailed = details?.let { id -> state.cardFor(id) as? RequestCardUi.Permission }
        ?.takeIf { it.state == CardState.Waiting && it.captured != null }
    PermissionDetailsSheet(detailed, actions) { details = null }
    val confirm = state.confirmAlways
    KitConfirm(
        spec =
            confirm?.let {
                val request = it.captured?.request
                val patterns = request?.let { ask -> ask.always.ifEmpty { ask.patterns }.joinToString(", ") }
                val mask = rememberKitMask()
                // Review 2026-11-13 #3: the scope in words ("any file change"), never a bare pattern.
                val scope = request?.let(AlwaysScopes::of)?.let { s -> scopeWords(s, mask) }
                KitConfirmSpec(
                    title = if (it.invitation) stringResource(Res.string.consentAlwaysAllowTitle)
                        else alwaysConfirmTitle(it.grant, request?.patterns?.firstOrNull()?.let(mask)),
                    confirmLabel = stringResource(Res.string.chatUiAlwaysAllow),
                    kind = KitConfirmKind.Neutral,
                    // S30 story 1 (Flutter `_confirmAlways`): the body always says what the grant covers and where.
                    body = stringResource(
                        Res.string.chatRequestAlwaysScope,
                        scope
                            ?: patterns?.takeIf { p -> p.isNotEmpty() }?.let { p -> KitBidi.ltr(mask(p)) }
                            ?: stringResource(Res.string.chatUiAllMatchingRequests),
                        stringResource(if (it.invitation) Res.string.chatUiInThisChat else Res.string.chatRequestAlwaysInProject),
                    ),
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
private fun RequestCard(
    card: RequestCardUi,
    actions: ChatRequestActions,
    nowMs: Long?,
    onDetails: () -> Unit,
) {
    LaunchedEffect(card.id) { actions.shown(card.id) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        when (val phase = card.state) {
            CardState.Waiting -> {
                val since = card.waitingSinceMs
                // C02: the caption keeps the page gutter, as the card under it does; it used to start on the window's
                // edge (and was clipped there in Arabic).
                KitRequestWaitingCaption(
                    waiting =
                        if (nowMs != null && since != null) waitedWords(nowMs - since) else null,
                    modifier = Modifier.padding(horizontal = kitPageGutter()),
                )
                when (card) {
                    is RequestCardUi.Permission -> {
                        PermissionWaiting(card, actions, onDetails.takeIf { card.captured != null })
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
                AnsweredLine(card, receiptWords(card, phase.words), KitReceiptState.Sending, null)
            is CardState.Sent ->
                AnsweredLine(card, receiptWords(card, phase.words), KitReceiptState.Sent, null)
            is CardState.NotAccepted ->
                AnsweredLine(card, receiptWords(card, phase.words), KitReceiptState.Refused(null)) {
                    actions.performReviewed(card, ReviewedRequestAction.Retry)
                }
            CardState.NoLongerNeeded -> NoLongerNeededLine(card)
        }
    }
}

/**
 * P-103: a waiting request after the first, as one line naming what it asks ("Run a shell command, npm test") on the
 * page's 16 dp edge; a tap opens it in full. Its place in the stack is spoken by the stack's label.
 */
@Composable
private fun FoldedRequestRow(card: RequestCardUi, onOpen: () -> Unit) {
    KitExpandRow(
        title = cardTarget(card),
        expanded = false,
        onExpandedChange = { if (it) onOpen() },
        leading = if (card is RequestCardUi.Question) KitIcons.question else KitIcons.permissions,
        titleMaxLines = 1,
        modifier = Modifier.testTag("chat-request-folded"),
    ) {}
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
 * words and "No longer needed · The run is ending, so nothing was sent." until the card leaves.
 */
@Composable
private fun NoLongerNeededLine(card: RequestCardUi) {
    val title =
        when (card) {
            is RequestCardUi.Permission -> askQuestion(card.ask)
            is RequestCardUi.Question -> card.question.ifBlank { card.title }
        }
    // P-109: a plain row on the page's 16 dp edge (the answered row sat on the card's inset, its words at 77 dp).
    KitRow(
        title = title,
        supporting =
            // The card retires as soon as Stop is accepted, while the run may still be "Stopping": never "The run stopped".
            "${stringResource(Res.string.chatRequestNoLongerNeeded)} · ${stringResource(Res.string.chatRequestNoLongerNeededEnding)}",
        leading = KitRowLeading.Glyph(if (card is RequestCardUi.Question) KitIcons.question else KitIcons.permissions),
        supportingMaxLines = 2,
        modifier = Modifier.testTag("chat-request-no-longer-needed").semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun receiptWords(card: RequestCardUi, words: AnswerWords): String =
    answerLine(words, (card as? RequestCardUi.Permission)?.ask, rememberKitMask())

@Composable
private fun PermissionWaiting(
    card: RequestCardUi.Permission,
    actions: ChatRequestActions,
    onDetails: (() -> Unit)?,
) {
    val always = card.canAlwaysAllow && !card.invitation
    val copies = RequestCardRules.copiesTargets(card.ask)
    KitPermissionCard(
        // RC1 device pass: one rule for every file ask, the question names its file ("Edit rc1.txt?" as "Create
        // rc1.txt?"); the target line under it adds the path and the change size.
        title = askQuestion(card.ask),
        toolLabel = if (RequestCardRules.showsToolLine(card.ask)) askToolLine(card.ask) else null,
        copyLabel = if (copies) stringResource(Res.string.approvalCopyAsk) else null,
        copiedLabel = if (copies) stringResource(Res.string.approvalCopied) else null,
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
        // Review 2026-11-13 #3: one pair everywhere a request is answered: "Allow once" says how long, "Deny" matches
        // the transcript's "You declined this".
        allowOnceLabel = stringResource(Res.string.kitRequestAllowOnce),
        // S30 story 1: Flutter's words (permission_ask.dart): "Always allow npm install", "Always allow commands like
        // this", "Always allow edits", else "Always allow requests like this". The confirm names the exact patterns.
        alwaysLabel = if (always) alwaysLabel(card.ask.always) else null,
        denyLabel = stringResource(Res.string.approvalDeny),
        onAllowOnce = { actions.performReviewed(card, ReviewedRequestAction.AllowOnce) },
        onAlways =
            if (card.canAlwaysAllow && !card.invitation)
                ({ actions.performReviewed(card, ReviewedRequestAction.AskAlways) })
            else null,
        onDeny = { actions.performReviewed(card, ReviewedRequestAction.Reject) },
        onDetails = onDetails,
        change = card.change?.let { changeOnCard(it, card.ask, onDetails) },
    )
}

/**
 * Review 2026-11-13 #1: the card shows what a file ask will write before the answers: the first lines of the change
 * (masked), "Show all" when more follows (it opens Details, which holds the whole change), or plain words when the
 * request carries no content at all.
 */
@Composable
private fun changeOnCard(change: PermissionChangeUi, ask: AskView, onDetails: (() -> Unit)?): KitRequestChange {
    val mask = rememberKitMask()
    val first = change.first
    val preview = remember(first, mask) { first?.let { KitDiffPreview.fromPatch(mask(it), RequestCardRules.CARD_PREVIEW_LINES) } ?: KitDiffPreview.Empty }
    val name = change.files.firstOrNull()?.path ?: askWhat(ask)
    val open = onDetails ?: {}
    return KitRequestChange(
        preview = preview,
        label = stringResource(Res.string.approvalChangeOf, KitBidi.ltr(mask(name.orEmpty()))),
        showAllLabel =
            if (onDetails != null && (preview.more || change.files.size > 1)) stringResource(Res.string.kitSuggestionsShowAll)
            else null,
        onOpen = open,
        missing = if (change.missing) stringResource(Res.string.approvalChangeMissing) else null,
    )
}

/**
 * S28 story 4 (Flutter `showPermissionDetails`): the whole command, file or pattern list, the change an edit asks to
 * make, and the tool's technical names under Details (Tool, Requested patterns, Part of the file, Runs in). Allow
 * once and Reject answer here as on the card, then the sheet closes. Server text is masked before it is drawn.
 */
@Composable
private fun PermissionDetailsSheet(
    card: RequestCardUi.Permission?,
    actions: ChatRequestActions,
    onClose: () -> Unit,
) {
    val request = card?.captured?.request
    val mask = rememberKitMask()
    val redactor = LocalKitRedactor.current
    val shown = remember(request) { request?.let(PermissionDetails::of) }
    // Flutter `showPermissionDetails`: the optional note sent with Reject lives here, not on the card.
    val note = rememberTextFieldState()
    LaunchedEffect(card?.id) { note.clearText() }
    KitSheet(
        visible = card != null && shown != null,
        onDismissRequest = onClose,
        title = card?.let { askQuestion(it.ask) } ?: stringResource(Res.string.kitDetails),
        closeLabel = stringResource(Res.string.kitConfirmCancel),
        // Grows to what it holds, up to the sheet's own cap; a long request scrolls with the answers pinned.
        height = KitSheetHeight.Content,
        // Deny | Allow, the card's own arrangement.
        answerPair = true,
        primary = card?.let {
            KitAction(stringResource(Res.string.kitRequestAllowOnce), {
                onClose()
                actions.performReviewed(it, ReviewedRequestAction.AllowOnce)
            })
        },
        secondary = card?.let {
            KitAction(stringResource(Res.string.approvalDeny), {
                val said = note.text.toString().trim()
                onClose()
                actions.performReviewed(
                    it,
                    if (said.isEmpty()) ReviewedRequestAction.Reject else ReviewedRequestAction.RejectWith(said),
                )
            })
        },
    ) {
        val details = shown ?: return@KitSheet
        Column(
            Modifier.fillMaxWidth().testTag("chat-permission-details"),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            // W4: a folder read shows the folder (never the server's "."), a search its pattern.
            val ask = card?.ask
            val full =
                (if (ask != null) RequestCardRules.detailsText(ask, details.fullText) else details.fullText)
                    ?: if (ask == null || RequestCardRules.detailsSaysAllMatching(ask))
                        stringResource(Res.string.chatUiAllMatchingRequests)
                    else null
            // Review 2026-11-13 #1: the whole change first, one block per file with its name; the path box only when
            // there is no change to name it.
            val change = card?.change
            val shownChange = change?.takeIf { it.first != null }
            if (full != null && shownChange == null)
                CommandOrTarget(mask(full), details.command)
            if (shownChange != null) WholeChange(shownChange, mask)
            else if (change == null) details.diff?.let { diff -> KitCodeBlock(mask(diff), KitCodeKind.Code, wrap = true, showWrapToggle = false) }
            if (change?.missing == true)
                KitText(
                    stringResource(Res.string.approvalChangeMissing),
                    Modifier.testTag("chat-permission-change-missing"),
                    role = TypeRole.Secondary,
                )
            // Review 2026-11-13 #3: what Always covers is said only where Always is offered, in words, with the raw
            // pattern under it; the facts are read, not copied (the one copy is the command's or the change's own).
            val offersAlways = card?.canAlwaysAllow == true && !card.invitation
            val values =
                details.facts
                    .filter { fact -> fact !is PermissionFact.AlsoCovers || offersAlways }
                    .map { fact -> permissionFactValue(fact, redactor).copy(copyable = false) }
            val scope = request?.takeIf { offersAlways }?.let(AlwaysScopes::of)?.let { scopeWords(it, mask) }
            // The scope is for the person, so it reads in plain words above the technical fold.
            if (scope != null)
                KitText(
                    stringResource(Res.string.approvalAlwaysCoversWords, scope),
                    Modifier.testTag("chat-permission-always-scope"),
                    role = TypeRole.Secondary,
                )
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.approvalCopyAsk),
                values = values,
                initiallyExpanded = true,
            )
            KitField(
                note,
                label = stringResource(Res.string.chatUiTellTheAgentWhyOrWhatTo),
                modifier = Modifier.testTag("permission-reject-message"),
                kind = KitFieldKind.Multiline,
                // The note travels with Deny only; Allow once never sends it.
                helper = stringResource(Res.string.approvalNoteDenyOnly),
            )
        }
    }
}

/** The command (with its one "Copy command") or the target a non-change ask names. */
@Composable
private fun CommandOrTarget(text: String, command: Boolean) {
    if (!command) {
        KitCodeBlock(text, KitCodeKind.Output, wrap = true, showWrapToggle = false)
        return
    }
    val copy =
        rememberKitRedactedCopyAction(
            stringResource(Res.string.kitCodeCopyCommand),
            stringResource(Res.string.approvalCopied),
            { text },
        )
    KitCodeBlock(
        text,
        KitCodeKind.Command,
        wrap = true,
        showWrapToggle = false,
        copyLabel = copy.label,
        onCopy = { copy.onClick?.invoke() },
    )
}

/** What an Always answer covers, in words: "any file change", "npm install with any arguments", "every command". */
@Composable
private fun scopeWords(scope: AlwaysScope, mask: (String) -> String): String =
    when (scope) {
        AlwaysScope.AnyFileChange -> stringResource(Res.string.approvalScopeAnyFileChange)
        is AlwaysScope.FileChangesMatching ->
            stringResource(Res.string.approvalScopeFileChangesMatching, KitBidi.ltr(mask(scope.patterns)))
        AlwaysScope.AnyCommand -> stringResource(Res.string.chatRequestAlwaysAnyCommandWhat)
        is AlwaysScope.CommandFamily -> stringResource(Res.string.approvalScopeCommandFamily, KitBidi.ltr(mask(scope.command)))
        is AlwaysScope.Exactly -> KitBidi.ltr(mask(scope.text))
        AlwaysScope.Everything -> stringResource(Res.string.approvalScopeEverything)
        is AlwaysScope.Matching -> stringResource(Res.string.approvalScopeMatching, KitBidi.ltr(mask(scope.patterns)))
    }

/** Every file's change in full (masked), coloured as a diff, named by its path; each with one copy of the change. */
@Composable
private fun WholeChange(change: PermissionChangeUi, mask: (String) -> String) {
    val blocks =
        change.files.mapNotNull { file -> file.patch?.takeIf { it.isNotBlank() }?.let { file.path to it } }
            .ifEmpty { listOfNotNull(change.combined?.let { null as String? to it }) }
    for ((path, patch) in blocks) {
        key(path, patch) {
            val masked = remember(patch, mask) { mask(patch) }
            val copy =
                rememberKitRedactedCopyAction(
                    stringResource(Res.string.approvalCopyChange),
                    stringResource(Res.string.approvalCopied),
                    { masked },
                )
            KitCodeBlock(
                masked,
                KitCodeKind.Code,
                Modifier.testTag("chat-permission-change"),
                language = CodeLanguage.Diff,
                maxLines = Int.MAX_VALUE,
                wrap = true,
                showWrapToggle = false,
                copyLabel = copy.label,
                onCopy = { copy.onClick?.invoke() },
                fileName = path?.let(mask),
            )
        }
    }
}

/** One fact, in Flutter's words ("Tool", "Runs in", "Part of the file: from line 20, 80 lines"). */
@Composable
private fun permissionFactValue(fact: PermissionFact, redactor: Redactor): KitTechnicalValue =
    when (fact) {
        is PermissionFact.RunsIn ->
            KitTechnicalValue(stringResource(Res.string.chatRequestDetailRunsIn), redactor.details(fact.folder))
        is PermissionFact.Asks ->
            KitTechnicalValue(stringResource(Res.string.chatRequestDetailAsks), redactor.details(fact.words))
        is PermissionFact.Range -> {
            val parts = listOfNotNull(
                fact.from?.let { stringResource(Res.string.chatRequestReadFrom, Numbers.format(it)) },
                fact.lines?.let {
                    pluralStringResource(Res.plurals.chatRequestReadLines, it.toInt(), Numbers.format(it))
                },
            )
            KitTechnicalValue(stringResource(Res.string.chatRequestDetailRange), redactor.details(parts.joinToString(", ")))
        }
        is PermissionFact.Helper ->
            KitTechnicalValue(stringResource(Res.string.chatRequestDetailHelper), redactor.details(fact.type))
        is PermissionFact.Tool ->
            KitTechnicalValue(stringResource(Res.string.chatRequestDetailTool), redactor.details(fact.name))
        is PermissionFact.Patterns ->
            KitTechnicalValue(
                stringResource(Res.string.chatRequestDetailPatterns),
                redactor.details(fact.lines.joinToString("\n")),
            )
        is PermissionFact.AlsoCovers ->
            KitTechnicalValue(
                stringResource(Res.string.approvalAlwaysPattern),
                redactor.details(fact.lines.joinToString("\n")),
            )
    }

@Composable
private fun QuestionWaiting(
    card: RequestCardUi.Question,
    actions: ChatRequestActions,
) {
    var confirming by remember { mutableStateOf(false) }
    KitConfirm(
        spec = if (!confirming) null else KitConfirmSpec(
            title = stringResource(Res.string.e7WorkspaceDismissRequest),
            confirmLabel = stringResource(Res.string.kitSheetDismiss),
            body = stringResource(Res.string.e7WorkspaceDismissDetail),
            kind = KitConfirmKind.Neutral,
        ),
        onConfirm = { confirming = false; actions.dismissQuestion(card.id) },
        onCancel = { confirming = false },
    )
    KitQuestionCard(
        dismiss = KitAction(stringResource(Res.string.kitSheetDismiss), { confirming = true }),
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

/**
 * Inside the hold: "Allowed · Undo" (plan 08 `HELD_ANSWER`), as the kit's answered row, so it sits on the page's edge
 * like the card it replaces (it was drawn from the window's edge).
 */
@Composable
private fun HeldLine(card: RequestCardUi, words: String, actions: ChatRequestActions) {
    KitRequestAnsweredRow(
        icon = if (card is RequestCardUi.Question) KitIcons.question else KitIcons.permissions,
        title = words,
        modifier = Modifier.testTag("chat-request-answered").semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        KitButton(
            stringResource(Res.string.chatStopAfterStepUndo),
            onClick = { actions.performReviewed(card, ReviewedRequestAction.Undo) },
            role = KitButtonRole.Tertiary,
        )
    }
}

/** After the hold: the answer and its receipt ("Sent" is never "Done"), on the page's edge (the kit's answered row). */
@Composable
private fun AnsweredLine(card: RequestCardUi, words: String, receipt: KitReceiptState, onRetry: (() -> Unit)?) {
    KitRequestAnsweredRow(
        icon = if (card is RequestCardUi.Question) KitIcons.question else KitIcons.permissions,
        title = words,
        modifier = Modifier.testTag("chat-request-answered"),
    ) {
        KitReceipt(receipt, onRetry = onRetry)
    }
}
