@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test

/**
 * B-KIT-MODAL-206: a sheet that comes back with a rebuilt screen is drawn in that screen's first frame. A rotation
 * rebuilds the page around the sheet it had open (the open state is saved with the screen), and the sheet must
 * register inside the composition that rebuilds the page — one frame later and the page shows through where the
 * sheet sits (the message box's hint under it). The rebuild here is the one the conversation's own test performs:
 * the screen subtree is composed fresh with its sheet already open.
 */
class KitSheetFirstFrameTest {
    @Test
    fun `B-KIT-MODAL-206 a sheet that comes back with a rebuilt screen is drawn in that screen's first frame`() =
        runDesktopComposeUiTest(412, 915) {
            var placed by mutableStateOf(false)
            var sheetOpen by mutableStateOf(false)
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en", stillFrame = true) {
                    InWindow(412, 915) {
                        if (placed) {
                            KitSheetHost(Modifier.fillMaxSize()) {
                                KitText("the page under the sheet")
                                KitSheet(
                                    visible = sheetOpen,
                                    onDismissRequest = {},
                                    title = "Commands",
                                ) {
                                    KitText("the sheet's body")
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            // The clock stops, the screen comes back with its sheet open, and exactly one frame is drawn.
            mainClock.autoAdvance = false
            runOnUiThread {
                sheetOpen = true
                placed = true
            }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("kit-sheet")
                .assertExists("the sheet that came back with the rebuilt screen is drawn in its first frame")
            mainClock.autoAdvance = true
            waitForIdle()
            onNodeWithTag("kit-sheet").assertExists("the sheet that came back is still shown once settled")
        }
}
