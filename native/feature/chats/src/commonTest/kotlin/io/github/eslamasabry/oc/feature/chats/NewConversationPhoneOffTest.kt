package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
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

/** The new-conversation page never waits on a server the person turned off: it says so and offers Start. */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationPhoneOffTest {
    private val phone = ProfileId("phone")

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class Host(off: Boolean) : NewConversationHost {
        var setups = 0
        var starts = 0
        override val servers = MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = false, phoneOff = off)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> {
            setups++
            return Outcome.Ok(NewConversationSetup("/root/projects/kept", emptyList(), emptyList(), emptyList()))
        }

        override suspend fun startPhoneServer(profile: ProfileId): Outcome<Unit> {
            starts++
            servers.value = listOf(NewConversationServer(phone, "This phone", connected = true))
            return Outcome.Ok(Unit)
        }

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
        ): NewConversationAttempt = NewConversationAttempt(directory, failure = Failure.Unavailable(FeatureId("test")))

        override suspend fun imports(profile: ProfileId, directory: String): Outcome<ImportableConversations> =
            Outcome.Failed(Failure.Unavailable(FeatureId("test")))

        override suspend fun import(
            profile: ProfileId,
            directory: String,
            conversation: ImportableConversation,
        ): Outcome<ConversationRef> = Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `PHONE-OFF a server the person turned off is not waited on, the page says it is off`() {
        val host = Host(off = true)
        val vm = NewConversationViewModel(ChatsRoute.New(phone), host, Nav(), Clock.System, ListenedReports())
        val shown = vm.state.value
        assertTrue(shown.phoneOff)
        assertFalse(shown.busy, "no permanent wait")
        assertEquals(0, host.setups, "nothing is read from a server that is off")
    }

    @Test
    fun `PHONE-OFF Start turns the server on and the page reads it again`() {
        val host = Host(off = true)
        val vm = NewConversationViewModel(ChatsRoute.New(phone), host, Nav(), Clock.System, ListenedReports())
        vm.startPhoneServer()
        assertEquals(1, host.starts)
        val shown = vm.state.value
        assertFalse(shown.phoneOff)
        assertFalse(shown.busy)
        assertEquals(1, host.setups)
        assertTrue(shown.setup != null)
    }
}
