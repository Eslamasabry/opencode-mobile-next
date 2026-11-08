package io.github.eslamasabry.oc.feature.search.presentation

/** One conversation under "Conversations": the hit, and what the home knows of it (mark, state, last change). */
public data class SearchConversationResult(
    val hit: SearchHitUi,
    val agentMark: String = "",
    val agentName: String? = null,
    val status: SearchRowStatus = SearchRowStatus.None,
    val updatedMs: Long? = null,
)

/**
 * Search's grouping rules (B1), apart from the drawing: the conversations a query found (title matches first, then
 * matches inside messages, one row per conversation), the projects whose names match, the place shortcuts over an empty
 * field and the recent conversations under them. Pure; the screen only lays them out.
 */
public object SearchResults {
    /** Conversation rows shown before "See all N". */
    public const val COLLAPSED: Int = 3

    /** Recent conversations listed under an empty field. */
    public const val RECENT: Int = 6

    /** Projects listed for a query. */
    public const val PROJECTS: Int = 3

    /**
     * One row per conversation, title matches first in the server's order, then conversations found only inside their
     * messages. A conversation found both ways keeps its place among the titles and shows the matching message when
     * [inside] is on (the search reads messages only then). Each row takes the agent, state and time the server's live
     * list has for it.
     */
    public fun conversations(state: SearchUi, inside: Boolean = state.content != ContentPhase.Off): List<SearchConversationResult> {
        val known = state.activity.recent.associateBy { it.ref }
        val byRef = LinkedHashMap<io.github.eslamasabry.oc.core.common.ids.ConversationRef, SearchHitUi>()
        for (hit in state.titleHits) byRef.putIfAbsent(hit.ref, hit)
        if (inside) {
            for (hit in state.contentHits) {
                val title = byRef[hit.ref]
                byRef[hit.ref] =
                    if (title == null) hit
                    else if (title.snippet == null) title.copy(snippet = hit.snippet, snippetFromYou = hit.snippetFromYou, messageId = hit.messageId)
                    else title
            }
        }
        return byRef.values.map { hit ->
            val live = known[hit.ref]
            SearchConversationResult(
                hit = if (inside) hit else hit.copy(snippet = null, messageId = null),
                agentMark = live?.agentMark.orEmpty(),
                agentName = live?.agentName,
                status = live?.status ?: SearchRowStatus.None,
                updatedMs = hit.updatedMs ?: live?.updatedMs,
            )
        }
    }

    /** Projects whose name holds every typed word (case ignored), in the order the server last used them. */
    public fun projects(projects: List<SearchProject>, query: String): List<SearchProject> {
        val words = SearchMatch.normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return projects.filter { p -> words.all { p.name.contains(it, ignoreCase = true) } }.take(PROJECTS)
    }

    /**
     * The place shortcuts over an empty field, in this order: Needs you, Projects (only where the server manages
     * projects), Settings, then Servers (or Add a server with none saved). Until the server is read: Needs you,
     * Settings and Servers.
     */
    public fun shortcuts(goTo: List<GoToRow>): List<GoToPlace> {
        val places = goTo.mapNotNull { it.place }.toSet()
        if (places.isEmpty()) return listOf(GoToPlace.NeedsYou, GoToPlace.Settings, GoToPlace.Servers)
        return buildList {
            add(GoToPlace.NeedsYou)
            if (GoToPlace.Projects in places) add(GoToPlace.Projects)
            add(GoToPlace.Settings)
            add(if (GoToPlace.AddServer in places) GoToPlace.AddServer else GoToPlace.Servers)
        }
    }

    /** The recent conversations under an empty field: the live list, else the Go to menu's last conversations. */
    public fun recent(state: SearchUi): List<SearchRecentConversation> {
        if (state.activity.recent.isNotEmpty()) return state.activity.recent.take(RECENT)
        return state.goTo
            .filter { it.section == GoToSection.Recent && it.ref != null }
            .map { SearchRecentConversation(requireNotNull(it.ref), it.title, project = null) }
            .take(RECENT)
    }
}
