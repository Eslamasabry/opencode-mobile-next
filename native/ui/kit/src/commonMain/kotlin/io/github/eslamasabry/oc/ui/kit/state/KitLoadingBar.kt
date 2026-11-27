package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.round

/** Under reduced motion a waiting bar is a still segment of this share at the start (KitProgress.md). */
internal const val STILL_WAITING_END: Float = 0.3f
private const val SEGMENT: Float = 0.35f
private const val DASH_DP: Int = 8
private const val DASH_STEP_DP: Int = 16

/**
 * The screen's one loading indicator (Dart `KitLoadingBar`; design standard §4): a 2 dp bar under the top bar while
 * something loads for the first time. It always takes its 2 dp, so the page never jumps, and carries [label] for screen
 * readers. One per screen (`KitScreenAudit`).
 *
 * States: idle, loading
 * Text: none (label is semantics only)
 * Cap: Fill
 * Motion: a segment sweeps along the bar; under Off, Calm and still frames it is a still 30 % segment
 * Replaces: KitLoadingBar (lib/ui/kit/kit_progress.dart)
 */
@Composable
public fun KitLoadingBar(loading: Boolean, label: String, modifier: Modifier = Modifier) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val base = modifier.fillMaxWidth().height(tokens.loadingBar.dp)
    if (!loading) {
        Canvas(base) {}
        return
    }
    val sweep = sweepPhase()
    Canvas(
        base.testTag("kit-loading-bar").semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
    ) {
        drawRect(colors.surface3)
        val phase = sweep?.value
        if (phase == null) {
            drawStillDashes(colors.accent, rtl)
        } else {
            val from = (phase * (1f + SEGMENT) - SEGMENT).coerceAtLeast(0f)
            val to = (phase * (1f + SEGMENT)).coerceAtMost(1f)
            val start = if (rtl) size.width * (1f - to) else size.width * from
            drawRect(colors.accent, topLeft = Offset(start, 0f), size = Size(size.width * (to - from), size.height))
        }
    }
}

/** The sweep's phase, or null when loops are not allowed (the still segment draws instead). */
@Composable
internal fun sweepPhase(): State<Float>? {
    if (!KitMotion.state.ambientLoops) return null
    return rememberInfiniteTransition(label = "kit-loading").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MotionTokens.SPINNER_LAP, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
}

private fun DrawScope.drawStillDashes(color: Color, rtl: Boolean) {
    if (size.width <= 0f) return
    val dash = DASH_DP.dp.toPx()
    val step = DASH_STEP_DP.dp.toPx()
    val n = ((size.width + dash) / step).toInt()
    if (n <= 1) {
        val w = minOf(dash, size.width)
        val left = if (rtl) size.width - w else 0f
        drawRect(color, topLeft = Offset(left, 0f), size = Size(w, size.height))
        return
    }
    val gap = (size.width - n * dash) / (n - 1)
    for (i in 0 until n) {
        val x = if (i == n - 1) size.width - dash else round(i * (dash + gap))
        val left = if (rtl) size.width - x - dash else x
        drawRect(color, topLeft = Offset(left, 0f), size = Size(dash, size.height))
    }
}
