package io.github.eslamasabry.oc.ui.kit.rb.o18

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitInlineStateView
import io.github.eslamasabry.oc.ui.kit.team.KitBoardCard
import io.github.eslamasabry.oc.ui.kit.team.KitBoardColumn
import io.github.eslamasabry.oc.ui.kit.team.KitBoardLane
import io.github.eslamasabry.oc.ui.kit.team.KitBoardLanes
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphMark
import kotlin.test.Test

/** The scenes of test/goldens/kit/kit_board_lane_golden_test.dart at 8302de537 (Scaffold > SafeArea: the whole window). */
class KitBoardLaneGateTest {
    private val names = listOf("Backlog", "Ready", "Working", "Review", "Done")

    /** Fixed card words (TEST-11: no clock in the gallery). */
    private val titles =
        listOf(
            listOf("Write the release notes", "Tidy the settings screen"),
            listOf("Add the Arabic plural rules"),
            listOf("Fix the sync engine retry", "Index the new project", "Rename the export button"),
            emptyList(),
            listOf("Ship 1.0.44", "Move the voice packs", "Trim the setup log", "Answer issue 87"),
        )

    @Composable
    private fun Card(lane: Int, i: Int, needsYou: Int) {
        val t = titles[lane]
        val title = if (i < t.size) t[i] else "Follow-up task ${i + 1}"
        val needs = lane == 2 && i < needsYou
        val mark =
            if (needs) KitWorkGraphMark.NeedsYou
            else when (lane) {
                2 -> KitWorkGraphMark.Working
                4 -> KitWorkGraphMark.Done
                else -> KitWorkGraphMark.Waiting
            }
        BoardTaskCardStandIn(title, mark, listOf(if (i % 2 == 0) "fox" else "owl", "${(i + 1) * 4} min ago"), needsYouFlag = needs)
    }

    @Composable
    private fun Board(selected: Int, loading: Boolean = false, needsYou: Int = 1, working: Int = 3) {
        val counts = listOf(2, 1, working, 0, 4)
        KitBoardLanes(
            columns = names.mapIndexed { i, n -> KitBoardColumn(n, count = if (loading) null else counts[i], needsYou = if (i == 2) needsYou else 0) },
            selected = selected,
            onSelected = {},
            modifier = Modifier.fillMaxSize(),
            loading = loading,
        ) { lane ->
            KitBoardLane(
                cards = (0 until counts[lane]).map { i -> KitBoardCard("card-$lane-$i") { Card(lane, i, needsYou) } },
                modifier = Modifier.testTag("list-$lane"),
                empty = { KitInlineStateView("Nothing waiting for review", KitIcons.checklist) },
            )
        }
    }

    @Test fun kit_board_lane_loading() { FlutterGoldenGate.check("kit_board_lane_loading", Frame.Screen) { Board(selected = 2, loading = true) } }
    @Test fun kit_board_lane_loaded() { FlutterGoldenGate.check("kit_board_lane_loaded", Frame.Screen) { Board(selected = 2) } }
    @Test fun kit_board_lane_lane_empty() { FlutterGoldenGate.check("kit_board_lane_lane_empty", Frame.Screen) { Board(selected = 3) } }
    @Test fun kit_board_lane_needs_you() { FlutterGoldenGate.check("kit_board_lane_needs_you", Frame.Screen) { Board(selected = 2, needsYou = 2) } }

    @Test fun kit_board_lane_long_lane() {
        FlutterGoldenGate.check(
            "kit_board_lane_long_lane",
            Frame.Screen,
            act = { onNodeWithTag("list-2").performTouchInput { swipeUp() } },
        ) { Board(selected = 2, working = 12) }
    }

    @Test fun kit_board_lane_loaded_1280x800() { FlutterGoldenGate.check("kit_board_lane_loaded_1280x800", Frame.Screen) { Board(selected = 0) } }
    @Test fun kit_board_lane_loaded_text2() { FlutterGoldenGate.check("kit_board_lane_loaded_text2", Frame.Screen) { Board(selected = 2) } }
    @Test fun kit_board_lane_loaded_text2_1280x800() { FlutterGoldenGate.check("kit_board_lane_loaded_text2_1280x800", Frame.Screen) { Board(selected = 2) } }

    @Test fun matrix() {
        // One lane: the cap row measures the lane (400); the board around it fills its host.
        FlutterGoldenGate.matrix("part-kit_board_lane", caps = mapOf("KitBoardLane" to "lane", "KitBoardLanes" to "fill")) {
            Box(Modifier.height(480.dp)) {
                KitBoardLane(
                    cards = (0 until 3).map { i -> KitBoardCard("card-2-$i") { Card(2, i, needsYou = 1) } },
                    empty = { KitInlineStateView("Nothing waiting for review", KitIcons.checklist) },
                )
            }
        }
    }
}
