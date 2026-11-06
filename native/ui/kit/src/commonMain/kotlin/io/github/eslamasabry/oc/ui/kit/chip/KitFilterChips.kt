package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowReach
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowRoom
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.kitScrollFade
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Dart `KitFilterChips` numbers. */
public object FilterChipsRules {
    /** An edge's fade: the gutter plus 32 dp, enough to show a word fading rather than cut. */
    public fun fadeWidthDp(gutter: Int): Int = gutter + 32 // F: lib/ui/kit/kit_filter_chips.dart:28
}

/**
 * The filter chips under a screen's title ("All projects", "Needs you", "Running") (Dart `KitFilterChips`; plan 06
 * §7.5). The chips are `KitChip`s the caller builds (selected = the active filter; only "Needs you" may be amber); this
 * part lays them on one line on the gutter. The line scrolls sideways when they do not fit (large text, Arabic): it
 * never wraps and no chip is cut or hidden; an edge with more past it fades ([kitScrollFade]).
 *
 * States: none (each chip carries its own selected look)
 * Text: frame (the chips)
 * Cap: Reading
 * Motion: the fade follows the scroll (class Follow)
 * Replaces: KitFilterChips, KitFilterChip (lib/ui/kit/kit_filter_chips.dart)
 */
@Composable
public fun KitFilterChips(
    modifier: Modifier = Modifier,
    bleed: Boolean = false,
    chips: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalKitTokens.current
    val scroll = rememberScrollState()
    val colors = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current
    // The line clips sideways for its scroll and fade; the chips' style shadows keep their room above and below.
    val room = kitShadowReach()
    // [bleed]: the line sits in a block that already keeps the gutter (a screen's bottom block); it reaches out over
    // that gutter to the screen's edges, so the chips still start on the gutter and scroll out of sight at the edge.
    val reach =
        if (!bleed) Modifier
        else
            Modifier.layout { measurable, constraints ->
                val extra = tokens.gutter.dp.roundToPx() * 2
                val wide =
                    if (constraints.hasBoundedWidth)
                        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
                    else constraints
                val placeable = measurable.measure(wide)
                val shown = if (constraints.hasBoundedWidth) placeable.width - extra else placeable.width
                layout(shown, placeable.height) { placeable.place(if (constraints.hasBoundedWidth) -extra / 2 else 0, 0) }
            }
    Box(
        modifier.then(reach).kitMaxWidth(CapRole.Reading)
            .kitShadowRoom(room)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val rtl = layoutDirection == LayoutDirection.Rtl
                val left = if (rtl) scroll.value < scroll.maxValue else scroll.value > 0
                val right = if (rtl) scroll.value > 0 else scroll.value < scroll.maxValue
                if (left || right) {
                    val edge = (FilterChipsRules.fadeWidthDp(tokens.gutter).dp.toPx() / size.width).coerceIn(0f, .5f) // F: lib/ui/kit/kit_filter_chips.dart:107
                    val solid = colors.ground.copy(alpha = 1f)
                    val clear = solid.copy(alpha = 0f)
                    drawRect(Brush.horizontalGradient(0f to if (left) clear else solid, edge to solid, (1f - edge) to solid, 1f to if (right) clear else solid), blendMode = BlendMode.DstIn)
                }
            }
            .testTag("kit-filter-chips"),
    ) {
        Row(
            Modifier.horizontalScroll(scroll).padding(horizontal = tokens.gutter.dp, vertical = room),
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = chips,
        )
    }
}
