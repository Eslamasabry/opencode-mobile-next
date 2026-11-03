package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Port of `loadSessionTail` / `prefetchSessionTail` (lib/state/connection/session_cache.dart).

/** The newest history page of a session, read up to a staged revert [boundary] when there is one. */
public fun interface SessionTailSource<M> {
    public suspend fun newestPage(id: SessionId, boundary: MessageId?): Outcome<ServerPage<M>>
}

/** Best-effort disk cache of the opening excerpt; [isCurrent] is checked again before its write. */
public fun interface SessionTailSaver<M> {
    public suspend fun save(profile: ProfileId, id: SessionId, items: List<M>, isCurrent: () -> Boolean)
}

/** What the loader needs to know about the connection now. All reads are cheap and synchronous. */
public interface SessionTailContext<M> {
    public val profile: ProfileId?
    public val source: SessionTailSource<M>?

    public fun isProfileReadable(profile: ProfileId): Boolean

    /** Bumped by every profile deletion attempt. */
    public fun deletionRevision(profile: ProfileId): Int

    public fun historyRevision(id: SessionId): Int

    public fun stagedBoundary(id: SessionId): MessageId?

    public fun isDeleted(id: SessionId): Boolean
}

/**
 * The authoritative newest page of a conversation. Prefetch and route hydration share one read in flight (same
 * connection, session, history revision and staged boundary). A result that lands after the profile was deleted, the
 * history moved or the staged boundary changed is rejected and never cached.
 */
public class SessionTailLoader<M : Any>(
    private val scope: CoroutineScope,
    private val context: SessionTailContext<M>,
    private val saver: SessionTailSaver<M>? = null,
) {
    private data class Key(val source: Any, val id: SessionId, val history: Int, val boundary: MessageId?)

    private val reads = SharedWork<Key, Outcome<ServerPage<M>>>(scope)

    public fun canLoad(id: SessionId): Boolean {
        val owner = context.profile
        return owner != null && context.source != null && context.isProfileReadable(owner) && !context.isDeleted(id)
    }

    public suspend fun load(id: SessionId): Outcome<ServerPage<M>> {
        val owner = context.profile
        val source = context.source
        if (owner == null || source == null || !canLoad(id)) return Outcome.Failed(Failure.Network())
        val history = context.historyRevision(id)
        val boundary = context.stagedBoundary(id)
        val deletion = context.deletionRevision(owner)
        fun current(): Boolean =
            context.source === source &&
                context.isProfileReadable(owner) &&
                context.deletionRevision(owner) == deletion &&
                context.historyRevision(id) == history &&
                context.stagedBoundary(id) == boundary &&
                !context.isDeleted(id)
        return reads.join(Key(source, id, history, boundary)) {
            when (val page = source.newestPage(id, boundary)) {
                is Outcome.Failed -> page
                is Outcome.Ok ->
                    if (!current()) {
                        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
                    } else {
                        // Disk caching never delays the authoritative hydration.
                        if (boundary == null && saver != null) scope.launch { saver.save(owner, id, page.value.items, ::current) }
                        page
                    }
            }
        }
    }

    /** For the next intentional navigation only; a failure says nothing. */
    public suspend fun prefetch(id: SessionId) {
        load(id)
    }
}
