package io.github.eslamasabry.oc.engine.cards

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role

/**
 * Which card payloads may authorize a new request, such as a connector card's "add this connector" (Flutter
 * `GenUiStateController.observedCard` / `observedState` / `state`; B-AGENT-131).
 *
 * The retained payload stays readable while the gateway is suspended ([live] false): [observedCard] still answers, but
 * [state] is [CardState.Unknown], so no new request is authorized. A payload whose revision changed, or whose
 * conversation was removed, is a different card: it never inherits the old card's consent. An incomplete read
 * (`tailComplete` false) that has not seen the card yet is "not known yet" (null), which differs from a complete
 * read where the card is gone ([CardState.Unknown]).
 */
public class CardAuthority(private val live: () -> Boolean) {
    private class Seen(val messages: List<MessageWithParts>, val tailComplete: Boolean)

    private val sessions = HashMap<Pair<CardScope, String>, Seen>()
    private val removed = HashSet<Pair<CardScope, String>>()

    public fun observe(scope: CardScope, sessionId: String, transcript: List<MessageWithParts>, tailComplete: Boolean) {
        val key = scope to sessionId
        removed -= key
        sessions[key] = Seen(transcript.toList(), tailComplete)
    }

    /** A deleted conversation: none of its cards may authorize anything again. */
    public fun removeSession(scope: CardScope, sessionId: String) {
        val key = scope to sessionId
        sessions -= key
        removed += key
    }

    /** The observed payload of exactly this card (same identity and revision), or null. */
    public fun observedCard(card: AgentCard): AgentCard? {
        val seen = sessions[card.scope to card.sessionId] ?: return null
        return find(seen, card)?.takeIf { it.revision == card.revision }
    }

    /** The card's state from what was observed, ignoring suspension; null while an incomplete read has not shown it. */
    public fun observedState(card: AgentCard): CardState? {
        val key = card.scope to card.sessionId
        if (key in removed) return CardState.Unknown
        val seen = sessions[key] ?: return null
        val found = find(seen, card)
        if (found == null) return if (seen.tailComplete) CardState.Unknown else null
        if (found.revision != card.revision) return CardState.Unknown
        return CardCodec.stateFor(card, seen.messages, seen.tailComplete)
    }

    /** The state that may authorize a new request: [CardState.Unknown] while suspended or not known. */
    public fun state(card: AgentCard): CardState = if (!live()) CardState.Unknown else observedState(card) ?: CardState.Unknown

    private fun find(seen: Seen, card: AgentCard): AgentCard? {
        for (message in seen.messages) {
            if (message.info.role != Role.Assistant) continue
            for (part in message.parts) {
                val parsed = CardCodec.fromPart(part, card.scope, card.sessionId, message.info.id) as? CardParse.Parsed ?: continue
                if (parsed.card.identity == card.identity) return parsed.card
            }
        }
        return null
    }
}
