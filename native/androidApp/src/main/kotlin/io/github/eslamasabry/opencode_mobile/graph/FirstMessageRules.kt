package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import io.github.eslamasabry.oc.engine.delivery.TypedKind
import io.github.eslamasabry.oc.engine.delivery.port.nothingSent

/**
 * The new-conversation screen's reading of a first message that [io.github.eslamasabry.oc.engine.delivery.PromptDelivery.send]
 * refused at once (no bubble, no receipt to wait for).
 */
internal object FirstMessageRules {
    /**
     * Issue 147: the phone's helper refused the first message's create for the agent's own sign-in (expired, refused,
     * missing): `Failure.Rejected` tagged `Paseo AgentSignIn`. The agent then reads Sign in needed everywhere.
     */
    /**
     * Issue 147: whether a first-message record whose words hashed to [recorded] is another message than the one now
     * sent ([now]): then its unknown fate is not this message's. A record with no digest proves nothing (kept).
     */
    fun otherMessage(recorded: String?, now: String): Boolean = recorded != null && recorded != now

    fun signInLapsed(failure: io.github.eslamasabry.oc.core.common.Failure?): Boolean =
        (failure as? io.github.eslamasabry.oc.core.common.Failure.Rejected)?.tag ==
            io.github.eslamasabry.oc.protocol.paseo.PaseoAgentSignInTag

    /**
     * True when the words provably never left the phone, so they go back in the box with "not sent" and Try again.
     * The engine dispatches a prompt only after `send` returned Ok, so every synchronous refusal of a prompt wrote
     * nothing: offline with no queue (Paseo has none: `Network.Offline`, which read as "We couldn't confirm your
     * message arrived" + an empty Review page), a blocked route, a refused hold. Two stay unknown, because a
     * duplicate is the worse error: an explicit DispatchUncertain, and a typed command (`/x`, `!x`), whose failure
     * may come after it ran.
     */
    fun notSent(failure: Failure, request: SendRequest): Boolean {
        if ((failure as? Failure.Refused)?.reason == RefusalReason.DispatchUncertain) return false
        if (failure.nothingSent) return true
        val command =
            !request.promptOnly &&
                request.attachments.isEmpty() &&
                TypedKind.of(request.typedText.trim()) !is TypedKind.Prompt
        return !command
    }

    /**
     * Whether New conversation opens the conversation page as soon as the first message is queued. A server's own
     * conversation exists the moment it is made, so it opens at once. A phone agent's conversation exists only once its
     * helper created the agent, so its page opens when the first message is Accepted and never before.
     */
    fun opensAtOnce(startsOnPhoneAgents: Boolean): Boolean = !startsOnPhoneAgents

    /**
     * Whether the phone's agents' helper made the agent of the conversation [session] (the app's own id): [daemonId]
     * names it by the helper's id once its first message created it, and by the app's id while it is only a draft. The
     * helper creates an agent and its first message in one request, so a made agent proves that first message
     * arrived; a draft, or a record with no conversation yet, proves nothing.
     */
    fun madeOnHelper(session: String, daemonId: (String) -> String): Boolean =
        session.isNotBlank() && daemonId(session).let { it.isNotBlank() && it != session }
}
