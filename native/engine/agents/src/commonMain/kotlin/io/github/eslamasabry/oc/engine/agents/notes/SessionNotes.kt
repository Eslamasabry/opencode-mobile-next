package io.github.eslamasabry.oc.engine.agents.notes

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.SessionNoteGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A reviewed value belongs to one graph, location and successful-write revision. */
public class SessionNoteReview
internal constructor(
    public val ref: ConversationRef,
    public val value: String?,
    internal val graph: ConnectionGraph,
    internal val location: ServerLocation,
    internal val revision: Long,
)

public class SessionNoteReceipt
internal constructor(
    public val saved: Boolean,
    internal val owner: ServerProfile,
    internal val location: ServerLocation,
)

/**
 * B-STATE-113. One app owner serializes notes across open screens. It wakes first, rereads before
 * every mutation, and never sends a review to a replacement graph/location. Receipts describe
 * acknowledged writes only.
 */
@SingleIn(AppScope::class)
@Inject
public class SessionNotes(private val registry: ConnectionRegistry) {
    private val writes = Mutex()
    private val revisions = mutableMapOf<Pair<ConversationRef, ServerLocation>, Long>()
    private val mutableReceipts =
        MutableStateFlow<ImmutableMap<ConversationRef, SessionNoteReceipt>>(persistentMapOf())
    public val receipts: StateFlow<ImmutableMap<ConversationRef, SessionNoteReceipt>> =
        mutableReceipts.asStateFlow()

    public fun availability(ref: ConversationRef): Flow<Boolean> = channelFlow {
        registry.live.collectLatest { live ->
            val graph = live[ref.profileId]
            if (graph == null) send(false)
            else graph.session.capabilities.collect { send(it.sessionNotes) }
        }
    }

    public fun receipt(review: SessionNoteReview): Boolean? =
        mutableReceipts.value[review.ref]
            ?.takeIf { it.owner == review.graph.profile && it.location == review.location }
            ?.saved

    private fun current(review: SessionNoteReview): Boolean =
        registry.graphFor(review.ref) === review.graph &&
            review.graph.session.server.location.value == review.location &&
            revisions[review.ref to review.location].orZero() == review.revision

    public suspend fun load(ref: ConversationRef): Outcome<SessionNoteReview> = writes.withLock {
        val graph = registry.graphFor(ref) ?: return@withLock stale()
        val gateway = graph.session.server
        val location = gateway.location.value
        val notes = gateway.facet<SessionNoteGateway>() ?: return@withLock unavailable()
        val ready = gateway.awaitActionTransport()
        if (ready is Outcome.Failed) return@withLock ready
        if (registry.graphFor(ref) !== graph || gateway.location.value != location)
            return@withLock stale()
        val value = notes.loadSessionNote(ref.sessionId, location)
        if (value is Outcome.Failed) return@withLock value
        val review =
            SessionNoteReview(
                ref,
                (value as Outcome.Ok).value,
                graph,
                location,
                revisions[ref to location].orZero(),
            )
        if (!current(review)) return@withLock stale()
        Outcome.Ok(review)
    }

    public suspend fun save(review: SessionNoteReview, value: String?): Outcome<Unit> {
        // Duplicate button decisions refuse immediately; they never wait to overwrite the first
        // decision.
        if (!writes.tryLock()) return Outcome.Failed(Failure.Busy())
        try {
            if (!current(review)) return stale()
            val gateway = review.graph.session.server
            val notes = gateway.facet<SessionNoteGateway>() ?: return unavailable()
            val ready = gateway.awaitActionTransport()
            if (ready is Outcome.Failed) return ready
            if (!current(review)) return stale()
            val fresh = notes.loadSessionNote(review.ref.sessionId, review.location)
            if (fresh is Outcome.Failed) return fresh
            if (!current(review) || (fresh as Outcome.Ok).value != review.value)
                return Outcome.Failed(Failure.Conflict())
            val written =
                notes.saveSessionNote(review.ref.sessionId, review.location, value) {
                    current(review)
                }
            if (written is Outcome.Ok && !current(review)) return stale()
            if (written is Outcome.Ok) {
                revisions[review.ref to review.location] = review.revision + 1
                val kept = mutableReceipts.value.toMutableMap()
                kept.remove(review.ref)
                kept[review.ref] =
                    SessionNoteReceipt(value != null, review.graph.profile, review.location)
                while (kept.size > 128) kept.remove(kept.keys.first())
                mutableReceipts.value = kept.toImmutableMap()
            }
            return written
        } finally {
            writes.unlock()
        }
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private fun stale(): Outcome<Nothing> =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

    private fun unavailable(): Outcome<Nothing> =
        Outcome.Failed(Failure.Unavailable(FeatureId("sessionNotes")))
}
