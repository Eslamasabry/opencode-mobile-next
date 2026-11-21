package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import io.github.eslamasabry.oc.feature.chat.E20Scene
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** Stage words: the page's waiting line says each stage in plain words, and the agent's own name for its stage. */
@OptIn(ExperimentalTestApi::class)
class StageWordsLineTest {
    private fun words(wait: ChatServerWait, agent: String?, expected: String) =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val state =
                scene.live().copy(
                    phase = ChatPhase.Loading,
                    rows = persistentListOf(),
                    status = ChatStatusUi("c", ChatStatusKind.Connecting, serverWait = wait),
                )
            setContent { E20Scene { scene.Screen(state, agentName = agent) } }
            assertTrue(
                onAllNodesWithText(expected, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
                "the line says: $expected",
            )
        }

    @Test
    fun `SW-5 the line reads Starting Linux`() = words(ChatServerWait.StartingLinux, "Antigravity", "Starting Linux…")

    @Test
    fun `SW-6 the line reads Starting OpenCode`() =
        words(ChatServerWait.StartingOpenCode, "OpenCode", "Starting OpenCode…")

    @Test
    fun `SW-7 the line reads Starting the agents helper`() =
        words(ChatServerWait.StartingHelper, "Antigravity", "Starting the agents’ helper…")

    @Test
    fun `SW-8 the line names the agent itself while it starts`() =
        words(ChatServerWait.StartingAgent, "Antigravity", "Starting Antigravity…")

    @Test
    fun `SW-9 the line reads Opening the conversation`() =
        words(ChatServerWait.OpeningConversation, "Antigravity", "Opening the conversation…")

    @Test
    fun `SW-10 guard a wait with no stage keeps the generic words`() =
        words(ChatServerWait.Connecting, "Antigravity", "Connecting to Antigravity…")
}
