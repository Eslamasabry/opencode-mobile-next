package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.coroutines.flow.MutableStateFlow

// Ports of lib/domain/session_note.dart, the note half of lib/api2/gateway_operations/ops_extras.dart and
// lib/state/connection/session_notes.dart.

/** Why a note operation was refused (Dart `SessionNoteFailure`). */
public enum class SessionNoteProblem { Unsupported, Changed, InvalidValue, TooLarge, Busy }

/** A note operation's result: the value, an app-side refusal, or the transport's Failure unchanged. */
public sealed interface NoteResult<out T> {
    public data class Ok<out T>(val value: T) : NoteResult<T>

    /** [maxBytes] is the limit to name for [SessionNoteProblem.TooLarge]: the server's own when it sent one. */
    public data class Refused(val problem: SessionNoteProblem, val maxBytes: Int = SessionNoteRules.MAX_BYTES) :
        NoteResult<Nothing>

    public data class Failed(val failure: Failure) : NoteResult<Nothing>
}

public object SessionNoteRules {
    /** The only instruction entry mobile manages. */
    public const val KEY: String = "mobile.note"

    /** The pinned server counts the UTF-8 bytes of `JSON.stringify(value)`, including quotes and escapes. */
    public const val MAX_BYTES: Int = 8192

    public fun encodedBytes(value: String): Int = DartJson.utf8(DartJson.encode(value)).size
}

public enum class NoteMethod { Get, Put, Delete }

/**
 * The request the wire port receives. It carries the gateway's location and nothing about auth: the port is bound to
 * one connection and adds that connection's credentials itself, so a note can never travel under another profile's.
 */
public data class SessionNoteWireRequest(
    val method: NoteMethod,
    val sessionId: SessionId,
    val location: ServerLocation,
    /** The JSON body of a Put: `{"value": …}`; null otherwise. */
    val value: String? = null,
) {
    public val path: String
        get() {
            val base = "/session/${DartJson.encodeComponent(sessionId.raw)}/instructions/entries"
            return if (method == NoteMethod.Get) base else "$base/${SessionNoteRules.KEY}"
        }

    public val query: Map<String, String>
        get() =
            buildMap {
                location.directory?.let { put("location[directory]", it) }
                location.workspace?.let { put("location[workspace]", it) }
            }

    public val body: String? get() = value?.let { DartJson.encode(mapOf("value" to it)) }
}

/** What the wire port answers: a read's decoded JSON document, or an acknowledged mutation. */
public sealed interface SessionNoteWireReply {
    public data class Read(val json: Any?) : SessionNoteWireReply

    public data object Done : SessionNoteWireReply
}

public fun interface SessionNoteWire {
    public suspend fun send(request: SessionNoteWireRequest): Outcome<SessionNoteWireReply>
}

/**
 * One connection's note adapter (pinned to [location]). Other entry names and values never leave it. An absent route
 * (405/501 or a non-session 404) latches [supported] off; a missing session or an auth failure does not.
 */
public class SessionNoteGateway(private val wire: SessionNoteWire, private val location: ServerLocation) {
    private val latch = MutableStateFlow(true)
    public val supported: Boolean get() = latch.value

    public suspend fun load(sessionId: SessionId): NoteResult<String?> =
        when (val reply = request(SessionNoteWireRequest(NoteMethod.Get, sessionId, location))) {
            is NoteResult.Ok -> decode(reply.value)
            is NoteResult.Refused -> reply
            is NoteResult.Failed -> reply
        }

    public suspend fun save(sessionId: SessionId, value: String): NoteResult<Unit> {
        if (SessionNoteRules.encodedBytes(value) > SessionNoteRules.MAX_BYTES) {
            return NoteResult.Refused(SessionNoteProblem.TooLarge)
        }
        return request(SessionNoteWireRequest(NoteMethod.Put, sessionId, location, value)).unit()
    }

    public suspend fun remove(sessionId: SessionId): NoteResult<Unit> =
        request(SessionNoteWireRequest(NoteMethod.Delete, sessionId, location)).unit()

    private suspend fun request(request: SessionNoteWireRequest): NoteResult<SessionNoteWireReply> =
        when (val result = wire.send(request)) {
            is Outcome.Ok -> NoteResult.Ok(result.value)
            is Outcome.Failed -> {
                val failure = result.failure
                when {
                    marksAbsent(failure) -> {
                        latch.value = false
                        NoteResult.Refused(SessionNoteProblem.Unsupported)
                    }
                    failure.wireTag() == TOO_LARGE_TAG -> {
                        val limit = (failure as? Failure.Rejected)?.extras?.fields?.get("maxBytes")?.toIntOrNull()
                        NoteResult.Refused(
                            SessionNoteProblem.TooLarge,
                            if (limit != null && limit > 0) limit else SessionNoteRules.MAX_BYTES,
                        )
                    }
                    else -> NoteResult.Failed(failure)
                }
            }
        }

    private fun decode(reply: SessionNoteWireReply): NoteResult<String?> {
        val data = ((reply as? SessionNoteWireReply.Read)?.json as? Map<*, *>)?.get("data") as? List<*>
            ?: return NoteResult.Refused(SessionNoteProblem.InvalidValue)
        val owned = data.filterIsInstance<Map<*, *>>().filter { it["key"] == SessionNoteRules.KEY }
        if (owned.isEmpty()) return NoteResult.Ok(null)
        val value = owned.singleOrNull()?.get("value") as? String
            ?: return NoteResult.Refused(SessionNoteProblem.InvalidValue)
        return NoteResult.Ok(value)
    }

