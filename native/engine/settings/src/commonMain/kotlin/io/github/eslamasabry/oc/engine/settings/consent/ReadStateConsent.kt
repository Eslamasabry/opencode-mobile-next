package io.github.eslamasabry.oc.engine.settings.consent

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference

/** Where a profile's sharing answer came from (Flutter `SettingOriginKind`). */
public enum class ConsentOrigin { Default, Explicit, Migrated }

/** Captured before a read receipt is sent; the receipt may only be sent, or acknowledged, while it is still [ReadStateConsentStore.stillShared]. */
public class SharingTicket internal constructor(public val profileId: ProfileId, internal val revision: Long)

/**
 * "Share which sessions I viewed with the server" (B-SEC-142/143/164), per profile and private by default:
 * `oc.shareSessionViews.<id>` (bool), `…Origin.<id>` (`migrated`/`explicit`), `…Default.<id>` (bool) and the L3 journal
 * `oc.readStateConsentMigration.v1` = `{"version":1,"complete":<bool>,"ids":[…],"legacy":<bool>}`.
 *
 * One instance serves every profile, so every observer reads the same answer and the same [revision]. Sharing is never
 * reported on before the disk acknowledged it: while a change saves, the profile reads private; a failed opt-out stays
 * private; an unfinished migration grants nothing. This store never sends a receipt.
 */
