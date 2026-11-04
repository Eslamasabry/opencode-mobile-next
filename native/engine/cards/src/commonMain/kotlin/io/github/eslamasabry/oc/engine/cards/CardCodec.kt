package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.toImmutableList

/** Exact tool names; never a suffix, substring or title match (plan 09 §13.3). */
public val CARD_TOOL_NAMES: Set<String> = setOf("mcp__oc-ui__show", "oc-ui_show")

/** A summary or answer that cannot be encoded: the card refuses the answer, plainly. */
public class CardAnswerInvalid : Exception("This card answer is invalid.")

/**
 * The card contract (Flutter `gen_ui_codec.dart`): admission from a tool part, state from the authoritative
 * transcript, the two-line answer receipt. Pure functions; the same input always gives the same result.
 */
public object CardCodec {
    /**
     * B-CHAT-030: only an executed, completed, identified, non-synthetic `show` call is a card; pending and running
     * calls stay ordinary tool rows, and missing input (after event loss) is reconciled, not invalid.
     */
    public fun fromPart(part: Part, scope: CardScope, sessionId: String, messageId: String): CardParse? {
        val tool = part.tool
        if (
            part.type != PartType.Tool ||
                part.synthetic ||
                part.toolName !in CARD_TOOL_NAMES ||
                tool == null ||
                tool.status != ToolStatus.Completed ||
                !tool.ran ||
                tool.input.isEmpty() ||
                part.callId.isNullOrEmpty() ||
                messageId.isEmpty() ||
                sessionId.isEmpty() ||
                scope.profileId.isEmpty() ||
                scope.sourceId.isEmpty() ||
                scope.directory.isEmpty() ||
                (part.messageId.isNotEmpty() && part.messageId != messageId)
        ) {
            return null
        }
        return fromInput(CardJson.validatorTree(tool.input), scope, sessionId, messageId, part.callId!!)
    }

    /** Validation of an already-decoded input tree (the corpus and the cycle tests reach the validator here). */
    public fun fromInput(input: Any?, scope: CardScope, sessionId: String, messageId: String, callId: String): CardParse =
        try {
            val normalized = CardValidator.normalize(input)
            CardParse.Parsed(toCard(normalized, scope, sessionId, messageId, callId))
        } catch (e: CardValidator.Invalid) {
            CardParse.Unreadable(e.reason)
        } catch (e: IllegalArgumentException) {
            CardParse.Unreadable(CardProblem.BadValue)
        } catch (e: ClassCastException) {
            CardParse.Unreadable(CardProblem.BadValue)
        } catch (e: IndexOutOfBoundsException) {
            CardParse.Unreadable(CardProblem.BadValue)
        }

    /** The canonical normalised map of an input, or the reason it is refused (the corpus compares this). */
    public fun normalized(input: Any?): Result<Map<String, Any?>> =
        try {
            Result.success(CardValidator.normalize(input))
        } catch (e: CardValidator.Invalid) {
            Result.failure(e)
        }

    public fun problemOf(error: Throwable): CardProblem = (error as? CardValidator.Invalid)?.reason ?: CardProblem.BadValue

    /** sha256 of the compact Dart JSON of the normalised card. */
    public fun revision(normalized: Map<String, Any?>): String = DartJson.sha256Hex(CardJson.encode(normalized).encodeToByteArray())

    private fun toCard(n: Map<String, Any?>, scope: CardScope, sessionId: String, messageId: String, callId: String): AgentCard =
        AgentCard(
            scope = scope,
            id = n["id"] as String,
            title = n["title"] as String,
            sessionId = sessionId,
            callId = callId,
            messageId = messageId,
            revision = revision(n),
            body = (n["body"] as List<*>).map { CardMapping.node(it as Map<*, *>) }.toImmutableList(),
            ask = (n["ask"] as Map<*, *>?)?.let(CardMapping::ask),
            connector = (n["connector"] as Map<*, *>?)?.let { ConnectorSuggestion(it["catalogId"] as String, it["reason"] as String) },
        )

    // ------------------------------------------------------------------ state

    /**
     * B-CHAT-031/052: [transcript] is the scoped, chronological authoritative projection (no optimistic rows, no
     * staged-revert suffix). Duplicate ids, another session, a repeated identity or a changed revision → Unknown; an
     * incomplete tail → Unknown; reports never supersede asks; the first later real user message settles a card.
     */
    public fun stateFor(card: AgentCard, transcript: List<MessageWithParts>, tailComplete: Boolean): CardState {
        var found = false
        var superseded = false
        val seen = HashSet<String>()
        for (message in transcript) {
            if (message.info.sessionId != card.sessionId || message.info.id.isEmpty() || !seen.add(message.info.id)) {
                return CardState.Unknown
            }
            if (found && authoritativeUser(message)) {
                if (!superseded && answerIn(message, card) != null) return CardState.Answered
                if (!tailComplete) return CardState.Unknown
                return if (card.ask == null) CardState.Report else CardState.PassedOver
            }
            if (message.info.role != Role.Assistant) continue
            for (part in message.parts) {
                val parsed = fromPart(part, card.scope, card.sessionId, message.info.id) as? CardParse.Parsed ?: continue
                val candidate = parsed.card
                if (candidate.identity == card.identity) {
                    if (found || candidate.revision != card.revision) return CardState.Unknown
                    found = true
                } else if (found && candidate.ask != null) {
                    superseded = true
                }
            }
        }
        if (!found || !tailComplete) return CardState.Unknown
        if (card.ask == null) return CardState.Report
        return if (superseded) CardState.PassedOver else CardState.Waiting
    }

