package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import kotlin.math.ceil
import kotlin.math.min

/*
 * The bottom edge of a page whose body runs under its bottom block (a chat's chips and message box; owner 2026-11-10,
 * "let the chat scroll under the message box"). The body clears its sharp content from just above the block
 * (kitScrollEdges); here the same content shows on behind the block as a soft blur of itself, down to the window's
 * edge, so the glass chips and the message box float over text that visibly runs under them instead of over an empty
 * page. It is the top edge's mirror (KitTopEdge.kt) without a dim: it draws only what was recorded, so at the end of
 * a conversation, with nothing under the block, it draws nothing and no band shows over a live background.
 *
 * The glass over it (the chips, the message box) still bends and blurs the body's own recording, never this copy.
 */

/**
 * What a screen's bottom block is. A block that is one pane of glass over the body (a chat's message box, owner
 * 2026-11-10: "a slab of glass without any fade below") reports [slab]: the frame then neither fades the body above
 * it nor blurs it behind it, so the body scrolls sharp up to the glass and bends under its edge, as under the bar.
 */
@Stable
public class KitScreenBottom internal constructor() {
    internal var slab: Boolean by mutableStateOf(false)
}

internal val LocalKitScreenBottom = staticCompositionLocalOf<KitScreenBottom?> { null }

/** Tells the screen frame that its bottom block is one pane of glass over the body. Outside such a frame: nothing. */
@Composable
public fun KitReportBottomSlab(slab: Boolean) {
    val bottom = LocalKitScreenBottom.current ?: return
    DisposableEffect(bottom, slab) {
        bottom.slab = slab
        onDispose { bottom.slab = false }
    }
}

/** Whether this screen's bottom block is one pane of glass over the body ([KitReportBottomSlab]); false outside one. */
@Composable
public fun kitScreenBottomIsSlab(): Boolean = LocalKitScreenBottom.current?.slab == true

/** The bottom edge's blur, sigma in dp: softer than any pane over it, so the glass reads as the clearer layer. */
internal const val BOTTOM_EDGE_BLUR_DP: Float = 6f

/** How much of the content shows on under the bottom block (0–1): present, never competing with the block's words. */
internal const val BOTTOM_EDGE_STRENGTH: Float = .55f

/**
 * The body's content under the bottom block (Glass): [source]'s recording, blurred by [blur], coming in from nothing
 * at [fromBottom] px above this box's bottom edge over [reachPx] (the curve the body clears its sharp copy along) and
 * holding [BOTTOM_EDGE_STRENGTH] from there down.
 */
@Composable
internal fun BoxScope.KitBottomEdge(source: KitFrostState, fromBottom: () -> Float, reachPx: Float, blur: RenderEffect) {
    val edge = rememberStatusEdgeLayers()
    val origin = remember { arrayOf(Offset.Zero) }
    Box(
        Modifier.matchParentSize()
            .onGloballyPositioned { origin[0] = it.positionInRoot() }
            .drawBehind {
                drawBottomEdge(edge, size.height - fromBottom(), reachPx, blur) {
                    val shift = source.sourceOrigin - origin[0]
                    translate(shift.x, shift.y) { drawLayer(source.layer) }
                }
            }
            .testTag("kit-bottom-edge")
    )
}

/**
 * Draws the bottom edge from [top] px down to this scope's bottom: [content] (a recording that already exists, drawn
 * where it lies) blurred by [effect], dissolving in over [reach] px and holding [strength] below.
 */
internal fun DrawScope.drawBottomEdge(
    layers: StatusEdgeLayers,
    top: Float,
    reach: Float,
    effect: RenderEffect,
    strength: Float = BOTTOM_EDGE_STRENGTH,
    content: DrawScope.() -> Unit,
) {
    val w = size.width
    val y0 = top.coerceAtLeast(0f)
    val h = size.height - y0
    if (w <= 0f || h <= 0f) return
    val box = IntSize(w.toInt().coerceAtLeast(1), ceil(h).toInt().coerceAtLeast(1))
    layers.blur.renderEffect = effect
    layers.blur.record(box) { translate(0f, -y0) { content() } }
    layers.mask.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
    layers.mask.record(box) {
        drawLayer(layers.blur)
        val r = min(reach, h)
        if (r > 0f)
            drawRect(
                Brush.verticalGradient(easedStops(Color.Black, 0f, strength), startY = 0f, endY = r),
                Offset.Zero,
                Size(w, r),
                blendMode = BlendMode.DstIn,
            )
        if (h > r) drawRect(Color.Black.copy(alpha = strength), Offset(0f, r), Size(w, h - r), blendMode = BlendMode.DstIn)
    }
    translate(0f, y0) { drawLayer(layers.mask) }
}
