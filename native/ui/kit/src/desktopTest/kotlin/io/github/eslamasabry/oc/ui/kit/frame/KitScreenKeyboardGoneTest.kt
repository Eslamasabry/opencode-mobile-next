@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Review item 8 (frame side): the message box stood one keyboard high over an empty screen after the keyboard had
 * gone. The window's keyboard inset is the animated one; when its closing animation never ends in this window, it
 * keeps the keyboard's last height while the animation's target already says 0. The page frame must let its bottom
 * block down anyway, and must not cut a normal close short (the box follows the sliding keyboard for the system's own
 * hide animation first). Both page layouts are covered: the overlaid one (a chat) and the stacked one (a page with a
 * bottom block).
 */
class KitScreenKeyboardGoneTest {
    private val nav: Dp = 20.dp
    private val keyboard: Dp = 300.dp

    @Test
    fun `B-FRAME-008 the overlaid page lets its box down when the keyboard is gone but its animation never ended`() =
        stuckKeyboard(overlay = true)

    @Test
    fun `B-FRAME-008 the stacked page lets its box down when the keyboard is gone but its animation never ended`() =
        stuckKeyboard(overlay = false)

    private fun stuckKeyboard(overlay: Boolean) = runComposeUiTest {
        var now by mutableStateOf(keyboard)
        var target by mutableStateOf(keyboard)
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en") {
                CompositionLocalProvider(
                    LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = nav),
                    LocalKitKeyboardSource provides KitKeyboardSource(WindowInsets(bottom = now), WindowInsets(bottom = target)),
                ) {
                    Box(Modifier.size(412.dp, 900.dp)) {
                        KitScreen(page = true, overlayChrome = overlay, topBar = { KitText("Page title") }, bottom = { KitText("Message box") }) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        val rootBottom = onRoot().getBoundsInRoot().bottom
        val open = boxBottom()
        assertTrue(open <= rootBottom - keyboard, "keyboard up: box bottom $open, keyboard top ${rootBottom - keyboard}")

        // The keyboard goes; the animated inset never comes down (its end never reached this window).
        mainClock.autoAdvance = false
        target = 0.dp
        mainClock.advanceTimeBy(KEYBOARD_CLOSE_HOLD_MS / 4)
        val early = boxBottom()
        // A normal close is not cut short: for the system's own hide animation the box still follows the inset.
        assertTrue(abs((early - open).value) < 1f, "the box left the keyboard before its hide animation could end: $early vs $open")

        mainClock.advanceTimeBy(KEYBOARD_CLOSE_HOLD_MS + 2_000L)
        val closed = boxBottom()
        val under = rootBottom - nav - closed
        assertTrue(under >= 0.dp && under < 48.dp, "keyboard gone, inset stuck at $now: room under the box $under")

        // The keyboard comes back: the box rests above it again.
        mainClock.autoAdvance = true
        target = keyboard
        waitForIdle()
        assertTrue(boxBottom() <= rootBottom - keyboard, "keyboard up again: box bottom ${boxBottom()}")
    }

    private fun ComposeUiTest.boxBottom(): Dp = onNodeWithText("Message box", useUnmergedTree = true).getBoundsInRoot().bottom
}
