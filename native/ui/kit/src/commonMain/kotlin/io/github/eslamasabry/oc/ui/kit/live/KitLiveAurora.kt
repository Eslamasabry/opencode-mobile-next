package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleAurora
import org.jetbrains.compose.resources.StringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Soft ribbons of light in the accent's hue over a faint drifting haze, brighter while a reply runs (Dart
 * `KitLiveAurora`, `styles/kit_live_aurora.dart`): the default look.
 */
public object KitLiveAurora : KitLiveStyle() {
    override val id: String = "aurora"
    override val label: StringResource = Res.string.liveStyleAurora

    /** In a dark theme the ribbons are brighter than text2 and text3 could read on, so those keep a halo of the ground. */
    override fun wantsTextHalo(palette: KitLivePalette): Boolean = palette.dark

    /** The haze's field and noise, kept between frames (Dart `paintField(..., 'f', 7, ..., every: .04)`). */
    private class Haze {
        var field: LiveField? = null
        var w = 0f
        var h = 0f
        var lastCompute = -1e9 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:71
        val teal = doubleArrayOf(0.0, 0.0, 0.0)
        val blue = doubleArrayOf(0.0, 0.0, 0.0)
        val glow = doubleArrayOf(0.0, 0.0, 0.0)
        var nx = DoubleArray(0)
        var ny = DoubleArray(0)
        var mx = DoubleArray(0)
        var my = DoubleArray(0)
        val n = LiveFbmGrid(sinHash = false)
        val m = LiveFbmGrid(sinHash = false)
    }

    /** The haze's colours: mixRgb(mixRgb(teal, blue, m), glow, [g]) and smooth(.38, .82, n) × [haze], as Dart's field. */
    private fun fillHaze(h: Haze, colors: IntArray, lw: Int, lh: Int, g: Double, haze: Double) {
        val (tr, tg, tb) = h.teal
        val (br, bg, bb) = h.blue
        val (gr, gg, gb) = h.glow
        var k = 0
        for (y in 0..lh) {
            for (x in 0..lw) {
                val m = h.m.at(x, y)
                val r0 = tr + (br - tr) * m
                val g0 = tg + (bg - tg) * m
                val b0 = tb + (bb - tb) * m
                colors[k++] = packArgb(r0 + (gr - r0) * g, g0 + (gg - g0) * g, b0 + (gb - b0) * g, smooth(.38, .82, h.n.at(x, y)) * haze) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:40
            }
        }
    }

    override fun paint(canvas: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val a = strengthOf(frame)
        val run = frame.running
        val tt = frame.t * .03 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:31
        // A faint drifting haze, brighter while a reply runs. The ribbons are measured on top of it at its brightest.
        val haze = KitAuroraBands.HAZE * (.4 + .6 * run) * a // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:35
        val h = frame.memory.of("haze") { Haze() }
        // paintField(canvas, size, f, 'f', 7, ..., every: .04), with its two noises taken a lattice at a time: both are
        // separable (x by column, y by row), so the values are fbm's exactly.
        var lw = max(8, (size.width / 7).roundToInt()) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:36 lib/ui/kit/live_background/styles/kit_live_toolkit.dart:87
        var lh = max(8, (size.height / 7).roundToInt()) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:36 lib/ui/kit/live_background/styles/kit_live_toolkit.dart:88
        while ((lw + 1) * (lh + 1) > 65000) { // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:89
            lw = (lw * .8).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:90
            lh = (lh * .8).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:91
        }
        var field = h.field
        if (field == null || field.cols != lw || field.rows != lh || h.w != size.width || h.h != size.height) {
            field = LiveField(lw, lh)
            h.field = field
            h.w = size.width
            h.h = size.height
            h.lastCompute = -1e9 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:99
            h.nx = DoubleArray(lw + 1)
            h.mx = DoubleArray(lw + 1)
            h.ny = DoubleArray(lh + 1)
            h.my = DoubleArray(lh + 1)
        }
        if (frame.t - h.lastCompute >= .04 || frame.t < h.lastCompute) { // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:41
            h.lastCompute = frame.t
            rgb(p.teal, h.teal)
            rgb(p.blue, h.blue)
            rgb(p.glow, h.glow)
            val aspect = size.height.toDouble() / size.width
            for (x in 0..lw) {
                val u = x.toDouble() / lw
                h.nx[x] = u * 1.6 + tt // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:37
                h.mx[x] = u * 1.1 - tt * .5 + 3 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:38
            }
            for (y in 0..lh) {
                val v = y.toDouble() / lh * aspect
                h.ny[y] = v * 1.6 - tt * .7 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:37
                h.my[y] = v * 1.3 + tt * .4 + 7 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:38
            }
            h.n.columns(h.nx, lw + 1)
            h.n.rows(h.ny, lh + 1)
            h.m.columns(h.mx, lw + 1)
            h.m.rows(h.my, lh + 1)
            fillHaze(h, field.colors, lw, lh, run * .7, haze) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:39
            field.upload()
        }
        field.draw(canvas, size.width / lw, size.height / lh)
        frame.memory.of("bands") { KitAuroraBands() }.paint(canvas, size, p, frame.t, frame.strength)
    }
}

