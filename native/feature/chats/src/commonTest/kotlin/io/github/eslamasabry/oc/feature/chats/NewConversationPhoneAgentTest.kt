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
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
 * New conversation on a phone with agents (Flutter new_chat_screen.dart): the agents this phone can run are choices,
 * the choice is remembered per server, and Start begins the conversation on the helper with that agent's model.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewConversationPhoneAgentTest {
    private val server = ProfileId("phone")
    private val helper = ProfileId("phone-agents")
    private val serverModel = ModelRef("anthropic", "server-model")
    private val claudeModel = ModelRef("claude", "sonnet")

    private class Nav : AppNavigator {
        val opened = mutableListOf<AppRoute>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private val claude = NewConversationPhoneAgent("claude", "Claude Code", "claude")
    private val codex = NewConversationPhoneAgent("codex", "Codex", "codex")

    private inner class Host(
        private val directory: String = "/root/projects/app",
        private val saved: String? = null,
    ) : NewConversationHost {
        class Started(val profile: ProfileId, val model: ModelRef?, val agent: String?, val copy: Boolean)

        val starts = mutableListOf<Started>()
        val chosen = mutableListOf<Pair<ProfileId, String?>>()
        val savedModels = mutableListOf<Triple<ProfileId, String, ModelRef?>>()
        var modelReads = 0
        override val servers = MutableStateFlow(listOf(NewConversationServer(server, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(
                NewConversationSetup(
                    directory = directory,
                    projects = emptyList(),
                    models = listOf(NewConversationModel(serverModel, "Server model")),
                    agents = emptyList(),
                    model = serverModel,
                    phone = NewConversationPhone(helper, listOf(claude), listOf(codex), selected = saved),
                )
            )

        override suspend fun choosePhoneAgent(profile: ProfileId, agentId: String?): Outcome<Unit> {
            chosen += profile to agentId
            return Outcome.Ok(Unit)
        }

        override suspend fun phoneAgentModels(profile: ProfileId, agentId: String): Outcome<NewConversationPhoneModels> {
            modelReads++
            return Outcome.Ok(
                NewConversationPhoneModels(listOf(NewConversationModel(claudeModel, "Sonnet")), claudeModel)
            )
        }

        override suspend fun savePhoneAgentModel(profile: ProfileId, agentId: String, model: ModelRef?): Outcome<Unit> {
            savedModels += Triple(profile, agentId, model)
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
        ): NewConversationAttempt {
            starts += Started(profile, model, agent, separateCopy)
            return NewConversationAttempt(
                directory,
                ref = ConversationRef(profile, SessionId("s1")),
                dispatched = true,
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

    private fun vm(host: Host, nav: Nav = Nav()) =
        NewConversationViewModel(ChatsRoute.New(server), host, nav, Clock.System, ListenedReports())

    @Test
    fun `PHONE-AGENT the offer lists what the phone can run and the choice is remembered per server`() {
        val host = Host()
        val vm = vm(host)
        val phone = vm.state.value.setup?.phone
        assertEquals(listOf("claude"), phone?.choices?.map { it.id }, "ready agents are choices")
        assertEquals(listOf("codex"), phone?.needsSetup?.map { it.id }, "agents that need setup are listed apart")
        assertNull(vm.state.value.phoneAgent, "OpenCode until another is chosen")
        vm.choosePhoneAgent("claude")
        assertEquals("claude", vm.state.value.phoneAgent)
        assertEquals(listOf(server to "claude"), host.chosen, "saved under the shown server, not the helper")
    }

    @Test
    fun `PHONE-AGENT a remembered agent is chosen again and its models are read`() {
        val host = Host(saved = "claude")
        val vm = vm(host)
        assertEquals("claude", vm.state.value.phoneAgent)
        assertEquals(1, host.modelReads)
        assertEquals(claudeModel, vm.state.value.phoneModel)
        assertEquals("Claude Code", vm.state.value.phoneAgentChoice?.name)
    }

    @Test
    fun `PHONE-AGENT an agent that still needs setup cannot be chosen, its row opens setup`() {
        val host = Host()
        val nav = Nav()
        val vm = vm(host, nav)
        vm.choosePhoneAgent("codex")
        assertNull(vm.state.value.phoneAgent)
        assertTrue(host.chosen.isEmpty(), "nothing is saved for an agent that cannot start")
        vm.setUpPhoneAgent("codex")
        assertEquals(listOf<AppRoute>(AgentsRoute.Home), nav.opened)
    }

    @Test
    fun `PHONE-AGENT a phone agent hides the separate copy and Build or Plan, OpenCode brings them back`() {
        val host = Host()
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        assertFalse(vm.state.value.canCopy)
        assertNull(vm.state.value.agent)
        vm.chooseAgent(null)
        assertNull(vm.state.value.phoneAgent, "the server's own agent is the choice again")
        assertEquals(listOf(server to "claude", server to null), host.chosen)
    }

    @Test
    fun `PHONE-AGENT the Model row shows the agent's models, not the server's`() {
        val host = Host()
        val vm = vm(host)
        assertEquals(listOf(serverModel), vm.state.value.shownModels.map { it.model })
        vm.choosePhoneAgent("claude")
        assertEquals(listOf(claudeModel), vm.state.value.shownModels.map { it.model })
        assertEquals(claudeModel, vm.state.value.shownModel)
        vm.chooseModel(claudeModel)
        assertEquals(listOf(Triple(server, "claude", claudeModel)), host.savedModels)
        assertEquals(serverModel, vm.state.value.model, "the server's own model is left alone")
    }

    @Test
    fun `PHONE-AGENT Start begins the conversation on the helper with the agent's model`() {
        val host = Host()
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        vm.draftChanged("hello")
        vm.start()
        val started = host.starts.single()
        assertEquals(helper, started.profile)
        assertEquals(claudeModel, started.model)
        assertNull(started.agent)
        assertFalse(started.copy)
    }

    @Test
    fun `PHONE-AGENT without a phone agent Start runs on the shown server as before`() {
        val host = Host()
        val vm = vm(host)
        vm.draftChanged("hello")
        vm.start()
        val started = host.starts.single()
        assertEquals(server, started.profile)
        assertEquals(serverModel, started.model)
    }

    @Test
    fun `PHONE-AGENT a phone agent works only in the projects on this phone`() {
        val host = Host(directory = "/srv/elsewhere")
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        vm.draftChanged("hello")
        assertTrue(vm.state.value.phoneNeedsProjectsFolder)
        vm.start()
        assertTrue(host.starts.isEmpty(), "Start waits for a folder under /root/projects/")
        vm.chooseAgent(null)
        assertFalse(vm.state.value.phoneNeedsProjectsFolder, "OpenCode has no such rule")
    }
}
