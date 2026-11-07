package io.github.eslamasabry.oc.ui.kit.rb.o20

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import kotlin.test.Test

/** test/goldens/kit/kit_milestone_row_golden_test.dart: every state of teamSample('kit_milestone_row'), then 2.0 text at 412x915 and 1280x800. */
class KitMilestoneRowGateTest {
    @Test fun kit_milestone_row_loading() { FlutterGoldenGate.check("kit_milestone_row_loading", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.Loading) } }
    @Test fun kit_milestone_row_empty() { FlutterGoldenGate.check("kit_milestone_row_empty", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.Empty) } }
    @Test fun kit_milestone_row_error() { FlutterGoldenGate.check("kit_milestone_row_error", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.Failed) } }
    @Test fun kit_milestone_row_working() { FlutterGoldenGate.check("kit_milestone_row_working", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.Running) } }
    @Test fun kit_milestone_row_disabled() { FlutterGoldenGate.check("kit_milestone_row_disabled", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.Stale) } }
    @Test fun kit_milestone_row_answered() { FlutterGoldenGate.check("kit_milestone_row_answered", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.Done) } }
    @Test fun kit_milestone_row_needs_you() { FlutterGoldenGate.check("kit_milestone_row_needs_you", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.NeedsYou) } }
    @Test fun kit_milestone_row_stalled() { FlutterGoldenGate.check("kit_milestone_row_stalled", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.Stalled) } }
    @Test fun kit_milestone_row_stale() { FlutterGoldenGate.check("kit_milestone_row_stale", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row", KitTeamState.Stale) } }
    @Test fun kit_milestone_row_text2() { FlutterGoldenGate.check("kit_milestone_row_text2", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row") } }
    @Test fun kit_milestone_row_text2_1280x800() { FlutterGoldenGate.check("kit_milestone_row_text2_1280x800", wrap = { FlutterCapture(it) }) { TeamSample("kit_milestone_row") } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_milestone_row", caps = mapOf("KitMilestoneRow" to "list")) { TeamSample("kit_milestone_row") } }
}
