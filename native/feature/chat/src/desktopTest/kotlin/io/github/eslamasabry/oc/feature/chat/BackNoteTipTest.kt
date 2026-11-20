package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.ui.BackNoteTip
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** B-LAMP-013: the "Back returns to" tip never draws a placeholder where the title was, as it arrives or leaves. */
@OptIn(ExperimentalTestApi::class)
class BackNoteTipTest {
    @Test
    fun `B-LAMP-013 the back tip keeps its title while it fades out and never says a dot`() =
        runComposeUiTest {
            var title by mutableStateOf<String?>("Deploy checks")
            mainClock.autoAdvance = false
            setContent {
                KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Full), localeTag = "en") {
                    BackNoteTip(title, { title = null }) { Box(Modifier.size(200.dp, 56.dp)) }
                }
            }
            mainClock.advanceTimeBy(400)
            fun count(words: String) = onAllNodesWithText(words, substring = true, useUnmergedTree = true).fetchSemanticsNodes().size
            assertEquals(1, count("Back returns to Deploy checks"), "the tip names where Back goes")
            title = null
            repeat(30) {
                mainClock.advanceTimeByFrame()
                assertEquals(0, count("Back returns to ·"), "frame $it: a placeholder where the title was")
                assertEquals(0, count("Back returns to ,"), "frame $it: a cut sentence")
            }
        }
}
