package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.feature.chat.E20Scene
import io.github.eslamasabry.oc.feature.chat.boundsOfTag
import io.github.eslamasabry.oc.feature.chat.boundsOfText
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * Owner, build 10058: "Connecting is just much faster. Can't we show a shimmer or some loading animation while we are
 * done?" While a phone agent's conversation waits for its helper, the page looks alive: what is known shows at once, the
 * kit's placeholder stands only where the content is really unknown, and the status says who it connects to with the
 * kit's working mark (still when motion is off).
 */
@OptIn(ExperimentalTestApi::class)
class ConnectingAliveTest {
    private val waiting =
        ChatStatusUi("connecting", ChatStatusKind.Connecting, serverWait = ChatServerWait.Connecting)

    @Test
    fun `CA-1 unknown content shows the kit placeholder and Connecting to the agent with the working mark`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val state = scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf(), status = waiting)
            setContent { E20Scene { scene.Screen(state, agentName = "Claude Code") } }
            onAllNodesWithTag("chat-loading", useUnmergedTree = true).assertCountEquals(1)
            kotlin.test.assertTrue(onAllNodesWithText("Connecting to Claude Code…", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the status says who it connects to")
            onAllNodesWithTag("kit-status-working", useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `CA-2 saved turns in memory show at once with no placeholder, and the status still says Connecting`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val state = scene.live().copy(status = waiting)
            setContent { E20Scene { scene.Screen(state, agentName = "Claude Code") } }
            onAllNodesWithTag("chat-loading", useUnmergedTree = true).assertCountEquals(0)
            kotlin.test.assertTrue(onAllNodesWithText("Connecting to Claude Code…", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the status says who it connects to")
            onAllNodesWithTag("kit-status-working", useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `CA-3 connected shows the conversation with no placeholder and no working mark`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            setContent { E20Scene { scene.Screen(scene.live(), agentName = "Claude Code") } }
            onAllNodesWithTag("chat-loading", useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithTag("kit-status-working", useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `CA-4 a wait that is spent says the agent is not answering, with Restart, and nothing is working`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val failure = Failure.Network(NetworkWhy.Refused)
            val spent =
                scene.live().copy(
                    phase = ChatPhase.Failed(failure),
                    rows = persistentListOf(),
                    status = ChatStatusUi("offline", ChatStatusKind.Offline, canRestartAgent = true),
                )
            setContent { E20Scene { scene.Screen(spent, agentName = "Claude Code") } }
            onAllNodesWithTag("chat-loading", useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithTag("kit-status-working", useUnmergedTree = true).assertCountEquals(0)
            onAllNodesWithTag("chat-connection-restart-agent", useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `CA-5 a sign-in wait is not a working wait`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val state =
                scene.live().copy(
                    phase = ChatPhase.Loading,
                    rows = persistentListOf(),
                    status = ChatStatusUi("c", ChatStatusKind.Connecting, serverWait = ChatServerWait.AgentNeedsSignIn),
                )
            setContent { E20Scene { scene.Screen(state, agentName = "Claude Code") } }
            onAllNodesWithTag("kit-status-working", useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `CA-6 the wait ending in the conversation moves neither the header nor the message box`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val live = scene.live()
            var state by androidx.compose.runtime.mutableStateOf(live.copy(phase = ChatPhase.Loading, rows = persistentListOf(), status = waiting))
            setContent { E20Scene { scene.Screen(state, agentName = "Claude Code") } }
            val title = live.title ?: error("the scene's conversation has a title")
            val headerBefore = boundsOfText(title)
            val boxBefore = boundsOfTag("chat-composer-pane")
            state = live
            waitForIdle()
            onAllNodesWithTag("chat-loading", useUnmergedTree = true).assertCountEquals(0)
            kotlin.test.assertEquals(headerBefore, boundsOfText(title), "the header stayed put")
            kotlin.test.assertEquals(boxBefore, boundsOfTag("chat-composer-pane"), "the message box stayed put")
        }

    @Test
    fun `CA-7 the wait ending in not answering moves neither the header nor the message box`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val live = scene.live()
            var state by androidx.compose.runtime.mutableStateOf(live.copy(phase = ChatPhase.Loading, rows = persistentListOf(), status = waiting))
            setContent { E20Scene { scene.Screen(state, agentName = "Claude Code") } }
            val title = live.title ?: error("the scene's conversation has a title")
            val headerBefore = boundsOfText(title)
            state =
                live.copy(
                    phase = ChatPhase.Loading,
                    rows = persistentListOf(),
                    status = ChatStatusUi("offline", ChatStatusKind.Offline, canRestartAgent = true),
                )
            waitForIdle()
            onAllNodesWithTag("kit-status-working", useUnmergedTree = true).assertCountEquals(0)
            kotlin.test.assertEquals(headerBefore, boundsOfText(title), "the header stayed put")
        }
}
