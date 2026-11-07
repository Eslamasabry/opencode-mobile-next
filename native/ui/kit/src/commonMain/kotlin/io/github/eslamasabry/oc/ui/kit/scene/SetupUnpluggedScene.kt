package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import kotlin.math.cos
import kotlin.math.sin

/**
 * A server that is not answering (lib/ui/kit/scenes/setup_unplugged_scene.dart): the app's plug hangs just short of a
 * quiet, grey portal, with a few small marks in the gap. Ambient while the app keeps trying: the plug nudges towards
 * the portal and back, and a faint spark flickers inside it.
 */
public class SetupUnpluggedScene : SceneArt() {
    override val box: Size get() = Size(160f, 120f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:18

    internal companion object {
        val cable: Path by lazy {
            Path().apply {
                moveTo(40f, 60f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:21
                cubicTo(22f, 60f, 30f, 96f, 6f, 100f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:22
            }
        }
        val plug: Rect = Rect(40f, 50f, 60f, 70f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24
        const val PLUG_RADIUS: Float = 4f // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:25
        val portal: Offset = Offset(120f, 60f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27

        /** The gap's marks: short strokes fanning out between plug and portal. */
        val marks: List<Pair<Offset, Offset>> =
            listOf(-.9f, 0f, .9f).map { angle -> // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:31
                val d = Offset(cos(angle), sin(angle))
                (Offset(80f, 60f) + d * 5f) to (Offset(80f, 60f) + d * 11f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34
            }
    }

    override fun paint(canvas: Canvas, frame: SceneFrame) {
        val p = frame.palette
        val t = frame.entrance
        val reach = if (frame.looping) SceneDraw.wave(frame.loop) else 0f

        // A quiet neutral wash behind the portal, no louder than an accent wash (LOOK-36).
        val wash = SceneDraw.interval(t, 0f, .4f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:46
        canvas.drawCircle(
            portal,
            34 * (.7f + .3f * wash), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:49
            SceneDraw.fill(SceneDraw.fade(p.line, FlutterKitTokens.SCENE_WASH_ALPHA * wash)),
        )

        // The portal: grey, open, waiting.
        SetupCast.portal(
            canvas,
            center = portal,
            size = 48f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:57
            color = SceneDraw.fade(p.muted, .75f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:58
            upper = SceneDraw.interval(t, .1f, .55f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:59
            lower = SceneDraw.interval(t, .2f, .65f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:60
            width = SceneDraw.STROKE,
        )
        if (frame.looping) {
            canvas.drawCircle(
                portal,
                3.5f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:66
                SceneDraw.fill(SceneDraw.fade(p.accent, .5f * reach)), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:67
            )
        }

        // The cable and the plug, which nudges towards the portal and back.
        canvas.save()
        canvas.translate(5 * reach, 0f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:73
        SceneDraw.partialPath(
            canvas,
            cable,
            SceneDraw.interval(t, .3f, .7f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:77
            SceneDraw.pen(p.muted),
        )
        val plugIn = SceneDraw.interval(t, .5f, .8f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:80
        if (plugIn > 0f) {
            canvas.save()
            canvas.translate(50f, 60f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:83
            canvas.scale(plugIn, plugIn)
            canvas.translate(-50f, -60f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:85
            canvas.drawRRect(plug, PLUG_RADIUS, SceneDraw.fill(p.accentSoft))
            canvas.drawRRect(plug, PLUG_RADIUS, SceneDraw.pen(p.accent))
            val prong = SceneDraw.pen(p.accent, 3.5f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88
            canvas.drawLine(Offset(60f, 55.5f), Offset(69f, 55.5f), prong) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:89
            canvas.drawLine(Offset(60f, 64.5f), Offset(69f, 64.5f), prong) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:90
            canvas.restore()
        }
        canvas.restore()

        // The gap: a few short marks in the muted tone (LOOK-4: "not answering" is not "needs you").
        val marksIn = SceneDraw.interval(t, .75f, 1f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:97
        if (marksIn > 0f) {
            val pen =
                SceneDraw.pen(
                    SceneDraw.fade(p.muted, marksIn * (1 - .5f * reach)), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:100
                    SceneDraw.HAIRLINE,
                )
            for ((a, b) in marks) {
                canvas.drawLine(
                    lerp(a, b, 1 - marksIn) + Offset(6f, 0f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:105
                    b + Offset(6f, 0f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:106
                    pen,
                )
            }
        }

        // Two quiet dots, the family's detail.
        val dots = SceneDraw.interval(t, .6f, 1f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:113
        val dot = SceneDraw.fill(SceneDraw.fade(p.muted, .4f * dots)) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:114
        canvas.drawCircle(Offset(142f, 22f), 2.2f, dot) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:115
        canvas.drawCircle(Offset(24f, 30f), 1.8f, dot) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:116
    }
}
