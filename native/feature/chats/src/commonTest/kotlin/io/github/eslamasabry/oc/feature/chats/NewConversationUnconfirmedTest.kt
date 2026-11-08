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
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
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

/**
 * B-096 / B-103: a first message whose record could not be read (the server was not answering) locked the form with
 * Discard as the only way out. "Check again" reads the record once more and frees the form when it is settled; Discard
 * still clears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationUnconfirmedTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/app"

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class Host : NewConversationHost {
        var serverUp = false
        var dismissed = 0
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList()))

        override suspend fun review(profile: ProfileId, directory: String?): Outcome<ConversationRef?> =
            if (serverUp) Outcome.Ok(null) else Outcome.Failed(Failure.Unavailable(FeatureId("test")))

        override suspend fun dismissReview(profile: ProfileId, directory: String?): Outcome<Unit> {
            dismissed++
            return Outcome.Ok(Unit)
        }

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("words")

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> =
            Outcome.Ok(Unit)

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

    private fun vm(host: Host) =
        NewConversationViewModel(ChatsRoute.New(phone), host, Nav(), Clock.System, ListenedReports())

    @Test
    fun `B-096 an unreadable record locks the form and Check again frees it once the server answers`() {
        val host = Host()
        val vm = vm(host)
        assertTrue(vm.state.value.deliveryUnconfirmed, "the unreadable record is held, not guessed away")
        assertTrue(vm.state.value.attempted)
        vm.recheckUnconfirmed()
        assertTrue(vm.state.value.deliveryUnconfirmed, "still unreadable while the server is down")
        host.serverUp = true
        vm.recheckUnconfirmed()
        assertFalse(vm.state.value.deliveryUnconfirmed)
        assertFalse(vm.state.value.attempted)
        assertEquals(null, vm.state.value.failure)
    }

    @Test
    fun `B-103 Discard clears an unconfirmed first message with nothing to open`() {
        val host = Host()
        val vm = vm(host)
        assertTrue(vm.state.value.deliveryUnconfirmed)
        vm.discardUnconfirmed()
        assertEquals(1, host.dismissed)
        assertFalse(vm.state.value.deliveryUnconfirmed)
        assertFalse(vm.state.value.attempted)
        assertEquals("", vm.state.value.text)
    }
}
