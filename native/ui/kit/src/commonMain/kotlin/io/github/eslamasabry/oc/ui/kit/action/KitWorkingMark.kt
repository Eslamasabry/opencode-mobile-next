package io.github.eslamasabry.oc.ui.kit.action

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion

private const val ARC_SWEEP = 270f
private const val FULL_TURN = 360f
private const val STROKE_SHARE = 0.14f

/**
 * The small "working" mark a control shows for the tap in flight (Dart `_Spinner`): a turning arc, read in the draw
 * phase; a still dot when loops are not allowed (motion Off, Calm, tests and screenshots; plan 08 MOT-7).
 */
@Composable
internal fun KitWorkingMark(color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    val motion = KitMotion.state
    if (!motion.ambientLoops) {
        Canvas(modifier.size(size)) { drawCircle(color, radius = this.size.minDimension / 6f) }
        return
    }
    val turn =
        rememberInfiniteTransition(label = "kit-working").animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN,
            animationSpec = infiniteRepeatable(tween(MotionTokens.SPINNER_LAP, easing = LinearEasing), RepeatMode.Restart),
            label = "turn",
        )
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension * STROKE_SHARE
        rotate(turn.value) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = ARC_SWEEP,
                useCenter = false,
                topLeft = Offset(w / 2, w / 2),
                size = Size(this.size.width - w, this.size.height - w),
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
        }
    }
}
