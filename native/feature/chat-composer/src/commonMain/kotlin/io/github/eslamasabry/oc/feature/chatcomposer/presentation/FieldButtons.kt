package io.github.eslamasabry.oc.feature.chatcomposer.presentation

import io.github.eslamasabry.oc.core.model.MidTurnPrompt

/**
 * The field button and the send mode from the conversation's facts (plan 09 §4.3, §10.2). Decided from capabilities
 * and the mid-turn facet, never from the backend kind (R19). Same order as `:engine:composer` `SendDecision`; once that
 * module compiles here, [how] delegates to it (S3-COMPUI report §6).
 */
object FieldButtons {
    fun button(hasText: Boolean, facts: ComposerFacts): FieldButton =
        when {
            facts.readOnly != null -> FieldButton.None
            !hasText -> if (facts.micAvailable) FieldButton.Mic else FieldButton.None
            facts.sending -> FieldButton.Sending
            else ->
                when (how(facts)) {
                    SendHow.Start -> FieldButton.Send
                    SendHow.Steer, SendHow.JoinTurn -> FieldButton.AddToTurn
                    SendHow.StopThenSend -> FieldButton.StopAndSend
                    SendHow.WhenOnline -> FieldButton.SendWhenOnline
                    null -> FieldButton.CannotSendYet
                }
        }

    /** How a send goes now; null = it cannot go (read-only, or a turn runs and nothing is known about mid-turn sends). */
    fun how(facts: ComposerFacts): SendHow? =
        when {
            facts.readOnly != null -> null
            facts.offline -> SendHow.WhenOnline
            !facts.busy -> SendHow.Start
            // Steer, never queue: steering wins wherever it exists.
            facts.steering -> SendHow.Steer
            facts.midTurn == MidTurnPrompt.JoinsTurn -> SendHow.JoinTurn
            facts.midTurn == MidTurnPrompt.RestartsTurn -> SendHow.StopThenSend
            else -> null
        }
}
