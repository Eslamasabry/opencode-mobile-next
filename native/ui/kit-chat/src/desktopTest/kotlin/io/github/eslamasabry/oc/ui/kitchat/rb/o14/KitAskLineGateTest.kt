package io.github.eslamasabry.oc.ui.kitchat.rb.o14

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitAskLine
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonTranscript
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.rb.o06.FlutterCapture
import kotlin.test.Test

/**
 * test/goldens/kit/kit_ask_line_golden_test.dart: KitAskLine inline above a composer-height inset at every gallery
 * size, stacked (a long question in 320 dp) at 412x915, light and dark; inline alone at 2.0 text and in Arabic at
 * 412x915 and 1280x800 (dark only); and KitSkeletonTranscript (360 dp tall) at every gallery size, light and dark.
 * Frame: kitGalleryPart (Part).
 */
class KitAskLineGateTest {
    private val question = "Tell me when the first reply arrives?" // F: test/goldens/kit/kit_ask_line_golden_test.dart:19
    private val longQuestion = "Tell me the moment the agent finishes its very first reply in this conversation?"
    private val questionAr = "أخبرني عند وصول أول رد من الوكيل؟"

    /** Flutter `_askLine` (kit_ask_line_golden_test.dart:25-33). */
    @Composable
    private fun AskLine(q: String = question, arabic: Boolean = false) {
        KitAskLine(
            icon = KitIcons.inbox,
            question = q,
            accept = KitAction(if (arabic) "أعلمني" else "Notify me", {}),
            decline = KitAction(if (arabic) "ليس الآن" else "Not now", {}),
        )
    }

    /** Flutter `_askAboveComposer` (kit_ask_line_golden_test.dart:57-64) with `_composerInset` (38-43). */
    @Composable
    private fun AskAboveComposer() {
        Column(Modifier.fillMaxWidth()) {
            AskLine()
            Box(Modifier.fillMaxWidth().height(56.dp).background(LocalKitColors.current.surface2)) // F: test/goldens/kit/kit_ask_line_golden_test.dart:40
        }
    }

    @Composable
    private fun Skeleton() {
        KitSkeletonTranscript(Modifier.height(360.dp)) // F: test/goldens/kit/kit_ask_line_golden_test.dart:152
    }

    private val sizes = listOf("", "_360x800", "_915x412", "_800x1280", "_1280x800", "_1600x1000") // F: test/goldens/kit/kit_ask_line_golden_test.dart:45-55
    private val dark = listOf("dark")

    private fun shot(name: String, modes: List<String> = listOf("light", "dark"), content: @Composable () -> Unit) =
        FlutterGoldenGate.check(name, wrap = { FlutterCapture(it) }, modes = modes, content = content)

    @Test fun ask_stacked() {
        shot("kit_ask_line_ask_stacked") {
            Box(Modifier.width(320.dp)) { AskLine(longQuestion) } // F: test/goldens/kit/kit_ask_line_golden_test.dart:83
        }
    }

    @Test fun ask_inline() {
        for (size in sizes) shot("kit_ask_line_ask_inline$size") { AskAboveComposer() }
    }

    @Test fun ask_inline_text2_and_ar() {
        shot("kit_ask_line_ask_inline_text2", dark) { AskLine() }
        shot("kit_ask_line_ask_inline_text2_1280x800", dark) { AskLine() }
        shot("kit_ask_line_ask_inline_ar", dark) { AskLine(questionAr, arabic = true) }
        shot("kit_ask_line_ask_inline_ar_1280x800", dark) { AskLine(questionAr, arabic = true) }
    }

    @Test fun skeleton() {
        for (size in sizes) shot("kit_ask_line_skeleton$size") { Skeleton() }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_ask_line", caps = mapOf("KitAskLine" to "conversation")) { AskAboveComposer() }
    }
}
