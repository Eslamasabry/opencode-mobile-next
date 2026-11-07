package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId

/**
 * P-103: several waiting requests took 61 % of the screen and left the conversation 88 dp. Every request still has its
 * own card (E06-S01, nothing hides behind "N more waiting"), but only the first waiting one is drawn in full; the
 * others are one-line rows naming what they ask, and a tap opens one in full. Flutter shows only the first request
 * (`chat_requests.dart`); this keeps the rest one tap away.
 */
public object RequestStackRules {
    /**
     * The cards drawn as one-line rows: every waiting card after the first waiting one, except the one a link opened
     * the chat for ([focused]) and the ones the person opened. A card answered or no longer needed is already one
     * line and is drawn as it is.
     */
    public fun folded(cards: List<RequestCardUi>, opened: Set<RequestId>, focused: RequestId? = null): Set<RequestId> {
        val waiting = cards.filter { it.state == CardState.Waiting }
        if (waiting.size < 2) return emptySet()
        val first = if (focused != null && waiting.any { it.id == focused }) focused else waiting.first().id
        return waiting.asSequence().map { it.id }.filter { it != first && it !in opened }.toSet()
    }

    /** What the person opened, without the cards that have left (an id never opens a later card by accident). */
    public fun stillOpened(opened: Set<RequestId>, cards: List<RequestCardUi>): Set<RequestId> {
        if (opened.isEmpty()) return opened
        val here = cards.mapTo(HashSet()) { it.id }
        return if (opened.all { it in here }) opened else opened.filterTo(HashSet()) { it in here }
    }
}
