package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Connection-owned reads shared by the list's prefetch, chat hydration and header. Never joins
 * another graph.
 */
public class ConnectionReads(
    private val gateway: ServerGateway,
    private val scope: ConnectionCoroutineScope,
) {
    private val order = Mutex()
    private val sessions = HashMap<SessionId, Deferred<Outcome<Session>>>()

    private data class TailKey(val session: SessionId, val cursor: String?, val limit: Int)

    private class Tail(
        val read: Deferred<Outcome<ServerPage<MessageWithParts>>>,
        var prefetched: Boolean,
    )

    private val tails = LinkedHashMap<TailKey, Tail>()
    private val mutableMissing = MutableStateFlow<Set<SessionId>>(emptySet())
    public val missing: StateFlow<Set<SessionId>> = mutableMissing.asStateFlow()
    private val closed = AtomicReference(false)

    /**
     * A confirmed deletion/404 is a tombstone for this graph; late reads and pages cannot restore
     * it.
     */
    public fun forget(session: SessionId) {
        mutableMissing.update { it + session }
    }

    public fun close() {
        closed.store(true)
    }

    private fun refusal(session: SessionId): Failure? =
        when {
            closed.load() -> Failure.Refused(RefusalReason.StaleScope)
            session in mutableMissing.value -> Failure.NotFound()
            else -> null
        }

    public suspend fun session(id: SessionId, fresh: Boolean = false): Outcome<Session> {
        val read = order.withLock {
            refusal(id)?.let {
                return it.failed()
            }
            if (fresh) sessions.remove(id)
            sessions.getOrPut(id) { scope.async { gateway.session(id) } }
        }
        return try {
            val result = read.await()
            if (result is Outcome.Failed && result.failure is Failure.NotFound) forget(id)
            refusal(id)?.failed() ?: result
        } finally {
            order.withLock { if (sessions[id] === read && read.isCompleted) sessions.remove(id) }
        }
    }

    /**
     * Starts one head read before navigation; opening the chat joins it or consumes its bounded
     * result.
     */
    public fun prefetch(id: SessionId, limit: Int = 100) {
        scope.launch { tail(id, cursor = null, limit = limit, prefetch = true) }
    }

    public suspend fun tail(
        id: SessionId,
        cursor: String? = null,
        limit: Int = 100,
    ): Outcome<ServerPage<MessageWithParts>> = tail(id, cursor, limit, prefetch = false)

    private suspend fun tail(
        id: SessionId,
        cursor: String?,
        limit: Int,
        prefetch: Boolean,
    ): Outcome<ServerPage<MessageWithParts>> {
        val key = TailKey(id, cursor, limit)
        val entry = order.withLock {
            refusal(id)?.let {
                return it.failed()
            }
            val held = tails[key]
            if (held != null) {
                if (!prefetch) held.prefetched = false
                held
            } else {
                while (tails.size >= MAX_PREFETCH) {
                    val removable = tails.entries.firstOrNull { it.value.read.isCompleted } ?: break
                    tails.remove(removable.key)
                }
                if (prefetch && tails.size >= MAX_PREFETCH) return Failure.StorageRefused().failed()
                Tail(scope.async { gateway.messagePage(id, cursor, limit) }, prefetch).also {
                    tails[key] = it
                }
            }
        }
        return try {
            val result = entry.read.await()
            if (result is Outcome.Failed && result.failure is Failure.NotFound) forget(id)
            if (result is Outcome.Failed)
                order.withLock { if (tails[key] === entry) tails.remove(key) }
            refusal(id)?.failed() ?: result
        } finally {
            order.withLock {
                if (tails[key] === entry && !entry.prefetched && entry.read.isCompleted)
                    tails.remove(key)
            }
        }
    }

    /**
     * Live changes discard cached and in-flight prefetches. Existing hydration callers still merge
     * against their own live revision, while a later hydration starts a fresh read.
     */
    public suspend fun invalidateTail(id: SessionId) {
        order.withLock {
            tails.entries.removeAll { it.key.session == id }
        }
    }

    private companion object {
        const val MAX_PREFETCH = 32
    }
}
