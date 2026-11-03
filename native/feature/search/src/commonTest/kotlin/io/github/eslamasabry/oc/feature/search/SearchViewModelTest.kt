package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.ConversationSearchSource
import io.github.eslamasabry.oc.feature.search.presentation.RecentSearchStore
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchConversationPage
import io.github.eslamasabry.oc.feature.search.presentation.SearchMatch
import io.github.eslamasabry.oc.feature.search.presentation.SearchMessage
import io.github.eslamasabry.oc.feature.search.presentation.SearchPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchServer
import io.github.eslamasabry.oc.feature.search.presentation.SearchViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val home = ProfileId("home")
    private val work = ProfileId("work")

    private fun conversation(id: String, title: String?, dir: String? = "/src/app") =
        SearchConversation(SessionId(id), title, dir, updatedMs = null)

    private inner class Source : ConversationSearchSource {
        override val servers = MutableStateFlow<ImmutableList<SearchServer>>(persistentListOf(SearchServer(home, "Home")))
        override val active = MutableStateFlow<ProfileId?>(home)
        var serverSearch = true
        var allProjects = true
        val all = mutableListOf(conversation("s1", "Fix login"), conversation("s2", "Deploy notes"), conversation("s3", null))
        val messages = mutableMapOf<String, List<SearchMessage>>()
        val asked = mutableListOf<Triple<ProfileId, String, String?>>()
        var titlesResult: Outcome<SearchConversationPage>? = null
        var gate: CompletableDeferred<Unit>? = null
        var pageSize = 50

        /** Exact server pages by cursor, for continuation tests (an archived-only page arrives empty with a cursor). */
        var scripted: Map<String?, Outcome<SearchConversationPage>>? = null

        override suspend fun conversations(profile: ProfileId, query: String, cursor: String?): Outcome<SearchConversationPage> {
            asked += Triple(profile, query, cursor)
            scripted?.let { pages -> if (query.isNotEmpty()) return pages.getValue(cursor) }
            gate?.takeIf { query.isNotEmpty() && cursor == null }?.await()
            titlesResult?.let { if (query.isNotEmpty()) return it }
            val pool = if (serverSearch) all.filter { SearchMatch.titleMatches(it.title, query) } else all
            val start = cursor?.toInt() ?: 0
            val page = pool.drop(start).take(pageSize)
            val next = (start + pageSize).takeIf { it < pool.size }?.toString()
            return Outcome.Ok(SearchConversationPage(page.toImmutableList(), next, serverSearch && query.isNotEmpty(), allProjects))
        }

        override suspend fun recentMessages(profile: ProfileId, session: SessionId, limit: Int): Outcome<ImmutableList<SearchMessage>> =
            messages[session.raw]?.let { Outcome.Ok(it.toImmutableList()) }
                ?: Outcome.Failed(Failure.Network(NetworkWhy.Refused))
    }

    private class Recents : RecentSearchStore {
        override val recent = MutableStateFlow<ImmutableList<String>>(persistentListOf())
        var fail = false

        override suspend fun remember(query: String): Outcome<Unit> {
            if (fail) return Outcome.Failed(Failure.StorageRefused())
            recent.value = SearchMatch.remember(recent.value, query).toImmutableList()
            return Outcome.Ok(Unit)
        }

        override suspend fun forget(query: String): Outcome<Unit> {
            recent.value = recent.value.filterNot { it == query }.toImmutableList()
            return Outcome.Ok(Unit)
        }

        override suspend fun clear(): Outcome<Unit> {
            recent.value = persistentListOf()
            return Outcome.Ok(Unit)
        }
    }

    private class Navigator : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(null)
        val chats = mutableListOf<ConversationRef>()
        val opened = mutableListOf<AppRoute>()

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            chats += ref
        }

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun vm(source: Source = Source(), recents: Recents = Recents(), nav: Navigator = Navigator(), profile: ProfileId? = null) =
        SearchViewModel(SearchRoute.Conversations(profile), source, recents, nav)

    @Test
    fun `B-NAV-049 a query shows only conversations whose title matches, on the active server`() = runTest {
        val source = Source()
        val vm = vm(source)
        assertEquals(SearchPhase.Idle, vm.state.value.phase)
        vm.search("login")
        val s = vm.state.value
        assertEquals(SearchPhase.Results, s.phase)
        assertEquals(listOf("s1"), s.titleHits.map { it.ref.sessionId.raw })
        assertEquals(home, s.titleHits.single().ref.profileId)
        assertEquals("app", s.titleHits.single().project)
        assertEquals(home to "login", source.asked.last().let { it.first to it.second })
    }

    @Test
    fun `B-NAV-049 a server without title search is matched here, bounded, and says it covers one project`() = runTest {
        val source = Source().apply {
            serverSearch = false
            allProjects = false
            pageSize = 2
        }
        val vm = vm(source)
        vm.search("deploy")
        val s = vm.state.value
        assertEquals(listOf("s2"), s.titleHits.map { it.ref.sessionId.raw })
        assertEquals(false, s.allProjects)
        assertEquals(false, s.canLoadMore)
        // Two pages read (s1,s2 then s3), never past the end.
        assertEquals(listOf(null, "2"), source.asked.map { it.third })
    }

    @Test
    fun `SCREENS-NAV-34 no match and a failed search are different states with the failure kept for Details`() = runTest {
        val source = Source()
        val vm = vm(source)
        vm.search("nothing like this")
        assertEquals(SearchPhase.NoMatch, vm.state.value.phase)
        source.titlesResult = Outcome.Failed(Failure.Network(NetworkWhy.Refused))
        vm.search("again")
        assertEquals(SearchPhase.Failed, vm.state.value.phase)
        assertTrue(vm.state.value.failure is Failure.Network)
        source.titlesResult = null
        source.all += conversation("s9", "again and again")
        vm.retry()
        assertEquals(SearchPhase.Results, vm.state.value.phase)
    }

    @Test
    fun `SCREENS-NAV-34 a newer query cancels the slower older one so it can never replace the result`() = runTest {
        val source = Source()
        val vm = vm(source)
        val slow = CompletableDeferred<Unit>()
        source.gate = slow
        vm.search("login")
        assertEquals(SearchPhase.Searching, vm.state.value.phase)
        source.gate = null
        vm.search("deploy")
        slow.complete(Unit)
        assertEquals(listOf("s2"), vm.state.value.titleHits.map { it.ref.sessionId.raw })
        assertEquals("deploy", vm.state.value.query)
    }

    @Test
    fun `SCREENS-NAV-34 search inside reads recent conversations only when turned on and shows the matching words`() = runTest {
        val source = Source()
        source.messages["s1"] = listOf(SearchMessage("m1", true, "can you check the rollback plan"))
        source.messages["s2"] = listOf(SearchMessage("m2", false, "nothing"))
        source.messages["s3"] = listOf(SearchMessage("m3", false, "Rollback done"))
        val vm = vm(source)
        vm.search("rollback")
        assertEquals(SearchPhase.NoMatch, vm.state.value.phase)
        assertTrue(source.asked.none { it.second.isEmpty() }, "inside text is never read while the switch is off")
        vm.searchInside(true)
        val s = vm.state.value
        assertEquals(ContentPhase.Done, s.content)
        assertEquals(SearchPhase.Results, s.phase)
        assertEquals(listOf("s1", "s3"), s.contentHits.map { it.ref.sessionId.raw })
        assertEquals(listOf("m1", "m3"), s.contentHits.map { it.messageId })
        assertTrue(s.contentHits.first().snippetFromYou)
        assertEquals(3, s.contentRead)
    }

    @Test
    fun `SCREENS-NAV-34 inside search that cannot read anything says so and keeps title results`() = runTest {
        val source = Source()
        val vm = vm(source)
        vm.searchInside(true)
        vm.search("login")
        val s = vm.state.value
        assertEquals(ContentPhase.Failed, s.content)
        assertEquals(listOf("s1"), s.titleHits.map { it.ref.sessionId.raw })
        assertEquals(SearchPhase.Results, s.phase)
    }

    @Test
    fun `SCREENS-NAV-34 opening a result opens that conversation on its server and remembers the search`() = runTest {
        val source = Source()
        val recents = Recents()
        val nav = Navigator()
        val vm = vm(source, recents, nav)
        vm.search("login")
        vm.open(vm.state.value.titleHits.single())
        assertEquals(listOf(ConversationRef(home, SessionId("s1"))), nav.chats)
        assertEquals(listOf("login"), vm.state.value.recent)
    }

    @Test
    fun `SCREENS-NAV-34 recent searches are kept on Enter, removable one by one and clearable`() = runTest {
        val recents = Recents()
        val vm = vm(recents = recents)
        vm.submit("  deploy  ")
        vm.submit("login")
        assertEquals(listOf("login", "deploy"), vm.state.value.recent)
        vm.forgetRecent("deploy")
        assertEquals(listOf("login"), vm.state.value.recent)
        vm.clearRecent()
        assertTrue(vm.state.value.recent.isEmpty())
        recents.fail = true
        vm.submit("x")
        assertTrue(vm.state.value.recentFailed)
    }

    @Test
    fun `SCREENS-NAV-34 the person can pick which server to search and the result names that server`() = runTest {
        val source = Source()
        source.servers.value = persistentListOf(SearchServer(home, "Home"), SearchServer(work, "Work"))
        val vm = vm(source, profile = work)
        assertEquals(work, vm.state.value.server)
        assertEquals("Work", vm.state.value.serverName)
        vm.search("login")
        assertEquals(work, vm.state.value.titleHits.single().ref.profileId)
        vm.chooseServer(home)
        assertEquals(home, vm.state.value.titleHits.single().ref.profileId)
    }

    @Test
    fun `SCREENS-NAV-34 with no connected server the screen says so and offers Servers`() = runTest {
        val source = Source()
        source.servers.value = persistentListOf()
        source.active.value = null
        val nav = Navigator()
        val vm = vm(source, nav = nav)
        assertEquals(SearchPhase.NoServer, vm.state.value.phase)
        vm.openServers()
        assertEquals(listOf<AppRoute>(ServersRoute.Home), nav.opened)
    }

    @Test
    fun `SCREENS-NAV-34 load more appends the next server page and a failure keeps what is shown`() = runTest {
        val source = Source().apply { pageSize = 1 }
        source.all.clear()
        source.all += listOf(conversation("a", "plan one"), conversation("b", "plan two"))
        val vm = vm(source)
        vm.search("plan")
        assertTrue(vm.state.value.canLoadMore)
        vm.loadMore()
        assertEquals(listOf("a", "b"), vm.state.value.titleHits.map { it.ref.sessionId.raw })
        assertEquals(false, vm.state.value.canLoadMore)
    }

    @Test
    fun `B-PROTO-020 an empty first page that keeps a cursor offers load more and reaches later matches`() = runTest {
        val source = Source()
        fun page(items: List<SearchConversation>, next: String?) =
            Outcome.Ok(SearchConversationPage(items.toImmutableList(), next, titlesMatched = true, allProjects = true))
        source.scripted =
            mapOf(
                null to page(emptyList(), "c1"),
                "c1" to Outcome.Failed(Failure.Network(NetworkWhy.Refused)),
            )
        val vm = vm(source)
        vm.search("plan")
        assertEquals(SearchPhase.NoMatch, vm.state.value.phase)
        assertTrue(vm.state.value.canLoadMore)
        vm.loadMore()
        assertTrue(vm.state.value.loadMoreFailed)
        assertEquals(SearchPhase.NoMatch, vm.state.value.phase)
        source.scripted = mapOf("c1" to page(emptyList(), "c2"), "c2" to page(listOf(conversation("late", "plan later")), null))
        vm.loadMore()
        assertEquals(SearchPhase.NoMatch, vm.state.value.phase)
        assertTrue(vm.state.value.canLoadMore)
        vm.loadMore()
        assertEquals(SearchPhase.Results, vm.state.value.phase)
        assertEquals(listOf("late"), vm.state.value.titleHits.map { it.ref.sessionId.raw })
        assertEquals(false, vm.state.value.canLoadMore)
    }
}
