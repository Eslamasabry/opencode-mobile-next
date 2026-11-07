package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleMatrixRain
import org.jetbrains.compose.resources.StringResource
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Matrix rain (Dart `KitLiveMatrixRain`, `styles/kit_live_matrix_rain.dart`): thin columns of glyphs fall slowly, the
 * classic digital rain; a short trail of dimmer glyphs follows each head. Faster and brighter while a reply runs.
 *
 * [glyphs] is the set the rain draws from. The default needs the system's CJK face for the katakana (Android has one);
 * tests that must not depend on it pass [ASCII_GLYPHS]. Each glyph is laid out once per size and face and drawn in
 * its colour and level, so a frame lays out no text.
 */
public class KitLiveMatrixRain(public val glyphs: String = DEFAULT_GLYPHS) : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleMatrixRain
    override val ceilingFactor: Double = CEILING_FACTOR

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val measurer = frame.text ?: return
        val p = frame.palette
        val peak = livePeak(frame) * ceilingFactor * 0.55 // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:41-42
        if (peak <= 0) return
        val fs = max(9.0, size.width / 22.0) // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:44
        val cache = frame.memory.of("matrix.glyphs") { Glyphs() }
        cache.ensure(fs, frame.mono, glyphs, measurer)
        val colour = p.glow
        val headColour = liveMix(p.glow, p.ink, .35) // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:46
        val clock = liveClock(frame, "matrix", 1 + frame.running * 3) // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:47
        val rows = size.height / fs
        val cols = ceil(size.width / fs).toInt()
        val headLevel = .85 + .15 * frame.running // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:50
        val tick = floor(clock * 6) // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:51
        val slow = floor(clock * .8) // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:64
        val n = glyphs.length
        for (i in 0 until cols) {
            val id = i.toDouble()
            val speed = speedOf(i)
            val cycle = rows + TRAIL + 2 + 6 * liveHash(id, 2.0) // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:54
            val head = floor(dmod(clock * speed + liveHash(id, 3.0) * cycle, cycle) - TRAIL).toInt() // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:55-56
            for (k in 0 until TRAIL) {
                val row = head - k
                if (row < 0 || row > rows) continue
                val level = levelOf(headLevel, k)
                if (level < 1) continue
                val g = floor(liveHash(id, row.toDouble(), if (k == 0) tick else slow) * n).toInt() % n
                val tint = if (k == 0) headColour else colour
                scope.drawText(
                    cache.layout(g),
                    color = tint.copy(alpha = (peak * level / LEVELS).toFloat().coerceIn(0f, 1f)),
                    topLeft = Offset((i * fs).toFloat(), (row * fs).toFloat()),
                )
            }
        }
    }

    /** One layout per glyph at this size and face (Dart `_Glyphs`, which keys a painter by glyph, level and head). */
    internal class Glyphs {
        private var fs = 0.0
        private var family: FontFamily? = null
        private var set = ""
        private var measurer: TextMeasurer? = null
        private var layouts = arrayOfNulls<TextLayoutResult>(0)
        private var style = TextStyle.Default

        fun ensure(fs: Double, family: FontFamily, set: String, measurer: TextMeasurer) {
            val sameFace = fs == this.fs && family == this.family
            if (sameFace && set == this.set && measurer === this.measurer) return
            this.fs = fs
            this.family = family
            this.set = set
            this.measurer = measurer
            layouts = arrayOfNulls(set.length)
            style =
                TextStyle(
                    color = Color.White,
                    fontFamily = family,
                    fontSize = fs.sp,
                    lineHeight = fs.sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
                )
        }

        fun layout(g: Int): TextLayoutResult =
            layouts[g] ?: measurer!!.measure(set[g].toString(), style, density = UNIT_DENSITY).also { layouts[g] = it }
    }

    public companion object {
        public const val ID: String = "matrixRain"

        /** Half-width katakana, digits and symbols (the classic rain). */
        public const val DEFAULT_GLYPHS: String =
            "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789:.=*+<>"

        /** Only what the bundled mono face has; deterministic in tests. */
        public const val ASCII_GLYPHS: String = "0123456789:.=*+<>|/ZXYWMK"

        internal const val CEILING_FACTOR: Double = 2.0 // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:21
        internal const val LEVELS: Int = 12 // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:35
        internal const val TRAIL: Int = 14 // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:36

        /** The glyphs are laid out in dp: the canvas is already scaled to the screen. */
        private val UNIT_DENSITY = Density(1f, 1f)

        /** Rows a second of column [i] at clock rate 1. */
        internal fun speedOf(i: Int): Double = 2.6 * (.6 + ((i * 7) % 5) / 8.0) // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:53

        /** The brightness level (0..[LEVELS]) of the [k]th glyph of a trail, 0 the head. */
        internal fun levelOf(headLevel: Double, k: Int): Int {
            val fade = if (k == 0) 1.0 else (1 - k.toDouble() / (TRAIL + 1)).pow(1.5) * .5 // F: lib/ui/kit/live_background/styles/kit_live_matrix_rain.dart:60
            // Positive, so Kotlin's half-up rounding is Dart's half-away-from-zero.
            return (headLevel * fade * LEVELS).roundToInt()
        }
    }
}
