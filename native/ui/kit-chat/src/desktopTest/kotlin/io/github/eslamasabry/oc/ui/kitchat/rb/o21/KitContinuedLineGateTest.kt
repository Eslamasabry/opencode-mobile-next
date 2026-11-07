package io.github.eslamasabry.oc.ui.kitchat.rb.o21

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitContinuedLine
import kotlin.test.Test

/** test/goldens/kit/kit_continued_line_golden_test.dart: "Continued in Claude Code", live and gone. */
class KitContinuedLineGateTest {
    private fun scene(gone: Boolean): @androidx.compose.runtime.Composable () -> Unit = {
        KitContinuedLine(text = "Continued in Claude Code", onOpen = {}, gone = gone)
    }

    private fun shots(state: String, gone: Boolean) {
        for (suffix in listOf("", "_1280x800", "_text2", "_text2_1280x800")) {
            FlutterGoldenGate.check(wrap = { FlutterCapture(it) }, name = "kit_continued_line_$state$suffix", content = scene(gone))
        }
    }

    @Test fun kit_continued_line_default() { shots("default", gone = false) }
    @Test fun kit_continued_line_disabled() { shots("disabled", gone = true) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_continued_line", caps = mapOf("KitContinuedLine" to "conversation"), content = scene(false))
    }
}
