package io.github.eslamasabry.oc.ui.kit.content.rb.o19

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.content.terminal.KitTerminalBarKey
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalKeyBar
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalKeyBarController
import kotlin.test.Test

/**
 * The native-only matrix for `part-terminal_key_bar` (standard §8.2): the two-row bar with `interruptKeys` (the server
 * terminal's form, Dart `terminal_key_bar.dart`'s extra row) and Ctrl latched (the accent cap and its on-colour hook),
 * rendered in every Style × light/dark × font scale 1.0/1.3/2.0 × LTR/RTL, with the 48 dp targets and the labelled keys
 * checked. The part has no Flutter golden of its own (order manifest: 0 goldens).
 */
class KitTerminalKeyBarGateTest {
    private val bar: @Composable () -> Unit = {
        val controller = remember { TerminalKeyBarController().apply { toggle(KitTerminalBarKey.Ctrl) } }
        TerminalKeyBar(
            controller = controller,
            onKey = { _, _, _ -> },
            interruptKeys = true, // F: lib/ui/kit/terminal_key_bar.dart:236 (the bar with interrupt keys leads with ^C ^D)
        )
    }

    @Test
    fun matrix() {
        FlutterGoldenGate.matrix("part-terminal_key_bar", content = bar)
    }
}
