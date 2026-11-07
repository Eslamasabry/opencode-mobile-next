package io.github.eslamasabry.oc.ui.kit.rb.o20

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import kotlin.test.Test

/** test/goldens/kit/kit_promote_card_golden_test.dart: every state of teamSample('kit_promote_card'), then 2.0 text at 412x915 and 1280x800. */
class KitPromoteCardGateTest {
    @Test fun kit_promote_card_loading() { FlutterGoldenGate.check("kit_promote_card_loading", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.Loading) } }
    @Test fun kit_promote_card_empty() { FlutterGoldenGate.check("kit_promote_card_empty", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.Empty) } }
    @Test fun kit_promote_card_error() { FlutterGoldenGate.check("kit_promote_card_error", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.Failed) } }
    @Test fun kit_promote_card_working() { FlutterGoldenGate.check("kit_promote_card_working", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.Running) } }
    @Test fun kit_promote_card_disabled() { FlutterGoldenGate.check("kit_promote_card_disabled", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.Stale) } }
    @Test fun kit_promote_card_answered() { FlutterGoldenGate.check("kit_promote_card_answered", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.Done) } }
    @Test fun kit_promote_card_needs_you() { FlutterGoldenGate.check("kit_promote_card_needs_you", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.NeedsYou) } }
    @Test fun kit_promote_card_stalled() { FlutterGoldenGate.check("kit_promote_card_stalled", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.Stalled) } }
    @Test fun kit_promote_card_stale() { FlutterGoldenGate.check("kit_promote_card_stale", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card", KitTeamState.Stale) } }
    @Test fun kit_promote_card_text2() { FlutterGoldenGate.check("kit_promote_card_text2", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card") } }
    @Test fun kit_promote_card_text2_1280x800() { FlutterGoldenGate.check("kit_promote_card_text2_1280x800", wrap = { FlutterCapture(it) }) { TeamSample("kit_promote_card") } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_promote_card", caps = mapOf("KitPromoteCard" to "reading")) { TeamSample("kit_promote_card") } }
}
