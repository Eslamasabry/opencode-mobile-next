package io.github.eslamasabry.oc.engine.attention.activity

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Instant

/** Completed automatic actions, never requests for the person to answer (Flutter `AutomaticActKind`). */
public enum class AutomaticActKind {
    Reconnect, Restart, HeatPause, HeatStop, HeatResume, Update, PermissionApproval, QueuedSend, Other;

    /** The persisted name, as Flutter's `kind.name` (lower camel case). */
    public val wire: String get() = name.replaceFirstChar { it.lowercaseChar() }

    public companion object {
        public fun fromWire(value: String): AutomaticActKind? = entries.firstOrNull { it.wire == value }
    }
}

/** One observed automatic action within a profile's location. [summary] is already redacted and bounded. */
public data class AutomaticAct(
    val id: String,
    val locationKey: String,
    val kind: AutomaticActKind,
    val summary: String,
    val occurredAt: Instant,
    val sessionId: String? = null,
    val acknowledged: Boolean = false,
    val undoAttempted: Boolean = false,
    val undone: Boolean = false,
)

/** No exception text leaves an inverse operation. An uncertain attempt is never retried, even after process death. */
public enum class AutomaticUndoResult { Unavailable, Undone, Failed, Unconfirmed }

/**
 * App-private key/value storage for the activity history (SharedPreferences on Android). [write] and [remove] return
 * false when the platform refused; they may also throw. Both are treated as "not saved".
 */
public interface AttentionKeyStore {
    public suspend fun read(key: String): String?

    public suspend fun write(key: String, value: String): Boolean

    public suspend fun remove(key: String): Boolean
}

/** What the Inbox reads: newest first, read-only. */
public data class AutomaticActivityState(
    val acts: ImmutableList<AutomaticAct> = persistentListOf(),
    val persistenceFailed: Boolean = false,
    val corruptHistory: Boolean = false,
    val undoing: ImmutableSet<String> = persistentSetOf(),
    /** Ids whose Undo is offered in this process (callbacks are memory-only). */
    val undoable: ImmutableSet<String> = persistentSetOf(),
)

/**
 * Local history of observed automatic acts; never initiates automation (port of `AutomaticActivityController`,
 * B-STATE-006/007, B-SEC-015/016, B-AGENT-030). One instance per profile ([AutomaticActivityRegistry]). Event ids
 * identify occurrences: a repeated delivery deduplicates, a new act does not. Only the newest [MAX_ENTRIES] are kept.
 * Undo callbacks are memory-only, offered once, never reconstructed on restart, and need a durable attempt marker
 * first. Commands run on one serial lane; admission is decided when the command is called.
 */
