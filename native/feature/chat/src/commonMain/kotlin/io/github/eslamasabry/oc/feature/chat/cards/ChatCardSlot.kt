package io.github.eslamasabry.oc.feature.chat.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.cards.AgentCard
import io.github.eslamasabry.oc.engine.cards.CardAnswer
import io.github.eslamasabry.oc.engine.cards.CardAsk
import io.github.eslamasabry.oc.engine.cards.CardBoardState
import io.github.eslamasabry.oc.engine.cards.CardDelivery
import io.github.eslamasabry.oc.engine.cards.CardEntry
import io.github.eslamasabry.oc.engine.cards.CardField
import io.github.eslamasabry.oc.engine.cards.CardFieldType
import io.github.eslamasabry.oc.engine.cards.CardNode
import io.github.eslamasabry.oc.engine.cards.CardParse
import io.github.eslamasabry.oc.engine.cards.CardRefusal
import io.github.eslamasabry.oc.engine.cards.CardState
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerAttachSource
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.card.KitAgentCard
import io.github.eslamasabry.oc.ui.kitchat.card.KitAgentCardMode
import io.github.eslamasabry.oc.ui.kitchat.card.KitCardChoice
import io.github.eslamasabry.oc.ui.kitchat.card.KitCardConfirm
import io.github.eslamasabry.oc.ui.kitchat.card.KitCardForm
import io.github.eslamasabry.oc.ui.kitchat.card.KitCardNode
import io.github.eslamasabry.oc.ui.kitchat.card.KitCardPhoto
import io.github.eslamasabry.oc.ui.kitchat.card.KitCardPhotoItem
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormField
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormFieldKind
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormOption
import io.github.eslamasabry.oc.ui.kitchat.form.KitFormValue
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentCardAsks
import io.github.eslamasabry.oc.ui.words.res.agentCardAsksAnnouncement
import io.github.eslamasabry.oc.ui.words.res.agentCardBusy
import io.github.eslamasabry.oc.ui.words.res.agentCardCancelDefault
import io.github.eslamasabry.oc.ui.words.res.agentCardChartBar
import io.github.eslamasabry.oc.ui.words.res.agentCardChartHighest
import io.github.eslamasabry.oc.ui.words.res.agentCardChartLine
import io.github.eslamasabry.oc.ui.words.res.agentCardChartNone
import io.github.eslamasabry.oc.ui.words.res.agentCardChartPoints
import io.github.eslamasabry.oc.ui.words.res.agentCardChartSeries
import io.github.eslamasabry.oc.ui.words.res.agentCardChooseOneOrMore
import io.github.eslamasabry.oc.ui.words.res.agentCardConfirmDefault
import io.github.eslamasabry.oc.ui.words.res.agentCardDangerBody
import io.github.eslamasabry.oc.ui.words.res.agentCardDangerConfirm
import io.github.eslamasabry.oc.ui.words.res.agentCardDangerTitle
import io.github.eslamasabry.oc.ui.words.res.agentCardDeliveryUnknown
import io.github.eslamasabry.oc.ui.words.res.agentCardDetails
import io.github.eslamasabry.oc.ui.words.res.agentCardDiffMore
import io.github.eslamasabry.oc.ui.words.res.agentCardFailed
import io.github.eslamasabry.oc.ui.words.res.agentCardFieldMax
import io.github.eslamasabry.oc.ui.words.res.agentCardFieldMin
import io.github.eslamasabry.oc.ui.words.res.agentCardFieldNumber
import io.github.eslamasabry.oc.ui.words.res.agentCardFieldRequired
import io.github.eslamasabry.oc.ui.words.res.agentCardFieldRequiredHint
import io.github.eslamasabry.oc.ui.words.res.agentCardFormFix
import io.github.eslamasabry.oc.ui.words.res.agentCardNotAnswered
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoChoose
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoCount
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoMax
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoName
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoNone
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoRemove
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoTake
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoTooLarge
import io.github.eslamasabry.oc.ui.words.res.agentCardPhotoUnavailable
import io.github.eslamasabry.oc.ui.words.res.agentCardProgressValue
import io.github.eslamasabry.oc.ui.words.res.agentCardReports
import io.github.eslamasabry.oc.ui.words.res.agentCardSend
import io.github.eslamasabry.oc.ui.words.res.agentCardSending
import io.github.eslamasabry.oc.ui.words.res.agentCardSent
import io.github.eslamasabry.oc.ui.words.res.agentCardShow
import io.github.eslamasabry.oc.ui.words.res.agentCardTableLabel
import io.github.eslamasabry.oc.ui.words.res.agentCardTextNote
import io.github.eslamasabry.oc.ui.words.res.agentCardUnknown
import io.github.eslamasabry.oc.ui.words.res.agentCardUnreadable
import io.github.eslamasabry.oc.ui.words.res.agentCardYourAnswer
import io.github.eslamasabry.oc.ui.words.res.cardsFromAgentsTitle
import io.github.eslamasabry.oc.ui.words.res.kitDateInvalid
import io.github.eslamasabry.oc.ui.words.res.kitUndoAction
import io.github.eslamasabry.oc.ui.words.res.photoPermissionDenied
import io.github.eslamasabry.oc.ui.words.res.photoUnavailable
import kotlin.io.encoding.Base64
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The open conversation's cards, provided by the chat entry; null where the chat draws no cards.
 */
