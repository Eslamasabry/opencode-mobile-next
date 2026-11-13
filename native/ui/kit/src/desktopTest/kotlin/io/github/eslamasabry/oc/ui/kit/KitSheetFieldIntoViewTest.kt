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

    private fun run(pinned: Boolean, check: (field: androidx.compose.ui.unit.DpRect, button: androidx.compose.ui.unit.DpRect?, sheet: androidx.compose.ui.unit.DpRect) -> Unit) =
        runComposeUiTest {
            var keyboard by mutableStateOf(0.dp)
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
                                        Spacer(Modifier.height(600.dp))
                                        KitField(
                                            remember { TextFieldState() },
                                            "Tell the agent why",
                                            Modifier.testTag("why"),
                                            kind = KitFieldKind.Multiline,
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
            fun measure() =
                check(
                    onNodeWithTag("why").getBoundsInRoot(),
                    if (pinned) onNodeWithText("Allow").getBoundsInRoot() else null,
                    onNodeWithTag("kit-sheet").getBoundsInRoot(),
                )
            measure()
            // The keyboard comes up under the sheet: the body is shorter, the field still rests above the fade.
            runOnIdle { keyboard = 300.dp }
            waitForIdle()
            measure()
        }

    @Test
    fun `a focused field at the end of a body with pinned buttons rests a fade's reach above them, keyboard up or not`() =
        run(pinned = true) { field, button, _ ->
            assertTrue(field.bottom <= button!!.top - fade, "field bottom ${field.bottom} must be at least $fade above the buttons ${button.top}")
        }

    @Test
    fun `without pinned buttons the focused field only reaches the sheet's edge`() =
        run(pinned = false) { field, _, sheet ->
            assertTrue(field.bottom <= sheet.bottom, "field bottom ${field.bottom} inside the sheet ${sheet.bottom}")
        }
}