public class AutomaticActivity private constructor(
    private val store: AttentionKeyStore,
    private val redactor: Redactor,
    public val profileId: String,
    private val isProfilePresent: () -> Boolean,
) {
    public val storageKey: String = keyFor(profileId)

    private val lane = Mutex()
    private val inverses = mutableMapOf<String, suspend () -> Boolean>()
    private val pendingUndo = mutableSetOf<String>()
    private var acts: List<AutomaticAct> = emptyList()
    private var persistenceFailed = false
    private var corruptHistory = false
    private val disposed = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val preparingDeletion = AtomicBoolean(false)

    private val mutableState = MutableStateFlow(AutomaticActivityState())
    public val state: StateFlow<AutomaticActivityState> = mutableState.asStateFlow()

    public val currentActs: ImmutableList<AutomaticAct> get() = acts.toImmutableList()

    private val available: Boolean get() = !disposed.load() && !closed.load() && isProfilePresent()
    private val accepting: Boolean get() = available && !preparingDeletion.load()

    /** Avoids persisting directory names or URLs (which can carry credentials). */
    public fun locationKey(location: String): String = DartJson.sha256Hex(location)

    public fun forLocation(location: String): ImmutableList<AutomaticAct> {
        val key = locationKey(location)
        return acts.filter { it.locationKey == key }.toImmutableList()
    }

    /**
     * False means the observation could not be durably recorded; the caller may retry this SAME event id and must
     * never repeat the original act to retry a write. [undo] returns true only after confirming the inverse.
     */
    public suspend fun record(
        eventId: String,
        location: String,
        kind: AutomaticActKind,
        summary: String,
        occurredAt: Instant,
        sessionId: String? = null,
        undo: (suspend () -> Boolean)? = null,
    ): Boolean {
        if (eventId.isEmpty() || occurredAt.toEpochMilliseconds() <= 0) return false
        val safeSummary = summaryOf(summary)
        if (safeSummary.isEmpty()) return false
        val id = DartJson.sha256Hex(DartJson.encode(listOf(location, eventId)))
        val safeSession =
            if (sessionId.isNullOrEmpty() || sessionId.length > 256 || redactor.containsSecret(sessionId)) null
            else redactor.redact(sessionId)
        val act = AutomaticAct(id, locationKey(location), kind, safeSummary, occurredAt, safeSession)
        return enqueue(false) {
            if (acts.any { it.id == id }) return@enqueue true
            if (!save(bounded(acts + act))) return@enqueue false
            if (undo != null && available && acts.any { it.id == id }) {
                inverses[id] = undo
                publish()
            }
            true
        }
    }

    /** Acknowledges only these ids, captured before entering the lane. Hides history; never marks work read. */
    public suspend fun acknowledge(ids: Iterable<String>): Boolean {
        val shown = ids.toSet()
        return enqueue(false) {
            if (acts.none { it.id in shown && !it.acknowledged }) return@enqueue true
            save(acts.map { if (it.id in shown) it.copy(acknowledged = true) else it })
        }
    }

    public fun canUndo(id: String): Boolean =
        accepting && id !in pendingUndo && inverses.containsKey(id) && acts.any { it.id == id && !it.undoAttempted }

    public suspend fun undo(id: String): AutomaticUndoResult {
        if (!canUndo(id)) return AutomaticUndoResult.Unavailable
        pendingUndo += id
        publish()
        try {
            return enqueue(AutomaticUndoResult.Unavailable) {
                val inverse = inverses[id]
                if (inverse == null || acts.none { it.id == id && !it.undoAttempted }) {
                    return@enqueue AutomaticUndoResult.Unavailable
                }
                // Commit intent first: a crash after the remote effect cannot offer the inverse again.
                if (!save(acts.map { if (it.id == id) it.copy(undoAttempted = true) else it })) {
                    return@enqueue AutomaticUndoResult.Failed
                }
                if (!available) return@enqueue AutomaticUndoResult.Unavailable
                inverses.remove(id)
                when (val result = attempt({ Failure.Unexpected() }) { inverse() }) {
                    is Outcome.Failed -> return@enqueue AutomaticUndoResult.Unconfirmed
                    is Outcome.Ok -> if (!result.value) return@enqueue AutomaticUndoResult.Failed
                }
                if (!available) return@enqueue AutomaticUndoResult.Unconfirmed
                val saved = save(acts.map { if (it.id == id) it.copy(undone = true) else it })
                if (saved) AutomaticUndoResult.Undone else AutomaticUndoResult.Unconfirmed
            }
        } finally {
            pendingUndo -= id
            publish()
        }
    }

    /**
     * Pauses new records, acknowledgements and Undo at once, then drains already admitted writes and inverses without
     * erasing history or callbacks. Use [cancelDeletion] if the deletion aborts.
     */
    public suspend fun prepareForDeletion() {
        if (!closed.load() && !disposed.load()) {
            preparingDeletion.store(true)
            publish()
        }
        lane.withLock { }
    }

    /** Resumes the same owner after reversible deletion preparation. Erasure or disposal cannot be cancelled. */
    public fun cancelDeletion() {
        if (!preparingDeletion.load() || closed.load() || disposed.load()) return
        preparingDeletion.store(false)
        publish()
    }

    /**
     * Closes first, drains in-flight writes and inverses, then removes the key. Call only in committed deletion. False
     * is retryable; the history stays closed even when the platform refused removal.
     */
    public suspend fun deleteProfileData(): Boolean {
        closed.store(true)
        inverses.clear()
        acts = emptyList()
        publish()
        return lane.withLock {
            val removed = attempt({ Failure.StorageRefused() }) { store.remove(storageKey) }
            val ok = removed is Outcome.Ok && removed.value
            persistenceFailed = !ok
            publish()
            ok
        }
    }

    /** Stops writes and notifications without deleting history. */
    public fun dispose() {
        disposed.store(true)
        inverses.clear()
    }

    private suspend fun <T> enqueue(unavailable: T, action: suspend () -> T): T {
        if (!accepting) return unavailable
        return lane.withLock { if (!available) unavailable else action() }
    }

    private suspend fun save(next: List<AutomaticAct>): Boolean {
        if (!available) return false
        val blob = encode(next)
        val written = attempt({ Failure.StorageRefused() }) { store.write(storageKey, blob) }
        if (written !is Outcome.Ok || !written.value) {
            persistenceFailed = true
            publish()
            return false
        }
        // A profile removed while a platform write was pending cannot be recreated by that late write.
        if (!isProfilePresent()) {
            val removed = attempt({ Failure.StorageRefused() }) { store.remove(storageKey) }
            if (removed !is Outcome.Ok || !removed.value) {
                persistenceFailed = true
                publish()
            }
            return false
        }
        if (closed.load() || disposed.load()) return false
        acts = next.toImmutableList()
        inverses.keys.retainAll(acts.map { it.id }.toSet())
        persistenceFailed = false
        publish()
        return true
    }

    private fun publish() {
        if (disposed.load()) return
        mutableState.value =
            AutomaticActivityState(
                acts = acts.toImmutableList(),
                persistenceFailed = persistenceFailed,
                corruptHistory = corruptHistory,
                undoing = pendingUndo.toImmutableSet(),
                undoable = acts.map { it.id }.filter { canUndo(it) }.toImmutableSet(),
            )
    }

    private fun summaryOf(value: String): String {
        // Redact BEFORE flattening/truncation: headers and multiline secrets depend on their original boundaries.
        val safe = redactor.redact(value).replace(FLATTEN, " ").trim()
        return takeCodePoints(safe, SUMMARY_LIMIT)
    }

    private fun encode(next: List<AutomaticAct>): String =
        buildJsonObject {
            put("version", 1)
            putJsonArray("acts") {
                for (act in next) addJsonObject {
                    put("id", redactor.redact(act.id))
                    put("location", redactor.redact(act.locationKey))
                    put("kind", act.kind.wire)
                    put("summary", summaryOf(act.summary))
                    put("at", act.occurredAt.toEpochMilliseconds())
                    put("session", act.sessionId?.let { redactor.redact(it) })
                    put("acknowledged", act.acknowledged)
                    put("undoAttempted", act.undoAttempted)
                    put("undone", act.undone)
                }
            }
        }.toString()

    private fun load(raw: String?) {
        if (raw == null) return
        val loaded = decode(raw)
        if (loaded == null) {
            acts = emptyList()
            corruptHistory = true
        } else {
            acts = bounded(loaded)
        }
        publish()
    }

    @Suppress("CyclomaticComplexMethod")
    private fun decode(raw: String): List<AutomaticAct>? {
        if (raw.length > MAX_BLOB) return null
        return try {
            val data = Json.parseToJsonElement(raw) as? JsonObject ?: return null
            if ((data["version"] as? JsonPrimitive)?.int != 1) return null
            val entries = data["acts"]!!.jsonArray
            if (entries.size > MAX_ENTRIES) return null
            val ids = mutableSetOf<String>()
            entries.map { element ->
                val e = element.jsonObject
                val id = e["id"]!!.jsonPrimitive.content
                val location = e["location"]!!.jsonPrimitive.content
                val summary = e["summary"]!!.jsonPrimitive.content
                val at = e["at"]!!.jsonPrimitive.long
                val session = (e["session"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
                val attempted = e["undoAttempted"]!!.jsonPrimitive.boolean
                val undone = e["undone"]!!.jsonPrimitive.boolean
                val kind = AutomaticActKind.fromWire(e["kind"]!!.jsonPrimitive.content) ?: return null
                if (!HEX64.matches(id) || !HEX64.matches(location) || !ids.add(id) || summaryOf(summary).isEmpty() ||
                    summary.length > 1600 || at <= 0 || (session != null && session.length > 256) || (undone && !attempted)
                ) {
                    return null
                }
                AutomaticAct(
                    id = id,
                    locationKey = location,
                    kind = kind,
                    summary = summaryOf(summary),
                    occurredAt = Instant.fromEpochMilliseconds(at),
                    sessionId = if (session == null || redactor.containsSecret(session)) null else redactor.redact(session),
                    acknowledged = e["acknowledged"]!!.jsonPrimitive.boolean,
                    undoAttempted = attempted,
                    undone = undone,
                )
            }
        } catch (e: IllegalArgumentException) {
            log.d { "activity history unreadable: ${e::class.simpleName}" }
            null
        } catch (e: NullPointerException) {
            log.d { "activity history incomplete" }
            null
        }
    }

    public companion object {
        public const val MAX_ENTRIES: Int = 200
        private const val MAX_BLOB = 512_000
        private const val SUMMARY_LIMIT = 400
        private val FLATTEN = Regex("[\\s\\x00-\\x1f\\x7f]+")
        private val HEX64 = Regex("^[a-f0-9]{64}$")
        private val OPAQUE_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
        private val log = AppLog.tagged("AutomaticActivity")

        /** The profile-scoped key the deletion sweep must cover (`oc.automaticActivity.<profileId>`). */
        public fun keyFor(profileId: String): String = "oc.automaticActivity.$profileId"

        /** Keys contain only an app-authored opaque profile id, never server input. */
        public fun isOpaqueProfileId(profileId: String, redactor: Redactor): Boolean =
            OPAQUE_ID.matches(profileId) && !redactor.containsSecret(profileId)

        /** Null for an id that is not an opaque app-authored profile id; nothing is read or written then. */
        public suspend fun open(
            store: AttentionKeyStore,
            redactor: Redactor,
            profileId: String,
            isProfilePresent: () -> Boolean,
        ): AutomaticActivity? {
            if (!isOpaqueProfileId(profileId, redactor)) return null
            val activity = AutomaticActivity(store, redactor, profileId, isProfilePresent)
            if (isProfilePresent()) {
                val raw = attempt({ Failure.StorageRefused() }) { store.read(activity.storageKey) }
                when (raw) {
                    is Outcome.Ok -> activity.load(raw.value)
                    is Outcome.Failed -> {
                        activity.corruptHistory = true
                        activity.publish()
                    }
                }
            }
            return activity
        }

        internal fun bounded(entries: List<AutomaticAct>): List<AutomaticAct> =
            entries
                .sortedWith(compareByDescending<AutomaticAct> { it.occurredAt }.thenBy { it.id })
                .take(MAX_ENTRIES)
                .toImmutableList()

        private fun takeCodePoints(text: String, limit: Int): String {
            var count = 0
            var i = 0
            while (i < text.length && count < limit) {
                i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
                count++
            }
            return text.substring(0, i)
        }
    }
}

/**
 * The one shared history per profile on one store, so every connection and the Inbox write the same instance, never
 * a second writer for one key (Flutter `AutomaticActivityController.forProfile` / `closeProfile`).
 */
public class AutomaticActivityRegistry(private val store: AttentionKeyStore, private val redactor: Redactor) {
    private val lane = Mutex()
    private val byProfile = mutableMapOf<String, AutomaticActivity>()

    public suspend fun forProfile(profileId: String, isProfilePresent: () -> Boolean): AutomaticActivity? =
        lane.withLock {
            byProfile[profileId]
                ?: AutomaticActivity.open(store, redactor, profileId, isProfilePresent)?.also { byProfile[profileId] = it }
        }

    /** Permanently closes and erases the shared history after profile removal commits. False when refused. */
    public suspend fun closeProfile(profileId: String): Boolean {
        val activity = lane.withLock { byProfile[profileId] } ?: return true
        try {
            return activity.deleteProfileData()
        } finally {
            lane.withLock { if (byProfile[profileId] === activity) byProfile.remove(profileId) }
            activity.dispose()
        }
    }
}
