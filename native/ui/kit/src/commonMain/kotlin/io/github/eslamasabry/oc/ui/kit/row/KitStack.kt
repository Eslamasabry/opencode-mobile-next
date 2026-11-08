package io.github.eslamasabry.oc.ui.kit.row

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space

/**
 * The one gap between sibling containers (row groups, panels, cards): `space4`, 16 dp. A screen never picks it.
 * A container ([kitContainer]) that finds another container flush above it, in the same stack, keeps this gap;
 * anything the screen already put between them (a heading, a spacer, a note) is left alone.
 */
public const val KIT_CONTAINER_GAP_DP: Int = Space.S4

/** What the containers of one stack report: the top and bottom (window pixels) of each, by id. */
public class KitStackTracker {
    internal val spans = mutableStateMapOf<Int, Pair<Float, Float>>()
    internal var next = 0
}

/** The stack the containers below belong to; null outside one (a container then keeps its own place). */
public val LocalKitStack: androidx.compose.runtime.ProvidableCompositionLocal<KitStackTracker?> =
    staticCompositionLocalOf { null }

/** A fresh stack scope for [content]: used by [KitStack], the sheet body and the screen body. */
@Composable
public fun KitStackScope(content: @Composable () -> Unit) {
    val tracker = remember { KitStackTracker() }
    CompositionLocalProvider(LocalKitStack provides tracker, content = content)
}

/**
 * A column of sections: containers set in it ([KitRowGroup], [kitContainer]) are always [KIT_CONTAINER_GAP_DP] apart,
 * with no number from the caller. Other children keep their own spacing.
 */
@Composable
public fun KitStack(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    KitStackScope { Column(modifier.fillMaxWidth(), content = content) }
}

/**
 * Marks this node a container of the enclosing [KitStack]: when another container ends right where this one starts,
 * it takes [KIT_CONTAINER_GAP_DP] above. Settles one frame after the first layout.
 */
public fun Modifier.kitContainer(): Modifier = composed {
    val tracker = LocalKitStack.current ?: return@composed this
    val id = remember(tracker) { tracker.next++ }
    var pad by remember { mutableStateOf(0f) }
    androidx.compose.runtime.DisposableEffect(tracker, id) { onDispose { tracker.spans.remove(id) } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Modifier.padding(top = with(density) { pad.toDp() })
        .onGloballyPositioned { c ->
            val top = c.positionInRoot().y
            val bottom = top + c.size.height
            tracker.spans[id] = top to bottom
            val flush = tracker.spans.any { (other, s) -> other != id && s.second <= top + 1f && top - s.second < 1f && s.second >= top - 1f }
            val wanted = if (flush) with(density) { KIT_CONTAINER_GAP_DP.dp.toPx() } else 0f
            // Sticky: once padded the node sits a gap below its neighbour, which is not flush any more.
            val settled = tracker.spans.any { (other, s) -> other != id && top - s.second in 0f..(pad + 1f) && pad > 0f && top - s.second >= pad - 1f }
            if (flush) pad = wanted else if (!settled) pad = 0f
        }
}
