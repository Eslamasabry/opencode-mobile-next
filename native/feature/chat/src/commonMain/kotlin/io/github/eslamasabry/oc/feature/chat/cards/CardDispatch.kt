package io.github.eslamasabry.oc.feature.chat.cards

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardTail
import io.github.eslamasabry.oc.engine.cards.CardsProblem
import io.github.eslamasabry.oc.engine.cards.CardsVerdict
import io.github.eslamasabry.oc.engine.delivery.LocalSendId
import io.github.eslamasabry.oc.engine.delivery.PendingSend
import io.github.eslamasabry.oc.engine.delivery.SendOutcome
import io.github.eslamasabry.oc.engine.delivery.SendPhase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/*
 * The card slot's pure pieces (P12 review chat-cards #5, #6, #7): the transcript the board reads, the qualification
 * verdict and the wait for a card answer's real delivery result. ChatCards wires them to the bound connection.
 */

/**
 * The open conversation as the cards read it (B-CHAT-031/052). Live events update it in place; a history read
 * replaces it whole, in the server's order, but only when no live event arrived while the read was out (Flutter's
 * revision fence): an older snapshot never overwrites newer parts, and cached messages outside the returned tail are
 * never appended after newer ones.
 */
internal class CardTranscript {
    private val messages = LinkedHashMap<String, MessageWithParts>()
    private var revision = 0L

    var complete: Boolean = false
        private set

    /** Taken before a read starts; [applyRead] compares it. */
    fun mark(): Long = revision

    fun upsert(info: MessageInfo) {
        revision++
        val old = messages[info.id]
        messages[info.id] = MessageWithParts(info, old?.parts ?: persistentListOf())
    }

    /** False when the part's message is not known yet (a later read reconciles). */
    fun upsert(part: Part): Boolean {
        val old = messages[part.messageId] ?: return false
        revision++
        val parts = old.parts.toMutableList()
        val at = parts.indexOfFirst { it.id == part.id }
        if (at >= 0) parts[at] = part else parts += part
        messages[part.messageId] = old.copy(parts = parts.toImmutableList())
        return true
    }

    /** Applies a finished read taken at [mark]; false = a live event arrived meanwhile and the read is dropped. */
    fun applyRead(mark: Long, tail: CardTail): Boolean {
        if (mark != revision) return false
        messages.clear()
        tail.messages.forEach { messages[it.info.id] = it }
        complete = tail.complete
        revision++
        return true
    }

    fun list(): List<MessageWithParts> = messages.values.toList()

    fun clear() {
        messages.clear()
        complete = false
        revision++
    }
}

/**
 * Qualification (B-CHAT-034/035, plan 09 §13.1): the `oc-ui` MCP self-check is necessary but not sufficient. OpenCode 2
 * stays unqualified until its card qualification is proven on the device (Flutter's OpenCode 2 adapter has
 * `cardsQualified = false`), so a connected helper alone never enables answer controls.
 */
internal object CardsQualification {
    const val HELPER: String = "oc-ui"

    /** Flutter `AgentToolAdapter.cardsQualified` for OpenCode 2. */
    const val OPEN_CODE_2_QUALIFIED: Boolean = false

    fun verdict(
        servers: Outcome<List<McpServerInfo>>,
        here: List<CardAgent>,
        qualified: Boolean = OPEN_CODE_2_QUALIFIED,
    ): CardsVerdict {
        val unqualified = CardsVerdict(emptyList(), CardsProblem.NotQualified, here)
        if (!qualified) return unqualified
        val ok = servers as? Outcome.Ok ?: return unqualified
        return if (ok.value.any { it.name == HELPER && it.status.equals("connected", ignoreCase = true) }) CardsVerdict(here)
        else unqualified
    }
}

/**
 * B-CHAT-050 (plan 09 §13.5): the delivery pipeline answers a send with a local id before the request leaves, so the
 * card waits for that send's own result. Accepted → Ok (the card stays "not confirmed" until the echo). Put back →
 * the definite failure (nothing left; the card may be answered again). Unconfirmed, queued or a result the card did
 * not see → "may have been sent" (Refused DispatchUncertain): never resent.
 */
internal suspend fun awaitCardSend(
    id: LocalSendId,
    outcomes: Flow<List<SendOutcome>>,
    pending: Flow<List<PendingSend>>,
): Outcome<Unit> {
    val uncertain = Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
    return combine(outcomes, pending) { o, p ->
        when (val mine = o.firstOrNull { it.localIdOf() == id.raw }) {
            is SendOutcome.Accepted -> Outcome.Ok(Unit)
            is SendOutcome.PutBack -> Outcome.Failed(mine.reason)
            is SendOutcome.Unconfirmed, is SendOutcome.Queued -> uncertain
            null -> {
                val bubble = p.firstOrNull { it.localId == id }
                when {
                    bubble == null -> uncertain // gone without a result this card saw
                    bubble.phase == SendPhase.Accepted -> Outcome.Ok(Unit)
                    else -> null // still on its way
                }
            }
        }
    }.first { it != null }!!
}

private fun SendOutcome.localIdOf(): String? =
    when (this) {
        is SendOutcome.Accepted -> localId.raw
        is SendOutcome.PutBack -> localId?.raw
        is SendOutcome.Unconfirmed -> localId?.raw
        is SendOutcome.Queued -> entryId
    }
