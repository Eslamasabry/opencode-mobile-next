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

class KitSectionLabelGateTest {
    @Composable private fun row(title: String, supporting: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        KitRow(title, supporting = supporting, leading = KitRowLeading.Glyph(icon), trailing = KitRowTrailing.Chevron, onClick = {})
    }
    @Composable private fun scene() {
        Column(Modifier.fillMaxWidth()) {
            KitRowGroup(label = "Planner") { row("Model", "Opus 5.5 · plans before it edits", KitIcons.agent) }
            KitSectionLabel("MCP servers", explanation = "Tools the agent can call, served by programs this server runs.", trailing = { KitText("3 connected", role = TypeRole.Caption, tone = TextTone.Secondary) })
            KitRowGroup { row("GitHub", "Connected · 12 tools", KitIcons.link); row("Browser", "Connected · 8 tools", KitIcons.terminal) }
            KitRowGroup(label = "Supervision") { row("Ask before", "Edits outside the project", KitIcons.chat) }
        }
    }
    @Test fun kit_section_label_page() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_section_label_page", modes = listOf("dark", "light")) { scene() } }
    @Test fun kit_section_label_page_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_section_label_page_1280x800", modes = listOf("dark", "light")) { scene() } }
    @Test fun kit_section_label_page_text2() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_section_label_page_text2", modes = listOf("dark", "light")) { scene() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_section_label", caps = mapOf("KitSectionLabel" to "list")) { KitSectionLabel("MCP servers", trailing = { KitText("3 connected", role = TypeRole.Caption) }) } }
}
