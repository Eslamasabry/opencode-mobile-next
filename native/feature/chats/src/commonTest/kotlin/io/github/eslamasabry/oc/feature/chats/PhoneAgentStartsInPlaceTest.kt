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
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhone
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneAgent
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneModels
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationWait
import io.github.eslamasabry.oc.feature.chats.presentation.PhoneStartWay
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
 * ONE-OP: starting a conversation with a phone agent is one operation. The person stays in New conversation, words
 * kept and "Starting <agent>…" shown, until the helper reported the agent's session (the first message Accepted); only
 * then the conversation page opens. A refused start never opens a page and names the agent with one way forward.
 * Every agent of the catalog that runs on the phone is driven through each case.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneAgentStartsInPlaceTest {
    private val server = ProfileId("phone")
    private val helper = ProfileId("phone-agents")
    private val words = "write the tests first"
    private val directory = "/root/projects/app"
    private val route = ChatsRoute.New(server)
    private val agents = AgentCatalog.builtIn.agents.filter { it.runsOnPhoneHost }
    private val modelOf = { id: String -> ModelRef(id, "$id-model") }

    private class Nav(route: AppRoute) : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(route)
        val chats = mutableListOf<ConversationRef>()
        val opened = mutableListOf<AppRoute>()
        var backs = 0

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            chats += ref
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {
            backs++
            top.value = null
        }
    }

    /** What the helper does with the first message: answers when the gate opens, or refuses. */
    private inner class Host(private val agent: String?) : NewConversationHost {
        val gate = CompletableDeferred<Failure?>()
        var starts = 0
        var listed = agents.map { NewConversationModel(modelOf(it.id.raw), "${it.name} model") }
        override val servers = MutableStateFlow(listOf(NewConversationServer(server, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(
                NewConversationSetup(
                    directory = directory,
                    projects = emptyList(),
                    models = listOf(NewConversationModel(ModelRef("server", "m"), "Server model")),
                    agents = emptyList(),
                    phone =
                        NewConversationPhone(
                            helper,
                            agents.map { NewConversationPhoneAgent(it.id.raw, it.name, it.id.raw) },
                            emptyList(),
                            selected = agent,
                        ),
                )
            )

        override suspend fun phoneAgentModels(profile: ProfileId, agentId: String): Outcome<NewConversationPhoneModels> =
            Outcome.Ok(NewConversationPhoneModels(listed, listed.firstOrNull()?.model))

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("")

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> =
            Outcome.Ok(Unit)

        /**
         * The contract of the real host: for a phone agent the page opens (onStarted) only once the helper answered
         * (the first message Accepted); for a server's own conversation it opens as soon as the send is queued.
         */
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
            val ref = ConversationRef(profile, SessionId("s$starts"))
            if (this.agent == null) onStarted(ref)
            val refusal = gate.await()
            if (refusal != null) return NewConversationAttempt(directory, failure = refusal)
            if (this.agent != null) onStarted(ref)
            return NewConversationAttempt(directory, ref = ref, dispatched = true)
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

    private fun vmFor(host: Host, nav: Nav, agent: String?): NewConversationViewModel {
        val vm = NewConversationViewModel(route, host, nav, Clock.System, ListenedReports())
        if (agent != null) vm.choosePhoneAgent(agent)
        vm.draftChanged(words)
        return vm
    }

    private fun refusal(reason: String) = Failure.ComputerDidNotAnswer(Redactor().details(reason))

    @Test
    fun `ONE-OP the catalog has agents to drive`() {
        assertTrue(agents.size >= 8, "every agent that runs on the phone is covered: ${agents.map { it.id.raw }}")
    }

    @Test
    fun `ONE-OP a started agent opens its page only after the helper answered, with Starting named while it waits`() {
        for (agent in agents) {
            val host = Host(agent.id.raw)
            val nav = Nav(route)
            val vm = vmFor(host, nav, agent.id.raw)
            vm.start()
            val waiting = vm.state.value
            assertTrue(waiting.openingChat && waiting.busy, "${agent.id}: still in New conversation")
            assertEquals(NewConversationWait.Sending, waiting.wait, "${agent.id}")
            assertEquals(agent.name, waiting.phoneAgentName, "${agent.id}: the waiting state names the agent")
            assertEquals(words, waiting.text, "${agent.id}: the words stay while it starts")
            assertTrue(nav.chats.isEmpty(), "${agent.id}: no page before the agent exists")
            host.gate.complete(null)
            assertEquals(1, nav.chats.size, "${agent.id}: the page opens once the agent answered")
            assertEquals("", vm.state.value.text, "${agent.id}: sent words leave the box")
        }
    }

    @Test
    fun `OT-05 a form the opened conversation covers keeps its starting frame, so its box never comes back under it`() {
        // Open-transitions (S22 recording): the form went back to its box (which takes focus whenever it comes into the
        // page) while still drawn under the arriving conversation; the keyboard rose over the conversation and fell
        // again as the form left.
        for (agent in agents) {
            val host = Host(agent.id.raw)
            val nav = Nav(route)
            val vm = vmFor(host, nav, agent.id.raw)
            vm.start()
            host.gate.complete(null)
            assertEquals(1, nav.chats.size, "${agent.id}: the page opened")
            val covered = vm.state.value
            assertTrue(covered.openingChat && covered.busy, "${agent.id}: the covered form keeps its starting frame")
            assertEquals("", covered.text, "${agent.id}: the sent words are not kept in the form")
        }
    }

    @Test
    fun `ONE-OP an agent that refuses to start never opens a page, keeps the words and names the agent`() {
        for (agent in agents) {
            val host = Host(agent.id.raw)
            val nav = Nav(route)
            val vm = vmFor(host, nav, agent.id.raw)
            vm.start()
            host.gate.complete(refusal("Provider ${agent.id} is not ready (unavailable)"))
            val shown = vm.state.value
            assertTrue(nav.chats.isEmpty(), "${agent.id}: no page for a conversation that does not exist")
            assertEquals(0, nav.backs, "${agent.id}: the person is not moved")
            assertEquals(words, shown.text, "${agent.id}: the words stay in the box")
            assertFalse(shown.busy, "${agent.id}")
            val problem = assertNotNull(shown.phoneStartProblem, "${agent.id}: the failure is shown with the agent")
            assertEquals(agent.name, problem.agentName, "${agent.id}")
            assertEquals(agent.id.raw, problem.agentId, "${agent.id}")
            assertEquals(PhoneStartWay.CheckAgain, problem.way, "${agent.id}: not ready means Check <agent> again, not set-up")
        }
    }

    @Test
    fun `ONE-OP a refusal that is not about set-up offers Try again, and a model the helper does not list offers another`() {
        for (agent in agents) {
            val host = Host(agent.id.raw)
            val nav = Nav(route)
            val vm = vmFor(host, nav, agent.id.raw)
            vm.start()
            host.gate.complete(refusal("the agent stopped"))
            assertEquals(PhoneStartWay.TryAgain, vm.state.value.phoneStartProblem?.way, "${agent.id}")

            val other = Host(agent.id.raw)
            val vm2 = vmFor(other, Nav(route), agent.id.raw)
            vm2.chooseModel(ModelRef("gone", "not-listed"))
            vm2.start()
            other.gate.complete(refusal("the agent stopped"))
            val problem = assertNotNull(vm2.state.value.phoneStartProblem, "${agent.id}")
            assertEquals(PhoneStartWay.ChooseModel, problem.way, "${agent.id}: an unlisted model, choose another")
            assertEquals(agent.name, problem.agentName, "${agent.id}")
        }
    }

    @Test
    fun `ONE-OP an agent that answers after a long wait opens its page then, not before`() {
        for (agent in agents) {
            val host = Host(agent.id.raw)
            val nav = Nav(route)
            val vm = vmFor(host, nav, agent.id.raw)
            vm.start()
            // Nothing has answered: however long, the person is still in New conversation with the words.
            assertTrue(nav.chats.isEmpty() && vm.state.value.busy, "${agent.id}")
            assertEquals(words, vm.state.value.text, "${agent.id}")
            host.gate.complete(null)
            assertEquals(1, nav.chats.size, "${agent.id}")
        }
    }

    @Test
    fun `ONE-OP leaving while an agent starts moves back and a late answer opens no page`() {
        val agent = agents.first()
        val host = Host(agent.id.raw)
        val nav = Nav(route)
        val vm = vmFor(host, nav, agent.id.raw)
        vm.start()
        vm.back()
        assertEquals(1, nav.backs, "Back is never dead while an agent starts")
        host.gate.complete(null)
        assertTrue(nav.chats.isEmpty(), "a start that completes after the person left opens no page by itself")
    }

    @Test
    fun `ONE-OP the way forward for a set-up refusal opens that agent's set-up`() {
        val agent = agents.first()
        val host = Host(agent.id.raw)
        val nav = Nav(route)
        val vm = vmFor(host, nav, agent.id.raw)
        vm.start()
        host.gate.complete(refusal("AuthRequired"))
        val problem = assertNotNull(vm.state.value.phoneStartProblem)
        vm.setUpPhoneAgent(problem.agentId)
        assertEquals(listOf<AppRoute>(AgentsRoute.Agent(agent.id.raw, forNewChat = true)), nav.opened)
    }

    @Test
    fun `ONE-OP a server's own conversation still opens at once`() {
        val host = Host(null)
        val nav = Nav(route)
        val vm = vmFor(host, nav, null)
        vm.start()
        assertEquals(1, nav.chats.size, "opened before the send settled, as before")
        assertNull(vm.state.value.phoneStartProblem)
        host.gate.complete(null)
    }
}
