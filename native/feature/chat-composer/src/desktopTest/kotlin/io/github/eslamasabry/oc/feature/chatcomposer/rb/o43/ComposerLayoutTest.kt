package io.github.eslamasabry.oc.feature.chatcomposer.rb.o43

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import kotlin.test.assertEquals
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chatcomposer.RecordingActions
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.*
import io.github.eslamasabry.oc.feature.chatcomposer.ui.ComposerScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ComposerLayoutTest {
    @Test fun enterCannotDispatchWhileSending() = runComposeUiTest {
        val actions = RecordingActions()
        val field = TextFieldState("Hello")
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { ComposerScreen(ComposerUi(button = FieldButton.Sending), actions, field, finePointer = true) } }
        onNode(hasSetTextAction()).performClick().performKeyInput { pressKey(Key.Enter) }
        assertEquals(0, actions.calls.count { it == "send" })
    }

    @Test fun shortPromptAndSendShareOneRow() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { ComposerScreen(ComposerUi(button = FieldButton.Send), RecordingActions(), TextFieldState("Hello")) } }
        val field = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val send = onNodeWithContentDescription("Send").fetchSemanticsNode().boundsInRoot
        assertTrue(send.center.y in field.top..field.bottom)
        onNodeWithTag("composer-open-editor").assertDoesNotExist()
    }
    @Test fun `issue 148 Send stays inside a 360 dp box at text 1_3 with a five line message`() = runComposeUiTest {
        setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 1.3f)
            ) {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.width(androidx.compose.ui.unit.Dp(360f)).testTag("narrow")
                    ) {
                        ComposerScreen(
                            ComposerUi(button = FieldButton.Send),
                            RecordingActions(),
                            TextFieldState("one\ntwo\nthree\nfour\nfive"),
                        )
                    }
                }
            }
        }
        val box = onNodeWithTag("narrow").fetchSemanticsNode().boundsInRoot
        val send = onNodeWithContentDescription("Send").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(send.left >= box.left && send.right <= box.right && send.width > 0f, "Send is inside the 360 dp box: $send in $box")
    }
    @Test fun multilinePromptOffersTheEditor() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { ComposerScreen(ComposerUi(button = FieldButton.Send), RecordingActions(), TextFieldState("First line\nSecond line")) } }
        onNodeWithTag("composer-open-editor").assertIsDisplayed().performClick()
        onNodeWithText("Prompt editor").assertIsDisplayed()
    }
    @Test fun `C19 delivery notes stay inside the floating field surface`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                ComposerScreen(ComposerUi(note = ComposerNote.ReadOnly(ReadOnlyReason.SubAgent)), RecordingActions(), TextFieldState())
            }
        }
        onNode(hasText("This sub-agent answers only its main conversation. Write there.") and hasAnyAncestor(hasTestTag("kit-composer-box")), useUnmergedTree = true).assertExists()
    }
    @Test fun `P09-10_2 a read-only composer says why once`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                ComposerScreen(
                    ComposerUi(
                        button = FieldButton.None,
                        note = ComposerNote.ReadOnly(ReadOnlyReason.SubAgent),
                        fieldEnabled = false,
                    ),
                    RecordingActions(),
                    TextFieldState(),
                )
            }
        }
        val why = "This sub-agent answers only its main conversation. Write there."
        // The disabled field draws its reason under the words and speaks it as its state (KitField); a note
        // above the field with the same words would say it a second time.
        onNode(hasStateDescription(why)).assertIsNotEnabled()
        onAllNodesWithText(why, useUnmergedTree = true).assertCountEquals(0)
    }

}
