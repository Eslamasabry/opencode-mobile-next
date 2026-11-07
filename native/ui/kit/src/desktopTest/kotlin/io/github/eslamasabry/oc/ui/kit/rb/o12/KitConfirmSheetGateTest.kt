package io.github.eslamasabry.oc.ui.kit.rb.o12

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import kotlin.test.Test

/**
 * test/goldens/kit/kit_confirm_sheet_golden_test.dart (`_destructive`, neutral, stop working, failed, discard, typed
 * name ready) and test/goldens/kit/kit_sheet_r2_golden_test.dart (`_confirm`: the alternative between the act and
 * Cancel with neutral facts). Each opens the confirmation over an empty page, as `kitGalleryShot` does.
 */
class KitConfirmSheetGateTest {
    @Composable
    private fun Open(spec: KitConfirmSpec, working: Boolean = false, failed: Boolean = false) {
        KitSheetHost { KitConfirm(spec = spec, onConfirm = {}, onCancel = {}, working = working, failed = failed) }
    }

    private fun destructive(arabic: Boolean = false) =
        KitConfirmSpec(
            title = if (arabic) "حذف مساحة العمل fox؟" else "Delete workspace fox?",
            body = if (arabic) "تُحذف ملفاتها وفرعها من الخادم. لا يمكن التراجع عن ذلك."
            else "Its files and branch are removed from the server. This can’t be undone.",
            confirmLabel = if (arabic) "حذف مساحة العمل" else "Delete workspace",
            kind = KitConfirmKind.Destructive,
            consequences = listOf(if (arabic) "ستُحذف 3 رسائل في قائمة الانتظار" else "3 queued prompts will be deleted"),
            typedName = "fox",
            alternative = KitAction(if (arabic) "تصدير أولًا" else "Export first", onClick = {}),
            details = listOf(KitTechnicalValue("Path", "/home/dev/code/.worktrees/fox")),
        )

    private val neutral =
        KitConfirmSpec(
            title = "Share conversation?",
            body = "Anyone with the link can read it. You can stop sharing later.",
            confirmLabel = "Share conversation",
        )

    private val stop =
        KitConfirmSpec(
            title = "Stop fox?",
            body = "It stops mid-task. Its work so far is kept.",
            confirmLabel = "Stop fox",
            kind = KitConfirmKind.Stop,
        )

    private val revoke =
        KitConfirmSpec(
            title = "Revoke saved permission?",
            body = "The agent asks again next time it edits files.",
            confirmLabel = "Revoke permission",
            kind = KitConfirmKind.Destructive,
        )

    private val discard =
        KitConfirmSpec(
            title = "Discard your changes?",
            body = "What you changed here isn’t saved. Discarding it can’t be undone.",
            confirmLabel = "Discard changes",
            kind = KitConfirmKind.Discard,
        )

    private val move =
        KitConfirmSpec(
            title = "Move fox?",
            body = "The conversation moves to the notes project.",
            confirmLabel = "Move fox",
            consequenceItems = listOf(
                KitConsequence("Its 3 changed files move with it", KitConsequenceMark.Neutral),
                KitConsequence("Its agent keeps its model", KitConsequenceMark.Neutral),
            ),
            alternative = KitAction("Copy fox instead", onClick = {}),
        )

    /** The Arabic shots open the folded Details first (`tester.tap(find.byKey('kit-details-toggle'))`). */
    private val openDetails: androidx.compose.ui.test.ComposeUiTest.() -> Unit = { onNodeWithTag("kit-details-toggle").performClick() }

    @Test fun kit_confirm_destructive() { FlutterGoldenGate.check("kit_confirm_destructive", Frame.Screen) { Open(destructive()) } }
    @Test fun kit_confirm_destructive_360x800() { FlutterGoldenGate.check("kit_confirm_destructive_360x800", Frame.Screen) { Open(destructive()) } }
    @Test fun kit_confirm_destructive_800x1280() { FlutterGoldenGate.check("kit_confirm_destructive_800x1280", Frame.Screen) { Open(destructive()) } }
    @Test fun kit_confirm_destructive_1280x800() { FlutterGoldenGate.check("kit_confirm_destructive_1280x800", Frame.Screen) { Open(destructive()) } }
    @Test fun kit_confirm_destructive_1600x1000() { FlutterGoldenGate.check("kit_confirm_destructive_1600x1000", Frame.Screen) { Open(destructive()) } }
    @Test fun kit_confirm_destructive_text2() { FlutterGoldenGate.check("kit_confirm_destructive_text2", Frame.Screen, textScale = 2f) { Open(destructive()) } }
    @Test fun kit_confirm_destructive_text2_1280x800() {
        FlutterGoldenGate.check("kit_confirm_destructive_text2_1280x800", Frame.Screen, textScale = 2f) { Open(destructive()) }
    }
    @Test fun kit_confirm_destructive_ar() {
        FlutterGoldenGate.check("kit_confirm_destructive_ar", Frame.Screen, textScale = 1.3f, act = openDetails) { Open(destructive(true)) }
    }
    @Test fun kit_confirm_destructive_ar_1280x800() {
        FlutterGoldenGate.check("kit_confirm_destructive_ar_1280x800", Frame.Screen, textScale = 1.3f, act = openDetails) { Open(destructive(true)) }
    }
    @Test fun kit_confirm_neutral() { FlutterGoldenGate.check("kit_confirm_neutral", Frame.Screen) { Open(neutral) } }
    @Test fun kit_confirm_stop_working() { FlutterGoldenGate.check("kit_confirm_stop_working", Frame.Screen) { Open(stop, working = true) } }
    @Test fun kit_confirm_failed() { FlutterGoldenGate.check("kit_confirm_failed", Frame.Screen) { Open(revoke, failed = true) } }
    @Test fun kit_confirm_discard() { FlutterGoldenGate.check("kit_confirm_discard", Frame.Screen) { Open(discard) } }
    @Test fun kit_confirm_typed_ready() {
        FlutterGoldenGate.check("kit_confirm_typed_ready", Frame.Screen, act = { onNode(hasSetTextAction()).performTextInput("fox") }) {
            Open(destructive())
        }
    }
    @Test fun kit_confirm_sheet_alternative() { FlutterGoldenGate.check("kit_confirm_sheet_alternative", Frame.Screen) { Open(move) } }
    @Test fun kit_confirm_sheet_alternative_1280x800() {
        FlutterGoldenGate.check("kit_confirm_sheet_alternative_1280x800", Frame.Screen) { Open(move) }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_confirm_sheet",
            caps = mapOf("KitConfirmSheet" to "confirm", "showKitConfirm" to "confirm"),
            frame = Frame.Screen,
        ) { Open(destructive()) }
    }
}
