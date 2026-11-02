package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sends a card's two-line answer as an ordinary prompt (plan 09 §13.5): never steered, never queued. The chat feature
 * implements it over its delivery pipeline. Failure.Refused(DispatchUncertain) means "may have been sent".
 */
public fun interface CardAnswerSender {
    public suspend fun send(sessionId: String, text: String, photos: List<PromptAttachment>): Outcome<Unit>
}

/** Why an answer was refused before anything was sent (plain words in the UI; never server text). */
public enum class CardRefusal { Changed, Busy, CheckFirst, Invalid, PhotosRefused, PhotoTooLarge }

/** One card call of the conversation, ready to draw (plan 09 §13.7 `CardUi`). */
public data class CardEntry(
    val callId: String,
    val messageId: String,
    /** The turn the card belongs to: the id of the user prompt before it ("head" before any). */
    val turnId: String,
    val parse: CardParse,
    val state: CardState,
    val delivery: CardDelivery,
    /** The answer's words: the receipt's summary once answered, or what is about to be sent while held. */
    val summary: String?,
    /** What the person entered before Undo, so the controls come back as they were (B-CHAT-004). */
    val draft: CardAnswer?,
    val refusal: CardRefusal? = null,
) {
    val card: AgentCard? get() = (parse as? CardParse.Parsed)?.card
}

public data class CardBoardState(
    val revision: Long = 0,
    /** Oldest first, in transcript order. */
    val cards: ImmutableList<CardEntry> = persistentListOf(),
    /** The conversation is running: card controls wait for it to be idle. */
    val busy: Boolean = false,
    /** Cards are on and qualified here: without this every card is read-only (Unknown). */
    val available: Boolean = false,
) {
    /** B-CHAT-037: the waiting cards the conversations list shows under the row. */
    val waiting: ImmutableList<AgentCard>
        get() = cards.filter { it.state == CardState.Waiting }.mapNotNull { it.card }.toImmutableList()
}

/**
 * The cards of one open conversation (Flutter `GenUiStateController`, trimmed to one session). It reads only the
 * authoritative transcript it is given ([observe]); the held answer waits [holdMillis] with Undo, re-checks the card,
 * then sends exactly once through [sender]. Only the authoritative echo makes a receipt: an acknowledgement alone
 * leaves the card "not confirmed" (DeliveryUnknown), which blocks a resend until the conversation shows the answer.
 */