/** One ribbon of light: a spine across the screen (shares of the width and height) and its look (Dart `_Ribbon`). */
internal class AuroraRibbon(
    val spine: List<Pair<Double, Double>>,
    val side: Double,
    val phase: Double,
    val width: Double = 1.0,
    val peak: Double = 1.0,
)

/** A soft strand of silk along a ribbon (Dart `_Strand`). */
internal class AuroraStrand(
    val at: Double,
    val weave: Double,
    val waves: Double,
    val phase: Double,
    val width: Double,
    val alpha: Double,
    val from: Double,
    val to: Double,
)

/**
 * The aurora's ribbons (Dart `KitAuroraBands`): built once per size, theme and Intensity, then only moved (a translate
 * and a rotate per ribbon per frame), so a frame allocates nothing.
 *
 * Flutter draws each ribbon as two coloured vertex meshes every frame. Here each ribbon's two meshes are drawn once,
 * when built, into a bitmap at the page's logical scale (the same meshes, the same colours), and a frame draws the four
 * bitmaps moved and turned: four image draws, no vertices, on every Android version (hardware canvases draw vertices
 * only from API 29).
 *
 * Brightness is measured, not guessed: the brightest spot anywhere is the accent's hue mixed into the ground, under the
 * haze at its brightest, exactly as far as the text colours allow ([peakOf]).
 */
public class KitAuroraBands {
    private class Plate(val image: ImageBitmap, val ox: Float, val oy: Float, val pivotX: Float, val pivotY: Float)

    private val plates = arrayOfNulls<Plate>(RIBBONS.size)
    private var builtSize = Size.Zero
    private var builtPalette: KitLivePalette? = null
    private var builtStrength = Double.NaN
    private var builtBoost = Double.NaN

    /**
     * Draws the ribbons at moment [t] seconds in [palette]'s colours. [strength] is the Intensity (1 = Normal). [boost]
     * multiplies the measured brightness, only for a picture whose words all sit on solid bubbles (a theme card).
     */
    public fun paint(canvas: DrawScope, size: Size, palette: KitLivePalette, t: Double, strength: Double, boost: Double = 1.0) {
        if (size.width <= 0 || size.height <= 0) return
        val q = (strength * 32).roundToInt() / 32.0 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:430
        if (size != builtSize || palette != builtPalette || q != builtStrength || boost != builtBoost) {
            builtSize = size
            builtPalette = palette
            builtStrength = q
            builtBoost = boost
            build(size, palette, q, boost)
        }
        val w = size.width
        val h = size.height
        for (b in RIBBONS.indices) {
            val plate = plates[b] ?: continue
            val ph = RIBBONS[b].phase
            val dx = (sin(t * .041 + ph * 2.1) * w * .022).toFloat() // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:451
            val dy = (sin(t * .033 + ph * 1.3) * h * .012).toFloat() // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:452
            val sway = sin(t * .027 + ph) * .03 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:453
            canvas.withTransform({
                translate(plate.pivotX + dx, plate.pivotY + dy)
                rotateRad(sway.toFloat(), pivot = Offset.Zero)
            }) {
                drawImage(plate.image, topLeft = Offset(plate.ox, plate.oy))
            }
        }
    }

