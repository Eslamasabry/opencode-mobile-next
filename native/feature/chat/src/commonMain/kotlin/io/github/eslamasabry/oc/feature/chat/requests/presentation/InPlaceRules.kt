package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.feature.chat.presentation.StepEntry
import io.github.eslamasabry.oc.feature.chat.presentation.StepListRules

/**
 * D1 "steps only" (owner 2026-11-14): which permission asks are answered in place under their step. An ask is drawn
 * there when the tool call it is for is a step of the turn in view ([work], the newest turn's); every other request
 * (a question, an ask from a sub-agent's or another conversation, an ask whose call the transcript does not hold yet)
 * stays in the request slot above the message box.
 */
public object InPlaceRules {
    /** The cards drawn in place, by the call each one is for, in the slot's order. */
    public fun placed(cards: List<RequestCardUi>, work: TurnWork?): Map<String, RequestCardUi> {
        if (work == null || cards.isEmpty()) return emptyMap()
        val calls = work.steps.mapNotNullTo(HashSet()) { it.callId }
        if (calls.isEmpty()) return emptyMap()
        // Sub-agents started together are ONE group row, which draws no ask under its children (and is folded): their
        // asks stay in the slot above the message box, where the box's "above" is true.
        val grouped =
            StepListRules.entries(work, emptyList())
                .filterIsInstance<StepEntry.Group>()
                .flatMapTo(HashSet()) { group -> group.steps.mapNotNull { it.callId } }
        val out = LinkedHashMap<String, RequestCardUi>()
        for (card in cards) {
            val call = (card as? RequestCardUi.Permission)?.captured?.callId ?: continue
            if (call in calls && call !in grouped && call !in out) out[call] = card
        }
        return out
    }

    /** Whether one of the [placed] asks still waits for the person (the message box then says to answer above). */
    public fun waiting(placed: Map<String, RequestCardUi>): Boolean = placed.values.any { it.state == CardState.Waiting }
}
