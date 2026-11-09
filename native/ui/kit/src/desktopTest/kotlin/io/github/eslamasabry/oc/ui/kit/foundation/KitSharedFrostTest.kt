@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoothness guard (smoothness 2026-11-09): Glass's in-flow panes share one blurred copy of the picture behind them per
 * frost sigma, instead of each pane blurring the whole picture again in a layer of its own every time the picture moves.
 */
class KitSharedFrostTest {
    @Test
    fun `B-KIT-018 panes of one frost blur the picture behind them once between them, and still show it`() = runComposeUiTest {
        lateinit var backdrop: KitGlassBackdrop
        setContent {
            KitTheme(KitAppearance(style = Style.Glass), FakeKitPlatform(frost = true), "en", stillFrame = true) {
                backdrop = remember { KitGlassBackdrop().also { it.painted = true } }
                Box(Modifier.size(320.dp, 480.dp).kitGlassBackdropBoundary()) {
                    Box(Modifier.fillMaxSize().kitGlassBackdropSource(backdrop) { drawRect(Color(0xFFFF2200)) })
                    CompositionLocalProvider(LocalKitGlassBackdrop provides backdrop) {
                        Column(Modifier.padding(16.dp)) {
                            repeat(3) { i ->
                                Box(Modifier.padding(8.dp).size(200.dp, 60.dp).kitSurfaceGlass(SurfaceDecoration(frost = 12f)).testTag("pane$i"))
                            }
                            Box(Modifier.padding(8.dp).size(200.dp, 60.dp).kitSurfaceGlass(SurfaceDecoration(frost = 26f)))
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(setOf(12f, 26f), backdrop.frosted.keys, "one shared blurred copy per sigma, not one per pane")
        // Each pane still shows the (blurred) picture behind it, not the plain ground.
        for (i in 0 until 3) {
            val px = onNodeWithTag("pane$i").captureToImage().toPixelMap()
            val c = px[px.width / 2, px.height / 2]
            assertTrue(c.red > .8f && c.blue < .2f, "pane $i shows the picture through its frost ($c)")
        }
    }
}
