package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.SignalPalette
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.composer.rememberLoopPhase

/**
 * Decorative node; status is spoken by the tool row's words. Running never replaces the tool's
 * identity.
 */
@Composable
internal fun KitToolNode(view: KitToolView, size: Dp, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val motion = KitMotion.state
    val running = view.status == KitToolStatus.Running
    val transition = remember(view.id) { Animatable(1f) }
    var previous by remember(view.id) { mutableStateOf(view.status) }
    var finishedFromRunning by remember(view.id) { mutableStateOf(false) }
    LaunchedEffect(view.status) {
        finishedFromRunning = previous == KitToolStatus.Running && !running
        previous = view.status
        if (finishedFromRunning && !motion.reduced) {
            transition.snapTo(0f)
            transition.animateTo(
                1f,
                tween(
                    when (view.status) {
                        KitToolStatus.Done -> MotionTokens.STEP_TICK + MotionTokens.STEP_SPRING
                        KitToolStatus.Stopped -> MotionTokens.STEP_FADE
                        else -> MotionTokens.STEP_FLASH
                    }
                ),
            )
        }
    }
    if (LocalKitAppearance.current.style == KitSurfaceStyle.Signal) {
        val palette = SignalPalette.of(colors.dark)
        val pulse =
            rememberLoopPhase(
                running && motion.ambientLoops,
                MotionTokens.STEP_BREATHE,
                1f,
                still = .5f,
            )
        if (view.status == KitToolStatus.Failed) {
            KitToolGlyph(view.kind, colors.text2, size, modifier)
            return
        }
        Canvas(modifier.size(size)) {
            when (view.status) {
                KitToolStatus.Running,
                KitToolStatus.Pending -> {
                    val width = this.size.minDimension * .55f
                    drawRoundRect(
                        Color(palette.head.v),
                        topLeft = Offset((this.size.width - width) / 2f, 0f),
                        size = androidx.compose.ui.geometry.Size(width, this.size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f),
                    )
                    val lit =
                        if (running) Color(SignalPalette.LampGreen.v) else Color(palette.siding.v)
                    val alpha =
                        if (running && motion.ambientLoops)
                            .65f + .35f * kotlin.math.abs(pulse.floatValue * 2f - 1f)
                        else 1f
                    drawCircle(lit, radius = width * .3f, center = center, alpha = alpha)
                }
                else ->
                    drawCircle(
                        colors.text2,
                        radius = minOf(6.dp.toPx(), this.size.minDimension / 3f),
                        style = Stroke(1.5.dp.toPx()),
                    )
            }
        }
        return
    }
    // Flutter's step node: the bare tool glyph (no tile); a running step's glyph turns live and the step light
    // draws its rounded square around it.
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val showTick =
            finishedFromRunning &&
                view.status == KitToolStatus.Done &&
                transition.value <
                    MotionTokens.STEP_TICK.toFloat() /
                        (MotionTokens.STEP_TICK + MotionTokens.STEP_SPRING)
        if (showTick)
            Canvas(Modifier.size(size * .6f)) {
                drawLine(
                    colors.success,
                    Offset(0f, this.size.height * .5f),
                    Offset(this.size.width * .35f, this.size.height * .85f),
                    2.dp.toPx(),
                )
                drawLine(
                    colors.success,
                    Offset(this.size.width * .35f, this.size.height * .85f),
                    Offset(this.size.width, this.size.height * .1f),
                    2.dp.toPx(),
                )
            }
        else KitToolGlyph(view.kind, if (running) colors.live else colors.text2, if (running) size * .7f else size * .9f)
        if (running)
            KitStepLight(KitStepLightState.Working, colors.live, size, animate = !motion.reduced)
        else if (finishedFromRunning && transition.value < 1f && view.status != KitToolStatus.Done)
            Canvas(Modifier.size(size)) {
                drawRoundRect(
                    if (view.status == KitToolStatus.Stopped) colors.accent else colors.text2,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.width * .3f),
                    alpha = 1f - transition.value,
                    style = Stroke(1.8.dp.toPx()),
                )
            }
    }
}
