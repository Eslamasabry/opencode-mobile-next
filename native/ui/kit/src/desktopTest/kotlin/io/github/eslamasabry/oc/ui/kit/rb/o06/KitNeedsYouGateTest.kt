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

class KitNeedsYouGateTest {
    @Composable private fun scene(state: String) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            when (state) {
                "reasons" -> for (reason in KitNeedsYouReason.entries) Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    KitText(reason.name.lowercase(), Modifier.weight(1f), role = TypeRole.RowTitle)
                    Spacer(Modifier.width(12.dp))
                    KitNeedsYou(reason)
                }
                "row" -> {
                    KitNeedsYouRow("Run a shell command", KitNeedsYouReason.Decision, "The team waits; nothing is lost.", {}, who = "fox", server = "laptop")
                    KitDivider()
                    KitNeedsYouRow("Approve the migration that renames every profile-scoped storage key across the offline queue", KitNeedsYouReason.Blocked, "Drafts stay queued until this is answered.", {}, server = "laptop")
                    KitDivider()
                    KitNeedsYouRow("Allow microphone access", KitNeedsYouReason.Consent, "Voice input stays off.", {}, waiting = "4 min")
                }
                "marks" -> for ((title, count) in listOf("Run a shell command" to 1, "Approve the migration" to 2, "Rotate the API key" to 5)) {
                    KitRow(title, supporting = kitNeedsYouSpan(count).text, leading = KitRowLeading.Glyph(KitIcons.question))
                }
                "badges" -> for (count in listOf(0, 3, 120)) Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    KitNeedsYouBadge(count) { Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { KitIcon(KitIcons.activity) } }
                    Spacer(Modifier.width(32.dp))
                    KitNeedsYouBadge(count) { Box(Modifier.background(LocalKitColors.current.surface2, KitShapeId.Pill.shape()).padding(horizontal = 12.dp, vertical = 6.dp)) { KitText("laptop", role = TypeRole.Label) } }
                    Spacer(Modifier.width(32.dp))
                    Box(Modifier.weight(1f)) { KitRow("laptop", supporting = "Connected", leading = KitRowLeading.Glyph(KitIcons.server)) }
                }
            }
        }
    }
    @Test fun kit_needs_you_badges() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_needs_you_badges", modes = listOf("dark", "light")) { scene("badges") } }
    @Test fun kit_needs_you_badges_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_needs_you_badges_1280x800", modes = listOf("dark", "light")) { scene("badges") } }
    @Test fun kit_needs_you_marks() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_needs_you_marks", modes = listOf("dark", "light")) { scene("marks") } }
    @Test fun kit_needs_you_marks_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_needs_you_marks_1280x800", modes = listOf("dark", "light")) { scene("marks") } }
    @Test fun kit_needs_you_reasons() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_needs_you_reasons", modes = listOf("dark", "light")) { scene("reasons") } }
    @Test fun kit_needs_you_reasons_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_needs_you_reasons_1280x800", modes = listOf("dark", "light")) { scene("reasons") } }
    @Test fun kit_needs_you_row() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_needs_you_row", modes = listOf("dark", "light")) { scene("row") } }
    @Test fun kit_needs_you_row_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_needs_you_row_1280x800", modes = listOf("dark", "light")) { scene("row") } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_needs_you") { scene("reasons") } }
}
