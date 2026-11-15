package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversationPage
import io.github.eslamasabry.oc.feature.search.presentation.SearchMatch
import kotlinx.collections.immutable.toImmutableList

/**
 * Search finds every conversation the home lists for a connection. The gateway read answers for one
 * folder (or one protocol's own list); the home's merged feed also holds the phone agents' conversations
 * and the rows kept from earlier runs. The first page therefore also carries the feed's top-level rows
 * of [profile] that the page does not hold, filtered by title when the page's own filter was applied.
 */
internal fun withFeedRows(
    page: SearchConversationPage,
    feed: List<ChatFeedItem>,
    profile: ProfileId,
    query: String,
): SearchConversationPage {
    val have = page.items.mapTo(HashSet()) { it.id }
    val extra =
        feed
            .filter { it.profileId == profile && !it.isSubagent && it.sessionId !in have }
            .filter { !page.titlesMatched || SearchMatch.titleMatches(it.title, query) }
            .distinctBy { it.sessionId }
            .map { SearchConversation(it.sessionId, it.title, it.directory, it.lastActivityMs.takeIf { ms -> ms > 0 }) }
    if (extra.isEmpty()) return page
    val merged = (page.items + extra).sortedByDescending { it.updatedMs ?: 0L }
    return page.copy(items = merged.toImmutableList())
}
