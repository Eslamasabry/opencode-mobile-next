package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.protocol.paseo.feed.PaseoTitles
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonPrimitive

/**
 * One row per conversation (B-AGENT-180, Dart `PaseoChatFeedSource.refreshChatFeed`, lib/paseo/chat_feed_source.dart).
 * Paseo keeps one record per resume or import of a runtime session (`provider:sessionId`); a listing keeps only the
 * newest record of each conversation, and an older record gives the kept one its title when the kept one has none.
 * Older records stay on the daemon: Flutter archives them as housekeeping, which needs the saved-title writer; here
 * they are only left out of the list (they still open by id). One listing runs across its pages: a first page (no
 * cursor) starts a new one. Lane-confined, like every gateway map.
 */
internal fun PaseoGateway.withoutOlderCopies(page: ServerPage<Session>, firstPage: Boolean): ServerPage<Session> {
    if (firstPage) listedCopies.clear()
    val kept = ArrayList<Session>(page.items.size)
    for (session in page.items) {
        val id = session.id.raw
        val key = PaseoTitles.sessionKey(agents[id])
        val keeper = key?.let(listedCopies::get)
        if (key == null || keeper == null || keeper == id) {
            if (key != null) listedCopies[key] = id
            kept += session
            continue
        }
        val at = kept.indexOfFirst { it.id.raw == keeper }
        when {
            // The keeper came on an earlier page (the daemon lists newest first): this one is older.
            at < 0 -> sessions[keeper]?.let { titledFrom(it, session) }
            (session.time?.updatedMs ?: 0L) > (kept[at].time?.updatedMs ?: 0L) -> {
                listedCopies[key] = id
                kept[at] = titledFrom(session, kept[at])
            }
            else -> kept[at] = titledFrom(kept[at], session)
        }
    }
    return if (kept.size == page.items.size) page else page.copy(items = kept.toImmutableList())
}

/** [keeper] as listed, with [older]'s title when [keeper]'s record has none of its own. */
private fun PaseoGateway.titledFrom(keeper: Session, older: Session): Session {
    if (PaseoTitles.hasOwnTitle(agents[keeper.id.raw])) return keeper
    val title = agents[older.id.raw]?.str("title")?.takeIf { it.isNotBlank() } ?: return keeper
    keepTitle(keeper.id.raw, title)
    return keeper.copy(title = title)
}

/**
 * The title a resume lost (Dart `keepTitle`, gateway/copies.dart): shown while the record has none of its own. Nothing
 * is written to the helper: `update_agent_request` would move the conversation to the top as if just used.
 */
internal fun PaseoGateway.keepTitle(id: String, title: String) {
    if (title.isBlank()) return
    titleHints[id] = title
    if (PaseoTitles.hasOwnTitle(agents[id])) return
    agents[id]?.let { agents[id] = it.with("title", JsonPrimitive(title)) }
    sessions[id]?.let { sessions[id] = it.copy(title = title) }
}
