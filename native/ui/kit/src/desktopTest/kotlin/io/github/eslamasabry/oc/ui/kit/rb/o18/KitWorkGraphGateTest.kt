package io.github.eslamasabry.oc.ui.kit.rb.o18

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraph
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphLayout
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphMark
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphNode
import kotlin.test.Test

/** The six-item fixture (done, working, blocked, needs you, queued, failed) of kit_work_graph_golden_test.dart. */
internal fun workGraphSix(): List<KitWorkGraphNode> =
    listOf(
        KitWorkGraphNode(id = "a", title = "Storage layer", mark = KitWorkGraphMark.Done, open = false),
        KitWorkGraphNode(id = "b", title = "Sync engine", mark = KitWorkGraphMark.Working, dependsOn = listOf("a")),
        KitWorkGraphNode(id = "c", title = "Conflict policy", mark = KitWorkGraphMark.Waiting, word = "Blocked", dependsOn = listOf("b"), stuck = true),
        KitWorkGraphNode(id = "d", title = "Background sync", mark = KitWorkGraphMark.Waiting, dependsOn = listOf("c")),
        KitWorkGraphNode(id = "e", title = "Unit tests", mark = KitWorkGraphMark.NeedsYou, dependsOn = listOf("a")),
        KitWorkGraphNode(id = "f", title = "Docs", mark = KitWorkGraphMark.Failed),
    )

/** A chain of three, all stuck-or-downstream; the stuck item's title wraps to two lines. */
internal fun workGraphBlockedChain(): List<KitWorkGraphNode> =
    listOf(
        KitWorkGraphNode(id = "x", title = "Provision the runner on the build machine before the window", mark = KitWorkGraphMark.Waiting, word = "Blocked", stuck = true),
        KitWorkGraphNode(id = "y", title = "Run the migration", mark = KitWorkGraphMark.Waiting, dependsOn = listOf("x")),
        KitWorkGraphNode(id = "z", title = "Publish the release", mark = KitWorkGraphMark.Waiting, dependsOn = listOf("y")),
    )

class KitWorkGraphGateTest {
    /** The host's rails: rows run the full width between them. */
    @Composable
    private fun Rails(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content() }
    }

    @Composable
    private fun Tall(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth().height(640.dp)) { content() }
    }

    @Test fun kit_work_graph_rows() { FlutterGoldenGate.check("kit_work_graph_rows") { Rails { KitWorkGraph(workGraphSix(), {}, layout = KitWorkGraphLayout.Rows) } } }
    @Test fun kit_work_graph_rows_blocked() { FlutterGoldenGate.check("kit_work_graph_rows_blocked") { Rails { KitWorkGraph(workGraphBlockedChain(), {}, layout = KitWorkGraphLayout.Rows) } } }
    @Test fun kit_work_graph_layers() { FlutterGoldenGate.check("kit_work_graph_layers") { Tall { KitWorkGraph(workGraphSix(), {}, layout = KitWorkGraphLayout.Layers) } } }
    @Test fun kit_work_graph_empty() { FlutterGoldenGate.check("kit_work_graph_empty") { Rails { KitWorkGraph(emptyList(), {}, layout = KitWorkGraphLayout.Rows) } } }

    @Test fun kit_work_graph_one() {
        FlutterGoldenGate.check("kit_work_graph_one") {
            Rails { KitWorkGraph(listOf(KitWorkGraphNode(id = "a", title = "Storage layer", mark = KitWorkGraphMark.Working)), {}, layout = KitWorkGraphLayout.Rows) }
        }
    }

    @Test fun kit_work_graph_auto_1280x800() { FlutterGoldenGate.check("kit_work_graph_auto_1280x800") { Tall { KitWorkGraph(workGraphSix(), {}) } } }
    @Test fun kit_work_graph_rows_text2() { FlutterGoldenGate.check("kit_work_graph_rows_text2") { Rails { KitWorkGraph(workGraphSix(), {}, layout = KitWorkGraphLayout.Rows) } } }
    @Test fun kit_work_graph_rows_text2_1280x800() { FlutterGoldenGate.check("kit_work_graph_rows_text2_1280x800") { Rails { KitWorkGraph(workGraphSix(), {}, layout = KitWorkGraphLayout.Rows) } } }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_work_graph", caps = mapOf("KitWorkGraph" to "fill")) { Rails { KitWorkGraph(workGraphSix(), {}, layout = KitWorkGraphLayout.Rows) } }
    }
}
