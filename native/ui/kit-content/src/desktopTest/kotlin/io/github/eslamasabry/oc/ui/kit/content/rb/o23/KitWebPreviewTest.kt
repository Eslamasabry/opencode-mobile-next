package io.github.eslamasabry.oc.ui.kit.content.rb.o23

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitWebPreview
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitWebPreviewPhase
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** lib/ui/kit/kit_web_preview.dart at 8302de537: the failed answer covers the page only while the phase is failed. */
@OptIn(ExperimentalTestApi::class)
class KitWebPreviewTest {
    @Test
    fun `B-KIT-240 a failed page says nothing is answering and Try again reloads`() = runComposeUiTest {
        val phase = mutableStateOf(KitWebPreviewPhase.Loading)
        var retries = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                KitWebPreview(phase.value, port = 5173, onRetry = { retries++ })
            }
        }
        onNodeWithText("Nothing is answering yet").assertDoesNotExist()
        phase.value = KitWebPreviewPhase.Failed
        waitForIdle()
        onNodeWithText("Nothing is answering yet").assertExists()
        onNodeWithText("Port 5173 is not responding. The app may still be starting.").assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
        phase.value = KitWebPreviewPhase.Ready
        waitForIdle()
        onNodeWithText("Nothing is answering yet").assertDoesNotExist()
    }

    @Test
    fun `B-KIT-241 without a web view the preview explains itself in plain words`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                KitWebPreview(KitWebPreviewPhase.Loading, port = 5173, onRetry = {}, supported = false)
            }
        }
        onNodeWithText("Preview isn't available here").assertExists()
    }
}
