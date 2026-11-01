package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The row → page container transform seam (plan 08 §5.4, option A). `:ui:shell` provides one host per navigation
 * entry; kit parts read it. With no host (previews, desktop gallery, tests) parts draw without a transform.
 *
 * P2 proof: this file is the minimum the shell needs from the kit; the kit owner folds it into `KitRouteTransition` /
 * `Modifier.growFromRow` and `RowTransformGate` with unit M1.
 */
@Stable
public interface KitGrowHost {
    public val sharedTransitionScope: SharedTransitionScope

    /** The entry's own AnimatedVisibilityScope (Nav3 `LocalNavAnimatedContentScope`). */
    public val animatedVisibilityScope: AnimatedVisibilityScope

    /** False while a back gesture runs or settles, and under reduced motion. Read during the transition (snapshot). */
    public val transformEnabled: Boolean

    /** True when the last navigation was forward (open, 360 ms), false for a return (300 ms). */
    public val opening: Boolean
}

public val LocalKitGrowHost: ProvidableCompositionLocal<KitGrowHost?> = staticCompositionLocalOf { null }

/** Kit motion values used by the transform (plan 08 §2.6 X1/X2, §5.4 tracks). */
public object KitGrowMotion {
    public const val OPEN_BOUNDS_MS: Int = 360
    public const val RETURN_MS: Int = 300
    public const val ROW_CONTENT_OUT_MS: Int = 90
    public const val PAGE_CONTENT_IN_DELAY_MS: Int = 90
    public const val PAGE_CONTENT_IN_MS: Int = 180
    public const val PAGE_CONTENT_OUT_MS: Int = 120
    public const val ROW_HANDBACK_DELAY_MS: Int = 240
    public const val ROW_HANDBACK_MS: Int = 60
    public val ROW_CORNER: Dp = 20.dp

    /** M3 emphasized, as the cubic approximation of the path (plan 08 §2.7 `KitEasing.Emphasized`). */
    public val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    public val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
}

/** Keys are data classes, never strings (motion-native §4.3); wrapping keeps kit keys apart from any other key. */
private data class GrowKey(val key: Any)

private class GateConfig(private val host: KitGrowHost) : SharedTransitionScope.SharedContentConfig {
    override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
        get() = host.transformEnabled

    /** A gesture that starts mid-transform turns the shared bounds off at once (plan 08 §5.4). */
    override val shouldKeepEnabledForOngoingAnimation: Boolean
        get() = false

    /** Row gone: never animate toward stale bounds; the page falls back to its own exit (P2-5). */
    override fun SharedTransitionScope.SharedContentState.alternativeTargetBoundsInTransitionScopeAfterRemoval(
        targetBoundsBeforeRemoval: Rect,
        sharedTransitionLayoutSize: Size,
    ): Rect? = null
}

private fun boundsTransform(host: KitGrowHost): BoundsTransform = BoundsTransform { _, _ ->
    if (host.opening) tween(KitGrowMotion.OPEN_BOUNDS_MS, easing = KitGrowMotion.Emphasized)
    else tween(KitGrowMotion.RETURN_MS, easing = KitGrowMotion.Emphasized)
}

/** Corner of the growing box: 20 dp while it is a row, 0 dp once it is the page (shapes do not morph by themselves). */
@Composable
private fun AnimatedVisibilityScope.growCorner(pageSide: Boolean): Dp {
    val corner by transition.animateDp(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) tween(KitGrowMotion.OPEN_BOUNDS_MS, easing = KitGrowMotion.Emphasized)
            else tween(KitGrowMotion.RETURN_MS, easing = KitGrowMotion.Emphasized)
        },
        label = "kit.grow.corner",
    ) { state ->
        val visible = state == EnterExitState.Visible
        if (pageSide == visible) 0.dp else KitGrowMotion.ROW_CORNER
    }
    return corner
}

/** The row side: its surface becomes the page. Used by kit rows only. */
@Composable
internal fun Modifier.kitGrowSource(key: Any?): Modifier {
    val host = LocalKitGrowHost.current ?: return this
    if (key == null) return this
    val config = remember(host) { GateConfig(host) }
    with(host.sharedTransitionScope) {
        val state = rememberSharedContentState(GrowKey(key), config)
        val corner = host.animatedVisibilityScope.growCorner(pageSide = false)
        return this@kitGrowSource.sharedBounds(
            sharedContentState = state,
            animatedVisibilityScope = host.animatedVisibilityScope,
            enter = fadeIn(
                tween(
                    KitGrowMotion.ROW_HANDBACK_MS,
                    delayMillis = KitGrowMotion.ROW_HANDBACK_DELAY_MS,
                    easing = KitGrowMotion.EaseOutCubic,
                ),
            ),
            exit = fadeOut(tween(KitGrowMotion.ROW_CONTENT_OUT_MS, easing = KitGrowMotion.EaseOutCubic)),
            boundsTransform = boundsTransform(host),
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
        )
    }
}

/**
 * A page that grows out of the row registered with the same [growKey] (plan 08 §5.4). The box's ground is the page's
 * ground from frame 1 (the row's surface becoming the page); only the words fade (in 90–270 ms, out 0–120 ms). On the
 * return the ground hands back to the row in 240–300 ms. The page lays out once at full size and the growing box
 * reveals it (`skipToLookaheadSize`): no per-frame re-measure, no scaling (MOT-2). With no matching row (opened from
 * elsewhere, or the row is gone or off screen: P2-5) ground and words fade together instead.
 */
@Composable
public fun KitGrowPage(growKey: Any?, content: @Composable () -> Unit) {
    val host = LocalKitGrowHost.current
    if (host == null || growKey == null) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val config = remember(host) { GateConfig(host) }
    val ground = MaterialTheme.colorScheme.background
    val transition = host.animatedVisibilityScope.transition
    val enabled = host.transformEnabled
    val wordsAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(
                    KitGrowMotion.PAGE_CONTENT_IN_MS,
                    delayMillis = KitGrowMotion.PAGE_CONTENT_IN_DELAY_MS,
                    easing = KitGrowMotion.EaseOutCubic,
                )
            } else {
                tween(KitGrowMotion.PAGE_CONTENT_OUT_MS, easing = KitGrowMotion.EaseOutCubic)
            }
        },
        label = "kit.grow.words",
    ) { if (it == EnterExitState.Visible || !enabled) 1f else 0f }
    val groundAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                snap()
            } else {
                keyframes {
                    durationMillis = KitGrowMotion.RETURN_MS
                    1f at KitGrowMotion.ROW_HANDBACK_DELAY_MS
                    0f at KitGrowMotion.RETURN_MS
                }
            }
        },
        label = "kit.grow.ground",
    ) { if (it == EnterExitState.Visible || !enabled) 1f else 0f }
    with(host.sharedTransitionScope) {
        val state = rememberSharedContentState(GrowKey(growKey), config)
        val corner = host.animatedVisibilityScope.growCorner(pageSide = true)
        Box(
            Modifier.fillMaxSize()
                .sharedBounds(
                    sharedContentState = state,
                    animatedVisibilityScope = host.animatedVisibilityScope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = boundsTransform(host),
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
                    zIndexInOverlay = 1f,
                )
                .drawBehind { drawRect(ground, alpha = if (state.isMatchFound) groundAlpha else wordsAlpha) },
        ) {
            Box(Modifier.fillMaxSize().skipToLookaheadSize().graphicsLayer { alpha = wordsAlpha }) { content() }
        }
    }
}