public val LocalChatCards: androidx.compose.runtime.ProvidableCompositionLocal<ChatCardSlotHost?> =
    staticCompositionLocalOf {
        null
    }

/** What the slot needs: the board's state and the three intents. Tests pass a fake. */
public interface ChatCardSlotHost {
    public val state: kotlinx.coroutines.flow.StateFlow<CardBoardState>

    public fun answer(card: AgentCard, answer: CardAnswer)

    public fun undo(card: AgentCard)

    public fun openLink(url: String)

    /** The conversation's agent, as the cards name it ("OpenCode"). */
    public val agentName: String

    /**
     * Settings › Cards from agents: the visible way to turn cards on (a read-only card offers it).
     */
    public fun openSettings() {}
}

/**
 * Adapts the per-conversation model to the slot. [links] is the chat's own link action (B-SEC-065):
 * a card link is server content, so it takes the chat's ask-first confirmation naming the host and
 * opens only through the app's ExternalLinkPolicy → ExternalLinkOpener path, never straight from
 * the card.
 */
public interface ChatCardPhotoHost {
    public val photoSource: ComposerAttachSource?

    public fun answerPhotos(card: AgentCard, photos: List<PromptAttachment>)
}

public class ModelCardSlotHost(
    private val model: ChatCardsModel,
    private val links: (String) -> Unit,
    private val settings: () -> Unit = {},
    override val agentName: String = "OpenCode",
    override val photoSource: ComposerAttachSource? = null,
) : ChatCardSlotHost, ChatCardPhotoHost {
    override fun answerPhotos(card: AgentCard, photos: List<PromptAttachment>): Unit =
        model.answer(card, CardAnswer.Photo(photos.size), photos)

    override fun openSettings(): Unit = settings()

    override val state: kotlinx.coroutines.flow.StateFlow<CardBoardState>
        get() = model.state

    override fun answer(card: AgentCard, answer: CardAnswer): Unit = model.answer(card, answer)

    override fun undo(card: AgentCard): Unit = model.undo(card)

    override fun openLink(url: String): Unit = links(url)
}

/**
 * The cards of [turnId], drawn under that turn's work line (plan 09 §13.7: in the reply, never
 * inside the folded work line). Nothing when the chat has no card slot or the turn holds no card
 * call.
 */
@Composable
public fun ChatTurnCards(turnId: String, modifier: Modifier = Modifier) {
    val host = LocalChatCards.current ?: return
    val state by host.state.collectAsStateWithLifecycle()
    val entries = state.cards.filter { it.turnId == turnId }
    if (entries.isEmpty()) return
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { entry ->
            key(entry.messageId, entry.callId) {
                ChatCard(entry, state.busy, host, state.available)
            }
        }
    }
}

