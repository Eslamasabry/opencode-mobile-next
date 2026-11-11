package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatEdgeDock
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * Device run 6: the dock's Undo bar sat over the newest reply's footer. While the bar shows, the transcript keeps the
 * bar's height as extra bottom room (the dock reports it), so the newest content ends above the bar; when the bar
 * goes, the room goes. Away from the end nothing the person reads moves.
 */
@OptIn(ExperimentalTestApi::class)
class DockUndoRoomTest {
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    private class RoomDock : ChatEdgeDock {
        var offered by mutableStateOf(true)

        @Composable
        override fun Content(chrome: PaddingValues, onBottomRoom: (Int) -> Unit) {
            Box(Modifier.fillMaxSize()) {
                KitUndoBar(
                    offer = if (offered) KitUndoOffer("dock-removed-a", "Alpha left the dock") else null,
                    onUndo = {},
                    onTimeout = {},
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = chrome.calculateBottomPadding()),
                    onHeight = onBottomRoom,
                )
            }
        }
    }

    private fun run(rowCount: Int, body: androidx.compose.ui.test.ComposeUiTest.(RoomDock) -> Unit) = runComposeUiTest {
        mainClock.autoAdvance = false
        val rows =
            persistentListOf(
                FooterRow("footer:m0", "m0", failed = false, stopped = true),
                *(0 until rowCount).map { block("m$it", "m$it", "p$it", 0, "Reply number $it") }.toTypedArray(),
            )
        val state = ChatUi.loading(Ref).copy(title = "say hi", phase = ChatPhase.Live, rows = rows)
        val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        val dock = RoomDock()
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.size(412.dp, 820.dp)) { ChatScreen(state, presenter, composer, edgeDock = dock) }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        body(dock)
    }

    @Test
    fun `B-DOCK-UNDO-05 the newest reply ends above the Undo bar and comes back down when the bar goes`() =
        run(rowCount = 1) { dock ->
            val undo = onNodeWithText("Undo").getBoundsInRoot()
            val more = onNodeWithContentDescription("More for this reply", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(more.bottom <= undo.top, "the reply's footer ends above the bar: Undo $undo, More $more")
            runOnUiThread { dock.offered = false }
            mainClock.advanceTimeBy(1_000)
            val after = onNodeWithContentDescription("More for this reply", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(after.bottom > more.bottom, "the room went with the bar: before $more, after $after")
        }

    @Test
    fun `B-DOCK-UNDO-06 reading older replies, nothing they see moves when the bar comes and goes`() =
        run(rowCount = 30) { dock ->
            runOnUiThread { dock.offered = false }
            mainClock.advanceTimeBy(1_000)
            onRoot().performTouchInput { swipeDown() }
            mainClock.advanceTimeBy(1_000)
            // One reply the person reads, followed by its own text (the composed set may differ by an off-screen row).
            val read =
                onAllNodesWithText("Reply number", substring = true)
                    .fetchSemanticsNodes()
                    .first { it.boundsInRoot.top >= 0f && it.boundsInRoot.bottom <= 820f * density.density }
                    .config[SemanticsProperties.Text]
                    .first()
                    .text
            val before = onNodeWithText(read).getBoundsInRoot()
            runOnUiThread { dock.offered = true }
            mainClock.advanceTimeBy(1_000)
            val during = onNodeWithText(read).getBoundsInRoot()
            assertEquals(before.top, during.top, "what the person reads moved when the bar came: $before then $during")
            runOnUiThread { dock.offered = false }
            mainClock.advanceTimeBy(1_000)
            val after = onNodeWithText(read).getBoundsInRoot()
            assertEquals(before.top, after.top, "what the person reads moved when the bar went: $before then $after")
        }
}
