package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.state.KitAnswerScale
import io.github.eslamasabry.oc.ui.kit.state.KitAnswerTone
import io.github.eslamasabry.oc.ui.kit.state.KitStatusAnswer
import kotlin.test.Test

/**
 * test/goldens/kit/kit_status_answer_golden_test.dart: `_part` (ok, paused with an action, needs you, off; 412x915 and
 * 1280x800, light and dark; 2.0 text dark only) and `_calm` (the calm and compact scales with no mark; 412x915, light and dark).
 */
class KitStatusAnswerGateTest {
    private val part: @Composable () -> Unit = {
        Column(Modifier.padding(24.dp)) {
            KitStatusAnswer("Everything is running", tone = KitAnswerTone.Ok, line = "Uses about 410 MB of memory")
            Spacer(Modifier.height(32.dp))
            KitStatusAnswer(
                "Android closed the server",
                tone = KitAnswerTone.Paused,
                line = "It restarts by itself in about 40 s",
                action = KitAction("Use this phone's server", onClick = {}),
            )
            Spacer(Modifier.height(32.dp))
            KitStatusAnswer("The server didn't start", tone = KitAnswerTone.NeedsYou)
            Spacer(Modifier.height(32.dp))
            KitStatusAnswer("The server is off", tone = KitAnswerTone.Off)
        }
    }

    private val calm: @Composable () -> Unit = {
        Column(Modifier.padding(24.dp)) {
            KitStatusAnswer(
                "No conversations yet",
                scale = KitAnswerScale.Calm,
                line = "Say what you want to build, and an agent gets to work.",
            )
            Spacer(Modifier.height(32.dp))
            KitStatusAnswer("Nothing matches “deploy staging”", scale = KitAnswerScale.Compact)
        }
    }

    @Test fun kit_status_answer_calm() { FlutterGoldenGate.check("kit_status_answer_calm", content = calm) }
    @Test fun kit_status_answer_default() { FlutterGoldenGate.check("kit_status_answer_default", content = part) }
    @Test fun kit_status_answer_default_1280x800() { FlutterGoldenGate.check("kit_status_answer_default_1280x800", content = part) }
    @Test fun kit_status_answer_default_text2() { FlutterGoldenGate.check("kit_status_answer_default_text2", modes = listOf("dark"), content = part) }
    @Test fun kit_status_answer_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_status_answer_default_text2_1280x800", modes = listOf("dark"), content = part)
    }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_status_answer", caps = mapOf("KitStatusAnswer" to "conversation"), content = part) }
}
