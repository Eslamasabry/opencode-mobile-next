package io.github.eslamasabry.oc.ui.kitchat.stream

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** EaseOutCubic (plan 08 §5.1 step 5). */
private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * Fresh words fade in the draw phase (plan 09 §8.3, plan 08 §5.1 step 5). The text draws fully; for each fresh run,
 * the glyph path of its range is drawn with `DstOut` at alpha `1 − EaseOutCubic(age / 250 ms)`, which lowers the
 * text's own alpha on any background. Spans never change, so a fade frame never re-lays out the paragraph. The
 * offscreen layer exists only while runs are fading; the strategy is read in the layer lambda (no recomposition).
 */
public fun Modifier.kitFreshFade(layout: () -> TextLayoutResult?, state: RevealState): Modifier =
    graphicsLayer {
        compositingStrategy = if (state.runs.isEmpty()) CompositingStrategy.Auto else CompositingStrategy.Offscreen
    }.drawWithCache {
        val path = Path()
        onDrawWithContent {
            drawContent()
            val tl = layout() ?: return@onDrawWithContent
            val now = state.frameNanos // read here = draw-only invalidation
            for (r in state.runs) {
                val age = ((now - r.bornNanos).toFloat() / RevealPace.FADE_NS).coerceIn(0f, 1f)
                val hide = 1f - EaseOutCubic.transform(age)
                if (hide <= 0f) continue
                val end = r.end.coerceAtMost(tl.layoutInput.text.length)
                if (r.start >= end) continue
                path.reset()
                path.addPath(tl.getPathForRange(r.start, end))
                drawPath(path, Color.Black, alpha = hide, blendMode = BlendMode.DstOut)
            }
        }
    }

/**
 * Smooth growth of the live block (plan 08 §5.1 step 6; plan 09 §8.4 names `animateContentSize`, whose fallback this
 * is — 08 is more precise: growth only, a shrink is taken at once, velocity carries when the next line lands).
 * The height is clamped to the content height plus a 4 dp descender allowance; pair with clipToBounds.
 */
public fun Modifier.growOnly(enabled: Boolean): Modifier = this then GrowOnlyElement(enabled)

private data class GrowOnlyElement(val enabled: Boolean) : ModifierNodeElement<GrowOnlyNode>() {
    override fun create(): GrowOnlyNode = GrowOnlyNode(enabled)

    override fun update(node: GrowOnlyNode) {
        node.enabled = enabled
    }
}

private class GrowOnlyNode(var enabled: Boolean) : Modifier.Node(), LayoutModifierNode {
    private var height: Animatable<Float, AnimationVector1D>? = null

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        val target = placeable.height.toFloat()
        val anim = height ?: Animatable(target).also { height = it }
        when {
            !enabled || target < anim.targetValue -> if (anim.targetValue != target) coroutineScope.launch { anim.snapTo(target) }
            target > anim.targetValue ->
                // replyGrow: ζ = 1, stiffness 700 (≈ 210 ms a line; 06 §4.8 token, Flutter KitMotion.replyGrow).
                coroutineScope.launch { anim.animateTo(target, spring(dampingRatio = 1f, stiffness = 700f)) }
        }
        val shown =
            if (!enabled) placeable.height
            else (anim.value + DESCENDER.toPx()).roundToInt().coerceAtMost(placeable.height)
        return layout(placeable.width, shown) { placeable.place(0, 0) }
    }

    private companion object {
        val DESCENDER = 4.dp
    }
}
