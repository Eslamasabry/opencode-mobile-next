package io.github.eslamasabry.oc.engine.settings.defaults

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.core.data.scope.DefaultProfileScope
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference

/**
 * Finds secrets in text before it is stored or announced (bound to `:core:security` `Redactor` in the app graph;
 * `:engine:settings` may not depend on it).
 */
public interface SecretFilter {
    public fun containsSecret(text: String): Boolean

    /** [text] with every known secret masked. */
    public fun mask(text: String): String

    public companion object {
        public val None: SecretFilter = object : SecretFilter {
            override fun containsSecret(text: String): Boolean = false

            override fun mask(text: String): String = text
        }
    }
}

/**
 * The remembered defaults of each saved server (Flutter `InteractionDefaultsStore`): the last project
 * (`oc.defaultProject.<profileId>`) and which automatic choices were already announced
 * (`oc.defaultNotices.<profileId>`, a string list of [DefaultKind.wire]). Both keys are swept with the profile.
 *
 * ONE owner per store and profile (B-SEC-057): every write for a profile runs one at a time in that profile's writer
 * lane, and is checked when asked and again when it starts. A profile that is not saved, or is closing, refuses
 * writes. [closeProfile] stops admission; [forgetProfile] drains the pending writes and removes both keys.
 */
public class DefaultNotices(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val secrets: SecretFilter = SecretFilter.None,
    /** Whether the profile is saved; by default it is looked up in `oc.profiles` like Dart did. */
    private val profileExists: (ProfileId) -> Boolean = { id -> savedProfileIds(store).contains(id.raw) },
) {
    private val locks = AtomicReference<PersistentMap<String, Mutex>>(persistentHashMapOf())

    /** The project opened last on this server; never a value that holds a secret. */
    public fun lastProjectId(profileId: ProfileId): String? =
        store.string(projectKey(profileId))?.takeUnless(secrets::containsSecret)

    /**
     * Call only after a project was opened or created. An empty or secret-bearing id is refused (a masked id could
     * target another project), and nothing is stored.
     */
    public suspend fun rememberProject(profileId: ProfileId, projectId: String): Outcome<Unit> {
        if (projectId.isEmpty() || secrets.containsSecret(projectId)) return invalid()
        return serialized(profileId) { lane ->
            store.commit(lane) { putString(projectKey(profileId), projectId) }
        }
    }

    /**
     * Claims the one announcement of [kind] for this server, at the visible point of use. Returns the masked label to
     * name in the notice, or null when there is nothing to say (an explicit or unresolved choice, or already said).
     * Concurrent claims are serialized: only one wins. A refused save returns the failure and does not consume the
     * notice.
     */
    public suspend fun takeAnnouncement(profileId: ProfileId, kind: DefaultKind, choice: DefaultChoice<*>): Outcome<String?> {
        if (!choice.isAutomatic) return serialized(profileId) { Outcome.Ok(null) }
        return serialized(profileId) { lane ->
            val seen = store.stringList(noticesKey(profileId)).orEmpty()
            if (kind.wire in seen) return@serialized Outcome.Ok(null)
            when (val saved = store.commit(lane) { putStringList(noticesKey(profileId), seen + kind.wire) }) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> Outcome.Ok(secrets.mask(choice.label))
            }
        }
    }

    /** Synchronous first step of a profile close or delete: writes for [profileId] are refused from now on. */
    public fun closeProfile(profileId: ProfileId) {
        lanes.closeProfile(profileId)
    }

    /** Drains pending writes, then removes both keys in one commit (app lane). Idempotent. */
    public suspend fun forgetProfile(profileId: ProfileId): Outcome<Unit> {
        lanes.closeProfile(profileId)
        lanes.drainProfile(profileId)
        lockFor(profileId).withLock { }
        val present = listOf(projectKey(profileId), noticesKey(profileId)).filter { store.get(it) != null }
        if (present.isEmpty()) return Outcome.Ok(Unit)
        return store.commit(lanes.app) { present.forEach(::remove) }
    }

    private suspend fun <T> serialized(
        profileId: ProfileId,
        action: suspend (io.github.eslamasabry.oc.core.data.lane.WriterLane) -> Outcome<T>,
    ): Outcome<T> {
        admission(profileId)?.let { return Outcome.Failed(it) }
        return lockFor(profileId).withLock {
            admission(profileId)?.let { return@withLock Outcome.Failed(it) }
            action(lanes.forProfile(profileId))
        }
    }

    private fun admission(profileId: ProfileId): Failure? = when {
        profileId.raw.isEmpty() || secrets.containsSecret(profileId.raw) -> Failure.Storage(StorageProblem.InvalidValue)
        !lanes.forProfile(profileId).isAdmitting -> Failure.Refused(RefusalReason.ProfileClosing)
        !profileExists(profileId) -> Failure.Refused(RefusalReason.ProfileDeleted)
        else -> null
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
        public const val PROJECT_PREFIX: String = "oc.defaultProject."
        public const val NOTICES_PREFIX: String = "oc.defaultNotices."

        public val SHAPES: List<KeyShape> = listOf(
            KeyShape(PROJECT_PREFIX, KeyScope.Profile, PrefType.Str, ":engine:settings", "last project per server"),
            KeyShape(NOTICES_PREFIX, KeyScope.Profile, PrefType.StrList, ":engine:settings", "default notices already said"),
        )

        public fun projectKey(profileId: ProfileId): String = PROJECT_PREFIX + profileId.raw

        public fun noticesKey(profileId: ProfileId): String = NOTICES_PREFIX + profileId.raw

        private fun <T> invalid(): Outcome<T> = Outcome.Failed(Failure.Storage(StorageProblem.InvalidValue))

        private fun savedProfileIds(store: KeyValueStore): Set<String> {
            val rows = store.string(DefaultProfileScope.PROFILES_KEY)?.let(DartJson::decodeOrNull) as? List<*> ?: return emptySet()
            return rows.mapNotNullTo(LinkedHashSet()) { (it as? Map<*, *>)?.get("id") as? String }
        }
    }
}
