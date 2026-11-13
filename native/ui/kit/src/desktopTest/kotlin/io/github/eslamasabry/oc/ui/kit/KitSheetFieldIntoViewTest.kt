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
import io.github.eslamasabry.oc.ui.design.style.Style
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

    private fun run(pinned: Boolean, keyboardUp: Boolean, middle: Boolean = false, typed: String? = null, lines: IntRange = 1..8, style: Style = Style.Solid, windowHeight: Int = 700, answerPair: Boolean = false, check: (field: androidx.compose.ui.unit.DpRect, button: androidx.compose.ui.unit.DpRect?, sheet: androidx.compose.ui.unit.DpRect) -> Unit) =
        runComposeUiTest {
            var keyboard by mutableStateOf(if (keyboardUp) 200.dp else 0.dp)
            val focus = FocusRequester()
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f), LocalSheetKeyboard provides keyboard) {
                    KitTheme(
                        KitAppearance(style = style, motion = MotionLevel.Off),
                        if (style == Style.Glass) FakeKitPlatform(frost = true, liquidGlass = true) else FakeKitPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        InWindow(400, windowHeight) {
                            KitSheetHost {
                                KitSheet(
                                    visible = true,
                                    onDismissRequest = {},
                                    title = "Details",
                                    primary = if (pinned) KitAction("Allow", onClick = {}) else null,
                                    secondary = if (pinned) KitAction("Deny", onClick = {}) else null,
                                    answerPair = answerPair,
                                ) {
                                    Column {
                                        Spacer(Modifier.height(if (middle) 400.dp else 1500.dp))
                                        KitField(
                                            remember { TextFieldState() },
                                            "Tell the agent why",
                                            Modifier.testTag("why"),
                                            kind = KitFieldKind.Multiline,
                                            // One line to start: the caret line is the whole field, as typing goes on.
                                            multilineLines = lines,
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
            // The caret's line ends one inner padding (under 16 dp) above the field's own bottom edge.
            assertTrue(field.bottom - 16.dp <= button!!.top - fade, "field bottom ${field.bottom} less its padding at least $fade above ${button.top}")
        }

    @Test
    fun `without pinned buttons the focused field only reaches the sheet's edge`() =
        run(pinned = false, keyboardUp = false) { field, _, sheet ->
            assertTrue(field.bottom <= sheet.bottom, "field bottom ${field.bottom} inside the sheet ${sheet.bottom}")
        }

    @Test
    fun `a three-line field at the end of a tall body is whole above the buttons after one character, keyboard up`() =
        run(pinned = true, keyboardUp = true, typed = "a", lines = 3..8) { field, button, sheet ->
            assertTrue(field.bottom <= button!!.top, "field bottom ${field.bottom} at or above the buttons ${button.top}")
            assertTrue(field.top >= sheet.top, "the whole box is in the sheet")
        }

    @Test
    fun `a three-line field in the middle of a tall body is whole and a fade's reach above the buttons`() =
        run(pinned = true, keyboardUp = true, middle = true, typed = "a", lines = 3..8) { field, button, sheet ->
            assertTrue(field.bottom <= button!!.top - fade, "field bottom ${field.bottom} a fade above ${button.top}")
            assertTrue(field.top >= sheet.top, "the whole box is in the sheet")
        }

    @Test
    fun `a field taller than the viewport keeps its caret line clear of the fade`() =
        run(pinned = true, keyboardUp = true, middle = true, typed = "a", lines = 24..24) { field, button, _ ->
            assertTrue(field.top + 40.dp <= button!!.top - fade, "the first line (caret) ends above the fade: top ${field.top}, buttons ${button.top}")
        }

    // Review item 7 (the permission note cut open by the pinned Deny / Allow): a small window with the keyboard up, the
    // answers side by side as the permission sheet lays them, under Soft and Glass (Glass keeps its pane inset).
    private fun noteWhole(style: Style, middle: Boolean) =
        run(pinned = true, keyboardUp = true, middle = middle, typed = "a", lines = 3..8, style = style, windowHeight = 520, answerPair = true) { field, button, sheet ->
            val room = if (middle) fade else 0.dp
            assertTrue(field.bottom <= button!!.top - room, "$style: field bottom ${field.bottom}, buttons ${button.top}, fade $room")
            assertTrue(field.top >= sheet.top, "$style: the whole field is in the sheet: field ${field.top}, sheet ${sheet.top}")
        }

    @Test
    fun `R7 under Soft the permission note at the end of the body is whole above Deny and Allow, small window, keyboard up`() =
        noteWhole(Style.Soft, middle = false)

    @Test
    fun `R7 under Soft a note in the middle of the body rests a fade above Deny and Allow, small window, keyboard up`() =
        noteWhole(Style.Soft, middle = true)

    @Test
    fun `R7 under Glass the permission note at the end of the body is whole above Deny and Allow, small window, keyboard up`() =
        noteWhole(Style.Glass, middle = false)

    @Test
    fun `R7 under Glass a note in the middle of the body rests a fade above Deny and Allow, small window, keyboard up`() =
        noteWhole(Style.Glass, middle = true)
}
