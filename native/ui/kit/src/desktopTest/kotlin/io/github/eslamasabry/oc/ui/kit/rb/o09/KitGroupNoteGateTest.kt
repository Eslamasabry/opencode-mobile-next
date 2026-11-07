package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import kotlin.test.Test

/** test/goldens/kit/kit_group_note_golden_test.dart `_group`: one "Voice" row in "Conversations", the note under it. */
class KitGroupNoteGateTest {
    private val message = "2 settings aren't available on this server"

    private fun group(action: Boolean = true): @Composable () -> Unit = {
        Column {
            KitRowGroup(label = "Conversations") {
                KitRow("Voice", leading = KitRowLeading.Glyph(KitIcons.mic), trailing = KitRowTrailing.Chevron, onClick = {})
            }
            KitGroupNote(message, action = if (action) KitAction("Why", {}) else null)
        }
    }

    private fun shot(name: String, action: Boolean = true) {
        FlutterGoldenGate.check(name, wrap = { FlutterCapture(it) }, content = group(action))
    }

    @Test fun kit_group_note_text() { shot("kit_group_note_text", action = false) }
    @Test fun kit_group_note_default() { shot("kit_group_note_default") }
    @Test fun kit_group_note_default_360x800() { shot("kit_group_note_default_360x800") }
    @Test fun kit_group_note_default_915x412() { shot("kit_group_note_default_915x412") }
    @Test fun kit_group_note_default_800x1280() { shot("kit_group_note_default_800x1280") }
    @Test fun kit_group_note_default_1280x800() { shot("kit_group_note_default_1280x800") }
    @Test fun kit_group_note_default_1600x1000() { shot("kit_group_note_default_1600x1000") }
    @Test fun kit_group_note_default_text2() {
        FlutterGoldenGate.check("kit_group_note_default_text2", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = group())
    }
    @Test fun kit_group_note_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_group_note_default_text2_1280x800", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = group())
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_group_note", caps = mapOf("KitGroupNote" to "reading"), wrap = { FlutterCapture(it) }) {
            KitGroupNote(message, action = KitAction("Why", {}))
        }
    }
}
