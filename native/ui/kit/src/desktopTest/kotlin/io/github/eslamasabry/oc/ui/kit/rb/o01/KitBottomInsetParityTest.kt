package io.github.eslamasabry.oc.ui.kit.rb.o01

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitBottomInset
import io.github.eslamasabry.oc.ui.kit.foundation.KitBottomInsetAdd
import io.github.eslamasabry.oc.ui.kit.foundation.KitClearance
import io.github.eslamasabry.oc.ui.kit.foundation.kitBottomInset
import kotlin.test.Test
import kotlin.test.assertEquals

/** kit_bottom_inset.dart: the published clearance wins, `add` stacks on the parent and keeps `start` unless given. */
@OptIn(ExperimentalTestApi::class)
class KitBottomInsetParityTest {
    @Test fun noPublisherIsZeroOnDesktop() = runComposeUiTest {
        var seen = KitClearance(1.dp, 1.dp)
        // With no publisher the inset is the window's own (navigation bar, keyboard), read through the kit's one
        // keyboard value, which lives in the theme as every real page does.
        setContent { KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en") { seen = kitBottomInset() } }
        assertEquals(KitClearance(), seen)
    }

    @Test fun publishedInsetsWin() = runComposeUiTest {
        var seen = KitClearance()
        setContent { KitBottomInset(KitClearance(bottom = 80.dp, start = 72.dp)) { seen = kitBottomInset() } }
        assertEquals(KitClearance(bottom = 80.dp, start = 72.dp), seen)
    }

    @Test fun addStacksOnTheParentAndKeepsStart() = runComposeUiTest {
        var seen = KitClearance()
        setContent {
            KitBottomInset(KitClearance(bottom = 80.dp, start = 72.dp)) {
                KitBottomInsetAdd(extraBottom = 20.dp) { seen = kitBottomInset() }
            }
        }
        assertEquals(KitClearance(bottom = 100.dp, start = 72.dp), seen)
    }

    @Test fun addTakesANewStart() = runComposeUiTest {
        var seen = KitClearance()
        setContent {
            KitBottomInset(KitClearance(bottom = 80.dp, start = 72.dp)) {
                KitBottomInsetAdd(extraBottom = 0.dp, start = 16.dp) { seen = kitBottomInset() }
            }
        }
        assertEquals(KitClearance(bottom = 80.dp, start = 16.dp), seen)
    }
}
