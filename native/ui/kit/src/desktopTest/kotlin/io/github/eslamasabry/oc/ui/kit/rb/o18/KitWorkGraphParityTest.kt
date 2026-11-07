package io.github.eslamasabry.oc.ui.kit.rb.o18

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphGeometry
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphMark
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pure geometry of lib/ui/kit/kit_work_graph.dart at 8302de537, as test/team_work_graph_test.dart reads it. */
class KitWorkGraphParityTest {
    private val node = Size(156f, 56f)

    @Test fun constants() {
        assertEquals(16f, KitWorkGraphGeometry.CANVAS_PADDING) // kit_work_graph.dart:326
        assertEquals(12f, KitWorkGraphGeometry.LANE_WIDTH) // kit_work_graph.dart:331
    }

    @Test fun six_items_layer_order_critical_path_and_blocked_chain() {
        val g = KitWorkGraphGeometry.layers(workGraphSix(), node)
        assertEquals(listOf(listOf("a", "f"), listOf("b", "e"), listOf("c"), listOf("d")), g.layerRows)
        assertEquals(listOf("a", "b", "c", "d"), g.criticalPath)
        assertEquals(setOf("b", "c", "d"), g.blockedChain)
        assertEquals(setOf("a" to "b", "b" to "c", "c" to "d"), g.criticalEdges)
        assertEquals(Size(2 * 156f + 24f + 32f, 4 * 56f + 3 * 48f + 32f), g.size)
        // Rows are centred on the widest: the single-node rows sit under the middle.
        assertEquals(16f, g.rects.getValue("a").left)
        assertEquals(16f + 156f + 24f, g.rects.getValue("f").left)
        assertEquals(16f + (336f - 156f) / 2, g.rects.getValue("c").left)
        val ab = g.edges.single { it.from == "a" && it.to == "b" }
        assertTrue(ab.critical && !ab.blocked)
        assertEquals(Offset(16f + 78f, 16f + 56f), ab.start)
        assertEquals(ab.start + Offset(0f, 24f), ab.control1)
        val bc = g.edges.single { it.from == "b" && it.to == "c" }
        assertTrue(bc.critical && bc.blocked)
        val ae = g.edges.single { it.from == "a" && it.to == "e" }
        assertTrue(!ae.critical && !ae.blocked)
        assertEquals(0f, g.gutter)
    }

    @Test fun rows_share_the_order_and_assign_lanes_greedily() {
        val g = KitWorkGraphGeometry.rows(workGraphSix(), 380f) { 64f }
        assertEquals(listOf("a", "f", "b", "e", "c", "d"), g.layerRows.flatten())
        val lane = g.edges.associate { (it.from to it.to) to it.lane }
        assertEquals(mapOf(("a" to "b") to 0, ("a" to "e") to 1, ("b" to "c") to 0, ("c" to "d") to 0), lane)
        assertEquals(24f, g.gutter)
        assertEquals(Size(380f, 6 * 64f), g.size)
        assertEquals(24f, g.rects.getValue("a").left)
        assertEquals(380f - 24f, g.rects.getValue("a").width)
        val ae = g.edges.single { it.from == "a" && it.to == "e" }
        assertEquals(Offset(12f + 6f, 32f), ae.start)
        assertEquals(Offset(12f + 6f, 3 * 64f + 32f), ae.end)
    }

    @Test fun blocked_chain_runs_downstream_of_a_stuck_item() {
        val g = KitWorkGraphGeometry.rows(workGraphBlockedChain(), 380f) { 64f }
        assertEquals(setOf("x", "y", "z"), g.blockedChain)
        assertTrue(g.edges.all { it.blocked })
    }

    @Test fun a_cycle_is_broken_and_duplicates_dropped() {
        val nodes =
            listOf(
                KitWorkGraphNode("a", "A", KitWorkGraphMark.Waiting, dependsOn = listOf("b")),
                KitWorkGraphNode("b", "B", KitWorkGraphMark.Waiting, dependsOn = listOf("a", "a", "zz")),
                KitWorkGraphNode("a", "A again", KitWorkGraphMark.Done),
            )
        val g = KitWorkGraphGeometry.layers(nodes, node)
        assertEquals(2, g.nodes.size)
        assertEquals(1, g.edges.size)
        assertEquals(2, g.criticalPath.size)
    }

    @Test fun empty_and_one() {
        val empty = KitWorkGraphGeometry.layers(emptyList(), node)
        assertEquals(Size(32f, 32f), empty.size)
        assertTrue(empty.criticalPath.isEmpty())
        val one = KitWorkGraphGeometry.layers(listOf(KitWorkGraphNode("a", "A", KitWorkGraphMark.Working)), node)
        assertEquals(listOf("a"), one.criticalPath)
        assertTrue(one.edges.isEmpty())
    }
}
