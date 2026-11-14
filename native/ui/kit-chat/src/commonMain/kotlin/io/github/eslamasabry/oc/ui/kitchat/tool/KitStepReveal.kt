package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.pace
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * How much of a [KitStepReveal]'s content is in view while it opens, folds or arrives: [progress] (0..1) of its [full]
 * height, measured from the content's top ([coords]). Read in the draw phase only (a row's ink, the rail's ink), so a
 * moving fold redraws its rows and recomposes nothing. Folds nest: a row inside a run inside the turn's earlier steps
 * is in view as far as every fold around it lets it be.
 */
@Stable
internal class RailReveal(private val parent: RailReveal?, private val progress: () -> Float) {
    var full: Float = 0f
    var coords: LayoutCoordinates? = null

    /** The share (0..1) of [node]'s box in view through this fold and every fold around it. */
    fun shown(node: LayoutCoordinates?): Float {
        var share = 1f
        var r: RailReveal? = this
        while (r != null) {
            share *= r.own(node)
            if (share <= 0f) return 0f
            r = r.parent
        }
        return share
    }

    /**
     * How strongly [node] is drawn (0..1) while a fold moves: by how much of it is still hidden, never by the share of it
     * in view (a two-line sentence cut through its last line was 85 % in view and drew at 72 % ink, a cut glyph read
     * whole): hidden by [fadePx] or more it is not drawn; less, it fades in as the edge uncovers it.
     */
    fun ink(node: LayoutCoordinates?, fadePx: Float): Float {
        val share = shown(node)
        if (share >= 1f) return 1f
        val h = node?.takeIf { it.isAttached }?.size?.height?.toFloat() ?: return share * share
        if (h <= 0f || fadePx <= 0f) return share * share
        val left = (1f - (1f - share) * h / fadePx).coerceIn(0f, 1f)
        return left * left
    }

    private fun own(node: LayoutCoordinates?): Float {
        val p = progress().coerceIn(0f, 1f)
        if (p >= 1f) return 1f
        val c = coords
        if (node == null || c == null || !c.isAttached || !node.isAttached) return p
        val top = c.localPositionOf(node, Offset.Zero).y
        val h = node.size.height.toFloat()
        val edge = full * p
        if (h <= 0f) return if (top < edge) 1f else 0f
        return ((edge - top) / h).coerceIn(0f, 1f)
    }
}

/** The fold a step-list row sits in, if any (its ink follows how much of it is in view). */
internal val LocalRailReveal = compositionLocalOf<RailReveal?> { null }

/**
 * A part of a step list that opens and folds like an accordion (owner's reference #052; owner 2026-11-14: "clipping
 * flashing"): its height grows from its top and the reply below moves with it, never in one jump; every row inside is
 * drawn by how much of it is in view (a row the edge crosses is a faint row, never a row cut at full ink), and the rail's
 * ink to it fades with it. In over STANDARD on `Enter`, out over QUICK; clipped only while it moves, so a settled row's
 * light and glow are never cut. A folded part leaves composition, and its rows' places on the rail with it.
 *
 * [arrives]: composed visible, it still grows in from nothing (#001 Pop-in: a step that arrives while the list shows).
 * [order]: rows that open together start [STEP_STAGGER_MS] apart (#002 Stagger), at most [STEP_STAGGER_MAX] steps. A
 * part composed visible shows at once (a list scrolled back into view does not replay); reduced motion is instant. A
 * leaving part is picture only (no semantics).
 */
@Composable
public fun KitStepReveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    arrives: Boolean = false,
    order: Int = 0,
    content: @Composable () -> Unit,
) {
    val motion = KitMotion.state
    val specs = KitMotion.specs
    val reduced = motion.reduced
    val shown = remember { Animatable(if (visible && (!arrives || reduced)) 1f else 0f) }
    var present by remember { mutableStateOf(visible) }
    val enter = specs.pace<Float>(KitPace.Standard)
    val exit = specs.pace<Float>(KitPace.Quick)
    val wait = specs.delayMs(min(order, STEP_STAGGER_MAX) * STEP_STAGGER_MS.toFloat())
    LaunchedEffect(visible, reduced) {
        if (visible) {
            present = true
            if (reduced) shown.snapTo(1f)
            else if (shown.value < 1f) {
                if (shown.value == 0f && wait > 0) delay(wait)
                shown.animateTo(1f, enter)
            }
        } else {
            if (reduced) shown.snapTo(0f) else shown.animateTo(0f, exit)
            present = false
        }
    }
    if (!visible && !present) return
    val parent = LocalRailReveal.current
    val reveal = remember(parent) { RailReveal(parent) { shown.value } }
    CompositionLocalProvider(LocalRailReveal provides reveal) {
        Box(
            modifier
                .drawWithContent {
                    // Cut at the top and the moving bottom edge only while it moves (a settled row's light may reach
                    // past its box); never at the sides, where the rows' glyphs sit on the rail outside this box.
                    if (shown.value >= 1f) drawContent()
                    else clipRect(-SIDE_REACH, 0f, size.width + SIDE_REACH, size.height) { this@drawWithContent.drawContent() }
                }
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    reveal.full = p.height.toFloat()
                    val h = (p.height * shown.value.coerceIn(0f, 1f)).roundToInt()
                    layout(p.width, h) { p.place(0, 0) }
                }
                .onPlaced { reveal.coords = it }
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
        ) {
            content()
        }
    }
}

/** How far past its sides a moving fold still draws (the rail column and the glyphs sit outside its box). */
private const val SIDE_REACH = 4096f

/** How much of a row may still be hidden while it is drawn at all: a row fades in over its last 24 dp of uncovering. */
internal const val REVEAL_FADE_DP: Int = 24

/** The gap between two rows that open together (#002 Stagger). */
public const val STEP_STAGGER_MS: Int = 24

/** Rows past this many open with the last one (the whole fold stays inside about 350 ms). */
public const val STEP_STAGGER_MAX: Int = 4
