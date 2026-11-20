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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-LAMP-013: the "Back returns to" tip never draws a placeholder where the title was, as it arrives or leaves. */
@OptIn(ExperimentalTestApi::class)
class BackNoteTipTest {
    @Test
    fun `B-LAMP-013 the back tip is never drawn without its title, arriving or leaving`() =
        runComposeUiTest {
            var title by mutableStateOf<String?>(null)
            mainClock.autoAdvance = false
            setContent {
                val appearance = KitAppearance(motion = MotionLevel.Full)
                KitTheme(platform = DesktopKitPlatform(), appearance = appearance, localeTag = "en") {
                    BackNoteTip(title, { title = null }) { Box(Modifier.size(200.dp, 56.dp)) }
                }
            }
            fun drawn(): List<String> =
                onAllNodesWithText("Back returns to", substring = true, useUnmergedTree = true).fetchSemanticsNodes()
                    .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }
            fun assertEveryDrawnFrameHasTheTitle(what: String) {
                for (t in drawn()) assertTrue("Deploy checks" in t, "$what: the tip is drawn without its title: \"$t\"")
            }
            // No title yet: nothing is drawn, not even a sentence with a hole.
            repeat(10) { mainClock.advanceTimeByFrame() }
            assertEquals(emptyList(), drawn(), "no title: no tip")
            // The title arrives: every frame of the fade-in names it.
            title = "Deploy checks"
            repeat(30) {
                mainClock.advanceTimeByFrame()
                assertEveryDrawnFrameHasTheTitle("arriving, frame $it")
            }
            assertTrue(drawn().isNotEmpty(), "the tip is drawn and names where Back goes")
            // It leaves: every frame of the fade-out still names it.
            title = null
            repeat(30) {
                mainClock.advanceTimeByFrame()
                assertEveryDrawnFrameHasTheTitle("leaving, frame $it")
            }
        }
}
