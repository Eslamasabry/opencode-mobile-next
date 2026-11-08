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
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/** A tile's least comfortable width in the [KitAgentTiles] grid; a row holds as many as fit, two to four. */
internal const val AGENT_TILE_MIN_WIDTH: Int = 200

/**
 * How many tiles side by side in [widthDp]: one above 1.3x text, else as many [AGENT_TILE_MIN_WIDTH] tiles as fit with
 * the kit's gap, never fewer than two (a phone) nor more than four (a wide window, still inside the Reading cap).
 */
internal fun agentTileColumns(widthDp: Float, textScale: Float): Int {
    if (agentGridOneColumn(textScale)) return 1
    val gap = Space.S3.toFloat()
    val fit = ((widthDp + gap) / (AGENT_TILE_MIN_WIDTH + gap)).toInt()
    return fit.coerceIn(2, 4)
}

/**
 * Where each tile goes for [columns]: full rows of [columns], and the rest in a last row. In two columns an odd last
 * tile is alone and spans the row (as [KitAgentGrid]); with three or four the last row keeps the column width.
 */
internal fun agentTileRows(count: Int, columns: Int): List<List<Int>> = (0 until count).chunked(columns.coerceAtLeast(1))

/**
 * Agent tiles ([KitAgentTile]) in a bento grid: two columns on a phone, three or four when the window is wide (each at
 * least [AGENT_TILE_MIN_WIDTH] dp, inside the Reading cap), every row as tall as its tallest tile with the kit's gap
 * between them. In two columns an odd last tile spans the row and lies sideways, as in [KitAgentGrid]; at large text it
 * is one column of full-width tiles. Unlike [KitAgentGrid] the tiles are separate buttons, not one choice.
 *
 * States: none — a layout of its children.
 */
@Composable
public fun KitAgentTiles(children: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    val scale = kitTextScale()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth()) {
        val columns = agentTileColumns(maxWidth.value, scale)
        val rows = agentTileRows(children.size, columns)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            for (row in rows) {
                if (row.size == 1 && columns == 2) {
                    CompositionLocalProvider(LocalKitAgentGridSpan provides true) { children[row[0]]() }
                } else if (columns == 1) {
                    children[row[0]]()
                } else {
                    EqualRow(row.map { children[it] }, columns)
                }
            }
        }
    }
}

/**
 * One row of tiles, each [columns]-th of the width (less the gap) and all as tall as the tallest. Not
 * `IntrinsicSize.Min`: a tile's chip measures itself with BoxWithConstraints, which has no intrinsic size. Each tile is
 * measured once at its width, then placed at the row's height; a short last row keeps the column width.
 */
@Composable
private fun EqualRow(cells: List<@Composable () -> Unit>, columns: Int) {
    androidx.compose.ui.layout.SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val gap = Space.S3.dp.roundToPx()
        val width = ((constraints.maxWidth - gap * (columns - 1)) / columns).coerceAtLeast(0)
        val cell = androidx.compose.ui.unit.Constraints(minWidth = width, maxWidth = width)
        val height =
            cells.indices.maxOfOrNull { k -> subcompose("probe-$k") {
                    // Measured only, never placed: no semantics of its own, so the page reads each tile once.
                    androidx.compose.foundation.layout.Box(
                        Modifier.clearAndSetSemantics {}
                    ) { cells[k]() }
                }.maxOfOrNull { it.measure(cell).height } ?: 0 }
                ?: 0
        val placed =
            cells.indices.map { k ->
                subcompose("tile-$k") { androidx.compose.foundation.layout.Box(Modifier.fillMaxHeight()) { cells[k]() } }
                    .map { it.measure(androidx.compose.ui.unit.Constraints.fixed(width, height)) }
            }
        layout(constraints.maxWidth, height) {
            placed.forEachIndexed { k, parts -> parts.forEach { it.placeRelative(k * (width + gap), 0) } }
        }
    }
}
