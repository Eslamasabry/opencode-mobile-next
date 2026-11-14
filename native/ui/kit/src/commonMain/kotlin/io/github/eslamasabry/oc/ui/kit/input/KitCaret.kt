package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible

/** When a kit field shows its caret (audit 2026-11-14, k6 and h1). */
public object KitCaretRules {
    /**
     * A caret says "what you type lands here". On a touch screen with the keyboard down nothing can be typed (the field
     * keeps its focus after the keyboard is put away, and while "Waiting for your decision above…" no keyboard is up),
     * so no caret blinks; a hardware keyboard ([touch] false) types into a focused field, so its caret stays.
     */
    public fun shows(touch: Boolean, keyboardUp: Boolean): Boolean = !touch || keyboardUp
}

/** The caret brush of a kit field in [color]: clear while [KitCaretRules.shows] says no caret. */
@Composable
internal fun kitCaret(color: Color): Brush {
    val touch = LocalInputModeManager.current.inputMode == InputMode.Touch
    val up = kitKeyboardVisible()
    return SolidColor(if (KitCaretRules.shows(touch, up)) color else Color.Transparent)
}
