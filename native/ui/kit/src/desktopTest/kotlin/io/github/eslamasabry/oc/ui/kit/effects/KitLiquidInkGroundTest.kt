@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The words on a liquid pane read the content behind it to pick their ink (adaptive glass). A page that clears its
 * content under a floating control (the chat ends its transcript above "Jump to latest", owner 2026-11-14) leaves
 * nothing drawn there: the sample must read the page's ground, so the words take the theme's ink back. The sampler draws
 * the backdrop scaled to an eighth, and the ground under it was sized by that small target, so it covered a corner only:
 * the cleared content read as see-through, the sample was dropped, and the ink kept the flip it took over the words.
 */
class KitLiquidInkGroundTest {
    @Test
    fun `B-CHAT-COL-04 a liquid pane's words take the theme's ink back once the content behind it is cleared`() =
        runComposeUiTest {
            var bright by mutableStateOf(true)
            var source: KitFrostState? = null
            setContent {
                KitTheme(
                    appearance = KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark, glassLook = GlassLookId.Glossy),
                    platform = FakeKitPlatform(liquidGlass = true),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    Box(Modifier.size(360.dp, 480.dp)) {
                        val s = rememberKitFrostState()
                        source = s
                        // Read in draw only: clearing it records the content again (a new version), nothing else.
                        Box(Modifier.fillMaxSize().kitFrostSource(s).drawBehind { if (bright) drawRect(Color.White) })
                        KitJumpPill("Latest", {}, visible = true, modifier = Modifier.align(Alignment.Center), backdrop = s)
                    }
                }
            }
            mainClock.advanceTimeBy(2_000)
            val flip = assertNotNull(source?.liquidFlips?.get(LiquidRole.Pill), "the pill is a liquid pane over the content")
            assertTrue(flip.progress.value > .9f, "over white the dark theme's words take the other ink: ${flip.progress.value}")
            // The content is cleared: nothing is drawn behind the pill, so it lies on the page's dark ground.
            bright = false
            mainClock.advanceTimeBy(2_000)
            assertEquals(0f, flip.progress.value, "over the page's ground the words take the theme's ink back")
        }
}
