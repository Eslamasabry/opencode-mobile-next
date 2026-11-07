package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long after a touch the touched row is held in place: the kit's fold (250 ms) and some slack. */
internal const val FOLD_ANCHOR_HOLD_MS = 600L

/**
 * P-010: the transcript is a reverse list anchored at the bottom, so a row that grows (a tool group, a step, a
 * result opening) grows upward and slides away from the finger that opened it. The row a person touched is held:
 * for a short while after the touch, its height change is paid back with an equal scroll, so its top stays where it
 * was. Content that grows on its own (a streaming reply) is never held.
 */
@Stable
internal class FoldAnchor {
    /** The list item last touched, while it is held; plain bookkeeping, read in layout callbacks. */
    var item: String? = null
        private set

    var serial: Int by mutableIntStateOf(0)
        private set

    fun hold(key: String) {
        item = key
        serial++
    }

    fun release(atSerial: Int) {
        if (serial == atSerial) item = null
    }

    /** The scroll that keeps a held item's top in place when its height goes from [before] to [after]; 0 = none. */
    fun scrollFor(key: String, before: Int, after: Int): Float =
        FoldAnchorRules.scrollFor(held = item == key, before = before, after = after)
}

/** The pure part of [FoldAnchor]. */
internal object FoldAnchorRules {
    /**
     * In a reverse list, scrolling forward by d moves the content down by d: a held item that grew by d is paid back
     * with +d (and one that shrank with −d). Nothing for an item not held or not measured before.
     */
    fun scrollFor(held: Boolean, before: Int, after: Int): Float =
        if (!held || before < 0 || before == after) 0f else (after - before).toFloat()
}

@Composable
internal fun rememberFoldAnchor(): FoldAnchor {
    val anchor = remember { FoldAnchor() }
    val serial = anchor.serial
    LaunchedEffect(serial) {
        if (serial > 0) {
            delay(FOLD_ANCHOR_HOLD_MS)
            anchor.release(serial)
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
                anchor.hold(key)
            }
        }
        .onSizeChanged { size ->
            val before = last[0]
            last[0] = size.height
            val scroll = anchor.scrollFor(key, before, size.height)
            if (scroll != 0f) scope.launch { lazy.scrollBy(scroll) }
        }
}
