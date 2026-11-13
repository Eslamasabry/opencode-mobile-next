package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.firstuse.AgentFirstUse
import io.github.eslamasabry.oc.feature.chat.world.AgentSetUpNeed
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState

/**
 * Set up at first use, in the chat (engine [AgentFirstUse]): a phone agent that cannot report its sign-in ahead of time
 * (Qwen Code, Goose, omp) learns the truth when its conversation starts. A send of such an agent that failed because it
 * was never set up reads "<agent> needs setting up before it can answer" with "Set up <agent>" instead of Not sent / Try
 * again; the typed words stay on the bubble. Every other failure, and every agent the app can check, keeps its words.
 */
internal object AgentSetUpRules {
    /**
     * The set-up [bubble] needs, or null for the ordinary failure words.
     *
     * - [runtimeId]: the conversation's own runtime (its provider id, from the gateway), never a reply row's.
     * - [firstMessage]: nothing in the conversation yet, so this send was to open it ([AgentFirstUse.Phase.StartConversation]);
     *   otherwise it answers a conversation that opened ([AgentFirstUse.Phase.FirstPrompt]).
     * - [setUpDone]: the agent's set-up terminal ended, as known when this failure came.
     * - [setUpSinceFailure]: the person finished the set-up after this failure: the bubble goes back to its normal Try
     *   again, and the next failure's own text decides.
     *
     * Counted failures: a send that did not leave ([PendingState.Failed]), and a send the agent's computer answered with
     * its own refusal on a server without a waiting-sends record (Paseo: the daemon's `rpc_error` to the conversation's
     * creation arrives after the prompt went out, as an unconfirmed send whose details carry the agent's sentence). The
     * sentence is matched, never shown.
     */
    fun need(
        bubble: PendingSend,
        runtimeId: String?,
        firstMessage: Boolean,
        setUpDone: Boolean,
        setUpSinceFailure: Boolean,
        catalog: AgentCatalog = AgentCatalog.builtIn,
    ): AgentSetUpNeed? {
        if (setUpSinceFailure || !counts(bubble)) return null
        val agent = runtimeId?.let { id -> catalog.agents.firstOrNull { it.providerId == id } } ?: return null
        val phase = if (firstMessage) AgentFirstUse.Phase.StartConversation else AgentFirstUse.Phase.FirstPrompt
        val verdict = AgentFirstUse.classify(agent, phase, bubble.failure?.details?.text, setUpDone)
        return if (verdict == AgentFirstUse.Verdict.NeedsSetUp) AgentSetUpNeed(agent.id.raw, agent.name) else null
    }

    /** The catalog agent behind a conversation's runtime (the id its set-up terminal and set-up list use). */
    fun agentId(runtimeId: String, catalog: AgentCatalog = AgentCatalog.builtIn): String? =
        catalog.agents.firstOrNull { it.providerId == runtimeId }?.id?.raw

    /** The bubbles whose failure is the agent's own answer: the ones [need] reads. */
    fun counts(bubble: PendingSend): Boolean {
        val failure = bubble.failure ?: return false
        return when (bubble.state) {
            PendingState.Failed -> true
            PendingState.Unconfirmed ->
                !bubble.reviewable && failure is Failure.ComputerDidNotAnswer && failure.details != null
            PendingState.Sending -> false
        }
    }
}
