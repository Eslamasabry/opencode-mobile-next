package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A tap on a field's label puts the cursor in the field and opens the keyboard, as a form's label does everywhere
 * (emulator walk 2026-11-10: a tap on "Address" on the add-server page did nothing). A disabled field's label does
 * nothing. The label adds no control of its own: the field already carries its name.
 */
@OptIn(ExperimentalTestApi::class)
class KitFieldLabelTapTest {
    private class Keyboard : SoftwareKeyboardController {
        var shows = 0

        override fun show() {
            shows++
        }

        override fun hide() {}
    }

    @Test
    fun `a tap on the label focuses its field and asks for the keyboard`() = runComposeUiTest {
        val keyboard = Keyboard()
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    KitField(state = TextFieldState(), label = "Address")
                }
            }
        }
        val field = onNode(hasSetTextAction())
        field.assertIsNotFocused()
        onNodeWithTag("kit-field-label", useUnmergedTree = true).performTouchInput { click() }
        field.assertIsFocused()
        runOnIdle { assertTrue(keyboard.shows > 0, "the keyboard is asked for") }
    }

    @Test
    fun `a disabled field's label does nothing`() = runComposeUiTest {
        val keyboard = Keyboard()
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    KitField(state = TextFieldState(), label = "Address", enabled = KitEnabled.Disabled("Connect first"))
                }
            }
        }
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasTestTag("kit-field-label"), useUnmergedTree = true).fetchSemanticsNodes().size)
        runOnIdle { assertEquals(0, keyboard.shows) }
    }
}
