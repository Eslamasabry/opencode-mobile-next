package io.github.eslamasabry.oc.ui.kit.rb.o08

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionStack
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import kotlin.test.Test

/** The KitActionStack scenes of test/goldens/kit/kit_action_golden_test.dart (`_stackDefault`, `_stackDisabled`, `_stackDestructive`). */
class KitActionStackGateTest {
    private fun t(ar: Boolean, en: String, arabic: String) = if (ar) arabic else en

    private fun stackDefault(ar: Boolean = false): @Composable () -> Unit = {
        KitActionStack(
            primary = KitAction(t(ar, "Update", "تحديث"), {}),
            secondary = KitAction(t(ar, "Check now", "التحقق الآن"), {}),
            tertiary = listOf(KitAction(t(ar, "Restart", "إعادة التشغيل"), {})),
        )
    }

    private val stackDisabled: @Composable () -> Unit = {
        KitActionStack(
            secondary = KitAction("Check now", {}),
            tertiary = listOf(KitAction("Restart", null, enabled = KitEnabled.Disabled("Not connected right now."))),
        )
    }

    private val stackDestructive: @Composable () -> Unit = {
        KitActionStack(
            secondary = KitAction("Restart", {}),
            tertiary = listOf(KitAction("Stop server", {}, destructive = true)),
        )
    }

    @Test fun kit_action_stack_default() { FlutterGoldenGate.check("kit_action_stack_default", wrap = { FlutterCapture(it) }, content = stackDefault()) }
    @Test fun kit_action_stack_disabled() { FlutterGoldenGate.check("kit_action_stack_disabled", wrap = { FlutterCapture(it) }, content = stackDisabled) }
    @Test fun kit_action_stack_destructive() { FlutterGoldenGate.check("kit_action_stack_destructive", wrap = { FlutterCapture(it) }, content = stackDestructive) }
    @Test fun kit_action_stack_default_360x800() { FlutterGoldenGate.check("kit_action_stack_default_360x800", wrap = { FlutterCapture(it) }, content = stackDefault()) }
    @Test fun kit_action_stack_default_915x412() { FlutterGoldenGate.check("kit_action_stack_default_915x412", wrap = { FlutterCapture(it) }, content = stackDefault()) }
    @Test fun kit_action_stack_default_800x1280() { FlutterGoldenGate.check("kit_action_stack_default_800x1280", wrap = { FlutterCapture(it) }, content = stackDefault()) }
    @Test fun kit_action_stack_default_1280x800() { FlutterGoldenGate.check("kit_action_stack_default_1280x800", wrap = { FlutterCapture(it) }, content = stackDefault()) }
    @Test fun kit_action_stack_default_1600x1000() { FlutterGoldenGate.check("kit_action_stack_default_1600x1000", wrap = { FlutterCapture(it) }, content = stackDefault()) }
    @Test fun kit_action_stack_default_text2() { FlutterGoldenGate.check("kit_action_stack_default_text2", wrap = { FlutterCapture(it) }, content = stackDefault()) }
    @Test fun kit_action_stack_default_text2_1280x800() { FlutterGoldenGate.check("kit_action_stack_default_text2_1280x800", wrap = { FlutterCapture(it) }, content = stackDefault()) }
    @Test fun kit_action_stack_default_ar() { FlutterGoldenGate.check("kit_action_stack_default_ar", wrap = { FlutterCapture(it) }, content = stackDefault(ar = true)) }
    @Test fun kit_action_stack_default_ar_1280x800() { FlutterGoldenGate.check("kit_action_stack_default_ar_1280x800", wrap = { FlutterCapture(it) }, content = stackDefault(ar = true)) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_action_stack", caps = mapOf("KitActionStack" to "reading"), content = stackDefault()) }
}
