package io.github.eslamasabry.oc.ui.kit.rb.o11

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import kotlin.test.Test

/** test/goldens/kit/kit_sheet_golden_test.dart: the Language sheet over an empty page. */
class KitSheetGateTest {
    private val rows = listOf(
        Triple(KitIcons.check, "English", "The phone’s language"),
        Triple(KitIcons.info, "العربية", "Arabic"),
        Triple(KitIcons.info, "Deutsch", "German"),
    )

    @Composable
    private fun Language(height: KitSheetHeight = KitSheetHeight.Content, icon: ImageVector? = null) {
        KitSheetHost {
            KitSheet(
                visible = true,
                onDismissRequest = {},
                title = "Language",
                subtitle = "Words across the app",
                height = height,
                icon = icon,
                primary = KitAction("Use English", onClick = {}),
                secondary = KitAction("Keep current", onClick = {}),
                tertiary = listOf(KitAction("More languages", onClick = {})),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    rows.forEach { (i, t, s) ->
                        KitRow(t, Modifier.padding(vertical = 8.dp), supporting = s, leading = KitRowLeading.Glyph(i), onClick = {})
                    }
                }
            }
        }
    }

    @Test fun kit_sheet_default() { FlutterGoldenGate.check("kit_sheet_default", Frame.Screen) { Language() } }
    @Test fun kit_sheet_with_icon() { FlutterGoldenGate.check("kit_sheet_with_icon", Frame.Screen) { Language(icon = KitIcons.globe) } }
    @Test fun kit_sheet_with_icon_1280x800() { FlutterGoldenGate.check("kit_sheet_with_icon_1280x800", Frame.Screen) { Language(icon = KitIcons.globe) } }
    @Test fun kit_sheet_half() { FlutterGoldenGate.check("kit_sheet_half", Frame.Screen) { Language(KitSheetHeight.Half) } }
    @Test fun kit_sheet_full_1280x800() { FlutterGoldenGate.check("kit_sheet_full_1280x800", Frame.Screen) { Language(KitSheetHeight.Full) } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_sheet", caps = mapOf("KitSheet" to "sheet"), frame = Frame.Screen) { Language(icon = KitIcons.globe) } }
}
