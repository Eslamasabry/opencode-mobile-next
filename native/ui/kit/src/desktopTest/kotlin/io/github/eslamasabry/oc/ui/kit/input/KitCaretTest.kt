@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitKeyboardShown
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Audit 2026-11-14 (k6, h1): the message box kept blinking its caret after the keyboard had closed, and while it said
 * "Waiting for your decision above…" with no keyboard up. A caret shows only where typing can reach the field.
 */
class KitCaretTest {
    private class Mode(override val inputMode: InputMode) : InputModeManager {
        override fun requestInputMode(inputMode: InputMode): Boolean = false
    }

    @Test
    fun `B-KIT-CARET-01 the caret shows with a hardware keyboard or the screen keyboard up, never on a touch screen with it down`() {
        assertTrue(KitCaretRules.shows(touch = false, keyboardUp = false))
        assertTrue(KitCaretRules.shows(touch = true, keyboardUp = true))
        assertFalse(KitCaretRules.shows(touch = true, keyboardUp = false))
    }

    @Test
    fun `B-KIT-CARET-02 a focused message box on a touch screen draws no caret while the keyboard is down`() {
        val up = caretPixels(keyboardUp = true)
        val down = caretPixels(keyboardUp = false)
        assertTrue(up > 0, "keyboard up: the caret shows ($up accent pixels)")
        assertEquals(0, down, "keyboard down on a touch screen: no caret ($down accent pixels)")
    }

    /** The most accent pixels the focused, empty field draws over the caret's blink. */
    private fun caretPixels(keyboardUp: Boolean): Int {
        var most = 0
        runComposeUiTest {
            var accent = Color.Unspecified
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                    accent = LocalKitColors.current.accent
                    CompositionLocalProvider(
                        LocalInputModeManager provides remember { Mode(InputMode.Touch) },
                        LocalKitKeyboardShown provides keyboardUp,
                    ) {
                        val focus = remember { FocusRequester() }
                        Box(Modifier.size(320.dp, 64.dp)) {
                            KitField(
                                state = remember { TextFieldState() },
                                label = "Message",
                                kind = KitFieldKind.Multiline,
                                labelShown = false,
                                framed = false,
                                composer = true,
                                multilineLines = 1..5,
                                focusRequester = focus,
                            )
                        }
                        LaunchedEffect(Unit) { focus.requestFocus() }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            repeat(BLINK_SAMPLES) {
                most = maxOf(most, accentPixels(accent))
                mainClock.advanceTimeBy(BLINK_STEP_MS)
            }
        }
        return most
    }

    private fun ComposeUiTest.accentPixels(accent: Color): Int {
        val map = onRoot().captureToImage().toPixelMap()
        var n = 0
        for (y in 0 until map.height) for (x in 0 until map.width) {
            val c = map[x, y]
            if (abs(c.red - accent.red) < .06f && abs(c.green - accent.green) < .06f && abs(c.blue - accent.blue) < .06f && c.alpha > .5f) n++
        }
        return n
    }

    private companion object {
        const val BLINK_SAMPLES = 8
        const val BLINK_STEP_MS = 125L
    }
}