public class ReadStateConsentStore(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
) : ConsentStore {

    private data class State(
        val closed: PersistentSet<String> = persistentHashSetOf(),
        /** Private whatever the disk says: a change is saving, failed, or the profile closed. */
        val private: PersistentSet<String> = persistentHashSetOf(),
        val blocked: PersistentSet<String> = persistentHashSetOf(),
        val pending: PersistentMap<String, Int> = persistentHashMapOf(),
        val generations: PersistentMap<String, Long> = persistentHashMapOf(),
        val migrationCompleted: Boolean = false,
    )

    private val state = AtomicReference(State())
    private val writes = Mutex()
    private val changes = Revision()
    private val log = AppLog.tagged("ReadStateConsent")

    override val topics: Set<ConsentTopic> = setOf(ConsentTopic.ReadStateSharing)
    override val revision: StateFlow<Long> = changes.flow

    override fun read(profileId: ProfileId, topic: ConsentTopic): ConsentRead = when {
        topic != ConsentTopic.ReadStateSharing -> ConsentRead.Unknown
        enabled(profileId) -> ConsentRead.Granted
        profileId.raw in state.load().private || origin(profileId) != ConsentOrigin.Default -> ConsentRead.Refused
        else -> ConsentRead.Unknown
    }

    /** True only for an acknowledged `true` with nothing saving, failed, blocked or closed. */
    public fun enabled(profileId: ProfileId): Boolean {
        val s = state.load()
        val id = profileId.raw
        if (id.isEmpty() || id in s.closed || id in s.private || id in s.blocked || (s.pending[id] ?: 0) > 0) return false
        return store.bool(ConsentKeys.shareViews(profileId)) == true
    }

    public fun saving(profileId: ProfileId): Boolean = (state.load().pending[profileId.raw] ?: 0) > 0

    public fun isClosed(profileId: ProfileId): Boolean = profileId.raw in state.load().closed

    /** Off for a new server; the carried-over value for one that existed before consent was per server. */
    public fun defaultFor(profileId: ProfileId): Boolean {
        store.bool(ConsentKeys.shareViewsDefault(profileId))?.let { return it }
        if (store.string(ConsentKeys.shareViewsOrigin(profileId)) == MIGRATED) return store.bool(ConsentKeys.shareViews(profileId)) == true
        return false
    }

    public fun origin(profileId: ProfileId): ConsentOrigin = when {
        store.string(ConsentKeys.shareViewsOrigin(profileId)) == MIGRATED -> ConsentOrigin.Migrated
        store.get(ConsentKeys.shareViews(profileId)) is PrefValue.Bool -> ConsentOrigin.Explicit
        else -> ConsentOrigin.Default
    }

    /** Null unless sharing is on now. A receipt sender checks [stillShared] before sending and again before acknowledging. */
    public fun ticket(profileId: ProfileId): SharingTicket? =
        if (enabled(profileId)) SharingTicket(profileId, revision.value) else null

    /** False after ANY change since the ticket (conservative: private wins every race). */
    public fun stillShared(ticket: SharingTicket): Boolean = revision.value == ticket.revision && enabled(ticket.profileId)

    /**
     * L3 (plan 05): copies the legacy app-wide value to the INITIAL saved population, journalled so a restart resumes
     * the same cohort. Missing legacy meant on; anything but `true` cannot grant. An explicit per-profile value wins.
     * Completion is committed last; until then nothing new is shared and a retry resumes.
     */
    public suspend fun migrateExistingProfiles(profileIds: Collection<ProfileId>): Outcome<Unit> {
        if (state.load().migrationCompleted) return Outcome.Ok(Unit)
        val present = profileIds.map { it.raw }.filter { it.isNotEmpty() }.toSet()
        mutate { s -> s.copy(pending = present.fold(s.pending) { m, id -> m.putting(id, (m[id] ?: 0) + 1) }) }
        try {
            return writes.withLock {
                when (val done = migrate(present)) {
                    is Outcome.Ok -> {
                        mutate { it.copy(migrationCompleted = true, blocked = persistentHashSetOf()) }
                        done
                    }
                    is Outcome.Failed -> {
                        log.w { "read-state migration incomplete; sharing stays off until it finishes" }
                        mutate { s -> s.copy(blocked = present.fold(s.blocked) { b, id -> b.adding(id) }) }
                        done
                    }
                }
            }
        } finally {
            mutate { s -> s.copy(pending = present.fold(s.pending) { m, id -> m.putting(id, (m[id] ?: 1) - 1) }) }
        }
    }

    public suspend fun setEnabled(profileId: ProfileId, value: Boolean): Outcome<Unit> = setEnabledIfCurrent(profileId, value) { true }

    /** [isCurrent] guards the caller's captured editor: a stale editor cannot authorise a replacement owner. */
    public suspend fun setEnabledIfCurrent(profileId: ProfileId, value: Boolean, isCurrent: () -> Boolean): Outcome<Unit> {
        val id = profileId.raw
        if (id.isEmpty() || isClosed(profileId) || !isCurrent()) return Outcome.Failed(stale())
        var generation = 0L
        // Even a failed opt-out stays private; an opt-in never authorises before the disk answered.
        mutate { s ->
            generation = (s.generations[id] ?: 0) + 1
            s.copy(
                generations = s.generations.putting(id, generation),
                pending = s.pending.putting(id, (s.pending[id] ?: 0) + 1),
                private = s.private.adding(id),
            )
        }
        try {
            return writes.withLock {
                if (isClosed(profileId) || !isCurrent()) return@withLock Outcome.Failed(stale())
                val keepDefault = store.get(ConsentKeys.shareViewsDefault(profileId)) == null
                val default = defaultFor(profileId)
                val written = store.commit(lanes.forProfile(profileId)) {
                    if (keepDefault) putBool(ConsentKeys.shareViewsDefault(profileId), default)
                    putString(ConsentKeys.shareViewsOrigin(profileId), EXPLICIT)
                    putBool(ConsentKeys.shareViews(profileId), value)
                }
                if (written is Outcome.Failed) {
                    val failure = written.failure
                    return@withLock Outcome.Failed(if (failure is Failure.Refused) stale() else failure)
                }
                if (isClosed(profileId) || !isCurrent()) {
                    // The write reached disk; erase its authorisation before a restart could read it.
                    if (value) store.commit(lanes.app) { putBool(ConsentKeys.shareViews(profileId), false) }
                    return@withLock Outcome.Failed(stale())
                }
                mutate { s -> if (s.generations[id] == generation) s.copy(private = s.private.removing(id)) else s }
                Outcome.Ok(Unit)
            }
        } finally {
            mutate { s -> s.copy(pending = s.pending.putting(id, (s.pending[id] ?: 1) - 1)) }
        }
    }

    override fun closeProfile(profileId: ProfileId) {
        val id = profileId.raw
        mutate { s ->
            s.copy(
                closed = s.closed.adding(id),
                private = s.private.adding(id),
                generations = s.generations.putting(id, (s.generations[id] ?: 0) + 1),
            )
        }
    }

    /** A failed deletion may reopen editing, but never restores automatic sharing: the profile stays private until set. */
    public fun reopenProfile(profileId: ProfileId) {
        mutate { s -> s.copy(closed = s.closed.removing(profileId.raw)) }
    }

    /**
     * Closes, drains every accepted write (a pending grant ends refused and erased), prunes the id from the journal,
     * then removes the three per-profile keys. A refused prune is retryable and never restores consent.
     */
    override suspend fun forgetProfile(profileId: ProfileId): Outcome<Unit> {
        closeProfile(profileId)
        return writes.withLock {
            when (val pruned = pruneJournal(profileId)) {
                is Outcome.Failed -> pruned.also { log.w { "read-state journal prune refused" } }
                is Outcome.Ok -> {
                    val keys = listOf(
                        ConsentKeys.shareViews(profileId),
                        ConsentKeys.shareViewsOrigin(profileId),
                        ConsentKeys.shareViewsDefault(profileId),
                    ).filter { store.get(it) != null }
                    if (keys.isEmpty()) Outcome.Ok(Unit) else store.commit(lanes.app) { keys.forEach(::remove) }
                }
            }
        }
    }

    // ---- internals

    private class Journal(val complete: Boolean, val ids: List<String>, val legacy: Boolean)

    /** Caller holds [writes]. */
    private suspend fun migrate(present: Set<String>): Outcome<Unit> {
        val raw = store.get(ConsentKeys.READ_STATE_MIGRATION)
        val existing = if (raw == null) null else parseJournal(raw) ?: return Outcome.Failed(unreadable())
        if (existing?.complete == true) return Outcome.Ok(Unit)
        val journal: Journal
        if (existing != null) {
            journal = existing
        } else {
            val legacyRaw = store.get(ConsentKeys.SHARE_VIEWS_LEGACY)
            // Missing used to mean on. Invalid data must not grant sharing.
            journal = Journal(false, present.sorted(), legacyRaw == null || legacyRaw == PrefValue.Bool(true))
            val started = store.commit(lanes.app) { putString(ConsentKeys.READ_STATE_MIGRATION, encode(journal)) }
            if (started is Outcome.Failed) return started
        }
        val closedNow = state.load().closed
        for (id in journal.ids) {
            if (id !in present || id in closedNow) continue
            val profileId = ProfileId(id)
            if (store.get(ConsentKeys.shareViews(profileId)) != null) continue // explicit destination wins
            val copied = store.commit(lanes.forProfile(profileId)) {
                putString(ConsentKeys.shareViewsOrigin(profileId), MIGRATED)
                putBool(ConsentKeys.shareViews(profileId), journal.legacy)
            }
            if (copied is Outcome.Failed) return copied
        }
        // Completion is committed last; a completed journal keeps no ids.
        return store.commit(lanes.app) {
            putString(ConsentKeys.READ_STATE_MIGRATION, encode(Journal(true, emptyList(), journal.legacy)))
        }
    }

    /** Caller holds [writes]. */
    private suspend fun pruneJournal(profileId: ProfileId): Outcome<Unit> {
        val raw = store.get(ConsentKeys.READ_STATE_MIGRATION) ?: return Outcome.Ok(Unit)
        val text = (raw as? PrefValue.Str)?.value ?: return Outcome.Ok(Unit)
        val data = DartJson.decodeOrNull(text) as? Map<*, *> ?: return Outcome.Failed(unreadable())
        val ids = data["ids"] as? List<*> ?: return Outcome.Failed(unreadable())
        if (profileId.raw !in ids) return Outcome.Ok(Unit)
        val next = LinkedHashMap<Any?, Any?>(data).apply { put("ids", ids.filter { it != profileId.raw }) }
        return store.commit(lanes.app) { putString(ConsentKeys.READ_STATE_MIGRATION, DartJson.encode(next)) }
    }

    private fun parseJournal(raw: PrefValue): Journal? {
        val text = (raw as? PrefValue.Str)?.value ?: return null
        val data = DartJson.decodeOrNull(text) as? Map<*, *> ?: return null
        if (!isVersionOne(data["version"])) return null
        val complete = data["complete"] as? Boolean ?: return null
        val ids = data["ids"] as? List<*> ?: return null
        if (ids.any { it !is String }) return null
        val legacy = data["legacy"] as? Boolean ?: return null
        return Journal(complete, ids.map { it as String }, legacy)
    }

    private fun encode(journal: Journal): String =
        DartJson.encode(linkedMapOf("version" to 1L, "complete" to journal.complete, "ids" to journal.ids, "legacy" to journal.legacy))

    private fun stale(): Failure = Failure.Refused(RefusalReason.StaleScope)

    private inline fun mutate(change: (State) -> State) {
        update(state, change)
        changes.bump()
    }

    private companion object {
        const val MIGRATED = "migrated"
        const val EXPLICIT = "explicit"
    }
}
