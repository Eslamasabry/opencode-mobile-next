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

    private fun run(pinned: Boolean, keyboardUp: Boolean, check: (field: androidx.compose.ui.unit.DpRect, button: androidx.compose.ui.unit.DpRect?, sheet: androidx.compose.ui.unit.DpRect) -> Unit) =
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
                                        Spacer(Modifier.height(1500.dp))
                                        KitField(
                                            remember { TextFieldState() },
                                            "Tell the agent why",
                                            Modifier.testTag("why"),
                                            kind = KitFieldKind.Multiline,
                                            // One line to start: the caret line is the whole field, as typing goes on.
                                            multilineLines = 1..8,
                                            focusRequester = focus,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            runOnIdle { focus.requestFocus() }
            waitForIdle()
            check(
                onNodeWithTag("why").getBoundsInRoot(),
                if (pinned) onNodeWithText("Allow").getBoundsInRoot() else null,
                onNodeWithTag("kit-sheet").getBoundsInRoot(),
            )
        }

    @Test
    fun `a focused field at the end of a body with pinned buttons rests a fade's reach above them`() =
        run(pinned = true, keyboardUp = false) { field, button, _ ->
            assertTrue(field.bottom <= button!!.top - fade, "field bottom ${field.bottom} must be at least $fade above the buttons ${button.top}")
        }

    @Test
    fun `the same with the keyboard up under the sheet`() =
        run(pinned = true, keyboardUp = true) { field, button, _ ->
            assertTrue(field.bottom <= button!!.top - fade, "field bottom ${field.bottom} must be at least $fade above the buttons ${button.top}")
        }

    @Test
    fun `without pinned buttons the focused field only reaches the sheet's edge`() =
        run(pinned = false, keyboardUp = false) { field, _, sheet ->
            assertTrue(field.bottom <= sheet.bottom, "field bottom ${field.bottom} inside the sheet ${sheet.bottom}")
        }

    // Probes while the first CI run gave no numbers: how far above the buttons the field rests.
    @Test fun `probe gap 0`() = run(true, false) { f, b, _ -> assertTrue(f.bottom <= b!!.top) }
    @Test fun `probe gap 8`() = run(true, false) { f, b, _ -> assertTrue(f.bottom <= b!!.top - 8.dp) }
    @Test fun `probe gap 16`() = run(true, false) { f, b, _ -> assertTrue(f.bottom <= b!!.top - 16.dp) }
    @Test fun `probe gap 32`() = run(true, false) { f, b, _ -> assertTrue(f.bottom <= b!!.top - 32.dp) }
    @Test fun `probe field is in the sheet`() = run(true, false) { f, _, sh -> assertTrue(f.bottom <= sh.bottom && f.top >= sh.top) }
    @Test fun `probe field is under the header`() = run(true, false) { f, _, sh -> assertTrue(f.top >= sh.top + 60.dp) }
}
