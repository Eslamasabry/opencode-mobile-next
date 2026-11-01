package io.github.eslamasabry.oc.engine.settings.scope

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference

/**
 * Per-chat agent overrides for servers that do not own the session selection (OC1; frozen contract "OC1 chat-agent
 * correction", Flutter `ProfileStore.sessionAgentsFor` / `_changeSessionAgents`). Stored in
 * `oc.sessionAgents.<profileId>` as a JSON object session id → agent, newest last, at most [CAP] entries, swept with
 * the profile. The profile default `oc.agent.<profileId>` is never touched.
 *
 * - A choice is visible only after storage acknowledged it; a refused save keeps the previous choice (B-STATE-129).
 * - Writes for one profile run one at a time, in that profile's writer lane; after [closeProfile] they are refused
 *   with a stale-target failure, and a write that was already running is reported stale and never resurrected:
 *   [forgetProfile] drains it before removing the key.
 */
public class ChatAgentChoices(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
) {
    private val locks = AtomicReference<PersistentMap<String, Mutex>>(persistentHashMapOf())
    private val mutableRevision = MutableStateFlow(0L)

    /** Moves after every acknowledged change (watchers re-read [choicesFor]). */
    public val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    /** The acknowledged overrides of [profileId]; malformed entries are dropped, never surfaced. */
    public fun choicesFor(profileId: ProfileId): ImmutableMap<String, String> = decode(store.string(key(profileId)))

    /** The chat's own agent, or null when it follows the profile default. */
    public fun agentFor(profileId: ProfileId, sessionId: String): String? = choicesFor(profileId)[sessionId]

    /** Sets (or with null clears) one chat's agent. */
    public suspend fun set(profileId: ProfileId, sessionId: String, agent: String?): Outcome<Unit> =
        change(profileId) { choices ->
            choices.remove(sessionId)
            if (agent != null) choices[sessionId] = agent
        }

    /** Replaces every override of the profile at once. */
    public suspend fun setAll(profileId: ProfileId, choices: Map<String, String>): Outcome<Unit> {
        val snapshot = LinkedHashMap(choices)
        return change(profileId) { it.clear(); it.putAll(snapshot) }
    }

    /** A deleted chat drops only its own override. */
    public suspend fun forgetSession(profileId: ProfileId, sessionId: String): Outcome<Unit> = set(profileId, sessionId, null)

    /** Synchronous first step of a profile close or delete: new writes for [profileId] are refused from now on. */
    public fun closeProfile(profileId: ProfileId) {
        lanes.closeProfile(profileId)
    }

    /**
     * Drains the profile's pending writes, then removes its overrides (on the app lane: the profile lane is closed).
     * Idempotent; a refused removal returns the storage failure and the key stays.
     */
    public suspend fun forgetProfile(profileId: ProfileId): Outcome<Unit> {
        lanes.closeProfile(profileId)
        lanes.drainProfile(profileId)
        lockFor(profileId).withLock { }
        if (store.get(key(profileId)) == null) return Outcome.Ok(Unit)
        val result = store.commit(lanes.app) { remove(key(profileId)) }
        if (result is Outcome.Ok) mutableRevision.value += 1
        return result
    }

    private suspend fun change(profileId: ProfileId, update: (LinkedHashMap<String, String>) -> Unit): Outcome<Unit> {
        val lane = lanes.forProfile(profileId)
        if (!lane.isAdmitting) return SettingsFailureReason.StaleTarget.failed()
        return lockFor(profileId).withLock {
            if (!lane.isAdmitting) return@withLock SettingsFailureReason.StaleTarget.failed()
            val choices = LinkedHashMap(choicesFor(profileId))
            update(choices)
            val kept = choices.entries.toList().takeLast(CAP)
            val encoded = kept.associateTo(LinkedHashMap()) { it.key to it.value }
            val saved = store.commit(lane) {
                if (encoded.isEmpty()) remove(key(profileId)) else putString(key(profileId), DartJson.encode(encoded))
            }
            when {
                saved is Outcome.Failed -> saved.failure.settingsReason()
                    ?.takeIf { it == SettingsFailureReason.StaleTarget }?.failed()
                    ?: SettingsFailureReason.StorageFailed.failed()
                // Acknowledged, but the profile closed meanwhile: the deletion drains this write and sweeps it.
                !lane.isAdmitting -> SettingsFailureReason.StaleTarget.failed()
                else -> Outcome.Ok(Unit).also { mutableRevision.value += 1 }
            }
        }
    }

    private fun lockFor(profileId: ProfileId): Mutex {
        while (true) {
            val current = locks.load()
            current[profileId.raw]?.let { return it }
            val lock = Mutex()
            if (locks.compareAndSet(current, current.putting(profileId.raw, lock))) return lock
        }
    }

    public companion object {
        public const val PREFIX: String = "oc.sessionAgents."
        public const val CAP: Int = 200

        public val SHAPE: KeyShape =
            KeyShape(PREFIX, KeyScope.Profile, PrefType.Str, ":engine:settings", "per-chat agent overrides (OC1)")

        public fun key(profileId: ProfileId): String = PREFIX + profileId.raw

        private fun decode(raw: String?): ImmutableMap<String, String> {
            val decoded = raw?.let(DartJson::decodeOrNull) as? Map<*, *> ?: return persistentHashMapOf()
            val entries = decoded.entries.toList().takeLast(CAP)
            val result = LinkedHashMap<String, String>()
            for ((k, v) in entries) if (k is String && v is String) result[k] = v
            return result.toImmutableMap()
        }
    }
}
