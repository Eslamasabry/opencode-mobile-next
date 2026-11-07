package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Setup finished (lib/ui/kit/scenes/setup_ready_scene.dart, a celebration): the phone lights, the portal on its screen
 * springs open around a check, rays burst out and fade, and sparkles and confetti settle around it. It plays once and
 * then rests; with reduced motion it is simply there, finished.
 */
public class SetupReadyScene : SceneArt() {
    override val box: Size get() = Size(160f, 120f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:17

    internal companion object {
        val center: Offset = Offset(80f, 60f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:19
        val portal: Offset = Offset(80f, 58f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:20
        val phoneFrame: Path by lazy {
            SetupCast.phone(
                Rect(54f, 8f, 106f, 112f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:22
                12f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:23
            )
        }
        val screen: Rect = Rect(61f, 17f, 99f, 101f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:26
        const val SCREEN_RADIUS: Float = 5f // F: lib/ui/kit/scenes/setup_ready_scene.dart:27
        val check: Path by lazy { SetupCast.check(portal, 15f) } // F: lib/ui/kit/scenes/setup_ready_scene.dart:29

        /** The burst: eight rays, evenly around the phone, a little off the axes. */
        val rays: List<Offset> =
            (0 until 8).map { i -> // F: lib/ui/kit/scenes/setup_ready_scene.dart:33
                Offset(cos(i * PI / 4 + .3).toFloat(), sin(i * PI / 4 + .3).toFloat()) // F: lib/ui/kit/scenes/setup_ready_scene.dart:34
            }

        /** Confetti that stays: short dashes at rest, each (centre, angle, muted). */
        val confetti: List<Triple<Offset, Float, Boolean>> =
            listOf(
                Triple(Offset(28f, 30f), .6f, false), // F: lib/ui/kit/scenes/setup_ready_scene.dart:39
                Triple(Offset(136f, 72f), -.5f, false), // F: lib/ui/kit/scenes/setup_ready_scene.dart:40
                Triple(Offset(124f, 100f), .9f, true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:41
                Triple(Offset(24f, 76f), -.8f, true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:42
                Triple(Offset(140f, 20f), .2f, true), // F: lib/ui/kit/scenes/setup_ready_scene.dart:43
            )
    }

    override fun paint(canvas: Canvas, frame: SceneFrame) {
        val p = frame.palette
        val t = frame.entrance

        val wash = SceneDraw.interval(t, 0f, .4f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:51
        canvas.drawCircle(
            center,
            50 * (.7f + .3f * wash), // F: lib/ui/kit/scenes/setup_ready_scene.dart:54
            SceneDraw.fill(SceneDraw.fade(p.accentSoft, wash)),
        )

        val lit = SceneDraw.interval(t, .1f, .35f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:58
        if (lit > 0f) {
            canvas.drawRRect(screen, SCREEN_RADIUS, SceneDraw.fill(SceneDraw.fade(p.surface, lit)))
            canvas.drawRRect(
                screen,
                SCREEN_RADIUS,
                SceneDraw.fill(SceneDraw.fade(p.accentSoft, lit * 1.4f)), // F: lib/ui/kit/scenes/setup_ready_scene.dart:63
            )
        }
        SceneDraw.partialPath(
            canvas,
            phoneFrame,
            SceneDraw.interval(t, 0f, .35f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:69
            SceneDraw.pen(p.ink),
        )
        canvas.drawLine(
            Offset(74f, 106.5f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:73
            Offset(74 + 12 * SceneDraw.interval(t, .3f, .4f), 106.5f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:74
            SceneDraw.pen(SceneDraw.fade(p.muted, .8f * lit), 2f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:75
        )

        // The portal springs open: closed brackets overshoot to open.
        val draw = SceneDraw.interval(t, .25f, .5f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:79
        val open = SceneDraw.interval(t, .45f, .8f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_ready_scene.dart:80
        SetupCast.portal(
            canvas,
            center = portal,
            size = 34f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:84
            color = p.accent,
            upper = draw,
            lower = draw,
            open = -3 + 5.5f * open, // F: lib/ui/kit/scenes/setup_ready_scene.dart:88
            width = 4.5f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:89
        )
        SceneDraw.partialPath(
            canvas,
            check,
            SceneDraw.interval(t, .6f, .85f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:94
            SceneDraw.pen(p.accent, 3.5f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:95
        )

        // Rays shoot out and fade: the moment, not the rest.
        val burst = SceneDraw.interval(t, .55f, 1f, SceneDraw.enter) // F: lib/ui/kit/scenes/setup_ready_scene.dart:99
        if (burst > 0f && burst < 1f) {
            val pen = SceneDraw.pen(SceneDraw.fade(p.accent, 1 - burst), SceneDraw.HAIRLINE)
            for (ray in rays) {
                val inner = 34 + 26 * burst // F: lib/ui/kit/scenes/setup_ready_scene.dart:106
                val outer = inner + 8 * (1 - burst * .6f) // F: lib/ui/kit/scenes/setup_ready_scene.dart:107
                canvas.drawLine(
                    center + Offset(ray.x * inner * 1.2f, ray.y * inner), // F: lib/ui/kit/scenes/setup_ready_scene.dart:109
                    center + Offset(ray.x * outer * 1.2f, ray.y * outer), // F: lib/ui/kit/scenes/setup_ready_scene.dart:110
                    pen,
                )
            }
        }

        // What stays: sparkles and confetti.
        val big = SceneDraw.interval(t, .6f, .9f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_ready_scene.dart:117
        val small = SceneDraw.interval(t, .7f, 1f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_ready_scene.dart:118
        SetupCast.sparkle(canvas, Offset(126f, 30f), 10f, p.accent, pop = big) // F: lib/ui/kit/scenes/setup_ready_scene.dart:119
        SetupCast.sparkle(
            canvas,
            Offset(34f, 50f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:122
            6f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:123
            SceneDraw.fade(p.accent, .8f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:124
            pop = small,
        )
        SetupCast.sparkle(
            canvas,
            Offset(118f, 88f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:129
            4.5f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:130
            SceneDraw.fade(p.accent, .6f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:131
            pop = small,
        )
        confetti.forEachIndexed { index, (at, angle, muted) ->
            val pop =
                SceneDraw.interval(
                    t,
                    .6f + index * .05f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:137
                    .85f + index * .03f, // F: lib/ui/kit/scenes/setup_ready_scene.dart:138
                    SceneDraw.land,
                )
            if (pop <= 0f) return@forEachIndexed
            val d = Offset(cos(angle), sin(angle)) * 3.5f * pop // F: lib/ui/kit/scenes/setup_ready_scene.dart:142
            canvas.drawLine(
                at - d,
                at + d,
                SceneDraw.pen(
                    if (muted) SceneDraw.fade(p.muted, .6f) else SceneDraw.fade(p.accent, .9f), // F: lib/ui/kit/scenes/setup_ready_scene.dart:147
                    SceneDraw.HAIRLINE,
                ),
            )
        }
    }
}
