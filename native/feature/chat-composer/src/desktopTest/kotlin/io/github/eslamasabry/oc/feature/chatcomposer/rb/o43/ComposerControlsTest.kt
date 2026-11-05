package io.github.eslamasabry.oc.feature.chatcomposer.rb.o43

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.chatcomposer.RecordingActions
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerUi
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButton
import io.github.eslamasabry.oc.feature.chatcomposer.ui.ComposerScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
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
    @Test fun `B-KIT-011 sending keeps the circular control disabled with a still working mark`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                ComposerScreen(ComposerUi(button = FieldButton.Sending), actions, TextFieldState("Next task"))
            }
        }
        onNodeWithContentDescription("Sending").assertIsNotEnabled()
        onNodeWithTag("kit-composer-send-working", useUnmergedTree = true).assertExists()
        val disc = onNodeWithTag("kit-composer-send-disc", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(40f, disc.width)
        assertEquals(disc.width, disc.height)
        assertEquals(0, actions.calls.count { it == "send" })
    }

    @Test fun `B-KIT-011 offline send retains its existing intent in the circular control`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                ComposerScreen(ComposerUi(button = FieldButton.SendWhenOnline), actions, TextFieldState("Next task"))
            }
        }
        onNodeWithTag("kit-composer-send-disc", useUnmergedTree = true).assertExists()
        onNode(hasContentDescription("Send when back online")).performClick()
        assertEquals(1, actions.calls.count { it == "send" })
    }

}
