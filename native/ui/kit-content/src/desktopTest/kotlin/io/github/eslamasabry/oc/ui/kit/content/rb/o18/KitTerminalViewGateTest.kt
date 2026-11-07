package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalBarKey
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalLiveView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalOutput
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalScreen
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalViewTags
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalKeyBarController
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * test/goldens/kit/kit_terminal_view_golden_test.dart: the live terminal with its key bar (a fixed ANSI sample written
 * to the screen) and the output form in a sheet, in every declared state, at the gallery sizes, at 2.0 text and in
 * Arabic. Scenes are pushed full-window (`Frame.Screen`) and captured as the gallery does (DPR 3 view, 1x picture).
 */
class KitTerminalViewGateTest {
    private val sample =
        "\u001b[32mdev@build-server\u001b[0m:\u001b[34m~/app\u001b[0m\$ ls --color\r\n" +
            "\u001b[34mlib\u001b[0m  \u001b[34mtest\u001b[0m  \u001b[34mtool\u001b[0m  " +
            "\u001b[32mbuild.sh\u001b[0m  pubspec.yaml\r\n" +
            "\u001b[32mdev@build-server\u001b[0m:\u001b[34m~/app\u001b[0m\$ flutter test\r\n" +
            "\u001b[31merror: test/app_test.dart:12:3: Expected a value\u001b[0m\r\n" +
            "\u001b[32m00:06 +42: All tests passed!\u001b[0m\r\n" +
            "\u001b[32mdev@build-server\u001b[0m:\u001b[34m~/app\u001b[0m\$ "

    /** A finished test run, read from the end: the tail shows how it went. */
    private val testRun =
        listOf(
            "00:00 +0: loading test/kit/kit_terminal_view_test.dart",
            "00:01 +1: the key bar: every key is 48 x 48",
            "00:01 +2: the key bar compact: every key is 48 x 48",
            "00:01 +3: the key bar: Ctrl latches for one key",
            "00:02 +4: the key bar: no key vibrates",
            "00:02 +5: the key bar: disabled keys ignore taps",
            "00:02 +6: the live view: Tab reaches the shell",
            "00:03 +7: the live view: read-only drops typed input",
            "00:03 +8: the palette: themeOf(dark) uses the roles",
            "00:03 +9: the palette: themeOf(light) uses the roles",
            "00:04 +10: the output form: the tail first",
            "00:04 +11: the output form: too long, Open all",
            "00:04 +12: the output form: secrets are masked",
            "00:05 +13: the output form: tints",
            "00:05 +13 ~1: the output form: on a PC (skipped)",
            "Warning: golden images were not compared",
            "00:05 +14 ~1: the output form: in Arabic",
            "00:06 +14 ~1 -1: the old TerminalView forwards [E]",
            "  Expected: <1>",
            "    Actual: <0>",
            "error: Some tests failed.",
            "\u001b[2mTest run took 6.1 s\u001b[0m",
            "\u001b[32m14 passed\u001b[0m, \u001b[33m1 skipped\u001b[0m, \u001b[31m1 failed\u001b[0m",
        )

    private val command = "flutter test --concurrency=1 test/kit/kit_terminal_view_test.dart"

    /** An apt run long enough to open elsewhere (2,500 lines). */
    private val aptRun = (1..2500).joinToString("\n") { "Setting up package-$it (2.$it-1) ..." }

    @BeforeTest
    fun latinDigits() {
        // The Flutter gallery writes counts with Latin digits in every locale.
        Numbers.pinned = NumberSymbols.Latin
    }

    @AfterTest
    fun phoneDigits() {
        Numbers.pinned = null
    }

    private val capture: @Composable (@Composable () -> Unit) -> Unit = { TerminalCapture(it) }

    /** The live terminal of a server, with its keys (`_live`). [desktop]: a PC window, hardware keys only. */
    @Composable
    private fun Live(
        readOnly: Boolean = false,
        latched: Boolean = false,
        interruptKeys: Boolean = false,
        label: String = "Terminal · build-server",
        desktop: Boolean = false,
        keyboard: Dp? = null,
    ) {
        val screen = remember { KitTerminalScreen().apply { write(sample) } }
        val keys = remember { TerminalKeyBarController().apply { if (latched) toggle(KitTerminalBarKey.Ctrl) } }
        KitTerminalLiveView(
            screen = screen,
            semanticsLabel = label,
            modifier = Modifier.fillMaxSize(),
            readOnly = readOnly,
            keys = keys,
            interruptKeys = interruptKeys,
            hardwareKeysOnly = desktop,
            keyboardInset = keyboard,
        )
    }

    /** A command's output in a sheet (`_output`). */
    @Composable
    private fun Output(
        command: String? = this.command,
        output: String? = null,
        tailLines: Int = 12,
        title: String = "Test run",
    ) {
        KitSheetHost(Modifier.fillMaxSize()) {
            KitSheet(visible = true, onDismissRequest = {}, title = title) {
                KitTerminalOutput(
                    output = output ?: testRun.joinToString("\n"),
                    command = command,
                    tailLines = tailLines,
                    onOpenFull = {},
                )
            }
        }
    }

