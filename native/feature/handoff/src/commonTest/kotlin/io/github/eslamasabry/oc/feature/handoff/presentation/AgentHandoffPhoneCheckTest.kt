package io.github.eslamasabry.oc.feature.handoff.presentation

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.handoff.*
import io.github.eslamasabry.oc.ui.routes.AgentHandoffRoute
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** B-263: "Continue with" no longer walls on an agent that only waits on its phone check. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentHandoffPhoneCheckTest {
    private val dispatcher = StandardTestDispatcher()
    private val source = ConversationRef(ProfileId("p"), SessionId("s"))
    private val unchecked =
        AgentHandoffChoice("claude\u0000m", "Claude Code", "Sonnet", HandoffChoiceIssue.PhoneCheck, agentId = "claude")
    private val ready = unchecked.copy(issue = null)

    @BeforeTest fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `B-263 an agent waiting only on its phone check is offered, not the no-agent page`() {
        val ui = AgentHandoffUi(loading = false, connected = true, choices = persistentListOf(unchecked))
        assertEquals(HandoffPage.Choose, ui.page())
        assertEquals(null, ui.stateView())
    }

    @Test
    fun `B-263 a phone check with no agent to check still leads to Agents`() {
        val ui =
            AgentHandoffUi(
                loading = false,
                connected = true,
                choices = persistentListOf(unchecked.copy(agentId = null)),
            )
        assertEquals(HandoffPage.NoAgent, ui.page())
    }

    @Test
    fun `B-263 Check opens that agent's own check and the page reads the agents again on return`() =
        runTest(dispatcher) {
            val nav = Nav()
            val host = Host(listOf(listOf(unchecked), listOf(ready)))
            val vm = AgentHandoffViewModel(AgentHandoffRoute(source), host, nav)
            runCurrent()
            assertEquals(listOf(unchecked), vm.state.value.choices)

            // Choosing it does nothing: it cannot run yet.
            vm.select(unchecked.id)
            assertEquals(null, vm.state.value.selected)

            vm.checkAgent(unchecked.id)
            assertEquals(listOf<AppRoute>(AgentsRoute.Agent("claude", forNewChat = true)), nav.opened)

            vm.resumed()
            runCurrent()
            assertEquals(2, host.opens)
            assertEquals(listOf(ready), vm.state.value.choices)
            vm.select(ready.id)
            assertEquals(ready.id, vm.state.value.selected)

            // A later return with no check opened reads nothing again.
            vm.resumed()
            runCurrent()
            assertEquals(2, host.opens)
        }

    private class Nav : AppNavigator {
        val opened = mutableListOf<AppRoute>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            opened += route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    private class Host(private val reads: List<List<AgentHandoffChoice>>) : AgentHandoffHost {
        var opens = 0

        override suspend fun open(source: ConversationRef): Outcome<AgentHandoffSession> =
            Session(reads[minOf(opens++, reads.lastIndex)].toImmutableList()).ok()

        override suspend fun review(source: ConversationRef): Outcome<AgentHandoffReview?> = null.ok()
    }

    private class Session(override val choices: ImmutableList<AgentHandoffChoice>) : AgentHandoffSession {
        override val title: String = "Work"
        override val directory: String = "/root/projects/app"
        override val links: ImmutableList<AgentHandoffLink> = persistentListOf()
        override val state: StateFlow<HandoffState> = MutableStateFlow(HandoffState())

        override fun current(): Boolean = true

        override suspend fun prepare(choice: String): Outcome<HandoffPrepared> = unavailable()

        override fun sanitizePrompt(text: String): String = text

        override suspend fun send(prompt: String): Outcome<HandoffSent> = unavailable()

        override suspend fun retryLink(): Outcome<HandoffSent> = unavailable()

        override suspend fun openLink(id: String, continued: Boolean): Outcome<ConversationRef> = unavailable()

        override fun createdRef(): ConversationRef? = null

        override fun close() = Unit

        private fun <T> unavailable(): Outcome<T> = Failure.Unavailable(FeatureId("test")).failed()
    }
}
