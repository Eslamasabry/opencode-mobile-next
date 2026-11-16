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
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneFolderSide
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
import kotlinx.coroutines.flow.map
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

        /** Holds the next checks back until completed (null answers at once). */
        var checkGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var fixGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        override suspend fun phoneFolder(profile: ProfileId, phoneAgent: String?, directory: String): PhoneFolderReach? {
            checks += phoneAgent to directory
            checkGate?.await()
            stale?.let { return if ((phoneAgent != null) in it) PhoneFolderReach.Restart else PhoneFolderReach.Ready }
            return reach[phoneAgent]
        }

        /**
         * W3: a model of the phone's two sides when set: the sides (true: the agents' helper, false: the phone's
         * OpenCode) now running without the folder. One restart restarts both, as `PhoneFolderRestarts` does, unless a
         * reply runs on OpenCode ([serverReplying]): that side then waits.
         */
        var stale: MutableSet<Boolean>? = null
        var serverReplying = false
        var waitingSides: Set<PhoneFolderSide> = emptySet()

        /** What Linux answered on 10050 about a folder made a moment ago: not there until a side was restarted. */
        var linuxSeesFolder: Boolean? = null
        val fixedFor = mutableListOf<String>()
        val agents = mutableListOf(NewConversationPhoneAgent("claude", "Claude Code", "claude"))

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = linuxSeesFolder

        override suspend fun fixPhoneFolder(
            profile: ProfileId,
            phoneAgent: String?,
            reach: PhoneFolderReach,
            directory: String,
        ): Outcome<Unit> {
            fixedFor += directory
            stale?.let { sides ->
                for (side in sides.toList())
                    if (!side && serverReplying) waitingSides = waitingSides + PhoneFolderSide.Server
                    else sides.remove(side)
                if (reach == PhoneFolderReach.Restart) linuxSeesFolder = true
            }
            return fixPhoneFolder(profile, phoneAgent, reach)
        }

        override suspend fun phoneFolderBothSides(profile: ProfileId, phoneAgent: String?, directory: String): Boolean =
            stale?.contains(phoneAgent == null) == true

        override suspend fun phoneFolderWaiting(profile: ProfileId): Set<PhoneFolderSide> = waitingSides

        /** I147-WAIT: sides the phone reports waiting while a restart still runs (null: only what [waitingSides] says). */
        val liveWaiting = MutableStateFlow<Set<PhoneFolderSide>?>(null)

        override fun phoneFolderWaitingUpdates(profile: ProfileId) =
            liveWaiting.map { it ?: waitingSides }

        override suspend fun fixPhoneFolder(
            profile: ProfileId,
            phoneAgent: String?,
            reach: PhoneFolderReach,
        ): Outcome<Unit> {
            fixes += phoneAgent to reach
            fixGate?.await()
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
                            agents.toList(),
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

    @Test
    fun `I147-STORAGE a restart shows at once that it is running, cannot be tapped twice, and says when it is done`() {
        val host = Host()
        host.reach["claude"] = PhoneFolderReach.Restart
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        vm.draftChanged("hello")
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        host.fixGate = gate
        vm.fixPhoneFolder()
        assertTrue(vm.state.value.fixingPhoneFolder, "busy at once, before the restart answers")
        vm.fixPhoneFolder()
        assertEquals(1, host.fixes.size, "a second tap while it runs does nothing")
        vm.start()
        assertTrue(host.starts.isEmpty())
        host.reach["claude"] = PhoneFolderReach.Ready
        gate.complete(Unit)
        assertFalse(vm.state.value.fixingPhoneFolder)
        assertEquals(PhoneFolderReach.Ready, vm.state.value.phoneFolder)
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolderFixed, "the page says the restart is done")
    }

    @Test
    fun `I147-STORAGE a refused restart says why and keeps the notice and its action`() {
        val host = Host()
        host.reach["claude"] = PhoneFolderReach.Restart
        host.fixAnswer = Outcome.Failed(Failure.Unavailable(FeatureId("busy")))
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        vm.fixPhoneFolder()
        assertFalse(vm.state.value.fixingPhoneFolder)
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder)
        assertTrue(vm.state.value.phoneFolderFailure != null)
        assertEquals(null, vm.state.value.phoneFolderFixed)
    }

    @Test
    fun `I147-STORAGE the phone's own OpenCode is checked when the folder is chosen, and Start waits for the answer`() {
        val host = Host()
        host.reach[null] = PhoneFolderReach.Restart
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        host.checkGate = gate
        val vm = vm(host)
        vm.draftChanged("hello")
        assertEquals(null to folder, host.checks.first(), "checked for the server itself, with no agent chosen")
        assertTrue(vm.state.value.checkingPhoneFolder, "the page says it is checking")
        vm.start()
        assertTrue(host.starts.isEmpty(), "Start waits while the folder is read")
        gate.complete(Unit)
        assertFalse(vm.state.value.checkingPhoneFolder)
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder, "said before the person taps Start")
    }

    @Test
    fun `W3-STORAGE the walker's steps - a new folder with Codex, one restart for both sides, no stale "folder gone", no second restart`() {
        val host = Host()
        host.agents += NewConversationPhoneAgent("codex", "Codex", "codex")
        host.agents += NewConversationPhoneAgent("antigravity", "Antigravity", "antigravity")
        val vm = vm(host)
        vm.draftChanged("say ok")
        // New conversation › agent Codex › Project › Phone storage › Documents › New folder w3b (made and chosen). Both
        // sides of the phone were running before it existed; Linux has not bound it yet (the 10050 answer).
        vm.choosePhoneAgent("codex")
        host.stale = mutableSetOf(true, false)
        host.linuxSeesFolder = false
        val w3b = "/storage/emulated/0/Documents/w3b"
        vm.chooseDirectory(w3b)
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder)
        assertTrue(vm.state.value.phoneFolderBoth, "the notice says the agents and OpenCode both need it")
        // The agent chip to Antigravity: the same notice names Antigravity.
        vm.choosePhoneAgent("antigravity")
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder)
        assertTrue(vm.state.value.phoneFolderBoth)
        // One tap.
        vm.fixPhoneFolder()
        assertEquals(listOf(w3b), host.fixedFor, "one restart, for this folder")
        assertEquals(PhoneFolderReach.Ready, vm.state.value.phoneFolder)
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolderFixed, "the page says it is done")
        assertFalse(vm.state.value.folderMissing, "no stale \"This project's folder isn't on the server any more\"")
        // Five minutes later the person chooses OpenCode (or opens w3b again): no second restart is asked for.
        vm.choosePhoneAgent(null)
        assertEquals(PhoneFolderReach.Ready, vm.state.value.phoneFolder)
        vm.chooseDirectory(w3b)
        assertEquals(PhoneFolderReach.Ready, vm.state.value.phoneFolder)
        assertEquals(1, host.fixes.size)
        vm.start()
        assertEquals(listOf(server to w3b), host.starts)
    }

    @Test
    fun `W3-STORAGE a reply running on the phone's OpenCode is not interrupted, the page says it restarts after`() {
        val host = Host()
        val vm = vm(host)
        vm.draftChanged("hello")
        host.stale = mutableSetOf(false)
        host.serverReplying = true
        val folder = "/storage/emulated/0/Documents/w3c"
        vm.chooseDirectory(folder)
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder)
        vm.fixPhoneFolder()
        assertEquals(setOf(PhoneFolderSide.Server), vm.state.value.phoneFolderWaiting)
        assertTrue(vm.state.value.chosenSideWaits, "the notice says OpenCode restarts when the reply ends")
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder, "it was not restarted mid-reply")
        vm.fixPhoneFolder()
        assertEquals(1, host.fixes.size, "nothing to tap while it waits")
        vm.start()
        assertTrue(host.starts.isEmpty())
    }

    @Test
    fun `I147-WAIT a restart that waits on a running reply says so while it runs, not only after`() {
        val host = Host()
        val vm = vm(host)
        vm.draftChanged("hello")
        host.stale = mutableSetOf(true, false)
        val folder = "/storage/emulated/0/Documents/w3f"
        vm.chooseDirectory(folder)
        vm.choosePhoneAgent("claude")
        assertEquals(PhoneFolderReach.Restart, vm.state.value.phoneFolder)
        host.fixGate = kotlinx.coroutines.CompletableDeferred()
        vm.fixPhoneFolder()
        assertTrue(vm.state.value.fixingPhoneFolder)
        assertFalse(vm.state.value.restartWaitsForReply, "restarting, nothing waits yet")
        host.liveWaiting.value = setOf(PhoneFolderSide.Server)
        assertTrue(vm.state.value.restartWaitsForReply, "OpenCode waits for its reply while the agents restart")
        host.liveWaiting.value = emptySet()
        assertFalse(vm.state.value.restartWaitsForReply, "the reply ended: back to the restarting words")
        host.fixGate!!.complete(Unit)
    }
}
