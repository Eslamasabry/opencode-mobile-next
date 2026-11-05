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
public fun KitFilterChips(modifier: Modifier = Modifier, chips: @Composable RowScope.() -> Unit) {
    val tokens = LocalKitTokens.current
    val scroll = rememberScrollState()
    val colors = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current
    Box(
        modifier.kitMaxWidth(CapRole.Reading)
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
            Modifier.horizontalScroll(scroll).padding(horizontal = tokens.gutter.dp),
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = chips,
        )
    }
}
