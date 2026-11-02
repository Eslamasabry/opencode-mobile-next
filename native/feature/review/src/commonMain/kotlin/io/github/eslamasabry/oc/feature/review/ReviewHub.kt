package io.github.eslamasabry.oc.feature.review

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.ReviewGateways
import io.github.eslamasabry.oc.engine.review.ReviewLifetime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The review reads its conversation's gateways from that conversation's own graph (ADR §9.1). */
@ContributesTo(ConnectionScope::class)
public interface ReviewGraphAccessors {
    public val reviewGateway: ServerGateway
    public val reviewOperations: ServerOperationsGateway
}

/**
 * One [ConversationReview] per conversation, shared by its review pages, so a page opened from another sees the same
 * staged state. Each follows the registry: a reconnect swaps its gateways, a closed profile leaves none (and the
 * engine dispatches to neither). The few most recent stay open; older ones stop. Main thread only.
 */
@SingleIn(AppScope::class)
@Inject
public class ReviewHub(private val registry: ConnectionRegistry) {
    private val open = LinkedHashMap<ConversationRef, Pair<ConversationReview, ReviewLifetime>>()

    public fun review(ref: ConversationRef): ConversationReview {
        open.remove(ref)?.let { kept ->
            open[ref] = kept // most recent last
            return kept.first
        }
        val lifetime = ReviewLifetime(registry.appScope, registry.mainContext)
        val scope = lifetime.scope
        val gateways =
            registry.live
                .map { graphs -> (graphs[ref.profileId] as? ReviewGraphAccessors)?.let { ReviewGateways(it.reviewGateway, it.reviewOperations) } }
                .stateIn(scope, SharingStarted.Eagerly, gatewaysOf(ref))
        val review = ConversationReview(ref.sessionId, gateways, scope)
        open[ref] = review to lifetime
        while (open.size > KEEP) {
            val oldest = open.keys.first()
            open.remove(oldest)?.second?.close()
        }
        return review
    }

    private fun gatewaysOf(ref: ConversationRef): ReviewGateways? =
        (registry.graphFor(ref) as? ReviewGraphAccessors)?.let { ReviewGateways(it.reviewGateway, it.reviewOperations) }

    private companion object {
        const val KEEP = 6
    }
}
