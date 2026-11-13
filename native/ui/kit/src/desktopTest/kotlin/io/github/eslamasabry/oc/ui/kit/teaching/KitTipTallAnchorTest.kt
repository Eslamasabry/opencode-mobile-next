@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.teaching

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tip never covers the controls of the thing it explains (device sweep: the terminal's tip lay on its key rows). An
 * anchor that fills the page holds its tip inside it, at its top edge: the bubble sits in the anchor's top third and
 * stays out of its bottom third.
 */
class KitTipTallAnchorTest {
    @Test
    fun `a tip on a page-filling anchor sits inside its top and never reaches its bottom third`() = runComposeUiTest {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                KitSheetHost {
                    KitTip("Hold the output to select text", visible = true, onDismissed = {}, modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().testTag("tall-anchor"))
                    }
                }
            }
        }
        waitForIdle()

        val anchor = onNodeWithTag("tall-anchor").fetchSemanticsNode().boundsInRoot
        val bubble = onNodeWithTag("kit-tip").fetchSemanticsNode().boundsInRoot
        val third = anchor.height / 3f
        assertTrue(bubble.top >= anchor.top, "bubble starts inside the anchor: $bubble in $anchor")
        assertTrue(bubble.bottom <= anchor.top + third, "bubble lies in the anchor's top third: $bubble in $anchor")
        assertTrue(bubble.bottom <= anchor.bottom - third, "bubble does not touch the anchor's bottom third: $bubble in $anchor")
    }
}
