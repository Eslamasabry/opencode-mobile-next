package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * Review #8 (C-023, C-017): with the keyboard closed the message box sat where the keyboard had held it, over about
 * 850 px of empty screen. The rule the chat relies on: the bottom block rests on the page's bottom inset as it is NOW
 * (the keyboard's while it is up, the navigation bar's once it is gone), in the same frame the inset changes, with
 * nothing in the chat keeping the keyboard's height. A desktop test has no keyboard; the inset the page frame reads is
 * given here (LocalKitScreenInsets) and changed as the keyboard would change it.
 */
@OptIn(ExperimentalTestApi::class)
class R8ComposerFollowsKeyboardTest {
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    private val nav: Dp = 20.dp
    private val keyboard: Dp = 300.dp

    @Test
    fun `R8 the message box follows the bottom inset down when the keyboard goes, and keeps no keyboard room`() = runComposeUiTest {
        val bottom = mutableStateOf(keyboard)
        val state =
            ChatUi.loading(Ref).copy(title = "Native chat", phase = ChatPhase.Live, rows = persistentListOf(prompt("m1", "say hi")))
        val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true, localeTag = "en") {
                CompositionLocalProvider(
                    LocalKitRedactor provides Redactor(),
                    LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = bottom.value),
                ) {
                    ChatScreen(state, actions, composer, facts = io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi.Empty)
                }
            }
        }
        val rootBottom = onRoot().getBoundsInRoot().bottom
        val open = onNodeWithText("Composer slot", useUnmergedTree = true).getBoundsInRoot().bottom
        // Keyboard up: the box rests above it.
        assertTrue(open <= rootBottom - keyboard, "box bottom $open, keyboard top ${rootBottom - keyboard}")
        // The keyboard goes (Back, the system, a send): the inset drops to the navigation bar's.
        bottom.value = nav
        mainClock.advanceTimeByFrame()
        val closedOneFrame = onNodeWithText("Composer slot", useUnmergedTree = true).getBoundsInRoot().bottom
        waitForIdle()
        val closed = onNodeWithText("Composer slot", useUnmergedTree = true).getBoundsInRoot().bottom
        // It moved down by the keyboard's room less the navigation bar, at once (no frame holds the old height)...
        assertTrue(abs((closed - open).value - (keyboard - nav).value) < 1f, "moved ${closed - open}, expected ${keyboard - nav}")
        assertTrue(abs((closedOneFrame - closed).value) < 1f, "one frame after the inset changed the box was at $closedOneFrame, at rest $closed")
        // ...and rests on the navigation bar with only the frame's own small gap under it.
        val under = rootBottom - nav - closed
        assertTrue(under >= 0.dp && under < 48.dp, "room under the box with the keyboard gone: $under")
    }
}
