package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.testing.FakeOperationsGateway
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.feature.chats.presentation.AllPhase
import io.github.eslamasabry.oc.feature.chats.presentation.AllProblemKind
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServers
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** All conversations (07 §5 B12, SCREENS-NAV-34) over fake gateways; every action reaches the row's own server. */
@OptIn(ExperimentalCoroutinesApi::class)
class AllConversationsViewModelTest {
    private class Nav : AppNavigator {
        val calls = mutableListOf<String>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            calls += "open $route"
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            calls += "chat ${ref.profileId.raw}/${ref.sessionId.raw}"
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            calls += "back"
        }
    }

    /** Pages of a fake server: page i answers cursor "c$i"; [failMore] makes the next older page fail once. */
    private class Paged(val inner: FakeServerGateway, val pages: List<List<Session>>) : ServerGateway by inner {
        var failMore: Failure? = null
        val asked = mutableListOf<String?>()

        override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> {
            asked += cursor
            if (cursor != null) failMore?.let {
                failMore = null
                return it.failed()
            }
            val index = cursor?.removePrefix("c")?.toInt() ?: 0
            val next = if (index + 1 < pages.size) "c${index + 1}" else null
            return ServerPage(pages[index].toImmutableList(), next).ok()
        }
    }

    private class Servers(list: List<ConversationServer>) : ConversationServers {
        override val servers = MutableStateFlow<ImmutableList<ConversationServer>>(list.toImmutableList())
    }

    private fun session(id: String, at: Long, title: String = "Chat $id", dir: String = "/work/api", parent: String? = null, archived: Boolean = false) =
        Session(
            SessionId(id),
            title,
            parentId = parent?.let(::SessionId),
            directory = dir,
            time = SessionTime(createdMs = at, updatedMs = at, archivedMs = if (archived) at else null),
        )

    private val p1 = ProfileId("p1")
    private val p2 = ProfileId("p2")

    private fun server(profile: ProfileId, gateway: ServerGateway, ops: ServerOperationsGateway? = null, name: String = profile.raw) =
        ConversationServer(profile, name, gateway, ops)

    private fun vm(nav: Nav, vararg servers: ConversationServer, filter: String? = null) =
        ChatsAllViewModel(ChatsRoute.All(filter), nav, Servers(servers.toList()), AppCoroutineScope(UnconfinedTestDispatcher()))

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-NAV-030 equal session ids from two servers stay two rows and each opens its own server`() {
        val nav = Nav()
        val a = FakeServerGateway(listOf(session("s1", 20, "On laptop")))
        val b = FakeServerGateway(listOf(session("s1", 10, "On desktop")))
        val vm = vm(nav, server(p1, a, name = "laptop"), server(p2, b, name = "desktop"))
        val rows = vm.state.value.rows
        assertEquals(listOf("p1/s1", "p2/s1"), rows.map { it.key })
        assertEquals(listOf("laptop", "desktop"), rows.map { it.server })
        vm.tapRow("p2/s1")
        assertEquals(listOf("chat p2/s1"), nav.calls)
    }

    @Test
    fun `B-NAV-067 rows use the cleaned title and subagent sessions stay out of the list`() {
        val gateway =
            FakeServerGateway(
                listOf(
                    session("a", 30, "Fix login <tool_call>{}"),
                    session("b", 20, "New session - 2026-09-02T14:47:06Z"),
                    session("child", 25, parent = "a"),
                )
            )
        val vm = vm(Nav(), server(p1, gateway))
        val rows = vm.state.value.rows
        assertEquals(listOf("p1/a", "p1/b"), rows.map { it.key })
        assertEquals(RowTitle.Text("Fix login"), rows[0].title)
        assertEquals(RowTitle.New, rows[1].title)
    }

    @Test
    fun `SCREENS-NAV-34 older pages load on request, repeat rows are dropped, a failed page keeps its cursor for Try again`() {
        val paged =
            Paged(
                FakeServerGateway(),
                listOf(
                    listOf(session("a", 30), session("b", 20)),
                    listOf(session("b", 20), session("c", 10)),
                    listOf(session("d", 5)),
                ),
            )
        val vm = vm(Nav(), server(p1, paged))
        assertEquals(AllPhase.List, vm.state.value.phase)
        assertTrue(vm.state.value.canLoadMore)
        vm.loadMore()
        assertEquals(listOf("a", "b", "c"), vm.state.value.rows.map { it.ref.sessionId.raw })
        paged.failMore = Failure.Network(NetworkWhy.Offline)
        vm.loadMore()
        assertNotNull(vm.state.value.loadMoreFailure)
        assertTrue(vm.state.value.canLoadMore)
        vm.loadMore() // the visible Try again asks for the same page
        assertNull(vm.state.value.loadMoreFailure)
        assertEquals(listOf("a", "b", "c", "d"), vm.state.value.rows.map { it.ref.sessionId.raw })
        assertFalse(vm.state.value.canLoadMore)
        assertEquals(listOf(null, "c1", "c2", "c2"), paged.asked)
    }

    @Test
    fun `SCREENS-NAV-34 a first page that fails everywhere says so with Try again, and Try again reads again`() {
        val gateway = FakeServerGateway(listOf(session("a", 1)))
        gateway.failNext = Failure.Network(NetworkWhy.Refused)
        val vm = vm(Nav(), server(p1, gateway))
        assertEquals(AllPhase.Failed, vm.state.value.phase)
        assertNotNull(vm.state.value.failure)
        vm.retry()
        assertEquals(AllPhase.List, vm.state.value.phase)
    }

    @Test
    fun `SCREENS-NAV-34 no connected server says so instead of an endless skeleton`() {
        val vm = vm(Nav())
        assertEquals(AllPhase.NoServer, vm.state.value.phase)
    }

    @Test
    fun `SCREENS-NAV-34 search, Running and project filters narrow the loaded rows, archived ones are their own filter`() {
        val gateway =
            FakeServerGateway(
                listOf(
                    session("a", 40, "Reconnect loop", dir = "/work/api"),
                    session("b", 30, "Theme packs", dir = "/work/web"),
                    session("old", 20, "Old idea", archived = true),
                )
            )
        gateway.statuses = persistentMapOf(SessionId("b") to SessionStatus.Busy)
        val vm = vm(Nav(), server(p1, gateway))
        assertEquals(listOf("a", "b"), vm.state.value.rows.map { it.ref.sessionId.raw })
        assertTrue(vm.state.value.hasArchived)
        vm.search("theme")
        assertEquals(listOf("b"), vm.state.value.rows.map { it.ref.sessionId.raw })
        vm.search("")
        vm.toggleRunning()
        assertEquals(listOf("b"), vm.state.value.rows.map { it.ref.sessionId.raw })
        vm.toggleRunning()
        vm.chooseProject("/work/api")
        assertEquals(listOf("a"), vm.state.value.rows.map { it.ref.sessionId.raw })
        vm.chooseProject("/work/api")
        vm.toggleArchived()
        assertEquals(listOf("old"), vm.state.value.rows.map { it.ref.sessionId.raw })
        vm.search("nothing like this")
        assertEquals(AllPhase.NoMatch, vm.state.value.phase)
    }

    @Test
    fun `SCREENS-NAV-34 delete hides the row behind Undo and sends nothing until the window ends`() {
        val gateway = FakeServerGateway(listOf(session("a", 2, "Keep me"), session("b", 1)))
        val vm = vm(Nav(), server(p1, gateway))
        vm.delete("p1/a")
        assertEquals(listOf("p1/b"), vm.state.value.rows.map { it.key })
        assertEquals(1, vm.state.value.undo?.count)
        assertEquals(RowTitle.Text("Keep me"), vm.state.value.undo?.title)
        assertFalse(gateway.calls.any { it.startsWith("deleteSession") })
        vm.undoDelete()
        assertEquals(listOf("p1/a", "p1/b"), vm.state.value.rows.map { it.key })
        assertNull(vm.state.value.undo)
        vm.delete("p1/a")
        vm.commitDelete()
        assertEquals(listOf("deleteSession a"), gateway.calls.filter { it.startsWith("deleteSession") })
        assertEquals(listOf("p1/b"), vm.state.value.rows.map { it.key })
    }

    @Test
    fun `SCREENS-NAV-34 a refused delete brings the row back with a plain problem and its Details`() {
        val gateway = FakeServerGateway(listOf(session("a", 2)))
        val vm = vm(Nav(), server(p1, gateway))
        vm.delete("p1/a")
        gateway.failNext = Failure.Server(500)
        vm.commitDelete()
        assertEquals(listOf("p1/a"), vm.state.value.rows.map { it.key })
        assertEquals(AllProblemKind.Delete, vm.state.value.problem?.kind)
    }

    @Test
    fun `SCREENS-NAV-34 leaving inside the undo window still sends the delete, and a second delete sends the first`() {
        val nav = Nav()
        val gateway = FakeServerGateway(listOf(session("a", 3), session("b", 2), session("c", 1)))
        val vm = vm(nav, server(p1, gateway))
        vm.delete("p1/a")
        vm.delete("p1/b") // one offer at a time: the earlier delete goes out now
        assertEquals(listOf("deleteSession a"), gateway.calls.filter { it.startsWith("deleteSession") })
        vm.back()
        assertEquals(listOf("deleteSession a", "deleteSession b"), gateway.calls.filter { it.startsWith("deleteSession") })
        assertEquals(listOf("back"), nav.calls)
    }

    @Test
    fun `SCREENS-NAV-34 select mode picks several rows and deletes them under one undo`() {
        val nav = Nav()
        val gateway = FakeServerGateway(listOf(session("a", 3), session("b", 2), session("c", 1)))
        val vm = vm(nav, server(p1, gateway))
        vm.startSelecting()
        vm.tapRow("p1/a")
        vm.tapRow("p1/c")
        assertTrue(nav.calls.isEmpty()) // a tap selects, it does not open
        assertEquals(2, vm.state.value.selectedCount)
        vm.deleteSelected()
        assertFalse(vm.state.value.selecting)
        assertEquals(2, vm.state.value.undo?.count)
        assertNull(vm.state.value.undo?.title)
        assertEquals(listOf("p1/b"), vm.state.value.rows.map { it.key })
    }

    @Test
    fun `SCREENS-NAV-34 rename starts from the readable title and keeps the new one, a refusal says so`() {
        val gateway = FakeServerGateway(listOf(session("a", 1, "Old name")))
        val vm = vm(Nav(), server(p1, gateway))
        vm.rename("p1/a")
        assertEquals("Old name", vm.state.value.renaming?.initial)
        vm.finishRename(" New name ")
        assertNull(vm.state.value.renaming)
        assertTrue("renameSession a" in gateway.calls)
        assertEquals(RowTitle.Text("New name"), vm.state.value.rows.single().title)
        vm.rename("p1/a")
        gateway.failNext = Failure.Server(500)
        vm.finishRename("Another")
        assertEquals(AllProblemKind.Rename, vm.state.value.problem?.kind)
        assertEquals(RowTitle.Text("New name"), vm.state.value.rows.single().title)
    }

    @Test
    fun `SCREENS-NAV-34 Archive works only where the server can archive, and says why elsewhere`() {
        val plain = FakeServerGateway(listOf(session("a", 2)))
        plain.capabilities.value = ServerCapabilities(sessionArchive = false)
        val archiving = FakeServerGateway(listOf(session("b", 1)))
        archiving.capabilities.value = ServerCapabilities(sessionArchive = true)
        val archived = mutableListOf<String>()
        val ops =
            object : FakeOperationsGateway() {
                override suspend fun archiveSession(id: SessionId): Outcome<Unit> {
                    archived += id.raw
                    return Unit.ok()
                }
            }
        val vm = vm(Nav(), server(p1, plain, FakeOperationsGateway()), server(p2, archiving, ops))
        val byKey = vm.state.value.rows.associateBy { it.key }
        assertFalse(byKey.getValue("p1/a").canArchive)
        assertTrue(byKey.getValue("p2/b").canArchive)
        vm.archive("p2/b")
        assertEquals(listOf("b"), archived)
        assertEquals(listOf("p1/a"), vm.state.value.rows.map { it.key })
        vm.startSelecting("p1/a")
        assertFalse(vm.state.value.archiveAvailable)
    }

    @Test
    fun `the route's running filter arrives on and a project path filters at once`() {
        val gateway = FakeServerGateway(listOf(session("a", 2, dir = "/work/api"), session("b", 1, dir = "/work/web")))
        val vm = vm(Nav(), server(p1, gateway), filter = "/work/web")
        assertEquals(listOf("b"), vm.state.value.rows.map { it.ref.sessionId.raw })
        assertEquals(listOf("/work/api", "/work/web"), vm.state.value.projects.map { it.directory })
    }

    /** First pages wait for [gate], as a slow replacement connection does. */
    private class Gated(val inner: FakeServerGateway) : ServerGateway by inner {
        val gate = CompletableDeferred<Unit>()

        override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> {
            gate.await()
            return inner.sessionPage(cursor, limit)
        }
    }

    @Test
    fun `SCREENS-NAV-34 Running follows live run events, so the indicator and the Running filter stay true`() {
        val gateway = FakeServerGateway(listOf(session("a", 2), session("b", 1)))
        val vm = vm(Nav(), server(p1, gateway))
        assertEquals(listOf(false, false), vm.state.value.rows.map { it.running })
        gateway.events.emit(GatewayEvent.RunStarted(SessionId("a")))
        assertEquals(listOf(true, false), vm.state.value.rows.map { it.running })
        vm.toggleRunning()
        assertEquals(listOf("a"), vm.state.value.rows.map { it.ref.sessionId.raw })
        gateway.events.emit(GatewayEvent.RunEnded(SessionId("a"), RunOutcome.Succeeded))
        assertEquals(AllPhase.NoMatch, vm.state.value.phase)
        // After a reconnect the statuses are read again, never replayed.
        gateway.statuses = persistentMapOf(SessionId("b") to SessionStatus.Busy)
        gateway.events.connect()
        assertEquals(listOf("b"), vm.state.value.rows.map { it.ref.sessionId.raw })
    }

    @Test
    fun `B-AGENT-264 the AI Team's own conversations stay out of All conversations and its bulk acts`() {
        val gateway =
            FakeServerGateway(
                listOf(
                    session("mine", 5, dir = "/root/projects/my-app"),
                    session("home", 4, dir = "/root/aiteam/city"),
                    session("gc", 3, dir = "/work/city/.gc/worktrees/my-app/polecat"),
                    session("termux", 2, dir = "/data/data/com.termux/files/home/.oc/aiteam/rig"),
                    session("dots", 1, dir = "/root/projects/../aiteam"),
                )
            )
        val vm = vm(Nav(), server(p1, gateway))
        assertEquals(listOf("mine"), vm.state.value.rows.map { it.ref.sessionId.raw })
        assertEquals(listOf("/root/projects/my-app"), vm.state.value.projects.map { it.directory })
        vm.startSelecting()
        vm.tapRow("p1/home")
        assertEquals(0, vm.state.value.selectedCount)
    }

    @Test
    fun `SCREENS-NAV-34 a reload inside the undo window still sends the delete to the row's server`() {
        val first = FakeServerGateway(listOf(session("a", 2, "Gone soon"), session("b", 1)))
        val servers = Servers(listOf(server(p1, first)))
        val vm = ChatsAllViewModel(ChatsRoute.All(null), Nav(), servers, AppCoroutineScope(UnconfinedTestDispatcher()))
        vm.delete("p1/a")
        val replacement = Gated(FakeServerGateway(listOf(session("a", 2, "Gone soon"), session("b", 1))))
        servers.servers.value = listOf(server(p1, replacement)).toImmutableList()
        assertEquals(RowTitle.Text("Gone soon"), vm.state.value.undo?.title)
        vm.commitDelete()
        assertEquals(listOf("deleteSession a"), replacement.inner.calls.filter { it.startsWith("deleteSession") })
        replacement.gate.complete(Unit)
        assertEquals(listOf("p1/b"), vm.state.value.rows.map { it.key })
    }
}
