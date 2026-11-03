package io.github.eslamasabry.oc.feature.search.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/** A connected server the person can search. */
public data class SearchServer(val id: ProfileId, val name: String)

/** One conversation row as a search source returns it. Never carries message text. */
public data class SearchConversation(
    val id: SessionId,
    val title: String?,
    val directory: String?,
    val updatedMs: Long?,
)

/**
 * One page of conversations. [titlesMatched] true = the server already applied the title search; false = the page is
 * unfiltered and the screen matches titles itself. [allProjects] says whether the page covers every project on the
 * server or only the server's current project (the screen says which, never implies more).
 */
public data class SearchConversationPage(
    val items: ImmutableList<SearchConversation>,
    val next: String?,
    val titlesMatched: Boolean,
    val allProjects: Boolean,
)

/** The visible text of one message (text parts only; no tool output, no files). */
public data class SearchMessage(val id: String, val fromYou: Boolean, val text: String)

/**
 * The server side of Search, implemented by the app runtime (the Android wiring) over the live connection graph. Every
 * read is a GET; nothing here changes a conversation.
 */
public interface ConversationSearchSource {
    /** Servers with a live connection, in saved order. */
    public val servers: StateFlow<ImmutableList<SearchServer>>

    /** The server the app is using now, if any. */
    public val active: StateFlow<ProfileId?>

    /** Conversations newest first; [query] blank = no title filter. */
    public suspend fun conversations(profile: ProfileId, query: String, cursor: String?): Outcome<SearchConversationPage>

    /** The newest [limit] messages of one conversation. */
    public suspend fun recentMessages(profile: ProfileId, session: SessionId, limit: Int): Outcome<ImmutableList<SearchMessage>>
}

/** The person's recent searches on this phone (app-wide, newest first, at most [RECENT_LIMIT]). */
public interface RecentSearchStore {
    public val recent: StateFlow<ImmutableList<String>>

    public suspend fun remember(query: String): Outcome<Unit>

    public suspend fun forget(query: String): Outcome<Unit>

    public suspend fun clear(): Outcome<Unit>

    public companion object {
        public const val RECENT_LIMIT: Int = 8
    }
}
