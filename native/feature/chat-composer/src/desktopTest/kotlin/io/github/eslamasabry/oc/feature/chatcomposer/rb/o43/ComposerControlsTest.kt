package io.github.eslamasabry.oc.feature.chatcomposer.rb.o43

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chatcomposer.RecordingActions
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerUi
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButton
import io.github.eslamasabry.oc.feature.chatcomposer.ui.ComposerScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ComposerControlsTest {
    @Test fun `C19 fallback microphone keeps a full touch target and calls the voice action`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent { KitTheme(platform = DesktopKitPlatform()) { ComposerScreen(ComposerUi(), actions, TextFieldState()) } }
        val mic = onNodeWithContentDescription("Talk instead of typing")
        val target = mic.fetchSemanticsNode().boundsInRoot
        val disc = onNodeWithTag("kit-composer-mic-disc", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(target.width >= 48 && target.height >= 48)
        assertTrue(disc.width < target.width && disc.height < target.height)
        mic.performClick()
        assertEquals(1, actions.calls.count { it == "mic" })
    }

    @Test fun `C19 stop and send uses the explicit kit pill and existing send intent`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent { KitTheme(platform = DesktopKitPlatform()) { ComposerScreen(ComposerUi(button = FieldButton.StopAndSend), actions, TextFieldState("Next task")) } }
        onNodeWithTag("kit-stop-and-send").assertIsDisplayed().performClick()
        assertEquals(1, actions.calls.count { it == "send" })
    }
}
