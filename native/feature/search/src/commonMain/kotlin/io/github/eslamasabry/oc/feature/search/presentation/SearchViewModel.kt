package io.github.eslamasabry.oc.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search across one server's conversations: the server's own title search (every project when it can), and, only when
 * the person turns it on, the text of the [SearchMatch.CONTENT_CONVERSATIONS] most recent conversations. A newer query
 * cancels the older one, so a slow answer never replaces a newer result. Nothing here sends or changes anything.
 */
@AssistedInject
public class SearchViewModel(
    @Assisted private val route: SearchRoute.Conversations,
    private val source: ConversationSearchSource,
    private val recents: RecentSearchStore,
    private val nav: AppNavigator,
) : ViewModel(), SearchActions {
    private val local = MutableStateFlow(SearchUi(server = route.profile))
    public val state: StateFlow<SearchUi> = local.asStateFlow()

    private var cancelSearch: () -> Unit = {}
    private var cancelMore: () -> Unit = {}
    private var cursor: String? = null
    private var titlesDone = false
    private var generation = 0

    init {
        viewModelScope.launch {
            source.servers.collect { servers ->
                val before = local.value.server
                val chosen =
                    before?.takeIf { id -> servers.any { it.id == id } }
                        ?: source.active.value?.takeIf { id -> servers.any { it.id == id } }
                        ?: servers.firstOrNull()?.id
                local.update { it.copy(servers = servers, server = chosen) }
                val noServerShown = local.value.phase == SearchPhase.NoServer
                if (chosen != before || (chosen == null) != noServerShown) restart()
                if (chosen != before || local.value.goTo.isEmpty()) loadGoTo(chosen)
            }
        }
        viewModelScope.launch { recents.recent.collect { recent -> local.update { it.copy(recent = recent) } } }
    }

    override fun back(): Unit = nav.back()

    override fun search(query: String) {
        val q = SearchMatch.normalize(query)
        if (q == local.value.query && local.value.phase != SearchPhase.Failed) return
        local.update { it.copy(query = q) }
        restart()
    }

    override fun submit(query: String) {
        search(query)
        remember(SearchMatch.normalize(query))
    }

    override fun chooseServer(id: ProfileId) {
        if (local.value.server == id || local.value.servers.none { it.id == id }) return
        local.update { it.copy(server = id) }
        restart()
        loadGoTo(id)
    }

    override fun searchInside(on: Boolean) {
        val now = local.value.content != ContentPhase.Off
        if (now == on) return
        local.update { it.copy(content = if (on) ContentPhase.Searching else ContentPhase.Off) }
        restart()
    }

    override fun retry(): Unit = restart()

    override fun loadMore() {
        val s = local.value
        val server = s.server ?: return
        val next = cursor ?: return
        if (!s.canLoadMore || s.loadingMore) return
        val mine = generation
        local.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        viewModelScope.launch {
            val page = source.conversations(server, s.query, next)
            if (mine != generation) return@launch
            when (page) {
                is Outcome.Failed -> local.update { it.copy(loadingMore = false, loadMoreFailed = true) }
                is Outcome.Ok -> {
                    cursor = page.value.next
                    val more = hits(server, page.value, s.query)
                    // A page can be empty (all archived) yet keep a cursor: the phase follows what has arrived.
                    local.update {
                        settle(
                            it.copy(
                                titleHits = (it.titleHits + more).distinctBy { hit -> hit.key }.toImmutableList(),
                                loadingMore = false,
                                canLoadMore = page.value.next != null,
                            )
                        )
                    }
                }
            }
        }.also { job -> cancelMore = { job.cancel() } }
    }

    override fun open(hit: SearchHitUi) {
        remember(local.value.query)
        // ChatRoute has no message anchor yet; the row shows the matching words (integrator item in the unit report).
        nav.openChat(hit.ref)
    }

    override fun openServers(): Unit = nav.open(ServersRoute.Home)

    override fun goTo(row: GoToRow) {
        val server = local.value.server
        val ref = row.ref
        when (row.place) {
            null -> if (ref != null) nav.openChat(ref)
            GoToPlace.NeedsYou -> nav.open(ChatsRoute.All(null))
            GoToPlace.Projects -> nav.open(ProjectsRoute.Picker(server))
            GoToPlace.Settings -> nav.open(SettingsRoute.Home)
            GoToPlace.Servers,
            GoToPlace.AddServer -> nav.open(ServersRoute.Home)
            GoToPlace.NewConversation -> nav.open(ChatsRoute.New(server))
            GoToPlace.Files,
            GoToPlace.Terminal,
            GoToPlace.Help -> Unit
        }
    }

    private fun loadGoTo(server: ProfileId?) {
        if (server == null) {
            local.update { it.copy(goTo = persistentListOf()) }
            return
        }
        viewModelScope.launch {
            val facts = source.goToFacts(server)
            if (local.value.server != server) return@launch
            local.update { s ->
                s.copy(
                    goTo =
                        facts
                            ?.let {
                                GoToMenu.rows(
                                    GoToInput(
                                        fileBrowsing = false,
                                        terminal = false,
                                        projectManagement = it.projectManagement,
                                        hasSavedServer = s.servers.isNotEmpty(),
                                        waiting = 0,
                                        project = null,
                                        recent = it.recent,
                                    )
                                ).toImmutableList()
                            } ?: persistentListOf()
                )
            }
        }
    }

    override fun forgetRecent(query: String) {
        viewModelScope.launch { recentResult(recents.forget(query)) }
    }

    override fun clearRecent() {
        viewModelScope.launch { recentResult(recents.clear()) }
    }

    private fun remember(query: String) {
        if (query.isEmpty()) return
        viewModelScope.launch { recentResult(recents.remember(query)) }
    }

    private fun recentResult(result: Outcome<Unit>) {
        local.update { it.copy(recentFailed = result is Outcome.Failed) }
    }

    private fun restart() {
        cancelSearch()
        cancelMore()
        generation++
        cursor = null
        titlesDone = false
        val s = local.value
        val inside = s.content != ContentPhase.Off
        val cleared =
            s.copy(
                titleHits = persistentListOf(),
                contentHits = persistentListOf(),
                contentRead = 0,
                content = if (inside) ContentPhase.Searching else ContentPhase.Off,
                canLoadMore = false,
                loadingMore = false,
                loadMoreFailed = false,
                failure = null,
            )
        val server = s.server
        if (server == null) {
            local.value = cleared.copy(phase = SearchPhase.NoServer)
            return
        }
        if (s.query.isEmpty()) {
            local.value = cleared.copy(phase = SearchPhase.Idle, content = if (inside) ContentPhase.Done else ContentPhase.Off)
            return
        }
        local.value = cleared.copy(phase = SearchPhase.Searching)
        val mine = generation
        val query = s.query
        viewModelScope.launch {
            titles(server, query, mine)
            if (inside && mine == generation) inside(server, query, mine)
        }.also { job -> cancelSearch = { job.cancel() } }
    }

    private suspend fun titles(server: ProfileId, query: String, mine: Int) {
        var scanned = 0
        var page = source.conversations(server, query, null)
        val found = mutableListOf<SearchHitUi>()
        while (true) {
            if (mine != generation) return
            val ok =
                when (val current = page) {
                    is Outcome.Failed -> {
                        val failure = current.failure
                        titlesDone = true
                        local.update { it.copy(phase = SearchPhase.Failed, failure = failure) }
                        return
                    }
                    is Outcome.Ok -> current.value
                }
            found += hits(server, ok, query)
            scanned += ok.items.size
            cursor = ok.next
            // A server that cannot search titles itself: read further pages here, bounded, then stop.
            val next = ok.next
            if (ok.titlesMatched || next == null || scanned >= CLIENT_SCAN_LIMIT) {
                titlesDone = true
                local.update {
                    settle(
                        it.copy(
                            titleHits = found.distinctBy { hit -> hit.key }.toImmutableList(),
                            allProjects = ok.allProjects,
                            canLoadMore = ok.titlesMatched && next != null,
                        )
                    )
                }
                return
            }
            page = source.conversations(server, query, next)
        }
    }

    private suspend fun inside(server: ProfileId, query: String, mine: Int) {
        val recent =
            when (val page = source.conversations(server, "", null)) {
                is Outcome.Failed -> {
                    if (mine == generation) local.update { settle(it.copy(content = ContentPhase.Failed)) }
                    return
                }
                is Outcome.Ok -> page.value.items.take(SearchMatch.CONTENT_CONVERSATIONS)
            }
        var read = 0
        var failed = 0
        for (conversation in recent) {
            if (mine != generation) return
            when (val messages = source.recentMessages(server, conversation.id, SearchMatch.CONTENT_MESSAGES)) {
                is Outcome.Failed -> failed++
                is Outcome.Ok -> {
                    read++
                    val match = SearchMatch.newestMatch(messages.value, query)
                    if (mine != generation) return
                    local.update { s ->
                        val added =
                            match?.let { (message, excerpt) ->
                                SearchHitUi(
                                    ConversationRef(server, conversation.id),
                                    conversation.title,
                                    SearchMatch.projectName(conversation.directory),
                                    snippet = excerpt,
                                    snippetFromYou = message.fromYou,
                                    messageId = message.id,
                                )
                            }
                        settle(
                            s.copy(
                                contentRead = read,
                                contentHits = if (added == null) s.contentHits else (s.contentHits + added).toImmutableList(),
                            )
                        )
                    }
                }
            }
        }
        if (mine != generation) return
        val outcome = if (read == 0 && failed > 0) ContentPhase.Failed else ContentPhase.Done
        local.update { settle(it.copy(content = outcome)) }
    }

    private fun hits(server: ProfileId, page: SearchConversationPage, query: String): List<SearchHitUi> =
        page.items
            .filter { page.titlesMatched || SearchMatch.titleMatches(it.title, query) }
            .map { SearchHitUi(ConversationRef(server, it.id), it.title, SearchMatch.projectName(it.directory)) }

    /** The phase that follows from what has arrived so far. */
    private fun settle(s: SearchUi): SearchUi {
        if (s.phase == SearchPhase.Failed || s.phase == SearchPhase.Idle || s.phase == SearchPhase.NoServer) return s
        val any = s.titleHits.isNotEmpty() || s.contentHits.isNotEmpty()
        val phase =
            when {
                any -> SearchPhase.Results
                !titlesDone || s.content == ContentPhase.Searching -> SearchPhase.Searching
                else -> SearchPhase.NoMatch
            }
        return s.copy(phase = phase)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SearchRoute.Conversations): SearchViewModel
    }

    public companion object {
        /** Conversations read when the server cannot search titles itself (plan 07 A12: up to 400). */
        public const val CLIENT_SCAN_LIMIT: Int = 400
    }
}
