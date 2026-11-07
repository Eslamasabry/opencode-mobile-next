package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Faces that arrive one after another (owner reference 2026-11-07): stacked avatars or agent marks overlapping by
 * [overlap], each later one drawn under the one before. Marks that arrive after the row first shows slide in from the
 * start side and fade, [STAGGER_MS] apart, on the Spatial spring; marks there from the start show at once (first frame
 * final). Paint only: the row's size is final from the first frame.
 *
 * States: at rest, arriving
 * Text: none (content decides)
 * Cap: none
 * Motion: slide 12 dp + fade, staggered 35 ms, Spatial at Full; Calm = staggered fade; Off = instant (class Travel)
 */
@Composable
public fun <T> KitStaggerRow(
    items: List<T>,
    keyOf: (T) -> Any,
    modifier: Modifier = Modifier,
    overlap: Dp = 8.dp,
    content: @Composable (T) -> Unit,
) {
    val state = KitMotion.state
    val specs = KitMotion.specs
    val first = remember { items.map(keyOf).toHashSet() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-overlap), verticalAlignment = Alignment.CenterVertically) {
        var arriving = 0
        items.forEachIndexed { i, item ->
            val k = keyOf(item)
            val plays = !state.reduced && k !in first
            val order = if (plays) arriving++ else 0
            key(k) {
                val p = remember { Animatable(if (plays) 0f else 1f) }
                LaunchedEffect(Unit) {
                    if (p.value < 1f) {
                        delay((order * STAGGER_MS * state.debugTimeScale).toLong())
                        p.animateTo(1f, specs.spatial())
                    }
                }
                val travel = if (state.travels) STAGGER_TRAVEL_DP.dp else 0.dp
                Box(
                    // Earlier faces sit over later ones, as a stack read from the start.
                    Modifier.zIndex((items.size - i).toFloat()).graphicsLayer {
                        val v = p.value
                        alpha = v.coerceIn(0f, 1f)
                        translationX = (if (rtl) 1f else -1f) * travel.toPx() * (1f - v)
                    },
                ) { content(item) }
            }
        }
    }
}

/** The gap between two arriving faces. */
public const val STAGGER_MS: Int = 35

/** How far an arriving face slides in from the start side. */
public const val STAGGER_TRAVEL_DP: Int = 12
