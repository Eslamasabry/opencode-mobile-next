package io.github.eslamasabry.oc.ui.kitchat.rb.o15

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kitchat.tool.kitWorkIcon
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_work_line_golden_test.dart: a reply's prose, the work line under it and the
 * reply's next line, 16 dp from the sides, 8 dp apart, inside Flutter's `kitGalleryPart` frame ([FlutterGoldenGate.Frame.Part]:
 * scrolled, 16 dp above and below, centred, at most 720 dp). Until 2026-11-07 the case used `Frame.Screen`, which put
 * the scene 16 dp higher than the golden (SCOREBOARD-2026-11-07 B3).
 *
 * The summary is `KitWorkLine.summaryOf(counts: read 3, edited 1, ran 2)` and the state words are the English ARB
 * strings (kitWorkWaitingForYou, kitWorkDidntFinish, kitWorkStopped): the caller gives the native line its words.
 */
class KitWorkLineGateTest {
    private fun scene(state: KitWorkState, expanded: Boolean = state.opensByDefault, now: String? = null): @Composable () -> Unit = {
        val words = when (state) {
            KitWorkState.Running -> "Working"
            KitWorkState.WaitingForYou -> "Waiting for you"
            KitWorkState.Done -> null
            KitWorkState.EndedFailed -> "Didn't finish"
            KitWorkState.Stopped -> "Stopped"
        }
        Column(Modifier.padding(horizontal = 16.dp)) { // F: test/goldens/kit/kit_work_line_golden_test.dart:29
            KitText("I will look at how the app starts, then fix the failing test.")
            Spacer(Modifier.height(8.dp)) // F: test/goldens/kit/kit_work_line_golden_test.dart:37
            KitWorkLine(
                label = "Read 3 files · edited 1 file · ran 2 commands",
                stateWords = words,
                state = state,
                expanded = expanded,
                onToggle = {},
                icon = kitWorkIcon(read = 3, edited = 1, ran = 2), // F: test/goldens/kit/kit_work_line_golden_test.dart:17
                now = now,
            ) {
                // F: test/goldens/kit/kit_work_line_golden_test.dart:21-25 plain mono text stands in for tool rows
                KitText("Read lib/main.dart", role = TypeRole.Mono)
                KitText("Edited lib/ui/kit/chat/kit_work_line.dart", role = TypeRole.Mono)
                KitText("Ran flutter test test/kit", role = TypeRole.Mono)
            }
            Spacer(Modifier.height(8.dp)) // F: test/goldens/kit/kit_work_line_golden_test.dart:39
            KitText("The test passes now; the change is one line.")
        }
    }

    @Test fun states() {
        FlutterGoldenGate.check("kit_work_line_running", content = scene(KitWorkState.Running, now = "Editing lib/main.dart"))
        FlutterGoldenGate.check("kit_work_line_waiting_for_you", content = scene(KitWorkState.WaitingForYou))
        FlutterGoldenGate.check("kit_work_line_done", content = scene(KitWorkState.Done))
        FlutterGoldenGate.check("kit_work_line_done_expanded", content = scene(KitWorkState.Done, expanded = true))
        FlutterGoldenGate.check("kit_work_line_ended_failed", content = scene(KitWorkState.EndedFailed))
        FlutterGoldenGate.check("kit_work_line_stopped", content = scene(KitWorkState.Stopped))
    }

    @Test fun default_sizes() {
        for (suffix in listOf("_1280x800", "_text2", "_text2_1280x800")) {
            FlutterGoldenGate.check("kit_work_line_default$suffix", content = scene(KitWorkState.Done))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_work_line", caps = mapOf("KitWorkLine" to "conversation"), content = scene(KitWorkState.Done))
    }
}
