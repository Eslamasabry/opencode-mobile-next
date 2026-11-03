package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import kotlinx.collections.immutable.toImmutableList

/** One full read of a server's conversation list (QA wave 1 B1). */
internal class SessionPages(val sessions: List<Session>, val complete: Boolean)

/**
 * QA wave 1 B1: a server with more than one page of conversations is not a failure. The list follows `nextCursor`
 * (at most [maxPages] pages, never the same cursor twice); only a failed FIRST page fails the read. A later page
 * that fails, more pages than [maxPages] or a repeated cursor keep what was read and say the list is incomplete.
 */
internal suspend fun readSessionPages(server: ServerGateway, maxPages: Int = MAX_SESSION_PAGES): Outcome<SessionPages> {
    val first = when (val page = server.sessionPage()) {
        is Outcome.Failed -> return page
        is Outcome.Ok -> page.value
    }
    val all = ArrayList<Session>(first.items)
    val seenIds = first.items.mapTo(HashSet()) { it.id }
    val seenCursors = HashSet<String>()
    var cursor = first.nextCursor
    var pages = 1
    var complete = true
    while (cursor != null && pages < maxPages && seenCursors.add(cursor)) {
        val next: ServerPage<Session> =
            when (val page = server.sessionPage(cursor)) {
                is Outcome.Failed -> {
                    complete = false
                    break
                }
                is Outcome.Ok -> page.value
            }
        pages++
        for (s in next.items) if (seenIds.add(s.id)) all += s
        cursor = next.nextCursor
    }
    // B-NAV-031: a cursor left over (the page cap, or a cursor the server repeated) means older conversations
    // were not read; the feed says so (Flutter marks a page-capped feed incomplete).
    if (cursor != null) complete = false
    return Outcome.Ok(SessionPages(all.toImmutableList(), complete))
}

/** 10 × 100: the newest thousand conversations; older ones are not a load failure. */
internal const val MAX_SESSION_PAGES: Int = 10
