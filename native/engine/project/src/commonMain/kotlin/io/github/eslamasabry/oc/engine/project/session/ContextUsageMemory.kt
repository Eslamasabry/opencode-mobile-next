package io.github.eslamasabry.oc.engine.project.session

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.round

// Port of lib/state/context_usage_memory.dart.

/**
 * The last context-window share seen for each conversation of one server, so the chats list can draw a small ring
 * without opening a chat. Stored under `oc.contextUsage.<profileId>` (a fraction per session id, newest
 * [MAX_ENTRIES] kept), so the standard profile sweep removes it. [close] stops admission and waits for a running write;
 * with the profile's [lane] closed too, a late write can never bring a deleted server's values back.
 */
public class ContextUsageMemory(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val profile: ProfileId,
) {
    private var values: LinkedHashMap<String, Double>? = null
    private var closed = false
    private val writes = Mutex()

    /** The last share seen for [session], or null. */
    public fun usageOf(session: SessionId): Double? = if (closed) null else map()[session.raw]

    /**
     * Remembers [fraction] (null forgets it; NaN and infinities are not kept), clamped to 0..1 and rounded to three
     * decimals. Memory changes at once; only a change is written, in order.
     */
    public suspend fun remember(session: SessionId, fraction: Double?): Outcome<Unit> {
        if (closed || profile.raw.isEmpty() || session.raw.isEmpty()) return Outcome.Ok(Unit)
        val map = map()
        val value = if (fraction == null || !fraction.isFinite()) null else round(fraction.coerceIn(0.0, 1.0) * 1000) / 1000
        if (map[session.raw] == value) return Outcome.Ok(Unit)
        // Re-inserting keeps the newest last, so the oldest go first at the cap.
        map.remove(session.raw)
        if (value != null) map[session.raw] = value
        while (map.size > MAX_ENTRIES) map.remove(map.keys.first())
        val text = DartJson.encode(LinkedHashMap(map))
        return writes.withLock {
            if (closed) Outcome.Ok(Unit) else store.commit(lane) { putString(keyFor(profile), text) }
        }
    }

    /** Stops admission, then waits for the write already running. */
    public suspend fun close() {
        closed = true
        writes.withLock { }
    }

    private fun map(): LinkedHashMap<String, Double> =
        values ?: LinkedHashMap<String, Double>().also { read ->
            val decoded = store.string(keyFor(profile))?.let(DartJson::decodeOrNull) as? Map<*, *>
            decoded?.forEach { (k, v) -> if (k is String && v is Number) read[k] = v.toDouble() }
            values = read
        }

    public companion object {
        public const val MAX_ENTRIES: Int = 200

        public fun keyFor(profile: ProfileId): String = "oc.contextUsage.${profile.raw}"

        public val SHAPE: KeyShape =
            KeyShape("oc.contextUsage.", KeyScope.Profile, PrefType.Str, ":engine:project", "last context share per chat")
    }
}
