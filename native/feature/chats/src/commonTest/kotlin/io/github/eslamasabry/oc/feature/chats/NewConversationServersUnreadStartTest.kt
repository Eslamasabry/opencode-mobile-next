package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationWait
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** F-039: before the saved connections are read the page is getting ready; a typed Start waits and goes on. */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationServersUnreadStartTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/app"
    private val route = ChatsRoute.New(null)

    private object Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)
        override fun open(route: AppRoute) {}
        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}
        override fun land() {}
        override fun replaceRoot(root: AppRoute) {}
        override fun back() {}
    }

    private inner class Host : NewConversationHost {
        override val servers = MutableStateFlow<List<NewConversationServer>>(emptyList())
        override val serversRead = MutableStateFlow(false)
        var starts = 0

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList()))
        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true
        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("")
        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> = Outcome.Ok(Unit)
        override suspend fun start(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt {
            starts++
            return NewConversationAttempt(directory, ref = ConversationRef(profile, SessionId("s1")), creationAttempted = true)
        }
        override suspend fun imports(profile: ProfileId, directory: String): Outcome<ImportableConversations> =
            Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("t")))
        override suspend fun import(profile: ProfileId, directory: String, conversation: ImportableConversation) =
            Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("t")))
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-NEW-147 an unread list is getting ready and a typed Start goes on when the server appears`() {
        val host = Host()
        val vm = NewConversationViewModel(route, host, Nav, Clock.System, ListenedReports())
        assertFalse(vm.state.value.serversRead)
        assertEquals(NewConversationWait.Preparing, vm.state.value.wait)
        vm.draftChanged("fix the tests")
        vm.start()
        assertEquals(0, host.starts)
        host.servers.value = listOf(NewConversationServer(phone, "This phone", connected = false))
        host.serversRead.value = true
        assertTrue(vm.state.value.serversRead)
        assertEquals(1, host.starts, "the kept tap starts once the connection is ready")
    }
}
