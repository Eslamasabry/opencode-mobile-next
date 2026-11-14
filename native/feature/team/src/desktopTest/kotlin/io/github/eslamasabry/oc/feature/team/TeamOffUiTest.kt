package io.github.eslamasabry.oc.feature.team

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.assertTrue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamIntroUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamServer
import io.github.eslamasabry.oc.feature.team.presentation.TeamServerKind
import io.github.eslamasabry.oc.feature.team.ui.TeamHomeScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** Device walk 2026-11-08: the off page says what the team does, what it needs, and how to turn it on. */
@OptIn(ExperimentalTestApi::class)
class TeamOffUiTest {
    private class Recorder : TeamHomeActions {
        val calls = mutableListOf<String>()
        override fun back() = Unit
        override fun openProject(id: String) = Unit
        override fun openRequest(projectId: String, requestId: String) = Unit
        override fun resumeProject(projectId: String) = Unit
        override fun retry() = Unit
        override fun turnOn() { calls += "turnOn" }
        override fun setUpOnPhone() { calls += "setUpOnPhone" }
        override fun openGuide() { calls += "openGuide" }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            KitChatEnvironment(Redactor()).Provide { content() }
        }
    }

    private fun off(intro: TeamIntroUi) = TeamHomeUi(phase = TeamPhase.Off, intro = intro)

    @Test
    fun `B-AGENT-294 this phone's off page explains the team and turns it on`() = runComposeUiTest {
        val actions = Recorder()
        setContent {
            Themed {
                TeamHomeScreen(
                    off(TeamIntroUi(TeamServer(TeamServerKind.ThisPhone, "This phone"), canTurnOn = true)),
                    actions,
                )
            }
        }
        onNodeWithText("Off").assertExists()
        onNodeWithText("Give a bigger job to a team").assertExists()
        onNodeWithText("It plans").assertExists()
        onNodeWithTag("team-intro").performScrollToNode(hasText("What it needs on this phone"))
        onNodeWithText("Installed on this phone").assertExists()
        onNodeWithText("Turn on AI Team on this phone").performClick()
        assertEquals(listOf("turnOn"), actions.calls)
        // The old dead end is gone.
        onNodeWithText("AI Team is off").assertDoesNotExist()
    }

    @Test
    fun `W2-023 the turn on button stands on the page gutter, not a second one in`() = runComposeUiTest {
        setContent {
            Themed {
                TeamHomeScreen(
                    off(TeamIntroUi(TeamServer(TeamServerKind.ThisPhone, "This phone"), canTurnOn = true)),
                    Recorder(),
                )
            }
        }
        val window = onAllNodesWithTag("kit-screen", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val actions = onAllNodesWithTag("team-intro-actions", useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val gutter = 16.dp
        assertTrue(
            (actions.left - window.left) < gutter + 1.dp,
            "the button block starts ${actions.left - window.left} in, the page's gutter is $gutter",
        )
    }

    @Test
    fun `B-AGENT-294 a computer's off page names it and opens the host guide`() = runComposeUiTest {
        val actions = Recorder()
        setContent {
            Themed { TeamHomeScreen(off(TeamIntroUi(TeamServer(TeamServerKind.Computer, "studio-pc"))), actions) }
        }
        onNodeWithTag("team-intro").performScrollToNode(hasText("What it needs on studio-pc"))
        onNodeWithText("Runs on studio-pc").assertExists()
        onNodeWithText("Set up AI Team on studio-pc").performClick()
        assertEquals(listOf("openGuide"), actions.calls)
    }

    @Test
    fun `B-AGENT-294 the host guide shows the four steps with their commands`() = runComposeUiTest {
        setContent {
            Themed {
                TeamHomeScreen(
                    off(TeamIntroUi(TeamServer(TeamServerKind.Computer, "studio-pc"), guide = true)),
                    Recorder(),
                )
            }
        }
        onNodeWithText("Run an AI team on your computer").assertExists()
        onNodeWithTag("team-host-guide-step-1", useUnmergedTree = true).assertExists()
        onNodeWithTag("team-host-guide-step-4", useUnmergedTree = true).assertExists()
    }
}
