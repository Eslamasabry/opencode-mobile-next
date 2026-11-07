package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.state.KitWindowRow
import io.github.eslamasabry.oc.ui.kit.state.KitWindowTone
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class KitWindowRowTest {
    @Test
    fun `KitWindowRow reads title, spoken value and note as one label in every tone`() = runComposeUiTest {
        setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), stillFrame = true) {
                KitWindowRow("Week", .18f, "18% · Thu", "18% left, resets Thursday", note = "Resets at Wed 1:59 AM", tone = KitWindowTone.Low)
            }
        }
        onNodeWithContentDescription("Week, 18% left, resets Thursday, Resets at Wed 1:59 AM").assertExists()
    }
}
