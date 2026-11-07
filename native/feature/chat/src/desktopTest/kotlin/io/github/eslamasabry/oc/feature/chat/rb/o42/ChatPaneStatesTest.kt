package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.semantics.SemanticsActions
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chat.E20Scene
import io.github.eslamasabry.oc.feature.chat.boundsOfTag
import io.github.eslamasabry.oc.feature.chat.boundsOfText
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatPane
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scoreboard 2026-11-07 group E (order 42): the chat's pane, states, find and bar, against Flutter
 * chat_transcript.dart:146, chat_states.dart:22 and :69-95, chat_composer_region.dart:279 and chat_top_bar.dart:180-190.
 * Each test fails on the screen as it was before the fix.
 */
@OptIn(ExperimentalTestApi::class)
class ChatPaneStatesTest {
    private val cap = ChatPane.maxWidth(FlutterKitTokens.GUTTER).value

    @Test
    fun `E1 the conversation and the composer are 700 dp plus gutters wide and centred on a wide window`() =
        runDesktopComposeUiTest(1280, 800) {
            val scene = ChatScene()
            setContent { E20Scene { scene.Screen(scene.live()) } }
            waitForIdle()
            val list = boundsOfTag("transcript-list")
            assertTrue(list.width <= cap + 0.5f, "transcript ${list.width} dp wide, cap $cap")
            assertTrue(abs(list.center.x - 640f) <= 1f, "transcript centred, was at ${list.center.x}")
            val composer = boundsOfTag("chat-composer-pane")
            assertTrue(composer.width <= cap + 0.5f, "E20-S27: composer ${composer.width} dp wide, cap $cap")
            assertTrue(abs(composer.center.x - 640f) <= 1f, "composer centred, was at ${composer.center.x}")
        }

    @Test
    fun `E1 a phone keeps the full width`() = runDesktopComposeUiTest(412, 915) {
        val scene = ChatScene()
        setContent { E20Scene { scene.Screen(scene.live()) } }
        waitForIdle()
        assertEquals(412f, boundsOfTag("transcript-list").width, 0.5f)
    }

    @Test
    fun `E3 first load shows two placeholder turns at the bottom and no composer`() = runDesktopComposeUiTest(412, 915) {
        val scene = ChatScene()
        setContent { E20Scene { scene.Screen(scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf())) } }
        waitForIdle()
        val skeleton = boundsOfTag("chat-loading")
        assertTrue(skeleton.bottom > 915f * 0.75f, "the placeholder turns rest at the bottom, ended at ${skeleton.bottom}")
        onAllNodesWithTag("kit-composer-box", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `E4 could not load is a centred page with the cable, Report a problem and Details, without a composer`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            setContent {
                E20Scene { scene.Screen(scene.live().copy(phase = ChatPhase.Failed(Failure.Unexpected()), rows = persistentListOf())) }
            }
            waitForIdle()
            boundsOfTag("kit-state-illustration")
            val title = boundsOfText("Couldn't open this conversation")
            assertTrue(title.top > 200f, "the page state sits in the middle of the room, title at ${title.top}")
            onNodeWithTag("kit-details-fold", useUnmergedTree = true).fetchSemanticsNode()
            onAllNodesWithTag("kit-composer-box", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithText("Report a problem").performClick()
            assertEquals(listOf<Any>(SettingsRoute.ReportProblem), scene.nav.opened.toList())
        }

    @Test
    fun `E2 find sits over the composer, which stays`() = runDesktopComposeUiTest(412, 915) {
        val scene = ChatScene()
        setContent { E20Scene { scene.Screen(scene.live().copy(find = FindUi("total", 2, 0, null, 1, false))) } }
        waitForIdle()
        val find = boundsOfTag("transcript-find-bar")
        val composer = boundsOfTag("kit-composer-box")
        assertTrue(find.bottom <= composer.top + 0.5f, "find $find over the composer $composer")
    }

    @Test
    fun `E5 at the window's root the title starts on the gutter and has no chevron`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            setContent { E20Scene { scene.Screen(scene.live()) } }
            waitForIdle()
            onAllNodesWithTag("kit-top-bar-exit", useUnmergedTree = true).assertCountEquals(0)
            val title = boundsOfTag("kit-top-bar-title")
            assertEquals(16f, title.left, 1f, "the title's words start on the 16 dp gutter")
            // Flutter's chat title is not a switcher: Rename is in the ⋯ menu.
            onAllNodesWithTag("kit-top-bar-title", useUnmergedTree = true)
                .assertCountEquals(1)
            val tappable = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true)
                .fetchSemanticsNodes().filter { it.boundsInRoot.contains(title.center) }
            assertEquals(0, tappable.size, "the title is not tappable")
        }

    @Test
    fun `E5 opened from somewhere the chat keeps Back, and a known branch shows on the project line`() = runDesktopComposeUiTest(412, 915) {
        val scene = ChatScene()
        setContent { E20Scene { scene.Screen(scene.live(), canGoBack = true, branch = "main") } }
        waitForIdle()
        boundsOfTag("kit-top-bar-exit")
        boundsOfText("main")
    }
}
