package io.github.eslamasabry.oc.feature.search.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public enum class SearchPhase {
    /** Nothing typed: recent searches show. */
    Idle,
    Searching,
    Results,
    NoMatch,
    Failed,

    /** No server is connected, so there is nothing to search. */
    NoServer,
}

/** Inside-text search: off until the person turns it on (it reads up to 20 conversations). */
public enum class ContentPhase {
    Off,
    Searching,
    Done,
    Failed,
}

public data class SearchHitUi(
    val ref: ConversationRef,
    val title: String?,
    val project: String?,
    /** The matching excerpt, for inside-text hits only. */
    val snippet: String? = null,
    val snippetFromYou: Boolean = false,
    /** The matching message, for inside-text hits only. */
    val messageId: String? = null,
    /** When the conversation last changed, epoch ms; null = unknown. */
    val updatedMs: Long? = null,
) {
    val key: String
        get() = ref.profileId.raw + "/" + ref.sessionId.raw + "/" + (messageId ?: "")
}

public data class SearchUi(
    val query: String = "",
    val servers: ImmutableList<SearchServer> = persistentListOf(),
    val server: ProfileId? = null,
    val phase: SearchPhase = SearchPhase.Idle,
    val titleHits: ImmutableList<SearchHitUi> = persistentListOf(),
    val contentHits: ImmutableList<SearchHitUi> = persistentListOf(),
    val content: ContentPhase = ContentPhase.Off,
    /** How many conversations the inside-text search has read so far. */
    val contentRead: Int = 0,
    val allProjects: Boolean = true,
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val recent: ImmutableList<String> = persistentListOf(),
    val failure: Failure? = null,
    /** A recent-search write failed; the search itself still works. */
    val recentFailed: Boolean = false,
    /**
     * The Go to menu's rows for the chosen server (B-NAV-056); empty until the server has been
     * read.
     */
    val goTo: ImmutableList<GoToRow> = persistentListOf(),
    val destinations: ImmutableList<SearchIndexEntry> = persistentListOf(),
    /** The chosen server's newest conversations, projects and waiting count (B1). */
    val activity: SearchActivity = SearchActivity(),
    /** The clock when the state was last built, for "5 min ago". 0 = unknown (no ages shown). */
    val nowMs: Long = 0,
) {
    val serverName: String?
        get() = servers.firstOrNull { it.id == server }?.name
}

public interface SearchActions {
    public fun back()

    public fun search(query: String)

    /** Enter: the query is kept in recent searches. */
    public fun submit(query: String)

    public fun chooseServer(id: ProfileId)

    public fun searchInside(on: Boolean)

    public fun loadMore()

    public fun retry()

    public fun open(hit: SearchHitUi)

    public fun forgetRecent(query: String)

    public fun clearRecent()

    /** The way out of "No server connected". */
    public fun openServers()

    /** Open the place or conversation a Go to row names. */
    public fun goTo(row: GoToRow)

    public fun openDestination(entry: SearchIndexEntry) {}

    /** Open a project found by its name (B1 "Projects"). */
    public fun openProject(project: SearchProject) {}

    /** Open one of the recent conversations listed under an empty field. */
    public fun openRecent(conversation: SearchRecentConversation) {}
}
