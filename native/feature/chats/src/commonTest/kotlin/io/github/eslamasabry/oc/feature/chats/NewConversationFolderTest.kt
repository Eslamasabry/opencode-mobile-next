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
 * B-063: the phone's server kept listing project "new" after its folder was gone, and every message
 * started there was refused (404 LocationNotFoundError). The Start form says so before Start and
 * offers to make the folder again or to choose another project.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationFolderTest {
    private val phone = ProfileId("phone")
    private val gone = "/root/projects/new"
    private val kept = "/root/projects/kept"

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class Host(var present: MutableSet<String>) : NewConversationHost {
        val starts = mutableListOf<String>()
        val remade = mutableListOf<String>()
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup(gone, emptyList(), emptyList(), emptyList()))

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? =
            directory in present

        override suspend fun remakeFolder(profile: ProfileId, directory: String): Outcome<String> {
            remade += directory
            present += directory
            return Outcome.Ok(directory)
        }

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
            starts += directory
            return NewConversationAttempt(directory, failure = Failure.Unavailable(FeatureId("test")))
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

    private fun vm(host: Host) = NewConversationViewModel(ChatsRoute.New(phone), host, Nav(), Clock.System)

    @Test
    fun `B-CHATS-110 B-063 a project whose folder is gone says so and Start sends nothing`() {
        val host = Host(mutableSetOf(kept))
        val vm = vm(host)
        assertEquals(gone, vm.state.value.directory)
        assertTrue(vm.state.value.folderMissing, "the missing folder is found before Start")
        vm.draftChanged("Hi")
        vm.start()
        assertTrue(host.starts.isEmpty(), "nothing is sent into a folder that is not there")
    }

    @Test
    fun `B-CHATS-110 B-063 making the folder again or choosing another project clears it and Start goes`() {
        val host = Host(mutableSetOf(kept))
        val vm = vm(host)
        vm.remakeFolder()
        assertEquals(listOf(gone), host.remade)
        assertFalse(vm.state.value.folderMissing)
        vm.draftChanged("Hi")
        vm.start()
        assertEquals(listOf(gone), host.starts)

        val other = Host(mutableSetOf(kept))
        val second = vm(other)
        second.sheet(NewConversationSheet.Projects)
        second.chooseDirectory(kept)
        assertFalse(second.state.value.folderMissing, "a project that is there needs no step")
    }
}
