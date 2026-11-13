package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion

/**
 * A scrolling page body with a block pinned under it (a guide's "4 of 4 / Finish" bar, a form's Send row). While more of
 * the body lies hidden below, its last [COMPOSER_FADE_DP] dp fade out above the foot instead of ending in a hard cut
 * across a line of text; scrolled to the end, or with nothing to scroll, the body is left whole. Without a [foot]
 * nothing fades (the body ends at the page's own edge).
 *
 * With a [head] (a page's tab row) the head is pinned above the body, whole at every scroll position, and the body
 * fades out over its first [COMPOSER_FADE_DP] dp while it is scrolled, as it passes beneath the head. Hosts that run
 * the body under a floating bar must not use a head: the bar would cover it.
 *
 * The fade eases in and out with the kit's enter motion as the list leaves and reaches its end (at once under
 * reduced motion), is read while drawing, and sits on whole pixels (see [wholePx]). [contentPadding] and
 * [verticalArrangement] are the scrolling column's own, inside the fade.
 */
@Composable
public fun KitPinnedFootBody(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    foot: (@Composable () -> Unit)? = null,
    head: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val more = remember { Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    val reduced = KitMotion.state.reduced
    val hasFoot = foot != null
    val hasHead = head != null
    val before = remember { Animatable(0f) }
    var settledBefore by remember { mutableStateOf(false) }
    val spec = KitMotion.specs.enter<Float>()
    LaunchedEffect(scroll, reduced, hasFoot) {
        if (!hasFoot) return@LaunchedEffect
        snapshotFlow { scroll.canScrollForward }.collect { hidden ->
            val target = if (hidden) 1f else 0f
            if (!settled || reduced) more.snapTo(target) else more.animateTo(target, spec)
            settled = true
        }
    }
    LaunchedEffect(scroll, reduced, hasHead) {
        if (!hasHead) return@LaunchedEffect
        snapshotFlow { scroll.canScrollBackward }.collect { hidden ->
            val target = if (hidden) 1f else 0f
            if (!settledBefore || reduced) before.snapTo(target) else before.animateTo(target, spec)
            settledBefore = true
        }
    }
    Column(modifier.fillMaxSize()) {
        head?.invoke()
        Column(
            Modifier.fillMaxWidth()
                .weight(1f)
                .then(
                    if (!hasFoot && !hasHead) Modifier
                    else
                        Modifier.kitScrollEdges(
                            // Under a pinned head the body thins out as it passes beneath it.
                            clearAbove = if (hasHead) ({ 0f }) else null,
                            topReach = COMPOSER_FADE_DP.dp,
                            strength = { before.value },
                            fadeFromBottom = if (hasFoot) ({ with(density) { COMPOSER_FADE_DP.dp.toPx() } }) else null,
                            bottomReach = 0.dp,
                            bottomReachPx = { with(density) { COMPOSER_FADE_DP.dp.toPx() } },
                            bottomStrength = { more.value },
                        )
                )
                .verticalScroll(scroll)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        foot?.invoke()
    }
}
