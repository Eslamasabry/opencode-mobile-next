package io.github.eslamasabry.oc.engine.settings.teaching

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant

/** The one-time in-context tips (UX plan 5.8 item 4). [wire] is the stable storage name. */
public enum class NudgeId(public val wire: String) {
    /** Third permission request of one kind in a conversation. */
    Approvals("approvals"),

    /** A run finished after changing files. */
    ReviewChanges("review-changes"),

    /** A run kept the person waiting while finished-run notifications can reach them. */
    LeaveAndBeTold("leave"),

    /** The conversation fills most of the model's context window. */
    Compact("compact"),

    /** A second project has been used. */
    PinConversations("pin"),
    ;

    public companion object {
        public fun fromWire(value: Any?): NudgeId? = entries.firstOrNull { it.wire == value }
    }
}

/** [dismissed]: the person closed the card or took its action (a card whose moment passed stays false). */
public data class NudgeRecord(val id: NudgeId, val shownAt: Instant, val dismissed: Boolean = false)

/** The nudge on screen and the place it belongs to (a conversation id or [NudgeRegistry.WORK_SCOPE]). */
public data class ActiveNudge(val id: NudgeId, val scope: String, val detail: String = "")

/**
 * Which nudges were shown, and the single card slot (B-NAV-038). Every rule lives here:
 * - a nudge is offered at most once ever (until [reset]);
 * - one nudge is active app-wide; an offer made while another shows is refused WITHOUT being consumed;
 * - nothing is offered before the first reply.
 *
 * Stored app-wide (Flutter keys unchanged): `oc.nudges.shown` = `{"<wire>":{"shownAt":"<ISO>","dismissed":<bool>}}`,
 * `oc.nudges.firstReplySeen` (bool), `oc.nudges.projects` (≤ 2 truncated digests, never a path). Unreadable state
 * means nothing was shown. One instance for the app; the slot is not shared between instances.
 */
