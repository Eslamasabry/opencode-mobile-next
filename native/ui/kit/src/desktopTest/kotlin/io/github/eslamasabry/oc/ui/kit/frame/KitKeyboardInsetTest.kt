@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitBottomInset
import kotlin.test.Test
import kotlin.test.assertEquals

/** Screens read the keyboard through the kit; a floating part keeps the larger of navigation bar and keyboard. */
class KitKeyboardInsetTest {
    private val nav = 24.dp

    private fun source(keyboard: androidx.compose.ui.unit.Dp) =
        KitKeyboardSource(WindowInsets(bottom = keyboard), WindowInsets(bottom = keyboard))

    @Test
    fun `a floating part's clearance is the larger of the navigation bar and the keyboard, not their sum`() = runComposeUiTest {
        var keyboard by mutableStateOf(0.dp)
        var bottom = -1.dp
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en") {
                CompositionLocalProvider(LocalKitKeyboardSource provides source(keyboard)) {
                    bottom = kitBottomInset(WindowInsets(bottom = nav)).bottom
                }
            }
        }
        assertEquals(nav, bottom, "no keyboard: the navigation bar")
        keyboard = 300.dp
        waitForIdle()
        assertEquals(300.dp, bottom, "keyboard up over the navigation bar: the keyboard's room alone")
        keyboard = 10.dp
        waitForIdle()
        assertEquals(nav, bottom, "a keyboard lower than the bar: the bar")
    }

    @Test
    fun `a screen padded by kitImePadding rises with the keyboard and the kit reads its height`() = runComposeUiTest {
        var keyboard by mutableStateOf(0.dp)
        var height = -1.dp
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en") {
                CompositionLocalProvider(LocalKitKeyboardSource provides source(keyboard)) {
                    height = kitKeyboardHeight()
                    Box(Modifier.size(300.dp, 600.dp).kitImePadding(), contentAlignment = Alignment.BottomStart) {
                        Box(Modifier.testTag("foot").fillMaxWidth().height(20.dp))
                    }
                }
            }
        }
        assertEquals(600.dp, onNodeWithTag("foot").getBoundsInRoot().bottom)
        assertEquals(0.dp, height)
        keyboard = 250.dp
        waitForIdle()
        assertEquals(350.dp, onNodeWithTag("foot").getBoundsInRoot().bottom)
        assertEquals(250.dp, height)
    }
}
