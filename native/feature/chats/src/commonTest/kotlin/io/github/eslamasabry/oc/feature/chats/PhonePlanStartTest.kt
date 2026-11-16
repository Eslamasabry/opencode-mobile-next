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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Plan chosen on New conversation for a phone agent reaches the first message (it was dropped: the mode went null). */
@OptIn(ExperimentalCoroutinesApi::class)
class PhonePlanStartTest {
    private val server = ProfileId("phone")
    private val helper = ProfileId("phone-agents")
    private val route = ChatsRoute.New(server)
    private val agents = AgentCatalog.builtIn.agents.filter { it.runsOnPhoneHost }

    private class Nav(route: AppRoute) : AppNavigator {
        override val top = MutableStateFlow<AppRoute?>(route)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class Host : NewConversationHost {
        val modes = mutableListOf<String?>()
        override val servers = MutableStateFlow(listOf(NewConversationServer(server, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(
                NewConversationSetup(
                    directory = "/root/projects/app",
                    projects = emptyList(),
                    models = listOf(NewConversationModel(ModelRef("server", "m"), "Server model")),
                    agents = emptyList(),
                    modes = listOf("Build", "Plan"),
                    phone =
                        NewConversationPhone(
                            helper,
                            agents.map { NewConversationPhoneAgent(it.id.raw, it.name, it.id.raw) },
                            emptyList(),
                            selected = null,
                        ),
                )
            )

        override suspend fun phoneAgentModels(profile: ProfileId, agentId: String): Outcome<NewConversationPhoneModels> {
            val m = NewConversationModel(ModelRef(agentId, "$agentId-model"), "model")
            return Outcome.Ok(NewConversationPhoneModels(listOf(m), m.model))
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
            modes += agent
            val ref = ConversationRef(profile, SessionId("s${modes.size}"))
            onStarted(ref)
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

    private fun started(agent: String, plan: Boolean): Pair<List<String?>, Boolean> {
        val host = Host()
        val vm = NewConversationViewModel(route, host, Nav(route), Clock.System, ListenedReports())
        vm.choosePhoneAgent(agent)
        if (plan) vm.chooseMode("Plan")
        vm.draftChanged("Create p1.txt with hi")
        vm.start()
        return host.modes to vm.state.value.phoneCanPlan
    }

    @Test
    fun `B-AGENT-PLAN-002 Plan chosen for a phone agent that can hold it reaches the first message`() {
        for (agent in agents) {
            val (modes, canPlan) = started(agent.id.raw, plan = true)
            if (canPlan) assertEquals(listOf<String?>("plan"), modes, "${agent.id}: Plan rides the first message")
            else assertEquals(listOf<String?>(null), modes, "${agent.id}: an agent that cannot hold Plan is never sent it")
        }
        assertTrue(agents.any { started(it.id.raw, plan = true).second }, "at least Codex and Claude Code can hold Plan")
    }

    @Test
    fun `B-AGENT-PLAN-003 without choosing Plan the first message carries no mode`() {
        for (agent in agents) {
            val (modes, _) = started(agent.id.raw, plan = false)
            assertEquals(listOf<String?>(null), modes, "${agent.id}")
        }
        assertFalse(started("codex", plan = false).first.contains("plan"))
    }
}
