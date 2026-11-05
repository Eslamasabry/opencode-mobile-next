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
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetTone
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test

/** test/goldens/kit/kit_sheet_golden_test.dart: the Language sheet over an empty page. */
class KitSheetGateTest {
    private val rows = listOf(
        Triple(KitIcons.check, "English", "The phone’s language"),
        Triple(KitIcons.info, "العربية", "Arabic"),
        Triple(KitIcons.info, "Deutsch", "German"),
    )

    @Composable
    private fun Language(
        height: KitSheetHeight = KitSheetHeight.Content,
        icon: ImageVector? = null,
        tone: KitSheetTone = KitSheetTone.Neutral,
        dismissible: Boolean = true,
        dirty: Boolean = false,
        loading: Boolean = false,
    ) {
        KitSheetHost {
            KitSheet(
                visible = true,
                onDismissRequest = {},
                title = "Language",
                subtitle = "Words across the app",
                height = height,
                icon = icon,
                tone = tone,
                dismissible = dismissible,
                dirty = dirty,
                loading = loading,
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

    /** kit_sheet_r2_golden_test.dart `_folders`: four rows with a folder glyph, no supporting line. */
    @Composable
    private fun Folder() {
        KitSheetHost {
            KitSheet(
                visible = true,
                onDismissRequest = {},
                title = "Project folder",
                subtitle = "Where the agent works",
                icon = KitIcons.folderOpen,
                primary = KitAction("Use oc_app", onClick = {}),
                secondary = KitAction("Cancel", onClick = {}),
                secondaryDismisses = true,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    listOf("oc_app", "notes", "site", "scripts").forEach {
                        KitRow(it, leading = KitRowLeading.Glyph(KitIcons.folderOpen), onClick = {})
                    }
                }
            }
        }
    }

    @Test fun kit_sheet_attention() { FlutterGoldenGate.check("kit_sheet_attention", Frame.Screen) { Language(icon = KitIcons.globe, tone = KitSheetTone.Attention) } }
    @Test fun kit_sheet_loading() { FlutterGoldenGate.check("kit_sheet_loading", Frame.Screen) { Language(loading = true) } }
    @Test fun kit_sheet_not_dismissible() { FlutterGoldenGate.check("kit_sheet_not_dismissible", Frame.Screen) { Language(dismissible = false) } }
    @Test fun kit_sheet_discard() {
        FlutterGoldenGate.check("kit_sheet_discard", Frame.Screen, act = { onNodeWithContentDescription("Close").performClick() }) { Language(dirty = true) }
    }
    @Test fun kit_sheet_with_icon_text2() { FlutterGoldenGate.check("kit_sheet_with_icon_text2", Frame.Screen) { Language(icon = KitIcons.globe) } }
    @Test fun kit_sheet_with_icon_text2_1280x800() { FlutterGoldenGate.check("kit_sheet_with_icon_text2_1280x800", Frame.Screen) { Language(icon = KitIcons.globe) } }
    @Test fun kit_sheet_oneclose() { FlutterGoldenGate.check("kit_sheet_oneclose", Frame.Screen) { Folder() } }
    @Test fun kit_sheet_oneclose_1280x800() { FlutterGoldenGate.check("kit_sheet_oneclose_1280x800", Frame.Screen) { Folder() } }
    @Test fun kit_sheet_text250() { FlutterGoldenGate.check("kit_sheet_text250", Frame.Screen, textScale = 2.5f) { Folder() } }
    @Test fun kit_sheet_text250_1280x800() { FlutterGoldenGate.check("kit_sheet_text250_1280x800", Frame.Screen, textScale = 2.5f) { Folder() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_sheet", caps = mapOf("KitSheet" to "sheet"), frame = Frame.Screen) { Language(icon = KitIcons.globe) } }
}
