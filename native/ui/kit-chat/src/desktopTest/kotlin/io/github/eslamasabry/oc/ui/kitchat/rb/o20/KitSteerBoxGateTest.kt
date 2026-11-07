package io.github.eslamasabry.oc.ui.kitchat.rb.o20

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.composer.KitSteerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitSteerBoxPhase
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import kotlin.test.Test

/** test/goldens/kit/kit_steer_box_golden_test.dart `_box`: five states at 412x915 and 1280x800, light and dark, and 2.0 text (dark). */
class KitSteerBoxGateTest {
    @Composable
    private fun SteerBox(text: String, phase: KitSteerBoxPhase = KitSteerBoxPhase.Ready, blockedReason: String? = null, message: String? = null) {
        val state = remember(text) { TextFieldState(text) }
        Box(Modifier.padding(16.dp)) {
            KitSteerBox(
                title = "Add to this turn", state = state, hint = "Write a message", sendLabel = "Send", phase = phase,
                onSend = {}, onDictate = {}, onClose = {}, message = message, blockedReason = blockedReason,
            )
        }
    }

    @Composable private fun Default() { SteerBox("Also add a test for the empty case", KitSteerBoxPhase.Ready, null, null) }
    @Composable private fun Working() { SteerBox("Also add a test", KitSteerBoxPhase.Sending, null, null) }
    @Composable private fun Disabled() { SteerBox("A draft that stays", KitSteerBoxPhase.Ready, "Waiting for conversation details…", null) }
    @Composable private fun Answered() { SteerBox("Done", KitSteerBoxPhase.Sent, null, null) }
    @Composable private fun Note() { SteerBox("Keep me", KitSteerBoxPhase.Ready, null, "The conversation changed. Your message is still here.") }

    @Test fun kit_steer_box_default() { FlutterGoldenGate.check("kit_steer_box_default", wrap = { FlutterCapture(it) }) { Default() } }
    @Test fun kit_steer_box_default_1280x800() { FlutterGoldenGate.check("kit_steer_box_default_1280x800", wrap = { FlutterCapture(it) }) { Default() } }
    @Test fun kit_steer_box_default_text2() { FlutterGoldenGate.check("kit_steer_box_default_text2", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Default() } }
    @Test fun kit_steer_box_default_text2_1280x800() { FlutterGoldenGate.check("kit_steer_box_default_text2_1280x800", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Default() } }
    @Test fun kit_steer_box_working() { FlutterGoldenGate.check("kit_steer_box_working", wrap = { FlutterCapture(it) }) { Working() } }
    @Test fun kit_steer_box_working_1280x800() { FlutterGoldenGate.check("kit_steer_box_working_1280x800", wrap = { FlutterCapture(it) }) { Working() } }
    @Test fun kit_steer_box_working_text2() { FlutterGoldenGate.check("kit_steer_box_working_text2", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Working() } }
    @Test fun kit_steer_box_working_text2_1280x800() { FlutterGoldenGate.check("kit_steer_box_working_text2_1280x800", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Working() } }
    @Test fun kit_steer_box_disabled() { FlutterGoldenGate.check("kit_steer_box_disabled", wrap = { FlutterCapture(it) }) { Disabled() } }
    @Test fun kit_steer_box_disabled_1280x800() { FlutterGoldenGate.check("kit_steer_box_disabled_1280x800", wrap = { FlutterCapture(it) }) { Disabled() } }
    @Test fun kit_steer_box_disabled_text2() { FlutterGoldenGate.check("kit_steer_box_disabled_text2", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Disabled() } }
    @Test fun kit_steer_box_disabled_text2_1280x800() { FlutterGoldenGate.check("kit_steer_box_disabled_text2_1280x800", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Disabled() } }
    @Test fun kit_steer_box_answered() { FlutterGoldenGate.check("kit_steer_box_answered", wrap = { FlutterCapture(it) }) { Answered() } }
    @Test fun kit_steer_box_answered_1280x800() { FlutterGoldenGate.check("kit_steer_box_answered_1280x800", wrap = { FlutterCapture(it) }) { Answered() } }
    @Test fun kit_steer_box_answered_text2() { FlutterGoldenGate.check("kit_steer_box_answered_text2", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Answered() } }
    @Test fun kit_steer_box_answered_text2_1280x800() { FlutterGoldenGate.check("kit_steer_box_answered_text2_1280x800", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Answered() } }
    @Test fun kit_steer_box_note() { FlutterGoldenGate.check("kit_steer_box_note", wrap = { FlutterCapture(it) }) { Note() } }
    @Test fun kit_steer_box_note_1280x800() { FlutterGoldenGate.check("kit_steer_box_note_1280x800", wrap = { FlutterCapture(it) }) { Note() } }
    @Test fun kit_steer_box_note_text2() { FlutterGoldenGate.check("kit_steer_box_note_text2", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Note() } }
    @Test fun kit_steer_box_note_text2_1280x800() { FlutterGoldenGate.check("kit_steer_box_note_text2_1280x800", wrap = { FlutterCapture(it) }, modes = listOf("dark")) { Note() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_steer_box", caps = mapOf("KitSteerBox" to "conversation")) { Default() } }
}