/** One card in its state (Flutter `AgentCardView`). */
@Composable
internal fun ChatCard(
    entry: CardEntry,
    busy: Boolean,
    host: ChatCardSlotHost,
    available: Boolean = true,
) {
    val agent = host.agentName
    // Lives outside the ask subtree so Undo restores the same pictures.
    val photos = remember(entry.messageId, entry.callId) { mutableStateListOf<PromptAttachment>() }
    val parse = entry.parse
    if (parse is CardParse.Unreadable) {
        KitAgentCard(
            eyebrow = stringResource(Res.string.agentCardAsks, agent),
            title = stringResource(Res.string.agentCardUnreadable),
            mode = KitAgentCardMode.Unreadable,
            details = parse.reason.name,
            detailsLabel = stringResource(Res.string.agentCardDetails),
        )
        return
    }
    val card = (parse as CardParse.Parsed).card
    val asks = card.ask != null
    val eyebrow =
        stringResource(if (asks) Res.string.agentCardAsks else Res.string.agentCardReports, agent)
    val body = cardBody(card.body)
    when {
        entry.delivery == CardDelivery.Held ->
            KitAgentCard(
                eyebrow = eyebrow,
                title = card.title,
                mode = KitAgentCardMode.Receipt,
                body = body,
                receiptLabel = entry.summary ?: stringResource(Res.string.agentCardYourAnswer),
                undoLabel = stringResource(Res.string.kitUndoAction),
                onUndo = { host.undo(card) },
            )
        entry.state == CardState.Answered ->
            KitAgentCard(
                eyebrow = eyebrow,
                title = card.title,
                mode = KitAgentCardMode.Receipt,
                body = body,
                onLink = host::openLink,
                receiptLabel = entry.summary ?: stringResource(Res.string.agentCardSent),
                expandLabel = stringResource(Res.string.agentCardShow),
            )
        entry.state == CardState.PassedOver ->
            KitAgentCard(
                eyebrow = eyebrow,
                title = card.title,
                mode = KitAgentCardMode.PassedOver,
                body = body,
                onLink = host::openLink,
                passedOverLabel = stringResource(Res.string.agentCardNotAnswered),
                expandLabel = stringResource(Res.string.agentCardShow),
            )
        else -> {
            val waiting = entry.state == CardState.Waiting && asks
            val blocked =
                when {
                    entry.delivery == CardDelivery.DeliveryUnknown ->
                        stringResource(Res.string.agentCardDeliveryUnknown)
                    entry.delivery == CardDelivery.Sending ->
                        stringResource(Res.string.agentCardSending)
                    busy -> stringResource(Res.string.agentCardBusy)
                    else -> null
                }
            val (notice, tone) = notice(entry)
            KitAgentCard(
                eyebrow = eyebrow,
                title = card.title,
                mode = if (asks) KitAgentCardMode.Ask else KitAgentCardMode.Report,
                body = body,
                onLink = host::openLink,
                ask =
                    when {
                        waiting -> ({
                                CardAskControls(
                                    card,
                                    card.ask!!,
                                    entry.draft,
                                    blocked,
                                    host as? ChatCardPhotoHost,
                                    photos,
                                ) {
                                    host.answer(card, it)
                                }
                            })
                        // Cards are off or unproven here: the card is read-only and says where to
                        // turn them on.
                        asks && !available -> ({
                                KitButton(
                                    stringResource(Res.string.cardsFromAgentsTitle),
                                    host::openSettings,
                                    role = KitButtonRole.Secondary,
                                    modifier = Modifier.testTag("chat-card-open-settings"),
                                )
                            })
                        else -> null
                    },
                notice =
                    notice
                        ?: if (entry.state == CardState.Unknown && asks)
                            stringResource(Res.string.agentCardUnknown)
                        else null,
                noticeTone = tone,
                announcement =
                    if (waiting)
                        stringResource(Res.string.agentCardAsksAnnouncement, agent, card.title)
                    else null,
            )
        }
    }
}

