package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The recurring cast of the setup and connecting drawings (lib/ui/kit/scenes/setup_cast.dart): the portal brackets, a
 * phone with a rounded screen, a terminal prompt, sparkles and dots. Every path is built once; scenes place them with
 * canvas transforms.
 */
public object SetupCast {
    // mark.svg (viewBox 24..84) in unit space, centred on the origin: the mark spans -0.38..0.38 on both axes.
    internal fun m(x: Float, y: Float): Offset = Offset((x - 54) / 60, (y - 54) / 60) // F: lib/ui/kit/scenes/setup_cast.dart:15

    internal val upper: Path by lazy {
        scenePath(m(61f, 31f)) { // F: lib/ui/kit/scenes/setup_cast.dart:18
            lineTo(m(43f, 31f)) // F: lib/ui/kit/scenes/setup_cast.dart:19
            arcTo(m(31f, 43f), 12f / 60f, clockwise = false) // F: lib/ui/kit/scenes/setup_cast.dart:21,22
            lineTo(m(31f, 64f)) // F: lib/ui/kit/scenes/setup_cast.dart:25
        }
    }

    internal val lower: Path by lazy {
        scenePath(m(77f, 44f)) { // F: lib/ui/kit/scenes/setup_cast.dart:28
            lineTo(m(77f, 65f)) // F: lib/ui/kit/scenes/setup_cast.dart:29
            arcTo(m(65f, 77f), 12f / 60f, clockwise = true) // F: lib/ui/kit/scenes/setup_cast.dart:31,32
            lineTo(m(44f, 77f)) // F: lib/ui/kit/scenes/setup_cast.dart:35
        }
    }

    /**
     * The portal mark at [center], [size] scene units across its box. [upper] and [lower] (0..1) say how much of each
     * bracket has drawn itself; [open] moves the brackets apart along the diagonal (negative closes them), in scene
     * units. [width] is the stroke in scene units.
     */
    internal fun portal(
        canvas: Canvas,
        center: Offset,
        size: Float,
        color: Color,
        upper: Float = 1f,
        lower: Float = 1f,
        open: Float = 0f,
        width: Float? = null,
    ) {
        val stroke = (width ?: (size / 7)) / size // F: lib/ui/kit/scenes/setup_cast.dart:52
        val pen = SceneDraw.pen(color, stroke)
        val shift = open / size
        canvas.save()
        canvas.translate(center.x, center.y)
        canvas.scale(size, size)
        canvas.save()
        canvas.translate(-shift, -shift)
        SceneDraw.partialPath(canvas, this.upper, upper, pen)
        canvas.restore()
        canvas.save()
        canvas.translate(shift, shift)
        SceneDraw.partialPath(canvas, this.lower, lower, pen)
        canvas.restore()
        canvas.restore()
    }

    // A four-point sparkle in unit space: the "energy" mark.
    internal val sparklePath: Path by lazy {
        Path().apply {
            moveTo(0f, -1f)
            quadraticTo(.14f, -.14f, 1f, 0f) // F: lib/ui/kit/scenes/setup_cast.dart:72
            quadraticTo(.14f, .14f, 0f, 1f) // F: lib/ui/kit/scenes/setup_cast.dart:73
            quadraticTo(-.14f, .14f, -1f, 0f) // F: lib/ui/kit/scenes/setup_cast.dart:74
            quadraticTo(-.14f, -.14f, 0f, -1f) // F: lib/ui/kit/scenes/setup_cast.dart:75
            close()
        }
    }

    /** A filled sparkle of [radius] at [center], scaled by [pop] (may overshoot) and turned by [turn] radians. */
    internal fun sparkle(
        canvas: Canvas,
        center: Offset,
        radius: Float,
        color: Color,
        pop: Float = 1f,
        turn: Float = 0f,
    ) {
        if (pop <= 0f) return
        canvas.save()
        canvas.translate(center.x, center.y)
        if (turn != 0f) canvas.rotate((turn * 180 / PI).toFloat()) // NV: Compose rotates in degrees, Dart in radians
        canvas.scale(radius * pop, radius * pop)
        canvas.drawPath(sparklePath, SceneDraw.fill(color))
        canvas.restore()
    }

    /** A phone's outline as one path (so it can draw itself in), for [frame]. */
    internal fun phone(frame: Rect, radius: Float): Path =
        Path().apply { addRoundRect(RoundRect(frame, CornerRadius(radius))) }

    /** A `>` prompt whose top-left is at [origin], [size] units tall. */
    internal fun prompt(origin: Offset, size: Float): Path =
        Path().apply {
            moveTo(origin.x, origin.y)
            lineTo(origin.x + size / 2, origin.y + size / 2) // F: lib/ui/kit/scenes/setup_cast.dart:104
            lineTo(origin.x, origin.y + size)
        }

    /** A check mark inside a box of [size] centred at [center]. */
    internal fun check(center: Offset, size: Float): Path =
        Path().apply {
            moveTo(center.x - size * .36f, center.y + size * .02f) // F: lib/ui/kit/scenes/setup_cast.dart:109
            lineTo(center.x - size * .1f, center.y + size * .28f) // F: lib/ui/kit/scenes/setup_cast.dart:110
            lineTo(center.x + size * .38f, center.y - size * .24f) // F: lib/ui/kit/scenes/setup_cast.dart:111
        }

    /** Whether a blinking cursor shows at the frame's loop (twice per loop period). */
    internal fun cursorOn(frame: SceneFrame): Boolean =
        !frame.looping || (floor(frame.loop * 4).toInt() and 1) == 0 // F: lib/ui/kit/scenes/setup_cast.dart:115

    /** A point on the circle of [radius] around [center] at [turns] (0..1, from the top, clockwise). */
    internal fun orbit(center: Offset, radius: Float, turns: Float): Offset {
        val angle = turns * 2 * PI - PI / 2 // F: lib/ui/kit/scenes/setup_cast.dart:120
        return center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius
    }
}
