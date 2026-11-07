package io.github.eslamasabry.oc.ui.kit.content.rb.o18

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.content.qr.KitScanner
import io.github.eslamasabry.oc.ui.kit.content.qr.KitScannerPhase
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The scenes of test/goldens/kit/kit_scanner_golden_test.dart at 8302de537: the part in the host a screen gives it
 * (KitScreen with a top bar), the camera a fake with a flat `surface3` preview. Slow is the start 9 s ago (the Dart test
 * pumps its fake clock 9 s); paused is the app gone inactive.
 */
class KitScannerGateTest {
    private enum class State { Starting, Slow, Scanning, Rejected, Paused }

    private fun scene(state: State): @Composable () -> Unit = {
        KitScreen(topBar = { KitTopBar("Scan pairing code", exit = KitTopBarExit.Close, exitLabel = "Close", onExit = {}) }) {
            val surface3 = LocalKitColors.current.surface3
            KitScanner(
                phase = when (state) {
                    State.Starting, State.Slow -> KitScannerPhase.Starting
                    State.Scanning, State.Rejected -> KitScannerPhase.Scanning
                    State.Paused -> KitScannerPhase.Paused
                },
                instruction = "Point the camera at the QR code printed by opencode2 pair.",
                rejected = if (state == State.Rejected) "That is not a pairing code. Scan the QR code that opencode2 pair prints." else null,
                onSlow = listOf(KitAction(label = "Paste it instead", onClick = {})),
                since = if (state == State.Slow) TimeSource.Monotonic.markNow() - 9.seconds else TimeSource.Monotonic.markNow(),
            ) { Canvas(Modifier.fillMaxSize()) { drawRect(surface3) } }
        }
    }

    private val cap: @Composable (@Composable () -> Unit) -> Unit = { ScannerCapture(it) }
    private val screen = FlutterGoldenGate.Frame.Screen

    @Test fun kit_scanner_loading() { FlutterGoldenGate.check("kit_scanner_loading", wrap = cap, frame = screen, content = scene(State.Starting)) }
    @Test fun kit_scanner_loading_slow() { FlutterGoldenGate.check("kit_scanner_loading_slow", wrap = cap, frame = screen, content = scene(State.Slow)) }
    @Test fun kit_scanner_scanning() { FlutterGoldenGate.check("kit_scanner_scanning", wrap = cap, frame = screen, content = scene(State.Scanning)) }
    @Test fun kit_scanner_scanning_1280x800() { FlutterGoldenGate.check("kit_scanner_scanning_1280x800", wrap = cap, frame = screen, content = scene(State.Scanning)) }
    @Test fun kit_scanner_error() { FlutterGoldenGate.check("kit_scanner_error", wrap = cap, frame = screen, content = scene(State.Rejected)) }
    @Test fun kit_scanner_error_text2() { FlutterGoldenGate.check("kit_scanner_error_text2", wrap = cap, frame = screen, modes = listOf("dark"), content = scene(State.Rejected)) }
    @Test fun kit_scanner_paused() { FlutterGoldenGate.check("kit_scanner_paused", wrap = cap, frame = screen, content = scene(State.Paused)) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_scanner", caps = mapOf("KitScanner" to "fill"), frame = screen, content = scene(State.Rejected)) }
}
