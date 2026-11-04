package io.github.eslamasabry.oc.engine.settings.context

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlin.concurrent.atomics.AtomicReference
import kotlin.math.round
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Last confirmed context-window share per conversation, never invented token counts. */
public interface ContextUsageHistory {
    public fun fraction(profile: ProfileId, session: String): Double?

    public suspend fun remember(
        profile: ProfileId,
        session: String,
        fraction: Double?,
    ): Outcome<Unit>
}

/** Flutter-compatible oc.contextUsage key, newest 200, with deletion's shared admission fence. */
public class ContextUsageMemory(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val present: (ProfileId) -> Boolean,
) : ContextUsageHistory {
    private val writes = Mutex()

    private class Decoded(val raw: String, val values: Map<String, Double>)

    private val cache = AtomicReference<Map<String, Decoded>>(emptyMap())

    private fun values(profile: ProfileId): Map<String, Double> {
        if (!present(profile) || !lanes.forProfile(profile).isAdmitting) return linkedMapOf()
        val raw = (store.get(key(profile)) as? PrefValue.Str)?.value ?: return linkedMapOf()
        cache.load()[profile.raw]?.let { if (it.raw == raw) return it.values }
        val decoded =
            try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            }
        val valid =
            decoded
                .orEmpty()
                .mapNotNull { (id, value) ->
                    val fraction = (value as? JsonPrimitive)?.doubleOrNull
                    if (
                        id.isNotEmpty() &&
                            fraction != null &&
                            fraction.isFinite() &&
                            fraction in 0.0..1.0
                    )
                        id to fraction
                    else null
                }
                .takeLast(MAX_ENTRIES)
        val result = LinkedHashMap(valid.toMap())
        while (true) {
            val before = cache.load()
            if (cache.compareAndSet(before, before + (profile.raw to Decoded(raw, result)))) break
        }
        return result
    }

    override fun fraction(profile: ProfileId, session: String): Double? = values(profile)[session]

    override suspend fun remember(
        profile: ProfileId,
        session: String,
        fraction: Double?,
    ): Outcome<Unit> = writes.withLock {
        if (!present(profile) || !lanes.forProfile(profile).isAdmitting)
            return@withLock Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        if (session.isEmpty()) return@withLock Outcome.Failed(Failure.StorageRefused())
        val value =
            fraction
                ?.takeIf { it.isFinite() }
                ?.coerceIn(0.0, 1.0)
                ?.let { round(it * 1_000) / 1_000 }
        val next = LinkedHashMap(values(profile))
        if (next[session] == value) return@withLock Outcome.Ok(Unit)
        next.remove(session)
        if (value != null) next[session] = value
        while (next.size > MAX_ENTRIES) next.remove(next.keys.first())
        val raw = JsonObject(next.mapValues { JsonPrimitive(it.value) }).toString()
        store.commit(lanes.forProfile(profile)) { putString(key(profile), raw) }
    }

    public companion object {
        public const val MAX_ENTRIES: Int = 200

        public fun key(profile: ProfileId): String = "oc.contextUsage." + profile.raw
    }
}