@Composable
private fun notice(entry: CardEntry): Pair<String?, StatusTone> =
    when {
        entry.delivery == CardDelivery.Sending ->
            stringResource(Res.string.agentCardSending) to StatusTone.Progress
        entry.delivery == CardDelivery.DeliveryUnknown ->
            stringResource(Res.string.agentCardDeliveryUnknown) to StatusTone.Neutral
        entry.delivery == CardDelivery.Failed ->
            stringResource(Res.string.agentCardFailed) to StatusTone.Failure
        entry.refusal != null ->
            stringResource(
                when (entry.refusal) {
                    CardRefusal.Busy -> Res.string.agentCardBusy
                    CardRefusal.CheckFirst -> Res.string.agentCardDeliveryUnknown
                    CardRefusal.Invalid -> Res.string.agentCardFormFix
                    CardRefusal.PhotosRefused -> Res.string.agentCardPhotoUnavailable
                    CardRefusal.PhotoTooLarge -> Res.string.agentCardPhotoTooLarge
                    else -> Res.string.agentCardUnknown
                }
            ) to StatusTone.Failure
        else -> null to StatusTone.Neutral
    }

/** The ask controls; what the person entered survives Undo through [draft]. */
@Composable
private fun CardAskControls(
    card: AgentCard,
    ask: CardAsk,
    draft: CardAnswer?,
    blocked: String?,
    photoHost: ChatCardPhotoHost?,
    photos: MutableList<PromptAttachment>,
    onAnswer: (CardAnswer) -> Unit,
) {
    when (ask) {
        is CardAsk.Choice -> {
            val selected =
                remember(card.identity, draft) {
                    mutableStateMapOf<String, Boolean>().apply {
                        (draft as? CardAnswer.Choice)?.ids?.forEach { put(it, true) }
                    }
                }
            KitCardChoice(
                options =
                    ask.options.map { KitFormOption(it.id, it.label, it.detail) }.toImmutableList(),
                multi = ask.multi,
                selected = selected.filterValues { it }.keys,
                onToggle = { selected[it] = !(selected[it] ?: false) },
                onSend = { onAnswer(CardAnswer.Choice(it)) },
                sendLabel = stringResource(Res.string.agentCardSend),
                chooseHint = stringResource(Res.string.agentCardChooseOneOrMore),
                blockedReason = blocked,
            )
        }
        is CardAsk.Confirm -> {
            var confirming by remember(card.identity) { mutableStateOf(false) }
            KitCardConfirm(
                confirmLabel =
                    ask.confirmLabel ?: stringResource(Res.string.agentCardConfirmDefault),
                cancelLabel = ask.cancelLabel ?: stringResource(Res.string.agentCardCancelDefault),
                danger = ask.danger,
                onAnswer = { answer ->
                    if (answer && ask.danger) confirming = true
                    else onAnswer(CardAnswer.Confirm(answer))
                },
                blockedReason = blocked,
            )
            KitConfirm(
                spec =
                    if (confirming && blocked == null)
                        KitConfirmSpec(
                            title = stringResource(Res.string.agentCardDangerTitle),
                            body = stringResource(Res.string.agentCardDangerBody),
                            confirmLabel = stringResource(Res.string.agentCardDangerConfirm),
                            kind = KitConfirmKind.Destructive,
                        )
                    else null,
                onConfirm = {
                    confirming = false
                    if (blocked == null) onAnswer(CardAnswer.Confirm(true))
                },
                onCancel = { confirming = false },
            )
        }
        is CardAsk.Form -> {
            val values =
                remember(card.identity, draft) {
                    mutableStateMapOf<String, KitFormValue>().apply {
                        putAll(CardForms.initial(ask, draft as? CardAnswer.Form))
                    }
                }
            val touched = remember(card.identity) { mutableStateMapOf<String, Boolean>() }
            val answer = CardForms.answer(ask, values)
            KitCardForm(
                fields =
                    CardForms.fields(ask)
                        .map { field ->
                            field.copy(
                                description =
                                    if (ask.fields.first { it.id == field.key }.required)
                                        stringResource(Res.string.agentCardFieldRequiredHint)
                                    else null,
                                error =
                                    if (touched[field.key] == true)
                                        formProblemWords(
                                            CardForms.problem(
                                                ask.fields.first { it.id == field.key },
                                                values[field.key],
                                            )
                                        )
                                    else null,
                                maxLength =
                                    if (
                                        field.kind == KitFormFieldKind.Text ||
                                            field.kind == KitFormFieldKind.Multiline
                                    )
                                        2000
                                    else null,
                            )
                        }
                        .toImmutableList(),
                values = values.toImmutableMap(),
                onChange = { k, v ->
                    touched[k] = true
                    values[k] = v
                },
                sendLabel = ask.submitLabel ?: stringResource(Res.string.agentCardSend),
                onSend = { answer?.let(onAnswer) },
                note = stringResource(Res.string.agentCardTextNote),
                sendBlockedReason =
                    if (answer == null) stringResource(Res.string.agentCardFormFix) else null,
                blockedReason = blocked,
            )
        }
        is CardAsk.Photo -> CardPhotoControls(card, ask, blocked, photoHost, photos)
    }
}

