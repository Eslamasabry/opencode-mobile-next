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
 * Owner's pick 2026-11-09, chat edge option A ("the text scrolls under the status bar and blurs through the glass
 * header, so the screen reads as one surface"; mockups in Storage2/mockups/2026-11-09-chat-edge): an edge-to-edge
 * chat frame. At rest its first row lies wholly below the bar; scrolled, the content runs on behind the status bar
 * and the bar: blurred there in Glass (liquid and frosted), under the page's ground with a soft fade in the other
 * styles, and that ground only while something lies under it.
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

    /** A chat frame: [rows] bright rows ([gap] dp apart) in a list scrolled to [first], a 56 dp bar, a 96 dp block. */
    @Composable
    private fun Scene(rows: Int, first: Int, gap: Dp) {
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
                    LazyColumn(
                        state = list,
                        contentPadding = kitScreenChromePadding(),
                        modifier = Modifier.fillMaxSize().kitFrostSource(source).testTag("list"),
                    ) {
                        items(rows) { i ->
                            Box(Modifier.fillMaxWidth().height(ROW_DP.dp).padding(bottom = gap).background(Color.White).testTag("row-$i"))
                        }
                    }
                }
            }
        }
    }

    private class Shot(val image: PixelMap, val bar: androidx.compose.ui.unit.DpRect, val row0: androidx.compose.ui.unit.DpRect?)

    private fun shot(appearance: KitAppearance, platform: KitPlatform, rows: Int = 60, first: Int = 0, gap: Dp = 8.dp, ground: ((Color) -> Color)? = null): Shot {
        lateinit var shot: Shot
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                        if (ground == null) Scene(rows, first, gap)
                        else {
                            // A busy picture behind a see-through ground, for "no band at rest".
                            Box(Modifier.size(W.dp, H.dp).drawBehind { stripes() }) {
                                val colors = LocalKitColors.current
                                CompositionLocalProvider(LocalKitColors provides colors.copy(ground = ground(colors.ground))) {
                                    Scene(rows, first, gap)
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

    /** Brightness down column [xDp] from [fromDp] to [toDp]: the mean, the spread and the steepest row-to-row step. */
    private fun column(image: PixelMap, xDp: Float, fromDp: Float, toDp: Float): Triple<Float, Float, Float> {
        val x = (xDp * D).roundToInt().coerceIn(0, image.width - 1)
        val ys = ((fromDp * D).roundToInt() until (toDp * D).roundToInt()).filter { it in 0 until image.height }
        val l = ys.map { luma(image[x, it]) }
        val step = l.zipWithNext { a, b -> abs(a - b) }.maxOrNull() ?: 0f
        return Triple(l.average().toFloat(), (l.maxOrNull() ?: 0f) - (l.minOrNull() ?: 0f), step)
    }

    private fun dark(style: Style, look: GlassLookId = GlassLookId.Glossy) =
        KitAppearance(style = style, mode = AppearanceMode.Dark, glassLook = look)

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

    @Test
    fun `E20-S30 scrolled under liquid and frosted Glass the content blurs behind the status bar and the bar`() {
        for ((what, appearance) in listOf("liquid" to dark(Style.Glass), "frosted" to dark(Style.Glass, GlassLookId.Frosted))) {
            val s = shot(appearance, LensPlatform(), first = 8)
            val ground = luma(s.image[(W * D / 2).toInt(), ((H - NAV_DP - 2) * D).toInt()])
            // Under the status bar, where it is all blur: the rows show, as a blur (no sharp edge between a row and its gap).
            val (statusMean, _, statusStep) = column(s.image, W / 2f, 1f, STATUS_DP * STATUS_EDGE_SOLID_SHARE - 1f)
            assertTrue(statusMean >= ground + 25f, "$what: content under the status bar, mean $statusMean over ground $ground")
            assertTrue(statusStep <= BLUR_STEP, "$what: blurred under the status bar, a step of $statusStep")
            println("$what: status mean $statusMean step $statusStep, ground $ground")
            // In the bar's capsule: the rows show through its glass, blurred.
            val barFrom = STATUS_DP + 10f
            val barTo = STATUS_DP + BAR_DP - 10f
            val (capsuleMean, _, capsuleStep) = column(s.image, W / 2f, barFrom, barTo)
            assertTrue(capsuleMean >= ground + 15f, "$what: content behind the bar's glass, mean $capsuleMean over ground $ground")
            assertTrue(capsuleStep <= BLUR_STEP, "$what: blurred behind the bar's glass, a step of $capsuleStep")
            println("$what: capsule mean $capsuleMean step $capsuleStep")
            // Beside the capsule (its 6 dp margin) the rows run on as they are: one surface, nothing cleared.
            val (_, sideSpread, _) = column(s.image, 2f, barFrom, barTo)
            assertTrue(sideSpread >= 120f, "$what: the rows run on beside the bar, spread $sideSpread")
        }
    }

    @Test
    fun `E20-S30 scrolled in Solid the page's ground runs through the status bar and the bar and fades out below it`() {
        val s = shot(dark(Style.Solid), FakeKitPlatform(), first = 8, gap = 0.dp)
        val x = (W * D / 2).toInt()
        val ground = luma(s.image[x, ((H - NAV_DP - 2) * D).toInt()])
        // Behind the status bar and the bar: the ground, the rows hidden.
        for (y in 0 until ((STATUS_DP + BAR_DP - 1) * D).toInt()) {
            val l = luma(s.image[x, y])
            assertTrue(abs(l - ground) <= 3f, "Solid: the ground behind the chrome at y=${y / D} dp, was $l over $ground")
        }
        // The 16 dp fade below it: the rows half there in its middle, whole past its end.
        val mid = luma(s.image[x, ((STATUS_DP + BAR_DP + 8) * D).toInt()])
        assertTrue(mid > ground + 20f && mid < 235f, "Solid: the band fades out over 16 dp, its middle was $mid")
        val past = luma(s.image[x, ((STATUS_DP + BAR_DP + 20) * D).toInt()])
        assertTrue(past >= 240f, "Solid: the rows are whole past the fade, was $past")
    }

    @Test
    fun `E20-S30 scrolled in Solid the ground lies over a live picture behind the chrome, not the picture alone`() {
        // A see-through ground over a busy picture stands in for the live background: scrolled, something (the band of
        // ground) lies over the picture behind the status bar and the bar, where a cleared body would show it bare.
        val s = shot(dark(Style.Solid), FakeKitPlatform(), first = 8, gap = 0.dp, ground = { it.copy(alpha = .6f) })
        val bare = ((H - NAV_DP / 2) * D).toInt()
        var same = 0
        var seen = 0
        for (y in 0 until ((STATUS_DP + BAR_DP - 1) * D).toInt() step 3)
            for (x in 0 until s.image.width step 5) {
                seen++
                if (abs(luma(s.image[x, y]) - luma(s.image[x, bare])) <= 4f) same++
            }
        assertTrue(same * 10 < seen, "Solid scrolled: $same of $seen points behind the chrome show the bare picture")
    }

    @Test
    fun `E20-S30 at rest in Solid no band lies over a live picture`() {
        // Nothing under the chrome (no rows): the picture shows the same at the top as further down, in every column.
        val s = shot(dark(Style.Solid), FakeKitPlatform(), rows = 0, ground = { it.copy(alpha = .6f) })
        val far = ((H / 2) * D).toInt()
        for (y in 0 until ((STATUS_DP + BAR_DP + GROUND_BAND_FADE_DP) * D).toInt() step 3)
            for (x in 0 until s.image.width step 5) {
                val d = abs(luma(s.image[x, y]) - luma(s.image[x, far]))
                assertTrue(d <= 2f, "Solid at rest: a band at (${x / D}, ${y / D}) dp, $d of 255 off the picture")
            }
    }

    @Test
    fun `B-KIT-003 the bar's words keep their floor over the transcript blurred under it`() {
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
            for (fill in listOfNotNull(face.fill, face.fillStart)) {
                val bare = fill.over(under)
                for (seen in listOf(bare, face.sheen.over(bare)))
                    for ((ink, target) in LiquidEdge.inks(roles, LiquidRole.Bar)) {
                        val c = wcagContrast(ink, seen)
                        assertTrue(c >= target - 1e-3, "$pack dark=$darkMode: bar words at $c over moving text, floor $target")
                    }
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

        /** The most brightness may step between two rows where the content is blurred (a sharp row edge is ~200). */
        const val BLUR_STEP = 24f
    }
}
