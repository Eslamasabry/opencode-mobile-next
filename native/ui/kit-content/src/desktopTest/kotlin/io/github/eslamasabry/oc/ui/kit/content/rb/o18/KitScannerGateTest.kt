package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.scanner.KitScanner
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test

/**
 * Every scene of test/goldens/kit/kit_scanner_golden_test.dart, in the host a screen gives it (KitScreen with the
 * "Scan pairing code" bar and Close; Frame.Screen). The fake camera's preview is a flat `surface3`.
 *
 * Native's KitScanner (`:ui:kit`, not this module) is the frame and its words only: it has no camera lifecycle, so
 * starting (`loading`), slow (`loading_slow`, with "Paste it instead") and paused have no native input. Those shots
 * render the nearest native call (the frame with no preview: the camera has not given a picture) and are measured as
 * they are; the gaps go to the kit owner.
 */
class KitScannerGateTest {
    private val instruction = "Point the camera at the QR code printed by opencode2 pair."
    private val rejectedText = "That is not a pairing code. Scan the QR code that opencode2 pair prints."

    private fun host(rejected: String? = null, preview: Boolean = true): @Composable () -> Unit = {
        KitScreen(
            page = true,
            topBar = { KitTopBar("Scan pairing code", exit = KitTopBarExit.Close, exitLabel = "Close", onExit = {}) },
        ) {
            KitScanner(instruction, previewLabel = "Camera", rejected = rejected) {
                if (preview) Box(Modifier.fillMaxSize().background(LocalKitColors.current.surface3))
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit, modes: List<String> = listOf("light", "dark")) =
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, modes = modes, content = content)

    @Test fun states() {
        shot("kit_scanner_loading", host(preview = false))
        shot("kit_scanner_loading_slow", host(preview = false))
        shot("kit_scanner_scanning", host())
        shot("kit_scanner_error", host(rejectedText))
        shot("kit_scanner_paused", host(preview = false))
        shot("kit_scanner_scanning_1280x800", host())
        shot("kit_scanner_error_text2", host(rejectedText), modes = listOf("dark"))
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_scanner", caps = mapOf("KitScanner" to "fill"), frame = FlutterGoldenGate.Frame.Screen, content = host())
    }
}
