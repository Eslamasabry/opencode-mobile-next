package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import kotlin.test.Test

/** The default scene of test/goldens/kit/kit_row_golden_test.dart (`_defaultRows` in a group labelled "Conversations"). */
class KitRowGateTest {
    private val rows: @Composable () -> Unit = {
        KitRowGroup(label = "Conversations") {
            KitRow("Fix the login bug", supporting = "Finished 5h ago", leading = KitRowLeading.Glyph(KitIcons.chat), onClick = {})
            KitRow("Model", leading = KitRowLeading.Glyph(KitIcons.model), trailing = KitRowTrailing.Value("Claude Sonnet 4"), onClick = {})
            KitRow("Project settings", leading = KitRowLeading.Glyph(KitIcons.settings), trailing = KitRowTrailing.Chevron, onClick = {})
        }
    }

    @Test fun kit_row_default() { FlutterGoldenGate.check("kit_row_default", content = rows) }
    @Test fun kit_row_default_1280x800() { FlutterGoldenGate.check("kit_row_default_1280x800", content = rows) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_row", caps = mapOf("KitRow" to "list"), content = rows) }
}
