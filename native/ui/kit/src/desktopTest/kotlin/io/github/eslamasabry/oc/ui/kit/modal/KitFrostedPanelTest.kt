@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBudget
import io.github.eslamasabry.oc.ui.kit.effects.LocalKitLiquidBudget
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Frosted glass look on the panels over the page (owner 2026-11-09: with Frosted chosen the sheet was an opaque
 * surface, "Frosted seems not to blur at all"): a sheet is frost of the page behind it, the page blurred, never hidden,
 * on any platform with a blur, and it claims none of the window's liquid budget (it has no lens).
 */
class KitFrostedPanelTest {
    private class Scene(val pagePx: Pair<Color, Color>, val sheetPx: Pair<Color, Color>, val edgeStepPage: Float, val edgeStepSheet: Float, val claims: Int)

    /** A page red on the left and blue on the right, a sheet over its lower half; samples both sides and the seam. */
    private fun scene(look: GlassLookId, dark: Boolean): Scene {
        var out: Scene? = null
        runDesktopComposeUiTest(400, 800) {
            var budget: KitLiquidBudget? = null
            mainClock.autoAdvance = false
            setContent {
                KitTheme(
                    KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, style = Style.Glass, glassLook = look),
                    FakeKitPlatform(frost = true),
                    "en",
                    stillFrame = true,
                ) {
                    KitSheetHost {
                        budget = LocalKitLiquidBudget.current
                        Box(
                            Modifier.fillMaxSize().drawBehind {
                                drawRect(Red, Offset.Zero, Size(size.width / 2f, size.height))
                                drawRect(Blue, Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height))
                            }
                        )
                        KitSheet(visible = true, onDismissRequest = {}, title = "Sheet") {
                            Spacer(Modifier.fillMaxWidth().height(240.dp).testTag("body"))
                        }
                    }
                }
            }
            repeat(20) { mainClock.advanceTimeBy(100) }
            val body = onNodeWithTag("body").fetchSemanticsNode().boundsInRoot
            val px = onRoot().captureToImage().toPixelMap()
            val y = body.center.y.toInt()
            val mid = px.width / 2
            val step = (4 * density.density).toInt()
            fun hue(c: Color) = c.red - c.blue
            out =
                Scene(
                    pagePx = px[px.width / 8, 8] to px[px.width * 7 / 8, 8],
                    sheetPx = px[px.width / 8, y] to px[px.width * 7 / 8, y],
                    edgeStepPage = abs(hue(px[mid - step, 8]) - hue(px[mid + step, 8])),
                    edgeStepSheet = abs(hue(px[mid - step, y]) - hue(px[mid + step, y])),
                    claims = budget?.claims?.size ?: -1,
                )
        }
        return out!!
    }

    @Test
    fun `B-KIT-018 under the Frosted look a sheet shows the page behind it, blurred`() {
        for (dark in listOf(false, true)) {
            val s = scene(GlassLookId.Frosted, dark)
            val (left, right) = s.sheetPx
            // The page's red and blue show through: the sheet's left leans red, its right blue (an opaque sheet is one colour).
            assertTrue(left.red - left.blue > .04f, "dark=$dark: the red page shows through the sheet's left: $left")
            assertTrue(right.blue - right.red > .04f, "dark=$dark: the blue page shows through the sheet's right: $right")
            // Blurred: across the seam the sheet changes far more gently than the page does.
            assertTrue(s.edgeStepSheet < s.edgeStepPage * .5f, "dark=$dark: the seam is soft through the sheet (${s.edgeStepSheet} vs ${s.edgeStepPage})")
            assertTrue(s.claims == 0, "dark=$dark: the Frosted sheet claims no liquid glass (${s.claims})")
        }
    }

    @Test
    fun `B-KIT-018 a lens look on a platform without the lens keeps its own face`() {
        // Glossy here has no lens to draw (the fake platform has none): the sheet keeps today's frosted fallback, so this
        // change touches only the Frosted look.
        val s = scene(GlassLookId.Glossy, dark = true)
        val (left, right) = s.sheetPx
        assertTrue(abs((left.red - left.blue) - (right.red - right.blue)) < .02f, "the fallback face is one colour: $left / $right")
    }

    private companion object {
        val Red = Color(0xFFE53935)
        val Blue = Color(0xFF1E5BE5)
    }
}
