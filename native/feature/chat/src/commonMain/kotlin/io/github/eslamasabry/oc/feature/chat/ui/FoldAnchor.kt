package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** How long after a touch the touched row is held in place: the kit's fold (250 ms) and some slack. */
internal const val FOLD_ANCHOR_HOLD_MS = 600L

/**
 * P-010: the transcript is a reverse list anchored at the bottom, so a row that grows (a tool group, a step, a
 * result opening) grows upward and slides away from the finger that opened it. The row a person touched is held:
 * for a short while after the touch, its height change is paid back with an equal scroll, so its top stays where it
 * was. Content that grows on its own (a streaming reply) is never held.
 *
 * A short conversation has nothing above to scroll to, so the pay-back ran out and the row slid up anyway (owner's
 * opened picture, 2026-11-15: a finished turn's row tapped near the bottom ended a screen higher, the reply pushed off
 * below all the same). What the scroll cannot pay is lent as [room]: blank room above the oldest row (where the short
 * conversation's empty space already was), into which the list scrolls. Folding the row again scrolls the reply back;
 * once the list rests on its newest row again with nothing held, the room is given back, unseen (it lies above
 * everything).
 */
@Stable
internal class FoldAnchor {
    /** The list item last touched, while it is held; read in layout callbacks and by the room's keeper. */
    var item: String? by mutableStateOf(null)
        private set

    /** Room (px) lent above the oldest row so a held row can stay put in a short conversation; 0 = none. */
    var room: Int by mutableIntStateOf(0)

    var serial: Int by mutableIntStateOf(0)
        private set

    /** Where the held item's top stood when it was touched: px from the list's newest (bottom) edge. */
    private var heldTop: Int? = null

    fun hold(key: String, lazy: LazyListState? = null) {
        item = key
        heldTop = lazy?.topOf(key)
        serial++
    }

    /**
     * At the touch, before anything grows: a short conversation's empty space above its oldest row becomes room, which
     * changes nothing in view (the list still just fills, so nothing scrolls) but lets the first frame of a growth
     * be paid back by scrolling in that same pass, never a frame late.
     */
    fun prepare(lazy: LazyListState) {
        // A pixel short of full: a list that overflowed by a rounding pixel would say it can scroll (the header's
        // ground would show).
        val empty = lazy.layoutInfo.emptySpace() - 1
        if (empty > 0) room += empty
    }

    fun release(atSerial: Int) {
        if (serial == atSerial) item = null
    }

    /** The scroll that keeps a held item's top in place when its height goes from [before] to [after]; 0 = none. */
    fun scrollFor(key: String, before: Int, after: Int): Float =
        FoldAnchorRules.scrollFor(held = item == key, before = before, after = after)

    /**
     * Once the person has scrolled up from the newest edge by any amount, a visible item changing height must not
     * shift what they are reading above it.
     */
    fun pinsWhileScrolled(lazy: LazyListState, key: String): Boolean =
        !lazy.isScrollInProgress &&
            (lazy.firstVisibleItemIndex > 0 || lazy.firstVisibleItemScrollOffset > 0) &&
            lazy.layoutInfo.visibleItemsInfo.any { it.key == key && it.index >= lazy.firstVisibleItemIndex }

    /**
     * Pays the held item [key]'s size change back: by how far its top moved from where it was touched, measured (or by
     * its size change, [sized], when it is not in view or pinned while scrolled up). Measured, because a list resting
     * on its oldest row keeps that row, and the held one, in place on its own as the item folds; paying the fold back
     * as well moved the row up by as much again. What the list cannot scroll (nothing above it) is lent as [room] and
     * scrolled into; the room first fills the short conversation's empty space above its oldest row (room there adds
     * nothing to scroll into).
     */
    suspend fun payBack(lazy: LazyListState, key: String, sized: Float, useHeldTop: Boolean = true) {
        val t0 = if (useHeldTop) heldTop else null
        val now = if (t0 != null) lazy.topOf(key) else null
        val scroll = if (t0 != null && now != null) (now - t0).toFloat() else sized
        if (kotlin.math.abs(scroll) < FoldAnchorRules.HALF_PX) return
        val left = scroll - lazy.scrollBy(scroll)
        val lend = FoldAnchorRules.lend(scroll, left, lazy.layoutInfo.emptySpace())
        if (lend <= 0) return
        room += lend
        // The room is an item the list measures as it scrolls, so the rest is paid at once; a frame later if not yet.
        val rest = left - lazy.scrollBy(left)
        if (rest >= FoldAnchorRules.HALF_PX) {
            withFrameNanos {}
            lazy.scrollBy(rest)
        }
    }
}

