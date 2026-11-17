package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    // Owner 2026-11-14 (a loading conversation was a top bar and nothing else): was "... and no composer". The message
    // box stands in its place while the conversation loads, so the page keeps its shape and nothing moves when it has.
    fun `E3 first load shows two placeholder turns at the bottom over the message box, which keeps its place`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            var state by mutableStateOf(scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf()))
            setContent { E20Scene { scene.Screen(state) } }
            waitForIdle()
            val skeleton = boundsOfTag("chat-loading")
            val loadingBox = boundsOfTag("kit-composer-box")
            assertTrue(skeleton.bottom > 915f * 0.6f, "the placeholder turns rest at the bottom, ended at ${skeleton.bottom}")
            assertTrue(skeleton.bottom <= loadingBox.top + 0.5f, "the turns $skeleton end above the message box $loadingBox")
            state = scene.live()
            waitForIdle()
            val liveBox = boundsOfTag("kit-composer-box")
            for ((what, pair) in listOf(
                "top" to (loadingBox.top to liveBox.top),
                "bottom" to (loadingBox.bottom to liveBox.bottom),
                "left" to (loadingBox.left to liveBox.left),
                "right" to (loadingBox.right to liveBox.right),
            )) assertEquals(pair.first, pair.second, 0.5f, "the message box's $what while loading and live")
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
    fun `E5 at the window's root the title starts 16 dp inside the header capsule and has no chevron`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            setContent { E20Scene { scene.Screen(scene.live()) } }
            waitForIdle()
            onAllNodesWithTag("kit-top-bar-exit", useUnmergedTree = true).assertCountEquals(0)
            val title = boundsOfTag("kit-top-bar-title")
            // B-438: the header is one floating capsule; the bar's own 16 dp gutter now lies inside it.
            val capsule = boundsOfTag("kit-header-capsule")
            assertEquals(capsule.left + 16f, title.left, 1f, "the title's words start 16 dp inside the header capsule")
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
