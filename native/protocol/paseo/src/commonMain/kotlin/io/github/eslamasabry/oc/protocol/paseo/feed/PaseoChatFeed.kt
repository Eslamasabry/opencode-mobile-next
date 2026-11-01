package io.github.eslamasabry.oc.protocol.paseo.feed

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.StorageProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The chat list of exactly one Paseo gateway scope (B-AGENT-153, 154, 180), a port of `lib/paseo/chat_feed_source.dart`.
 * The owner refreshes after gateway events; this feed never rescopes the gateway, never claims a global inventory and
 * never reads a timeline. All calls on one lane (the owner's); [scope] runs the shared refresh and the housekeeping.
 *
 * Failures: [refresh] never fails (a failed or timed-out read keeps the rows and reports `complete = false`); [startChat]
 * and [rememberLastUsedProject] return [Outcome].
 */
public class PaseoChatFeed(
    private val port: PaseoFeedPort,
    private val scope: CoroutineScope,
    private val projectName: String? = null,
    private val isGit: Boolean = false,
    private val maxPages: Int = 5,
    private val refreshTimeoutMs: Long = 45_000,
    initialLastUsedProjectDirectory: String? = null,
    private val persistLastUsedProject: (suspend (String) -> Unit)? = null,
    /** A waiting gen-UI card for the session (owner state); it reads as needs-you too. */
    private val hasWaitingCard: ((SessionId) -> Boolean)? = null,
    /** The title kept for a conversation (`provider:sessionId`) after its older records were archived. */
    private val savedTitle: ((String) -> String?)? = null,
    /** Keeps such a title; false when it could not be saved (the older record then stays). */
    private val saveTitle: (suspend (String, String) -> Boolean)? = null,
    /** The runtime's display name from the agent catalog (`:engine:agents`); null leaves the row unlabeled. */
    private val agentLabel: (String) -> String? = { null },
) {
    init {
        require(maxPages in 1..8 && refreshTimeoutMs > 0) { "Feed read bounds are invalid." }
    }

    private val directory: String? = port.directory
    private val changeFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var items: List<PaseoFeedRow> = emptyList()
    private val draftProviders = HashMap<SessionId, String>()
    private var complete = false
    private var disposed = false
    private var refreshing: Deferred<Unit>? = null
    private var revision = 0
    private var lastUsed: String? =
        initialLastUsedProjectDirectory?.takeIf { it == directory && !PaseoFeedPaths.isTemporary(it) }
    private val attention = scope.launch { port.attentionChanges.collect { if (valid) notifyChanged() } }

    /** Something a [snapshot] reads changed (no payload; conflated). */
    public val changes: Flow<Unit> = changeFlow.asSharedFlow()

    private val valid: Boolean get() = !disposed && !port.isClosed && port.directory == directory

    private fun notifyChanged() {
        if (!disposed) changeFlow.tryEmit(Unit)
    }

    private val name: String? get() = projectName ?: directory?.split('/')?.lastOrNull { it.isNotEmpty() }

    /** The rows for [filter]; reads held state only. Rows of another scope (the gateway moved) are hidden. */
    public fun snapshot(filter: PaseoFeedFilter = PaseoFeedFilter.All): PaseoFeedSnapshot {
        val rows = if (valid) items.map(::withCards).map(::withBackgroundWork).filter(filter::matches) else emptyList()
        return PaseoFeedSnapshot(
            rows = rows.sortedWith(PaseoFeedOrder).toImmutableList(),
            loading = valid && refreshing != null && items.isEmpty(),
            complete = valid && complete,
        )
    }

    /** The one project of this feed with its counts; empty for a temporary folder or another scope. */
    public val projectSummaries: List<PaseoProjectSummary>
        get() {
            val dir = directory
            if (!valid || dir == null || PaseoFeedPaths.isTemporary(dir)) return persistentListOf()
            val rows = items.map(::withCards).map(::withBackgroundWork).filter { !it.isSubagent }
            return persistentListOf(
                PaseoProjectSummary(
                    directory = dir,
                    name = name,
                    isGit = isGit,
                    chatCount = rows.size,
                    runningCount = rows.count { it.status == PaseoFeedStatus.Running },
                    needsYouCount = rows.count { it.status == PaseoFeedStatus.NeedsYou },
                    lastActivityMs = rows.maxOfOrNull { it.lastActivityMs },
                ),
            )
        }

    /** Re-read after a delivery or turn boundary: an older in-flight read may predate that activity. */
    public suspend fun refreshAfterActivity() {
        refreshing?.await()
        refresh()
    }

    /** Reads up to [maxPages] pages. Concurrent calls share one read; a read is bounded by [refreshTimeoutMs]. */
    public suspend fun refresh() {
        if (!valid) return
        refreshing?.let {
            it.await()
            return
        }
        val myRevision = ++revision
        val read = scope.async(start = CoroutineStart.LAZY) {
            try {
                val finished = withTimeoutOrNull(refreshTimeoutMs) {
                    read(myRevision)
                    true
                }
                if (finished == null) fence(myRevision)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fence(myRevision)
            } finally {
                refreshing = null
                notifyChanged()
            }
        }
        refreshing = read
        notifyChanged()
        read.await()
    }

    /** A failed or late read: whatever it would still deliver is fenced off, and the rows say they are partial. */
    private fun fence(myRevision: Int) {
        if (valid && revision == myRevision) {
            revision++
            complete = false
        }
    }

    private fun current(myRevision: Int): Boolean = valid && myRevision == revision

    private suspend fun read(myRevision: Int) {
        val dir = directory ?: return
        val sessions = LinkedHashMap<SessionId, Session>()
        var cursor: String? = null
        var allRead = false
        val seen = mutableSetOf<String>()
        var receivedPage = false
        try {
            try {
                for (pageIndex in 0 until maxPages) {
                    val page = port.sessionPage(cursor, PAGE_LIMIT)
                    if (!current(myRevision)) return
                    receivedPage = true
                    for (session in page.items) {
                        if (session.directory == dir && !PaseoFeedPaths.isTemporary(session.directory)) sessions[session.id] = session
                    }
                    val next = page.nextCursor
                    if (next == null) {
                        allRead = true
                        break
                    }
                    cursor = next
                    if (!seen.add(next)) break // a repeated cursor stops the bounded read
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (!current(myRevision)) return
                if (!receivedPage) {
                    complete = false
                    return
                }
            }
            val (bySession, olderCopies) = foldCopies(sessions)
            val statuses = port.sessionStatuses()
            if (!current(myRevision)) return
            items = sessions.values.map { row(it, dir, statuses[it.id]) }.toImmutableList()
            complete = allRead
            // Housekeeping, never waited on: a helper slow to answer must not hold the list.
            scope.launch { archiveOlderCopies(olderCopies, bySession, statuses, myRevision) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (current(myRevision)) complete = false
        }
    }

    /**
     * A resume leaves the old record of the same conversation behind: one row per conversation, the latest record, with
     * the title it had (B-AGENT-180). A kept record without a title of its own takes the saved one (B-AGENT-181).
     * Returns the kept record per key and the older ids that were folded away (id → key).
     */
    private fun foldCopies(sessions: LinkedHashMap<SessionId, Session>): Pair<Map<String, Session>, Map<SessionId, String>> {
        val bySession = LinkedHashMap<String, Session>()
        val olderCopies = LinkedHashMap<SessionId, String>()
        for (session in sessions.values.toList()) {
            val key = port.sessionKey(session.id) ?: continue
            val other = bySession[key]
            if (other == null) {
                bySession[key] = session
                continue
            }
            val newer = if ((session.time?.updatedMs ?: 0) >= (other.time?.updatedMs ?: 0)) session else other
            val older = if (newer === session) other else session
            sessions.remove(older.id)
            olderCopies[older.id] = key
            olderCopies.remove(newer.id)
            val titled = if (port.hasOwnTitle(newer.id)) newer else newer.copy(title = older.title)
            // The gateway gets the title back too, so the conversation's own header says what this row says.
            if (!port.hasOwnTitle(newer.id) && port.hasOwnTitle(older.id)) port.keepTitle(newer.id, older.title.orEmpty())
            sessions[newer.id] = titled
            bySession[key] = titled
        }
        for ((key, kept) in bySession.entries.toList()) {
            if (port.hasOwnTitle(kept.id)) continue
            val saved = savedTitle?.invoke(key)
            if (saved.isNullOrBlank()) continue
            port.keepTitle(kept.id, saved)
            val retitled = kept.copy(title = saved)
            sessions[kept.id] = retitled
            bySession[key] = retitled
        }
        return bySession to olderCopies
    }

    private fun row(session: Session, dir: String, status: SessionStatus?): PaseoFeedRow {
        val provider = port.providerIdForSession(session.id) ?: draftProviders[session.id]
        val feedStatus =
            when {
                status == SessionStatus.Busy || status == SessionStatus.Retry -> PaseoFeedStatus.Running
                status?.raw == "error" -> PaseoFeedStatus.Failed
                // The daemon's own word for an agent whose last turn failed (the status map only says busy or idle).
                port.agentFailed(session.id) -> PaseoFeedStatus.Failed
                else -> PaseoFeedStatus.Idle
            }
        return PaseoFeedRow(
            sessionId = session.id,
            title = session.title?.takeIf { it.isNotEmpty() },
            directory = dir,
            projectName = name,
            isGit = isGit,
            status = feedStatus,
            lastActivityMs = session.time?.updatedMs ?: session.time?.createdMs ?: 0,
            parentId = session.parentId,
            agentId = provider ?: PaseoFeedRow.UNKNOWN_AGENT,
            agentLabel = provider?.let(agentLabel),
        )
    }

    /** Archives the older records the list folded; their title is saved first; a failure leaves them for next time. */
    private suspend fun archiveOlderCopies(
        olderCopies: Map<SessionId, String>,
        kept: Map<String, Session>,
        statuses: Map<SessionId, SessionStatus>,
        myRevision: Int,
    ) {
        try {
            for ((olderId, key) in olderCopies) {
                if (!current(myRevision)) return
                val status = statuses[olderId]
                if (status == SessionStatus.Busy || status == SessionStatus.Retry) continue
                val keeper = kept[key] ?: continue
                if (keeper.id == olderId) continue
                if (port.hasOwnTitle(keeper.id)) {
                    val title = keeper.title.orEmpty()
                    if (savedTitle?.invoke(key) != title) {
                        val save = saveTitle ?: continue
                        if (!save(key, title)) continue // persisted before the record that carried it goes away
                    }
                }
                port.archiveOlderCopy(olderId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Housekeeping; the list is already right.
        }
    }

    /** A pending permission, question or waiting card outranks running; nothing of the request is exposed. */
    private fun withCards(row: PaseoFeedRow): PaseoFeedRow {
        if (row.status == PaseoFeedStatus.NeedsYou) return row
        val waiting = port.hasPendingQuestion(row.sessionId) || port.hasPendingPermission(row.sessionId) ||
            hasWaitingCard?.invoke(row.sessionId) == true
        return if (waiting) row.copy(status = PaseoFeedStatus.NeedsYou) else row
    }

    /** A turn that ended on the agent's own failure reads failed; one with background work still going reads running. */
    private fun withBackgroundWork(row: PaseoFeedRow): PaseoFeedRow {
        if (row.status != PaseoFeedStatus.Idle) return row
        return when {
            port.lastTurnFailed(row.sessionId) -> row.copy(status = PaseoFeedStatus.Failed)
            port.backgroundTasksRunning(row.sessionId) -> row.copy(status = PaseoFeedStatus.Running)
            else -> row
        }
    }

    // ---- starting a chat

    /** The last project a chat was started in, while this feed's scope is current. */
    public val lastUsedProjectDirectory: String? get() = if (valid) lastUsed else null

    /** Remembers [directory] when it is this feed's real project; another scope or a temporary folder is ignored. */
    public suspend fun rememberLastUsedProject(directory: String): Outcome<Unit> =
        attempt(classify = ::classify) { remember(directory) }

    private suspend fun remember(directory: String) {
        if (!valid || directory != this.directory || PaseoFeedPaths.isTemporary(directory)) return
        try {
            persistLastUsedProject?.invoke(directory)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw FeedFailure(Failure.Storage(StorageProblem.WriteRefused))
        }
        if (!valid) return
        lastUsed = directory
        notifyChanged()
    }

    /**
     * Starts a chat in [directory] (this feed's project only) with runtime [agentId] (else the host's default). A
     * runtime the host did not report, or another, temporary or system folder, is refused before any draft is made. With
     * a [firstPrompt] the agent is created at once; without one the draft keeps its runtime until its first prompt.
     * Returns the daemon's id (the chat opens on a fresh gateway). A start that fails leaves no draft behind.
     *
     * Failures: another scope / unusable folder → `Refused(StaleScope)`; unknown runtime →
     * `Unavailable(hostAgentProviders)`; daemon failures as `PaseoException.toFailure()`.
     */
    public suspend fun startChat(
        directory: String,
        agentId: String? = null,
        firstPrompt: String? = null,
        modelId: String? = null,
    ): Outcome<SessionId> = attempt(classify = ::classify) { start(directory, agentId, firstPrompt, modelId) }

    private suspend fun start(directory: String, agentId: String?, firstPrompt: String?, modelId: String?): SessionId {
        checkDirectory(directory)
        var draft: SessionId? = null
        var started = false
        try {
            val providers = port.providers()
            checkDirectory(directory)
            val provider = agentId ?: providers.defaultProviderId
            if (provider == null || providers.providers.none { it.id == provider }) {
                throw FeedFailure(Failure.Unavailable(FeatureId("hostAgentProviders")))
            }
            val session = port.createDraft()
            draft = session.id
            checkDirectory(directory)
            port.seedDraftProvider(session.id, provider)
            draftProviders[session.id] = provider
            if (!firstPrompt.isNullOrBlank()) {
                port.prompt(session.id, firstPrompt, ModelRef(provider, modelId ?: DEFAULT_MODEL))
                checkDirectory(directory)
            }
            remember(directory)
            refresh()
            checkDirectory(directory)
            started = true
            return port.daemonSessionId(session.id)
        } finally {
            val made = draft
            if (!started && made != null) {
                port.discardDraft(made)
                draftProviders.remove(made)
            }
        }
    }

    private fun checkDirectory(directory: String) {
        if (!valid || directory != this.directory || !PaseoFeedPaths.isStartable(directory)) {
            throw FeedFailure(Failure.Refused(RefusalReason.StaleScope))
        }
    }

    private fun classify(error: Throwable): Failure =
        when (error) {
            is FeedFailure -> error.failure
            is PaseoException -> error.toFailure()
            else -> Failure.Unexpected(Redacted.shape(error))
        }

    /** Stops reading and hides every row. Idempotent. */
    public fun dispose() {
        if (disposed) return
        disposed = true
        revision++
        items = emptyList()
        draftProviders.clear()
        attention.cancel()
    }

    private class FeedFailure(val failure: Failure) : Exception("Paseo feed refusal")

    private companion object {
        const val PAGE_LIMIT = 200

        /** Dart `paseoDefaultModel`: the runtime's own default model. */
        const val DEFAULT_MODEL = "default"
    }
}
