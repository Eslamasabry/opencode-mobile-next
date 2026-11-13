@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.LocalSheetKeyboard
import kotlin.test.Test
import kotlin.test.assertTrue

/** A field at the end of a sheet's tall body comes to rest above the fade over the pinned buttons, keyboard or not. */
class KitSheetFieldIntoViewTest {
    private val fade = 24.dp

    private fun run(pinned: Boolean, keyboardUp: Boolean, middle: Boolean = false, typed: String? = null, check: (field: androidx.compose.ui.unit.DpRect, button: androidx.compose.ui.unit.DpRect?, sheet: androidx.compose.ui.unit.DpRect) -> Unit) =
        runComposeUiTest {
            var keyboard by mutableStateOf(if (keyboardUp) 200.dp else 0.dp)
            val focus = FocusRequester()
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalSheetKeyboard provides keyboard) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        InWindow(400, 700) {
                            KitSheetHost {
                                KitSheet(
                                    visible = true,
                                    onDismissRequest = {},
                                    title = "Details",
                                    primary = if (pinned) KitAction("Allow", onClick = {}) else null,
                                    secondary = if (pinned) KitAction("Deny", onClick = {}) else null,
                                ) {
                                    Column {
                                        Spacer(Modifier.height(if (middle) 400.dp else 1500.dp))
                                        KitField(
                                            remember { TextFieldState() },
                                            "Tell the agent why",
                                            Modifier.testTag("why"),
                                            kind = KitFieldKind.Multiline,
                                            // One line to start: the caret line is the whole field, as typing goes on.
                                            multilineLines = 1..8,
                                            focusRequester = focus,
                                        )
                                        if (middle) Spacer(Modifier.height(1500.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            runOnIdle { focus.requestFocus() }
            waitForIdle()
            if (typed != null) {
                onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput(typed)
                waitForIdle()
            }
            check(
                onNodeWithTag("why").getBoundsInRoot(),
                if (pinned) onNodeWithText("Allow").getBoundsInRoot() else null,
                onNodeWithTag("kit-sheet").getBoundsInRoot(),
            )
        }

    @Test
    fun `at the end of the body the focused field is whole, above the pinned buttons`() =
        run(pinned = true, keyboardUp = false) { field, button, _ ->
            assertTrue(field.bottom <= button!!.top, "field bottom ${field.bottom} is at or above the buttons ${button.top}")
        }

    @Test
    fun `a focused field in the middle of a tall body rests a fade's reach above the buttons with the keyboard up`() =
        run(pinned = true, keyboardUp = true, middle = true) { field, button, _ ->
            assertTrue(field.bottom <= button!!.top - fade, "field bottom ${field.bottom} at least $fade above ${button.top}")
        }

    @Test
    fun `typing new lines in a field in the middle keeps it a fade's reach above the buttons`() =
        run(pinned = true, keyboardUp = true, middle = true, typed = "one\ntwo\nthree\nfour") { field, button, _ ->
            assertTrue(field.bottom <= button!!.top - fade, "field bottom ${field.bottom} at least $fade above ${button.top}")
        }

    @Test
    fun `without pinned buttons the focused field only reaches the sheet's edge`() =
        run(pinned = false, keyboardUp = false) { field, _, sheet ->
            assertTrue(field.bottom <= sheet.bottom, "field bottom ${field.bottom} inside the sheet ${sheet.bottom}")
        }
}