    private fun NoteResult<SessionNoteWireReply>.unit(): NoteResult<Unit> =
        when (this) {
            is NoteResult.Ok -> NoteResult.Ok(Unit)
            is NoteResult.Refused -> this
            is NoteResult.Failed -> this
        }

    private companion object {
        const val TOO_LARGE_TAG = "InstructionEntryValueTooLargeError"
    }
}

private fun Failure.wireTag(): String? =
    when (this) {
        is Failure.NotFound -> tag?.raw
        is Failure.Conflict -> tag?.raw
        is Failure.Busy -> tag?.raw
        is Failure.Server -> tag?.raw
        is Failure.Rejected -> tag?.raw
        else -> null
    }

/** What a person reviewed: the note as read in one location. Only [SessionNotes] makes one. */
public class SessionNoteReview internal constructor(
    internal val owner: SessionNotes,
    internal val locationRevision: Int,
    public val sessionId: SessionId,
    public val value: String?,
    internal val revision: Int,
)

/**
 * The state side of session notes (Dart `_ConnectionControllerSessionNotes`). A save re-reads the note first and is
 * refused when another client changed it; a location change (while waking the connection or during the fresh read)
 * never writes into the new location; a second save of the same note while one runs is refused as busy; only an
 * accepted write leaves a receipt.
 *
 * [current] is the connection's live note gateway (null when the server has none); [prepare] wakes the connection and
 * returns the gateway to act on; [locationRevision] changes with every location switch.
 */
public class SessionNotes(
    private val current: () -> SessionNoteGateway?,
    private val prepare: suspend () -> SessionNoteGateway?,
    private val locationRevision: () -> Int,
    private val isDeleted: (SessionId) -> Boolean = { false },
    private val isClosed: () -> Boolean = { false },
) {
    private data class Key(val location: Int, val session: SessionId)

    private val writes = MutableStateFlow<Set<Key>>(emptySet())
    private val revisions = HashMap<Key, Int>()
    private val receipts = LinkedHashMap<Key, Boolean>()

    public val supported: Boolean get() = current()?.supported == true

    /** True after a save, false after a removal, null when nothing was written here. */
    public fun receipt(sessionId: SessionId): Boolean? = receipts[Key(locationRevision(), sessionId)]

    public fun dismissReceipt(sessionId: SessionId) {
        receipts.remove(Key(locationRevision(), sessionId))
    }

    public fun isCurrent(review: SessionNoteReview): Boolean =
        !isClosed() &&
            review.owner === this &&
            review.locationRevision == locationRevision() &&
            !isDeleted(review.sessionId) &&
            review.revision == (revisions[Key(review.locationRevision, review.sessionId)] ?: 0)

    public suspend fun load(sessionId: SessionId): NoteResult<SessionNoteReview> {
        val location = locationRevision()
        val revision = revisions[Key(location, sessionId)] ?: 0
        val gateway = prepare()
        if (location != locationRevision() || isClosed() || isDeleted(sessionId)) return changed()
        if (gateway == null || !gateway.supported) return NoteResult.Refused(SessionNoteProblem.Unsupported)
        return when (val read = gateway.load(sessionId)) {
            is NoteResult.Refused -> read
            is NoteResult.Failed -> read
            is NoteResult.Ok -> {
                val review = SessionNoteReview(this, location, sessionId, read.value, revision)
                if (!isCurrent(review) || gateway !== current()) changed() else NoteResult.Ok(review)
            }
        }
    }

    /** [value] null removes the note. */
    public suspend fun save(review: SessionNoteReview, value: String?): NoteResult<Unit> {
        if (!isCurrent(review)) return changed()
        if (value != null && SessionNoteRules.encodedBytes(value) > SessionNoteRules.MAX_BYTES) {
            return NoteResult.Refused(SessionNoteProblem.TooLarge)
        }
        val key = Key(locationRevision(), review.sessionId)
        if (!admit(key)) return NoteResult.Refused(SessionNoteProblem.Busy)
        try {
            val gateway = prepare()
            if (!isCurrent(review)) return changed()
            if (gateway == null || !gateway.supported) return NoteResult.Refused(SessionNoteProblem.Unsupported)
            when (val fresh = gateway.load(review.sessionId)) {
                is NoteResult.Refused -> return fresh
                is NoteResult.Failed -> return fresh
                is NoteResult.Ok ->
                    if (!isCurrent(review) || gateway !== current() || fresh.value != review.value) return changed()
            }
            val written = if (value == null) gateway.remove(review.sessionId) else gateway.save(review.sessionId, value)
            if (written !is NoteResult.Ok) return written
            // A receipt belongs only to the session and location that accepted the write.
            if (review.locationRevision == locationRevision() && !isClosed() && !isDeleted(review.sessionId)) {
                revisions[key] = (revisions[key] ?: 0) + 1
                receipts.remove(key)
                receipts[key] = value != null
                while (receipts.size > MAX_RECEIPTS) receipts.remove(receipts.keys.first())
            }
            return written
        } finally {
            release(key)
        }
    }

    private fun admit(key: Key): Boolean {
        while (true) {
            val now = writes.value
            if (key in now) return false
            if (writes.compareAndSet(now, now + key)) return true
        }
    }

    private fun release(key: Key) {
        while (true) {
            val now = writes.value
            if (writes.compareAndSet(now, now - key)) return
        }
    }

    private fun changed(): NoteResult<Nothing> = NoteResult.Refused(SessionNoteProblem.Changed)

    private companion object {
        const val MAX_RECEIPTS = 128
    }
}
