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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.kitScrollFade
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Dart `KitFilterChips` numbers. */
public object FilterChipsRules {
    /** An edge's fade: the gutter plus 32 dp, enough to show a word fading rather than cut. */
    public fun fadeWidthDp(gutter: Int): Int = gutter + 32
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
    Box(
        modifier.fillMaxWidth()
            .clipToBounds()
            .kitScrollFade(scroll, FilterChipsRules.fadeWidthDp(tokens.gutter).dp)
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
