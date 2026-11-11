@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.GlassLook
import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import io.github.eslamasabry.oc.ui.design.style.GlassSpec
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.drawGlassFace
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRowPanel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * docs/designs/glass-in-the-kit.md step 1 in the kit: a clear pane's rim lit at both ends in dark, drawn as three stops
 * (1a), Frosted and Smoked drawn exactly as before, chosen rows keeping their accent rim, and a list panel's row lines
 * stopping one gutter before the rim under Glass (1c).
 */
class GlassInTheKitStep1KitTest {
    private val white = Argb(0xFFFFFFFF.toInt())

    private fun clearPane(dark: Boolean) =
        StyleResolver.adjust(ThemeResolver.roles(PackId.Opencode, null, dark, saver = false, dynamic = null), Style.Glass).let { roles ->
            roles to GlassSpec.resolveClear(SurfaceRequest(roles, SurfaceRole.Content, Knobs()))!!
        }

    @Test
    fun `B-KIT-003 1a a clear pane's rim in dark is start 24, rest 10, end 30 percent white, light as before`() {
        val (dark, darkPane) = clearPane(true)
        val lit = glassRims(darkPane, dark, clear = true)
        assertEquals(white.withAlpha(.24f), lit.edgeStart)
        assertEquals(white.withAlpha(.10f), lit.edgeRest)
        assertEquals(white.withAlpha(.30f), lit.edgeEnd)
        val (light, lightPane) = clearPane(false)
        val litLight = glassRims(lightPane, light, clear = true)
        assertEquals(white.withAlpha(.60f), litLight.edgeStart)
        assertNull(litLight.edgeRest, "light: a straight fade, as before")
        assertEquals(white.withAlpha(.16f), litLight.edgeEnd)
    }

    @Test
    fun `B-KIT-003 1a under Frosted and Smoked a clear pane's rim is exactly what it was before step 1`() {
        for (dark in listOf(true, false)) {
            val (roles, pane) = clearPane(dark)
            // Today's numbers, written out: .24 to .07 in dark, .60 to .16 in light, straight, white.
            val today = pane.copy(edgeStart = white.withAlpha(if (dark) .24f else .60f), edgeEnd = white.withAlpha(if (dark) .07f else .16f), edgeRest = null)
            for (look in listOf(GlassLooks.Frosted.spec, GlassLooks.Smoked.spec)) {
                assertEquals(today, glassRims(pane, roles, clear = true, look = look), "dark=$dark $look")
            }
        }
    }

    @Test
    fun `B-KIT-037 1a a chosen row keeps its accent rim, with no rest`() {
        for (dark in listOf(true, false)) {
            val roles = StyleResolver.adjust(ThemeResolver.roles(PackId.Opencode, null, dark, saver = false, dynamic = null), Style.Glass)
            for (clear in listOf(true, false)) {
                val face = chosenRowFace(roles, Knobs(), clear = clear, spots = emptyList(), radius = 12f, look = GlassLook.Default)
                assertEquals(roles.accent.withAlpha(1f).v and 0xFFFFFF, face.edgeStart!!.v and 0xFFFFFF)
                assertEquals(roles.accent.withAlpha(1f).v and 0xFFFFFF, face.edgeEnd!!.v and 0xFFFFFF)
                assertNull(face.edgeRest, "dark=$dark clear=$clear")
            }
        }
    }

    @Test
    fun `B-KIT-003 1b' the page's ink lift and a liquid pane's second-line lift never stack - the larger applies`() {
        val liquid = io.github.eslamasabry.oc.ui.design.effects.LiquidJoin.SECOND_INK_ON_GLASS
        val tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary
        assertEquals(liquid, io.github.eslamasabry.oc.ui.kit.kitToneLift(tone, .3f, onLiquid = true))
        assertEquals(.7f, io.github.eslamasabry.oc.ui.kit.kitToneLift(tone, .7f, onLiquid = true))
        assertEquals(.3f, io.github.eslamasabry.oc.ui.kit.kitToneLift(tone, .3f, onLiquid = false))
        assertEquals(.3f, io.github.eslamasabry.oc.ui.kit.kitToneLift(io.github.eslamasabry.oc.ui.design.type.TextTone.Tertiary, .3f, onLiquid = true))
        assertEquals(0f, io.github.eslamasabry.oc.ui.kit.kitToneLift(io.github.eslamasabry.oc.ui.design.type.TextTone.Primary, .3f, onLiquid = true))
        assertEquals(0f, io.github.eslamasabry.oc.ui.kit.kitToneLift(tone, 0f, onLiquid = false), "no page light, no liquid pane: the plain ink")
    }

