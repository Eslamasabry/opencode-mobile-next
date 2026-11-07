package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.team.KitSpecBlock
import kotlin.test.Test

/**
 * test/goldens/kit/kit_spec_block_golden_test.dart: KitSpecBlock(label: 'Goal') in 16 padding; `empty` editable with no
 * text, `disabled` read-only with 'Keep drafts across restarts.' (412x915, light and dark), and `text2` the read-only one
 * at 2.0 text (412x915 and 1280x800, light and dark).
 */
class KitSpecBlockGateTest {
    private fun part(text: String, readOnly: Boolean): @Composable () -> Unit = {
        KitSpecBlock("Goal", rememberTextFieldState(text), Modifier.padding(16.dp), readOnly = readOnly)
    }

    @Test fun kit_spec_block_empty() { FlutterGoldenGate.check("kit_spec_block_empty", content = part("", false)) }
    @Test fun kit_spec_block_disabled() {
        FlutterGoldenGate.check("kit_spec_block_disabled", content = part("Keep drafts across restarts.", true))
    }
    @Test fun kit_spec_block_text2() { FlutterGoldenGate.check("kit_spec_block_text2", content = part("Keep drafts across restarts.", true)) }
    @Test fun kit_spec_block_text2_1280x800() {
        FlutterGoldenGate.check("kit_spec_block_text2_1280x800", content = part("Keep drafts across restarts.", true))
    }
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_spec_block", caps = mapOf("KitSpecBlock" to "reading"), content = part("Keep drafts across restarts.", true))
    }
}
