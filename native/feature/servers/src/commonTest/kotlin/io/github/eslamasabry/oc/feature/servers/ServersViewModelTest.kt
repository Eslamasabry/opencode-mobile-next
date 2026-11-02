package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.feature.servers.presentation.ServerIssue
import io.github.eslamasabry.oc.feature.servers.presentation.ServersViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ServersViewModelTest {
    private val id = ProfileId("saved")
    private val ref = ConversationRef(id, SessionId("created"))

    private inner class Connections : ServerConnections {
        override val servers = MutableStateFlow<ImmutableList<SavedServer>>(persistentListOf())
        override val busy = MutableStateFlow(false)
        var saves = 0
        var connects = 0
        var creates = 0
        var removes = 0
        var consent: Boolean? = null
        var saveResult: Outcome<ProfileId> = Outcome.Ok(id)
        var connectResult: Outcome<Unit> = Outcome.Ok(Unit)
        var createResult: Outcome<ConversationRef> = Outcome.Ok(ref)
        var connectWait: CompletableDeferred<Unit>? = null

        override suspend fun save(
            name: String,
            address: String,
            username: String,
            password: String,
            allowCleartext: Boolean,
        ): Outcome<ProfileId> {
            saves++
            consent = allowCleartext
            return saveResult
        }

        override suspend fun connect(id: ProfileId): Outcome<Unit> {
            connects++
            connectWait?.await()
            return connectResult
        }

        override suspend fun createConversation(profile: ProfileId?): Outcome<ConversationRef> {
            creates++
            return createResult
        }

        override suspend fun remove(id: ProfileId): Outcome<Unit> {
            removes++
            return Outcome.Ok(Unit)
        }
    }

    private class Navigator : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(null)
        val chats = mutableListOf<ConversationRef>()

        override fun open(route: AppRoute) {
            top.value = route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            chats += ref
        }

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) {
            top.value = root
        }

        override fun back() = Unit
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-SEC-124 save passes only the explicit HTTP choice and resets it after saving`() =
        runTest {
            val connections = Connections()
            val nav = Navigator()
            val vm = ServersViewModel(null, connections, nav)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            vm.save("Server", "http://example.test:4096", "opencode", "private")
            assertEquals(false, connections.consent)
            vm.allowCleartext(true, "http://example.test:4096")
            vm.save("Server", "http://example.test:4096", "opencode", "private")
            assertEquals(true, connections.consent)
            assertFalse(vm.state.value.cleartextAllowed)
            assertEquals(2, vm.state.value.savedGeneration)
            assertTrue(nav.chats.isEmpty())
        }

    @Test
    fun `B-SEC-153 consent for a previous address never follows an edited address`() = runTest {
        val connections = Connections()
        val vm = ServersViewModel(null, connections, Navigator())
        vm.allowCleartext(true, "http://first.test:4096")
        vm.save("Server", "http://second.test:4096", "opencode", "private")
        assertEquals(false, connections.consent)
    }

    @Test
    fun `B-STATE-098 failed secure save keeps the form and never connects or creates`() = runTest {
        val connections =
            Connections().apply { saveResult = Outcome.Failed(Failure.StorageRefused()) }
        val nav = Navigator()
        val vm = ServersViewModel(ChatsRoute.New(null), connections, nav)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.save("Server", "https://example.test", "opencode", "private")
        assertEquals(ServerIssue.SaveFailed, vm.state.value.issue)
        assertEquals(0, vm.state.value.savedGeneration)
        assertEquals(0, connections.connects)
        assertEquals(0, connections.creates)
        assertTrue(nav.chats.isEmpty())
    }

    @Test
    fun `B-STATE-065 new conversation saves connects and opens the exact returned conversation`() =
        runTest {
            val connections = Connections()
            val nav = Navigator()
            val vm = ServersViewModel(ChatsRoute.New(null), connections, nav)
            vm.save("Server", "https://example.test", "opencode", "private")
            assertEquals(1, connections.saves)
            assertEquals(1, connections.connects)
            assertEquals(1, connections.creates)
            assertEquals(listOf(ref), nav.chats)
        }

    @Test
    fun `B-SEC-124 failed connection never creates a conversation or navigates`() = runTest {
        val connections = Connections().apply { connectResult = Outcome.Failed(Failure.Network()) }
        val nav = Navigator()
        val vm = ServersViewModel(ChatsRoute.New(null), connections, nav)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.create(id)
        assertEquals(0, connections.creates)
        assertEquals(ServerIssue.ConnectFailed, vm.state.value.issue)
        assertTrue(nav.chats.isEmpty())
    }

    @Test
    fun `B-STATE-065 repeated taps while connecting admit only one creation`() = runTest {
        val wait = CompletableDeferred<Unit>()
        val connections = Connections().apply { connectWait = wait }
        val nav = Navigator()
        val vm = ServersViewModel(ChatsRoute.New(null), connections, nav)
        vm.create(id)
        vm.create(id)
        vm.remove(id)
        assertEquals(1, connections.connects)
        assertEquals(0, connections.removes)
        wait.complete(Unit)
        assertEquals(1, connections.creates)
        assertEquals(listOf(ref), nav.chats)
    }

    @Test
    fun `B-STATE-065 failed creation stays on the saved server screen with no automatic retry`() =
        runTest {
            val connections =
                Connections().apply { createResult = Outcome.Failed(Failure.Server(503)) }
            val nav = Navigator()
            val vm = ServersViewModel(ChatsRoute.New(null), connections, nav)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
            vm.create(id)
            assertEquals(1, connections.creates)
            assertEquals(ServerIssue.CreateFailed, vm.state.value.issue)
            assertTrue(nav.chats.isEmpty())
            assertFalse(vm.state.value.busy)
        }
}
