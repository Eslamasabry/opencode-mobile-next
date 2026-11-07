package io.github.eslamasabry.oc.ui.kit.agent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale

/**
 * [children] (agent cards, [KitAgentChoice]) in two columns of equal width, each pair as tall as its taller card, with
 * the kit's gap between them (Dart `KitAgentGrid`, lib/ui/kit/kit_agent_grid.dart). An odd last card spans both columns
 * and lays out sideways (mark at the start, words beside it). Above 1.3× text it is one column of full-width cards. It
 * scrolls with the page that holds it and follows the reading direction.
 *
 * States: none — a layout of its children
 * Text: none (children decide)
 * Cap: Reading (720)
 * Motion: none
 * Replaces: KitAgentGrid, KitAgentGridSpan (lib/ui/kit/kit_agent_grid.dart, kit_agent_grid_span.dart)
 */
@Composable
public fun KitAgentGrid(children: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    val layout = agentGridLayout(children.size, kitTextScale())
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth().selectableGroup()) {
        layout.forEachIndexed { i, cell ->
            if (i > 0) Spacer(Modifier.height(Space.S3.dp))
            when (cell) {
                is AgentGridCell.Single -> Box(Modifier.fillMaxWidth()) { children[cell.index]() }
                is AgentGridCell.Span -> Box(Modifier.fillMaxWidth()) { KitAgentGridSpan { children[cell.index]() } }
                is AgentGridCell.Pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(Modifier.weight(1f).fillMaxHeight()) { children[cell.start]() }
                        Spacer(Modifier.width(Space.S3.dp))
                        Box(Modifier.weight(1f).fillMaxHeight()) { children[cell.start + 1]() }
                    }
            }
        }
    }
}

/** One line of a [KitAgentGrid]: a full-width card, a pair, or the odd last card spanning the row. */
internal sealed interface AgentGridCell {
    data class Single(val index: Int) : AgentGridCell
    data class Pair(val start: Int) : AgentGridCell
    data class Span(val index: Int) : AgentGridCell
}

/** The grid's lines for [count] cards at text scale [scale] (kit_agent_grid.dart:29-65). */
internal fun agentGridLayout(count: Int, scale: Float): List<AgentGridCell> {
    // At large text two columns are too narrow for a name: one column of full-width cards.
    if (scale > 1.3f) return List(count) { AgentGridCell.Single(it) } // F: lib/ui/kit/kit_agent_grid.dart:29
    val odd = count % 2 == 1 // F: lib/ui/kit/kit_agent_grid.dart:41,46
    val pairs = if (odd) count - 1 else count
    return buildList {
        for (i in 0 until pairs step 2) add(AgentGridCell.Pair(i)) // F: lib/ui/kit/kit_agent_grid.dart:46
        if (odd) add(AgentGridCell.Span(count - 1))
    }
}
