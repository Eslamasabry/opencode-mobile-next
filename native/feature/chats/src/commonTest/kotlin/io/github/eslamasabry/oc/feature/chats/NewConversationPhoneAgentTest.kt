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
import io.github.eslamasabry.oc.feature.chats.presentation.newConversationModelName
import io.github.eslamasabry.oc.feature.chats.presentation.phoneModelsNeedRecheck
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
import kotlinx.coroutines.CompletableDeferred
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

    private class Started(val profile: ProfileId, val model: ModelRef?, val agent: String?, val copy: Boolean)

    private val claude = NewConversationPhoneAgent("claude", "Claude Code", "claude")
    private val codex = NewConversationPhoneAgent("codex", "Codex", "codex")

    private inner class Host(
        private val directory: String = "/root/projects/app",
        private val saved: String? = null,
        /** A ready agent not yet certified for chat: offered only once it is the chosen one (Flutter agentChoosable). */
        private val listedOnceChosen: NewConversationPhoneAgent? = null,
    ) : NewConversationHost {
        val starts = mutableListOf<Started>()
        val chosen = mutableListOf<Pair<ProfileId, String?>>()
        val savedModels = mutableListOf<Triple<ProfileId, String, ModelRef?>>()
        var modelReads = 0
        /** What the helper lists for the agent on the next read. */
        var agentModels = listOf(NewConversationModel(claudeModel, "Sonnet"))
        override val servers = MutableStateFlow(listOf(NewConversationServer(server, "This phone", connected = true)))

        /** Holds the agent's model list back until completed (null answers at once). */
        var modelsGate: CompletableDeferred<Unit>? = null

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> {
            val picked = if (listedOnceChosen == null) saved else chosen.lastOrNull()?.second ?: saved
            return Outcome.Ok(
                NewConversationSetup(
                    directory = directory,
                    projects = emptyList(),
                    models = listOf(NewConversationModel(serverModel, "Server model")),
                    agents = emptyList(),
                    model = serverModel,
                    phone =
                        NewConversationPhone(
                            helper,
                            listOf(claude) + listOfNotNull(listedOnceChosen?.takeIf { it.id == picked }),
                            listOf(codex),
                            selected = picked,
                        ),
                )
            )
        }

        override suspend fun choosePhoneAgent(profile: ProfileId, agentId: String?): Outcome<Unit> {
            chosen += profile to agentId
            return Outcome.Ok(Unit)
        }

        /** The next reads fail with this (null: they answer [agentModels]). */
        var modelsFailure: io.github.eslamasabry.oc.core.common.Failure? = null

        override suspend fun phoneAgentModels(profile: ProfileId, agentId: String): Outcome<NewConversationPhoneModels> {
            modelReads++
            modelsGate?.await()
            modelsFailure?.let { return Outcome.Failed(it) }
            return Outcome.Ok(NewConversationPhoneModels(agentModels, agentModels.firstOrNull()?.model))
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

        /** The thinking effort each start carried. */
        val efforts = mutableListOf<String?>()

        /** What [cachedPhoneAgentModels] answers: the list last read for an agent in this app run. */
        var cached: NewConversationPhoneModels? = null

        override fun cachedPhoneAgentModels(profile: ProfileId, agentId: String): NewConversationPhoneModels? = cached

        override suspend fun startWithEffort(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            variant: String?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt {
            efforts += variant
            return start(profile, directory, text, model, agent, separateCopy, previous, onStarted)
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
        assertEquals(listOf<Pair<ProfileId, String?>>(server to "claude"), host.chosen, "saved under the shown server, not the helper")
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
    fun `PHONE-AGENT an agent chosen on the Agents page is the one chosen here and is remembered`() {
        val host = Host()
        val vm = NewConversationViewModel(ChatsRoute.New(server, phoneAgent = "claude"), host, Nav(), Clock.System, ListenedReports())
        assertEquals("claude", vm.state.value.phoneAgent)
        assertEquals(listOf<Pair<ProfileId, String?>>(server to "claude"), host.chosen)
        assertEquals(1, host.modelReads)
    }

    @Test
    fun `PHONE-AGENT a requested agent that is not offered is ignored`() {
        val host = Host()
        val vm = NewConversationViewModel(ChatsRoute.New(server, phoneAgent = "codex"), host, Nav(), Clock.System, ListenedReports())
        assertNull(vm.state.value.phoneAgent)
        assertTrue(host.chosen.isEmpty())
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
        assertEquals(listOf<AppRoute>(AgentsRoute.Agent("codex", forNewChat = true)), nav.opened)
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
        assertEquals(listOf<Pair<ProfileId, String?>>(server to "claude", server to null), host.chosen)
    }

    private val antigravity = NewConversationPhoneAgent("antigravity", "Antigravity", "antigravity")
    private val antigravityModel = ModelRef("antigravity", "gemini-3-pro")

    @Test
    fun `AGENT-CARD New conversation from an agent's card opens with that agent chosen, not the remembered one`() {
        val host = Host(saved = "claude", listedOnceChosen = antigravity)
        host.agentModels = listOf(NewConversationModel(antigravityModel, "Gemini 3 Pro"))
        val vm = NewConversationViewModel(ChatsRoute.New(server, phoneAgent = "antigravity"), host, Nav(), Clock.System, ListenedReports())
        assertEquals("antigravity", vm.state.value.phoneAgent)
        assertEquals("Antigravity", vm.state.value.phoneAgentChoice?.name, "the sheet lists the agent it opened with")
        assertEquals(listOf<Pair<ProfileId, String?>>(server to "antigravity"), host.chosen, "remembered like a tap")
        assertEquals(1, host.modelReads, "only the chosen agent's models are read")
        assertEquals(antigravityModel, vm.state.value.phoneModel)
    }

    @Test
    fun `AGENT-CARD the card's agent stays chosen while its models are still being read`() {
        val host = Host(saved = "claude", listedOnceChosen = antigravity)
        host.agentModels = listOf(NewConversationModel(antigravityModel, "Gemini 3 Pro"))
        val gate = CompletableDeferred<Unit>()
        host.modelsGate = gate
        val vm = NewConversationViewModel(ChatsRoute.New(server, phoneAgent = "antigravity"), host, Nav(), Clock.System, ListenedReports())
        assertEquals("antigravity", vm.state.value.phoneAgent)
        assertTrue(vm.state.value.phoneModelsBusy)
        assertTrue(vm.state.value.phoneModels.isEmpty())
        gate.complete(Unit)
        assertEquals("antigravity", vm.state.value.phoneAgent, "the models arriving do not change the agent")
        assertEquals(listOf("Gemini 3 Pro"), vm.state.value.phoneModels.map { it.name })
    }

    @Test
    fun `MODELS-STALE coming back from the Agents page reads the agent's models again`() {
        val host = Host(saved = "claude")
        // Read while the agent was not signed in yet: the helper listed nothing for it.
        host.agentModels = emptyList()
        val vm = vm(host)
        assertEquals(1, host.modelReads)
        assertTrue(vm.state.value.phoneModels.isEmpty())
        vm.agents()
        host.agentModels = listOf(NewConversationModel(claudeModel, "Sonnet"))
        vm.shown()
        assertEquals(2, host.modelReads, "the list is read again when the page is shown again")
        assertEquals(listOf("Sonnet"), vm.state.value.phoneModels.map { it.name })
        assertEquals(claudeModel, vm.state.value.phoneModel)
    }

    @Test
    fun `MODELS-STALE a page shown again without a visit to setup reads nothing`() {
        val host = Host(saved = "claude")
        val vm = vm(host)
        vm.shown()
        assertEquals(1, host.modelReads, "no polling: only a return from setup reads again")
    }

    @Test
    fun `MODELS-STALE the helper is asked to look again only when a ready agent has no real model`() {
        val default = NewConversationModel(ModelRef("claude", "default"), "Default model")
        assertTrue(phoneModelsNeedRecheck(agentReady = true, models = emptyList()))
        assertTrue(phoneModelsNeedRecheck(agentReady = true, models = listOf(default)))
        assertFalse(phoneModelsNeedRecheck(agentReady = true, models = listOf(NewConversationModel(claudeModel, "Sonnet"))))
        assertFalse(phoneModelsNeedRecheck(agentReady = false, models = emptyList()), "nothing will change for an agent that is not ready")
    }

    @Test
    fun `MODELS-RAW model rows never show the wire id`() {
        assertEquals("Opus 5.5", newConversationModelName("claude-opus-5-5", null, "claude-opus-5-5"))
        assertEquals("Fable 5.1", newConversationModelName("claude-fable-5-1", null, null))
        assertEquals("Haiku 4.5", newConversationModelName("claude-haiku-4-5", "", null))
        assertEquals("Opus 4.6 · 1M context", newConversationModelName("claude-opus-4-6[1m]", null, "claude-opus-4-6[1m]"))
        assertEquals("Opus", newConversationModelName("opus", null, "Opus"), "the agent's own name wins")
        assertEquals("Claude Opus 5.5", newConversationModelName("claude-opus-5-5", "Claude Opus 5.5", "x"), "the catalog's name wins")
    }

    private fun named(n: Int) = List(n) { NewConversationModel(ModelRef("p", "m$it"), "Model $it") }

    private fun uiWith(server: List<NewConversationModel>, phone: List<NewConversationModel>? = null, busy: Boolean = false) =
        io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi(
            setup = NewConversationSetup(
                directory = "/root/projects/app",
                projects = emptyList(),
                models = server,
                agents = emptyList(),
                model = null,
                phone = NewConversationPhone(helper, listOf(claude), emptyList(), selected = null),
            ),
            phoneAgent = phone?.let { "claude" },
            phoneModels = phone.orEmpty(),
            phoneModelsBusy = busy,
        )

    @Test
    fun `MODEL-ROW is a control only when there is a model to choose between`() {
        assertTrue(uiWith(named(12)).modelChoosable, "the phone's OpenCode with many models")
        assertTrue(uiWith(named(2)).modelChoosable)
        assertFalse(uiWith(named(1), phone = named(1)).modelChoosable, "a phone agent with one model")
        assertFalse(uiWith(named(5), phone = emptyList()).modelChoosable, "an agent that reports none")
        assertFalse(uiWith(emptyList()).modelChoosable, "a server with none")
        assertFalse(
            uiWith(listOf(NewConversationModel(ModelRef("p", "default"), "Default"))).modelChoosable,
            "the server's default alias is not a choice",
        )
        assertTrue(uiWith(named(5), phone = emptyList(), busy = true).modelChoosable, "still reading the agent's list")
    }

    @Test
    fun `MODEL-ROW the model sheet never opens with nothing to choose`() {
        val vm = vm(Host())
        vm.choosePhoneAgent("claude")
        assertFalse(vm.state.value.modelChoosable)
        vm.sheet(io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet.Models)
        assertNull(vm.state.value.sheet)
        vm.choosePhoneAgent(null)
        vm.sheet(io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet.Models)
        assertNull(vm.state.value.sheet, "the server's single model is not a choice either")
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
        assertEquals(listOf<Triple<ProfileId, String, ModelRef?>>(Triple(server, "claude", claudeModel)), host.savedModels)
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

    // ---- Owner 11-12 on the phone (Paseo helper): thinking effort, a list being read, the last known list

    private val codexSol = ModelRef("codex", "gpt-6.1-sol")

    @Test
    fun `EFFORT a Codex model's thinking efforts are offered and the chosen one goes out with the first message`() {
        val host = Host()
        // As the helper lists Codex (thinkingOptions kept as variants by the Paseo gateway).
        host.agentModels = listOf(NewConversationModel(codexSol, "GPT-6.1-Sol", variants = listOf("low", "medium", "high", "xhigh")))
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        assertEquals(listOf("low", "medium", "high", "xhigh"), vm.state.value.shownVariants)
        assertNull(vm.state.value.variant, "the model's own default until a level is chosen")
        vm.chooseVariant("xhigh")
        assertEquals("xhigh", vm.state.value.variant)
        vm.chooseVariant("turbo")
        assertEquals("xhigh", vm.state.value.variant, "a level the model does not offer is ignored")
        vm.draftChanged("hello")
        vm.start()
        assertEquals(listOf<String?>("xhigh"), host.efforts)
        assertEquals(codexSol, host.starts.single().model)
    }

    @Test
    fun `EFFORT another model starts again from its own default effort`() {
        val host = Host()
        val other = ModelRef("codex", "gpt-6-luna")
        host.agentModels =
            listOf(
                NewConversationModel(codexSol, "GPT-6.1-Sol", variants = listOf("low", "high")),
                NewConversationModel(other, "GPT-6-Luna", variants = listOf("low", "high")),
            )
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        vm.chooseVariant("high")
        vm.chooseModel(other)
        assertNull(vm.state.value.variant)
        vm.draftChanged("hello")
        vm.start()
        assertEquals(listOf<String?>(null), host.efforts)
    }

    @Test
    fun `MODELS-LOADING while the agent's list is being read the sheet says so, never "no models"`() {
        val host = Host()
        host.modelsGate = CompletableDeferred()
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        assertTrue(vm.state.value.modelsLoading)
        assertTrue(vm.state.value.shownModels.isEmpty())
        host.modelsGate!!.complete(Unit)
        assertFalse(vm.state.value.modelsLoading)
        assertEquals(listOf(claudeModel), vm.state.value.shownModels.map { it.model })
    }

    /** The sheet's `loading` is `modelsLoading`; its "no models" line shows when the list is empty and it is false. */
    @Test
    fun `MODELS-LOADING the flag ends after a failed read and "no models" waits for a read that ended with none`() {
        val host = Host()
        host.modelsGate = CompletableDeferred()
        host.modelsFailure = io.github.eslamasabry.oc.core.common.Failure.Network(
            io.github.eslamasabry.oc.core.common.NetworkWhy.Offline
        )
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        assertTrue(vm.state.value.modelsLoading, "a read is on its way: the loading row, never \"no models\"")
        host.modelsGate!!.complete(Unit)
        assertFalse(vm.state.value.modelsLoading, "a failed read ended")
        // A read that ends with no model: the flag is off, so the empty list is told as "no models".
        host.modelsFailure = null
        host.agentModels = emptyList()
        host.modelsGate = CompletableDeferred()
        vm.choosePhoneAgent(null) // the server's own models, then the agent again: a fresh read
        vm.choosePhoneAgent("claude")
        assertTrue(vm.state.value.modelsLoading)
        host.modelsGate!!.complete(Unit)
        assertFalse(vm.state.value.modelsLoading)
        assertTrue(vm.state.value.shownModels.isEmpty())
    }

    @Test
    fun `MODELS-LOADING an older read never ends the flag while a newer one runs`() {
        val host = Host()
        val first = CompletableDeferred<Unit>()
        host.modelsGate = first
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        val second = CompletableDeferred<Unit>()
        host.modelsGate = second
        vm.choosePhoneAgent(null)
        vm.choosePhoneAgent("claude")
        first.complete(Unit)
        assertTrue(vm.state.value.modelsLoading, "the newer read is still on its way")
        second.complete(Unit)
        assertFalse(vm.state.value.modelsLoading)
    }

    @Test
    fun `MODELS-CACHED the agent's last known list shows at once and the fresh read replaces it`() {
        val host = Host()
        val stale = ModelRef("claude", "old-model")
        host.cached = NewConversationPhoneModels(listOf(NewConversationModel(stale, "Old model")), stale)
        host.modelsGate = CompletableDeferred()
        val vm = vm(host)
        vm.choosePhoneAgent("claude")
        assertEquals(listOf(stale), vm.state.value.shownModels.map { it.model }, "shown before the read ends")
        assertTrue(vm.state.value.modelsLoading, "the list shown is the last known one while the fresh read runs")
        host.modelsGate!!.complete(Unit)
        assertEquals(listOf(claudeModel), vm.state.value.shownModels.map { it.model }, "the fresh read replaces the stale list")
        assertEquals(claudeModel, vm.state.value.phoneModel)
        assertFalse(vm.state.value.modelsLoading)
    }
}
