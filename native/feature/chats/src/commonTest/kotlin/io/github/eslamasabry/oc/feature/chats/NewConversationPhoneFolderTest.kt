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
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhone
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneAgent
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneModels
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderReach
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
 * Issue 147 (the first customer's storage report): a folder chosen in the phone's storage is checked from the Linux
 * side that will work in it (the phone's OpenCode or the phone agents' helper) when it is chosen and again right before
 * the first message. Anything but Ready keeps Start back with the reason and the way forward; the first message only
 * ever goes to the folder shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationPhoneFolderTest {
    private val server = ProfileId("phone")
    private val helper = ProfileId("phone-agents")
    private val folder = "/storage/emulated/0/MyProj"
    private val model = ModelRef("anthropic", "m")
    private val claudeModel = ModelRef("claude", "sonnet")

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class Host : NewConversationHost {
        /** What the phone's OpenCode (key null) and the helper (key "claude") find the folder to be now. */
        val reach = mutableMapOf<String?, PhoneFolderReach?>(null to PhoneFolderReach.Ready, "claude" to PhoneFolderReach.Ready)
        val checks = mutableListOf<Pair<String?, String>>()
        val fixes = mutableListOf<Pair<String?, PhoneFolderReach>>()
        var fixAnswer: Outcome<Unit> = Outcome.Ok(Unit)
        val starts = mutableListOf<Pair<ProfileId, String>>()

        override suspend fun phoneFolder(profile: ProfileId, phoneAgent: String?, directory: String): PhoneFolderReach? {
            checks += phoneAgent to directory
            return reach[phoneAgent]
        }

        override suspend fun fixPhoneFolder(
            profile: ProfileId,
            phoneAgent: String?,
            reach: PhoneFolderReach,
        ): Outcome<Unit> {
            fixes += phoneAgent to reach
            return fixAnswer
        }

        override val servers = MutableStateFlow(listOf(NewConversationServer(server, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(
                NewConversationSetup(
                    directory = folder,
                    projects = emptyList(),
                    models = listOf(NewConversationModel(model, "Model")),
                    agents = emptyList(),
                    model = model,
                    phone =
                        NewConversationPhone(
                            helper,
                            listOf(NewConversationPhoneAgent("claude", "Claude Code", "claude")),
                            emptyList(),
                        ),
                )
            )

        override suspend fun phoneAgentModels(profile: ProfileId, agentId: String): Outcome<NewConversationPhoneModels> =
            Outcome.Ok(NewConversationPhoneModels(listOf(NewConversationModel(claudeModel, "Sonnet")), claudeModel))

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
            starts += profile to directory
            return NewConversationAttempt(directory, ref = ConversationRef(profile, SessionId("s1")), dispatched = true)
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

    private fun vm(host: Host) = NewConversationViewModel(ChatsRoute.New(server), host, Nav(), Clock.System, ListenedReports())

    @Test
    fun `I147-STORAGE without All files access Start waits and the action opens Android's page, then the folder is checked again`() {
        val host = Host()
        host.reach[null] = PhoneFolderReach.AppAccess
        val vm = vm(host)
        vm.draftChanged("hello")
        assertEquals(PhoneFolderReach.AppAccess, vm.state.value.phoneFolder)
        assertTrue(vm.state.value.phoneFolderBlocked)
        vm.start()
        assertTrue(host.starts.isEmpty(), "nothing is sent while the phone's Linux cannot open the folder")
        // The person allows it on Android's page.
        host.reach[null] = PhoneFolderReach.Ready
        vm.fixPhoneFolder()
        assertEquals(listOf<Pair<String?, PhoneFolderReach>>(null to PhoneFolderReach.AppAccess), host.fixes)
        assertEquals(PhoneFolderReach.Ready, vm.state.value.phoneFolder)
        vm.start()
        assertEquals(listOf(server to folder), host.starts, "the first message goes to the folder shown, nowhere else")
    }

    @Test
    fun `I147-STORAGE the folder is checked for the side that will work in it, and a restart is that side's own`() {
        val host = Host()
        host.reach["claude"] = PhoneFolderReach.Restart
        val vm = vm(host)
        vm.draftChanged("hello")
        assertEquals(PhoneFolderReach.Ready, vm.state.value.phoneFolder, "the phone's OpenCode sees it")
        vm.choosePhoneAgent("claude")
        assertEquals("claude" to folder, host.checks.last())
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder, "the helper started before the choice")
        vm.start()
        assertTrue(host.starts.isEmpty())
        host.reach["claude"] = PhoneFolderReach.Ready
        vm.fixPhoneFolder()
        assertEquals(listOf<Pair<String?, PhoneFolderReach>>("claude" to PhoneFolderReach.Restart), host.fixes)
        vm.start()
        assertEquals(listOf(helper to folder), host.starts)
    }

    @Test
    fun `I147-STORAGE the check runs again right before the first message and stops it when access was taken back`() {
        val host = Host()
        val vm = vm(host)
        vm.draftChanged("hello")
        assertEquals(PhoneFolderReach.Ready, vm.state.value.phoneFolder)
        // Android took the access back while the page was open.
        host.reach[null] = PhoneFolderReach.AppAccess
        vm.start()
        assertTrue(host.starts.isEmpty(), "the pre-flight before the first message refuses it")
        assertEquals(PhoneFolderReach.AppAccess, vm.state.value.phoneFolder)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `I147-STORAGE a failed restart says so and keeps Start back`() {
        val host = Host()
        host.reach[null] = PhoneFolderReach.Restart
        host.fixAnswer = Outcome.Failed(Failure.Unavailable(FeatureId("busy")))
        val vm = vm(host)
        vm.draftChanged("hello")
        vm.fixPhoneFolder()
        assertTrue(vm.state.value.phoneFolderFailure != null)
        assertTrue(vm.state.value.phoneFolderBlocked)
        vm.start()
        assertTrue(host.starts.isEmpty())
    }
}
