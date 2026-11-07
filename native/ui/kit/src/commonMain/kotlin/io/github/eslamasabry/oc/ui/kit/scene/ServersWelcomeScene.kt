package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import kotlin.math.floor

/**
 * The Servers welcome's hero (lib/ui/kit/scenes/servers_welcome_scene.dart, no servers yet): the computer where the
 * agent runs, the portal on its screen, and this phone in front with a dotted path between them. Entrance only (a
 * resting screen never loops): the laptop draws itself, the portal opens on it, the phone joins, the path dots across
 * and a spark lands in the portal.
 */
public class ServersWelcomeScene : SceneArt() {
    override val box: Size get() = Size(200f, 120f) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:20

    internal companion object {
        val laptop: ServersLaptop = ServersLaptop(Rect(78f, 14f, 176f, 78f)) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:22
        val phone: ServersPhone = ServersPhone(Rect(24f, 52f, 56f, 108f)) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:23
        val portal: Offset = Offset(127f, 46f) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:24

        val path: Path by lazy {
            Path().apply {
                moveTo(40f, 44f) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:27
                quadraticTo(42f, 8f, 88f, 9f) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:28
            }
        }

        /** Spacing of the path's dots, in scene units. */
        const val DOT_STEP: Float = 6.5f // F: lib/ui/kit/scenes/servers_welcome_scene.dart:31

        val dots: List<Offset> by lazy {
            val metric = PathMeasure().apply { setPath(path, false) }
            generateSequence(0f) { it + DOT_STEP }.takeWhile { it <= metric.length }.map { metric.getPosition(it) }.toList()
        }

        /** How many of the path's dots show at entrance [t] (Dart line 52). */
        fun shownDots(t: Float, count: Int): Int =
            floor(SceneDraw.interval(t, .55f, .9f) * count).toInt() // F: lib/ui/kit/scenes/servers_welcome_scene.dart:52
    }

    override fun paint(canvas: Canvas, frame: SceneFrame) {
        val palette = frame.palette
        val t = frame.entrance

        laptop.paint(canvas, palette, SceneDraw.interval(t, 0f, .5f)) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:40
        ServersPortal.paint(
            canvas,
            portal,
            36f, // F: lib/ui/kit/scenes/servers_welcome_scene.dart:44
            palette.accent,
            SceneDraw.interval(t, .25f, .75f), // F: lib/ui/kit/scenes/servers_welcome_scene.dart:46
            dot = SceneDraw.interval(t, .75f, 1f, SceneDraw.land), // F: lib/ui/kit/scenes/servers_welcome_scene.dart:47
        )
        phone.paint(canvas, palette, SceneDraw.interval(t, .3f, .8f)) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:49

        // The path from the phone to the computer, one dot at a time.
        val shown = shownDots(t, dots.size)
        val dot = SceneDraw.fill(palette.accent)
        for (i in 0 until shown) canvas.drawCircle(dots[i], 1.8f, dot) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:55

        // Two small sparkles, the scene's only decoration.
        val sparkle = SceneDraw.interval(t, .8f, 1f) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:59
        if (sparkle > 0f) {
            val pen = SceneDraw.pen(SceneDraw.fade(palette.accent, .55f * sparkle), SceneDraw.HAIRLINE) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:62
            paintSparkle(canvas, Offset(188f, 26f), 4.5f * sparkle, pen) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:65
            paintSparkle(canvas, Offset(14f, 28f), 3.5f * sparkle, pen) // F: lib/ui/kit/scenes/servers_welcome_scene.dart:66
        }
    }
}