    private fun build(size: Size, p: KitLivePalette, strength: Double, boost: Double) {
        val (hue, peak) = peakOf(p, strength)
        val top = min(1.0, peak * boost)
        // Subtle dims everything; Bold (and the previews) widens and lifts the glow and the bloom. Nothing passes top.
        val dim = strength.coerceIn(0.0, 1.0)
        val bold = ((strength - 1) / .6).coerceIn(0.0, 1.0) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:263
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val short = min(w, h)
        val paint = Paint().apply { color = Color.White }
        for (b in RIBBONS.indices) {
            val r = RIBBONS[b]
            val px = DoubleArray(r.spine.size) { r.spine[it].first * w }
            val py = DoubleArray(r.spine.size) { r.spine[it].second * h }
            val sx = DoubleArray(ROWS + 1)
            val sy = DoubleArray(ROWS + 1)
            val pt = doubleArrayOf(0.0, 0.0)
            for (i in 0..ROWS) {
                catmullRom(px, py, i.toDouble() / ROWS, pt)
                sx[i] = pt[0]
                sy[i] = pt[1]
            }
            val nx = DoubleArray(ROWS + 1)
            val ny = DoubleArray(ROWS + 1)
            for (i in 0..ROWS) {
                val ddx = sx[min(ROWS, i + 1)] - sx[max(0, i - 1)]
                val ddy = sy[min(ROWS, i + 1)] - sy[max(0, i - 1)]
                val dist = sqrt(ddx * ddx + ddy * ddy)
                val len = if (dist == 0.0) 1.0 else dist
                nx[i] = -ddy / len * r.side
                ny[i] = ddx / len * r.side
            }
            val pivotX = sx[ROWS / 2] // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:280
            val pivotY = sy[ROWS / 2] // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:280
            // Where the sheet folds it narrows and brightens, as silk does; the ends fade out, well beyond the screen.
            fun fold(v: Double) = sin(2 * PI * (v * 1.35) + r.phase) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:284
            fun width(v: Double) = (1 + .18 * fold(v)) * r.width // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:285
            fun shine(v: Double) = (.9 - .1 * fold(v)) * smooth(0.0, .14, v) * smooth(0.0, .14, 1 - v) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:287
            fun glowAt(d: Double, v: Double): Double {
                val x = (if (d < 0) -d / LONG else d) / width(v)
                return profile(x, bold, p.dark) * top * shine(v) * r.peak * dim
            }

            // The glow: one column either side per offset, the core shared.
            val offsets = (ACROSS.reversed().map { -it * LONG } + ACROSS.drop(1)).toDoubleArray()
            val cols = offsets.size
            val gPos = ArrayList<Offset>((ROWS + 1) * cols)
            val gCol = ArrayList<Color>((ROWS + 1) * cols)
            for (i in 0..ROWS) {
                val v = i.toDouble() / ROWS
                for (c in 0 until cols) {
                    val d = offsets[c]
                    val off = d * short * width(v)
                    gPos += Offset((sx[i] + nx[i] * off - pivotX).toFloat(), (sy[i] + ny[i] * off - pivotY).toFloat())
                    val edge = c == 0 || c == cols - 1
                    val a = if (edge) 0.0 else glowAt(d * width(v), v)
                    gCol += liveAlpha(hue, a)
                }
            }

            // The strands: a soft ridge each, five vertices across, as bright as the room the glow leaves under it.
            val sPos = ArrayList<Offset>()
            val sCol = ArrayList<Color>()
            val sIdx = ArrayList<Int>()
            for (st in STRANDS) {
                val base = sPos.size
                for (i in 0..ROWS) {
                    val v = i.toDouble() / ROWS
                    val wv = width(v)
                    val d = st.at + st.weave * sin(2 * PI * st.waves * v + st.phase + r.phase) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:343
                    val near = sign(st.at) * max(0.0, abs(st.at) - st.weave)
                    val under = glowAt(near * wv, v)
                    val full = top * shine(v) * r.peak * dim
                    val room = if (under >= full) 0.0 else (full - under) / (1 - under)
                    val shown = smooth(st.from, st.from + .12, v) * smooth(st.to, st.to - .12, v) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:349
                    for (c in RIDGE_AT.indices) {
                        val off = (d * wv + RIDGE_AT[c] * st.width * wv / 2) * short // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:351
                        sPos += Offset((sx[i] + nx[i] * off - pivotX).toFloat(), (sy[i] + ny[i] * off - pivotY).toFloat())
                        sCol += liveAlpha(hue, room * st.alpha * shown * RIDGE[c])
                    }
                }
                for (g in grid(ROWS, RIDGE_AT.size)) sIdx += base + g
            }

            // The plate: both meshes, drawn once at the page's scale into a bitmap around the pivot.
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (o in gPos) {
                minX = min(minX, o.x); minY = min(minY, o.y); maxX = max(maxX, o.x); maxY = max(maxY, o.y)
            }
            for (o in sPos) {
                minX = min(minX, o.x); minY = min(minY, o.y); maxX = max(maxX, o.x); maxY = max(maxY, o.y)
            }
            val ox = floor(minX) - 1
            val oy = floor(minY) - 1
            val iw = ceil(maxX - ox).toInt() + 1
            val ih = ceil(maxY - oy).toInt() + 1
            val image = ImageBitmap(iw, ih)
            val c = Canvas(image)
            c.translate(-ox, -oy)
            // No shader: the vertex colours are drawn as they are (white × colour under Modulate, wherever a platform
            // combines them with the paint's colour).
            c.drawVertices(Vertices(VertexMode.Triangles, gPos, gPos, gCol, grid(ROWS, cols)), BlendMode.Modulate, paint)
            c.drawVertices(Vertices(VertexMode.Triangles, sPos, sPos, sCol, sIdx), BlendMode.Modulate, paint)
            liveSettle(image)
            plates[b] = Plate(image, ox, oy, pivotX.toFloat(), pivotY.toFloat())
        }
    }

