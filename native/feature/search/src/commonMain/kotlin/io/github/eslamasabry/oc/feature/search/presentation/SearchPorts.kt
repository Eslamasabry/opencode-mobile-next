package io.github.eslamasabry.oc.feature.search.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

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
 * One page of conversations. [titlesMatched] true = the server already applied the title search;
 * false = the page is unfiltered and the screen matches titles itself. [allProjects] says whether
 * the page covers every project on the server or only the server's current project (the screen says
 * which, never implies more).
 */
public data class SearchConversationPage(
    val items: ImmutableList<SearchConversation>,
    val next: String?,
    val titlesMatched: Boolean,
    val allProjects: Boolean,
)

/**
 * What the Go to menu is built from for one server: what it can open (never its flavour) and the
 * newest conversations.
 */
public data class SearchGoToFacts(
    val fileBrowsing: Boolean,
    val terminal: Boolean,
    val projectManagement: Boolean,
    /** Newest first; the menu shows the last three. */
    val recent: ImmutableList<Pair<ConversationRef, String?>>,
    val capabilities: io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities =
        io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities.Unknown,
    val platform: io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities =
        io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities.desktop(),
    val managedOnPhone: Boolean = false,
    val hasShell: Boolean = true,
    val projectDirectory: String? = null,
    val projectRef: ConversationRef? = null,
    /** This server has an AI Team set up (Flutter `scope.hasTeam`). */
    val hasTeam: Boolean = false,
)

/** What a conversation is doing now, as Search's rows say it (the home's words). */
public enum class SearchRowStatus { None, Working, NeedsYou, Failed }

/** One of the server's newest conversations, with what the home knows of it (B1 "Recent conversations"). */
public data class SearchRecentConversation(
    val ref: ConversationRef,
    val title: String?,
    val project: String?,
    val updatedMs: Long? = null,
    /** The agent's bundled mark slug; blank = the neutral mark. */
    val agentMark: String = "",
    val agentName: String? = null,
    val status: SearchRowStatus = SearchRowStatus.None,
)

/** A project the server's conversations live in, found by its name (B1 "Projects"). */
public data class SearchProject(val name: String, val directory: String, val conversations: Int)

/** The server's live picture for Search: newest conversations, their projects, and how many wait on the person. */
public data class SearchActivity(
    /** Newest first. */
    val recent: ImmutableList<SearchRecentConversation> = kotlinx.collections.immutable.persistentListOf(),
    /** Most recently active first. */
    val projects: ImmutableList<SearchProject> = kotlinx.collections.immutable.persistentListOf(),
    val needsYou: Int = 0,
)

/** The visible text of one message (text parts only; no tool output, no files). */
public data class SearchMessage(val id: String, val fromYou: Boolean, val text: String)

/**
 * The server side of Search, implemented by the app runtime (the Android wiring) over the live
 * connection graph. Every read is a GET; nothing here changes a conversation.
 */
public interface ConversationSearchSource {
    public val platformCapabilities:
        io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
        get() = io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities.desktop()

    public val hasShell: Boolean
        get() = true

    /** Servers with a live connection, in saved order. */
    public val servers: StateFlow<ImmutableList<SearchServer>>

    /** The server the app is using now, if any. */
    public val active: StateFlow<ProfileId?>

    /** Conversations newest first; [query] blank = no title filter. */
    public suspend fun conversations(
        profile: ProfileId,
        query: String,
        cursor: String?,
    ): Outcome<SearchConversationPage>

    /** What the Go to menu offers for [profile]; null when the server cannot be read now. */
    public suspend fun goToFacts(profile: ProfileId): SearchGoToFacts? = null

    /** Live capabilities remove stale destinations while Search remains open. */
    public fun navigationFacts(profile: ProfileId): Flow<SearchGoToFacts?> = flow {
        emit(goToFacts(profile))
    }

    public suspend fun projectHealth(
        profile: ProfileId,
        directory: String,
    ): Outcome<ProjectHealthSnapshot> =
        Outcome.Failed(
            io.github.eslamasabry.oc.core.common.Failure.Unavailable(
                io.github.eslamasabry.oc.core.common.FeatureId("projectHealth")
            )
        )

    /** [profile]'s newest conversations, projects and waiting count, live; empty when the app has none. */
    public fun activity(profile: ProfileId): Flow<SearchActivity> = flowOf(SearchActivity())

    /** The newest [limit] messages of one conversation. */
    public suspend fun recentMessages(
        profile: ProfileId,
        session: SessionId,
        limit: Int,
    ): Outcome<ImmutableList<SearchMessage>>
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
