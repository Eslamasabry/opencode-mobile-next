package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitCurves
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.fade
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.interval
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.pen
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.wave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The small drawn mark beside Stop while a reply is being written (states_working_scene.dart): the brand's two
 * brackets, small, with a spark inside. Entrance: the brackets draw in and the spark lands. While the reply streams
 * (ambient) the brackets breathe a unit apart and back and the spark travels slowly around the aperture, one breath
 * per loop. Under reduced motion it is the still mark.
 *
 * Drawn in a 48-unit box so the brand's stroke stays readable at 24 dp.
 */
public object StatesWorkingScene : KitDrawnScene {
    override val box: Size = Size(48f, 48f) // F: lib/ui/kit/scenes/states_working_scene.dart:20

    /** The mark's geometry (mark.svg, viewBox 24..84) mapped into 4..44 (states_working_scene.dart:22-25). */
    internal const val S: Float = 40f / 60f // F: lib/ui/kit/scenes/states_working_scene.dart:23

    internal fun p(x: Float, y: Float): Offset = Offset((x - 24) * S + 4, (y - 24) * S + 4) // F: lib/ui/kit/scenes/states_working_scene.dart:25

    internal const val RADIUS: Float = 12 * S // F: lib/ui/kit/scenes/states_working_scene.dart:26
    internal val CENTRE: Offset = Offset(24f, 24f) // F: lib/ui/kit/scenes/states_working_scene.dart:40
    internal const val PEN: Float = 5.5f // F: lib/ui/kit/scenes/states_working_scene.dart:47
    internal const val DRIFT: Float = 1.4f // F: lib/ui/kit/scenes/states_working_scene.dart:48
    internal const val ORBIT: Float = 5.0f // F: lib/ui/kit/scenes/states_working_scene.dart:65
    internal const val SPARK: Float = 3.6f // F: lib/ui/kit/scenes/states_working_scene.dart:74
    internal const val REST: Float = 3.8f // F: lib/ui/kit/scenes/states_working_scene.dart:76

    private val upper by lazy {
        Path().apply {
            p(61f, 31f).let { moveTo(it.x, it.y) } // F: lib/ui/kit/scenes/states_working_scene.dart:29
            p(43f, 31f).let { lineTo(it.x, it.y) } // F: lib/ui/kit/scenes/states_working_scene.dart:30
            sceneArcTo(p(43f, 31f), p(31f, 43f), RADIUS, clockwise = false) // F: lib/ui/kit/scenes/states_working_scene.dart:30,31
            p(31f, 64f).let { lineTo(it.x, it.y) } // F: lib/ui/kit/scenes/states_working_scene.dart:32
        }
    }
    private val lower by lazy {
        Path().apply {
            p(77f, 44f).let { moveTo(it.x, it.y) } // F: lib/ui/kit/scenes/states_working_scene.dart:35
            p(77f, 65f).let { lineTo(it.x, it.y) } // F: lib/ui/kit/scenes/states_working_scene.dart:36
            sceneArcTo(p(77f, 65f), p(65f, 77f), RADIUS) // F: lib/ui/kit/scenes/states_working_scene.dart:36,37
            p(44f, 77f).let { lineTo(it.x, it.y) } // F: lib/ui/kit/scenes/states_working_scene.dart:38
        }
    }

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val palette = frame.palette
        val t = frame.entrance
        val breath = if (frame.looping) wave(frame.loop) else 0f
        val drift = DRIFT * breath
        withTransform({ translate(-drift, -drift) }) {
            drawPartial(upper, interval(t, 0f, .55f), palette.accent, PEN) // F: lib/ui/kit/scenes/states_working_scene.dart:52
        }
        withTransform({ translate(drift, drift) }) {
            drawPartial(lower, interval(t, .2f, .75f), palette.accent, PEN) // F: lib/ui/kit/scenes/states_working_scene.dart:56
        }
        val land = interval(t, .6f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/states_working_scene.dart:59
        if (land <= 0f) return
        // The spark travels a small circle inside the aperture while working, with a faint trail behind it; at rest
        // it sits in the middle.
        if (frame.looping) {
            val angle = sparkAngle(frame.loop)
            drawArc(
                fade(palette.accent, .35f), // F: lib/ui/kit/scenes/states_working_scene.dart:71
                (angle - PI.toFloat() * .6f).sceneDegrees(), // F: lib/ui/kit/scenes/states_working_scene.dart:68
                (PI.toFloat() * .6f).sceneDegrees(), // F: lib/ui/kit/scenes/states_working_scene.dart:69
                false,
                Rect(CENTRE, ORBIT).topLeft,
                Rect(CENTRE, ORBIT).size,
                style = pen(2.2f), // F: lib/ui/kit/scenes/states_working_scene.dart:71
            )
            drawCircle(palette.accent, SPARK, CENTRE + Offset(cos(angle), sin(angle)) * ORBIT)
        } else {
            drawCircle(palette.accent, REST * land, CENTRE)
        }
    }

    /** Where the spark is on its orbit at [loop]: a quarter turn back, so it starts at the top (line 64). */
    public fun sparkAngle(loop: Float): Float = loop * 2 * PI.toFloat() - PI.toFloat() / 2 // F: lib/ui/kit/scenes/states_working_scene.dart:64
}
