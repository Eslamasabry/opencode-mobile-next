@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidHold
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Independent review 2026-11-13, item 9: behind a foreground decision (a permission sheet, a context menu) the page
 * showed as another interface: a green Allow button glowing as a button behind the sheet's "Copy what it asks to run",
 * the list's rows blurred behind the menu's own labels. Behind a sheet or a menu the page must read as soft light and
 * colour, never as shapes, and the words on the pane must still read over the worst of it.
 *
 * The REAL menu and sheet faces (`kitLiquidMenuFace`, `kitLiquidSheetFace`: their claim, spec and recipe) over a
 * recorded page, drawn through the desktop stand-in of the plain liquid pane ([kitLiquidPlainStandIn]: colour controls,
 * blur, the hold, the tint, the rim light; no bend), Glass dark and light, at the phone's 2.625 px/dp. The page holds
 * the theme's own accent as a 160 x 48 dp button, or rows of the theme's first ink as lines of words. Measured inside the
 * pane, clear of the rim (where the lens shows the page on purpose):
 * (a) the ghost: the picture less the same pane over the empty page, and how far that difference changes across
 *     [STEP_DP] (a hidden edge seen through the pane) in luminance and in colour;
 * (b) the words: the contrast of the theme's first and second ink against every pixel of the pane.
 * Renders (the pane with a line of words on it, before and after) when `GHOST_RENDER_OUT` names a folder.
 */
class ForegroundGhostTest {
    private enum class Kind { Menu, Sheet }

    private enum class Content { Empty, Button, Rows }

    /** The recipe before 2026-11-13 (blur 12 dp, the joined bar's knee, colour limit .26, the thin tints): for renders. */
    private val before: (KitLiquidPaneSpec, Kind, Boolean) -> KitLiquidPaneSpec = { s, k, dark ->
        val tint = if (k == Kind.Sheet) (if (dark) .22f else .14f) else (if (dark) .20f else .10f)
        s.copy(recipe = s.recipe.copy(blurDp = 12f, hold = LiquidHold(true, .26f), tint = tint))
    }

