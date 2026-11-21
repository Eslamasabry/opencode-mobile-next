package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldAgents
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DOWNLOAD-CONSENT: what Home's one status line says about the two automatic downloads, and the ask. */
@OptIn(ExperimentalTestApi::class)
class HomeDownloadLineTest {
    private class Actions : RecordingChatsActions() {
        override fun actOnDownload() { calls += "act" }
        override fun downloadNow() { calls += "now" }
        override fun waitForWifi() { calls += "wait" }
    }

    private val actions = Actions()

    @Composable
    private fun Home(state: ChatsUi) {
        ChatEnvironment {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
            ) {
                ChatsScreen(state, actions)
            }
        }
    }

    private fun home(fact: HomeDownloadFact?, ask: HomeDownloadKind? = null) =
        ChatsUi(phase = ChatsPhase.Empty, download = fact, downloadAsk = ask)

    @Test
    fun `DOWNLOAD-CONSENT a waiting OpenCode update says so with its size, and its action opens the ask`() = kitUiTest {
        setContent { Home(home(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting))) }
        waitForIdle()
        onNodeWithText("OpenCode update waiting for Wi-Fi · about 130 MB").assertExists()
        onNodeWithText("Download on mobile data").performClick()
        assertEquals(listOf("act"), actions.calls)
    }

    @Test
    fun `B-DL-405 a waiting OpenCode update with agents held says both in the same row`() = kitUiTest {
        val agents = HomeHeldAgents(3, 660)
        val fact = HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting, moreAgents = agents)
        setContent { Home(home(fact)) }
        waitForIdle()
        // The status line announces its message and supporting line as one node.
        onNodeWithText("OpenCode update waiting for Wi-Fi · about 130 MB", substring = true).assertExists()
        onNodeWithText("3 more agent updates wait for Wi-Fi · about 660 MB", substring = true).assertExists()
        onNodeWithText("Download on mobile data").performClick()
        assertEquals(listOf("act"), actions.calls)
    }

    @Test
    fun `B-DL-425 OpenCode and its helper are one line with one total`() = kitUiTest {
        val fact =
            HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting, megabytes = 460, withHelper = true)
        setContent { Home(home(fact)) }
        waitForIdle()
        onNodeWithText("OpenCode update and Agent helper waiting for Wi-Fi · about 460 MB").assertExists()
    }

    @Test
    fun `B-DL-427 the ask for OpenCode with its helper names both and the one total`() = kitUiTest {
        val fact =
            HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting, megabytes = 460, withHelper = true)
        setContent { Home(home(fact, ask = HomeDownloadKind.OpenCode)) }
        waitForIdle()
        onNodeWithText("Download about 460 MB on mobile data?").assertExists()
        onNodeWithText(
            "This is the OpenCode update and the Agent helper it needs. It counts against your data plan. " +
                "Waiting for Wi-Fi keeps it for later.",
        ).assertExists()
    }

    @Test
    fun `B-DL-406 one more agent update reads in the singular`() = kitUiTest {
        val agents = HomeHeldAgents(1, 360)
        val fact = HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Waiting, moreAgents = agents)
        setContent { Home(home(fact)) }
        waitForIdle()
        onNodeWithText("1 more agent update waits for Wi-Fi · about 360 MB", substring = true).assertExists()
    }

    @Test
    fun `B-DL-407 only agents held is its own line`() = kitUiTest {
        val fact = HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.AgentsWaiting(HomeHeldAgents(3, 660)))
        setContent { Home(home(fact)) }
        waitForIdle()
        onNodeWithText("3 agent updates waiting for Wi-Fi · about 660 MB").assertExists()
    }

    @Test
    fun `B-DL-444 the agents-only waiting line offers Download on mobile data and its tap is the yes`() = kitUiTest {
        val fact = HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.AgentsWaiting(HomeHeldAgents(3, 660)))
        setContent { Home(home(fact)) }
        waitForIdle()
        onNodeWithText("Download on mobile data").performClick()
        assertEquals(listOf("act"), actions.calls)
    }

    @Test
    fun `B-DL-436 the helper line uses the same name as the combined ask`() = kitUiTest {
        setContent { Home(home(HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Waiting))) }
        waitForIdle()
        onNodeWithText("Agent helper update waiting for Wi-Fi · about 330 MB").assertExists()
    }

    @Test
    fun `DOWNLOAD-CONSENT the ask names the size and offers Download now and Wait for Wi-Fi`() = kitUiTest {
        setContent {
            Home(home(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting), HomeDownloadKind.OpenCode))
        }
        waitForIdle()
        onNodeWithText("Download about 130 MB on mobile data?").assertExists()
        onNodeWithText("Wait for Wi-Fi").performClick()
        onNodeWithText("Download now").performClick()
        assertEquals(listOf("wait", "now"), actions.calls)
    }

    @Test
    fun `DOWNLOAD-CONSENT no ask is open until the line's action opened it`() = kitUiTest {
        setContent { Home(home(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting))) }
        waitForIdle()
        assertTrue(onAllNodesWithText("Download about 130 MB on mobile data?").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `DOWNLOAD-CONSENT a running download shows the share done as a number and no action`() = kitUiTest {
        setContent { Home(home(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Running(40)))) }
        waitForIdle()
        onNodeWithText("Updating OpenCode · 40%").assertExists()
        assertTrue(onAllNodesWithText("Download on mobile data").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `B-DL-435 OpenCode shows its download size while running without a percentage`() = kitUiTest {
        setContent { Home(home(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Running(null)))) }
        waitForIdle()
        onNodeWithText("Updating OpenCode · 130 MB").assertExists()
        onNodeWithText("Download on mobile data").assertDoesNotExist()
    }

    @Test
    fun `DOWNLOAD-CONSENT an unknown share shows only the working words`() = kitUiTest {
        setContent { Home(home(HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Running(null)))) }
        waitForIdle()
        onNodeWithText("Updating the agent helper").assertExists()
    }

    @Test
    fun `DOWNLOAD-CONSENT a failure says it in plain words with Try again`() = kitUiTest {
        setContent { Home(home(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Failed))) }
        waitForIdle()
        onNodeWithText("The OpenCode update didn't finish.").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("act"), actions.calls)
    }

    @Test
    fun `DOWNLOAD-CONSENT nothing downloading leaves the line empty`() = kitUiTest {
        setContent { Home(home(null)) }
        waitForIdle()
        assertTrue(onAllNodesWithText("Updating OpenCode", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText("waiting for Wi-Fi", substring = true).fetchSemanticsNodes().isEmpty())
    }

    private fun listed(fact: HomeDownloadFact?, stage: HomeStage? = null): ChatsUi {
        val base = mixed()
        return base.copy(download = fact, home = base.home.copy(stage = stage))
    }

    @Test
    fun `B-HOME-211 on a list the waiting update is a status line of its own above the lists, with its action`() =
        kitUiTest {
            val waiting = HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting)
            setContent { Home(listed(waiting)) }
            waitForIdle()
            onNodeWithText("OpenCode update waiting for Wi-Fi · about 130 MB").assertExists()
            // 7bbf340fd0: it no longer sits as bare words in Recent's status place (under the Recent heading, where it
            // read as part of Recent); it is a kit status line of its own with its mark and action.
            assertTrue(onAllNodesWithTag("kit-row-panel-status", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
            onNodeWithText("Download on mobile data").performClick()
            assertEquals(listOf("act"), actions.calls)
        }

    @Test
    fun `B-HOME-211 while the helper starts its words stay with Recent and the waiting update is its own line`() =
        kitUiTest {
            val waiting = HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting)
            setContent { Home(listed(waiting, HomeStage.Helper)) }
            waitForIdle()
            // 7bbf340fd0: the Recent heading keeps only the start-up stage; the update's line stacks above the lists.
            onNodeWithText("Starting the agents’ helper…").assertExists()
            onNodeWithText("OpenCode update waiting for Wi-Fi · about 130 MB").assertExists()
            onNodeWithText("Download on mobile data").assertExists()
        }
}
