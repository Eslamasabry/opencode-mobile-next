package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.modal.KitPredictiveBack
import io.github.eslamasabry.oc.ui.kit.modal.PREDICTIVE_SHRINK
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The grow's pure rules (LAMP-CRAFT §3). */
public object GrowFromAnchorRules {
    /** The content takes touches only once the body has grown this far, so a double tap opens nothing by accident. */
    public const val TOUCH_AT: Float = 0.6f

    /** The content starts to fade in this long into the open (ms). */
    public const val CONTENT_IN_DELAY_MS: Int = 70

    /** The body starts back into its anchor this long after the content starts to fade (ms). */
    public const val BODY_OUT_DELAY_MS: Int = 40

    /** The body's bounds at [fraction] (0 = the anchor, 1 = the pane), never past either end. */
    public fun bounds(anchor: Rect, pane: Rect, fraction: Float): Rect {
        val t = fraction.coerceIn(0f, 1f)
        return Rect(mix(anchor.left, pane.left, t), mix(anchor.top, pane.top, t), mix(anchor.right, pane.right, t), mix(anchor.bottom, pane.bottom, t))
    }

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Where the predictive-back shrink is centred: the anchor's centre, as a share of the pane. */
    public fun origin(anchor: Rect, pane: Rect): TransformOrigin {
        if (pane.width <= 0f || pane.height <= 0f) return TransformOrigin.Center
        val c = anchor.center
        return TransformOrigin(((c.x - pane.left) / pane.width).coerceIn(0f, 1f), ((c.y - pane.top) / pane.height).coerceIn(0f, 1f))
    }
}

/**
 * Where a [KitGrowFromAnchor] stands now, readable without recomposing: [fraction] (0 = folded into the anchor, 1 =
 * the whole pane), [contentShown] (the content's fade), [backProgress] (the predictive-back gesture, 0–1, eased): a
 * scrim behind the pane lightens with it.
 */
@Stable
public class KitGrowFromAnchorState internal constructor(open: Boolean) {
    internal val body: Animatable<Float, AnimationVector1D> = Animatable(if (open) 1f else 0f)
    internal val content: Animatable<Float, AnimationVector1D> = Animatable(if (open) 1f else 0f)
    internal var back: () -> Float = { 0f }
    internal var anchorInPane: Rect by mutableStateOf(Rect.Zero)

    public val fraction: Float get() = body.value
    public val contentShown: Float get() = content.value
    public fun backProgress(): Float = back()

    /** Fully folded and still: the pane is not drawn at all. */
    public val folded: Boolean get() = body.value <= 0f && body.targetValue <= 0f && !body.isRunning
}

@Composable
public fun rememberKitGrowFromAnchorState(open: Boolean = false): KitGrowFromAnchorState = remember { KitGrowFromAnchorState(open) }

/**
 * A pane that grows out of a small anchor (the edge light) and goes back into it (LAMP-CRAFT §3). Its body's bounds
 * move from [anchorInWindow] (a rect in window px; read when laid out, so an anchor that moves keeps its pane honest)
 * to the pane's own bounds: a bounds morph with a clip, the content laid out once at full size and never scaled
 * (MOT-2). The body is the style's face for [role] at [level], by role (Glass draws its own Glass roles), with
 * [shape]'s corners; its outline follows the moving bounds whole, never cut.
 *
 * [open] turns it either way at any moment: a change mid-flight turns the body around from where it is, with its
 * velocity (springs). Opening: the body grows on `spatial`; the content fades in and rises
 * [MotionTokens.ENTRANCE_RISE_DP] over QUICK from 70 ms in. Closing: the content fades first (QUICK), then the body
 * goes back into the anchor 40 ms later on `spatialFast`. The content takes touches only once the body is 60 % grown.
 *
 * While [open], it owns Back: the system's predictive-back gesture shrinks the pane toward the anchor's centre (scale
 * 1 → 0.9, eased on `PredictiveBack`; [KitGrowFromAnchorState.backProgress] for the scrim), letting go past the
 * system's line calls [onBack], cancelling springs back. Calm = the body jumps and only fades; Off = every change is
 * final in the next frame and the gesture does not shrink it (Back still calls [onBack]).
 *
 * States: folded (nothing drawn), growing, open, shrinking, predictive back
 * Text: none (the content brings its own)
 * Cap: none of its own; the pane's caller caps it (the panel's 600 dp)
 * Motion: bounds spatial (open) / spatialFast (close), content QUICK; Calm = fade; Off = instant
 * Replaces: none
 */
