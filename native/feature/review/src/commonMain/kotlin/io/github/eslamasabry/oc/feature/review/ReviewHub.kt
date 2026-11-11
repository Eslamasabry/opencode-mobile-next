package io.github.eslamasabry.oc.feature.review

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.LocatedVcsGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.feature.review.presentation.ReviewProjectRead
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.ReviewGateways
import io.github.eslamasabry.oc.engine.review.ReviewLifetime
import io.github.eslamasabry.oc.feature.review.presentation.ReviewBranchRead
import io.github.eslamasabry.oc.feature.review.presentation.ReviewCommentBook
import io.github.eslamasabry.oc.feature.review.presentation.ReviewGuide
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The review reads its conversation's gateways from that conversation's own graph (ADR §9.1). */
@ContributesTo(ConnectionScope::class)
public interface ReviewGraphAccessors {
    public val reviewGateway: ServerGateway
    public val reviewOperations: ServerOperationsGateway

    /** The conversation's send pipeline: line comments and the guide question reach its agent (B-155/156). */
    public val reviewDelivery: ConnectionDelivery
}

/** A conversation's review comments and guides, kept as long as its review is (in memory only). */
public class ReviewNotes {
    public val comments: ReviewCommentBook = ReviewCommentBook()
    public val guides: MutableMap<String, ReviewGuide> = HashMap()
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
    private val notes = HashMap<ConversationRef, ReviewNotes>()

    /** The conversation's comments and guides; they go when its review is closed. */
    public fun notes(ref: ConversationRef): ReviewNotes {
        review(ref) // keeps the review (and so these notes) among the open ones
        return notes.getOrPut(ref) { ReviewNotes() }
    }

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
            notes.remove(oldest)
        }
        return review
    }

    /**
     * The whole branch's changes for this conversation, read through its current connection at read time (a
     * reconnect swaps the graph). A server whose gateway has no branch diff answers Unavailable, so the scope hides.
     */
    public fun branch(ref: ConversationRef): ReviewBranchRead =
        ReviewBranchRead {
            val ops = (registry.graphFor(ref) as? ReviewGraphAccessors)?.reviewOperations
                ?: return@ReviewBranchRead Failure.Unavailable(FeatureId("vcsBranchDiff")).failed()
            ops.listVcsDiffs(VcsDiffMode.Branch)
        }

    /**
     * The uncommitted changes of [directory] on [profile], read through its current connection at read time. A server
     * that cannot diff a chosen folder nor the session's own answers Unavailable; a closed profile is refused.
     */
    public fun projectTree(profile: ProfileId, directory: String): ReviewProjectRead =
        ReviewProjectRead {
            val held = registry.live.value[profile]
            val graph = held as? ReviewGraphAccessors
                ?: return@ReviewProjectRead Failure.Refused(RefusalReason.ProfileClosing).failed()
            val gateway = graph.reviewGateway
            val located = gateway as? LocatedVcsGateway
            // Without a located read the server diffs only the folder it is on now: any other folder's answer would be
            // a different project's changes, so that is Unavailable (as Files' own project changes are).
            fun onFolder() = gateway.location.value.directory?.trimEnd('/') == directory.trimEnd('/')
            when {
                located != null -> located.workingTreeAt(ServerLocation(directory, null))
                gateway.capabilities.value.sessionDiff && onFolder() -> {
                    val read = graph.reviewOperations.listVcsDiffs(VcsDiffMode.WorkingTree)
                    if (onFolder() && registry.live.value[profile] === held) read
                    else Failure.Unavailable(FeatureId("sessionDiff")).failed()
                }
                else -> Failure.Unavailable(FeatureId("sessionDiff")).failed()
            }
        }

    private fun gatewaysOf(ref: ConversationRef): ReviewGateways? =
        (registry.graphFor(ref) as? ReviewGraphAccessors)?.let { ReviewGateways(it.reviewGateway, it.reviewOperations) }

    private companion object {
        const val KEEP = 6
    }
}
