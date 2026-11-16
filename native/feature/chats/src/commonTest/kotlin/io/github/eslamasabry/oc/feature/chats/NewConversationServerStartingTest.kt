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
 * Issue 147 (emulator, after a restart): New conversation opened while This phone's server was still starting, said its
 * projects and models "couldn't be read" and stayed there until Try again. A refused read of a server that is starting
 * is waited out: the page keeps getting ready and reads again by itself; only a read that keeps failing says so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationServerStartingTest {
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
    private inner class Host(private val refusals: Int) : NewConversationHost {
        var reads = 0
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

    @Test
    fun `I147-NCS-1 a server still starting at open is waited for, and the lists appear with no tap`() = runTest {
        mainOn()
        val host = Host(refusals = 3)
        val vm = NewConversationViewModel(route, host, Nav(route), Clock.System, ListenedReports())
        runCurrent()
        assertNull(vm.state.value.setup, "not read yet")
        assertNull(vm.state.value.failure, "no \"couldn't be read\" while the server is starting")
        assertEquals(true, vm.state.value.busy, "the page keeps getting ready")
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(dir, vm.state.value.setup?.directory, "the lists came by themselves")
        assertNull(vm.state.value.failure)
    }

    @Test
    fun `I147-NCS-2 a server that is up and keeps failing still says it could not read`() = runTest {
        mainOn()
        val host = Host(refusals = 1000)
        val vm = NewConversationViewModel(route, host, Nav(route), Clock.System, ListenedReports())
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(Failure.Network(NetworkWhy.Refused), vm.state.value.failure)
        assertNull(vm.state.value.setup)
    }
}
