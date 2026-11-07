package io.github.eslamasabry.oc.feature.review

import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import io.github.eslamasabry.oc.feature.review.presentation.AgentGuideAsk
import io.github.eslamasabry.oc.feature.review.presentation.ReviewGuideAsk
import io.github.eslamasabry.oc.feature.review.presentation.ReviewPromptSender

/**
 * The review's way to the conversation's agent (B-155, B-156): the chat's own send pipeline, so a comment steers a
 * running turn where the server steers (never a queue) and is the next prompt when the agent is idle. A failed send
 * never leaves its words in the chat's box.
 */
@Inject
public class ReviewAgent(private val registry: ConnectionRegistry, private val hub: ReviewHub) {
    /** Null when this conversation's connection has no send pipeline (nothing to send through). */
    public fun sender(ref: ConversationRef): ReviewPromptSender? {
        if ((registry.graphFor(ref) as? ReviewGraphAccessors)?.reviewDelivery?.engine == null) return null
        return ReviewPromptSender { text -> send(ref, text) }
    }

    public fun guideAsk(ref: ConversationRef): ReviewGuideAsk? = sender(ref)?.let { AgentGuideAsk(hub.review(ref), it) }

    private suspend fun send(ref: ConversationRef, text: String): Outcome<Unit> {
        // The current connection at send time: a reconnect swaps the graph.
        val engine =
            (registry.graphFor(ref) as? ReviewGraphAccessors)?.reviewDelivery?.engine
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("reviewComments")))
        return when (val sent = engine.sendInsteadOfDraft(SendRequest(ref, text, promptOnly = true))) {
            is Outcome.Failed -> sent
            is Outcome.Ok -> Outcome.Ok(Unit)
        }
    }
}
