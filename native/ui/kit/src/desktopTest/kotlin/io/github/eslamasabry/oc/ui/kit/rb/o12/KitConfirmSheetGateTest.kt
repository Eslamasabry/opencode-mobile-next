package io.github.eslamasabry.oc.ui.kit.rb.o12

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import kotlin.test.Test

/** test/goldens/kit/kit_confirm_sheet_golden_test.dart `_destructive`: delete with consequences, typed name, safer path. */
class KitConfirmSheetGateTest {
    @Composable
    private fun Destructive(arabic: Boolean = false) {
        KitConfirm(
            spec = KitConfirmSpec(
                title = if (arabic) "حذف مساحة العمل fox؟" else "Delete workspace fox?",
                body = if (arabic) "تُحذف ملفاتها وفرعها من الخادم. لا يمكن التراجع عن ذلك."
                else "Its files and branch are removed from the server. This can’t be undone.",
                confirmLabel = if (arabic) "حذف مساحة العمل" else "Delete workspace",
                kind = KitConfirmKind.Destructive,
                consequences = listOf(if (arabic) "ستُحذف 3 رسائل في قائمة الانتظار" else "3 queued prompts will be deleted"),
                typedName = "fox",
                alternative = KitAction(if (arabic) "تصدير أولًا" else "Export first", onClick = {}),
                details = listOf(KitTechnicalValue("Path", "/home/dev/code/.worktrees/fox")),
            ),
            onConfirm = {},
            onCancel = {},
        )
    }

    @Test fun kit_confirm_destructive() { FlutterGoldenGate.check("kit_confirm_destructive", Frame.Screen) { Destructive() } }
    @Test fun kit_confirm_destructive_360x800() { FlutterGoldenGate.check("kit_confirm_destructive_360x800", Frame.Screen) { Destructive() } }
    @Test fun kit_confirm_destructive_800x1280() { FlutterGoldenGate.check("kit_confirm_destructive_800x1280", Frame.Screen) { Destructive() } }
    @Test fun kit_confirm_destructive_1280x800() { FlutterGoldenGate.check("kit_confirm_destructive_1280x800", Frame.Screen) { Destructive() } }
    @Test fun kit_confirm_destructive_1600x1000() { FlutterGoldenGate.check("kit_confirm_destructive_1600x1000", Frame.Screen) { Destructive() } }
    @Test fun kit_confirm_destructive_text2() { FlutterGoldenGate.check("kit_confirm_destructive_text2", Frame.Screen, textScale = 2f) { Destructive() } }
    @Test fun kit_confirm_destructive_text2_1280x800() { FlutterGoldenGate.check("kit_confirm_destructive_text2_1280x800", Frame.Screen, textScale = 2f) { Destructive() } }
    @Test fun kit_confirm_destructive_ar() { FlutterGoldenGate.check("kit_confirm_destructive_ar", Frame.Screen, textScale = 1.3f) { Destructive(true) } }
    @Test fun kit_confirm_destructive_ar_1280x800() { FlutterGoldenGate.check("kit_confirm_destructive_ar_1280x800", Frame.Screen, textScale = 1.3f) { Destructive(true) } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_confirm_sheet", caps = mapOf("KitConfirm" to "sheet"), frame = Frame.Screen) { Destructive() } }
}