@Composable
public fun KitGrowFromAnchor(
    open: Boolean,
    anchorInWindow: () -> Rect,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    state: KitGrowFromAnchorState = rememberKitGrowFromAnchorState(open),
    role: SurfaceRole = SurfaceRole.Floating,
    level: SurfaceLevel = SurfaceLevel.Surface1,
    shape: KitShapeId = KitShapeId.Dialog,
    content: @Composable () -> Unit,
) {
    val specs = KitMotion.specs
    val level0 = KitMotion.state.level
    val reduced = KitMotion.state.reduced
    LaunchedEffect(open) {
        val body = state.body
        val shown = state.content
        coroutineScope {
            if (open) {
                if (level0 == MotionLevel.Calm) body.snapTo(1f) else launch { body.animateTo(1f, specs.spatial()) }
                launch {
                    if (!reduced && shown.value <= 0f) delay(specs.delayMs(GrowFromAnchorRules.CONTENT_IN_DELAY_MS.toFloat()))
                    shown.animateTo(1f, specs.quick())
                }
            } else if (level0 == MotionLevel.Calm) {
                // Calm: no travel; the pane fades with its content, then folds.
                launch {
                    shown.animateTo(0f, specs.quick())
                    body.snapTo(0f)
                }
            } else {
                launch { shown.animateTo(0f, specs.quick()) }
                launch {
                    if (!reduced) delay(specs.delayMs(GrowFromAnchorRules.BODY_OUT_DELAY_MS.toFloat()))
                    body.animateTo(0f, specs.spatialFast())
                }
            }
        }
    }
    val back = KitPredictiveBack(enabled = open) { onBack() }
    state.back = { if (reduced) 0f else back.value() }
    // Read through a derived state: the body's every frame must not recompose the pane, only its folding.
    val folded by remember(state) { derivedStateOf { state.folded } }
    val place = remember { PaneOrigin() }
    if (!open && folded) {
        // Folded: nothing is drawn, but the pane's place is kept, so the first frame of an open starts at the anchor.
        Layout(content = {}, modifier = modifier.onGloballyPositioned { place.at = it.positionInWindow() }) { _, c ->
            layout(if (c.hasBoundedWidth) c.maxWidth else c.minWidth, if (c.hasBoundedHeight) c.maxHeight else c.minHeight) {}
        }
        return
    }
    val anchorNow by rememberUpdatedState(anchorInWindow)
    val colors = LocalKitColors.current
    val look = kitSurfaceLook(KitSurfaceInput(role = role, shape = shape, level = level, ownFill = colors.surface1))
    val calm = level0 == MotionLevel.Calm
    val rise = MotionTokens.ENTRANCE_RISE_DP.dp
    Layout(
        content = {
            // The body: the face on the moving bounds, so its outline stays whole all the way round.
            Box(Modifier.testTag(GROW_BODY_TAG).kitSurface(look))
            // The content: laid out once at the pane's size, clipped to the body, never scaled.
            Box(
                Modifier
                    .testTag(GROW_CONTENT_TAG)
                    .graphicsLayer {
                        val r = GrowFromAnchorRules.bounds(state.anchorInPane, Rect(Offset.Zero, size), state.body.value)
                        shape = BodyClip(r, look.shape)
                        clip = true
                    },
            ) {
                Box(
                    Modifier
                        .graphicsLayer {
                            alpha = state.content.value
                            translationY = (1f - state.content.value) * rise.toPx()
                        }
                        .pointerInput(state) {
                            awaitPointerEventScope {
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    if (state.body.value < GrowFromAnchorRules.TOUCH_AT || state.body.targetValue < 1f) e.changes.forEach { it.consume() }
                                }
                            }
                        },
                ) { content() }
            }
        },
        modifier =
            modifier
                .onGloballyPositioned { place.at = it.positionInWindow() }
                .graphicsLayer {
                    alpha = if (calm) state.content.value else 1f
                    val p = state.backProgress()
                    if (p > 0f) {
                        val s = 1f - PREDICTIVE_SHRINK * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = GrowFromAnchorRules.origin(state.anchorInPane, Rect(Offset.Zero, size))
                    }
                },
    ) { measurables, constraints ->
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val pane = Rect(0f, 0f, w.toFloat(), h.toFloat())
        val a = anchorNow().translate(-place.at.x, -place.at.y)
        if (a != state.anchorInPane) state.anchorInPane = a
        val r = GrowFromAnchorRules.bounds(a, pane, state.body.value)
        val bodyP = measurables[0].measure(Constraints.fixed(r.width.toInt().coerceAtLeast(0), r.height.toInt().coerceAtLeast(0)))
        val contentP = measurables[1].measure(Constraints.fixed(w, h))
        layout(w, h) {
            bodyP.place(r.left.toInt(), r.top.toInt())
            contentP.place(0, 0)
        }
    }
}

/** The body's bounds [r] (pane px) in the face's [face] shape, as the content's clip; a new one each frame it moves. */
private data class BodyClip(private val r: Rect, private val face: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        when (val o = face.createOutline(r.size, layoutDirection, density)) {
            is Outline.Rounded -> Outline.Rounded(o.roundRect.translate(r.topLeft))
            is Outline.Rectangle -> Outline.Rectangle(o.rect.translate(r.topLeft))
            is Outline.Generic -> Outline.Rounded(RoundRect(r, CornerRadius.Zero))
        }
}

/** Where the pane sits in the window, kept outside the snapshot state (it is read in layout). */
private class PaneOrigin {
    var at: Offset = Offset.Zero
}

/** The growing body, for tests. */
public const val GROW_BODY_TAG: String = "kit-grow-body"

/** The content inside the growing body, for tests. */
public const val GROW_CONTENT_TAG: String = "kit-grow-content"

/** The default anchor corner for a strip beside the edge light. */
public val GROW_ANCHOR_CORNER: Dp = KitLampDims.CORNER.dp
