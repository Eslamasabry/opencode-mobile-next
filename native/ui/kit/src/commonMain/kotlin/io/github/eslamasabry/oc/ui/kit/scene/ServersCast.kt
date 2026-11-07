package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path

// The recurring cast of the Servers drawings (lib/ui/kit/scenes/servers_cast.dart): a phone with a rounded screen, a
// laptop, and the brand's portal on the laptop's screen. Each part draws itself in from a 0..1 t, so the scenes stage
// them the same way. Each part's paths are built once, when the scene's static instance is created, never per frame.

/** A phone in the Servers drawings: a rounded body, a speaker and three short lines on the screen (a conversation). */
public class ServersPhone(public val body: Rect) {
    internal val radius: Float = body.width * .25f // F: lib/ui/kit/scenes/servers_cast.dart:16
    internal val outline: Path by lazy { Path().apply { addRoundRect(RoundRect(body, CornerRadius(radius))) } }
    internal val speaker: Path by lazy {
        Path().apply {
            moveTo(body.center.x - body.width * .14f, body.top + body.height * .1f) // F: lib/ui/kit/scenes/servers_cast.dart:19
            lineTo(body.center.x + body.width * .14f, body.top + body.height * .1f) // F: lib/ui/kit/scenes/servers_cast.dart:21,22
        }
    }

    /** Three short lines on the screen: a conversation. */
    internal val lines: List<Path> by lazy {
        LINES.map { (y, w) ->
            Path().apply {
                moveTo(body.left + body.width * .24f, body.top + body.height * y) // F: lib/ui/kit/scenes/servers_cast.dart:27
                lineTo(body.left + body.width * (.24f + w), body.top + body.height * y) // F: lib/ui/kit/scenes/servers_cast.dart:29
            }
        }
    }

    internal fun paint(canvas: Canvas, palette: ScenePalette, t: Float, screen: Color? = null) {
        if (t <= 0f) return
        canvas.drawRRect(
            body,
            radius,
            SceneDraw.fill(SceneDraw.fade(palette.surface, SceneDraw.interval(t, .3f, 1f))), // F: lib/ui/kit/scenes/servers_cast.dart:46
        )
        SceneDraw.partialPath(
            canvas,
            outline,
            SceneDraw.interval(t, 0f, .8f), // F: lib/ui/kit/scenes/servers_cast.dart:51
            SceneDraw.pen(palette.muted, 4f), // F: lib/ui/kit/scenes/servers_cast.dart:52
        )
        val detail = SceneDraw.interval(t, .55f, 1f) // F: lib/ui/kit/scenes/servers_cast.dart:54
        SceneDraw.partialPath(canvas, speaker, detail, SceneDraw.pen(palette.line, SceneDraw.HAIRLINE))
        lines.forEachIndexed { i, line ->
            SceneDraw.partialPath(
                canvas,
                line,
                SceneDraw.interval(t, .6f + i * .1f, .9f + i * .03f), // F: lib/ui/kit/scenes/servers_cast.dart:65
                SceneDraw.pen(screen ?: palette.line, SceneDraw.HAIRLINE + .5f), // F: lib/ui/kit/scenes/servers_cast.dart:66
            )
        }
    }

    internal companion object {
        /** The screen lines, each (y, width) as fractions of the body. */
        val LINES: List<Pair<Float, Float>> =
            listOf(.34f to .5f, .48f to .34f, .62f to .44f) // F: lib/ui/kit/scenes/servers_cast.dart:25
    }
}

/** A laptop: a screen with rounded corners and a base with a hinge notch. */
public class ServersLaptop(public val screen: Rect) {
    internal val radius: Float = screen.height * .14f // F: lib/ui/kit/scenes/servers_cast.dart:77
    internal val outline: Path by lazy { Path().apply { addRoundRect(RoundRect(screen, CornerRadius(radius))) } }
    internal val base: Path by lazy {
        Path().apply {
            moveTo(screen.left - screen.width * .12f, screen.bottom + 9) // F: lib/ui/kit/scenes/servers_cast.dart:80
            lineTo(screen.right + screen.width * .12f, screen.bottom + 9) // F: lib/ui/kit/scenes/servers_cast.dart:81
        }
    }
    internal val hinge: Path by lazy {
        Path().apply {
            moveTo(screen.center.x - screen.width * .12f, screen.bottom + 9) // F: lib/ui/kit/scenes/servers_cast.dart:83
            lineTo(screen.center.x - screen.width * .08f, screen.bottom + 12.5f) // F: lib/ui/kit/scenes/servers_cast.dart:84
            lineTo(screen.center.x + screen.width * .08f, screen.bottom + 12.5f) // F: lib/ui/kit/scenes/servers_cast.dart:85
            lineTo(screen.center.x + screen.width * .12f, screen.bottom + 9) // F: lib/ui/kit/scenes/servers_cast.dart:86
        }
    }

