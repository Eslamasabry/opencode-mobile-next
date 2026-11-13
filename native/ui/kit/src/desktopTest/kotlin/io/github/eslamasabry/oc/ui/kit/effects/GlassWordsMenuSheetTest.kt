@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidHold
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Words on glass 3 (owner 2026-11-11, device sweep): behind the words of a menu and of a sheet nothing may read as a
 * shape, a line of text or a bright or coloured blob, and the rim still bends real content. Every measure is taken on
 * the REAL menu/sheet face (`kitLiquidMenuFace`, `kitLiquidSheetFace`: the claim, the spec, the recipe) over a recorded
 * page, drawn through the desktop stand-in of the plain liquid pane ([kitLiquidPlainStandIn]: colour controls, blur,
 * the hold, the tint; no bend, no rim light). "Before" is the same pane with the old recipe (the menu's 6 dp dark / 3 dp
 * light blur and no hold). Numbers are printed so a run's log carries them.
 */
class GlassWordsMenuSheetTest {
    private enum class Kind { Menu, Sheet }

    private enum class Content { Empty, TextLine, DarkBubble, BrightCard, Green, Orange, AppGreen }

    /** Which recipe the pane wears: the old one, the knee alone, the knee with saturation 1.0 (a), or the shipped one (b). */
    private enum class Variant(val spec: (KitLiquidPaneSpec, Kind, Boolean) -> KitLiquidPaneSpec) {
        Before({ s, k, dark -> s.copy(recipe = s.recipe.copy(blurDp = if (k == Kind.Menu) (if (dark) 6f else 3f) else 12f, hold = LiquidHold.Off)) }),
        // The alternatives weighed on 2026-11-11, at that day's 12 dp frost and the joined bar's knee.
        KneeOnly({ s, _, _ -> s.copy(recipe = s.recipe.copy(blurDp = 12f, hold = LiquidHold(true, 0f))) }),
        SaturationOne({ s, _, _ -> s.copy(recipe = s.recipe.copy(blurDp = 12f, saturation = 1f, hold = LiquidHold(true, 0f))) }),
        Shipped({ s, _, _ -> s }),
    }

