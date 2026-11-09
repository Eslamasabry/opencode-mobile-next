package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import io.github.eslamasabry.oc.feature.chats.presentation.AllPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationPreviews
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServers
import io.github.eslamasabry.oc.feature.chats.presentation.NoConversationPins
import io.github.eslamasabry.oc.feature.chats.presentation.ServerName
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Cache-first (owner rule, 2026-11-09): All conversations opened while its server is still connecting (or away)
 * draws the kept rows in the real list, never "no server" or skeleton rows; the live page replaces them in place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CacheFirstAllConversationsTest {
    private object Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private val p1 = ProfileId("p1")

    private fun session(id: String, at: Long) =
        Session(SessionId(id), "Chat $id", directory = "/work/api", time = SessionTime(createdMs = at, updatedMs = at))

    private val rows = listOf(session("s2", 2_000), session("s1", 1_000))

    private class Servers : ConversationServers {
        override val servers = MutableStateFlow<ImmutableList<ConversationServer>>(persistentListOf())
        override val waiting = MutableStateFlow<ImmutableList<ServerName>>(persistentListOf())
    }

    private inner class Kept : ConversationPreviews {
        override fun scope(server: ConversationServer): String? = null

        override fun read(server: ConversationServer): List<Session> = emptyList()

        override fun read(profile: ProfileId): List<Session> = if (profile == p1) rows.map { it.copy(directory = null) } else emptyList()

        override suspend fun save(server: ConversationServer, scope: String, sessions: List<Session>): Outcome<Unit> = Outcome.Ok(Unit)
    }

    private class Paged(val inner: FakeServerGateway, val page: List<Session>) : ServerGateway by inner {
        override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> =
            ServerPage(page.toImmutableList(), null).ok()
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `CF-17 a connecting server's kept rows are the first frame, and the live page keeps their keys`() {
        val servers = Servers()
        servers.waiting.value = persistentListOf(ServerName(p1, "Studio"))
        val vm =
            ChatsAllViewModel(
                ChatsRoute.All(null),
                Nav,
                servers,
                AppCoroutineScope(UnconfinedTestDispatcher()),
                NoConversationPins(),
                previews = Kept(),
            )

        val first = vm.state.value
        assertTrue(first.phase != AllPhase.NoServer && first.phase != AllPhase.Opening, "phase ${first.phase}")
        assertEquals(listOf("p1|s2", "p1|s1").size, first.rows.size)
        val keptKeys = first.rows.map { it.key }
        assertEquals(2, keptKeys.size)

        // The server answers: it becomes a live source and its page replaces the kept rows in place.
        servers.servers.value = persistentListOf(ConversationServer(p1, "Studio", Paged(FakeServerGateway(), rows), null))
        servers.waiting.value = persistentListOf()
        val live = vm.state.value
        assertEquals(keptKeys, live.rows.map { it.key }, "same rows, same keys, no re-layout")
    }

    @Test
    fun `CF-18 with nothing kept and nothing connected yet the page still waits, not "no server"`() {
        val servers = Servers()
        servers.waiting.value = persistentListOf(ServerName(ProfileId("other"), "Laptop"))
        val vm =
            ChatsAllViewModel(ChatsRoute.All(null), Nav, servers, AppCoroutineScope(UnconfinedTestDispatcher()), NoConversationPins(), previews = Kept())
        assertEquals(AllPhase.Opening, vm.state.value.phase)
    }
}
