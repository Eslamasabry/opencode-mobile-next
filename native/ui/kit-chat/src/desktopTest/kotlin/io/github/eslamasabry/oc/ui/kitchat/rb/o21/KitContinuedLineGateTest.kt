package io.github.eslamasabry.oc.ui.kitchat.rb.o21

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitContinuedLine
import kotlin.test.Test

/** The default scene of test/goldens/kit/kit_continued_line_*: "Continued in Claude Code" at the top start. */
class KitContinuedLineGateTest {
    private val scene: @androidx.compose.runtime.Composable () -> Unit = {
        KitContinuedLine(text = "Continued in Claude Code", onOpen = {})
    }

    @Test fun default() {
        for (size in listOf("", "_1280x800")) FlutterGoldenGate.check("kit_continued_line_default$size", content = scene)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_continued_line", caps = mapOf("KitContinuedLine" to "wrapper"), content = scene)
    }
}
