package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitSliderStep
import io.github.eslamasabry.oc.ui.kit.choice.KitStepSlider
import kotlin.test.Test

/** test/goldens/kit/kit_step_slider_golden_test.dart `_part`: four text sizes, centred at the top. */
@Composable
private fun slider(enabled: Boolean, selected: String = "c") {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        KitStepSlider(
            semanticsLabel = "Text size",
            steps = listOf(
                KitSliderStep("a", "Smallest"),
                KitSliderStep("b", "Small"),
                KitSliderStep("c", "Default"),
                KitSliderStep("d", "Large"),
            ),
            selected = selected,
            onChange = if (enabled) ({ _: String -> }) else null,
        )
    }
}

class KitStepSliderGateTest {
    @Test fun kit_step_slider_default() { FlutterGoldenGate.check("kit_step_slider_default") { slider(true) } }
    @Test fun kit_step_slider_default_1280x800() { FlutterGoldenGate.check("kit_step_slider_default_1280x800") { slider(true) } }
    @Test fun kit_step_slider_default_text2() {
        FlutterGoldenGate.check("kit_step_slider_default_text2", modes = listOf("dark")) { slider(true) }
    }
    @Test fun kit_step_slider_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_step_slider_default_text2_1280x800", modes = listOf("dark")) { slider(true) }
    }

    /** The declared state, shot on a Scaffold page (kitGalleryShot): the whole window, no gallery inset. */
    @Test fun kit_step_slider_disabled() {
        FlutterGoldenGate.check("kit_step_slider_disabled", frame = FlutterGoldenGate.Frame.Screen) { slider(false, "b") }
    }
    @Test fun kit_step_slider_disabled_1280x800() {
        FlutterGoldenGate.check("kit_step_slider_disabled_1280x800") { slider(false, "b") }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_step_slider", caps = mapOf("KitStepSlider" to "reading")) { slider(true) }
    }
}
