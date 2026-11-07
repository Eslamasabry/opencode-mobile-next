package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitFineTuneKnob
import io.github.eslamasabry.oc.ui.kit.choice.KitFineTunePanel
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import kotlin.test.Test

private fun segments(vararg labels: String): List<KitSegment<String>> = labels.map { KitSegment(it, it) }

/** test/goldens/kit/kit_fine_tune_panel_golden_test.dart `_part`: two knobs changed, so the reset shows. */
@Composable
private fun finePart(disabled: Boolean = false) {
    Box(Modifier.padding(16.dp)) {
        KitFineTunePanel(
            resetLabel = "Reset to Solid defaults",
            onReset = {},
            disabledReason = if (disabled) "Saver keeps surfaces solid, so Fine-tune waits until Saver is off." else null,
            knobs = listOf(
                KitFineTuneKnob(
                    title = "Corners",
                    defaultNote = "Default: Round",
                    selected = "Sharp",
                    segments = segments("Round", "Soft", "Sharp"),
                    onChanged = {},
                ),
                KitFineTuneKnob(
                    title = "Depth",
                    hint = "Raised lifts cards off the page.",
                    defaultNote = "Default: Flat",
                    selected = "Flat",
                    segments = segments("Flat", "Raised"),
                    onChanged = {},
                ),
                KitFineTuneKnob(
                    title = "Density",
                    hint = "Compact fits more on the screen.",
                    defaultNote = "Default: Comfortable",
                    selected = "Compact",
                    segments = segments("Comfortable", "Compact"),
                    onChanged = {},
                ),
                KitFineTuneKnob(
                    title = "Edges",
                    hint = "A thin line around cards.",
                    defaultNote = "Default: Hairline",
                    selected = "Hairline",
                    segments = segments("Hairline", "None"),
                    disabledReason = "High contrast keeps cards flat with edges.",
                    onChanged = null,
                ),
            ),
        )
    }
}

class KitFineTunePanelGateTest {
    @Test fun kit_fine_tune_panel_default() { FlutterGoldenGate.check("kit_fine_tune_panel_default") { finePart() } }

    @Test fun kit_fine_tune_panel_default_1280x800() {
        FlutterGoldenGate.check("kit_fine_tune_panel_default_1280x800") { finePart() }
    }

    @Test fun kit_fine_tune_panel_disabled() {
        FlutterGoldenGate.check("kit_fine_tune_panel_disabled") { finePart(disabled = true) }
    }

    @Test fun kit_fine_tune_panel_disabled_1280x800() {
        FlutterGoldenGate.check("kit_fine_tune_panel_disabled_1280x800") { finePart(disabled = true) }
    }

    @Test fun kit_fine_tune_panel_default_text2() {
        FlutterGoldenGate.check("kit_fine_tune_panel_default_text2", modes = listOf("dark")) {
            finePart() // the Dart scroll view: the gallery frame already lets the panel run past the window
        }
    }

    @Test fun kit_fine_tune_panel_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_fine_tune_panel_default_text2_1280x800", modes = listOf("dark")) {
            finePart() // the Dart scroll view: the gallery frame already lets the panel run past the window
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_fine_tune_panel", caps = mapOf("KitFineTunePanel" to "reading")) { finePart() }
    }
}
