package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.motion.KitPresence
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

/** lib/ui/kit/kit_presence.dart at 8302de537: once gone it builds nothing; reduced motion changes at once; both axes. */
@OptIn(ExperimentalTestApi::class)
class KitPresenceTest {
    @Test
    fun `B-KIT-260 a hidden presence is not in the tree and comes back at once under motion Off`() = runComposeUiTest {
        val show = mutableStateOf(true)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                KitPresence(show.value) { KitText("Stop") }
            }
        }
        onNodeWithText("Stop").assertExists()
        show.value = false
        waitForIdle()
        onNodeWithText("Stop").assertDoesNotExist()
        show.value = true
        waitForIdle()
        onNodeWithText("Stop").assertExists()
    }

    @Test
    fun `B-KIT-261 a vertical presence leaves the tree once its slot has closed`() = runComposeUiTest {
        val show = mutableStateOf(true)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(), stillFrame = true) {
                KitPresence(show.value, Orientation.Vertical) { KitText("Queued") }
            }
        }
        onNodeWithText("Queued").assertExists()
        show.value = false
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        onNodeWithText("Queued").assertDoesNotExist()
    }
}
