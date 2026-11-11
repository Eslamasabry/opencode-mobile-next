package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.FormSpec
import io.github.eslamasabry.oc.core.model.GlobalSessionResult
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withTimeoutOrNull

/** Flutter `_feedPages` / `_feedPageSize` (state/connection/chat_feed.dart): four pages of fifty. */
internal const val ACROSS_FEED_PAGES: Int = 4
internal const val ACROSS_FEED_PAGE_SIZE: Int = 50

/**
 * The home's conversation list. A server that lists across projects (`globalSessionSearch`: OpenCode 2) gives the
 * conversations of EVERY project, as Flutter's feed does (`listGlobalSessions`, roots only, four pages of fifty); the
 * current project's own list (children included, so sub-agent rows keep their parent) is merged in. Any other server
 * keeps the current project only. A failed or capped global read keeps what was read and says the list is incomplete.
 */
internal suspend fun readFeedSessionPages(
    server: ServerGateway,
    operations: ServerOperationsGateway?,
    maxPages: Int = MAX_SESSION_PAGES,
): Outcome<SessionPages> {
    val project = readSessionPages(server, maxPages)
    if (operations == null || !server.capabilities.value.globalSessionSearch) return project
    val global = readGlobalPages(operations)
    val own = (project as? Outcome.Ok)?.value
    if (global == null) return own?.let { Outcome.Ok(SessionPages(it.sessions, complete = false)) } ?: project
    val seen = HashSet<SessionId>()
    val merged = ArrayList<Session>()
    for (s in own?.sessions.orEmpty()) if (seen.add(s.id)) merged += s
    for (s in global.sessions) if (seen.add(s.id)) merged += s
    return Outcome.Ok(SessionPages(merged.toImmutableList(), global.complete && (own?.complete ?: false)))
}

private suspend fun readGlobalPages(operations: ServerOperationsGateway): SessionPages? {
    val all = ArrayList<Session>()
    val seenCursors = HashSet<String>()
    var cursor: String? = null
    var complete = true
    for (page in 0 until ACROSS_FEED_PAGES) {
        val read: ServerPage<GlobalSessionResult> =
            when (
                val answer =
                    operations.listGlobalSessions(null, true, cursor, ACROSS_FEED_PAGE_SIZE)
            ) {
                is Outcome.Failed -> {
                    if (page == 0) return null
                    complete = false
                    break
                }
                is Outcome.Ok -> answer.value
            }
        all += read.items.map { it.session }
        val next = read.nextCursor
        if (next == null || !seenCursors.add(next)) {
            cursor = null
            break
        }
        cursor = next
        if (page == ACROSS_FEED_PAGES - 1) complete = false
    }
    return SessionPages(all.toImmutableList(), complete)
}

/** What conversations of other projects are blocked on (permissions and forms), read one conversation at a time. */
internal class OtherProjectAsks(val permissions: List<PermissionRequest>, val forms: List<FormSpec>) {
    companion object {
        val None = OtherProjectAsks(emptyList(), emptyList())
    }
}

/**
 * The location-wide ask lists only cover the connection's own folder, so a conversation of another project that is
 * running is asked for its own (Flutter reads each such folder, `_refreshFeedDirectoryQuestions`: at most four, three
 * seconds each). Here at most [MAX_OTHER_READS] running conversations, three seconds each; a failed read is "no ask
 * known", never a failed feed.
 */
internal suspend fun readOtherProjectAsks(
    server: ServerGateway,
    sessions: List<Session>,
    statuses: Map<SessionId, SessionStatus>?,
    here: String?,
): OtherProjectAsks {
    if (statuses == null) return OtherProjectAsks.None
    val running =
        sessions
            .filter {
                (statuses[it.id] == SessionStatus.Busy || statuses[it.id] == SessionStatus.Retry) &&
                    it.directory != null &&
                    !sameFolder(it.directory, here)
            }
            .take(MAX_OTHER_READS)
    if (running.isEmpty()) return OtherProjectAsks.None
    val caps = server.capabilities.value
    val permissions = ArrayList<PermissionRequest>()
    val forms = ArrayList<FormSpec>()
    for (session in running) {
        if (caps.permissionRequests)
            (withTimeoutOrNull(OTHER_READ_MS) { server.sessionPermissions(session.id) } as? Outcome.Ok)
                ?.value
                ?.let { permissions += it }
        if (caps.forms)
            (withTimeoutOrNull(OTHER_READ_MS) { server.sessionForms(session.id) } as? Outcome.Ok)
                ?.value
                ?.let { forms += it }
    }
    return OtherProjectAsks(permissions, forms)
}

private fun sameFolder(a: String?, b: String?): Boolean =
    a != null && b != null && a.trimEnd('/').ifEmpty { "/" } == b.trimEnd('/').ifEmpty { "/" }

private const val MAX_OTHER_READS: Int = 8
private const val OTHER_READ_MS: Long = 3_000