    /** [d] drawn alone on black, a 200 x 200 px square (density 1). */
    private fun drawn(d: SurfaceDecoration): PixelMap {
        lateinit var map: PixelMap
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.size(200.dp).background(Color.Black).testTag("face")) {
                        Canvas(Modifier.size(200.dp)) { drawGlassFace(d) }
                    }
                }
            }
            map = onNodeWithTag("face").captureToImage().toPixelMap()
        }
        return map
    }

    @Test
    fun `B-KIT-003 1a the rim is drawn with three stops - lit at the top start and the bottom end, quiet between`() {
        val rim = SurfaceDecoration(edgeStart = white.withAlpha(.24f), edgeRest = white.withAlpha(.10f), edgeEnd = white.withAlpha(.30f), edgeWidthDp = 1f)
        val map = drawn(rim)
        // The rim runs along the inside of the square's edge; the fade goes from the top start to the bottom end, so the
        // top-end and bottom-start corners lie at its middle (the rest).
        val topStart = map[2, 0].red
        val bottomEnd = map[197, 199].red
        val topEnd = map[199, 2].red
        val bottomStart = map[0, 197].red
        assertTrue(bottomEnd > topEnd + .1f, "the bottom end is lit ($bottomEnd vs the rest $topEnd)")
        assertTrue(topStart > topEnd + .06f, "the top start is lit ($topStart vs the rest $topEnd)")
        assertTrue(kotlin.math.abs(topEnd - bottomStart) < .03f, "the rest is the same at both middles ($topEnd, $bottomStart)")
        assertTrue(kotlin.math.abs(topEnd - .10f) < .03f, "the rest is .10 white on black ($topEnd)")
        // A straight rim (no rest) is drawn as before: its middle is halfway between its ends.
        val straight = drawn(rim.copy(edgeRest = null, edgeEnd = white.withAlpha(.07f)))
        assertTrue(kotlin.math.abs(straight[199, 2].red - .155f) < .03f, "a straight rim's middle (${straight[199, 2].red})")
    }

    /** Whether the panel's seam row is painted at [xDp] (dp from the panel's start), under [style]. */
    private fun seamAt(style: Style, xDp: (gutter: Int) -> Float): Boolean {
        var painted = false
        runComposeUiTest {
            var gutter = 0
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(3f)) {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off, style = style),
                        stillFrame = true,
                        localeTag = "en",
                    ) {
                        gutter = LocalKitTokens.current.gutter
                        Box(Modifier.width(300.dp).testTag("panel")) {
                            KitRowPanel(leadingMarks = false, prominentDividers = true) {
                                Box(Modifier.fillMaxWidth().height(40.dp))
                                Box(Modifier.fillMaxWidth().height(40.dp))
                            }
                        }
                    }
                }
            }
            val map = onNodeWithTag("panel").captureToImage().toPixelMap()
            val x = (xDp(gutter) * 3f).toInt()
            // The seam lies 40 dp down (120 px); the rows' own face is sampled 60 dp down (mid second row, below any top glow), in the same column.
            val seam = map[x, 121]
            val face = map[x, 180]
            painted = kotlin.math.abs(seam.red - face.red) > .01f || kotlin.math.abs(seam.green - face.green) > .01f ||
                kotlin.math.abs(seam.blue - face.blue) > .01f
        }
        return painted
    }

    @Test
    fun `B-KIT-037 1c under Glass a panel's row lines stop one gutter before its end edge, in Solid they run to it`() {
        // The panel sits on the page gutter: its end edge is 300 - gutter dp from the start.
        val middle: (Int) -> Float = { 150f }
        val nearEnd: (Int) -> Float = { g -> 300f - g - g / 2f }
        assertTrue(seamAt(Style.Glass, middle), "Glass: the line is there mid-panel")
        assertTrue(!seamAt(Style.Glass, nearEnd), "Glass: no line in the last gutter, clear of the rim")
        assertTrue(seamAt(Style.Solid, middle), "Solid: the line is there mid-panel")
        assertTrue(seamAt(Style.Solid, nearEnd), "Solid: the line still runs to the edge")
    }
}
