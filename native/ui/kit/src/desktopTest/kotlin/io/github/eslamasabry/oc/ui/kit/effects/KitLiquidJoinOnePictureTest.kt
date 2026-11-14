@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderCompiler
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cut audit 1 (c14, c25): with the chat's "..." menu open, the bar and its menu joined into one body showed a second,
 * darker rounded shape inside the body at the junction. The menu's side drew from the page (which holds the bar's own
 * face and everything floating over the transcript) while the bar's side drew from the transcript the bar records, and
 * the menu's hold pulled what only the page holds into a band under the bar, ending in a rounded corner on the line where
 * the two sides meet. One body reads one picture: something on the page outside the bar's recording changes nothing
 * inside the joined menu.
 */
class KitLiquidJoinOnePictureTest {
    private val glass = KitAppearance(style = Style.Glass)

    private fun platform(): FakeKitPlatform =
        FakeKitPlatform(
            liquidGlass = true,
            liveShaders = LiveShaderCompiler(DesktopLiveShader::compile),
            joinEffect = { p, b -> DesktopKitPlatform().liquidJoinEffect(p, b) },
        )

    /** A page whose transcript is one even grey; [overlay] lays a magenta field over it on the page, outside the bar's recording. */
    @Composable
    private fun Scene(overlay: Boolean, open: Boolean) {
        val source = rememberKitFrostState()
        KitScreen(
            page = true,
            overlayChrome = true,
            frost = source,
            topBar = {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    KitMenu(
                        expanded = open,
                        items = listOf(KitMenuItem("Find", onSelect = {}), KitMenuItem("Rename", onSelect = {}), KitMenuItem("Fork", onSelect = {}), KitMenuItem("Copy", onSelect = {})),
                        onDismissRequest = {},
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    ) { KitText("More") }
                }
            },
            edges = KitScreenEdges.Bleed,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().kitFrostSource(source)) { Box(Modifier.fillMaxSize().background(Color(0xFF606870))) }
                if (overlay) Box(Modifier.fillMaxSize().background(Color(0xFFFF00FF)))
            }
        }
    }

    private class Shot(val img: PixelMap, val menu: Rect)

    private fun shot(overlay: Boolean): Shot {
        lateinit var out: Shot
        runDesktopComposeUiTest(412, 800) {
            var open by mutableStateOf(false)
            setContent { KitTheme(appearance = glass, platform = platform(), localeTag = "en", stillFrame = true) { Scene(overlay, open) } }
            waitForIdle()
            // Opened once the bar has been placed, as a person opens it: the menu joins the bar's glass.
            open = true
            waitForIdle()
            mainClock.advanceTimeBy(1_500)
            waitForIdle()
            val menu = onNodeWithTag("kit-menu", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            out = Shot(onRoot().captureToImage().toPixelMap(), menu)
        }
        return out
    }

    @Test
    fun `B-GLASS-JOIN-001 the joined menu reads the bar's picture - a field on the page outside it changes nothing inside the menu`() {
        val plain = shot(overlay = false)
        val over = shot(overlay = true)
        val m = plain.menu
        assertTrue(m.width > 100f && m.height > 100f, "the menu is open: $m")
        // The menu's glass from its top (the junction with the bar) down, clear of its sides' rims.
        val inset = 30f
        var worst = 0f
        var at = ""
        for (y in (m.top + 2f).roundToInt() until (m.bottom - inset).roundToInt()) for (x in (m.left + inset).roundToInt() until (m.right - inset).roundToInt()) {
            val a = plain.img[x, y]
            val b = over.img[x, y]
            val d = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue))) * 255f
            if (d > worst) {
                worst = d
                at = "($x, $y)"
            }
        }
        println("JOIN ONE PICTURE: largest change inside the joined menu $worst at $at; menu $m")
        // The field shows on the page beside the menu: the overlay is really there.
        val beside = over.img[(m.left / 2f).roundToInt(), (m.bottom + 40f).roundToInt()]
        assertTrue(beside.red > .8f && beside.blue > .8f && beside.green < .2f, "the magenta field lies on the page beside the menu: $beside")
        assertTrue(worst <= 3f, "the joined menu shows what the page holds outside the bar's picture: a change of $worst at $at")
    }
}
