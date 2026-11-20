package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/** FLICKER F14 / A2: what Home's status places do when a line comes or a stage changes its words. */
@OptIn(ExperimentalTestApi::class)
class HomeStatusSteadyTest {
    private fun hasContentDescriptionContaining(text: String) =
        SemanticsMatcher("content description contains $text") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text in it } == true
        }

    @Test
    fun `B-HOME-221 a status line that arrives does not drop the list by its whole height in one frame`() =
        runComposeUiTest {
            var state by mutableStateOf(mixed())
            mainClock.autoAdvance = false
            setContent {
                ChatEnvironment {
                    KitTheme(
                        appearance = KitAppearance(motion = MotionLevel.Full),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                    ) {
                        ChatsScreen(state, RecordingChatsActions())
                    }
                }
            }
            mainClock.advanceTimeBy(1_000)
            fun top(): Float = onNode(hasContentDescriptionContaining("Write the notes")).fetchSemanticsNode().boundsInRoot.top
            val before = top()
            state =
                state.copy(download = HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Running(10)))
            mainClock.advanceTimeBy(16)
            val firstFrame = top()
            mainClock.advanceTimeBy(1_500)
            val settled = top()
            assertTrue(settled > before + 10f, "the line took room: $before -> $settled")
            assertTrue(
                firstFrame - before < (settled - before) * 0.6f,
                "the list moved $before -> $firstFrame in one frame of a $before -> $settled drop",
            )
        }

    @Test
    fun `B-HOME-222 the helper's two stages read with the same words`() = runComposeUiTest {
        var stage by mutableStateOf(HomeStage.Helper)
        setContent {
            ChatEnvironment {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                ) {
                    val base = mixed()
                    ChatsScreen(base.copy(home = base.home.copy(stage = stage)), RecordingChatsActions())
                }
            }
        }
        waitForIdle()
        onNodeWithText("Starting the agents’ helper…").assertExists()
        stage = HomeStage.Agents
        waitForIdle()
        onNodeWithText("Starting the agents’ helper…").assertExists()
    }
}
