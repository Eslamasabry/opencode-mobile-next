package io.github.eslamasabry.oc.ui.kit.rb.o07

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.status.KitStatusTag
import io.github.eslamasabry.oc.ui.kit.status.KitStatusTagTone
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_status_tag_golden_test.dart: three tags in a centred row, 12 dp apart. */
class KitStatusTagGateTest {
    private val part: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            KitStatusTag("Needs you", KitStatusTagTone.NeedsYou)
            KitStatusTag("Running", KitStatusTagTone.Running)
            KitStatusTag("Done", KitStatusTagTone.Done)
        }
    }
    private val capture: @Composable (@Composable () -> Unit) -> Unit = { FlutterCapture(it) }

    @Test fun kit_status_tag_default() { FlutterGoldenGate.check("kit_status_tag_default", wrap = capture, content = part) }
    @Test fun kit_status_tag_default_1280x800() { FlutterGoldenGate.check("kit_status_tag_default_1280x800", wrap = capture, content = part) }
    @Test fun kit_status_tag_default_text2() {
        FlutterGoldenGate.check("kit_status_tag_default_text2", wrap = capture, modes = listOf("dark"), content = part)
    }
    @Test fun kit_status_tag_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_status_tag_default_text2_1280x800", wrap = capture, modes = listOf("dark"), content = part)
    }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_status_tag", caps = mapOf("KitStatusTag" to "chip"), content = part) }
}
