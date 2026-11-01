package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The string preferences the brief store writes through (bound over `:core:data`'s prefs store by the app graph).
 * [write] returns false when the platform refused the commit.
 */
public interface BriefPreferences {
    public suspend fun read(key: String): String?

    public suspend fun write(key: String, value: String): Boolean

    public suspend fun remove(key: String)
}

/**
 * Exact local dismissals of the return brief (Dart `ReturnBriefStore`), one blob per profile under
 * `oc.returnBrief.<profileId>` (ProfileScope-registered by the profiles owner), a map of location → ack. A refused write
 * never publishes an acknowledgement; writes for one profile run one at a time and merge; profile deletion drains the
 * queue, then clears both copies (B-STATE-082, 083).
 */
public class ReturnBriefStore(private val prefs: BriefPreferences) {
    private val loaded = mutableMapOf<ProfileId, Map<String, ReturnBriefAck>>()
    private val lanes = mutableMapOf<ProfileId, Mutex>()

    private fun lane(profile: ProfileId): Mutex = lanes.getOrPut(profile) { Mutex() }

    private suspend fun load(profile: ProfileId): Map<String, ReturnBriefAck> =
        loaded[profile] ?: decode(prefs.read(key(profile))).also { loaded[profile] = it }

    public suspend fun acknowledged(profile: ProfileId, location: String): ReturnBriefAck =
        load(profile)[location] ?: ReturnBriefAck.Empty

    /** Acknowledges exactly the identities [shown] holds now, even if rows arrive while the save waits. */
    public suspend fun acknowledge(profile: ProfileId, location: String, shown: ReturnBrief): Outcome<Unit> {
        val snapshot = shown.acknowledgement(null)
        return lane(profile).withLock {
            val entries = LinkedHashMap(load(profile))
            val ack = (entries.remove(location) ?: ReturnBriefAck.Empty).merge(snapshot.runs, snapshot.requestIds)
            entries[location] = ack
            while (entries.size > MAX_LOCATIONS) entries.remove(entries.keys.first())
            val blob = JsonObject(entries.mapValues { it.value.toJson() }).toString()
            if (prefs.write(key(profile), blob)) {
                loaded[profile] = entries
                Unit.ok()
            } else {
                Failure.StorageRefused().failed()
            }
        }
    }

    /** Waits for queued dismissals of [profile], then clears the stored and in-memory acknowledgements. */
    public suspend fun deleteProfile(profile: ProfileId) {
        lane(profile).withLock {
            prefs.remove(key(profile))
            loaded.remove(profile)
        }
    }

    private fun decode(raw: String?): Map<String, ReturnBriefAck> {
        if (raw == null) return emptyMap()
        val obj = runCatchingJson(raw) as? JsonObject ?: return emptyMap()
        return obj.entries.take(MAX_LOCATIONS)
            .mapNotNull { (location, value) -> ReturnBriefAck.fromJson(value)?.let { location to it } }
            .toMap()
    }

    private fun runCatchingJson(raw: String) =
        try {
            Json.parseToJsonElement(raw)
        } catch (_: IllegalArgumentException) {
            null // corrupt data means never dismissed
        }

    public companion object {
        public const val PREFIX: String = "oc.returnBrief."
        public const val MAX_LOCATIONS: Int = 64

        public fun key(profile: ProfileId): String = PREFIX + profile.raw
    }
}
