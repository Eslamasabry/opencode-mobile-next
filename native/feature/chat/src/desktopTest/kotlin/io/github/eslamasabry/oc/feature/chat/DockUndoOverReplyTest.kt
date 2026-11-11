package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
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
import kotlinx.collections.immutable.persistentListOf

/**
 * Device run 5, problem 2, in the real chat screen: the dock's Undo bar (placed as the app's dock host places it: the
 * bottom of the dock's full-size box, lifted by the chat's bottom room) over the newest reply's More. A tap on Undo's
 * centre must run Undo and must not open the reply's menu.
 */
@OptIn(ExperimentalTestApi::class)
class DockUndoOverReplyTest {
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    /** The dock host's own arrangement (EdgeDockHost): a full-size box, the Undo bar at its bottom centre. */
    private class UndoDock(private val onUndo: () -> Unit) : ChatEdgeDock {
        @Composable
        override fun Content(chrome: PaddingValues) {
            Box(Modifier.fillMaxSize()) {
                KitUndoBar(
                    offer = KitUndoOffer("dock-removed-a", "Alpha left the dock"),
                    onUndo = onUndo,
                    onTimeout = {},
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = chrome.calculateBottomPadding()),
                )
            }
        }
    }

    @Test
    fun `B-DOCK-UNDO-02 Undo over the newest reply runs Undo, never the reply's menu`() = runComposeUiTest {
        val rows =
            persistentListOf(
                FooterRow("footer:m1", "m1", failed = false, stopped = true),
                block("m1", "m1", "p1", 0, "Hi! How can I help you today?"),
            )
        val state = ChatUi.loading(Ref).copy(title = "say hi", phase = ChatPhase.Live, rows = rows)
        val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        val menus = mutableListOf<String>()
        val actions =
            object : ChatActions by presenter {
                override fun openMessageMenu(rowKey: String) {
                    menus += rowKey
                }
            }
        var undone = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
                CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                    Box(Modifier.size(412.dp, 820.dp)) {
                        ChatScreen(state, actions, composer, edgeDock = UndoDock { undone++ })
                    }
                }
            }
        }
        waitForIdle()
        val undo = onNodeWithText("Undo").getBoundsInRoot()
        val more = onNodeWithContentDescription("More for this reply", useUnmergedTree = true).getBoundsInRoot()
        val overlap = undo.left < more.right && more.left < undo.right && undo.top < more.bottom && more.top < undo.bottom
        onNodeWithText("Undo").performTouchInput { click(center) }
        waitForIdle()
        assertEquals(emptyList(), menus, "the tap on Undo opened the reply's menu (Undo $undo, More $more, overlap $overlap)")
        assertEquals(1, undone, "Undo ran (Undo $undo, More $more, overlap $overlap)")
    }
}