/** Explicit photo intake through the same bounded platform source as the composer. */
@Composable
private fun CardPhotoControls(
    card: AgentCard,
    ask: CardAsk.Photo,
    blocked: String?,
    host: ChatCardPhotoHost?,
    photos: MutableList<PromptAttachment>,
) {
    val source = host?.photoSource
    if (source == null || AttachKind.Photos !in source.kinds) {
        KitCardPhoto(
            purpose = ask.purpose,
            countLabel = "",
            unavailable = stringResource(Res.string.agentCardPhotoUnavailable),
        )
        return
    }
    val scope = rememberCoroutineScope()
    var picking by remember(card.identity) { mutableStateOf(false) }
    var failure by remember(card.identity) { mutableStateOf<Failure?>(null) }
    var tooLarge by remember(card.identity) { mutableStateOf(false) }
    val busy = blocked ?: if (picking) stringResource(Res.string.agentCardPhotoChoose) else null
    val maximum = photos.size >= ask.max
    val maxWords = stringResource(Res.string.agentCardPhotoMax)
    fun pick(kind: AttachKind) {
        if (picking || blocked != null || maximum) return
        picking = true
        failure = null
        tooLarge = false
        scope.launch {
            try {
                when (val picked = source.attach(kind, photos.toList())) {
                    is Outcome.Failed -> failure = picked.failure
                    is Outcome.Ok -> {
                        var total = photos.sumOf { attachmentBytes(it) ?: 0L }
                        picked.value.take(ask.max - photos.size).forEach { attachment ->
                            val bytes = attachmentBytes(attachment)
                            if (
                                bytes == null ||
                                    bytes > 10L * 1024 * 1024 ||
                                    total + bytes > 20L * 1024 * 1024
                            )
                                tooLarge = true
                            else {
                                total += bytes
                                photos += attachment
                            }
                        }
                    }
                }
            } finally {
                picking = false
            }
        }
    }
    KitCardPhoto(
        purpose = ask.purpose,
        countLabel = stringResource(Res.string.agentCardPhotoCount, photos.size, ask.max),
        items =
            photos
                .mapIndexed { index, attachment ->
                    KitCardPhotoItem(
                        index.toString(),
                        stringResource(Res.string.agentCardPhotoName, index + 1),
                        stringResource(Res.string.agentCardPhotoRemove, index + 1),
                        photoBytes(attachment),
                    )
                }
                .toImmutableList(),
        onRemove = { id ->
            if (busy == null)
                id.toIntOrNull()?.takeIf { it in photos.indices }?.let { photos.removeAt(it) }
        },
        takeLabel =
            if (AttachKind.Camera in source.kinds) stringResource(Res.string.agentCardPhotoTake)
            else null,
        onTake = if (AttachKind.Camera in source.kinds) ({ pick(AttachKind.Camera) }) else null,
        chooseLabel = stringResource(Res.string.agentCardPhotoChoose),
        onChoose = { pick(AttachKind.Photos) },
        sendLabel = stringResource(Res.string.agentCardSend),
        onSend = {
            if (busy == null && photos.isNotEmpty()) host?.answerPhotos(card, photos.toList())
        },
        sendBlockedReason =
            if (photos.isEmpty()) stringResource(Res.string.agentCardPhotoNone) else null,
        blockedReason = busy,
    )
    if (maximum) io.github.eslamasabry.oc.ui.kit.KitText(maxWords)
    val error = failure
    if (tooLarge || error != null) {
        val words =
            when {
                tooLarge -> stringResource(Res.string.agentCardPhotoTooLarge)
                error is Failure.Media &&
                    error.problem in
                        setOf(MediaProblem.CameraDenied, MediaProblem.CameraDeniedForever) ->
                    stringResource(Res.string.photoPermissionDenied)
                else -> stringResource(Res.string.photoUnavailable)
            }
        KitNotice(words, tone = StatusTone.Failure)
    }
}

