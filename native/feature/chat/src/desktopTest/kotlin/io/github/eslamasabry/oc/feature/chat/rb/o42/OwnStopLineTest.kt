package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.E20Scene
import io.github.eslamasabry.oc.feature.chat.FakeChatWorld
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** The person's own stop on the chat's line: one sentence and Start OpenCode; no failure words, no countdown. */
@OptIn(ExperimentalTestApi::class)
class OwnStopLineTest {
    private class Phone : FakeChatWorld() {
        override val serverOnThisPhone: Boolean get() = true

        override suspend fun startPhoneServer(): Outcome<Unit> {
            calls += "start"
            return Outcome.Ok(Unit)
        }
    }

    @Test
    fun `B-CHAT-309 after the person's stop the line says it and offers Start OpenCode`() =
        runDesktopComposeUiTest(412, 915) {
            val world = Phone()
            val scene = ChatScene(world = world)
            // A link that dropped with a retry scheduled: before the fix this read "isn't answering / trying again".
            val state =
                scene.live().copy(
                    phase = ChatPhase.Loading,
                    rows = persistentListOf(),
                    status =
                        ChatStatusUi(
                            "c",
                            ChatStatusKind.Offline,
                            retryInSeconds = 6,
                            canRestart = true,
                            serverWait = ChatServerWait.StoppedByPerson,
                        ),
                )
            setContent { E20Scene { scene.Screen(state) } }
            fun shown(text: String) =
                onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            assertTrue(shown("You stopped OpenCode on this phone"))
            assertTrue(shown("Start OpenCode"))
            for (failure in listOf("isn't answering", "trying again", "Try now", "didn't start", "Restart")) {
                assertTrue(!shown(failure), "no \"$failure\" after the person's own stop")
            }
            onNodeWithText("Start OpenCode", useUnmergedTree = true).performClick()
            waitForIdle()
            assertEquals(listOf("start"), world.calls)
        }
}