/** The top of the item [key] in px from a reverse list's newest (bottom) edge, while it is laid out. */
private fun LazyListState.topOf(key: String): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { it.offset + it.size }

/** The viewport the list's content leaves empty (a short conversation's space above its oldest row); 0 when full. */
private fun LazyListLayoutInfo.emptySpace(): Int {
    val shown = visibleItemsInfo
    if (shown.isEmpty() || shown.size < totalItemsCount) return 0
    val used = shown.maxOf { it.offset + it.size } - shown.minOf { it.offset }
    return viewportSize.height - beforeContentPadding - afterContentPadding - used
}

/** The pure part of [FoldAnchor]. */
internal object FoldAnchorRules {
    /**
     * In a reverse list, scrolling forward by d moves the content down by d: a held item that grew by d is paid back
     * with +d (and one that shrank with −d). Nothing for an item not held or not measured before.
     */
    fun scrollFor(held: Boolean, before: Int, after: Int): Float =
        if (!held || before < 0 || before == after) 0f else (after - before).toFloat()

    /**
     * The room (px) to lend when a growth's pay-back of [scroll] left [left] unscrolled, in a list with [empty] px of
     * empty space above its content: that space, then the rest of the growth. Only for growth, whole px.
     */
    fun lend(scroll: Float, left: Float, empty: Int = 0): Int =
        if (scroll <= 0f || left < HALF_PX) 0 else kotlin.math.ceil(left).toInt() + empty.coerceAtLeast(0)

    /** Less than this is no scroll at all. */
    const val HALF_PX: Float = .5f
}

@Composable
internal fun rememberFoldAnchor(lazy: LazyListState? = null): FoldAnchor {
    val anchor = remember { FoldAnchor() }
    // The lent room goes back once the list rests on its newest row again and no row is held: it lies above the oldest
    // row, so nothing in view moves.
    if (lazy != null)
        LaunchedEffect(anchor, lazy) {
            snapshotFlow {
                    anchor.room > 0 && anchor.item == null && !lazy.isScrollInProgress &&
                        lazy.firstVisibleItemIndex == 0 && lazy.firstVisibleItemScrollOffset == 0
                }
                .collect { if (it) anchor.room = 0 }
        }
    // The serial moves on every touch of any row; read here in composition it would recompose the whole list.
    LaunchedEffect(anchor) {
        snapshotFlow { anchor.serial }
            .collectLatest { serial ->
                if (serial > 0) {
                    delay(FOLD_ANCHOR_HOLD_MS)
                    anchor.release(serial)
                }
            }
    }
    return anchor
}

/** Holds this list item in place after the person touches it (see [FoldAnchor]). */
@Composable
internal fun Modifier.foldAnchor(key: String, anchor: FoldAnchor, lazy: LazyListState): Modifier {
    val last = remember(key) { IntArray(1) { -1 } }
    // A list cannot scroll from inside its own layout pass: the pay-back runs right after it.
    val scope = rememberCoroutineScope()
    return this.pointerInput(key, anchor) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                anchor.hold(key, lazy)
                anchor.prepare(lazy)
            }
        }
        .onSizeChanged { size ->
            val before = last[0]
            last[0] = size.height
            val held = anchor.item == key
            val pinned = !held && anchor.pinsWhileScrolled(lazy, key)
            val scroll = FoldAnchorRules.scrollFor(held = held || pinned, before = before, after = size.height)
            if (scroll != 0f) scope.launch { anchor.payBack(lazy, key, scroll, useHeldTop = held) }
        }
}
