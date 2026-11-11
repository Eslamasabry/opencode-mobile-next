package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The one key the dock keeps: `oc.dockNames.<profileId>`, a JSON object of session id to nickname. */
public object DockKeys {
    public const val NAMES_PREFIX: String = "oc.dockNames."
    public const val REMOVED_PREFIX: String = "oc.dockRemoved."
    public const val PINS_PREFIX: String = "oc.dockPins."

    /** Device-wide placement keys (no profile id, never swept). */
    public const val SIDE: String = "oc.dockSide"
    public const val BIAS: String = "oc.dockBias"
    public const val OWNER: String = ":engine:attention"

    public fun pinsKey(profile: ProfileId): String = PINS_PREFIX + profile.raw

    public fun removedKey(profile: ProfileId): String = REMOVED_PREFIX + profile.raw

    public fun namesKey(profile: ProfileId): String = NAMES_PREFIX + profile.raw

    public val SHAPES: List<KeyShape> =
        listOf(
            KeyShape(NAMES_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "edge dock nicknames, session id to name"),
            KeyShape(REMOVED_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "edge dock removed conversations, session ids"),
            KeyShape(PINS_PREFIX, KeyScope.Profile, PrefType.Str, OWNER, "edge dock pinned conversations, session ids in order"),
            KeyShape(SIDE, KeyScope.App, PrefType.Str, OWNER, "edge dock side, start or end"),
            KeyShape(BIAS, KeyScope.App, PrefType.Str, OWNER, "edge dock vertical position, 0 to 1"),
        )

    /** Registers the key shape so the profile deletion sweep finds it. */
    public fun register(scope: ProfileScope) {
        SHAPES.forEach(scope::register)
    }
}

/** One JSON array of session ids per profile (the removed set, the pins), newest last, at most [max]. */
internal class DockIdList(private val store: AttentionKeyStore, private val keyOf: (ProfileId) -> String, private val max: Int) {
    private val lock = Mutex()

    suspend fun load(profile: ProfileId): List<SessionId> =
        lock.withLock {
            val raw = runCatching { store.read(keyOf(profile)) }.getOrNull() ?: return@withLock emptyList()
            val array = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return@withLock emptyList()
            array.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf { s -> s.isNotEmpty() } }
                .distinct().takeLast(max).map(::SessionId)
        }

    suspend fun save(profile: ProfileId, current: () -> List<SessionId>): Boolean =
        lock.withLock {
            val ids = current().takeLast(max)
            runCatching {
                if (ids.isEmpty()) store.remove(keyOf(profile)) else store.write(keyOf(profile), JsonArray(ids.map { JsonPrimitive(it.raw) }).toString())
            }.getOrDefault(false)
        }
}

/** The removed set of the dock under [DockKeys.removedKey]. Unreadable or malformed data reads as none. */
public class DockRemovals(store: AttentionKeyStore) {
    private val list = DockIdList(store, DockKeys::removedKey, DockModel.MAX_REMOVED)

    public suspend fun load(profile: ProfileId): List<SessionId> = list.load(profile)

    /** Saves what [current] returns, read inside the lock so the newest state always wins. An empty set removes the key. */
    public suspend fun save(profile: ProfileId, current: () -> List<SessionId>): Boolean = list.save(profile, current)
}

/** The pinned rings of the dock, in the person's order, under [DockKeys.pinsKey]. */
public class DockPins(store: AttentionKeyStore) {
    private val list = DockIdList(store, DockKeys::pinsKey, DockModel.MAX_PINNED)

    public suspend fun load(profile: ProfileId): List<SessionId> = list.load(profile)

    public suspend fun save(profile: ProfileId, current: () -> List<SessionId>): Boolean = list.save(profile, current)
}

/**
 * Where the dock sits, device-wide: `oc.dockSide` ("start" or "end") and `oc.dockBias` (a number 0..1 as text). Neither
 * has a profile id, so the profile deletion sweep leaves them alone, like `oc.appearance`.
 */
public class DockPlacementStore(private val store: AttentionKeyStore) {
    private val lock = Mutex()

    public suspend fun load(): DockPlacement =
        lock.withLock {
            val side = runCatching { store.read(DockKeys.SIDE) }.getOrNull()
            val bias = runCatching { store.read(DockKeys.BIAS) }.getOrNull()?.toFloatOrNull()
            DockPlacement.of(
                if (side == "start") DockSide.Start else DockSide.End,
                bias ?: DockPlacement.DEFAULT_BIAS,
            )
        }

