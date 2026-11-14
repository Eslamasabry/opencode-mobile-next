package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion

/**
 * A scrolling page body with a block pinned under it (a guide's "4 of 4 / Finish" bar, a form's Send row, a footer
 * line). While more of the body lies hidden below, its content dissolves out over the kit's page edge
 * ([TOP_DISSOLVE_DP]) and is gone [FOOT_GAP_DP] above the foot, so a line never runs into the foot's words (audit 2,
 * 2026-11-14: Report a problem's "Performance" heading sliced in half against its footer); scrolled to the end, or with
 * nothing to scroll, the body is left whole. Without a [foot] the page frame's own bottom edge does the same above the
 * navigation bar.
 *
 * Inside a page frame ([KitScreen]) the body reports its scroll and its foot to the frame, whose one page edge draws
 * both ends (one offscreen layer per page); outside one (a sheet, a preview) it draws its own bottom edge. The fade
 * eases in and out with the kit's enter motion as the list leaves and reaches its end (at once under reduced motion),
 * is read while drawing, and sits on whole pixels (see [wholePx]). [contentPadding] and [verticalArrangement] are the
 * scrolling column's own, inside the fade.
 */
@Composable
public fun KitPinnedFootBody(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    foot: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val frame = LocalKitScreenScroll.current
    KitReportPageScroll(scroll)
    val more = remember { Animatable(0f) }
    var settled by remember { mutableStateOf(false) }
    val reduced = KitMotion.state.reduced
    val hasFoot = foot != null
    val ownEdge = hasFoot && frame == null
    val spec = KitMotion.specs.enter<Float>()
    LaunchedEffect(scroll, reduced, ownEdge) {
        if (!ownEdge) return@LaunchedEffect
        snapshotFlow { scroll.canScrollForward }.collect { hidden ->
            val target = if (hidden) 1f else 0f
            if (!settled || reduced) more.snapTo(target) else more.animateTo(target, spec)
            settled = true
        }
    }
    // The frame's edge ends above the foot and leaves it whole.
    if (frame != null) {
        DisposableEffect(frame, hasFoot) {
            if (!hasFoot) frame.footRoom = 0
            onDispose { frame.footRoom = 0 }
        }
    }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth()
                .weight(1f)
                .then(
                    if (!ownEdge) Modifier
                    else
                        Modifier.kitScrollEdges(
                            clearAbove = null,
                            topReach = 0.dp,
                            fadeFromBottom = { with(density) { (TOP_DISSOLVE_DP + FOOT_GAP_DP).dp.toPx() } },
                            bottomReach = 0.dp,
                            bottomReachPx = { with(density) { TOP_DISSOLVE_DP.dp.toPx() } },
                            bottomStrength = { more.value },
                        )
                )
                .verticalScroll(scroll)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        foot?.let {
            Box(Modifier.fillMaxWidth().then(if (frame == null) Modifier else Modifier.onSizeChanged { frame.footRoom = it.height })) { it() }
        }
    }
}
