package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.team.KitSpecBlock
import kotlin.test.Test

/** test/goldens/kit/kit_spec_block_golden_test.dart: "Goal", editable and empty, then read-only with its words. */
class KitSpecBlockGateTest {
    @Composable private fun block(text: String, readOnly: Boolean) {
        KitSpecBlock("Goal", rememberTextFieldState(text), Modifier.padding(16.dp), readOnly = readOnly)
    }
    @Test fun kit_spec_block_empty() { gate23("kit_spec_block_empty") { block("", false) } }
    @Test fun kit_spec_block_disabled() { gate23("kit_spec_block_disabled") { block("Keep drafts across restarts.", true) } }
    @Test fun kit_spec_block_text2() { gate23("kit_spec_block_text2") { block("Keep drafts across restarts.", true) } }
    @Test fun kit_spec_block_text2_1280x800() { gate23("kit_spec_block_text2_1280x800") { block("Keep drafts across restarts.", true) } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_spec_block", caps = mapOf("KitSpecBlock" to "reading")) { block("Keep drafts across restarts.", true) } }
}