    internal fun authoritativeUser(message: MessageWithParts): Boolean =
        message.info.role == Role.User &&
            message.info.id.isNotEmpty() &&
            message.info.sessionId.isNotEmpty() &&
            message.parts.isNotEmpty() &&
            message.parts.any { !it.synthetic && (it.type == PartType.Text || it.type == PartType.File) } &&
            message.parts.none { it.synthetic }

    // ---------------------------------------------------------------- receipts

    private val RECEIPT = Regex("\\[oc-ui answer ([a-z0-9-]{1,48})\\] (.+)")

    private class Receipt(val summary: String, val envelope: Map<String, Any?>)

    private fun receipt(message: MessageWithParts): Receipt? {
        if (!authoritativeUser(message)) return null
        val text = message.parts.filter { it.type == PartType.Text }.joinToString("\n") { it.text }
        if (text.length > 34000) return null
        return try {
            val lines = text.split('\n')
            if (lines.size != 2) return null
            val match = RECEIPT.matchEntire(lines[0]) ?: return null
            val summary = match.groupValues[2]
            if (CardValidator.scalars(summary) > 240 || '\t' in summary || CardValidator.text(summary) != summary) return null
            if (CardValidator.utf8Length(lines[1]) > 32768) return null
            val decoded = CardJson.parseForValidator(lines[1])
            CardValidator.bounded(decoded)
            val keys = listOf("v", "cardId", "callId", "value")
            val envelope = CardValidator.obj(decoded, keys, keys)
            val v = envelope["v"]
            if (!(v == 1L || v == 1.0) ||
                envelope["cardId"] != match.groupValues[1] ||
                (envelope["callId"] as? String).isNullOrEmpty()
            ) {
                return null
            }
            CardAnswers.structural(envelope["value"])
            Receipt(summary, envelope)
        } catch (e: CardValidator.Invalid) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** The validated answer a user message carries for [card], or null. */
    public fun answerIn(message: MessageWithParts, card: AgentCard): Pair<String, Map<String, Any?>>? {
        val ask = card.ask ?: return null
        if (message.info.sessionId != card.sessionId || message.info.id == card.messageId) return null
        val r = receipt(message) ?: return null
        if (r.envelope["cardId"] != card.id || r.envelope["callId"] != card.callId) return null
        return try {
            r.summary to CardAnswers.validated(ask, r.envelope["value"])
        } catch (e: CardValidator.Invalid) {
            null
        }
    }

    /**
     * Structural display recognition only (the chat hides the JSON line of a receipt): settlement still requires the
     * card, the call and the validated answer.
     */
    public fun answerEnvelope(message: MessageWithParts): Pair<String, String>? =
        receipt(message)?.let { (it.envelope["cardId"] as String) to it.summary }

    /**
     * The two-line answer (plan 09 §13.4): `[oc-ui answer <id>] <summary>` then the compact envelope. Throws
     * [CardAnswerInvalid] for an answer the original ask refuses (B-CHAT-032).
     */
    public fun answerText(card: AgentCard, answer: CardAnswer): String {
        try {
            val ask = card.ask
            if (ask == null ||
                card.callId.isEmpty() || card.messageId.isEmpty() || card.sessionId.isEmpty() || card.revision.isEmpty() ||
                card.scope.profileId.isEmpty() || card.scope.sourceId.isEmpty() || card.scope.directory.isEmpty()
            ) {
                CardValidator.fail()
            }
            CardValidator.id(card.id, CardValidator.CARD_ID)
            val raw: Map<String, Any?> =
                when (answer) {
                    is CardAnswer.Choice -> mapOf("choice" to answer.ids)
                    is CardAnswer.Form -> mapOf("form" to answer.values)
                    is CardAnswer.Confirm -> mapOf("confirm" to answer.value)
                    is CardAnswer.Photo -> mapOf("photo" to answer.count.toLong())
                }
            CardValidator.bounded(raw)
            val value = CardAnswers.validated(ask, raw)
            val summary = CardAnswers.summary(ask, value)
            val envelope = linkedMapOf("v" to 1L, "cardId" to card.id, "callId" to card.callId, "value" to value)
            CardValidator.bounded(envelope)
            return "[oc-ui answer ${card.id}] $summary\n${CardJson.encode(envelope)}"
        } catch (e: CardValidator.Invalid) {
            throw CardAnswerInvalid()
        } catch (e: IllegalArgumentException) {
            throw CardAnswerInvalid()
        }
    }

    /** The first line's words without the tag ("SQLite", "Run it"): what a held answer says while Undo runs. */
    public fun heldWords(card: AgentCard, answer: CardAnswer): String? =
        try {
            answerText(card, answer).substringBefore('\n').substringAfter("] ")
        } catch (e: CardAnswerInvalid) {
            null
        }
}
