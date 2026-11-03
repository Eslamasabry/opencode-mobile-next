package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Port of the session list half of lib/state/connection/sessions.dart (refresh, paging, direct reads, busy status,
// delete and pins) and the revision rules of lifecycle.dart `_markSessionChanged`.

/** The connected server's session reads. A 404 is `Failure.NotFound`; an expired cursor is a 400/410 rejection. */
public interface SessionInventorySource {
    public suspend fun sessionPage(cursor: String?): Outcome<ServerPage<Session>>

    public suspend fun sessionStatuses(): Outcome<Map<SessionId, SessionStatus>>

    public suspend fun session(id: SessionId): Outcome<Session>

    public suspend fun deleteSession(id: SessionId): Outcome<Unit>
}

/**
 * The pins of the current profile and location (the app binds `:engine:project`'s `SessionPinStore` here). [ids] is
 * an immutable snapshot that changes only after storage acknowledged a write.
 */
public interface SessionPinsView {
    public val canPin: Boolean

    public fun ids(): Set<SessionId>

    public suspend fun setPinned(id: SessionId, pinned: Boolean): Outcome<Unit>
}

public object NoSessionPins : SessionPinsView {
    override val canPin: Boolean get() = false

    override fun ids(): Set<SessionId> = emptySet()

    override suspend fun setPinned(id: SessionId, pinned: Boolean): Outcome<Unit> =
        Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
}

/**
 * The session list of one connection and location. Rules kept from Flutter:
 * - every live change bumps a per-session revision; a page or direct read that started before it never overwrites it,
 *   and a metadata read never hides a concurrent busy status (separate status revisions);
 * - a partial head refresh keeps older cached rows; absence prunes only after the whole inventory was walked;
 * - a new head refresh retires an older page request; an expired cursor (400/410) or a looping cursor asks for a
 *   reload, a transient failure retries the same cursor;
 * - direct reads are shared per (connection, session, revision) and keep other locations out of the list;
 * - a confirmed delete or an authoritative 404 removes the session for good: a pending page cannot bring it back;
 * - pinned sessions lead (recency kept inside each group); pinned ones beyond the first page are read once, and a
 *   failed pinned read is retried by the next refresh without losing the pin.
 *
 * Confined to one dispatcher: [scope] (whose dispatcher must be that one) runs the shared reads.
 */
public class SessionInventory(
    private val scope: CoroutineScope,
    private val pins: SessionPinsView = NoSessionPins,
) {
    private data class ReadKey(val generation: Int, val id: SessionId, val revision: Long)

    private var source: SessionInventorySource? = null
    private var generation = 0
    private var location = ServerLocation.Default

    /** Changes with every location switch; a pin asked for in an older location is refused. */
    public var locationRevision: Int = 0
        private set

    private val sessions = LinkedHashMap<SessionId, Session>()
    private val inventoryIds = HashSet<SessionId>()
    private var inventoryInitialized = false
    private val pageIds = HashSet<SessionId>()
    private val usedCursors = HashSet<String>()
    private var cursor: String? = null
    private val deleted = HashSet<SessionId>()
    private val busySessions = LinkedHashSet<SessionId>()
    private val detailErrors = LinkedHashMap<SessionId, Failure>()

    private var sessionRevision = 0L
    private var snapshotRevision = 0L
    private val revisions = HashMap<SessionId, Long>()
    private val statusRevisions = HashMap<SessionId, Long>()
    private var refreshGeneration = 0
    private val reads = SharedWork<ReadKey, Unit>(scope)

    private var version = 0L
    private var sortedVersion = -1L
    private var sortedPins: Set<SessionId>? = null
    private var sortedResult: ImmutableList<Session> = persistentListOf()

    private val mutableChanges = MutableStateFlow(0L)

    /** Ticks after every visible change. */
    public val changes: StateFlow<Long> = mutableChanges.asStateFlow()

    /** Called with the sorted list after each successful refresh (the restart preview saves it). */
    public var onRefreshed: ((List<Session>) -> Unit)? = null

    /** Called when a session is removed for good (tail caches, selection memory). */
    public var onRemoved: ((SessionId) -> Unit)? = null

    public var loading: Boolean = false
        private set
    public var loadingMore: Boolean = false
        private set
    public var needsReload: Boolean = false
        private set
    public var error: Failure? = null
        private set
    public var moreError: Failure? = null
        private set

    public val hasMore: Boolean get() = cursor != null
    public val sessionsById: Map<SessionId, Session> get() = sessions
    public val busy: Set<SessionId> get() = busySessions
    public val detailsErrors: Map<SessionId, Failure> get() = detailErrors
    public val currentSource: SessionInventorySource? get() = source

    /** Adopts a connection (or none) for [newLocation]: everything known about the previous one is dropped. */
    public fun connect(newSource: SessionInventorySource?, newLocation: ServerLocation = ServerLocation.Default) {
        source = newSource
        generation++
        location = newLocation
        locationRevision++
        sessions.clear()
        inventoryIds.clear()
        inventoryInitialized = false
        pageIds.clear()
        usedCursors.clear()
        cursor = null
        deleted.clear()
        busySessions.clear()
        detailErrors.clear()
        revisions.clear()
        statusRevisions.clear()
        refreshGeneration++
        loading = false
        loadingMore = false
        needsReload = false
        error = null
        moreError = null
        changed()
    }

    /** Keeps the source but retires every request in flight (a reconnect of the same server). */
    public fun retire(newSource: SessionInventorySource?) {
        source = newSource
        generation++
        changed()
    }

    // ---------------- Reads

    public suspend fun refresh() {
        val src = source ?: return
        val gen = generation
        val refresh = ++refreshGeneration
        val revision = sessionRevision
        loading = true
        loadingMore = false
        error = null
        moreError = null
        changed()
        // Both reads describe this location independently; live revisions still win below.
        val statusRead = MutableStateFlow<Outcome<Map<SessionId, SessionStatus>>?>(null)
        scope.launch { statusRead.value = src.sessionStatuses() }
        val page = src.sessionPage(null)
        if (!currentRefresh(gen, src, refresh)) return
        if (page is Outcome.Failed) {
            loading = false
            error = page.failure
            changed()
            return
        }
        val statuses = statusRead.filterNotNull().first()
        if (!currentRefresh(gen, src, refresh)) return
        snapshotRevision = revision
        pageIds.clear()
        usedCursors.clear()
        merge((page as Outcome.Ok).value, revision)
        moreError = null
        needsReload = false
        if (statuses is Outcome.Ok) {
            val statusIds = LinkedHashSet<SessionId>().apply {
                addAll(sessions.keys)
                addAll(busySessions)
                addAll(statuses.value.keys)
            }
            for (id in statusIds) {
                if ((statusRevisions[id] ?: 0) > revision) continue
                val status = statuses.value[id]
                if (status != null && status != SessionStatus.Idle) {
                    busySessions += id
                    if (id !in sessions) scope.launch { ensureSession(id) }
                } else {
                    busySessions -= id
                }
            }
        }
        loading = false
        error = (statuses as? Outcome.Failed)?.failure
        changed()
        onRefreshed?.invoke(sortedSessions())
        scope.launch { refreshPinned(gen, src) }
    }

    public suspend fun loadMore() {
        if (loading || loadingMore) return
        if (needsReload) return refresh()
        val from = cursor ?: return
        val src = source ?: return
        val gen = generation
        val refresh = ++refreshGeneration
        val revision = sessionRevision
        loadingMore = true
        moreError = null
        changed()
        try {
            when (val page = src.sessionPage(from)) {
                is Outcome.Failed -> {
                    if (!currentRefresh(gen, src, refresh)) return
                    moreError = page.failure
                    val status = (page.failure as? Failure.Rejected)?.status
                    if (status == 400 || status == 410) needsReload = true
                }
                is Outcome.Ok -> {
                    if (!currentRefresh(gen, src, refresh)) return
                    val next = page.value.nextCursor
                    if (next != null && (next == from || next in usedCursors)) {
                        // The list changed under the cursor: only a refresh from the newest page can continue.
                        needsReload = true
                        moreError = Failure.Conflict()
                    } else {
                        usedCursors += from
                        merge(page.value, revision)
                    }
                }
            }
        } finally {
            if (currentRefresh(gen, src, refresh)) {
                loadingMore = false
                changed()
            }
        }
    }

    /** Direct routes and pinned rows: one shared read per (connection, session, revision). */
    public suspend fun ensureSession(id: SessionId) {
        val src = source ?: return
        reads.join(ReadKey(generation, id, revisions[id] ?: 0)) { readOne(src, id) }
    }

    private suspend fun readOne(src: SessionInventorySource, id: SessionId) {
        val gen = generation
        val revision = revisions[id] ?: 0
        val result = src.session(id)
        val current = gen == generation && src === source && revision == (revisions[id] ?: 0)
        when (result) {
            is Outcome.Ok -> {
                if (!current || id in deleted) return
                sessions[id] = result.value
                detailErrors.remove(id)
                rememberMembership(result.value, authoritative = true)
                markChanged(id, affectsStatus = false)
                changed()
            }
            is Outcome.Failed ->
                if (current) {
                    if (result.failure is Failure.NotFound) {
                        removeSession(id)
                    } else {
                        detailErrors[id] = result.failure
                        changed()
                    }
                }
        }
    }

    private suspend fun refreshPinned(gen: Int, src: SessionInventorySource) {
        for (id in pins.ids()) {
            if (gen != generation || src !== source) return
            if (id !in inventoryIds || id in detailErrors) ensureSession(id)
        }
    }

    // ---------------- Commands and live events

    public suspend fun deleteSession(id: SessionId): Outcome<Unit> {
        val src = source ?: return Outcome.Failed(Failure.Network())
        val gen = generation
        val result = src.deleteSession(id)
        if (result is Outcome.Ok && gen == generation && src === source) removeSession(id)
        return result
    }

    /** A live `session.updated` (or created): newer than any read in flight. */
    public fun applyUpdated(session: Session) {
        if (session.id in deleted) return
        markChanged(session.id)
        sessions[session.id] = session
        rememberMembership(session, authoritative = true)
        changed()
    }

    /** A live `session.deleted`, or a local removal. */
    public fun applyDeleted(id: SessionId) {
        removeSession(id)
    }

    /** A live status change; it never invalidates a metadata read in flight. */
    public fun applyStatus(id: SessionId, status: SessionStatus) {
        markChanged(id, affectsMetadata = false)
        if (status == SessionStatus.Idle) busySessions -= id else busySessions += id
        changed()
    }

    /** A live metadata patch (model or agent selected, renamed): applied to the known row only. */
    public fun applyPatch(id: SessionId, patch: (Session) -> Session) {
        markChanged(id, affectsStatus = false)
        sessions[id]?.let { sessions[id] = patch(it) }
        changed()
    }

    /** Something changed on the server for [id] without its new state (compaction): pending reads are stale. */
    public fun invalidate(id: SessionId) {
        markChanged(id, affectsStatus = false)
    }

    /** Sessions already known from elsewhere (a hand-off or a restore), without claiming list membership. */
    public fun adopt(known: Collection<Session>) {
        for (session in known) sessions[session.id] = session
        changed()
    }

    // ---------------- Pins and ordering

    public val pinnedIds: Set<SessionId> get() = pins.ids()

    public fun isPinned(id: SessionId): Boolean = id in pins.ids()

    /** Some pinned session could not be read; the next refresh retries it. */
    public val pinnedLoadFailed: Boolean get() = pins.ids().any { it in detailErrors }

    /** Refused (without a change) when the location moved on since the person asked. */
    public suspend fun setPinned(id: SessionId, pinned: Boolean, atLocationRevision: Int): Outcome<Unit> {
        if (!pins.canPin || atLocationRevision != locationRevision) {
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        }
        val result = pins.setPinned(id, pinned)
        changed()
        return result
    }

    /**
     * Top-level, unarchived sessions of this location; pinned first, then newest. One immutable snapshot is reused
     * until the inventory or the pins change, so repeated unrelated reads cost nothing.
     */
    public fun sortedSessions(): ImmutableList<Session> {
        val pinned = pins.ids()
        if (sortedVersion == version && (sortedPins === pinned || sortedPins == pinned)) return sortedResult
        sortedVersion = version
        sortedPins = pinned
        sortedResult = sessions.values
            .filter { it.parentId == null && it.time?.archivedMs == null && (!inventoryInitialized || it.id in inventoryIds) }
            .sortedWith(
                compareByDescending<Session> { it.id in pinned }
                    .thenByDescending { it.time?.updatedMs ?: it.time?.createdMs ?: 0L },
            )
            .toImmutableList()
        return sortedResult
    }

    // ---------------- Internals

    private fun currentRefresh(gen: Int, src: SessionInventorySource, refresh: Int): Boolean =
        gen == generation && src === source && refresh == refreshGeneration

    private fun merge(page: ServerPage<Session>, revision: Long) {
        if (!inventoryInitialized) {
            for (session in sessions.values) rememberMembership(session)
            inventoryInitialized = true
        }
        for (session in page.items) {
            pageIds += session.id
            if (session.id in deleted) continue
            if ((revisions[session.id] ?: 0) <= revision) {
                sessions[session.id] = session
                inventoryIds += session.id
            }
        }
        cursor = page.nextCursor
        // Absence means something only after the whole inventory was walked.
        if (page.nextCursor == null) {
            inventoryIds.removeAll { it !in pageIds && (revisions[it] ?: 0) <= snapshotRevision }
        }
        changed()
    }

    private fun rememberMembership(session: Session, authoritative: Boolean = false) {
        val dir = location.directory
        val ws = location.workspace
        if ((dir == null || session.directory == dir) && (ws == null || session.workspaceId?.raw == ws)) {
            inventoryIds += session.id
        } else if (
            authoritative &&
            ((dir != null && session.directory != null && session.directory != dir) || (ws != null && session.workspaceId?.raw != ws))
        ) {
            inventoryIds -= session.id
        }
    }

    private fun removeSession(id: SessionId) {
        markChanged(id)
        deleted += id
        sessions.remove(id)
        detailErrors.remove(id)
        inventoryIds.remove(id)
        busySessions.remove(id)
        changed()
        onRemoved?.invoke(id)
    }

    private fun markChanged(id: SessionId, affectsStatus: Boolean = true, affectsMetadata: Boolean = true) {
        sessionRevision++
        if (affectsMetadata) revisions[id] = sessionRevision
        if (affectsStatus) statusRevisions[id] = sessionRevision
    }

    private fun changed() {
        version++
        mutableChanges.value = mutableChanges.value + 1
    }
}
