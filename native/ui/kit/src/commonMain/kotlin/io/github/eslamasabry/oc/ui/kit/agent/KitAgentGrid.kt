package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale

/** Dart `KitAgentGridSpan`: true for the card that spans the whole row and lays out sideways. */
internal val LocalKitAgentGridSpan = staticCompositionLocalOf { false }

/** One column of full-width cards above 1.3x text (Dart `textScaler.scale(1) > 1.3`). */
internal fun agentGridOneColumn(textScale: Float): Boolean = textScale > 1.3f // F: lib/ui/kit/kit_agent_grid.dart:29

/**
 * Where each card goes: pairs of indices side by side, and an odd last card alone across the row (Dart `pairs`/`odd`).
 * In one column every card is its own row.
 */
internal fun agentGridRows(count: Int, oneColumn: Boolean): List<List<Int>> =
    if (oneColumn) List(count) { listOf(it) } else (0 until count).chunked(2) // F: lib/ui/kit/kit_agent_grid.dart:46

/**
 * Agent cards in two columns of equal width, each pair as tall as its taller card, with the kit's gap between them (a
 * port of Dart `KitAgentGrid`, lib/ui/kit/kit_agent_grid.dart). An odd last card spans both columns and lays out
 * sideways. At large text it is one column of full-width cards, each still mark above words. It scrolls with the page
 * and follows the reading direction.
 *
 * States: none — a layout of its children.
 */
@Composable
public fun KitAgentGrid(children: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    val oneColumn = agentGridOneColumn(kitTextScale())
    val rows = agentGridRows(children.size, oneColumn)
    Column(
        modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        for (row in rows) {
            if (row.size == 2) { // F: lib/ui/kit/kit_agent_grid.dart:46
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Space.S3.dp),
                ) {
                    for (i in row) {
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxHeight()) { children[i]() }
                    }
                }
            } else {
                // The odd last card spans the row and lies sideways; in one column the cards stay stacked.
                CompositionLocalProvider(LocalKitAgentGridSpan provides !oneColumn) { children[row[0]]() }
            }
        }
    }
}
