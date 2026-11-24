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
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * B-NAV-226 (tablet emulator, build of d1998a681d): New conversation resting beside Home's list is not on the back
 * stack, so the navigator's top is Home. Start created the conversation but never opened it (the page opened what it
 * started only while it was the navigator's top). Resting beside the list counts as shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationRestingStartTest {
    private val phone = ProfileId("phone")
    private val dir = "/root/projects/app"
    private val route = ChatsRoute.New(phone)
    private val conversation = ConversationRef(phone, SessionId("s1"))

    /** The list (Home) is the navigator's top: the resting page is beside it, not on the stack. */
    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(ChatsRoute.Home)
        val chats = mutableListOf<ConversationRef>()
        var backs = 0

        override fun open(route: AppRoute) = Unit

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            chats += ref
        }

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() {
            backs++
        }
    }

    private inner class Host : NewConversationHost {
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
            onStarted(conversation)
            return NewConversationAttempt(directory, ref = conversation, dispatched = true, creationAttempted = true)
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
    fun `B-NAV-226 Start on the resting New conversation opens the started conversation`() {
        val nav = Nav()
        val vm = NewConversationViewModel(route, Host(), nav, Clock.System, ListenedReports())
        vm.restingBeside(true)
        vm.draftChanged("write a short python function")
        vm.start()
        assertEquals(listOf(conversation), nav.chats.toList(), "the started conversation opens beside the list")
        assertEquals(0, nav.backs, "nothing is popped: the resting page is not on the back stack")
    }

    @Test
    fun `B-NAV-226 a page neither on top nor resting beside the list opens nothing`() {
        val nav = Nav()
        val vm = NewConversationViewModel(route, Host(), nav, Clock.System, ListenedReports())
        vm.draftChanged("write a short python function")
        vm.start()
        assertEquals(emptyList(), nav.chats.toList(), "a page the person left does not pull them back")
    }
}
