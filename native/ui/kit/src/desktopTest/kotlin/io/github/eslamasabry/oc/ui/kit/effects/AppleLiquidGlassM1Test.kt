@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.effects.RimLight
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.kitRimLightGround
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.platform.KitGlassTier
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint

/**
 * Apple Liquid Glass Milestone 1 verification:
 * - B-607: asymmetric corner SDF refraction bug fixed in vendored Shaders.kt
 * - B-608: floating chrome lets scrolling content enter header and composer rims
 * - B-609: dome bend, specular highlight, top sheen, clear bars/pills, deeper modals, dispersion off
 * - B-612: light-mode white specular rim, active backdrop rim light, low tint
 * - B-613: adaptive elevation shadow enabled on all liquid panes
 * - B-614: OS cross-window blur toggle & Reduce Transparency cleanly step down liquid glass
 * - B-615: modal hold shader reduced from 36 taps/px to <= 12 taps/px
 */
class AppleLiquidGlassM1Test {

    @Test
    fun `B-607 vendored Shaders kt passes centeredCoord to radiusAt in every shader and curves top sheet corners`() {
        val root = File(System.getProperty("user.dir")).let { dir ->
            generateSequence(dir) { it.parentFile }.firstOrNull { File(it, "third_party/backdrop").isDirectory }
                ?: error("Cannot locate native/third_party/backdrop from $dir")
        }
        val shaders = File(root, "third_party/backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt").readText()
        assertFalse("radiusAt(coord," in shaders, "Every shader must pass centeredCoord, not uncentered coord, to radiusAt")
        val centeredCalls = Regex("""radiusAt\(centeredCoord,\s*cornerRadii\)""").findAll(shaders).count()
        assertEquals(5, centeredCalls, "All 5 rounded-rect shaders must call radiusAt(centeredCoord, cornerRadii)")

        // Compile the fixed rounded-rect SDF refraction math in Skia SkSL on an asymmetric bottom sheet
        // (top corners = 28 px, bottom corners = 0 px) and prove top-left bends differently from square bottom-left.
        val sksl =
            """
            uniform shader content;
            uniform float2 size;
            uniform float4 cornerRadii;
            uniform float refractionHeight;
            uniform float refractionAmount;

            float radiusAt(float2 coord, float4 radii) {
                if (coord.x >= 0.0) {
                    if (coord.y <= 0.0) return radii.y;
                    else return radii.z;
                } else {
                    if (coord.y <= 0.0) return radii.x;
                    else return radii.w;
                }
            }

            float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
                float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
                float outside = length(max(cornerCoord, 0.0)) - radius;
                float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
                return outside + inside;
            }

            float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
                float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
                if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
                    return sign(coord) * normalize(max(cornerCoord, 0.0));
                } else {
                    float gradX = step(cornerCoord.y, cornerCoord.x);
                    return sign(coord) * float2(gradX, 1.0 - gradX);
                }
            }

            half4 main(float2 coord) {
                float2 halfSize = size * 0.5;
                float2 centeredCoord = coord - halfSize;
                float radius = radiusAt(centeredCoord, cornerRadii);
                float sd = sdRoundedRect(centeredCoord, halfSize, radius);
                if (-sd >= refractionHeight) return content.eval(coord);
                float t = clamp(1.0 - (-min(sd, 0.0)) / refractionHeight, 0.0, 1.0);
                float d = t * refractionAmount;
                float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
                float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius));
                return content.eval(coord + d * grad);
            }
            """.trimIndent()
        val program = assertNotNull(DesktopLiveShader.compile(sksl), "asymmetric corner refraction shader compiles as SkSL")
        val w = 120
        val h = 120
        program.float2("size", w.toFloat(), h.toFloat())
        program.float4("cornerRadii", 28f, 28f, 0f, 0f)
        program.float("refractionHeight", 24f)
        program.float("refractionAmount", 18f)
        val filter = assertNotNull((program as DesktopLiveShader).filter(0f))
        val bmp = Bitmap().apply { allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.PREMUL)) }
        Canvas(bmp).apply {
            saveLayer(null, Paint().apply { imageFilter = filter })
            // Encode x coordinate in red channel and y coordinate in green channel so refracted offset is exact.
            for (y in 0 until h) for (x in 0 until w) {
                drawRect(
                    org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), y.toFloat(), 1f, 1f),
                    Paint().apply { color = org.jetbrains.skia.Color.makeRGB((x * 2).coerceAtMost(255), (y * 2).coerceAtMost(255), 0) },
                )
            }
            restore()
        }
        // At (10, 10) inside the 28 px rounded top-left corner, both x and y shift diagonally; at (10, 110) near
        // square bottom-left, the shift is distinct because radius = 0 vs 28.
        val topCorner = bmp.getColor(10, 10)
        val botCorner = bmp.getColor(10, h - 11)
        val topRed = (topCorner shr 16) and 0xFF
        val botRed = (botCorner shr 16) and 0xFF
        assertTrue(abs(topRed - botRed) >= 4, "28 px top corner ($topRed) must refract distinctly from 0 px bottom corner ($botRed)")
    }

    @Test
    fun `B-608 floating chrome leaves scrolling backdrop visible in header and composer lens rims`() {
        assertTrue(GlassTuning.WORDS_CLEAR_FLOATING <= 0.30f, "floating chrome wordsClear must let passing content enter the lens rim")
        for (kind in listOf(GlassPaneKind.Bar, GlassPaneKind.MessageBox)) {
            assertEquals(GlassTuning.WORDS_CLEAR_FLOATING, GlassTuning.shipped(kind, true).wordsClear)
            assertEquals(GlassTuning.WORDS_CLEAR_FLOATING, GlassTuning.shipped(kind, false).wordsClear)
        }
    }

    @Test
    fun `B-609 shipped liquid glass restores dome bend, specular, top sheen, clear bars and deeper modals with dispersion off`() =
        runComposeUiTest {
            val platform = FakeKitPlatform(liquidGlass = true)
            setContent {
                KitTheme(KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark), platform, "en", stillFrame = true) {
                    val frost = rememberKitFrostState()
                    Box(Modifier.size(360.dp, 640.dp)) {
                        Box(Modifier.fillMaxSize().kitFrostSource(frost))
                        for (role in listOf(LiquidRole.Bar, LiquidRole.Composer, LiquidRole.Menu, LiquidRole.Sheet, LiquidRole.Pill, LiquidRole.Action)) {
                            Box(Modifier.fillMaxWidth().height(56.dp).then(kitLiquidFace(role, frost, RoundedCornerShape(24.dp)) ?: Modifier))
                        }
                    }
                }
            }
            waitForIdle()
            val specs = platform.liquidSpecs
            assertTrue(specs.isNotEmpty(), "liquid specs captured")
            for (s in specs) {
                assertTrue(s.bendShape < 0f, "dome bend must be negative (was ${s.bendShape})")
                assertTrue(s.specular.alpha > 0.3f, "specular highlight must be enabled (was ${s.specular.alpha})")
                assertTrue(s.sheen.alpha > 0f, "top sheen must be enabled (was ${s.sheen.alpha})")
                assertEquals(0f, s.dispersion, "chromatic dispersion stays OFF by default per owner choice")
                assertTrue(s.shadow.alpha > 0f, "B-613 adaptive elevation shadow must be active")
            }
            val bar = LiquidEdge.recipe(LiquidRole.Bar, dark = true)
            val pill = LiquidEdge.recipe(LiquidRole.Pill, dark = true)
            val menu = LiquidEdge.recipe(LiquidRole.Menu, dark = true)
            val sheet = LiquidEdge.recipe(LiquidRole.Sheet, dark = true)
            assertTrue(bar.blurDp <= 1f && pill.blurDp <= 1f, "bars and pills keep clear optical centers")
            assertTrue(menu.blurDp >= 5f && sheet.blurDp >= 5f, "modals carry heavier optical depth than slim bars")
            assertTrue(RimLight.FLOOR <= 0.20f, "rim floor avoids wireframe outline (was ${RimLight.FLOOR})")
        }

    @Test
    fun `B-612 light mode liquid glass uses white specular rim, active backdrop rim light, low tint and exterior shadow`() =
        runComposeUiTest {
            val platform = FakeKitPlatform(liquidGlass = true)
            var lightRimActive = false
            setContent {
                KitTheme(KitAppearance(style = Style.Glass, mode = AppearanceMode.Light), platform, "en", stillFrame = true) {
                    lightRimActive = kitRimLightGround() != null
                    val frost = rememberKitFrostState()
                    Box(Modifier.size(360.dp, 400.dp)) {
                        Box(Modifier.fillMaxSize().kitFrostSource(frost))
                        Box(Modifier.fillMaxWidth().height(56.dp).then(kitLiquidFace(LiquidRole.Bar, frost, RoundedCornerShape(24.dp)) ?: Modifier))
                        Box(Modifier.fillMaxWidth().height(40.dp).then(kitLiquidFace(LiquidRole.Pill, frost, RoundedCornerShape(20.dp)) ?: Modifier))
                    }
                }
            }
            waitForIdle()
            assertTrue(lightRimActive, "BackdropRimLight must stay active in Light mode")
            assertTrue(platform.liquidSpecs.isNotEmpty())
            for (s in platform.liquidSpecs) {
                assertEquals(1f, s.rim.red, "light-mode rim must be white specular, not black")
                assertEquals(1f, s.rim.green, "light-mode rim must be white specular, not black")
                assertEquals(1f, s.rim.blue, "light-mode rim must be white specular, not black")
                assertTrue(s.rim.alpha >= 0.5f, "light-mode white rim must be clearly luminous (${s.rim.alpha})")
                assertTrue(s.specular.alpha >= 0.5f, "light-mode specular must be luminous (${s.specular.alpha})")
                assertTrue(s.recipe.tint <= 0.16f, "light-mode tint must stay translucent, not milky plastic (${s.recipe.tint})")
                assertTrue(s.shadow.alpha > 0f, "light-mode glass uses soft exterior shadow for separation")
            }
        }

    @Test
    fun `B-614 cross-window blur toggle and Reduce Transparency cleanly step down liquid glass`() {
        fun liquid(signals: KitSystemSignals, liquidSupported: Boolean = true): Boolean {
            var on = false
            runComposeUiTest {
                val p = FakeKitPlatform(signals = signals, liquidGlass = liquidSupported)
                setContent {
                    KitTheme(KitAppearance(style = Style.Glass), p, "en", stillFrame = true) {
                        on = kitLiquidGlassOn()
                    }
                }
                waitForIdle()
            }
            return on
        }
        assertTrue(liquid(KitSystemSignals(crossWindowBlurEnabled = true, reduceTransparency = false)))
        assertFalse(liquid(KitSystemSignals(crossWindowBlurEnabled = false)), "OS blur disabled steps liquid glass down")
        assertFalse(liquid(KitSystemSignals(reduceTransparency = true)), "Reduce Transparency steps liquid glass down")
        assertEquals(KitGlassTier.AppleLiquid, FakeKitPlatform(liquidGlass = true).glassTier)
        assertEquals(KitGlassTier.Translucent, FakeKitPlatform(liquidGlass = false).glassTier)
    }

    @Test
    fun `B-615 modal hold shader avoids 36-tap per-pixel spiral and compiles in Skia`() {
        val loopMatch = Regex("""for\s*\(\s*int\s+i\s*=\s*0\s*;\s*i\s*<\s*(\d+)\s*;""").find(LiquidHoldShader.LOCAL_HOLD)
        val taps = assertNotNull(loopMatch, "localHold loop found").groupValues[1].toInt()
        assertTrue(taps in 4..12, "B-615 modal hold must use <= 12 taps/px (found $taps)")
        assertNotNull(DesktopLiveShader.compile(LiquidHoldShader.SOURCE), "optimized LiquidHoldShader compiles cleanly")
    }
}
