package io.github.eslamasabry.oc.ui.kit.rb.o30

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnIllustration
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesFolderScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesSearchScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesSheetScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesTerminalScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesTrayScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesUnpluggedScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesWorkingScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamBoardScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamDiscoverRelayScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamDiscoverTeaserScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamIdleScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamMergedScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamNudgeScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamPlanningScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamRestScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamWakingScene
import kotlin.test.Test

/**
 * The contact sheets of test/goldens/kit/kit_scenes_golden_test.dart that order 30 owns (`states`, `working`,
 * `team`, `team_discover`): each scene's finished frame at its gallery width, in a centred Wrap `space4` apart,
 * captured like kitGalleryPart (view DPR 3, 1x picture).
 */
class KitScenesGateTest {
    @OptIn(ExperimentalLayoutApi::class)
    private fun sheet(scenes: List<Pair<KitDrawnScene, Int>>): @Composable () -> Unit = {
        val gap = FlutterKitTokens.SPACE4.dp
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
        ) {
            for ((scene, width) in scenes) {
                KitDrawnIllustration(scene, Modifier.align(Alignment.CenterVertically), width = width.dp)
            }
        }
    }

    private fun gate(name: String, scenes: List<Pair<KitDrawnScene, Int>>) {
        FlutterGoldenGate.check(name = name, wrap = { FlutterCapture(it) }, content = sheet(scenes))
    }

    @Test fun kit_scenes_states() =
        gate(
            "kit_scenes_states",
            listOf(
                StatesSheetScene to 88,
                StatesFolderScene to 88,
                StatesTrayScene to 88,
                StatesSearchScene to 88,
                StatesTerminalScene() to 88,
                StatesUnpluggedScene to 88,
            ),
        )

    @Test fun kit_scenes_working() = gate("kit_scenes_working", listOf(StatesWorkingScene to 64))

    @Test fun kit_scenes_team() =
        gate(
            "kit_scenes_team",
            listOf(
                TeamBoardScene to 110,
                TeamPlanningScene to 110,
                TeamWakingScene to 110,
                TeamMergedScene to 110,
                TeamNudgeScene to 110,
                TeamRestScene to 110,
                TeamIdleScene to 110,
            ),
        )

    @Test fun kit_scenes_team_discover() =
        gate("kit_scenes_team_discover", listOf(TeamDiscoverTeaserScene to 130, TeamDiscoverRelayScene to 170))
}
