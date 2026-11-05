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

class KitSearchFieldGateTest {
    @Composable private fun scene(stateName: String = "typing") {
        val query = when (stateName) { "nomatch" -> "opus"; "filtered" -> "main"; "empty", "disabled" -> ""; else -> "notif" }
        val label = when (stateName) { "nomatch" -> "Search models"; "filtered" -> "Search files"; "disabled" -> "Search sessions"; else -> "Search settings" }
        Column(Modifier.fillMaxSize()) {
            KitText("Settings", Modifier.padding(horizontal = 16.dp, vertical = 12.dp), role = TypeRole.Title)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { KitSearchField(label, {}, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), state = rememberTextFieldState(query),
                resultCount = when (stateName) { "nomatch" -> 0; "filtered" -> 3; "results", "partial" -> 12; else -> null },
                partial = stateName == "partial", activeFilter = if (stateName == "filtered") "Symbols" else null,
                removeFilterLabel = if (stateName == "filtered") "Remove Symbols" else null, onClearFilter = {},
                enabled = stateName != "disabled", disabledReason = if (stateName == "disabled") "Connect to a server to search its sessions" else null,
                filters = if (stateName in listOf("typing", "empty", "filtered")) listOf(KitMenuItem("Symbols", {}), KitMenuItem("Files", {})) else emptyList()) }
            Spacer(Modifier.height(LocalKitTokens.current.loadingBar.dp))
            if (stateName == "nomatch") {
                KitSearchNoMatch("opus", {}, Modifier.padding(horizontal = 16.dp), what = "models", action = KitAction("Search all projects", onClick = {}))
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    for (name in listOf("Appearance", "Notifications", "Voice", "Servers")) KitText(name, Modifier.padding(vertical = 12.dp), role = TypeRole.RowTitle)
                }
            }
        }
    }
    @Test fun kit_search_field_disabled() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_disabled", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("disabled") } }
    @Test fun kit_search_field_empty() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_empty", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("empty") } }
    @Test fun kit_search_field_filtered() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_filtered", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("filtered") } }
    @Test fun kit_search_field_nomatch() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_nomatch", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("nomatch") } }
    @Test fun kit_search_field_partial() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_partial", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("partial") } }
    @Test fun kit_search_field_results() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_results", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("results") } }
    @Test fun kit_search_field_typing() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_typing", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("typing") } }
    @Test fun kit_search_field_typing_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_typing_1280x800", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("typing") } }
    @Test fun kit_search_field_typing_text2() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_typing_text2", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("typing") } }
    @Test fun kit_search_field_typing_text2_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_search_field_typing_text2_1280x800", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark", "light")) { scene("typing") } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_search_field", caps = mapOf("KitSearchField" to "list", "KitSearchNoMatch" to "state")) { Column(Modifier.width(440.dp)) { KitSearchField("Search settings", {}, resultCount = 12, state = rememberTextFieldState("notif")); KitSearchNoMatch("opus", {}) } } }
}
