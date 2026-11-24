package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
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
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.icon.KitStatusIcon
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.composer.rememberLoopPhase

/**
 * Decorative node; status is spoken by the tool row's words. Running never replaces the tool's
 * identity.
 */
@Composable
internal fun KitToolNode(view: KitToolView, size: Dp, modifier: Modifier = Modifier, onRail: Boolean = false) {
    val colors = LocalKitColors.current
    val motion = KitMotion.state
    val running = view.status == KitToolStatus.Running
    val mem = remember(view.id) { NodeMemory(view.status) }
    val finished =
        if (mem.status != view.status) mem.status == KitToolStatus.Running && !running
        else mem.fromRunning
    SideEffect {
        if (mem.status != view.status) {
            mem.fromRunning = finished
            mem.status = view.status
        }
    }
    val transition = remember(view.id, view.status) { Animatable(if (finished && !motion.reduced) 0f else 1f) }
    LaunchedEffect(view.id, view.status, motion.reduced) {
        if (motion.reduced) {
            if (transition.value < 1f) transition.snapTo(1f)
        } else if (finished && transition.value < 1f) {
            transition.animateTo(1f, tween(stepTransitionMs(view.status)))
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
                KitToolStatus.Pending,
                KitToolStatus.WaitingForYou -> {
                    val width = this.size.minDimension * .55f
                    drawRoundRect(
                        Color(palette.head.v),
                        topLeft = Offset((this.size.width - width) / 2f, 0f),
                        size = androidx.compose.ui.geometry.Size(width, this.size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f),
                    )
                    val lit =
                        when {
                            running -> Color(SignalPalette.LampGreen.v)
                            view.status == KitToolStatus.WaitingForYou -> Color(SignalPalette.LampRed.v)
                            else -> Color(palette.siding.v)
                        }
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
    if (onRail) {
        RailStepNode(view, size, modifier, finished, transition.value)
        return
    }
    // Flutter's step node: the bare tool glyph (no tile); a running step's glyph turns live and the step light
    // draws its rounded square around it.
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val showTick =
            finished &&
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
        // A lone running step: Flutter's `KitStepTimeline.alone`, the glyph in the accent with the light round it (no
        // rail above or below); the glyph keeps one size so nothing jumps when it starts or ends.
        else if (running) {
            // Alone, the glyph keeps the size it has at rest.
            val scale = GLYPH_SHARE / GLYPH_SHARE_FRAMED
            KitStepTile(view.icon ?: view.kind.icon(), KitStepNodeLook.Running, size, glyphScale = scale)
        } else KitToolGlyph(view.icon ?: view.kind.icon(), colors.text2, size * GLYPH_SHARE)
        val settling = finished && transition.value < 1f
        if (!running && settling && view.status != KitToolStatus.Done)
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

private class NodeMemory(var status: KitToolStatus, var fromRunning: Boolean = false)

private const val SPRING_START_SCALE = .4f
private const val SPRING_SPAN_SCALE = .6f

private fun stepTransitionMs(status: KitToolStatus): Int =
    when (status) {
        KitToolStatus.Done -> MotionTokens.STEP_TICK + MotionTokens.STEP_SPRING
        KitToolStatus.Stopped -> MotionTokens.STEP_FADE
        else -> MotionTokens.STEP_FLASH
    }

private fun railNodeLook(status: KitToolStatus): KitStepNodeLook =
    when (status) {
        KitToolStatus.Running -> KitStepNodeLook.Running
        KitToolStatus.Pending -> KitStepNodeLook.Queued
        KitToolStatus.WaitingForYou -> KitStepNodeLook.Waiting
        else -> KitStepNodeLook.Plain
    }

@Composable
private fun RailStepNode(
    view: KitToolView,
    size: Dp,
    modifier: Modifier,
    finishedFromRunning: Boolean,
    progress: Float,
) {
    // Flutter's step node (kit_step_light.dart `_StepLightState.build`): the glyph in its small tile, the light round
    // it while the step runs; a step that finishes flashes once (a tick that springs back to the glyph, a neutral
    // outline that fades before the failed mark, the accent outline fading on a stop).
    val colors = LocalKitColors.current
    val icon = view.icon ?: view.kind.icon()
    val tickShare = MotionTokens.STEP_TICK.toFloat() / (MotionTokens.STEP_TICK + MotionTokens.STEP_SPRING)
    val settling = finishedFromRunning && progress < 1f
    when (view.status) {
        KitToolStatus.Failed ->
            if (settling) {
                Box(modifier.size(size), contentAlignment = Alignment.Center) {
                    KitStepTile(icon, KitStepNodeLook.Plain, size)
                    StillOutline(colors.text2.copy(alpha = 1f - progress), size)
                }
            } else KitStatusIcon(StatusTone.Failure, modifier)
        KitToolStatus.Stopped ->
            Box(modifier.size(size), contentAlignment = Alignment.Center) {
                KitStepTile(icon, KitStepNodeLook.Plain, size)
                if (settling) StillOutline(colors.accent.copy(alpha = 1f - progress), size)
            }
        else -> {
            // One KitStepTile call site across Running -> Done so #kit-step-node keeps its identity and never blinks.
            val doneSettling = view.status == KitToolStatus.Done && settling
            val showTick = doneSettling && progress < tickShare
            val scale =
                if (doneSettling && !showTick) {
                    SPRING_START_SCALE + SPRING_SPAN_SCALE * ((progress - tickShare) / (1f - tickShare))
                } else 1f
            KitStepTile(
                icon = if (showTick) KitIcons.check else icon,
                look = railNodeLook(view.status),
                size = size,
                modifier = modifier,
                glyphColor = if (showTick) colors.success else null,
                glyphScale = scale,
            )
        }
    }
}
