package io.github.eslamasabry.oc.feature.chatcomposer.rb.o43

import androidx.compose.foundation.text.input.TextFieldState
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
    @Test fun multilinePromptOffersTheEditor() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { ComposerScreen(ComposerUi(button = FieldButton.Send), RecordingActions(), TextFieldState("First line\nSecond line")) } }
        onNodeWithTag("composer-open-editor").assertIsDisplayed().performClick()
        onNodeWithText("Prompt editor").assertIsDisplayed()
    }
}