    private class LensPlatform(val variant: (KitLiquidPaneSpec) -> KitLiquidPaneSpec) : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float) =
            kitLiquidPlainStandIn(variant(spec), backdrop, shape, this)
    }

    /** A platform with no lens, whose `liquidPane` must never be asked. */
    private class NoLensPlatform(val asked: MutableList<String>) : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = false

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            asked += "asked"
            return Modifier
        }
    }

    private fun appearance(dark: Boolean, style: Style = Style.Glass, look: GlassLookId = GlassLookId.Glossy) =
        KitAppearance(style = style, mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, glassLook = look)

    /** The pane's place, dp: a menu in the page's upper middle, a sheet at its foot. */
    private fun place(kind: Kind): Place = if (kind == Kind.Menu) Place(60f, 110f, 240f, 170f) else Place(0f, 250f, W, 230f)

    private class Place(val x: Float, val y: Float, val w: Float, val h: Float) {
        val cx get() = x + w / 2f
        val cy get() = y + h / 2f
    }

    private fun DrawScope.circle(color: Color, cxDp: Float, cyDp: Float, rDp: Float) =
        drawCircle(color, rDp * density, Offset(cxDp * density, cyDp * density))

    /** What the page shows behind the pane: [content] around the pane's centre, as device pictures showed. */
    private fun DrawScope.drawPage(content: Content, p: Place, dark: Boolean) {
        val d = density
        fun rect(color: Color, l: Float, t: Float, r: Float, b: Float, round: Float = 0f) =
            drawRoundRect(color, Offset(l * d, t * d), Size((r - l) * d, (b - t) * d), CornerRadius(round * d))
        when (content) {
            Content.Empty -> Unit
            // A line of white text: vertical bars 2.2 dp wide every 5 dp, 14 dp tall, with a line above and below.
            Content.TextLine ->
                for (line in listOf(-24f, 0f, 24f)) {
                    var x = p.cx - 110f
                    while (x < p.cx + 110f) {
                        rect(if (dark) Color.White else Color.Black, x, p.cy + line - 7f, x + 2.2f, p.cy + line + 7f)
                        x += 5f
                    }
                }
            // A user bubble on a mid ground, running to the screen edge (conv-menu2).
            Content.DarkBubble -> {
                rect(if (dark) Color(0xFF3A3D45) else Color(0xFFC9CCD4), 0f, p.y - 10f, W, p.y + p.h + 10f)
                rect(if (dark) Color(0xFF0A0B0E) else Color(0xFF2A2C33), p.cx - 40f, p.cy - 20f, W, p.cy + 20f, round = 16f)
            }
            Content.BrightCard -> rect(if (dark) Color.White else Color(0xFF0A0A0A), p.cx - 75f, p.cy - 30f, p.cx + 35f, p.cy + 30f, round = 12f)
            Content.Green -> circle(Color(0f, 1f, 0f), p.cx - 30f, p.cy, 28f)
            Content.Orange -> circle(Color(1f, .5f, 0f), p.cx - 30f, p.cy, 28f)
            Content.AppGreen -> circle(Color(0xFF34C759), p.cx - 30f, p.cy, 28f)
        }
    }

    @Composable
    private fun Scene(kind: Kind, content: Content, dark: Boolean, rimBlock: Boolean = false) {
        val page = rememberKitLiquidSource()
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.size(W.dp, H.dp).background(io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.ground).testTag("scene")) {
                val p = place(kind)
                Box(Modifier.fillMaxSize().kitLiquidSource(page).drawBehind { drawPage(content, p, dark); if (rimBlock) rimContent(p, content) })
                val shape = RoundedCornerShape(28.dp)
                val face = if (kind == Kind.Menu) kitLiquidMenuFace(shape, { 0f }) else kitLiquidSheetFace(shape, { 0f })
                Box(Modifier.offset(p.x.dp, p.y.dp).size(p.w.dp, p.h.dp).then(face ?: Modifier.background(Color.Magenta)).testTag("pane"))
            }
        }
    }

    /** A wide block of the content straddling the pane's left edge: what the rim shows. */
    private fun DrawScope.rimContent(p: Place, content: Content) {
        val d = density
        val color =
            when (content) {
                Content.Green -> Color(0f, 1f, 0f)
                Content.Orange -> Color(1f, .5f, 0f)
                else -> Color.White
            }
        drawRect(color, Offset((p.x - 30f) * d, (p.y + p.h / 2f - 40f) * d), Size(60f * d, 80f * d))
    }

    private fun shot(kind: Kind, content: Content, variant: Variant, dark: Boolean = true, rimBlock: Boolean = false): PixelMap {
        lateinit var out: PixelMap
        val platform = LensPlatform({ variant.spec(it, kind, dark) })
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = appearance(dark), platform = platform, localeTag = "en", stillFrame = true) { Scene(kind, content, dark, rimBlock) }
                }
            }
            waitForIdle()
            out = onNodeWithTag("scene").captureToImage().toPixelMap()
        }
        return out
    }

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    private fun chroma(c: Color): Float = (max(c.red, max(c.green, c.blue)) - min(c.red, min(c.green, c.blue))) * 255f

    /** What a region holds: its contrast (peak to peak luma), its peak luma, its peak chroma, all of 255. */
    private class Measure(val contrast: Float, val peakLuma: Float, val peakChroma: Float, val meanLuma: Float, val edge: Float, val meanChroma: Float) {
        override fun toString() = "contrast %.1f edge %.1f peak %.1f chroma %.1f (mean %.1f) mean %.1f".format(contrast, edge, peakLuma, peakChroma, meanChroma, meanLuma)
    }

    private fun measure(img: PixelMap, l: Float, t: Float, r: Float, b: Float): Measure {
        var lo = 255f
        var hi = 0f
        var ch = 0f
        var sum = 0f
        var chSum = 0f
        var edge = 0f
        var n = 0
        val step = (EDGE_DP * D).roundToInt()
        for (y in (t * D).roundToInt() until (b * D).roundToInt()) for (x in (l * D).roundToInt() until (r * D).roundToInt()) {
            val c = img[x, y]
            val v = luma(c)
            lo = min(lo, v)
            hi = max(hi, v)
            ch = max(ch, chroma(c))
            sum += v
            chSum += chroma(c)
            if (x + step < (r * D).roundToInt()) edge = max(edge, abs(v - luma(img[x + step, y])))
            n++
        }
        return Measure(hi - lo, hi, ch, sum / max(n, 1), edge, chSum / max(n, 1))
    }

    /** The pane's text band: a row of words in its middle, clear of the rim. */
    private fun band(kind: Kind): FloatArray = place(kind).let { floatArrayOf(it.cx - 90f, it.cy - 14f, it.cx + 90f, it.cy + 14f) }

    private fun band(img: PixelMap, kind: Kind): Measure = band(kind).let { measure(img, it[0], it[1], it[2], it[3]) }

    /** The largest per-pixel difference in brightness between [a] and [b] over the band. */
    private fun peakDiff(a: PixelMap, b: PixelMap, kind: Kind): Float {
        val r = band(kind)
        var m = 0f
        for (y in (r[1] * D).roundToInt() until (r[3] * D).roundToInt()) for (x in (r[0] * D).roundToInt() until (r[2] * D).roundToInt()) m = max(m, abs(luma(a[x, y]) - luma(b[x, y])))
        return m
    }

    private data class Row(val label: String, val m: Measure, val chromaOver: Float, val diff: Float, val blob: Float)

    /** The mean chroma over the circle's core (40 dp wide inside the 56 dp circle) across the words' band. */
    private fun blobChroma(img: PixelMap, kind: Kind): Float =
        place(kind).let { measure(img, it.cx - 30f - 20f, it.cy - 14f, it.cx - 30f + 20f, it.cy + 14f).meanChroma }

    private fun table(kind: Kind, dark: Boolean, contents: List<Content>, variants: List<Variant>): Map<Pair<Content, Variant>, Row> {
        val rows = mutableMapOf<Pair<Content, Variant>, Row>()
        for (v in variants) {
            val empty = shot(kind, Content.Empty, v, dark)
            val base = band(empty, kind)
            val baseBlob = blobChroma(empty, kind)
            for (c in contents) {
                val img = shot(kind, c, v, dark)
                val m = band(img, kind)
                val row = Row("$kind dark=$dark $c / $v", m, m.peakChroma - base.peakChroma, peakDiff(img, empty, kind), blobChroma(img, kind) - baseBlob)
                println("${row.label}: $m; chroma over empty %.1f; blob chroma over empty %.1f; peak difference from empty %.1f".format(row.chromaOver, row.blob, row.diff))
                rows[c to v] = row
            }
        }
        return rows
    }

    private val darkContents = listOf(Content.TextLine, Content.DarkBubble, Content.BrightCard, Content.Green, Content.Orange, Content.AppGreen)

    @Test
    fun `menu - a line of text, a dark bubble, a bright card and a coloured circle behind the words, before and after`() {
        val t = table(Kind.Menu, true, darkContents, Variant.entries)
        for (c in listOf(Content.TextLine, Content.DarkBubble)) {
            val before = t.getValue(c to Variant.Before)
            val after = t.getValue(c to Variant.Shipped)
            println("MENU $c: steepest edge ${before.m.edge} -> ${after.m.edge}; contrast ${before.m.contrast} -> ${after.m.contrast}; peak difference ${before.diff} -> ${after.diff}")
            assertTrue(after.m.edge < before.m.edge, "menu $c: steepest edge ${before.m.edge} -> ${after.m.edge}")
            assertTrue(after.diff <= before.diff, "menu $c: peak ${before.diff} -> ${after.diff}")
        }
        val card = t.getValue(Content.BrightCard to Variant.Before) to t.getValue(Content.BrightCard to Variant.Shipped)
        println("MENU BrightCard: peak luma ${card.first.m.peakLuma} -> ${card.second.m.peakLuma}")
        assertTrue(card.second.m.peakLuma < card.first.m.peakLuma, "a bright card is held back: ${card.first.m.peakLuma} -> ${card.second.m.peakLuma}")
    }

    @Test
    fun `sheet - a line of text, a dark bubble, a bright card and a coloured circle behind the words, before and after`() {
        val t = table(Kind.Sheet, true, darkContents, Variant.entries)
        val card = t.getValue(Content.BrightCard to Variant.Before) to t.getValue(Content.BrightCard to Variant.Shipped)
        println("SHEET BrightCard: peak luma ${card.first.m.peakLuma} -> ${card.second.m.peakLuma}")
        assertTrue(card.second.m.peakLuma < card.first.m.peakLuma, "a bright card is held back: ${card.first.m.peakLuma} -> ${card.second.m.peakLuma}")
    }

    @Test
    fun `the colour held back - the chroma under the words is at most a third of today's for a pure green and a pure orange, on a menu and a sheet`() {
        for (kind in Kind.entries) {
            val t = table(kind, true, listOf(Content.Green, Content.Orange, Content.AppGreen), Variant.entries)
            for (c in listOf(Content.Green, Content.Orange)) {
                val today = t.getValue(c to Variant.Before).blob
                val knee = t.getValue(c to Variant.KneeOnly).blob
                val a = t.getValue(c to Variant.SaturationOne).blob
                val b = t.getValue(c to Variant.Shipped).blob
                val peak = t.getValue(c to Variant.Before).chromaOver to t.getValue(c to Variant.Shipped).chromaOver
                println("CHROMA $kind $c (blob core mean): today %.1f, knee only %.1f (%.2f), (a) saturation 1.0 + knee %.1f (%.2f), (b) shipped %.1f (%.2f); band peak today %.1f shipped %.1f".format(today, knee, knee / today, a, a / today, b, b / today, peak.first, peak.second))
                assertTrue(b <= today / 3f, "$kind $c: shipped blob chroma $b is more than a third of today's $today")
                assertTrue(a > today / 3f, "$kind $c: saturation 1.0 with the knee alone already reached a third ($a of $today): the colour hold would not be needed")
            }
        }
    }

    @Test
    fun `the rim still bends real content - a bright block and a green block behind a pane's edge show in its rim`() {
        for (kind in Kind.entries) {
            val p = place(kind)
            for (c in listOf(Content.BrightCard, Content.Green)) {
                val empty = shot(kind, Content.Empty, Variant.Shipped)
                val img = shot(kind, c, Variant.Shipped, rimBlock = true)
                // The block lies behind the pane's left edge; its rim band is the first 20 dp inside the edge.
                val left = max(p.x, 0f) + 4f
                val a = measure(img, left, p.cy - 30f, left + 14f, p.cy + 30f)
                val b = measure(empty, left, p.cy - 30f, left + 14f, p.cy + 30f)
                println("RIM $kind $c: with ${a} / without ${b}")
                if (c == Content.Green) assertTrue(a.peakChroma >= 0.3f * 255f / 2f, "$kind: green shows in the rim: chroma ${a.peakChroma}")
                else assertTrue(a.peakLuma - b.peakLuma >= 20f, "$kind: the bright block shows in the rim: ${a.peakLuma} vs ${b.peakLuma}")
            }
        }
    }

    @Test
    fun `the rim keeps its colour - a pane edge over green keeps more chroma than the same pane's middle`() {
        val p = place(Kind.Sheet)
        // The block lies behind the pane's left edge (rimContent): the rim band keeps colour, the middle of the pane
        // (a circle there) is held to a third of today's.
        val rim = shot(Kind.Sheet, Content.Green, Variant.Shipped, rimBlock = true)
        val left = max(p.x, 0f) + 4f
        val inRim = measure(rim, left, p.cy - 30f, left + 14f, p.cy + 30f)
        val middle = blobChroma(shot(Kind.Sheet, Content.Green, Variant.Shipped), Kind.Sheet)
        println("RIM COLOUR: rim peak ${inRim.peakChroma} (mean ${inRim.meanChroma}), middle core mean $middle")
        assertTrue(inRim.peakChroma > 2f * middle, "the rim keeps more colour than the words' middle: ${inRim.peakChroma} vs $middle")
    }

    @Test
    fun `light theme - a dark bubble behind a menu or a sheet is lifted, not left a dark smear`() {
        for (kind in Kind.entries) {
            val t = table(kind, false, listOf(Content.DarkBubble, Content.TextLine), listOf(Variant.Before, Variant.Shipped))
            val b = t.getValue(Content.DarkBubble to Variant.Before).m
            val a = t.getValue(Content.DarkBubble to Variant.Shipped).m
            println("LIGHT $kind DarkBubble: contrast ${b.contrast} -> ${a.contrast}")
            assertTrue(a.contrast <= b.contrast, "$kind light: bubble contrast ${b.contrast} -> ${a.contrast}")
        }
    }

    @Test
    fun `a joined menu's frost moves from the bar's to the menu's as its rows come in, with no step`() {
        for (dark in listOf(true, false)) {
            val bar = LiquidEdge.recipe(LiquidRole.Bar, dark)
            val menu = LiquidEdge.recipe(LiquidRole.Menu, dark)
            assertEquals(bar, joinedFrost(bar, menu, 0f), "no rows yet: the bar's frost (dark=$dark)")
            assertEquals(bar.copy(blurDp = LiquidEdge.DECISION_BLUR_DP), joinedFrost(bar, menu, 1f), "rows in: the menu's blur, nothing else of the bar's moves (dark=$dark)")
            assertEquals(joinedFrost(bar, menu, 1f), joinedFrost(bar, menu, 1.7f), "clamped")
            var last = joinedFrost(bar, menu, 0f).blurDp
            for (i in 1..100) {
                val now = joinedFrost(bar, menu, i / 100f).blurDp
                assertTrue(now >= last && now - last <= .3f, "a step at ${i / 100f}: $last to $now")
                last = now
            }
        }
    }

    @Test
    fun `the other looks are not touched - no lens pane is asked outside Glass with the lens`() {
        val asked = mutableListOf<String>()
        val cases =
            Style.entries.filter { it != Style.Glass }.map { "$it" to appearance(true, it) } +
                listOf("Glass Frosted" to appearance(true, Style.Glass, GlassLookId.Frosted))
        for ((what, appearance) in cases) {
            val lens = LensPlatform({ asked += "lens pane for $what"; it })
            runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
                setContent { CompositionLocalProvider(LocalDensity provides Density(D)) { KitTheme(appearance = appearance, platform = lens, localeTag = "en", stillFrame = true) { Scene(Kind.Menu, Content.TextLine, true) } } }
                waitForIdle()
            }
        }
        val noLens = mutableListOf<String>()
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent { CompositionLocalProvider(LocalDensity provides Density(D)) { KitTheme(appearance = appearance(true), platform = NoLensPlatform(noLens), localeTag = "en", stillFrame = true) { Scene(Kind.Sheet, Content.TextLine, true) } } }
            waitForIdle()
        }
        assertEquals(emptyList(), asked, "no style but Glass with the lens asks for a liquid pane")
        assertEquals(emptyList(), noLens, "Glass with no lens asks for none")
    }

    @Test
    fun `the specs the real menu and sheet faces hand the platform carry the recipe - 12 dp and the hold`() {
        for (dark in listOf(true, false)) for (kind in Kind.entries) {
            val seen = mutableListOf<KitLiquidPaneSpec>()
            val platform = LensPlatform({ seen += it; it })
            runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
                setContent { CompositionLocalProvider(LocalDensity provides Density(D)) { KitTheme(appearance = appearance(dark), platform = platform, localeTag = "en", stillFrame = true) { Scene(kind, Content.TextLine, dark) } } }
                waitForIdle()
            }
            assertTrue(seen.isNotEmpty(), "$kind dark=$dark: the face asked the platform")
            val role = if (kind == Kind.Menu) LiquidRole.Menu else LiquidRole.Sheet
            val want = LiquidEdge.recipe(role, dark)
            assertEquals(LiquidEdge.DECISION_BLUR_DP, seen.last().recipe.blurDp, "$kind dark=$dark blur")
            assertEquals(want.hold, seen.last().recipe.hold, "$kind dark=$dark hold")
            assertTrue(seen.last().recipe.hold.knee && seen.last().recipe.hold.chroma > 0f)
        }
    }

    @Test
    fun `renders - the menu and the sheet over a text line, a dark bubble and a green circle, before and after`() {
        for (kind in Kind.entries) for (v in listOf(Variant.Before, Variant.Shipped)) {
            val img = shotAll(kind, v)
            val name = "render-words3-${kind.name.lowercase()}-${if (v == Variant.Before) "before" else "after"}.png"
            val bytes = ByteArrayOutputStream().also { ImageIO.write(img.toAwtImage(), "png", it) }.toByteArray()
            val text = Base64.getEncoder().encodeToString(bytes)
            text.chunked(1500).forEachIndexed { i, part -> println("PNGB64 $name $i $part") }
        }
    }

    /** The whole composite picture: the three kinds of content around the pane's words. */
    private fun shotAll(kind: Kind, variant: Variant): androidx.compose.ui.graphics.ImageBitmap {
        lateinit var out: androidx.compose.ui.graphics.ImageBitmap
        val platform = LensPlatform({ variant.spec(it, kind, true) })
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = appearance(true), platform = platform, localeTag = "en", stillFrame = true) { SceneAll(kind) }
                }
            }
            waitForIdle()
            out = onNodeWithTag("scene").captureToImage()
        }
        return out
    }

    @Composable
    private fun SceneAll(kind: Kind) {
        val page = rememberKitLiquidSource()
        CompositionLocalProvider(LocalKitLiquidPage provides page) {
            Box(Modifier.size(W.dp, H.dp).background(io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.ground).testTag("scene")) {
                val p = place(kind)
                Box(
                    Modifier.fillMaxSize().kitLiquidSource(page).drawBehind {
                        // Stripes of text across the whole page, a bubble, a bright card, a green and an orange circle.
                        drawPage(Content.DarkBubble, p, true)
                        for (row in 0 until 40) {
                            var x = 8f
                            while (x < W - 8f) {
                                drawRect(Color.White.copy(alpha = .85f), Offset(x * density, (row * 12f + 4f) * density), Size(2.2f * density, 8f * density))
                                x += 5f
                            }
                        }
                        drawPage(Content.BrightCard, p, true)
                        circle(Color(0f, 1f, 0f), p.cx - 70f, p.cy + 36f, 20f)
                        circle(Color(1f, .5f, 0f), p.cx + 40f, p.cy + 36f, 20f)
                    },
                )
                val shape = RoundedCornerShape(28.dp)
                val face = if (kind == Kind.Menu) kitLiquidMenuFace(shape, { 0f }) else kitLiquidSheetFace(shape, { 0f })
                Box(Modifier.offset(p.x.dp, p.y.dp).size(p.w.dp, p.h.dp).then(face ?: Modifier.background(Color.Magenta)).testTag("pane"))
            }
        }
    }

    private companion object {
        const val W = 360f
        const val H = 480f
        const val D = 2f

        /** The distance (dp) over which an edge's steepness is measured: a letter's stroke is about this wide. */
        const val EDGE_DP = 4f
    }
}
