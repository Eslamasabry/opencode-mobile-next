package io.github.eslamasabry.oc.ui.kit.rb.o12

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/** test/goldens/kit/kit_floating_action_golden_test.dart: the default "New conversation" button. */
class KitFloatingActionGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_floating_action", caps = mapOf("KitFab" to "frame")) {
            KitFab(label = "New conversation", icon = KitIcons.add, onClick = {})
        }
    }
}
