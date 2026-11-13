package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
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
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * First message (d): the New-conversation page opened the chat once the first message was admitted, and its send then
 * failed. The chat owns that failed bubble (its words, its Try again); going back to this page must not show the same
 * failure a second time. A failure before any chat opened still shows here, as today.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationHandedOverTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/app"
    private val route = ChatsRoute.New(phone)
    private val conversation = ConversationRef(phone, SessionId("s1"))

    private inner class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(route)
        val chats = mutableListOf<ConversationRef>()

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            chats += ref
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class Host(private val opensChat: Boolean) : NewConversationHost {
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList()))

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("")

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
        ): NewConversationAttempt {
            // Admitted: the chat opens; then the send is put back (it never left the phone).
            if (opensChat) onStarted(conversation)
            return NewConversationAttempt(
                directory,
                ref = conversation,
                failure = Failure.Unavailable(FeatureId("agentStart")),
                creationAttempted = true,
            )
        }

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
    fun `First message (d) a failure the opened chat owns is not shown again on this page`() {
        val nav = Nav()
        val vm = NewConversationViewModel(route, Host(opensChat = true), nav, Clock.System, ListenedReports())
        vm.draftChanged("fix the tests")
        vm.start()
        assertEquals(listOf(conversation), nav.chats.toList(), "the chat opened at admission")
        assertNull(vm.state.value.failure, "the chat's failed bubble is the one place it shows")
    }

    @Test
    fun `First message (d) a failure before any chat opened still shows here`() {
        val nav = Nav()
        val vm = NewConversationViewModel(route, Host(opensChat = false), nav, Clock.System, ListenedReports())
        vm.draftChanged("fix the tests")
        vm.start()
        assertEquals(emptyList<ConversationRef>(), nav.chats.toList())
        assertEquals(Failure.Unavailable(FeatureId("agentStart")), vm.state.value.failure)
    }
}