    internal fun paint(canvas: Canvas, palette: ScenePalette, t: Float) {
        if (t <= 0f) return
        canvas.drawRRect(
            screen,
            radius,
            SceneDraw.fill(SceneDraw.fade(palette.surface, SceneDraw.interval(t, .3f, 1f))), // F: lib/ui/kit/scenes/servers_cast.dart:97
        )
        SceneDraw.partialPath(
            canvas,
            outline,
            SceneDraw.interval(t, 0f, .7f), // F: lib/ui/kit/scenes/servers_cast.dart:102
            SceneDraw.pen(palette.muted, 4f), // F: lib/ui/kit/scenes/servers_cast.dart:103
        )
        SceneDraw.partialPath(
            canvas,
            base,
            SceneDraw.interval(t, .35f, .9f), // F: lib/ui/kit/scenes/servers_cast.dart:108
            SceneDraw.pen(palette.muted, 4f), // F: lib/ui/kit/scenes/servers_cast.dart:109
        )
        SceneDraw.partialPath(
            canvas,
            hinge,
            SceneDraw.interval(t, .6f, 1f), // F: lib/ui/kit/scenes/servers_cast.dart:114
            SceneDraw.pen(palette.line, SceneDraw.HAIRLINE),
        )
    }
}

/** The brand's "open portal" mark at any size: its two brackets in the mark's own geometry, scaled. */
public object ServersPortal {
    internal val upper: Path by lazy {
        scenePath(Offset(61f, 31f)) { // F: lib/ui/kit/scenes/servers_cast.dart:124
            lineTo(43f, 31f) // F: lib/ui/kit/scenes/servers_cast.dart:125
            arcTo(Offset(31f, 43f), 12f, clockwise = false) // F: lib/ui/kit/scenes/servers_cast.dart:127,128
            lineTo(31f, 64f) // F: lib/ui/kit/scenes/servers_cast.dart:131
        }
    }

    internal val lower: Path by lazy {
        scenePath(Offset(77f, 44f)) { // F: lib/ui/kit/scenes/servers_cast.dart:134
            lineTo(77f, 65f) // F: lib/ui/kit/scenes/servers_cast.dart:135
            arcTo(Offset(65f, 77f), 12f) // F: lib/ui/kit/scenes/servers_cast.dart:136
            lineTo(44f, 77f) // F: lib/ui/kit/scenes/servers_cast.dart:137
        }
    }

    /** Draws the mark centred on [center], [size] units across, each bracket drawing in over [t]; [dot] lands the spark. */
    internal fun paint(
        canvas: Canvas,
        center: Offset,
        size: Float,
        color: Color,
        t: Float,
        dot: Float = 0f,
        dotColor: Color? = null,
    ) {
        if (t <= 0f && dot <= 0f) return
        val scale = size / 60 // F: lib/ui/kit/scenes/servers_cast.dart:151
        canvas.save()
        canvas.translate(center.x, center.y)
        canvas.scale(scale, scale)
        canvas.translate(-54f, -54f) // F: lib/ui/kit/scenes/servers_cast.dart:155
        val pen = SceneDraw.pen(color, 10f) // F: lib/ui/kit/scenes/servers_cast.dart:156
        SceneDraw.partialPath(canvas, upper, SceneDraw.interval(t, 0f, .7f), pen) // F: lib/ui/kit/scenes/servers_cast.dart:157
        SceneDraw.partialPath(canvas, lower, SceneDraw.interval(t, .25f, 1f), pen) // F: lib/ui/kit/scenes/servers_cast.dart:158
        if (dot > 0f) {
            canvas.drawCircle(
                Offset(54f, 54f), // F: lib/ui/kit/scenes/servers_cast.dart:161
                6.5f * dot, // F: lib/ui/kit/scenes/servers_cast.dart:162
                SceneDraw.fill(dotColor ?: color),
            )
        }
        canvas.restore()
    }
}

/** A small four-point sparkle (decoration around a hero), [size] units. */
internal fun paintSparkle(canvas: Canvas, center: Offset, size: Float, paint: Paint) {
    canvas.drawLine(center + Offset(0f, -size), center + Offset(0f, size), paint)
    canvas.drawLine(center + Offset(-size, 0f), center + Offset(size, 0f), paint)
}
