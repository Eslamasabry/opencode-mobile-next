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

@OptIn(ExperimentalLayoutApi::class)
class KitFilterChipGateTest {
    @Composable private fun scene() {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KitFilterChip("Running", false, {})
            KitFilterChip("Running", true, {})
            KitFilterChip("Needs you · 1", true, {}, needsYou = true)
        }
    }
    @Test fun kit_filter_chip_default() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_filter_chip_default", modes = listOf("dark", "light")) { scene() } }
    @Test fun kit_filter_chip_default_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_filter_chip_default_1280x800", modes = listOf("dark", "light")) { scene() } }
    @Test fun kit_filter_chip_default_text2() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_filter_chip_default_text2", modes = listOf("dark")) { scene() } }
    @Test fun kit_filter_chip_default_text2_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_filter_chip_default_text2_1280x800", modes = listOf("dark")) { scene() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_filter_chip", caps = mapOf("KitFilterChip" to "chip")) { scene() } }
}
