package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.turnfacts.AgentRunFailureKind
import io.github.eslamasabry.oc.engine.transcript.turnfacts.classifyAgentRunFailure

/**
 * What a failed reply tells the person (QA wave 1 B5, B-AGENT-024): a plain reason with the next step. The server's
 * technical text is never the line itself; it stays under Details. The kinds are Flutter's `AgentRunFailureKind`,
 * read by the app's one classifier ([classifyAgentRunFailure], which reuses the B-CHAT-016 server causes).
 */
public enum class ReplyFailureKind {
    /** The provider refused the key or sign-in: sign in to that provider again, or pick another model. */
    SignIn,

    /** The account has no credit for this model: waiting cannot fix it; add credit, or pick another model. */
    NoCredit,

    /** The model is unknown or not offered any more: pick another model. */
    Model,

    /** Rate limit or an overloaded provider: wait, or pick another model. */
    Busy,

    /** The connection to the model provider dropped: the reply stopped where it was; continue picks up. */
    ConnectionLost,

    /** Anything else: send again, or pick another model. */
    Other,
    ;

    public companion object {
        /** Reads the server's error text; never shows it. */
        public fun of(reason: String?): ReplyFailureKind =
            when (classifyAgentRunFailure(reason)) {
                AgentRunFailureKind.SignIn -> SignIn
                AgentRunFailureKind.NoCredit -> NoCredit
                AgentRunFailureKind.ModelUnavailable -> Model
                AgentRunFailureKind.Busy -> Busy
                AgentRunFailureKind.ConnectionLost -> ConnectionLost
                AgentRunFailureKind.Unknown -> Other
            }
    }
}
