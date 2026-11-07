package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.status.KitAnswerScale
import io.github.eslamasabry.oc.ui.kit.status.KitAnswerTone
import io.github.eslamasabry.oc.ui.kit.status.KitStatusAnswer
import kotlin.test.Test

/** test/goldens/kit/kit_status_answer_golden_test.dart: `_part` (four tones) and `_calm` (calm and compact). */
class KitStatusAnswerGateTest {
    @Composable private fun part() {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KitStatusAnswer("Everything is running", tone = KitAnswerTone.Ok, line = "Uses about 410 MB of memory")
            Spacer(Modifier.height(32.dp))
            KitStatusAnswer(
                "Android closed the server",
                tone = KitAnswerTone.Paused,
                line = "It restarts by itself in about 40 s",
                action = KitAction("Use this phone's server", {}),
            )
            Spacer(Modifier.height(32.dp))
            KitStatusAnswer("The server didn't start", tone = KitAnswerTone.NeedsYou)
            Spacer(Modifier.height(32.dp))
            KitStatusAnswer("The server is off", tone = KitAnswerTone.Off)
        }
    }
    @Composable private fun calm() {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KitStatusAnswer("No conversations yet", scale = KitAnswerScale.Calm, line = "Say what you want to build, and an agent gets to work.")
            Spacer(Modifier.height(32.dp))
            KitStatusAnswer("Nothing matches “deploy staging”", scale = KitAnswerScale.Compact)
        }
    }
    @Test fun kit_status_answer_calm() { gate23("kit_status_answer_calm") { calm() } }
    @Test fun kit_status_answer_default() { gate23("kit_status_answer_default") { part() } }
    @Test fun kit_status_answer_default_1280x800() { gate23("kit_status_answer_default_1280x800") { part() } }
    @Test fun kit_status_answer_default_text2() { gate23("kit_status_answer_default_text2", "dark") { part() } }
    @Test fun kit_status_answer_default_text2_1280x800() { gate23("kit_status_answer_default_text2_1280x800", "dark") { part() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_status_answer", caps = mapOf("KitStatusAnswer" to "conversation")) { part() } }
}
