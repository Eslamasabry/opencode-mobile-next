package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.feed.PaseoTitles
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive

/**
 * One row per conversation (B-AGENT-180, Dart `PaseoChatFeedSource.refreshChatFeed`, lib/paseo/chat_feed_source.dart).
 * Paseo keeps one record per resume or import of a runtime session (`provider:sessionId`, [PaseoTitles.sessionKey]); a
 * listing keeps only the newest record of each conversation, and an older record gives the kept one its title when the
 * kept one has none. A record without a persistence handle is its own conversation and is never folded.
 *
 * Older records stay on the daemon: Flutter archives them as housekeeping, which needs the saved-title writer; here
 * they are only left out of the list (they still open by id) and are archived with their conversation
 * ([archiveOlderCopies]), so archiving a row does not bring its older record back.
 *
 * One listing runs across its pages: a first page (no cursor) starts a new one, and what its pages kept is carried to
 * the next page by the cursor that continues it ([PaseoListedCopies]). Many readers list at once (the home list, the
 * monitors, the projects pages), so the carried state is per cursor, never one shared map. Lane-confined, like every
 * gateway map.
 */
internal fun PaseoGateway.withoutOlderCopies(page: ServerPage<Session>, cursor: String?): ServerPage<Session> {
    // conversation key -> the record this listing keeps for it, on an earlier page or this one
    val listed = HashMap(listedCopies.before(cursor))
    val kept = ArrayList<Session>(page.items.size)
    for (session in page.items) {
        val id = session.id.raw
        val key = PaseoTitles.sessionKey(agents[id])
        val keeper = key?.let(listed::get)
        if (key == null || keeper == null || keeper == id) {
            if (key != null) listed[key] = id
            kept += session
            continue
        }
        val at = kept.indexOfFirst { it.id.raw == keeper }
        when {
            // The keeper came on an earlier page (the daemon lists newest first): this one is older.
            at < 0 -> sessions[keeper]?.let { titledFrom(it, session) }
            (session.time?.updatedMs ?: 0L) > (kept[at].time?.updatedMs ?: 0L) -> {
                listed[key] = id
                kept[at] = titledFrom(session, kept[at])
            }
            else -> kept[at] = titledFrom(kept[at], session)
        }
    }
    listedCopies.continueAt(page.nextCursor, listed)
    return if (kept.size == page.items.size) page else page.copy(items = kept.toImmutableList())
}

/**
 * A record an `agent_update` pushed: null when a newer record of the same conversation is known here (that record's
 * row stands for this one, so the push must not add a second row); otherwise the record, with an older record's
 * title when it has none of its own. Known times only grow, so a known newer time is a true one.
 */
internal fun PaseoGateway.pushedRecord(session: Session): Session? {
    val id = session.id.raw
    val key = PaseoTitles.sessionKey(agents[id]) ?: return session
    val at = session.time?.updatedMs ?: 0L
    val copies = agents.entries.filter { (other, agent) ->
        other != id && other !in drafts && PaseoTitles.sessionKey(agent) == key
    }.mapNotNull { sessions[it.key] }
    if (copies.any { (it.time?.updatedMs ?: 0L) > at }) return null
    return copies.fold(session) { kept, older -> titledFrom(kept, older) }
}

/**
 * The quiet records of [id]'s conversation older than [id] itself: archiving a conversation archives these too, or
 * the newest of them would come back as the conversation's row. Read before [id] is forgotten.
 */
internal fun PaseoGateway.olderCopiesOf(id: String): List<String> {
    val key = PaseoTitles.sessionKey(agents[id]) ?: return emptyList()
    val at = sessions[id]?.time?.updatedMs ?: return emptyList()
    return agents.entries.filter { (other, agent) ->
        other != id && other !in drafts && PaseoTitles.sessionKey(agent) == key &&
            (sessions[other]?.time?.updatedMs ?: Long.MAX_VALUE) <= at &&
            agent.str("status") in QUIET && statuses[other] != SessionStatus.Busy && other !in turnActive
    }.map { it.key }
}

/** Archives [ids] (older records of an archived conversation). A refusal leaves that one for the next archive. */
internal suspend fun PaseoGateway.archiveOlderCopies(ids: List<String>) {
    for (id in ids) {
        try {
            transport.request("archive_agent_request", jsonOf("agentId" to real(id)), mutation = true)
            forget(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaseoException) {
            // The conversation itself is archived; an older record that stays shows up again as its row.
        }
    }
}

private val QUIET = setOf("idle", "closed", "error")

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

/**
 * What each listing in progress kept on its pages so far (conversation key -> record id), by the cursor that
 * continues it. Bounded: a listing whose state was dropped only shows an older record its earlier pages already
 * stood for.
 */
internal class PaseoListedCopies {
    private val byCursor = LinkedHashMap<String, Map<String, String>>()

    fun before(cursor: String?): Map<String, String> = cursor?.let(byCursor::get).orEmpty()

    fun continueAt(cursor: String?, kept: Map<String, String>) {
        if (cursor == null) return
        byCursor.remove(cursor)
        byCursor[cursor] = kept
        while (byCursor.size > MAX_LISTINGS) byCursor.remove(byCursor.keys.first())
    }

    fun clear() = byCursor.clear()

    private companion object {
        const val MAX_LISTINGS = 16
    }
}
