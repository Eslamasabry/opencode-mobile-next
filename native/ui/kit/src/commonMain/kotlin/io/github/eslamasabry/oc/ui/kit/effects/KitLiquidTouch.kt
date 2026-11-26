package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionSpecs
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * How liquid glass answers a finger (owner 2026-11-10, "glass that reacts to a press").
 *
 * - A small pane that is itself the control (a chip, the jump pill, the round action button) swells a little under
 *   the finger and its edge catches more light; let go, it springs back and settles ([rememberKitLiquidPress],
 *   [kitLiquidSwell], [kitLiquidPressLight]). A quick tap still plays the whole swell.
 * - A large pane that holds controls (a bar's capsule, a menu) does not move: a soft light gathers in the glass under
 *   the finger and follows it, and the pane's edge brightens ([rememberKitLiquidTouch], [kitLiquidTouchWatch],
 *   [kitLiquidTouchLight]). The control's own press ink stays as it is.
 *
 * A pane joined to another (KitLiquidJoin.kt) hands its light to the joined panes, which draw it in the body's shape:
 * drawn in the pane's own shape it showed the pane's old outline inside the body.
 *
 * Only liquid and frosted glass faces answer; every other style keeps its own press feedback. Under reduced motion
 * nothing swells and the light comes and goes at once.
 */
internal object LiquidTouchRules {
    /** Gelatinous spring flex at full press (B-616: directional elastic stretch/squish, not flat uniform scale). */
    const val SWELL: Float = .065f
    const val FLEX_AXIS_BIAS: Float = .035f
    const val GEL_STIFFNESS: Float = 520f
    const val GEL_DAMPING: Float = 0.58f

    /** Inner press glow on any liquid glass control (B-616): blooms and spills across joined panes. */
    const val BLOOM_DP: Float = 84f
    const val BLOOM_DARK: Float = .24f
    const val BLOOM_LIGHT: Float = .42f

    /** Pressed glass specular edge boost (luminous white in BOTH dark and light modes, B-612/B-616). */
    const val EDGE_DARK: Float = .48f
    const val EDGE_LIGHT: Float = .44f
}

/** How far a small liquid pane is pressed and where the finger landed (for gelatinous flex + inner glow, B-616). */
@Stable
internal class KitLiquidPress {
    val amount: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var localAt: Offset by mutableStateOf(Offset.Unspecified)
    var origin: Offset by mutableStateOf(Offset.Zero)
}

/**
 * The press of the control [source] reports, for a small liquid pane; null when [on] is false (the part keeps its own
 * face). Pressing flexes gelatinously with an elastic spring; letting go completes the impulse then rebounds smoothly.
 */
@Composable
internal fun rememberKitLiquidPress(source: InteractionSource?, on: Boolean): KitLiquidPress? {
    if (!on || source == null) return null
    val press = remember { KitLiquidPress() }
    val motion = KitMotion.state
    val specs = remember(motion) { KitMotionSpecs(motion) }
    val gelSpring =
        remember(motion, specs) {
            specs.liquid<Float>(LiquidTouchRules.GEL_DAMPING, LiquidTouchRules.GEL_STIFFNESS)
        }
    LaunchedEffect(source, specs, gelSpring) {
        var job: Job? = null
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    press.localAt = interaction.pressPosition
                    job?.cancel()
                    job = launch { press.amount.animateTo(1f, specs.effectsFast()) }
                }
                is PressInteraction.Release -> {
                    job?.cancel()
                    job = launch {
                        if (press.amount.value < 1f) press.amount.animateTo(1f, specs.effectsFast())
                        press.amount.animateTo(0f, gelSpring)
                    }
                }
                is PressInteraction.Cancel -> {
                    job?.cancel()
                    job = launch { press.amount.animateTo(0f, gelSpring) }
                }
            }
        }
    }
    return press
}

/**
 * Gelatinous directional spring flex for a small liquid glass control (B-616): stretches slightly along the touch
 * axis and squishes softly on the orthogonal axis like elastic liquid glass. Instant no-op under reduced motion.
 */
@Composable
internal fun Modifier.kitLiquidSwell(press: KitLiquidPress?): Modifier {
    if (press == null || KitMotion.state.reduced) return this
    return onGloballyPositioned { press.origin = it.positionInWindow() }.graphicsLayer {
        val a = press.amount.value
        val at = press.localAt
        val nx = if (at.isSpecified && size.width > 1f) ((at.x / size.width) * 2f - 1f).coerceIn(-1f, 1f) else 0f
        val ny = if (at.isSpecified && size.height > 1f) ((at.y / size.height) * 2f - 1f).coerceIn(-1f, 1f) else 0f
        val axisBias = (kotlin.math.abs(nx) - kotlin.math.abs(ny)) * LiquidTouchRules.FLEX_AXIS_BIAS * a
        scaleX = (1f + LiquidTouchRules.SWELL * a + axisBias).coerceAtLeast(0.85f)
        scaleY = (1f + LiquidTouchRules.SWELL * a - axisBias * 0.65f).coerceAtLeast(0.85f)
    }
}

/**
 * A pressed small pane's inner illumination glow and luminous specular edge along [shape] (B-616).
 * When joined to another glass body, hands both touch position and glow intensity to the joined shader so inner
 * illumination visibly spills onto the neighbouring glass body!
 */