    private class LensPlatform(val variant: (KitLiquidPaneSpec) -> KitLiquidPaneSpec) : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float) =
            kitLiquidPlainStandIn(variant(spec), backdrop, shape, this)
    }

    private class Place(val x: Float, val y: Float, val w: Float, val h: Float) {
        val cx get() = x + w / 2f
        val cy get() = y + h / 2f
    }

    /** A menu in the page's upper middle, a sheet across its foot (dp). */
    private fun place(kind: Kind): Place = if (kind == Kind.Menu) Place(60f, 140f, 260f, 190f) else Place(0f, 300f, W, 260f)

    /** The pane's inside clear of its rim, where its words sit (dp): left, top, right, bottom. */
    private fun inner(kind: Kind): FloatArray {
        val p = place(kind)
        val rim = (if (kind == Kind.Menu) 26f else 30f) + 6f
        return floatArrayOf(p.x + rim, p.y + rim, p.x + p.w - rim, p.y + p.h - rim)
    }

    private class Inks(var text1: Color = Color.Unspecified, var text2: Color = Color.Unspecified)

    @Composable
    private fun Scene(kind: Kind, content: Content, words: Boolean, inks: Inks) {
        val page = rememberKitLiquidSource()
        val colors = LocalKitColors.current
        inks.text1 = colors.text1
        inks.text2 = colors.text2
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.size(W.dp, H.dp).background(colors.ground).testTag("scene")) {
                val p = place(kind)
                Box(
                    Modifier.fillMaxSize().kitLiquidSource(page).drawBehind {
                        val d = density
                        when (content) {
                            Content.Empty -> Unit
                            // The theme's accent button (the card's Allow) straight behind the pane's words.
                            Content.Button ->
                                drawRoundRect(colors.accent, Offset((p.cx - 80f) * d, (p.cy - 24f) * d), Size(160f * d, 48f * d), CornerRadius(24f * d))
                            // Lines of words in the first ink: glyph strokes 2.2 dp wide every 5 dp, 14 dp tall, 24 dp apart.
                            Content.Rows ->
                                for (line in -3..3) {
                                    var x = 8f
                                    val y = p.cy + line * 24f
                                    while (x < W - 8f) {
                                        drawRect(colors.text1, Offset(x * d, (y - 7f) * d), Size(2.2f * d, 14f * d))
                                        x += 5f
                                    }
                                }
                        }
                    },
                )
                val shape = RoundedCornerShape(28.dp)
                val face = if (kind == Kind.Menu) kitLiquidMenuFace(shape, { 0f }) else kitLiquidSheetFace(shape, { 0f })
                Box(Modifier.offset(p.x.dp, p.y.dp).size(p.w.dp, p.h.dp).then(face ?: Modifier.background(Color.Magenta)).testTag("pane")) {
                    if (words) {
                        CompositionLocalProvider(LocalKitOnLiquidGlass provides true) {
                            KitText("Copy what it asks to run", Modifier.align(Alignment.Center).padding(horizontal = 40.dp))
                        }
                    }
                }
            }
        }
    }

    private fun shot(kind: Kind, content: Content, dark: Boolean, words: Boolean = false, old: Boolean = false, inks: Inks = Inks()): PixelMap =
        picture(kind, content, dark, words, old, inks).toPixelMap()

    private fun picture(kind: Kind, content: Content, dark: Boolean, words: Boolean, old: Boolean, inks: Inks = Inks()): ImageBitmap {
        lateinit var out: ImageBitmap
        val platform = LensPlatform { if (old) before(it, kind, dark) else it }
        runDesktopComposeUiTest((W * D).roundToInt(), (H * D).roundToInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        appearance = KitAppearance(style = Style.Glass, mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light),
                        platform = platform,
                        localeTag = "en",
                        stillFrame = true,
                    ) { Scene(kind, content, words, inks) }
                }
            }
            waitForIdle()
            out = onNodeWithTag("scene").captureToImage()
        }
        return out
    }

    private fun lum(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    /** The largest change, across [STEP_DP] across and down the pane's inside, of the picture less the empty one. */
    private fun ghost(img: PixelMap, empty: PixelMap, kind: Kind): Pair<Float, Float> {
        val r = inner(kind)
        val step = (STEP_DP * D).roundToInt()
        val l = (r[0] * D).roundToInt()
        val t = (r[1] * D).roundToInt()
        val right = (r[2] * D).roundToInt()
        val bottom = (r[3] * D).roundToInt()
        fun diff(x: Int, y: Int): FloatArray {
            val a = img[x, y]
            val b = empty[x, y]
            return floatArrayOf((a.red - b.red) * 255f, (a.green - b.green) * 255f, (a.blue - b.blue) * 255f)
        }
        var luma = 0f
        var colour = 0f
        for (y in t until bottom step 2) for (x in l until right step 2) {
            val here = diff(x, y)
            for ((nx, ny) in listOf(x + step to y, x to y + step)) {
                if (nx >= right || ny >= bottom) continue
                val there = diff(nx, ny)
                luma = max(luma, abs((0.2126f * (here[0] - there[0]) + 0.7152f * (here[1] - there[1]) + 0.0722f * (here[2] - there[2]))))
                colour = max(colour, sqrt((0 until 3).sumOf { ((here[it] - there[it]) * (here[it] - there[it])).toDouble() }.toFloat()))
            }
        }
        return luma to colour
    }

    private fun linear(v: Float): Double = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)

    private fun relative(c: Color): Double = .2126 * linear(c.red) + .7152 * linear(c.green) + .0722 * linear(c.blue)

    private fun contrast(a: Color, b: Color): Double = (max(relative(a), relative(b)) + .05) / (min(relative(a), relative(b)) + .05)

    /** The least contrast [ink] keeps against any pixel of the pane's inside. */
    private fun worst(img: PixelMap, kind: Kind, ink: Color): Double {
        val r = inner(kind)
        var least = 21.0
        for (y in (r[1] * D).roundToInt() until (r[3] * D).roundToInt() step 2) for (x in (r[0] * D).roundToInt() until (r[2] * D).roundToInt() step 2) {
            least = min(least, contrast(ink, img[x, y]))
        }
        return least
    }

    @Test
    fun `foreground ghosts - a sheet and a menu show a bright button and lines of words behind them as light, not as shapes`() {
        val failures = mutableListOf<String>()
        for (kind in Kind.entries) for (dark in listOf(true, false)) {
            val empty = shot(kind, Content.Empty, dark)
            for (content in listOf(Content.Button, Content.Rows)) {
                val (luma, colour) = ghost(shot(kind, content, dark), empty, kind)
                val line = "$kind ${if (dark) "dark" else "light"} over $content: a hidden edge steps %.1f in light and %.1f in colour (of 255) across %.0f dp".format(luma, colour, STEP_DP)
                println("GHOST $line")
                if (luma > MAX_LIGHT_STEP || colour > MAX_COLOUR_STEP) failures += line
            }
        }
        assertTrue(failures.isEmpty(), "Shapes behind a foreground pane (at most $MAX_LIGHT_STEP in light, $MAX_COLOUR_STEP in colour):\n" + failures.joinToString("\n"))
    }

    @Test
    fun `foreground words - the first and second ink read on a sheet and a menu over the theme's accent button`() {
        val failures = mutableListOf<String>()
        for (kind in Kind.entries) for (dark in listOf(true, false)) {
            val inks = Inks()
            val img = shot(kind, Content.Button, dark, inks = inks)
            for ((name, ink) in listOf("first ink" to inks.text1, "second ink" to inks.text2)) {
                val least = worst(img, kind, ink)
                val line = "$kind ${if (dark) "dark" else "light"} $name over the accent button: %.2f".format(least)
                println("GHOST WORDS $line")
                if (least < MIN_CONTRAST) failures += line
            }
        }
        assertTrue(failures.isEmpty(), "Words below $MIN_CONTRAST:1 on a foreground pane:\n" + failures.joinToString("\n"))
    }

    /** Not a test: pictures for the eye when `GHOST_RENDER_OUT` names a folder. */
    @Test
    fun `foreground ghost renders`() {
        val out = System.getenv("GHOST_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        for (kind in Kind.entries) for (dark in listOf(true, false)) for (content in listOf(Content.Button, Content.Rows)) for (old in listOf(true, false)) {
            val img = picture(kind, content, dark, words = true, old = old)
            val name = "ghost-${kind.name.lowercase()}-${content.name.lowercase()}-glass-${if (dark) "dark" else "light"}-${if (old) "before" else "after"}.png"
            ImageIO.write(img.toAwtImage(), "png", File(out, name))
        }
    }

    private companion object {
        /** The phone: 1080 px wide at 2.625 px/dp. */
        const val D = 2.625f
        const val W = 1080f / D
        const val H = 640f

        /** The distance across which a hidden edge is measured: about a third of a button's height. */
        const val STEP_DP = 12f

        /**
         * The most a hidden edge may step through a foreground pane, of 255: under 5 % in light (about a soft glow's
         * gradient; a sharp button edge seen through the old recipe stepped 60) and 18 in colour.
         */
        const val MAX_LIGHT_STEP = 12f
        const val MAX_COLOUR_STEP = 18f

        /** Body words: WCAG AA. */
        const val MIN_CONTRAST = 4.5
    }
}
