package io.github.eslamasabry.oc.ui.kit.rb.o20

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import kotlin.test.Test

/** test/goldens/kit/kit_merge_queue_golden_test.dart: every state of teamSample('kit_merge_queue'), then 2.0 text at 412x915 and 1280x800. */
class KitMergeQueueGateTest {
    @Test fun kit_merge_queue_loading() { FlutterGoldenGate.check("kit_merge_queue_loading", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.Loading) } }
    @Test fun kit_merge_queue_empty() { FlutterGoldenGate.check("kit_merge_queue_empty", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.Empty) } }
    @Test fun kit_merge_queue_error() { FlutterGoldenGate.check("kit_merge_queue_error", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.Failed) } }
    @Test fun kit_merge_queue_working() { FlutterGoldenGate.check("kit_merge_queue_working", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.Running) } }
    @Test fun kit_merge_queue_disabled() { FlutterGoldenGate.check("kit_merge_queue_disabled", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.Stale) } }
    @Test fun kit_merge_queue_answered() { FlutterGoldenGate.check("kit_merge_queue_answered", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.Done) } }
    @Test fun kit_merge_queue_needs_you() { FlutterGoldenGate.check("kit_merge_queue_needs_you", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.NeedsYou) } }
    @Test fun kit_merge_queue_stalled() { FlutterGoldenGate.check("kit_merge_queue_stalled", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.Stalled) } }
    @Test fun kit_merge_queue_stale() { FlutterGoldenGate.check("kit_merge_queue_stale", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue", KitTeamState.Stale) } }
    @Test fun kit_merge_queue_text2() { FlutterGoldenGate.check("kit_merge_queue_text2", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue") } }
    @Test fun kit_merge_queue_text2_1280x800() { FlutterGoldenGate.check("kit_merge_queue_text2_1280x800", wrap = { FlutterCapture(it) }) { TeamSample("kit_merge_queue") } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_merge_queue", caps = mapOf("KitMergeQueue" to "reading")) { TeamSample("kit_merge_queue") } }
}
