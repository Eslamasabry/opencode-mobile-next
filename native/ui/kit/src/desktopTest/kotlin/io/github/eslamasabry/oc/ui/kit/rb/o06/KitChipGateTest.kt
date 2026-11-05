package io.github.eslamasabry.oc.ui.kit.rb.o06

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.*
import io.github.eslamasabry.oc.ui.design.type.*
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.kit.icon.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.chip.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import kotlin.test.Test

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.layout.Layout

class KitChipGateTest {
    private val longLabel = "A genuinely long label that will never fit inside this short chip"
    @Composable private fun chips(state: String) {
        if (state == "selected") {
            KitChip("Ask first", kind = KitChipKind.Action, selected = true, onClick = {})
            KitChip("Ask first", kind = KitChipKind.Action, selected = false, onClick = {})
        } else if (state == "expanded") {
            KitChip("Read 3 files · edited 1", kind = KitChipKind.Summary, expanded = true, onClick = {})
            KitChip("Read 3 files · edited 1", kind = KitChipKind.Summary, expanded = false, onClick = {})
        } else {
            val label = if (state == "truncated") longLabel else null
            val cap = if (state == "truncated") Modifier.width(160.dp) else Modifier
            KitChip(label ?: "main", cap)
            KitChip(label ?: "Open in terminal", cap, kind = KitChipKind.Action, onClick = {})
            KitChip(label ?: "file.txt", cap, kind = KitChipKind.Removable, onRemove = {})
            KitChip(label ?: "Tasks", cap, kind = KitChipKind.Count, count = if (state == "truncated") 42 else 3)
            KitChip(label ?: "Read 3 files · edited 1", cap, kind = KitChipKind.Summary, onClick = {})
        }
    }
    @Composable private fun scene(state: String = "default") {
        val c = LocalKitColors.current
        Column(Modifier.fillMaxWidth()) {
            for (fill in listOf(c.ground, c.surface1)) Box(Modifier.fillMaxWidth().background(fill).padding(16.dp)) { KitChipWrap { chips(state) } }
        }
    }
    @Test fun kit_chip_default() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_chip_default", modes = listOf("dark", "light")) { scene("default") } }
    @Test fun kit_chip_default_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_chip_default_1280x800", modes = listOf("dark", "light")) { scene("default") } }
    @Test fun kit_chip_default_text2() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_chip_default_text2", modes = listOf("dark", "light")) { scene("default") } }
    @Test fun kit_chip_default_text2_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_chip_default_text2_1280x800", modes = listOf("dark", "light")) { scene("default") } }
    @Test fun kit_chip_expanded() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_chip_expanded", modes = listOf("dark", "light")) { scene("expanded") } }
    @Test fun kit_chip_focused() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_chip_focused", frame = FlutterGoldenGate.Frame.Screen, act = { onNodeWithTag("kit-chip-remove").performSemanticsAction(SemanticsActions.RequestFocus) { it() } }, modes = listOf("dark", "light")) { FlutterCenter { KitChip("file.txt", kind = KitChipKind.Removable, onRemove = {}) } } }
    @Test fun kit_chip_selected() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_chip_selected", modes = listOf("dark", "light")) { scene("selected") } }
    @Test fun kit_chip_truncated() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_chip_truncated", modes = listOf("dark", "light")) { scene("truncated") } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_chip", caps = mapOf("KitChip" to "chip", "KitChipWrap" to "reading")) { KitChipWrap { KitChip("A long label that exceeds the chip cap ".repeat(20), kind = KitChipKind.Action, onClick = {}) } } }
}

/** Flutter Center retains fractional physical-pixel offsets (the focused fixture's odd-height window). */
@Composable
private fun FlutterCenter(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val child = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val x = (constraints.maxWidth - child.width) / 2f
        val y = (constraints.maxHeight - child.height) / 2f
        layout(constraints.maxWidth, constraints.maxHeight) {
            child.placeWithLayer(0, 0) { translationX = x; translationY = y }
        }
    }
}
