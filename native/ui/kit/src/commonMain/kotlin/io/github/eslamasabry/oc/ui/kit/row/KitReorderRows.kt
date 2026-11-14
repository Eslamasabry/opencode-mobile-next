package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.choice.rememberKitHaptics
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlinx.coroutines.launch

/** The reorder words: the handle's name ("Reorder ‹title›") and its two TalkBack actions. */
public class KitReorderWords(
    public val handle: (String) -> String,
    public val moveUp: String,
    public val moveDown: String,
)

/** The reorder's measures (LAMP-CRAFT §5). */
public object ReorderDims {
    /** The drag handle's touch box (`Space.S6` + `Space.S3`). */
    public const val HANDLE: Int = Space.S6 + Space.S3

    /** The handle's six dots' box (`Space.S4`). */
    public const val DOTS: Int = Space.S4

    /** Within this distance of the scroll's visible end, the list scrolls by itself (`Space.S6` × 2). */
    public const val EDGE: Int = Space.S6 * 2

    /** How far it scrolls per frame there (`Space.S1`). */
    public const val STEP: Int = Space.S1
}

/** The reorder's pure rules. */
public object ReorderRules {
    /**
     * The place a row lifted from [from] lands on when its centre is at [centre]: as many of the other rows as have their
     * middle above it. [spans] are the rows' (top, bottom) as laid out before the lift.
     */
    public fun target(from: Int, centre: Float, spans: List<Pair<Float, Float>>): Int =
        spans.withIndex().count { (i, s) -> i != from && (s.first + s.second) / 2f < centre }

    /** How far row [index] slides out of the way while the row from [from] (of [liftedHeight]) is over place [to]. */
    public fun offsetFor(index: Int, from: Int, to: Int, liftedHeight: Float): Float =
        when {
            from < to && index in (from + 1)..to -> -liftedHeight
            from > to && index in to until from -> liftedHeight
            else -> 0f
        }

    /** Where the lifted row's top ends up once dropped on [to] (the spans of the layout before the move). */
    public fun settledTop(from: Int, to: Int, spans: List<Pair<Float, Float>>): Float {
        val h = spans[from].second - spans[from].first
        return when {
            to > from -> spans[to].second - h
            to < from -> spans[to].first
            else -> spans[from].first
        }
    }
}

/** One drag in progress; plain state read in layout and draw. */
@Stable
private class ReorderDrag {
    var from by mutableIntStateOf(-1)
    var target by mutableIntStateOf(-1)
    var dy by mutableFloatStateOf(0f)
    val spans = HashMap<Int, Pair<Float, Float>>()
    var list: LayoutCoordinates? = null
    var count = 0
}

/**
 * Rows the person puts in order with a handle (LAMP-CRAFT §5 "Reorder", lifted out of the rings' list): pressing the
 * handle lifts the row at once ([KitHaptic.LongPress]; its floating face fades in over QUICK; no scale); it follows
 * the finger 1:1; the neighbours slide out of its way on `placement`, with one [KitHaptic.Select] tick each time it
 * takes a new place; let go, it settles into its place on `release()` with no haptic and [onMove] runs. Near the visible
 * ends of [scroll] the list scrolls by itself ([scrollReversed] for a list that scrolls from the bottom). TalkBack: the
 * handle is "Reorder ‹title›" with Move up / Move down. [row] draws one row and places the [handle] it is given; rows
 * [enabled] false have no handle and do not move.
 *
 * States: rest, lifted, settling
 * Text: none (the rows bring theirs)
 * Cap: none (it fills its column; the rows cap themselves)
 * Motion: lift QUICK, neighbours `placement`, drop `release()`; Off = no slide, final at once
 * Replaces: the rings' long-press drag in DockPanel (the rings keep theirs until the owner retires them)
 */
