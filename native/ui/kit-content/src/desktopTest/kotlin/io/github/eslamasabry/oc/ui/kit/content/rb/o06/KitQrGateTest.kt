package io.github.eslamasabry.oc.ui.kit.content.rb.o06

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

import io.github.eslamasabry.oc.ui.kit.content.qr.KitQr
class KitQrGateTest {
    @Composable private fun scene(long: Boolean = false) {
        Column(Modifier.fillMaxWidth().background(LocalKitColors.current.surface2).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KitQr(if (long) "x".repeat(4000) else "opencode://s/shopfront/9c2f", "QR code to open this conversation on your phone")
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                KitText("opencode://s/shopfront/9c2f", Modifier.weight(1f, fill = false), role = TypeRole.Mono, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                KitIconButton(KitIcons.copy, "Copy link", {})
            }
        }
    }
    @Test fun kit_qr_default() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_qr_default", modes = listOf("dark", "light")) { scene(long = false) } }
    @Test fun kit_qr_default_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_qr_default_1280x800", modes = listOf("dark", "light")) { scene(long = false) } }
    @Test fun kit_qr_too_long() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_qr_too_long", modes = listOf("dark", "light")) { scene(long = true) } }
    @Test fun kit_qr_too_long_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_qr_too_long_1280x800", modes = listOf("dark", "light")) { scene(long = true) } }
    @Test fun kit_qr_too_long_text2() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_qr_too_long_text2", modes = listOf("dark")) { scene(long = true) } }
    @Test fun kit_qr_too_long_text2_1280x800() { FlutterGoldenGate.check(wrap = { content -> FlutterCapture(content) }, name = "kit_qr_too_long_text2_1280x800", modes = listOf("dark")) { scene(long = true) } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_qr", caps = mapOf("KitQr" to "control")) { KitQr("opencode://s/shopfront/9c2f", "QR code to open this conversation on your phone") } }
}