    public companion object {
        /** The haze's peak opacity at strength 1 while a reply runs. */
        public const val HAZE: Double = .012 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:120

        internal const val ROWS = 72 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:122

        /** The longer side's widening of every offset. */
        internal const val LONG = 1.45 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:142

        /** Text2 and text3 read through this much of the ground (Dart `KitTextHalo.cover`). */
        internal const val HALO_COVER = .55 // F: lib/ui/kit/kit_text_halo.dart:33


        /** Across a ribbon, as shares of the screen's short side: from the core out through the glow to the bloom. */
        internal val ACROSS =
            doubleArrayOf(
                0.0,
                .005, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:129
                .011, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:130
                .018, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:131
                .027, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:132
                .039, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:133
                .054, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:134
                .072, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:135
                .094, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:136
                .12, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:137
                .15, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:138
                .19, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:139
                .24, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:140
            )

        internal val RIBBONS =
            listOf(
                // The sweep through the top start corner.
                AuroraRibbon(
                    listOf(-.16 to .25, .02 to .14, .2 to .065, .42 to .0, .62 to -.08), // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:147
                    side = 1.0,
                    phase = 0.0,
                    width = .85, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:150
                ),
                // The long S across the lower half.
                AuroraRibbon(
                    listOf(
                        .02 to 1.08, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:155
                        .16 to .92, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:156
                        .33 to .82, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:157
                        .53 to .78, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:158
                        .68 to .74, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:159
                        .81 to .66, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:160
                        .9 to .56, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:161
                        1.02 to .44, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:162
                        1.14 to .36, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:163
                    ),
                    side = -1.0,
                    phase = 1.9, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:166
                ),
                // The short arc along the bottom edge.
                AuroraRibbon(
                    listOf(.42 to 1.12, .62 to 1.02, .84 to .975, 1.2 to .955), // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:170
                    side = -1.0,
                    phase = 3.7, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:172
                    width = .7, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:173
                    peak = .85, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:174
                ),
                // A faint one in the top end corner.
                AuroraRibbon(
                    listOf(.7 to -.09, .86 to .03, 1.14 to .17), // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:178
                    side = 1.0,
                    phase = 5.3, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:180
                    width = .6, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:181
                    peak = .6, // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:182
                ),
            )

        internal val STRANDS =
            listOf(
                // Silk just off the core, on the short side.
                AuroraStrand(.03, .008, 1.3, 0.0, .012, .9, 0.0, .9), // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:188
                // Two in the glow on the long side, weaving past each other.
                AuroraStrand(-.05, .014, 1.9, 1.7, .016, .8, .08, 1.0), // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:190
                AuroraStrand(-.1, .014, 1.4, 3.1, .02, .7, .2, .85), // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:191
            )

        internal val RIDGE_AT = doubleArrayOf(-1.0, -.5, 0.0, .5, 1.0) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:327
        internal val RIDGE = doubleArrayOf(0.0, .55, 1.0, .55, 0.0) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:328

        /**
         * The glow across a ribbon at [d] shares of the short side from its spine, 0..1: a bright soft core, a glow
         * either side, a wide faint bloom; [bold] widens and lifts the glow and the bloom (Dart `_profile`).
         */
        internal fun profile(d: Double, bold: Double, dark: Boolean): Double {
            val core = exp(-(d / (if (dark) .009 else .018)).pow(2) / 2) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:200
            val body =
                ((if (dark) .4 else .8) + .1 * bold) * // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:202
                    exp(-(d / (.055 * (1 + .3 * bold))).pow(2) / 2) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:203
            val bloom =
                ((if (dark) .16 else .3) + .08 * bold) * // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:205
                    exp(-(d / (.12 * (1 + .3 * bold))).pow(2) / 2) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:206
            return 1 - (1 - core) * (1 - body) * (1 - bloom)
        }

        /**
         * The aurora's colour in [p] and its opacity at the brightest spot (Dart `peakOf`): the accent's hue (saturated
         * in dark themes, a pale wash in light ones) mixed into the ground, under the haze at its brightest, as far as
         * the text allows. In a dark theme text2 and text3 keep a halo of the ground, so they are read through
         * [HALO_COVER] of it.
         */
        internal fun peakOf(p: KitLivePalette, strength: Double): Pair<Color, Double> {
            val hsl = LiveHsl.of(p.glow)
            val hue =
                if (p.dark) {
                    hsl.withSaturation(max(hsl.saturation, .8)).withLightness(hsl.lightness.coerceIn(.45, .6)).toColor() // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:228,229
                } else {
                    hsl.withSaturation(hsl.saturation.coerceIn(.45, .7)).withLightness(.8).toColor() // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:232,233
                }
            // The haze's most telling colour: the brightest of its hues in a dark theme, the darkest in a light one.
            val hazes = listOf(p.teal, p.blue, p.glow).sortedBy { liveLuminance(it) }
            val hazed = liveMix(p.ground, if (p.dark) hazes.last() else hazes.first(), HAZE * (if (p.dark) 1.0 else .7) * max(strength, 1.0)) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:242
            // A light theme's wash could go further than its text needs; it stops short so it stays a soft wash.
            return hue to (if (p.dark) liveMost(p, hazed, hue, halo = HALO_COVER) else liveMost(p, hazed, hue) * .78) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:250
        }

        /** Two triangles per cell of a [rows] × ([cols] - 1) strip of vertices laid out row by row (Dart `_grid`). */
        internal fun grid(rows: Int, cols: Int): List<Int> {
            val idx = ArrayList<Int>(rows * (cols - 1) * 6) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:381
            for (i in 0 until rows) {
                for (c in 0 until cols - 1) {
                    val a = i * cols + c
                    val b = a + 1
                    val d = a + cols
                    val e = d + 1
                    idx += a; idx += b; idx += d; idx += b; idx += e; idx += d
                }
            }
            return idx
        }

        /** A point on the Catmull-Rom curve through ([px], [py]) at [s] (0..1), into [out] (Dart `_catmullRom`). */
        internal fun catmullRom(px: DoubleArray, py: DoubleArray, s: Double, out: DoubleArray) {
            val segs = px.size - 1
            val f = (s * segs).coerceIn(0.0, segs - 1e-9) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:401
            val i = floor(f).toInt()
            val u = f - i
            val i0 = max(0, i - 1)
            val i3 = min(segs, i + 2) // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:405
            val u2 = u * u
            val u3 = u2 * u
            fun c(p0: Double, p1: Double, p2: Double, p3: Double) =
                (p1 * 2 + (p2 - p0) * u + (p0 * 2 - p1 * 5 + p2 * 4 - p3) * u2 + (p1 * 3 - p0 - p2 * 3 + p3) * u3) * .5 // F: lib/ui/kit/live_background/styles/kit_live_aurora.dart:407-411
            out[0] = c(px[i0], px[i], px[i + 1], px[i3])
            out[1] = c(py[i0], py[i], py[i + 1], py[i3])
        }
    }
}
