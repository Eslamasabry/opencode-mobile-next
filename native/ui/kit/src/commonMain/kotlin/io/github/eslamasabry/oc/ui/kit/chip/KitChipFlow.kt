package io.github.eslamasabry.oc.ui.kit.chip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitRestsWhole
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowReach
import io.github.eslamasabry.oc.ui.kit.foundation.kitShadowRoom
import io.github.eslamasabry.oc.ui.kit.frame.FloatingPaneRow
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitOnFloatingPane
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Filter chips that are always whole: on one line when they fit, else the line that does not fit continues on the next.
 * Nothing scrolls and no chip is ever cut by the edge or thinned by a fade, so no label reads half-written ("All co")
 * at 360 dp or at large text. Same chips and gutters as [KitFilterChips] (this is the Home's line beside the server
 * pill); it grows a line only when it must, so no new row level appears.
 *
 * States: none (each chip carries its own selected look)
 * Text: frame (the chips)
 * Cap: Reading
 * Motion: none
 * Replaces: KitFilterChips where a cut chip is not acceptable (the Home's chips)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun KitChipFlow(
    modifier: Modifier = Modifier,
    /** The line sits beside something that already keeps the start gutter (the server pill): it starts flush. */
    bleed: Boolean = false,
    chips: @Composable () -> Unit,
) {
    val room = kitShadowReach()
    val inset = if (bleed) 0.dp else kitPageGutter()
    val onPane = LocalKitOnFloatingPane.current
    val paneInset = FloatingPaneRow.contentInsetDp(LocalKitTokens.current.gutter).dp
    val start = if (!onPane) inset else if (bleed) 0.dp else paneInset
    val end = if (onPane) paneInset else inset
    Box(
        modifier.kitMaxWidth(CapRole.Reading)
            .then(if (onPane) Modifier.padding(start = if (bleed) 0.dp else FloatingPaneRow.EDGE_DP.dp, end = FloatingPaneRow.EDGE_DP.dp) else Modifier)
            .kitShadowRoom(room, start = 0.dp, end = 0.dp)
            .kitRestsWhole("chip line")
            .testTag("kit-chip-flow"),
    ) {
        FlowRow(
            Modifier.padding(start = start, end = end, top = room, bottom = room),
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.CenterVertically),
        ) { chips() }
    }
}