private fun attachmentBytes(attachment: PromptAttachment): Long? {
    val prefix = "data:${attachment.mime};base64,"
    if (!attachment.url.startsWith(prefix)) return null
    val payload = attachment.url.substring(prefix.length)
    return payload.length.toLong() * 3 / 4 -
        when {
            payload.endsWith("==") -> 2
            payload.endsWith("=") -> 1
            else -> 0
        }
}

private fun photoBytes(attachment: PromptAttachment): ByteArray? =
    try {
        Base64.decode(attachment.url.substringAfter(','))
    } catch (_: IllegalArgumentException) {
        null
    }

/**
 * Card node → kit node, with the words a node needs (chart and table descriptions, the progress
 * percentage).
 */
@Composable
private fun cardBody(nodes: List<CardNode>): ImmutableList<KitCardNode> =
    nodes
        .flatMap { node ->
            if (node is CardNode.Chart) return@flatMap chartNodes(node)
            listOf(
                when (node) {
                    is CardNode.Text -> KitCardNode.Text(node.text)
                    is CardNode.KeyValue ->
                        KitCardNode.KeyValue(node.rows.map { it.key to it.value }.toImmutableList())
                    is CardNode.Checklist ->
                        KitCardNode.Checklist(
                            node.items.map { it.text to it.done }.toImmutableList(),
                            node.style == CardNode.ListStyle.Check,
                        )
                    is CardNode.Table ->
                        KitCardNode.Table(
                            node.columns,
                            node.rows,
                            stringResource(
                                Res.string.agentCardTableLabel,
                                node.columns.size,
                                node.rows.size,
                            ),
                        )
                    is CardNode.Chart -> error("charts are mapped by chartNodes")
                    is CardNode.Code -> KitCardNode.Code(node.language, node.text)
                    is CardNode.DiffStat ->
                        KitCardNode.DiffStat(
                            node.files
                                .take(30)
                                .map { Triple(it.path, it.added, it.removed) }
                                .toImmutableList(),
                            (node.files.size - 30)
                                .takeIf { it > 0 }
                                ?.let {
                                    pluralStringResource(Res.plurals.agentCardDiffMore, it, it)
                                },
                        )
                    is CardNode.Progress ->
                        KitCardNode.Progress(
                            node.label,
                            node.value,
                            stringResource(
                                Res.string.agentCardProgressValue,
                                (node.value * 100).toInt(),
                            ),
                        )
                    is CardNode.Callout ->
                        KitCardNode.Callout(
                            when (node.tone) {
                                CardNode.CalloutTone.Success -> StatusTone.Ok
                                CardNode.CalloutTone.Warning -> StatusTone.Failure
                                CardNode.CalloutTone.Info -> StatusTone.Neutral
                            },
                            node.text,
                        )
                    is CardNode.Link ->
                        KitCardNode.Link(node.label, CardForms.host(node.url), node.url)
                }
            )
        }
        .toImmutableList()

/**
 * B-CHAT-004/054: every series is drawn (one bar group each, its name above it when there are
 * several) and the first group carries the whole chart's summary for screen readers: kind, series,
 * points and the highest value (Flutter `KitChart.summaryOf`). The kit receives the original chart
 * kind so line data stays a line.
 */
