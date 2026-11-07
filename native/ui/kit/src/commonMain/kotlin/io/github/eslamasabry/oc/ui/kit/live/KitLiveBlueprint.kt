package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleBlueprint
import org.jetbrains.compose.resources.StringResource

/**
 * A technical-drawing grid with measurement ticks drifting slowly, like the back of an engineering sheet; a scan line
 * sweeps down it while a reply runs (Dart `KitLiveBlueprint`, `styles/kit_live_blueprint.dart`).
 *
 * The grid is two paths built once per size and only moved (the drift is a translate, so a frame builds nothing); the
 * measures are laid out once per size and colour.
 */
public object KitLiveBlueprint : KitLiveStyle() {
    override val id: String = "blueprint"
    override val label: StringResource = Res.string.liveStyleBlueprint
    override val ceilingFactor: Double = 2.0 // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:14

    /** The grid's minor step against the width. */
    internal const val COLUMNS = 18 // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:28

    /** Minor steps per major line. */
    internal const val MAJOR_EVERY = 5 // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:28

    /** Minor steps the grid drifts a second. */
    internal const val DRIFT = .15 // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:29

    /** The share of the ceiling it uses: calibrated so text3 keeps 4.5:1. */
    internal const val CALIBRATED = .64 // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:26

    /** Where along the page the scan line is, 0..1, at [t] seconds. */
    internal fun scanAt(t: Double): Double = dmod(t * .18, 1.0) // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:60

    /** The grid's drift at [t] for minor step [m]: 0 until one major step. */
    internal fun driftAt(t: Double, m: Double): Double = dmod(t * m * DRIFT, m * MAJOR_EVERY)

    private class Grid(val minor: Path, val major: Path)

    private class Labels {
        var fs = 0f
        var color = Color.Unspecified
        val laid = ArrayList<TextLayoutResult>()
    }

    private class Scan {
        var glow = Color.Unspecified
        var peak = -1.0
        var brush: Brush? = null
    }

    private val LINE = Stroke(width = 1f) // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:46

    override fun paint(canvas: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame) * ceilingFactor * CALIBRATED
        if (peak <= 0) return
        val m = size.width.toDouble() / COLUMNS
        val big = m * MAJOR_EVERY
        val o = driftAt(frame.t, m)
        val col = liveMix(p.ink, p.blue, .6) // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:30
        // The lines of the Dart loops at drift 0, one major step beyond each edge; drawn moved by the drift.
        val grid =
            liveCached(frame, "blueprint.grid", size) {
                val minor = Path()
                val major = Path()
                var k = 0
                var x = -big
                while (x < size.width + big + big) {
                    (if (k % MAJOR_EVERY == 0) major else minor).run {
                        moveTo(x.toFloat(), (-big).toFloat())
                        lineTo(x.toFloat(), (size.height + big).toFloat())
                    }
                    x += m
                    k++
                }
                k = 0
                var y = -big
                while (y < size.height + big + big) {
                    (if (k % MAJOR_EVERY == 0) major else minor).run {
                        moveTo((-big).toFloat(), y.toFloat())
                        lineTo((size.width + big).toFloat(), y.toFloat())
                    }
                    y += m
                    k++
                }
                Grid(minor, major)
            }
        val line = LINE
        canvas.translate(o.toFloat(), o.toFloat()) {
            drawPath(grid.minor, liveAlpha(col, peak * .3), style = line) // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:47
            drawPath(grid.major, liveAlpha(col, peak * .55), style = line) // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:48
        }

        val text = frame.text
        if (text != null) {
            val fs = if (m * .5 < 7) 7.0 else m * .5 // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:50
            val ink = liveAlpha(col, peak * .6) // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:52
            val labels = frame.memory.of("blueprint.labels") { Labels() }
            if (labels.fs != fs.toFloat() || labels.color != ink) {
                labels.fs = fs.toFloat()
                labels.color = ink
                labels.laid.clear()
            }
            var i = 0
            var x = o
            while (x < size.width) {
                if (i == labels.laid.size) {
                    labels.laid +=
                        text.measurer.measure(
                            AnnotatedString("${i * 50}"), // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:55
                            TextStyle(fontFamily = text.mono, fontSize = labels.fs.sp, color = labels.color),
                            density = UNIT,
                        )
                }
                canvas.drawText(labels.laid[i], topLeft = Offset((x + 2).toFloat(), (m * .25).toFloat())) // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:55
                x += big
                i++
            }
        }

        val run = frame.running
        if (run > .02) { // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:59
            val y = (scanAt(frame.t) * size.height).toFloat()
            val band = (m * 2).toFloat() // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:61
            // The fade is drawn in a unit band, so one brush serves every frame; the running share is its alpha.
            val scan = frame.memory.of("blueprint.scan") { Scan() }
            if (scan.glow != p.glow || scan.peak != peak || scan.brush == null) {
                scan.glow = p.glow
                scan.peak = peak
                scan.brush =
                    Brush.verticalGradient(
                        listOf(liveAlpha(p.glow, 0.0), liveAlpha(p.glow, peak * .7)), // F: lib/ui/kit/live_background/styles/kit_live_blueprint.dart:69,70
                        startY = 0f,
                        endY = 1f,
                    )
            }
            canvas.translate(0f, y - band) {
                scale(1f, band, pivot = Offset.Zero) {
                    drawRect(scan.brush!!, size = Size(size.width, 1f), alpha = run.toFloat())
                }
            }
        }
    }

    private val UNIT = Density(1f, 1f)
}
