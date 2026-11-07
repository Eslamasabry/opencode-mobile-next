package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate.Frame
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalBarKey
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalLiveView
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalOutput
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalOutputTags
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalKeyBarController
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_terminal_view_golden_test.dart: the live terminal (fixed ANSI sample, no process)
 * with its key bar, and the output form in a sheet. The Dart scenes push the live view as a page and open the output in
 * `showKitSheet`, so both render as `Frame.Screen`.
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

    private val testRun = listOf(
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
    ).joinToString("\n")

    private val command = "flutter test --concurrency=1 test/kit/kit_terminal_view_test.dart"
    private val aptRun = (1..2500).joinToString("\n") { "Setting up package-$it (2.$it-1) ..." }

    /**
     * Dart `_live`: the server's terminal as a page. The gate renders on the desktop platform; the Dart scenes run as
     * Android except where `_desktop()` makes a PC window (the plain 1280 and 1600 shots), so [desktop] says which.
     */
    private fun liveScene(
        readOnly: Boolean = false,
        latched: Boolean = false,
        interruptKeys: Boolean = false,
        label: String = "Terminal · build-server",
        desktop: Boolean = false,
        height: Int? = null,
    ): @Composable () -> Unit = {
        val keys = remember { TerminalKeyBarController().apply { if (latched) toggle(KitTerminalBarKey.Ctrl) } }
        Column(Modifier.fillMaxSize()) {
            // Dart's 200 dp keyboard: the page's body is what is left above it.
            Box(if (height == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(height.dp)) {
                KitTerminalLiveView(
                    screen = sample,
                    semanticsLabel = label,
                    modifier = Modifier.fillMaxSize(),
                    keys = keys,
                    readOnly = readOnly,
                    interruptKeys = interruptKeys,
                    hardwareKeysOnly = desktop,
                )
            }
        }
    }

    /** Dart `_output`: a command's output in `showKitSheet`. */
    private fun outputScene(
        cmd: String? = command,
        out: String = testRun,
        tailLines: Int = 12,
        title: String = "Test run",
    ): @Composable () -> Unit = {
        KitSheetHost(Modifier.fillMaxSize()) {
            KitSheet(visible = true, onDismissRequest = {}, title = title) {
                KitTerminalOutput(out, command = cmd, tailLines = tailLines, onOpenFull = {})
            }
        }
    }

    private val showEarlier: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {
        onNodeWithTag(KitTerminalOutputTags.SHOW_EARLIER).performClick()
    }

    /** Every shot of a test renders even when an earlier one fails its layout or alignment check; all failures are reported. */
    private val failures = ArrayList<Throwable>()

    private fun shot(
        name: String,
        frame: Frame,
        modes: List<String> = listOf("light", "dark"),
        act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        try {
            FlutterGoldenGate.check(name, frame, modes = modes, act = act, content = content)
        } catch (t: Throwable) {
            failures += t
        }
    }

    @AfterTest fun report() {
        if (failures.isNotEmpty()) throw AssertionError(failures.joinToString("\n\n") { it.message.orEmpty() })
    }

    @Test fun live() {
        shot("kit_terminal_view_live", Frame.Screen, content = liveScene())
        shot("kit_terminal_view_disabled", Frame.Screen, content = liveScene(readOnly = true))
        shot("kit_terminal_view_keys_latched", Frame.Screen, content = liveScene(latched = true, interruptKeys = true))
        shot("kit_terminal_view_keys_compact_915x412", Frame.Screen, content = liveScene(height = 212))
    }

    @Test fun live_sizes() {
        for (s in listOf("_360x800", "_800x1280", "_1280x800", "_1600x1000", "_915x412")) {
            // F: test/goldens/kit/kit_terminal_view_golden_test.dart:285 (`if (size.width >= 1280) _desktop()`)
            shot("kit_terminal_view_live$s", Frame.Screen, content = liveScene(desktop = s == "_1280x800" || s == "_1600x1000"))
        }
    }

    @Test fun output() {
        shot("kit_terminal_view_output_tail", Frame.Screen, content = outputScene())
        shot("kit_terminal_view_output_all", Frame.Screen, act = showEarlier, content = outputScene())
        shot(
            "kit_terminal_view_output_too_long",
            Frame.Screen,
            act = {
                showEarlier()
                waitForIdle()
                onNodeWithTag(KitTerminalOutputTags.OPEN_FULL).performScrollTo()
                // Dart `ensureVisible` leaves the sheet scrolled to its end: the block's foot shows under Open all.
                onNodeWithTag(KitTerminalOutputTags.VIEW).performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f) }
            },
            content = outputScene(cmd = "apt-get install -y build-essential", out = aptRun, title = "Install packages"),
        )
        shot("kit_terminal_view_empty", Frame.Screen, content = outputScene(cmd = "git status --short", out = "", title = "Changes"))
    }

    @Test fun scaled_and_arabic() {
        val dark = listOf("dark")
        for (s in listOf("", "_1280x800")) {
            shot("kit_terminal_view_live_text2$s", Frame.Screen, modes = dark, content = liveScene())
            shot(
                "kit_terminal_view_live_ar$s",
                Frame.Screen,
                modes = dark,
                content = liveScene(label = "الطرفية · build-server"),
            )
            shot("kit_terminal_view_output_tail_text2$s", Frame.Screen, modes = dark, content = outputScene())
            shot("kit_terminal_view_output_tail_ar$s", Frame.Screen, modes = dark, content = outputScene())
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_terminal_view",
            caps = mapOf("KitTerminalView" to "fill"),
            frame = Frame.Screen,
            content = liveScene(),
        )
    }
}
