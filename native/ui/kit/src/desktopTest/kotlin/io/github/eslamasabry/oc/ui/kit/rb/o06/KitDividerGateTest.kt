package io.github.eslamasabry.oc.ui.kit.rb.o06

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.*
import io.github.eslamasabry.oc.ui.design.type.*
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.theme.*
import kotlin.test.Test
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton

class KitDividerGateTest {
    @Composable private fun scene(ar: Boolean = false, vertical: Boolean = false) {
        val c = LocalKitColors.current
        val t = LocalKitTokens.current
        if (vertical) {
            Row(Modifier.fillMaxWidth().background(c.surface1).padding(horizontal = Space.S4.dp, vertical = Space.S2.dp).height(t.minTarget.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                KitIconButton(KitIcons.terminal, "Open terminal", {})
                KitIconButton(KitIcons.check, "Mark done", {})
                Spacer(Modifier.width(Space.S2.dp))
                KitDivider(Modifier.padding(vertical = Space.S3.dp), vertical = true)
                Spacer(Modifier.width(Space.S2.dp))
                KitIconButton(KitIcons.info, "Details", {})
                KitIconButton(KitIcons.copy, "Copy", {})
            }
        } else {
            Column(Modifier.fillMaxWidth().background(c.surface1)) {
                KitRow(if (ar) "بلا فاصل" else "Section header")
                KitDivider()
                KitRow(if (ar) "بلا أيقونة بادئة" else "No leading icon")
                KitDivider(inset = KitDividerInset.Gutter)
                KitRow(if (ar) "بأيقونة بادئة" else "With a leading icon", leading = KitRowLeading.Glyph(KitIcons.terminal))
                KitDivider(inset = KitDividerInset.Text)
                KitRow(if (ar) "الصف الأخير" else "Last row", leading = KitRowLeading.Glyph(KitIcons.terminal))
            }
        }
    }
    @Test fun kit_divider_insets() { FlutterGoldenGate.check("kit_divider_insets", modes = listOf("dark", "light")) { scene(ar = false, vertical = false) } }
    @Test fun kit_divider_insets_1280x800() { FlutterGoldenGate.check("kit_divider_insets_1280x800", modes = listOf("dark", "light")) { scene(ar = false, vertical = false) } }
    @Test fun kit_divider_insets_1600x1000() { FlutterGoldenGate.check("kit_divider_insets_1600x1000", modes = listOf("dark", "light")) { scene(ar = false, vertical = false) } }
    @Test fun kit_divider_insets_360x800() { FlutterGoldenGate.check("kit_divider_insets_360x800", modes = listOf("dark", "light")) { scene(ar = false, vertical = false) } }
    @Test fun kit_divider_insets_800x1280() { FlutterGoldenGate.check("kit_divider_insets_800x1280", modes = listOf("dark", "light")) { scene(ar = false, vertical = false) } }
    @Test fun kit_divider_insets_915x412() { FlutterGoldenGate.check("kit_divider_insets_915x412", modes = listOf("dark", "light")) { scene(ar = false, vertical = false) } }
    @Test fun kit_divider_insets_ar() { FlutterGoldenGate.check("kit_divider_insets_ar", modes = listOf("dark", "light")) { scene(ar = true, vertical = false) } }
    @Test fun kit_divider_insets_ar_1280x800() { FlutterGoldenGate.check("kit_divider_insets_ar_1280x800", modes = listOf("dark", "light")) { scene(ar = true, vertical = false) } }
    @Test fun kit_divider_insets_text2() { FlutterGoldenGate.check("kit_divider_insets_text2", modes = listOf("dark", "light")) { scene(ar = false, vertical = false) } }
    @Test fun kit_divider_insets_text2_1280x800() { FlutterGoldenGate.check("kit_divider_insets_text2_1280x800", modes = listOf("dark", "light")) { scene(ar = false, vertical = false) } }
    @Test fun kit_divider_vertical() { FlutterGoldenGate.check("kit_divider_vertical", modes = listOf("dark", "light")) { scene(ar = false, vertical = true) } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_divider", caps = mapOf("KitDivider" to "fill")) { scene() } }
}
