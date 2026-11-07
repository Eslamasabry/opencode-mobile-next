package io.github.eslamasabry.oc.ui.kit.rb.o20

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import kotlin.test.Test

/** test/goldens/kit/kit_digest_golden_test.dart: every state of teamSample('kit_digest'), then 2.0 text at 412x915 and 1280x800. */
class KitDigestGateTest {
    @Test fun kit_digest_loading() { FlutterGoldenGate.check("kit_digest_loading", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.Loading) } }
    @Test fun kit_digest_empty() { FlutterGoldenGate.check("kit_digest_empty", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.Empty) } }
    @Test fun kit_digest_error() { FlutterGoldenGate.check("kit_digest_error", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.Failed) } }
    @Test fun kit_digest_working() { FlutterGoldenGate.check("kit_digest_working", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.Running) } }
    @Test fun kit_digest_disabled() { FlutterGoldenGate.check("kit_digest_disabled", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.Stale) } }
    @Test fun kit_digest_answered() { FlutterGoldenGate.check("kit_digest_answered", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.Done) } }
    @Test fun kit_digest_needs_you() { FlutterGoldenGate.check("kit_digest_needs_you", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.NeedsYou) } }
    @Test fun kit_digest_stalled() { FlutterGoldenGate.check("kit_digest_stalled", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.Stalled) } }
    @Test fun kit_digest_stale() { FlutterGoldenGate.check("kit_digest_stale", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest", KitTeamState.Stale) } }
    @Test fun kit_digest_summary_text2() { FlutterGoldenGate.check("kit_digest_summary_text2", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest") } }
    @Test fun kit_digest_summary_text2_1280x800() { FlutterGoldenGate.check("kit_digest_summary_text2_1280x800", wrap = { FlutterCapture(it) }) { TeamSample("kit_digest") } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_digest", caps = mapOf("KitDigest" to "reading")) { TeamSample("kit_digest") } }
}
