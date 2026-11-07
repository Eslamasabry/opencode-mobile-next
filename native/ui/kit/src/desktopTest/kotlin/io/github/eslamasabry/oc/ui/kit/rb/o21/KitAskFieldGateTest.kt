package io.github.eslamasabry.oc.ui.kit.rb.o21

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.input.KitAskField
import io.github.eslamasabry.oc.ui.kit.input.KitAskFieldButton
import io.github.eslamasabry.oc.ui.kit.input.KitAskFieldPhase
import io.github.eslamasabry.oc.ui.kit.input.KitAskFieldRules
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** test/goldens/kit/kit_ask_field_golden_test.dart: `_part`, in a 16 dp padding. */
class KitAskFieldGateTest {
    @Composable
    private fun part(
        text: String = "",
        note: String? = null,
        prominent: Boolean = true,
        enabled: Boolean = true,
        phase: KitAskFieldPhase = KitAskFieldPhase.Ready,
    ) {
        KitAskField(
            state = rememberTextFieldState(text),
            hint = "Ask for a change…",
            sendLabel = "Send to the conversation",
            onSend = {},
            modifier = Modifier.padding(16.dp),
            phase = phase,
            onVoice = {},
            voiceLabel = "Speak your change",
            note = note,
            enabled = enabled,
            prominent = prominent,
        )
    }

    private fun shot(name: String, modes: List<String> = listOf("light", "dark"), content: @Composable () -> Unit) =
        FlutterGoldenGate.check(wrap = { FlutterCapture(it) }, name = name, modes = modes, content = content)

    @Test fun kit_ask_field_empty() {
        shot("kit_ask_field_empty") { part() }
        shot("kit_ask_field_empty_1280x800") { part() }
    }
    @Test fun kit_ask_field_empty_text2() {
        shot("kit_ask_field_empty_text2", listOf("dark")) { part() }
        shot("kit_ask_field_empty_text2_1280x800", listOf("dark")) { part() }
    }
    @Test fun kit_ask_field_typed() { shot("kit_ask_field_typed") { part(text = "Make the ball a little faster") } }
    @Test fun kit_ask_field_answered() { shot("kit_ask_field_answered") { part(note = "Sent to the conversation") } }
    @Test fun kit_ask_field_disabled() { shot("kit_ask_field_disabled") { part(enabled = false) } }
    @Test fun kit_ask_field_working() {
        shot("kit_ask_field_working") { part(text = "Make the ball a little faster", phase = KitAskFieldPhase.Working) }
    }
    @Test fun kit_ask_field_quiet() { shot("kit_ask_field_quiet") { part(prominent = false) } }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_ask_field", caps = mapOf("KitAskField" to "conversation")) { part() }
    }

    /** kit_ask_field.dart:160-164,368-413: which round button shows, when it is live, when Send goes. */
    @Test fun button_rules() {
        assertEquals(KitAskFieldButton.Mic, KitAskFieldRules.button(KitAskFieldPhase.Ready, hasText = false, hasVoice = true))
        assertEquals(KitAskFieldButton.Send, KitAskFieldRules.button(KitAskFieldPhase.Ready, hasText = false, hasVoice = false))
        assertEquals(KitAskFieldButton.Send, KitAskFieldRules.button(KitAskFieldPhase.Ready, hasText = true, hasVoice = true))
        assertEquals(KitAskFieldButton.Stop, KitAskFieldRules.button(KitAskFieldPhase.Listening, hasText = true, hasVoice = true))
        assertEquals(KitAskFieldButton.Send, KitAskFieldRules.button(KitAskFieldPhase.Working, hasText = false, hasVoice = true))
        assertFalse(KitAskFieldRules.live(KitAskFieldPhase.Ready, hasText = false, hasVoice = false, enabled = true))
        assertTrue(KitAskFieldRules.live(KitAskFieldPhase.Ready, hasText = false, hasVoice = true, enabled = true))
        assertFalse(KitAskFieldRules.live(KitAskFieldPhase.Working, hasText = true, hasVoice = true, enabled = true))
        assertFalse(KitAskFieldRules.live(KitAskFieldPhase.Ready, hasText = true, hasVoice = true, enabled = false))
        assertTrue(KitAskFieldRules.sends(KitAskFieldPhase.Ready, "Make it faster", enabled = true))
        assertFalse(KitAskFieldRules.sends(KitAskFieldPhase.Ready, "   ", enabled = true))
        assertFalse(KitAskFieldRules.sends(KitAskFieldPhase.Listening, "Make it faster", enabled = true))
    }
}