@Composable
public fun <T> KitReorderRows(
    items: List<T>,
    key: (T) -> Any,
    title: (T) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    words: KitReorderWords,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    scroll: ScrollState? = null,
    scrollReversed: Boolean = false,
    row: @Composable (item: T, handle: @Composable () -> Unit) -> Unit,
) {
    val specs = KitMotion.specs
    val haptics = rememberKitHaptics()
    val drag = remember { ReorderDrag() }
    val offsets = remember { HashMap<Any, Animatable<Float, AnimationVector1D>>() }
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    val keys = items.map(key)
    drag.count = items.size
    val density = LocalDensity.current
    val edge = with(density) { ReorderDims.EDGE.dp.toPx() }
    val step = with(density) { ReorderDims.STEP.dp.toPx() }
    // Neighbours make room for the lifted row; one tick each time it takes a new place.
    LaunchedEffect(drag.from, drag.target) {
        val from = drag.from
        if (from < 0) return@LaunchedEffect
        val span = drag.spans[from] ?: return@LaunchedEffect
        val h = span.second - span.first
        keys.forEachIndexed { i, k ->
            if (i != from) {
                val a = offsets.getOrPut(k) { Animatable(0f) }
                val to = ReorderRules.offsetFor(i, from, drag.target, h)
                if (a.targetValue != to) launch { a.animateTo(to, specs.placement()) }
            }
        }
    }
    // Near the scroll's visible ends, the list scrolls itself and the lifted row stays under the finger.
    LaunchedEffect(drag.from >= 0, scroll) {
        val s = scroll ?: return@LaunchedEffect
        if (drag.from < 0) return@LaunchedEffect
        while (drag.from >= 0) {
            withFrameNanos { }
            val list = drag.list ?: continue
            if (!list.isAttached) continue
            val span = drag.spans[drag.from] ?: continue
            val visible = list.boundsInWindow()
            val top = list.positionInWindow().y + span.first + drag.dy
            val bottom = top + (span.second - span.first)
            val wantDown = bottom > visible.bottom - edge
            val wantUp = top < visible.top + edge
            if (!wantDown && !wantUp) continue
            val toward = if (wantDown) step else -step
            val consumed = s.dispatchRawDelta(if (scrollReversed) -toward else toward)
            drag.dy += if (scrollReversed) -consumed else consumed
            updateTarget(drag, haptics)
        }
    }
    val colors = LocalKitColors.current
    val look = kitSurfaceLook(KitSurfaceInput(role = SurfaceRole.Floating, shape = KitShapeId.Card, level = SurfaceLevel.Surface2, ownFill = colors.surface2))
    Column(modifier.testTag(REORDER_TAG).onGloballyPositioned { drag.list = it }) {
        items.forEachIndexed { i, item ->
            val k = keys[i]
            key(k) {
                val offset = offsets.getOrPut(k) { Animatable(0f) }
                val lifted = drag.from == i
                val lift = remember { Animatable(0f) }
                LaunchedEffect(lifted) {
                    val to = if (lifted) 1f else 0f
                    if (lift.targetValue != to) lift.animateTo(to, specs.quick())
                }
                Box(
                    Modifier
                        .onPlaced { c ->
                            val y = c.positionInParent().y
                            drag.spans[i] = y to (y + c.size.height)
                        }
                        .zIndex(if (lifted || lift.value > 0f) 1f else 0f)
                        .graphicsLayer { translationY = if (drag.from == i) drag.dy else offset.value },
                ) {
                    if (lifted || lift.value > 0f) Box(Modifier.matchParentSize().graphicsLayer { alpha = lift.value }.kitSurface(look))
                    val handle: @Composable () -> Unit = {
                        if (enabled(item)) {
                            ReorderHandle(
                                label = words.handle(title(item)),
                                moveUp = if (i > 0) words.moveUp else null,
                                moveDown = if (i < items.size - 1) words.moveDown else null,
                                onMoveUp = { move(i, i - 1) },
                                onMoveDown = { move(i, i + 1) },
                                onStart = {
                                    drag.from = i
                                    drag.target = i
                                    drag.dy = 0f
                                    haptics(KitHaptic.LongPress)
                                },
                                onDrag = { dy ->
                                    drag.dy += dy
                                    updateTarget(drag, haptics)
                                },
                                onEnd = {
                                    val from = drag.from
                                    val to = drag.target
                                    val spans = (0 until items.size).mapNotNull { drag.spans[it] }
                                    if (from >= 0 && spans.size == items.size) {
                                        val residual = spans[from].first + drag.dy - ReorderRules.settledTop(from, to, spans)
                                        scope.launch {
                                            offset.snapTo(residual)
                                            offset.animateTo(0f, specs.release())
                                        }
                                        keys.forEachIndexed { j, other -> if (j != from) scope.launch { offsets[other]?.snapTo(0f) } }
                                        drag.from = -1
                                        drag.target = -1
                                        drag.dy = 0f
                                        if (to != from) move(from, to)
                                    } else {
                                        drag.from = -1
                                        drag.dy = 0f
                                    }
                                },
                            )
                        }
                    }
                    row(item, handle)
                }
            }
        }
    }
}

private fun updateTarget(drag: ReorderDrag, haptics: (KitHaptic) -> Unit) {
    val from = drag.from
    if (from < 0) return
    val spans = (0 until drag.count).mapNotNull { drag.spans[it] }
    if (spans.size != drag.count) return
    val span = drag.spans[from] ?: return
    val centre = (span.first + span.second) / 2f + drag.dy
    val to = ReorderRules.target(from, centre, spans)
    if (to != drag.target) {
        drag.target = to
        haptics(KitHaptic.Select)
    }
}

/** The six-dot handle: a press lifts the row at once, a drag moves it; TalkBack moves it one place at a time. */
@Composable
private fun ReorderHandle(
    label: String,
    moveUp: String?,
    moveDown: String?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    val ink = LocalKitColors.current.text2
    val start by rememberUpdatedState(onStart)
    val moved by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    Box(
        Modifier
            .size(ReorderDims.HANDLE.dp)
            .testTag(REORDER_HANDLE_TAG)
            .semantics {
                contentDescription = label
                customActions = listOfNotNull(
                    moveUp?.let { CustomAccessibilityAction(it) { onMoveUp(); true } },
                    moveDown?.let { CustomAccessibilityAction(it) { onMoveDown(); true } },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    start()
                    drag(down.id) { change ->
                        moved(change.positionChange().y)
                        change.consume()
                    }
                    end()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(ReorderDims.DOTS.dp)) {
            val r = size.minDimension * DOT_SHARE
            for (cx in listOf(DOT_COL_A, DOT_COL_B)) for (cy in listOf(DOT_ROW_A, DOT_ROW_B, DOT_ROW_C)) {
                drawCircle(ink, r, Offset(size.width * cx, size.height * cy))
            }
        }
    }
}

private const val DOT_SHARE: Float = 0.09f // NV: each dot's radius as a share of the 16 dp box (the prototype's grip)
private const val DOT_COL_A: Float = 0.35f // NV: the grip's first column
private const val DOT_COL_B: Float = 0.65f // NV: the grip's second column
private const val DOT_ROW_A: Float = 0.2f // NV: the grip's first row
private const val DOT_ROW_B: Float = 0.5f // NV: the grip's middle row
private const val DOT_ROW_C: Float = 0.8f // NV: the grip's last row

/** The reorder list, for tests. */
public const val REORDER_TAG: String = "kit-reorder"

/** A row's drag handle. */
public const val REORDER_HANDLE_TAG: String = "kit-reorder-handle"