@Composable
private fun chartNodes(node: CardNode.Chart): List<KitCardNode> {
    val facts = CardCharts.facts(node)
    val kind =
        stringResource(
            if (node.kind == CardNode.ChartKind.Bar) Res.string.agentCardChartBar
            else Res.string.agentCardChartLine
        )
    val head =
        "$kind, ${pluralStringResource(Res.plurals.agentCardChartSeries, node.series.size, node.series.size)}, " +
            pluralStringResource(Res.plurals.agentCardChartPoints, facts.points, facts.points)
    val tail =
        if (facts.highest == null) stringResource(Res.string.agentCardChartNone)
        else
            stringResource(
                Res.string.agentCardChartHighest,
                facts.where,
                CardCharts.shown(facts.highest, node.unit),
            )
    val summary = "$head; $tail"
    val labels = node.labels.take(CardCharts.MAX_POINTS).toImmutableList()
    if (node.series.isEmpty())
        return listOf(
            KitCardNode.Chart(
                labels,
                kotlinx.collections.immutable.persistentListOf(),
                node.unit,
                summary,
                line = node.kind == CardNode.ChartKind.Line,
            )
        )
    val several = node.series.size > 1
    return node.series.flatMapIndexed { i, series ->
        listOfNotNull(
            if (several) KitCardNode.Text(series.name) else null,
            KitCardNode.Chart(
                labels,
                series.values.take(CardCharts.MAX_POINTS).toImmutableList(),
                node.unit,
                if (i == 0) summary else series.name,
                line = node.kind == CardNode.ChartKind.Line,
            ),
        )
    }
}

/**
 * The numbers of a chart's spoken summary (Flutter `KitChart.summaryOf`, `_pointsOf`, `_plain`).
 */
internal object CardCharts {
    const val MAX_POINTS: Int = 30

    data class Facts(val points: Int, val where: String, val highest: Double?)

    fun facts(node: CardNode.Chart): Facts {
        var n = node.labels.size
        node.series.forEach { n = maxOf(n, it.values.size) }
        var best: Double? = null
        var where = ""
        for (s in node.series) {
            for (i in 0 until minOf(s.values.size, MAX_POINTS)) {
                val v = s.values[i]
                if (!v.isFinite()) continue
                if (best == null || v > best) {
                    best = v
                    val label = node.labels.getOrNull(i) ?: "${i + 1}"
                    where = if (node.series.size > 1) "${s.name}, $label" else label
                }
            }
        }
        return Facts(minOf(n, MAX_POINTS), where, best)
    }

    /**
     * "42 ms", "12%", "3.5": whole numbers bare, else at most two decimals without trailing zeros.
     */
    fun shown(v: Double, unit: String?): String {
        val value = plain(v)
        return when {
            unit.isNullOrEmpty() -> value
            unit == "%" -> "$value%"
            else -> "$value $unit"
        }
    }

    fun plain(v: Double): String {
        if (kotlin.math.abs(v) >= 1e15) return v.toString()
        if (v == kotlin.math.round(v)) return kotlin.math.round(v).toLong().toString()
        val scaled = kotlin.math.round(v * 100) / 100
        var text = scaled.toString()
        if ('.' in text) text = text.trimEnd('0').trimEnd('.')
        return text
    }
}

/**
 * Form ask ↔ kit form, and the answer the values make (null = Send is held: a required field is
 * empty or invalid).
 */
internal object CardForms {
    fun fields(ask: CardAsk.Form): ImmutableList<KitFormField> =
        ask.fields
            .map { f ->
                KitFormField(
                    key = f.id,
                    label = f.label,
                    kind =
                        when (f.type) {
                            CardFieldType.Text -> KitFormFieldKind.Text
                            CardFieldType.Multiline -> KitFormFieldKind.Multiline
                            CardFieldType.Number -> KitFormFieldKind.Number
                            CardFieldType.Toggle -> KitFormFieldKind.Toggle
                            CardFieldType.Select -> KitFormFieldKind.Select
                            CardFieldType.Date -> KitFormFieldKind.Date
                        },
                    placeholder = f.placeholder,
                    options =
                        f.options
                            .map { KitFormOption(it.id, it.label, it.detail) }
                            .toImmutableList(),
                )
            }
            .toImmutableList()