@Composable
internal fun Modifier.kitLiquidPressLight(press: KitLiquidPress?, shape: Shape, join: KitLiquidJoinPart? = null): Modifier {
    if (press == null) return this
    val dark = LocalKitRoles.current.dark
    if (join != null) {
        DisposableEffect(join, press) {
            join.touchAt = { if (press.localAt.isSpecified) press.origin + press.localAt else Offset.Unspecified }
            join.touchLight = { press.amount.value }
            onDispose {
                join.touchAt = NoTouchAt
                join.touchLight = NoTouchLight
            }
        }
    }
    return drawWithContent {
        drawContent()
        val a = press.amount.value.coerceIn(0f, 1f)
        if (a > 0f && join?.composedJoined != true) {
            val centre = if (press.localAt.isSpecified) press.localAt else Offset(size.width * 0.5f, size.height * 0.5f)
            val radius = maxOf(size.maxDimension * 0.85f, LiquidTouchRules.BLOOM_DP.dp.toPx() * 0.65f)
            val strength = (if (dark) LiquidTouchRules.BLOOM_DARK else LiquidTouchRules.BLOOM_LIGHT) * a
            val outline = shape.createOutline(size, layoutDirection, this)
            clipPath(Path().apply { addOutline(outline) }) {
                val glow = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = strength), Color.Transparent),
                    center = centre,
                    radius = radius,
                )
                drawCircle(
                    glow,
                    radius = radius,
                    center = centre,
                    blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
                )
            }
            drawPressedEdge(shape, dark, a)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawPressedEdge(shape: Shape, dark: Boolean, a: Float) {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline,
        color = Color.White.copy(alpha = (if (dark) LiquidTouchRules.EDGE_DARK else LiquidTouchRules.EDGE_LIGHT) * a),
        style = Stroke(1.25.dp.toPx()),
        blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
    )
}

/** Where a finger rests on a large liquid pane and how strongly its light shows (0–1). */
@Stable
public class KitLiquidTouch internal constructor() {
    internal val light: Animatable<Float, AnimationVector1D> = Animatable(0f)
    internal var at: Offset by mutableStateOf(Offset.Unspecified)

    /** Where the part that watches the finger is in the window, px ([at] is measured from it). */
    internal var origin: Offset by mutableStateOf(Offset.Zero)
}

/** A large liquid pane's touch light; null when [on] is false (the part is not glass here). */
@Composable
public fun rememberKitLiquidTouch(on: Boolean): KitLiquidTouch? = if (on) remember { KitLiquidTouch() } else null

/**
 * Watches fingers on this part and on everything inside it, taking nothing from them: the controls inside keep their
 * taps, drags and ink. Positions are this part's own.
 */
@Composable
public fun Modifier.kitLiquidTouchWatch(touch: KitLiquidTouch?): Modifier {
    if (touch == null) return this
    val motion = KitMotion.state
    val specs = remember(motion) { KitMotionSpecs(motion) }
    return onGloballyPositioned { touch.origin = it.positionInWindow() }.pointerInput(touch, specs) {
        coroutineScope {
            var job: Job? = null
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touch.at = down.position
                job?.cancel()
                job = launch { touch.light.animateTo(1f, specs.effectsFast()) }
                var held = true
                while (held) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) held = false else touch.at = change.position
                }
                job?.cancel()
                job = launch {
                    if (touch.light.value < 1f) touch.light.animateTo(1f, specs.effectsFast())
                    touch.light.animateTo(0f, specs.effects())
                }
            }
        }
    }
}

/**
 * The light a finger gathers in a large liquid pane of [shape], and the pane's brighter edge while it is touched. Drawn
 * over the glass and under the pane's own content. [inset] is this pane's place inside the part that watches the
 * finger ([kitLiquidTouchWatch]), in px. [join]: the pane's part of a joined body (KitLiquidJoin.kt); joined, the light
 * is drawn by the joined panes in the body's shape and nothing is drawn here.
 */
@Composable
public fun Modifier.kitLiquidTouchLight(
    touch: KitLiquidTouch?,
    shape: Shape,
    inset: () -> Offset = { Offset.Zero },
    join: KitLiquidJoinPart? = null,
): Modifier {
    if (touch == null) return this
    val dark = LocalKitRoles.current.dark
    if (join != null) {
        DisposableEffect(join, touch) {
            join.touchAt = { if (touch.at.isSpecified) touch.origin + touch.at else Offset.Unspecified }
            join.touchLight = { touch.light.value }
            onDispose {
                join.touchAt = NoTouchAt
                join.touchLight = NoTouchLight
            }
        }
    }
    return drawWithContent {
        val a = touch.light.value.coerceIn(0f, 1f)
        val at = touch.at
        if (a > 0f && at.isSpecified && join?.composedJoined != true) {
            val centre = at - inset()
            val radius = LiquidTouchRules.BLOOM_DP.dp.toPx()
            val strength = (if (dark) LiquidTouchRules.BLOOM_DARK else LiquidTouchRules.BLOOM_LIGHT) * a
            val outline = shape.createOutline(size, layoutDirection, this)
            clipPath(Path().apply { addOutline(outline) }) {
                val glow = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = strength), Color.Transparent),
                    center = centre,
                    radius = radius,
                )
                drawCircle(
                    glow,
                    radius = radius,
                    center = centre,
                    blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
                )
            }
            drawPressedEdge(shape, dark, a)
        }
        drawContent()
    }
}
