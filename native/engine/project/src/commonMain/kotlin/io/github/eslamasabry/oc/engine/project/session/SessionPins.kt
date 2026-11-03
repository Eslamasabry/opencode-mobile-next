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
import io.github.eslamasabry.oc.core.model.ServerLocation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Port of lib/state/session_pins.dart.

/** The selected location a pin belongs to: Dart `jsonEncode([directory, workspace])`, kept byte-exact on disk. */
public data class PinScope(val raw: String) {
    public companion object {
        public fun of(location: ServerLocation): PinScope = of(location.directory, location.workspace)

        public fun of(directory: String?, workspace: String?): PinScope = PinScope(DartJson.encode(listOf(directory, workspace)))
    }
}

/**
 * Device-only favourite conversations under `oc.sessionPins.<profileId>`, isolated by profile and location. Writes for
 * one profile run one after another (each builds on the previous acknowledged set), and a pin shows only after the
 * store acknowledged it: a refused commit leaves the shown set as it was, and a later write still succeeds.
 */
public class SessionPinStore(private val store: KeyValueStore, private val lane: WriterLane) {
    private val cache = HashMap<ProfileId, Map<PinScope, Set<SessionId>>>()
    private val locks = HashMap<ProfileId, Mutex>()

    public fun ids(profile: ProfileId, scope: PinScope): Set<SessionId> = load(profile)[scope] ?: emptySet()

    public suspend fun setPinned(profile: ProfileId, scope: PinScope, id: SessionId, pinned: Boolean): Outcome<Unit> =
        lockFor(profile).withLock {
            val next = LinkedHashMap<PinScope, LinkedHashSet<SessionId>>()
            for ((s, ids) in load(profile)) next[s] = LinkedHashSet(ids)
            val entries = next.getOrPut(scope) { LinkedHashSet() }
            if (pinned) entries += id else entries -= id
            if (entries.isEmpty()) next.remove(scope)
            val encoded = DartJson.encode(next.entries.associate { (s, ids) -> s.raw to ids.map { it.raw } })
            when (val saved = store.commit(lane) { putString(keyFor(profile), encoded) }) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> {
                    // Every published set is an immutable snapshot, replaced only after the store acknowledged it.
                    cache[profile] = next.mapValues { (_, ids) -> ids.toSet() }
                    Outcome.Ok(Unit)
                }
            }
        }

    /** After the profile sweep: drop the in-memory copy so nothing of the deleted profile is shown again. */
    public fun forget(profile: ProfileId) {
        cache.remove(profile)
    }

    private fun lockFor(profile: ProfileId): Mutex = locks.getOrPut(profile) { Mutex() }

    private fun load(profile: ProfileId): Map<PinScope, Set<SessionId>> =
        cache.getOrPut(profile) {
            val decoded = store.string(keyFor(profile))?.let(DartJson::decodeOrNull) as? Map<*, *> ?: return@getOrPut emptyMap()
            buildMap {
                for ((k, v) in decoded) {
                    if (k is String && v is List<*>) {
                        put(PinScope(k), v.filterIsInstance<String>().filter { it.isNotEmpty() }.mapTo(LinkedHashSet(), ::SessionId))
                    }
                }
            }
        }

    public companion object {
        public fun keyFor(profile: ProfileId): String = "oc.sessionPins.${profile.raw}"

        public val SHAPE: KeyShape =
            KeyShape("oc.sessionPins.", KeyScope.Profile, PrefType.Str, ":engine:project", "pinned conversations per location")
    }
}
