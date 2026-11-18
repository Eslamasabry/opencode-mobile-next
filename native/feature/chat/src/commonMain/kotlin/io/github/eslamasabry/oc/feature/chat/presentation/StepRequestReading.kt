package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.feature.chat.requests.presentation.AnswerWords
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi

/**
 * What the request cards tell the steps of the transcript, from the moment the person answers: the step that asked
 * stops saying "waiting for you" at once (Allow) or reads declined at once (Deny), without waiting for the server to
 * finish the tool call (it took about 15 s). Undo inside the hold puts the card back to Waiting, so the reading goes
 * back with it; when the server's own result arrives it says the same and the reading is gone.
 *
 * [waiting]: a card still needs the person (a permission card they answered no longer does); [declined]: tool call ids
 * of permission cards answered Deny.
 */
public data class StepRequestReading(val waiting: Boolean = false, val declined: Set<String> = emptySet()) {
    public companion object {
        public fun of(cards: List<RequestCardUi>): StepRequestReading {
            val declined = HashSet<String>()
            var waiting = false
            for (card in cards) {
                val answered = (card as? RequestCardUi.Permission)?.let { answeredWords(it.state) }
                if (answered == null) {
                    if (RunTailRules.waitsForYou(card.state)) waiting = true
                    continue
                }
                val call = (card as RequestCardUi.Permission).captured?.callId
                if (call != null && (answered is AnswerWords.Rejected || answered is AnswerWords.RejectedWith)) {
                    declined += call
                }
            }
            return StepRequestReading(waiting, declined)
        }

        /** The words of an answer that is held, sending or sent; null while the card waits or the answer failed. */
        private fun answeredWords(state: CardState): AnswerWords? =
            when (state) {
                is CardState.Held -> state.words
                is CardState.Sending -> state.words
                is CardState.Sent -> state.words
                else -> null
            }
    }
}
