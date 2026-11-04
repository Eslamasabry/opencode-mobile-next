package io.github.eslamasabry.oc.engine.settings.pins

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlin.concurrent.atomics.AtomicReference

/**
 * Device-only pinned conversations per profile and location (port of lib/state/session_pins.dart, B-STATE-055), under
 * `oc.sessionPins.<profileId>` as `{"<scope>": ["ses_1", …]}`. The work lists ask "is this row pinned?" thousands of
 * times per frame, so [scope] memoizes the latest location key and [ids] returns the same immutable snapshot until an
 * acknowledged write replaces it; a pending or refused write keeps the old visible pins.
 */
public class SessionPinStore(private val store: KeyValueStore, private val lanes: WriterLanes) {
    private val cache = AtomicReference<Map<String, Map<String, ImmutableSet<String>>>>(emptyMap())
    private val writes = Mutex()

    private fun load(profile: ProfileId): Map<String, ImmutableSet<String>> {
        cache.load()[profile.raw]?.let { return it }
        val loaded = decode((store.get(keyFor(profile)) as? PrefValue.Str)?.value)
        while (true) {
            val current = cache.load()
            current[profile.raw]?.let { return it }
            if (cache.compareAndSet(current, current + (profile.raw to loaded))) return loaded
        }
    }

    /** The pinned ids in [scope]: an immutable snapshot, the same instance until an acknowledged write. */
    public fun ids(profile: ProfileId, scope: String): ImmutableSet<String> = load(profile)[scope] ?: EMPTY

    /** Pins or unpins [id]; published only after storage acknowledges. A refusal keeps the old snapshot. */
    public suspend fun setPinned(profile: ProfileId, scope: String, id: String, pinned: Boolean): Outcome<Unit> =
        writes.withLock {
            val next = load(profile).mapValues { it.value.toMutableSet() }.toMutableMap()
            val entries = next.getOrPut(scope) { mutableSetOf() }
            if (pinned) entries += id else entries -= id
            if (entries.isEmpty()) next -= scope
            val encoded = buildJsonObject {
                for ((key, value) in next) put(key, buildJsonArray { value.forEach { add(JsonPrimitive(it)) } })
            }.toString()
            val result = store.commit(lanes.forProfile(profile)) { putString(keyFor(profile), encoded) }
            if (result is Outcome.Ok) {
                val published = next.mapValues { it.value.toImmutableSet() }
                while (true) {
                    val current = cache.load()
                    if (cache.compareAndSet(current, current + (profile.raw to published))) break
                }
            }
            result
        }

    /** A deleted profile leaves no cached pins and no location in the memo. */
    public fun forget(profile: ProfileId) {
        while (true) {
            val current = cache.load()
            if (cache.compareAndSet(current, current - profile.raw)) break
        }
        lastScope.store(null)
    }

    public companion object {
        public const val PREFIX: String = "oc.sessionPins."
        private val EMPTY: ImmutableSet<String> = persistentSetOf()

        private class Memo(val directory: String?, val workspace: String?, val encoded: String)

        private val lastScope = AtomicReference<Memo?>(null)

        public fun keyFor(profile: ProfileId): String = PREFIX + profile.raw

        /** The ledger row; register once with the app's [ProfileScope] so deletion sweeps the pins. */
        public val SHAPE: KeyShape = KeyShape(PREFIX, KeyScope.Profile, PrefType.Str, ":engine:settings", "pinned conversations")

        /** The JSON `[directory, workspace]` key of a location; the latest pair is memoized (same instance). */
        public fun scope(directory: String?, workspace: String?): String {
            val previous = lastScope.load()
            if (previous != null && previous.directory == directory && previous.workspace == workspace) return previous.encoded
            val encoded = buildJsonArray {
                add(directory?.let(::JsonPrimitive) ?: JsonNull)
                add(workspace?.let(::JsonPrimitive) ?: JsonNull)
            }.toString()
            lastScope.store(Memo(directory, workspace, encoded))
            return encoded
        }

        private fun decode(raw: String?): Map<String, ImmutableSet<String>> {
            val value =
                try {
                    Json.parseToJsonElement(raw ?: "{}") as? JsonObject
                } catch (_: IllegalArgumentException) {
                    null
                } ?: return emptyMap()
            return value.mapNotNull { (key, list) ->
                (list as? JsonArray)?.let { array ->
                    key to array.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.takeIf(String::isNotEmpty) }
                        .toImmutableSet()
                }
            }.toMap()
        }
    }
}