    fun initial(ask: CardAsk.Form, draft: CardAnswer.Form?): Map<String, KitFormValue> =
        ask.fields
            .mapNotNull { f ->
                // A draft is what the person had: a field they cleared stays empty, never the
                // agent's default (B-CHAT-004).
                val v = if (draft != null) draft.values[f.id] else f.defaultValue
                val value =
                    when (f.type) {
                        CardFieldType.Toggle -> KitFormValue.Toggle(v as? Boolean ?: false)
                        CardFieldType.Select ->
                            KitFormValue.Text(
                                (v as? String)
                                    ?.takeIf { id -> f.options.any { it.id == id } }
                                    .orEmpty()
                            )
                        CardFieldType.Number -> KitFormValue.Text(v?.let(::numberText) ?: "")
                        else -> KitFormValue.Text(v as? String ?: "")
                    }
                f.id to value
            }
            .toMap()

    private fun numberText(v: Any): String =
        if (v is Double && v == kotlin.math.truncate(v) && kotlin.math.abs(v) < 1e15)
            v.toLong().toString()
        else v.toString()

    sealed interface Problem {
        data object Required : Problem

        data object Number : Problem

        data class Minimum(val value: Double) : Problem

        data class Maximum(val value: Double) : Problem

        data object Date : Problem
    }

    fun problem(field: CardField, value: KitFormValue?): Problem? {
        if (field.type == CardFieldType.Toggle) return null
        val raw = (value as? KitFormValue.Text)?.text.orEmpty()
        if (raw.isBlank()) return if (field.required) Problem.Required else null
        return when (field.type) {
            CardFieldType.Number -> {
                val number = number(raw) ?: return Problem.Number
                when {
                    field.min != null && number < field.min!! -> Problem.Minimum(field.min!!)
                    field.max != null && number > field.max!! -> Problem.Maximum(field.max!!)
                    else -> null
                }
            }
            CardFieldType.Select ->
                if (field.options.none { it.id == raw }) Problem.Required else null
            CardFieldType.Date -> if (validDate(raw)) null else Problem.Date
            else -> null
        }
    }

    private fun number(raw: String): Double? =
        raw.trim()
            .map { c ->
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                }
            }
            .joinToString("")
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() }

    private fun validDate(raw: String): Boolean {
        if (!raw.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
        val year = raw.substring(0, 4).toInt()
        val month = raw.substring(5, 7).toInt()
        val day = raw.substring(8, 10).toInt()
        if (year !in 1..9999 || month !in 1..12) return false
        val days =
            when (month) {
                2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                4,
                6,
                9,
                11 -> 30
                else -> 31
            }
        return day in 1..days
    }

    /** Values the ask accepts, or null. Optional empty fields are left out, never sent as null. */
    fun answer(ask: CardAsk.Form, values: Map<String, KitFormValue>): CardAnswer.Form? {
        val out = LinkedHashMap<String, Any>()
        for (field in ask.fields) {
            val value = values[field.id]
            if (problem(field, value) != null) return null
            when (field.type) {
                CardFieldType.Toggle -> out[field.id] = (value as? KitFormValue.Toggle)?.on ?: false
                CardFieldType.Number ->
                    number((value as? KitFormValue.Text)?.text.orEmpty())?.let { number ->
                        out[field.id] =
                            if (
                                number == kotlin.math.truncate(number) &&
                                    kotlin.math.abs(number) < 1e15
                            )
                                number.toLong()
                            else number
                    }
                else ->
                    (value as? KitFormValue.Text)
                        ?.text
                        ?.takeIf { it.isNotBlank() }
                        ?.let { out[field.id] = it }
            }
        }
        return CardAnswer.Form(out)
    }

    fun host(url: String): String =
        url.substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
}

@Composable
private fun formProblemWords(problem: CardForms.Problem?): String? =
    when (problem) {
        null -> null
        CardForms.Problem.Required -> stringResource(Res.string.agentCardFieldRequired)
        CardForms.Problem.Number -> stringResource(Res.string.agentCardFieldNumber)
        is CardForms.Problem.Minimum ->
            stringResource(Res.string.agentCardFieldMin, CardCharts.plain(problem.value))
        is CardForms.Problem.Maximum ->
            stringResource(Res.string.agentCardFieldMax, CardCharts.plain(problem.value))
        CardForms.Problem.Date -> stringResource(Res.string.kitDateInvalid)
    }

/** Initial empty map for previews. */
internal val NoFormValues = persistentMapOf<String, KitFormValue>()
