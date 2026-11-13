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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
 * `oc.sessionPins.<profileId>` as `{"<scope>": ["ses_1", …], "order": ["ses_1", …]}`. The work lists ask "is this row
 * pinned?" thousands of times per frame, so [scope] memoizes the latest location key and [ids] returns the same
 * immutable snapshot until an acknowledged write replaces it; a pending or refused write keeps the old visible pins.
 *
 * This is the one pin store of the app: the home lists and the edge dock both read and write it. `"order"` is the
 * profile-wide list in the person's order (a scope key is a JSON array text, so it can never be called `order`); a
 * session in it is pinned in every location, which is how a pin made on the dock shows on the home list. A pin saved
 * before `"order"` existed sits only in its scope and is ordered after the ordered ones ([orderedIds]).
 */
public class SessionPinStore(private val store: KeyValueStore, private val lanes: WriterLanes) {
    private class Pins(val scopes: Map<String, ImmutableSet<String>>, val order: ImmutableList<String>) {
        private val orderSet: ImmutableSet<String> = order.toImmutableSet()
        private val effective: Map<String, ImmutableSet<String>> =
            scopes.mapValues { (_, ids) -> if (orderSet.all { it in ids }) ids else (ids + orderSet).toImmutableSet() }

        fun ids(scope: String): ImmutableSet<String> = effective[scope] ?: orderSet

        fun ordered(): List<String> = (order + scopes.values.flatten()).distinct()
    }

    private val cache = AtomicReference<Map<String, Pins>>(emptyMap())
    private val writes = Mutex()
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = CHANGE_BUFFER)

    /** Emits after an acknowledged write by any caller of this store, so another surface can read the pins again. */
    public val changes: SharedFlow<Unit> = changed.asSharedFlow()

    private fun load(profile: ProfileId): Pins {
        cache.load()[profile.raw]?.let { return it }
        val loaded = decode((store.get(keyFor(profile)) as? PrefValue.Str)?.value)
        while (true) {
            val current = cache.load()
            current[profile.raw]?.let { return it }
            if (cache.compareAndSet(current, current + (profile.raw to loaded))) return loaded
        }
    }

    /** The pinned ids in [scope] (its own and the profile-wide ones): an immutable snapshot, the same instance until an acknowledged write. */
    public fun ids(profile: ProfileId, scope: String): ImmutableSet<String> = load(profile).ids(scope)

    /** Every pinned id of [profile] in the person's order, whatever the location. */
    public fun orderedIds(profile: ProfileId): List<String> = load(profile).ordered()

    /**
     * Pins or unpins [id]; published only after storage acknowledges. A refusal keeps the old snapshot. A new pin goes
     * last in the order. Unpinning removes it everywhere: a conversation has one pin.
     */
    public suspend fun setPinned(profile: ProfileId, scope: String, id: String, pinned: Boolean): Outcome<Unit> =
        writes.withLock {
            val current = load(profile)
            val next = current.scopes.mapValues { it.value.toMutableSet() }.toMutableMap()
            val order = current.ordered().toMutableList()
            if (pinned) {
                val entries = next.getOrPut(scope) { mutableSetOf() }
                entries += id
                if (id !in order) order += id
            } else {
                for (entries in next.values) entries -= id
                order -= id
                next.values.removeAll { it.isEmpty() }
            }
            write(profile, next, order)
        }

    /**
     * Makes [ids] exactly the profile's pins, in that order (the dock's reorder, pin and unpin). A pin missing from
     * [ids] leaves every location. Nothing is written when nothing changes.
     */
    public suspend fun replaceOrder(profile: ProfileId, ids: List<String>): Outcome<Unit> =
        writes.withLock {
            val current = load(profile)
            val wanted = ids.filter { it.isNotEmpty() }.distinct()
            if (wanted == current.order && wanted == current.ordered()) return@withLock Outcome.Ok(Unit)
            val keep = wanted.toSet()
            val next = current.scopes.mapValues { (_, entries) -> entries.filterTo(mutableSetOf()) { it in keep } }.toMutableMap()
            next.values.removeAll { it.isEmpty() }
            write(profile, next, wanted)
        }

    private suspend fun write(profile: ProfileId, scopes: Map<String, Set<String>>, order: List<String>): Outcome<Unit> {
        val encoded = buildJsonObject {
            for ((key, value) in scopes) put(key, buildJsonArray { value.forEach { add(JsonPrimitive(it)) } })
            if (order.isNotEmpty()) put(ORDER_KEY, buildJsonArray { order.forEach { add(JsonPrimitive(it)) } })
        }.toString()
        val result = store.commit(lanes.forProfile(profile)) { putString(keyFor(profile), encoded) }
        if (result is Outcome.Ok) {
            val published = Pins(scopes.mapValues { it.value.toImmutableSet() }, order.toImmutableList())
            while (true) {
                val current = cache.load()
                if (cache.compareAndSet(current, current + (profile.raw to published))) break
            }
            changed.tryEmit(Unit)
        }
        return result
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

        /** The profile-wide ordered list inside the stored object; no location key can have this name. */
        public const val ORDER_KEY: String = "order"
        private const val CHANGE_BUFFER = 16

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

        private fun decode(raw: String?): Pins {
            val value =
                try {
                    Json.parseToJsonElement(raw ?: "{}") as? JsonObject
                } catch (_: IllegalArgumentException) {
                    null
                } ?: return Pins(emptyMap(), persistentListOf())
            fun idsOf(array: JsonArray): List<String> =
                array.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.takeIf(String::isNotEmpty) }
            val order = (value[ORDER_KEY] as? JsonArray)?.let(::idsOf).orEmpty().distinct().toImmutableList()
            val scopes =
                value.mapNotNull { (key, list) ->
                    if (key == ORDER_KEY) null else (list as? JsonArray)?.let { key to idsOf(it).toImmutableSet() }
                }.toMap()
            return Pins(scopes, order)
        }
    }
}
