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
import io.github.eslamasabry.oc.feature.chats.presentation.AppStart
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationPreviews
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServers
import io.github.eslamasabry.oc.feature.chats.presentation.NoConversationPins
import io.github.eslamasabry.oc.feature.chats.presentation.SavedRowLine
import io.github.eslamasabry.oc.feature.chats.presentation.ServerName
import io.github.eslamasabry.oc.feature.chats.presentation.SourceStartRules
import io.github.eslamasabry.oc.feature.chats.presentation.SourcesChipLabel
import io.github.eslamasabry.oc.feature.chats.presentation.WaitingWord
import io.github.eslamasabry.oc.feature.chats.presentation.KeptTapRules
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Owner's phone, 10035, one minute after start: the agents on this phone (their helper, a source the app itself
 * starts) were still starting. The home said only "2 of 3 connections" and left their conversations out; All
 * conversations greyed them as "Saved list. Connect to open this conversation." although the person cannot connect
 * them by hand. A source the app starts says it is starting, and its saved rows take a tap that opens when it can.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStartedSourcesTest {
    private val phone = ProfileId("phone")
    private val agents = ProfileId("phone-agents")
    private val laptop = ProfileId("laptop")

    private fun source(id: ProfileId, shown: Boolean = true, starting: AppStart? = null) =
        ChatsListSourceUi(id, id.raw, shown = shown, main = id == phone, onThisPhone = id != laptop, unreachable = false, starting = starting)

    @Test
    fun `APPSTART-001 the chip says the app is starting its agents, else all or N of M as before`() {
        assertEquals(
            SourcesChipLabel.Starting(AppStart.Agents),
            SourceStartRules.chip(listOf(source(phone), source(agents, starting = AppStart.Agents), source(laptop, shown = false))),
        )
        assertEquals(
            SourcesChipLabel.Some(2, 3),
            SourceStartRules.chip(listOf(source(phone), source(agents), source(laptop, shown = false))),
        )
        assertEquals(SourcesChipLabel.All, SourceStartRules.chip(listOf(source(phone), source(agents))))
        // A source switched off says nothing about starting.
        assertEquals(
            SourcesChipLabel.Some(1, 2),
            SourceStartRules.chip(listOf(source(phone), source(agents, shown = false, starting = AppStart.Agents))),
        )
    }

    @Test
    fun `APPSTART-002 a saved row of a source the app starts says starting and takes a tap, a computer's says connect`() {
        assertEquals(SavedRowLine.MustConnect, SourceStartRules.savedLine(null))
        assertEquals(SavedRowLine.PhoneStarting, SourceStartRules.savedLine(AppStart.Phone))
        assertEquals(SavedRowLine.AgentsStarting, SourceStartRules.savedLine(AppStart.Agents))
        assertFalse(SourceStartRules.takesTap(null))
        assertTrue(SourceStartRules.takesTap(AppStart.Agents))
        assertTrue(SourceStartRules.takesTap(AppStart.Phone))
    }

    @Test
    fun `APPSTART-005 while the app updates the agents the chip says so, and their rows say updating`() {
        val sources =
            listOf(source(phone), source(agents, starting = AppStart.AgentsUpdating).copy(updatingAgent = "Claude Code"))
        assertEquals(SourcesChipLabel.Updating("Claude Code"), SourceStartRules.chip(sources))
        // Without the agent's name the source's own name is said.
        assertEquals(
            SourcesChipLabel.Updating("phone-agents"),
            SourceStartRules.chip(listOf(source(phone), source(agents, starting = AppStart.AgentsUpdating))),
        )
        assertEquals(SavedRowLine.AgentsUpdating, SourceStartRules.savedLine(AppStart.AgentsUpdating))
        assertTrue(SourceStartRules.takesTap(AppStart.AgentsUpdating))
        assertEquals(WaitingWord.Updating, SourceStartRules.waitingWord(AppStart.AgentsUpdating))
        assertEquals(WaitingWord.Connecting, SourceStartRules.waitingWord(AppStart.Agents))
        assertEquals(WaitingWord.Connecting, SourceStartRules.waitingWord(null))
    }

    private class Nav : AppNavigator {
        val opened = mutableListOf<ConversationRef>()
        override val top = MutableStateFlow<AppRoute?>(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            opened += ref
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class Servers : ConversationServers {
        override val servers = MutableStateFlow<ImmutableList<ConversationServer>>(persistentListOf())
        override val waiting = MutableStateFlow<ImmutableList<ServerName>>(persistentListOf())
    }

    private val rows =
        listOf(Session(SessionId("s1"), "Claude Code chat", directory = "/root/projects/app", time = SessionTime(createdMs = 1_000, updatedMs = 1_000)))

    private inner class Kept : ConversationPreviews {
        override fun scope(server: ConversationServer): String? = null

        override fun read(server: ConversationServer): List<Session> = emptyList()

        override fun read(profile: ProfileId): List<Session> = if (profile == agents || profile == laptop) rows else emptyList()

        override suspend fun save(server: ConversationServer, scope: String, sessions: List<Session>): Outcome<Unit> = Outcome.Ok(Unit)
    }

    private class Paged(val inner: FakeServerGateway, val page: List<Session>) : ServerGateway by inner {
        override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> =
            ServerPage(page.toImmutableList(), null).ok()
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun vm(nav: Nav, servers: Servers) =
        ChatsAllViewModel(ChatsRoute.All(null), nav, servers, AppCoroutineScope(UnconfinedTestDispatcher()), NoConversationPins(), previews = Kept())

    @Test
    fun `APPSTART-003 on All conversations a tap on a starting source's row is kept and opens once it connects`() {
        val nav = Nav()
        val servers = Servers()
        servers.waiting.value = persistentListOf(ServerName(agents, "Claude Code", AppStart.Agents))
        val vm = vm(nav, servers)
        val row = vm.state.value.rows.single()
        assertEquals(AppStart.Agents, row.appStart)
        assertTrue(row.readOnly)
        vm.tapRow(row.key)
        assertTrue(vm.state.value.rows.single().waiting, "the row says it is connecting")
        assertEquals(emptyList(), nav.opened)
        // The helper connects: the kept tap opens the conversation, once.
        servers.servers.value = persistentListOf(ConversationServer(agents, "Claude Code", Paged(FakeServerGateway(), rows), null))
        servers.waiting.value = persistentListOf()
        assertEquals(listOf(ConversationRef(agents, SessionId("s1"))), nav.opened)
        servers.servers.value = persistentListOf(ConversationServer(agents, "Claude Code", Paged(FakeServerGateway(), rows), null))
        assertEquals(1, nav.opened.size)
    }

    @Test
    fun `APPSTART-004 a computer that is not connected keeps its saved rows inert`() {
        val nav = Nav()
        val servers = Servers()
        servers.waiting.value = persistentListOf(ServerName(laptop, "Laptop"))
        val vm = vm(nav, servers)
        val row = vm.state.value.rows.single()
        assertNull(row.appStart)
        vm.tapRow(row.key)
        assertFalse(vm.state.value.rows.single().waiting)
        servers.servers.value = persistentListOf(ConversationServer(laptop, "Laptop", Paged(FakeServerGateway(), rows), null))
        servers.waiting.value = persistentListOf()
        assertEquals(emptyList(), nav.opened)
    }
}
