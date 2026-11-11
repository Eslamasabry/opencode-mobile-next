package io.github.eslamasabry.oc.feature.git

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.delivery.SendRequest
import io.github.eslamasabry.oc.feature.git.presentation.GitAskAgent

/** The Git page reads the conversation's send pipeline from that conversation's own graph. */
@ContributesTo(ConnectionScope::class)
public interface GitGraphAccessors {
    public val gitDelivery: ConnectionDelivery
}

/**
 * The chat's own send pipeline, like the review's comments: a steer while the agent works where the server steers
 * (never a queue), the next prompt when it is idle. The connection is read again at send time (a reconnect swaps it).
 */
@Inject
@ContributesBinding(AppScope::class)
public class RegistryGitAskAgent(private val registry: ConnectionRegistry) : GitAskAgent {
    override fun available(ref: ConversationRef): Boolean =
        (registry.graphFor(ref) as? GitGraphAccessors)?.gitDelivery?.engine != null

    override suspend fun ask(ref: ConversationRef, prompt: String): Outcome<Unit> {
        val engine =
            (registry.graphFor(ref) as? GitGraphAccessors)?.gitDelivery?.engine
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("gitAskAgent")))
        return when (val sent = engine.sendInsteadOfDraft(SendRequest(ref, prompt, promptOnly = true))) {
            is Outcome.Failed -> sent
            is Outcome.Ok -> Outcome.Ok(Unit)
        }
    }
}