    public suspend fun save(placement: DockPlacement): Boolean =
        lock.withLock {
            runCatching {
                store.write(DockKeys.SIDE, if (placement.side == DockSide.Start) "start" else "end") &&
                    store.write(DockKeys.BIAS, placement.bias.toString())
            }.getOrDefault(false)
        }
}

public sealed interface NicknameResult {
    /** Saved; [name] is the stored text, or null when the nickname was cleared. */
    public data class Saved(val name: String?) : NicknameResult

    /** The store refused; nothing changed. */
    public data object Refused : NicknameResult
}

/**
 * Nicknames for conversations in the dock. The default is the session title (not stored here), so a nickname exists
 * only when the person typed one. One JSON object per profile under [DockKeys.namesKey]; survives restart, goes with the
 * profile. Callers use it from one dispatcher; the writes are serialized here.
 */
public class DockNicknames(private val store: AttentionKeyStore) {
    private val lock = Mutex()
    private val cache = mutableMapOf<ProfileId, Map<String, String>>()

    /** Reads (once) and returns the saved nicknames of [profile]. Unreadable or malformed data reads as none. */
    public suspend fun load(profile: ProfileId): Map<SessionId, String> =
        lock.withLock { namesOf(profile).mapKeys { SessionId(it.key) } }

    /** What was last loaded or saved; null before [load]. */
    public fun nickname(ref: ConversationRef): String? = cache[ref.profileId]?.get(ref.sessionId.raw)

    /** Saves [raw] as the nickname after [normalize]; blank or null clears it. */
    public suspend fun set(ref: ConversationRef, raw: String?): NicknameResult =
        lock.withLock {
            val name = normalize(raw)
            val current = namesOf(ref.profileId)
            if (current[ref.sessionId.raw] == name) return@withLock NicknameResult.Saved(name)
            val next = if (name == null) current - ref.sessionId.raw else current + (ref.sessionId.raw to name)
            if (!write(ref.profileId, next)) return@withLock NicknameResult.Refused
            cache[ref.profileId] = next
            NicknameResult.Saved(name)
        }

    public suspend fun clear(ref: ConversationRef): NicknameResult = set(ref, null)

    /** Drops the in-memory copy after a profile is deleted; the sweep removes the key itself. */
    public fun forget(profile: ProfileId) {
        cache.remove(profile)
    }

    private suspend fun namesOf(profile: ProfileId): Map<String, String> =
        cache[profile] ?: read(profile).also { cache[profile] = it }

    private suspend fun read(profile: ProfileId): Map<String, String> {
        val raw = runCatching { store.read(DockKeys.namesKey(profile)) }.getOrNull() ?: return emptyMap()
        val obj = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
        return buildMap {
            for ((session, value) in obj) {
                val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                normalize(text)?.let { put(session, it) }
            }
        }
    }

    private suspend fun write(profile: ProfileId, names: Map<String, String>): Boolean =
        runCatching {
            if (names.isEmpty()) {
                store.remove(DockKeys.namesKey(profile))
            } else {
                store.write(DockKeys.namesKey(profile), JsonObject(names.mapValues { JsonPrimitive(it.value) }).toString())
            }
        }.getOrDefault(false)

    public companion object {
        /** A label under a ring is 11 px; longer names are cut at rest and shown whole in the panel. */
        public const val MAX_LENGTH: Int = 32

        /**
         * Trims, turns every run of whitespace or control characters into one space, and cuts to [MAX_LENGTH] without
         * splitting a surrogate pair. Blank becomes null (no nickname).
         */
        public fun normalize(raw: String?): String? {
            if (raw == null) return null
            val flat = StringBuilder()
            var gap = false
            for (c in raw) {
                if (c.isWhitespace() || c.isISOControl()) {
                    gap = flat.isNotEmpty()
                } else {
                    if (gap) flat.append(' ')
                    gap = false
                    flat.append(c)
                }
            }
            if (flat.isEmpty()) return null
            var end = minOf(flat.length, MAX_LENGTH)
            if (end < flat.length && flat[end - 1].isHighSurrogate()) end--
            return flat.substring(0, end).trimEnd().ifEmpty { null }
        }
    }
}
