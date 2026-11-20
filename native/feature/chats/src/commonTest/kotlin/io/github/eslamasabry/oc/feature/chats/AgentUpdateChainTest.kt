package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.pace.DefaultPacer
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetModel
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdatesViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeWithAgentUpdates
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

// Metro resolves the same non-null port key that the phone contributes to Home.
@DependencyGraph(AgentUpdateTestGraph::class)
internal interface AgentUpdateTestGraph {
    val updates: AgentUpdatesViewModel

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides choices: AgentUpdateChoices, @Provides pacer: Pacer, @Provides nav: AppNavigator): AgentUpdateTestGraph
    }
}

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class AgentUpdateChainTest {
    private val models = mutableListOf<AgentUpdatesViewModel>()

    private fun home(port: FakeAgentUpdateChoices): AgentUpdatesViewModel =
        createGraphFactory<AgentUpdateTestGraph.Factory>()
            .create(port, DefaultPacer(MutableStateFlow(Visibility.Foreground)), UnusedNavigation)
            .updates.also(models::add)

    @AfterTest
    fun closeModels() = models.forEach { it.viewModelScope.cancel() }

    @Composable
    private fun Home(vm: AgentUpdatesViewModel) {
        val updates by vm.state.collectAsState()
        val actions = remember(vm) { HomeWithAgentUpdates(RecordingChatsActions(), vm) }
        Themed { ChatsScreen(ChatsUi(phase = ChatsPhase.Empty, agentUpdates = updates), actions) }
    }

    @Test
    fun `B-UPD-001 a due offer reaches the Home sheet through the bound port`() = runComposeUiTest {
        val port = FakeAgentUpdateChoices(null)
        val vm = home(port)
        setContent { Home(vm) }
        onNodeWithText(TITLE).assertDoesNotExist()
        runOnIdle { port.offer.value = FakeAgentUpdateChoices.dueOffer() }
        onNodeWithText(TITLE).assertExists()
        onNodeWithText("Update 1 agent").assertIsEnabled()
    }

    @Test
    fun `B-UPD-002 Not now leaves a Review action on Home across a restart`() = runComposeUiTest {
        val port = FakeAgentUpdateChoices()
        var vm by mutableStateOf(home(port))
        setContent { key(vm) { Home(vm) } }
        onNodeWithText("Not now").performClick()
        onNodeWithText(TITLE).assertDoesNotExist()
        onNodeWithText("Agent updates ready").assertExists()
        onNodeWithText("Review updates").assertExists()
        runOnIdle {
            assertTrue(port.current.answered)
            vm.viewModelScope.cancel()
            vm = home(port)
        }
        onNodeWithText(TITLE).assertDoesNotExist()
        onNodeWithText("Agent updates ready").assertExists()
        onNodeWithText("Review updates").performClick()
        onNodeWithText(TITLE).assertExists()
        assertEquals(1, port.reviews.count { it })
    }

    @Test
    fun `B-UPD-003 a refresh finishing after the decision leaves the sheet closed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val port = FakeAgentUpdateChoices()
            val reading = CompletableDeferred<Unit>()
            port.finishReading = reading
            val vm = home(port)
            backgroundScope.launch { vm.state.collect {} }
            runCurrent()
            assertTrue(vm.state.value.sheet.visible)
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(listOf(false), port.reviews)
            vm.proceedAgentUpdates()
            runCurrent()
            assertEquals(AgentUpdateChoice.Update, port.current.items.single().choice)
            assertFalse(vm.state.value.sheet.visible)
            reading.complete(Unit)
            runCurrent()
            advanceTimeBy(30_000)
            runCurrent()
            assertFalse(port.current.sheetDue)
            assertFalse(vm.state.value.sheet.visible)
            assertEquals(listOf(false), port.reviews)
        } finally {
            closeModels()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `B-UPD-004 changes to room and network leave the answer closed until the question changes`() = runTest {
        // Review of a deferred offer is both answered and due. Re-deliver that question with new readings.
        val port = FakeAgentUpdateChoices()
        val sheet = AgentUpdateSheetModel(port, backgroundScope)
        sheet.notNow()
        runCurrent()
        sheet.review()
        runCurrent()
        val reviewed = port.current
        assertTrue(reviewed.answered && sheet.current().sheet.visible)
        sheet.proceed()
        runCurrent()
        assertFalse(sheet.current().sheet.visible)
        port.offer.value = reviewed.copy(freeBytes = 0)
        assertFalse(sheet.current().sheet.visible)
        port.offer.value = reviewed.copy(network = AgentUpdateNetwork.Metered)
        assertFalse(sheet.current().sheet.visible)
        val row = reviewed.items.single()
        port.offer.value = reviewed.copy(items = listOf(row.copy(toVersion = "3")))
        assertTrue(sheet.current().sheet.visible)
        port.offer.value = reviewed.copy(items = listOf(row.copy(id = AgentId.CLAUDE, name = "Claude Code")))
        assertTrue(sheet.current().sheet.visible)
    }

    @Test
    fun `B-UPD-005 the update button explains low space and enables when the reading fits`() = runComposeUiTest {
        val free = 40 * FakeAgentUpdateChoices.MIB
        val port = FakeAgentUpdateChoices(FakeAgentUpdateChoices.dueOffer().copy(freeBytes = free))
        val vm = home(port)
        setContent { Home(vm) }
        val button = onNodeWithText("Update 1 agent")
        val needed = checkNotNull(port.current.items.single().downloadBytes)
        val reason = button.assertIsNotEnabled().fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertEquals(
            "These updates need about ${PhoneSizes.text(needed)} and this phone has ${PhoneSizes.text(free)} free. " +
                "Keep or remove an agent, or free up space.",
            reason,
        )
        runOnIdle { port.offer.value = port.current.copy(freeBytes = needed) }
        button.assertIsEnabled()
    }

    // All five journeys stay on Home. Any attempt to leave it fails the test.
    private object UnusedNavigation : AppNavigator {
        override val top: StateFlow<AppRoute?> get() = error("Unexpected navigation read")
        override fun open(route: AppRoute): Unit = error("Unexpected navigation")
        override fun openChat(ref: ConversationRef, focusComposer: Boolean): Unit = error("Unexpected navigation")
        override fun land(): Unit = error("Unexpected navigation")
        override fun replaceRoot(root: AppRoute): Unit = error("Unexpected navigation")
        override fun back(): Unit = error("Unexpected navigation")
    }

    private companion object {
        const val TITLE = "Updates for your agents"
    }
}
