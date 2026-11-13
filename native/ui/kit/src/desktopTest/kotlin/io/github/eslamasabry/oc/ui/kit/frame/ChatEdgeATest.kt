@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Chat edge (owner's pick 2026-11-09, option A, amended 2026-11-14 after the owner's "Wtfff" screenshot): an
 * edge-to-edge chat frame. At rest its first row lies wholly below the bar; scrolled, nothing of the content shows under
 * the status bar or the bar and it fades in over 12 dp below the bar. Glass bends or frosts the body's recording in its
 * capsule; the other styles (owner 2026-11-14: "solids ... on the header") lay nothing behind the chrome, so the page's
 * background runs unbroken from the window's top, and the fade below the bar shows only while something lies under it.
 */
class ChatEdgeATest {
    /** Desktop with the lens and a blur, whose liquid panes draw their backdrop blurred, as Android's lens does. */
    private class LensPlatform(private val base: FakeKitPlatform = FakeKitPlatform(frost = true, liquidGlass = true)) :
        KitPlatform by base {
        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val r = LENS_BLUR_DP.dp.toPx()
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        layer.renderEffect = BlurEffect(r, r)
                        layer.record { backdrop.draw(this, c) }
                        drawLayer(layer)
                        drawRect(spec.fill)
                    }
                }
        }
    }

    /**
     * A chat frame: [rows] rows of [rowColor] ([gap] dp apart) in a list scrolled to [first], a 56 dp bar, a 96 dp block;
     * with [pill], a control floating 8 dp under the bar ("N earlier messages") and a list that ends itself before it.
     */
    @Composable
    private fun Scene(rows: Int, first: Int, gap: Dp, paint: Boolean = true, rowColor: Color = Color.White, pill: Boolean = false) {
        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp)) {
            Box(Modifier.size(W.dp, H.dp).testTag("scene")) {
                val source = rememberKitFrostState()
                val list = rememberLazyListState(first)
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = source,
                    topBar = { Box(Modifier.fillMaxWidth().height(BAR_DP.dp).testTag("bar")) },
                    bottom = { Box(Modifier.fillMaxWidth().height(96.dp)) },
                    edges = KitScreenEdges.Bleed,
                ) {
                    KitReportScrolledUnder { list.canScrollBackward }
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = list,
                            contentPadding = kitScreenChromePadding(),
                            modifier =
                                Modifier.fillMaxSize().kitFrostSource(source)
                                    .then(if (pill) Modifier.kitScreenListEdges() else Modifier)
                                    .testTag("list"),
                        ) {
                            items(rows) { i ->
                                Box(
                                    Modifier.fillMaxWidth().height(ROW_DP.dp).padding(bottom = gap)
                                        .then(if (paint) Modifier.background(rowColor) else Modifier)
                                        .testTag("row-$i")
                                )
                            }
                        }
                        if (pill)
                            Box(
                                Modifier.align(Alignment.TopCenter)
                                    .padding(top = kitScreenChromePadding().calculateTopPadding() + PILL_GAP_DP.dp)
                                    .kitScreenFloat(KitJumpEdge.Top)
                                    .size(PILL_W_DP.dp, PILL_H_DP.dp)
                                    .background(PILL)
                                    .testTag("pill")
                            )
                    }
                }
            }
        }
    }

    private class Shot(val image: PixelMap, val bar: androidx.compose.ui.unit.DpRect, val row0: androidx.compose.ui.unit.DpRect?)

    private fun shot(
        appearance: KitAppearance,
        platform: KitPlatform,
        rows: Int = 60,
        first: Int = 0,
        gap: Dp = 8.dp,
        ground: ((Color) -> Color)? = null,
        paint: Boolean = true,
        rowColor: Color = Color.White,
        pill: Boolean = false,
    ): Shot {
        lateinit var shot: Shot
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                        if (ground == null) Scene(rows, first, gap, paint, rowColor, pill)
                        else {
                            // A busy picture behind a see-through ground, for "no band at rest".
                            Box(Modifier.size(W.dp, H.dp).drawBehind { stripes() }) {
                                val colors = LocalKitColors.current
                                CompositionLocalProvider(LocalKitColors provides colors.copy(ground = ground(colors.ground))) {
                                    Scene(rows, first, gap, paint, rowColor, pill)
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val row0 = if (first == 0 && rows > 0) onNodeWithTag("row-0").getBoundsInRoot() else null
            shot = Shot(onNodeWithTag("scene").captureToImage().toPixelMap(), onNodeWithTag("bar").getBoundsInRoot(), row0)
        }
        return shot
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.stripes() {
        val stripe = 12.dp.toPx()
        var x = 0f
        var i = 0
        while (x < size.width) {
            drawRect(if (i++ % 2 == 0) Color(0xFF7A2E8C) else Color(0xFF1E6B4A), Offset(x, 0f), Size(stripe, size.height))
            x += stripe
        }
    }

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    private fun dark(style: Style, look: GlassLookId = GlassLookId.Glossy) =
        KitAppearance(style = style, mode = AppearanceMode.Dark, glassLook = look)

    private fun light(style: Style) = KitAppearance(style = style, mode = AppearanceMode.Light)

    /**
     * Below the bar's bottom edge in column [x]: how far [s] is from [none] (the same scene whose rows draw nothing) at
     * the bar's edge and every pixel down to 2 dp past the fade, and 20 dp below the bar.
     */
    private fun fadeBelow(s: Shot, none: Shot, x: Int): Pair<List<Float>, Float> {
        val barBottom = s.bar.bottom.value
        val full = abs(luma(s.image[x, ((barBottom + 20) * D).toInt()]) - luma(none.image[x, ((barBottom + 20) * D).toInt()]))
        val ramp = (0..((HEADER_FADE_DP + 2) * D).toInt()).map { k ->
            val y = (barBottom * D).toInt() + k
            abs(luma(s.image[x, y]) - luma(none.image[x, y]))
        }
        return ramp to full
    }

    /** The rows come in below the bar over 12 dp as a soft fade: none at its edge, whole past it, no dip and no step. */
    private fun assertSoftFadeIn(what: String, ramp: List<Float>, full: Float) {
        println("$what: full $full ramp ${ramp.map { it.toInt() }}")
        assertTrue(full >= 150f, "$what: the rows are whole 20 dp below the bar, $full")
        assertTrue(ramp.first() <= CLEAR_MAX, "$what: nothing of the rows at the bar's bottom edge, ${ramp.first()}")
        assertTrue(ramp.last() >= full * .9f, "$what: the rows are whole by ${HEADER_FADE_DP + 2} dp below the bar, ${ramp.last()} of $full")
        assertTrue(ramp.zipWithNext().all { (a, b) -> b >= a - 2f }, "$what: the rows come in without a dip: ${ramp.map { it.toInt() }}")
        val levels = ramp.filter { it > CLEAR_MAX && it < full * .9f }.map { (it / 8f).toInt() }.distinct()
        assertTrue(levels.size >= 4, "$what: a soft fade, not a cut: ${levels.size} steps in ${ramp.map { it.toInt() }}")
        val maxStep = ramp.zipWithNext().maxOf { (a, b) -> abs(b - a) }
        assertTrue(maxStep <= full / 6f, "$what: no hard step in the fade, $maxStep of $full")
    }

    /** Behind the status bar and the bar of [s] (above its bottom edge): the same picture as [none]'s, every pixel. */
    private fun assertNothingUnderChrome(what: String, s: Shot, none: Shot) {
        var worst = 0f
        var at = ""
        for (y in 0 until ((s.bar.bottom.value - .5f) * D).toInt()) for (x in 0 until s.image.width) {
            val d = abs(luma(s.image[x, y]) - luma(none.image[x, y]))
            if (d > worst) {
                worst = d
                at = "(${x / D}, ${y / D}) dp"
            }
        }
        assertTrue(worst <= CLEAR_MAX, "$what: the rows show under the chrome, $worst of 255 at $at (bar bottom ${s.bar.bottom})")
    }

    @Test
    fun `E20-S13 at rest the first row starts right below the bar, in every look`() {
        val looks =
            listOf(
                "liquid Glass" to (dark(Style.Glass) to LensPlatform()),
                "frosted Glass" to (dark(Style.Glass, GlassLookId.Frosted) to LensPlatform()),
                "Solid" to (dark(Style.Solid) to FakeKitPlatform()),
            )
        for ((what, look) in looks) {
            val s = shot(look.first, look.second)
            val row0 = s.row0!!
            assertTrue(abs(s.bar.top.value - STATUS_DP) <= .5f, "$what: the bar sits under the status bar, at ${s.bar}")
            // The body's top padding is the status bar plus the bar: nothing is hidden at rest, and no extra gap.
            assertTrue(row0.top >= s.bar.bottom - .5.dp, "$what: the first row $row0 is under the bar ${s.bar}")
            assertTrue(row0.top <= s.bar.bottom + .5.dp, "$what: the first row $row0 starts right below the bar ${s.bar}")
        }
    }

    /**
     * Owner 2026-11-14 ("Wtfff": transcript text under the bar showed as a blurred band inside its lower edge), the
     * coordinator's option A for chat edge: scrolled, nothing of the content is drawn under the status bar or the bar,
     * through its glass or beside it, and it fades in over 12 dp below the bar. Was: "the content blurs behind the
     * status bar and the bar" (chat edge A, 2026-11-09). Compared with the same scene whose rows draw nothing.
     */
    @Test
    fun `E20-S30 scrolled under liquid and frosted Glass nothing of the content shows under the status bar or the bar, and it fades in below it`() {
        for ((what, appearance) in listOf("liquid" to dark(Style.Glass), "frosted" to dark(Style.Glass, GlassLookId.Frosted))) {
            val s = shot(appearance, LensPlatform(), first = 8, gap = 0.dp)
            val none = shot(appearance, LensPlatform(), first = 8, gap = 0.dp, paint = false)
            // Under the status bar, in the capsule and beside it: the same picture as with no rows at all.
            assertNothingUnderChrome(what, s, none)
            // Below the bar, beside the middle: the rows come in over 12 dp in steps, whole past it.
            val (ramp, full) = fadeBelow(s, none, (W * D / 4).toInt())
            assertSoftFadeIn(what, ramp, full)
        }
    }

    /**
     * Owner 2026-11-14 ("Bro why there are solids above the composer and on the header"): scrolled, the clear styles lay
     * no plate of the page's ground behind the chrome. Over a live picture (a see-through ground over stripes) every
     * pixel behind the status bar and the bar is the picture alone, the same as the bare picture at the window's
     * bottom: no ground band, no row. Was: "the ground lies over a live picture behind the chrome".
     */
    @Test
    fun `E20-S30 scrolled in Solid dark and light the live background alone lies behind the status bar and the bar`() {
        for ((what, appearance) in listOf("Solid dark" to dark(Style.Solid), "Solid light" to light(Style.Solid), "Soft dark" to dark(Style.Soft))) {
            val s = shot(appearance, FakeKitPlatform(), first = 8, gap = 0.dp, ground = { it.copy(alpha = .6f) })
            val bare = ((H - NAV_DP / 2) * D).toInt()
            var worst = 0f
            var at = ""
            for (y in 0 until ((s.bar.bottom.value - .5f) * D).toInt()) for (x in 0 until s.image.width step 2) {
                val d = abs(luma(s.image[x, y]) - luma(s.image[x, bare]))
                if (d > worst) {
                    worst = d
                    at = "(${x / D}, ${y / D}) dp"
                }
            }
            assertTrue(worst <= CLEAR_MAX, "$what scrolled: behind the chrome is not the bare picture, $worst of 255 at $at")
        }
    }

    /**
     * The clear styles' content is gone above the bar's bottom edge and fades in over 12 dp below it, the same fade as
     * Glass's; its fills dissolve with its words. Was: "the page's ground runs through the status bar and the bar".
     */
    @Test
    fun `E20-S30 scrolled in Solid nothing of the content shows under the chrome and it fades in below the bar`() {
        val cases =
            listOf(
                Triple("Solid dark", dark(Style.Solid), Color.White),
                Triple("Solid light", light(Style.Solid), Color.Black),
            )
        for ((what, appearance, row) in cases) {
            val s = shot(appearance, FakeKitPlatform(), first = 8, gap = 0.dp, rowColor = row)
            val none = shot(appearance, FakeKitPlatform(), first = 8, gap = 0.dp, paint = false)
            assertNothingUnderChrome(what, s, none)
            val (ramp, full) = fadeBelow(s, none, (W * D / 2).toInt())
            assertSoftFadeIn(what, ramp, full)
        }
    }

    @Test
    fun `E20-S30 at rest in Solid no band lies over a live picture`() {
        // Nothing under the chrome (no rows): the picture shows the same at the top as further down, in every column.
        for ((what, appearance) in listOf("dark" to dark(Style.Solid), "light" to light(Style.Solid))) {
            val s = shot(appearance, FakeKitPlatform(), rows = 0, ground = { it.copy(alpha = .6f) })
            val far = ((H / 2) * D).toInt()
            for (y in 0 until ((STATUS_DP + BAR_DP + HEADER_FADE_DP + 4) * D).toInt() step 3)
                for (x in 0 until s.image.width step 5) {
                    val d = abs(luma(s.image[x, y]) - luma(s.image[x, far]))
                    assertTrue(d <= 2f, "Solid $what at rest: a band at (${x / D}, ${y / D}) dp, $d of 255 off the picture")
                }
        }
    }

    @Test
    fun `E20-S30 at rest in Solid the first row is whole right below the bar, with no fade`() {
        // At rest nothing lies under the chrome, so the body's fade below the bar is not drawn: the first row reads whole
        // from its first pixel, as before the fix.
        val s = shot(dark(Style.Solid), FakeKitPlatform())
        val row0 = s.row0!!
        val x = (W * D / 2).toInt()
        for (y in (row0.top.value * D).toInt() until ((row0.top.value + ROW_DP - 8 - 1) * D).toInt()) {
            val l = luma(s.image[x, y])
            assertTrue(l >= 250f, "Solid at rest: the first row is faded at y=${y / D} dp (${row0.top} is its top), $l")
        }
    }

    /**
     * A control floating under the bar ("N earlier messages") is chrome, not content: scrolled, in Solid, it stays whole
     * (the body's fade below the bar gives way to the list's own end below the control), and the rows beside it are gone
     * down to its bottom.
     */
    @Test
    fun `E20-S30 scrolled in Solid a control floating under the bar stays whole and no row shows beside it`() {
        val s = shot(dark(Style.Solid), FakeKitPlatform(), first = 8, gap = 0.dp, pill = true)
        val none = shot(dark(Style.Solid), FakeKitPlatform(), first = 8, gap = 0.dp, paint = false, pill = true)
        val barBottom = s.bar.bottom.value
        val pillTop = barBottom + PILL_GAP_DP
        val mid = (W * D / 2).toInt()
        val want = luma(PILL)
        for (y in ((pillTop + .5f) * D).toInt() until ((pillTop + PILL_H_DP - .5f) * D).toInt()) {
            val l = luma(s.image[mid, y])
            assertTrue(abs(l - want) <= CLEAR_MAX, "the floating control is faded at y=${y / D} dp (its top $pillTop), $l of $want")
        }
        // Beside it, from the bar down to its bottom: the rows are gone.
        val side = (W * D / 8).toInt()
        for (y in (barBottom * D).toInt() until ((pillTop + PILL_H_DP) * D).toInt()) {
            val d = abs(luma(s.image[side, y]) - luma(none.image[side, y]))
            assertTrue(d <= CLEAR_MAX, "a row shows beside the floating control at y=${y / D} dp, $d of 255")
        }
        assertNothingUnderChrome("Solid with a floating control", s, none)
    }

    @Test
    fun `B-KIT-003 the bar's primary words keep the moving floor over the transcript blurred under it`() {
        // Measure how much ink a paragraph of the transcript's Body text keeps under the bar's blur (the lens's blur
        // radius as a Gaussian), then hold every pack's bar face to its targets over the worst of it, as glass-v3
        // measured its panes (LiquidEdge.worstContrast's spots).
        val share = blurredInkShare()
        println("transcript ink under the bar's blur: ${"%.3f".format(share)} (LiquidEdge.BLURRED_INK ${LiquidEdge.BLURRED_INK})")
        for (pack in PackId.entries) for (darkMode in listOf(true, false)) {
            val roles = ThemeResolver.roles(pack, null, darkMode, saver = false, dynamic = null)
            val recipe = LiquidEdge.recipe(LiquidRole.Bar, darkMode)
            val face = LiquidEdge.face(roles, LiquidRole.Bar)
            val ground = roles.ground.withAlpha(1f)
            val under = LiquidEdge.filtered(lerp(ground, roles.text1.withAlpha(1f), share), recipe)
            // Clear glass (owner 2026-11-10): the bar's face is one thin tint; its title keeps LiquidEdge.MOVING_FLOOR
            // over the densest blurred text, and the text floor at rest (LiquidEdgeTest).
            val ink = LiquidEdge.inks(roles, LiquidRole.Bar).first().first
            for (fill in listOfNotNull(face.fill, face.fillStart)) {
                val c = wcagContrast(ink, fill.over(under))
                assertTrue(c >= LiquidEdge.MOVING_FLOOR - 1e-3, "$pack dark=$darkMode: bar words at $c over moving text, floor ${LiquidEdge.MOVING_FLOOR}")
            }
        }
    }

    /** The densest share of ink a Body paragraph keeps under the bar's blur. */
    private fun blurredInkShare(): Float {
        val words =
            "Paragraph 19: the build finished and every check passed; the next step reads the log, compares the timings " +
                "with yesterday's and writes what changed into the notes, so a crash between them never loses a record."
        lateinit var image: PixelMap
        val w = 360
        val h = 200
        runDesktopComposeUiTest((w * PHONE_D).toInt(), (h * PHONE_D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(PHONE_D)) {
                    KitTheme(appearance = KitAppearance(mode = AppearanceMode.Light), platform = FakeKitPlatform(), localeTag = "en", stillFrame = true) {
                        Box(Modifier.size(w.dp, h.dp).background(Color.White).testTag("words")) {
                            KitText(words + " " + words, Modifier.padding(16.dp), role = TypeRole.Body)
                        }
                    }
                }
            }
            waitForIdle()
            image = onNodeWithTag("words").captureToImage().toPixelMap()
        }
        // Ink: how far each pixel is from the white page (dark words on white).
        val ink = Array(image.height) { y -> FloatArray(image.width) { x -> 1f - luma(image[x, y]) / 255f } }
        // The lens's blur radius as Android takes it (sigma = radius × 0.577 + 0.5 px), the smaller and so the worse.
        val sigma = LiquidEdge.recipe(LiquidRole.Bar, true).blurDp * PHONE_D * 0.57735f + .5f
        val blurred = gaussian(ink, sigma)
        // Inside the paragraph, away from its margins.
        val m = (24 * PHONE_D).toInt()
        var best = 0f
        for (y in m until image.height - m) for (x in m until image.width - m) best = maxOf(best, blurred[y][x])
        return best
    }

    private fun gaussian(src: Array<FloatArray>, sigma: Float): Array<FloatArray> {
        val r = (sigma * 3).toInt()
        val k = FloatArray(2 * r + 1) { i -> exp(-((i - r) * (i - r)) / (2 * sigma * sigma)) }
        val sum = k.sum()
        for (i in k.indices) k[i] /= sum
        val h = src.size
        val w = src[0].size
        val tmp = Array(h) { FloatArray(w) }
        for (y in 0 until h) for (x in 0 until w) {
            var a = 0f
            for (i in -r..r) a += k[i + r] * src[y][(x + i).coerceIn(0, w - 1)]
            tmp[y][x] = a
        }
        val out = Array(h) { FloatArray(w) }
        for (y in 0 until h) for (x in 0 until w) {
            var a = 0f
            for (i in -r..r) a += k[i + r] * tmp[(y + i).coerceIn(0, h - 1)][x]
            out[y][x] = a
        }
        return out
    }

    private companion object {
        const val W = 240
        const val H = 480
        const val D = 2f
        const val PHONE_D = 2.75f
        const val STATUS_DP = 24
        const val NAV_DP = 16
        const val BAR_DP = 56
        const val ROW_DP = 32
        const val LENS_BLUR_DP = 6f

        /** Where nothing of the content may show: at most this brightness of 255 against the scene with no rows. */
        const val CLEAR_MAX = 3f

        /** The control floating under the bar: its gap below the bar, its size (dp) and its colour. */
        const val PILL_GAP_DP = 8
        const val PILL_W_DP = 80
        const val PILL_H_DP = 24
        val PILL = Color(0xFFE0402A)
    }
}