public class CardBoard(
    private val scope: CoroutineScope,
    private val sender: CardAnswerSender,
    private val photosAllowed: () -> Boolean = { false },
    private val holdMillis: Long = HOLD_MILLIS,
) {
    private val mutable = MutableStateFlow(CardBoardState())
    public val state: StateFlow<CardBoardState> = mutable.asStateFlow()

    private var cardScope: CardScope? = null
    private var sessionId: String? = null
    private var messages: List<MessageWithParts> = emptyList()
    private var tailComplete = false
    private val delivery = HashMap<String, CardDelivery>()
    private val held = HashMap<String, Job>()
    private val heldWords = HashMap<String, String>()
    private val drafts = HashMap<String, CardAnswer>()
    private val refusals = HashMap<String, CardRefusal>()
    private val sending = HashSet<String>()
    private var closed = false

    /** The authoritative transcript (oldest first, no optimistic rows). [tailComplete] = the newest page is loaded. */
    public fun observe(scope: CardScope, sessionId: String, transcript: List<MessageWithParts>, tailComplete: Boolean) {
        if (closed) return
        if (this.cardScope != null && (this.cardScope != scope || this.sessionId != sessionId)) reset()
        this.cardScope = scope
        this.sessionId = sessionId
        messages = transcript
        this.tailComplete = tailComplete
        publish()
    }

    public fun setBusy(busy: Boolean) {
        if (mutable.value.busy != busy) publish(busy = busy)
    }

    /** Disabling blocks card actions at once (B-CHAT-036): a held answer is dropped, nothing new starts. */
    public fun setAvailable(available: Boolean) {
        if (!available) held.keys.toList().forEach(::undoIdentity)
        if (mutable.value.available != available) publish(available = available)
    }

    /**
     * Starts the 3 s hold (B-CHAT-004). Refuses at once what could never be sent: a card that is not waiting, an
     * answer its ask refuses, photos where they cannot go, or a card whose answer may already have arrived.
     */
    public fun answer(card: AgentCard, answer: CardAnswer, photos: List<PromptAttachment> = emptyList()): Outcome<Unit> {
        val identity = card.identity
        if (identity in held || identity in sending) return Outcome.Ok(Unit) // a duplicate tap sends once
        val refusal = admit(card, answer, photos)
        if (refusal != null) {
            refusals[identity] = refusal
            publish()
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        }
        refusals.remove(identity)
        heldWords[identity] = CardCodec.heldWords(card, answer) ?: ""
        drafts[identity] = answer
        delivery[identity] = CardDelivery.Held
        val copy = photos.toList()
        held[identity] =
            scope.launch {
                delay(holdMillis)
                if (held.remove(identity) == null) return@launch
                send(card, answer, copy)
            }
        publish()
        return Outcome.Ok(Unit)
    }

    /** Undo gives back exactly what the person had: the draft stays for the controls (B-CHAT-004). */
    public fun undo(card: AgentCard) {
        undoIdentity(card.identity)
    }

    private fun undoIdentity(identity: String) {
        val job = held.remove(identity) ?: return
        job.cancel()
        delivery.remove(identity)
        heldWords.remove(identity)
        publish()
    }

    /** Leaving the chat or closing the profile drops held answers; nothing is sent late. */
    public fun close() {
        held.keys.toList().forEach(::undoIdentity)
        closed = true
    }

    private fun reset() {
        held.keys.toList().forEach(::undoIdentity)
        delivery.clear()
        drafts.clear()
        refusals.clear()
        heldWords.clear()
    }

    private fun admit(card: AgentCard, answer: CardAnswer, photos: List<PromptAttachment>): CardRefusal? {
        val s = mutable.value
        if (!s.available || stateOf(card) != CardState.Waiting) return CardRefusal.Changed
        if (s.busy) return CardRefusal.Busy
        if (delivery[card.identity] == CardDelivery.DeliveryUnknown) return CardRefusal.CheckFirst
        try {
            CardCodec.answerText(card, answer)
        } catch (e: CardAnswerInvalid) {
            return CardRefusal.Invalid
        }
        return photoRefusal(answer, photos)
    }

    /** B-CHAT-053: photos ride only a photo answer, match its count, and stay within the size limits. */
    private fun photoRefusal(answer: CardAnswer, photos: List<PromptAttachment>): CardRefusal? {
        if (answer !is CardAnswer.Photo) return if (photos.isNotEmpty()) CardRefusal.PhotosRefused else null
        if (!photosAllowed() || photos.size != answer.count) return CardRefusal.PhotosRefused
        var total = 0L
        for (p in photos) {
            if (p.mime !in PHOTO_TYPES) return CardRefusal.PhotoTooLarge
            val bytes = dataUrlBytes(p.url, p.mime) ?: return CardRefusal.PhotoTooLarge
            if (bytes == 0L || bytes > PHOTO_BYTES) return CardRefusal.PhotoTooLarge
            total += bytes
        }
        return if (total > PHOTOS_TOTAL) CardRefusal.PhotoTooLarge else null
    }

    private suspend fun send(card: AgentCard, answer: CardAnswer, photos: List<PromptAttachment>) {
        val identity = card.identity
        heldWords.remove(identity)
        // Re-check after the hold: the card may have changed, the agent may be running, cards may be off.
        val again = admit(card, answer, photos)
        if (again != null) {
            delivery.remove(identity)
            refusals[identity] = again
            publish()
            return
        }
        val text = CardCodec.answerText(card, answer)
        sending += identity
        delivery[identity] = CardDelivery.Sending
        publish()
        val result = sender.send(card.sessionId, text, photos)
        sending -= identity
        if (closed) return
        delivery[identity] =
            when (result) {
                // Only the authoritative echo settles the card; until then the answer is "not confirmed".
                is Outcome.Ok -> CardDelivery.DeliveryUnknown
                is Outcome.Failed ->
                    if ((result.failure as? Failure.Refused)?.reason == RefusalReason.DispatchUncertain) CardDelivery.DeliveryUnknown
                    else CardDelivery.Failed
            }
        publish()
    }

    private fun stateOf(card: AgentCard): CardState =
        if (!mutable.value.available) CardState.Unknown else CardCodec.stateFor(card, messages, tailComplete)

    private fun publish(busy: Boolean = mutable.value.busy, available: Boolean = mutable.value.available) {
        val scope = cardScope
        val sid = sessionId
        val entries = ArrayList<CardEntry>()
        if (scope != null && sid != null) {
            var turn = "head"
            for (m in messages) {
                if (m.info.role == Role.User && m.parts.any { it.type == PartType.Text }) turn = m.info.id
                if (m.info.role != Role.Assistant) continue
                for (p in m.parts) {
                    val parse = CardCodec.fromPart(p, scope, sid, m.info.id) ?: continue
                    val card = (parse as? CardParse.Parsed)?.card
                    val identity = card?.identity
                    val st =
                        when {
                            card == null -> CardState.Unknown
                            !available -> CardState.Unknown
                            else -> CardCodec.stateFor(card, messages, tailComplete)
                        }
                    var d = identity?.let { delivery[it] } ?: CardDelivery.Idle
                    if (identity != null && (st == CardState.Answered || st == CardState.PassedOver) && d != CardDelivery.Held) {
                        // Settled: the receipt is the truth now.
                        delivery.remove(identity)
                        drafts.remove(identity)
                        refusals.remove(identity)
                        d = CardDelivery.Idle
                    }
                    val summary =
                        when {
                            card == null -> null
                            d == CardDelivery.Held -> heldWords[identity]
                            st == CardState.Answered -> answerSummary(card)
                            else -> null
                        }
                    entries +=
                        CardEntry(
                            callId = p.callId.orEmpty(),
                            messageId = m.info.id,
                            turnId = turn,
                            parse = parse,
                            state = st,
                            delivery = d,
                            summary = summary,
                            draft = identity?.let { drafts[it] },
                            refusal = identity?.let { refusals[it] },
                        )
                }
            }
        }
        val old = mutable.value
        mutable.value = CardBoardState(old.revision + 1, entries.toImmutableList(), busy, available)
    }

    private fun answerSummary(card: AgentCard): String? {
        var after = false
        for (m in messages) {
            if (m.info.id == card.messageId) {
                after = true
                continue
            }
            if (!after) continue
            CardCodec.answerIn(m, card)?.let { return it.first }
        }
        return null
    }

    public companion object {
        public const val HOLD_MILLIS: Long = 3_000
        private val PHOTO_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")
        private const val PHOTO_BYTES = 10L * 1024 * 1024
        private const val PHOTOS_TOTAL = 20L * 1024 * 1024

        /** Decoded size of a base64 `data:` URL of [mime]; null when it is not one. */
        internal fun dataUrlBytes(url: String, mime: String): Long? {
            val prefix = "data:$mime;base64,"
            if (!url.startsWith(prefix) || url.length > 14 * 1024 * 1024) return null
            val body = url.length - prefix.length
            if (body % 4 != 0) return null
            val pad = url.takeLast(2).count { it == '=' }
            return body / 4L * 3 - pad
        }
    }
}