    private fun shot(
        name: String,
        modes: List<String> = listOf("light", "dark"),
        act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null,
        dpr3: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        FlutterGoldenGate.check(name, Frame.Screen, modes = modes, wrap = if (dpr3) capture else ({ it() }), act = act, content = content)
    }

    private val showEarlier: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {
        onNodeWithTag(KitTerminalViewTags.SHOW_EARLIER).performClick()
    }

    @Test fun kit_terminal_view_live() = shot("kit_terminal_view_live") { Live() }

    @Test fun kit_terminal_view_disabled() = shot("kit_terminal_view_disabled") { Live(readOnly = true) }

    @Test fun kit_terminal_view_keys_latched() = shot("kit_terminal_view_keys_latched") { Live(latched = true, interruptKeys = true) }

    /** Landscape with the keyboard up: 200 dp of keyboard; the page above it is 212 dp tall. */
    @Test fun kit_terminal_view_keys_compact_915x412() =
        shot("kit_terminal_view_keys_compact_915x412") {
            Box(Modifier.fillMaxWidth().height(212.dp)) { Live(keyboard = 200.dp) }
        }

    @Test fun kit_terminal_view_output_tail() = shot("kit_terminal_view_output_tail") { Output() }

    @Test fun kit_terminal_view_output_all() = shot("kit_terminal_view_output_all", act = showEarlier) { Output() }

    @Test fun kit_terminal_view_output_too_long() =
        shot(
            "kit_terminal_view_output_too_long",
            act = {
                showEarlier()
                waitForIdle()
                onNodeWithTag(KitTerminalViewTags.OPEN_FULL).performScrollTo()
                waitForIdle()
                // Flutter's ensureVisible puts the target at the top, which the end of the scroll range clamps: the
                // sheet ends up scrolled all the way down.
                onNode(hasScrollAction() and hasAnyDescendant(hasTestTag(KitTerminalViewTags.OPEN_FULL)))
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, Float.MAX_VALUE / 2) }
            },
            // Two thousand lines in one DPR-3 layer take minutes a frame on this machine; drawn at 1x instead.
            dpr3 = false,
        ) { Output(command = "apt-get install -y build-essential", output = aptRun, title = "Install packages") }

    @Test fun kit_terminal_view_empty() =
        shot("kit_terminal_view_empty") { Output(command = "git status --short", output = "", title = "Changes") }

    @Test fun kit_terminal_view_live_360x800() = shot("kit_terminal_view_live_360x800") { Live() }

    @Test fun kit_terminal_view_live_800x1280() = shot("kit_terminal_view_live_800x1280") { Live() }

    /** A PC window: hardware keys, no key bar. */
    @Test fun kit_terminal_view_live_1280x800() = shot("kit_terminal_view_live_1280x800") { Live(desktop = true) }

    @Test fun kit_terminal_view_live_1600x1000() = shot("kit_terminal_view_live_1600x1000") { Live(desktop = true) }

    @Test fun kit_terminal_view_live_915x412() = shot("kit_terminal_view_live_915x412") { Live() }

    // 2.0 text and Arabic, dark: the live terminal (a touch tablet keeps its keys at 1280) and the output's tail.
    @Test fun kit_terminal_view_live_text2() = shot("kit_terminal_view_live_text2", listOf("dark")) { Live() }

    @Test fun kit_terminal_view_live_text2_1280x800() = shot("kit_terminal_view_live_text2_1280x800", listOf("dark")) { Live() }

    @Test fun kit_terminal_view_live_ar() = shot("kit_terminal_view_live_ar", listOf("dark")) { Live(label = "الطرفية · build-server") }

    @Test fun kit_terminal_view_live_ar_1280x800() =
        shot("kit_terminal_view_live_ar_1280x800", listOf("dark")) { Live(label = "الطرفية · build-server") }

    @Test fun kit_terminal_view_output_tail_text2() = shot("kit_terminal_view_output_tail_text2", listOf("dark")) { Output() }

    @Test fun kit_terminal_view_output_tail_text2_1280x800() =
        shot("kit_terminal_view_output_tail_text2_1280x800", listOf("dark")) { Output() }

    @Test fun kit_terminal_view_output_tail_ar() = shot("kit_terminal_view_output_tail_ar", listOf("dark")) { Output() }

    @Test fun kit_terminal_view_output_tail_ar_1280x800() =
        shot("kit_terminal_view_output_tail_ar_1280x800", listOf("dark")) { Output() }

    @Test
    fun matrix() {
        FlutterGoldenGate.matrix("part-kit_terminal_view", caps = mapOf("KitTerminalView" to "fill"), frame = Frame.Screen) {
            Live(interruptKeys = true)
        }
    }
}
