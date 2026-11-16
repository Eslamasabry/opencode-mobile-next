package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServerState
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * first-read-ready: New conversation follows This phone's own server state (starting, up, failed) instead of guessing
 * from a failed read. Cold start: + tapped while the server starts waits, then reads by itself when it is up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationPhoneServerStateTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/app"
    private val route = ChatsRoute.New(phone)

    private class Nav(route: AppRoute) : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(route)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    /** A server whose first [refusals] reads are refused (still starting); every later read answers. */
    private inner class Host(private val refusals: Int, val state: MutableStateFlow<NewConversationServerState>) : NewConversationHost {
        var reads = 0

        override fun serverState(profile: ProfileId): StateFlow<NewConversationServerState> = state
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> {
            reads++
            return if (reads <= refusals) Outcome.Failed(Failure.Network(NetworkWhy.Refused))
            else Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList()))
        }

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("")

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> =
            Outcome.Ok(Unit)

        override suspend fun imports(profile: ProfileId, directory: String): Outcome<ImportableConversations> =
            Outcome.Failed(Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("test")))

        override suspend fun import(
            profile: ProfileId,
            directory: String,
            conversation: ImportableConversation,
        ): Outcome<ConversationRef> = Outcome.Failed(Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("test")))

        override suspend fun start(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt = NewConversationAttempt(directory)
    }

    private fun TestScope.mainOn() = Dispatchers.setMain(StandardTestDispatcher(testScheduler))

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun page(refusals: Int, state: NewConversationServerState): Pair<Host, NewConversationViewModel> {
        val host = Host(refusals, MutableStateFlow(state))
        return host to NewConversationViewModel(route, host, Nav(route), Clock.System, ListenedReports())
    }

    @Test
    fun `FRR-1 tapped while the phone server starts waits with no failure words, then reads by itself when it is up`() = runTest {
        mainOn()
        val (host, vm) = page(0, NewConversationServerState.Starting)
        runCurrent()
        assertEquals(0, host.reads, "nothing is read from a server that is starting")
        assertNull(vm.state.value.failure)
        assertEquals(true, vm.state.value.busy)
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(0, host.reads, "still no guess from a failed read")
        assertNull(vm.state.value.failure)
        host.state.value = NewConversationServerState.Up
        runCurrent()
        assertEquals(dir, vm.state.value.setup?.directory, "the lists came with no tap and no clock beyond the state change")
        assertNull(vm.state.value.failure)
    }

    @Test
    fun `FRR-2 a server that stays starting past its start budget says it could not be read`() = runTest {
        mainOn()
        val (host, vm) = page(0, NewConversationServerState.Starting)
        runCurrent()
        advanceTimeBy(91_000)
        runCurrent()
        assertEquals(0, host.reads)
        assertEquals(Failure.TimedOut(), vm.state.value.failure)
    }

    @Test
    fun `FRR-3 a phone server that failed to start says so at once`() = runTest {
        mainOn()
        val (host, vm) = page(0, NewConversationServerState.Failed)
        runCurrent()
        assertEquals(0, host.reads)
        assertNull(vm.state.value.setup)
        assertEquals(Failure.Network(NetworkWhy.Refused), vm.state.value.failure)
        assertEquals(false, vm.state.value.busy)
    }

    @Test
    fun `FRR-4 a read that fails right after up is read once more shortly, then a real failure says so`() = runTest {
        mainOn()
        val (_, vm) = page(1, NewConversationServerState.Up)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(dir, vm.state.value.setup?.directory, "one short re-read covers a server still finishing")
        val (host2, vm2) = page(1000, NewConversationServerState.Up)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, host2.reads, "one re-read only, no retry loop")
        assertEquals(Failure.Network(NetworkWhy.Refused), vm2.state.value.failure)
    }
}
