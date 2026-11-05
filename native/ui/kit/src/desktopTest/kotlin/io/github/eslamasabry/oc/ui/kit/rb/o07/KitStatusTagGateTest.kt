package io.github.eslamasabry.oc.ui.kit.rb.o07

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.status.KitStatusTag
import io.github.eslamasabry.oc.ui.kit.status.KitStatusTagTone
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/** The `default` scene of test/goldens/kit/kit_status_tag_golden_test.dart: three tags in a centred row, 12 dp apart. */
class KitStatusTagGateTest {
    @Test fun kit_status_tag_default() {
        FlutterGoldenGate.check(wrap = { FlutterCapture(it) }, name = "kit_status_tag_default", modes = listOf("dark", "light")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
                KitStatusTag("Needs you", KitStatusTagTone.NeedsYou)
                KitStatusTag("Running", KitStatusTagTone.Running)
                KitStatusTag("Done", KitStatusTagTone.Done)
            }
        }
    }
}
