package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

/**
 * A short list (≤ 50 rows in a `Column`) whose rows come and go (Dart `KitAnimatedRows`; plan 08 §5.6 "Short list"): a
 * new row unfolds and fades in over STANDARD, a removed row folds and fades out over QUICK, a moved row changes place at
 * once. The first composition shows every row at once. A leaving row ignores touches and is hidden from TalkBack. Long
 * lists use the kit's lazy list helpers instead.
 *
 * States: at rest, rows arriving, rows leaving
 * Text: none (rows decide)
 * Cap: none
 * Motion: expand + fade STANDARD in, shrink + fade QUICK out; Calm = same; Off = instant (class Fold)
 * Replaces: KitAnimatedRows (lib/ui/kit/motion/kit_reveal.dart)
 */
@Composable
public fun <T> KitAnimatedRows(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    val reduced = KitMotion.state.reduced
    val specs = KitMotion.specs
    val holder = remember { RowsHolder<T>() }
    val shown = holder.merge(items, key, reduced)
    val enter: EnterTransition =
        if (reduced) EnterTransition.None
        else expandVertically(specs.pace<IntSize>(KitPace.Standard)) + fadeIn(specs.pace(KitPace.Standard))
    val exit: ExitTransition =
        if (reduced) ExitTransition.None
        else shrinkVertically(specs.pace<IntSize>(KitPace.Quick)) + fadeOut(specs.pace(KitPace.Quick))
    Column(modifier) {
        for (entry in shown) {
            key(entry.key) {
                AnimatedVisibility(entry.state, enter = enter, exit = exit, label = "kit.rows") {
                    Box(Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)) { row(entry.item) }
                }
            }
        }
    }
}

internal class RowEntry<T>(val key: Any, var item: T, val state: MutableTransitionState<Boolean>)

/** Keeps leaving rows in place until their fold ends. Pure enough to read: one merge per composition. */
internal class RowsHolder<T> {
    private var entries: List<RowEntry<T>>? = null

    fun merge(items: List<T>, key: (T) -> Any, reduced: Boolean): List<RowEntry<T>> {
        val old = entries
        val byKey = old?.associateBy { it.key }.orEmpty()
        val fresh =
            items.map { item ->
                val k = key(item)
                val existing = byKey[k]
                if (existing != null) {
                    existing.item = item
                    if (!existing.state.targetState) existing.state.targetState = true
                    existing
                } else {
                    // First composition, or Off: there at once. Later arrivals unfold.
                    val start = old == null || reduced
                    RowEntry(k, item, MutableTransitionState(start).apply { targetState = true })
                }
            }
        if (old == null) return fresh.also { entries = it }
        val freshKeys = fresh.mapTo(HashSet()) { it.key }
        val result = fresh.toMutableList()
        // Leaving rows keep their place after the row that preceded them; a finished fold drops out.
        var anchor: Any? = null
        for (e in old) {
            if (e.key in freshKeys) {
                anchor = e.key
                continue
            }
            if (reduced) continue
            if (e.state.targetState) e.state.targetState = false
            if (e.state.isIdle && !e.state.currentState) continue
            val at = if (anchor == null) 0 else result.indexOfFirst { it.key == anchor } + 1
            result.add(at, e)
            anchor = e.key
        }
        entries = result
        return result
    }
}