public class NudgeRegistry(
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val records = AtomicReference<PersistentMap<NudgeId, NudgeRecord>?>(null)
    private val mutableActive = MutableStateFlow<ActiveNudge?>(null)
    private val writes = Mutex()
    private val log = AppLog.tagged("Nudges")

    /** Request ids seen per conversation and permission kind, for this app run only. */
    private val permissionRequests = AtomicReference<PersistentMap<String, PersistentMap<String, Set<String>>>>(persistentMapOf())

    public val active: StateFlow<ActiveNudge?> = mutableActive.asStateFlow()

    /** The nudge to render at [place], or null: a nudge renders only where it was offered. */
    public fun activeFor(place: String): ActiveNudge? = mutableActive.value?.takeIf { it.scope == place }

    public fun record(id: NudgeId): NudgeRecord? = loaded()[id]

    public fun wasShown(id: NudgeId): Boolean = loaded().containsKey(id)

    public val firstReplySeen: Boolean get() = store.bool(FIRST_REPLY_SEEN_KEY) ?: false

    public suspend fun markFirstReplySeen(): Outcome<Unit> =
        if (firstReplySeen) Outcome.Ok(Unit) else store.commit(lanes.app) { putBool(FIRST_REPLY_SEEN_KEY, true) }

    /** Shows [id] at [place] when the rules allow; a refusal (slot taken, no first reply yet) consumes nothing. */
    public fun offer(id: NudgeId, place: String, detail: String = ""): Boolean {
        if (!firstReplySeen) return false
        if (mutableActive.value != null) return false
        if (loaded().containsKey(id)) return false
        val next = ActiveNudge(id, place, detail)
        if (!mutableActive.compareAndSet(null, next)) return false
        while (true) {
            val current = loaded()
            if (current.containsKey(id)) {
                mutableActive.compareAndSet(next, null)
                return false
            }
            if (records.compareAndSet(current, current.putting(id, NudgeRecord(id, clock.now())))) break
        }
        // The in-memory record already prevents a second showing in this run.
        persist()
        return true
    }

    /** The person closed the card or took its action. */
    public suspend fun dismiss(id: NudgeId): Outcome<Unit> {
        val current = loaded()
        val record = current[id]
        if (mutableActive.value?.id == id) mutableActive.value = null
        if (record == null || record.dismissed) return Outcome.Ok(Unit)
        updateRecords { it.putting(id, record.copy(dismissed = true)) }
        return save()
    }

    /** The moment passed: the card leaves without being dismissed. It still counts as shown. */
    public fun release(id: NudgeId) {
        mutableActive.compareAndSet(mutableActive.value?.takeIf { it.id == id } ?: return, null)
    }

    /** Releases whatever shows at [place]; called when that place closes so an unseen card cannot block every nudge. */
    public fun releaseScope(place: String) {
        mutableActive.value?.takeIf { it.scope == place }?.let { release(it.id) }
    }

    /** "Show tips again". The first-reply flag, the project count and this run's permission counts stay. */
    public suspend fun reset(): Outcome<Unit> {
        records.store(persistentMapOf())
        mutableActive.value = null
        return writes.withLock { store.commit(lanes.app) { remove(SHOWN_KEY) } }
    }

    /** Counts one request by id (a rebuilt card is not a new request); returns the distinct count of [kind] here. */
    public fun notePermissionRequest(conversation: String, kind: String, requestId: String): Int {
        while (true) {
            val all = permissionRequests.load()
            val byKind = all[conversation] ?: persistentMapOf()
            val ids = (byKind[kind] ?: emptySet()) + requestId
            if (permissionRequests.compareAndSet(all, all.putting(conversation, byKind.putting(kind, ids)))) return ids.size
        }
    }

    /** The most-repeated kind in [conversation] that reached [PERMISSION_REPEAT_THRESHOLD], or null. */
    public fun repeatedPermissionKind(conversation: String): String? {
        var kind: String? = null
        var most = PERMISSION_REPEAT_THRESHOLD - 1
        for ((k, ids) in permissionRequests.load()[conversation].orEmpty()) {
            if (ids.size > most) {
                most = ids.size
                kind = k
            }
        }
        return kind
    }

    /** Distinct projects used, capped at two: the only question is "is this the second one?". */
    public val projectsUsed: Int get() = projects().size
    public val secondProjectUsed: Boolean get() = projectsUsed >= 2

    /** Remembers that a project was used. Only a 16-hex-digit digest is stored: nothing readable. */
    public suspend fun noteProjectUsed(profileId: String, directory: String): Outcome<Unit> {
        if (profileId.isEmpty() || directory.isEmpty()) return Outcome.Ok(Unit)
        return writes.withLock {
            val seen = projects()
            val digest = DartJson.sha256Hex("$profileId\n$directory").substring(0, 16)
            if (seen.size >= 2 || digest in seen) Outcome.Ok(Unit) else store.commit(lanes.app) { putStringList(PROJECTS_KEY, seen + digest) }
        }
    }

    // ---- internals

    private fun projects(): List<String> = store.stringList(PROJECTS_KEY).orEmpty()

    private fun persist() {
        scope.launch { save() }
    }

    private suspend fun save(): Outcome<Unit> = writes.withLock {
        val records = loaded()
        // Nothing shown is stored as no key (so a write queued before a reset cannot leave `{}` behind).
        store.commit(lanes.app) { if (records.isEmpty()) remove(SHOWN_KEY) else putString(SHOWN_KEY, encode(records)) }
            .also { if (it is Outcome.Failed) log.w { "nudge record not saved; a nudge may repeat after a restart" } }
    }

    private inline fun updateRecords(change: (PersistentMap<NudgeId, NudgeRecord>) -> PersistentMap<NudgeId, NudgeRecord>) {
        while (true) {
            val current = loaded()
            if (records.compareAndSet(current, change(current))) return
        }
    }

    private fun loaded(): PersistentMap<NudgeId, NudgeRecord> {
        records.load()?.let { return it }
        records.compareAndSet(null, decode(store.string(SHOWN_KEY)))
        return records.load()!!
    }

    public companion object {
        public const val SHOWN_KEY: String = "oc.nudges.shown"
        public const val FIRST_REPLY_SEEN_KEY: String = "oc.nudges.firstReplySeen"
        public const val PROJECTS_KEY: String = "oc.nudges.projects"

        /** [ActiveNudge.scope] of the Work tab. */
        public const val WORK_SCOPE: String = "work"

        /** A third request of one kind earns the approvals tip. */
        public const val PERMISSION_REPEAT_THRESHOLD: Int = 3

        internal fun decode(text: String?): PersistentMap<NudgeId, NudgeRecord> {
            val value = text?.let(DartJson::decodeOrNull) as? Map<*, *> ?: return persistentMapOf()
            var out = persistentMapOf<NudgeId, NudgeRecord>()
            for ((key, data) in value) {
                val id = NudgeId.fromWire(key) ?: continue
                if (data !is Map<*, *>) continue
                val shownAt = (data["shownAt"] as? String)?.let(Instant::parseOrNull) ?: continue
                out = out.putting(id, NudgeRecord(id, shownAt, data["dismissed"] == true))
            }
            return out
        }

        internal fun encode(records: Map<NudgeId, NudgeRecord>): String =
            DartJson.encode(
                records.values.associateTo(LinkedHashMap()) { r ->
                    r.id.wire to linkedMapOf<String, Any>(
                        "shownAt" to DartJson.iso8601Utc(r.shownAt.epochSeconds * 1_000_000 + r.shownAt.nanosecondsOfSecond / 1_000),
                        "dismissed" to r.dismissed,
                    )
                },
            )
    }
}
