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

/**
 * The scenes of test/goldens/kit/kit_step_slider_golden_test.dart: "Text size" over Smallest, Small, Default, Large;
 * default rests on Default, disabled (no action) on Small.
 */
class KitStepSliderGateTest {
    private val steps = listOf(
        KitSliderStep("a", "Smallest"),
        KitSliderStep("b", "Small"),
        KitSliderStep("c", "Default"),
        KitSliderStep("d", "Large"),
    )

    @Composable
    private fun Slider(enabled: Boolean, selected: String) {
        val change: ((String) -> Unit)? = if (enabled) { { _ -> } } else null
        KitStepSlider("Text size", steps, selected, change)
    }

    /** `_part`: 16 dp padding all round, the slider top-centred (kitGalleryPart). */
    private fun part(enabled: Boolean, selected: String): @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopCenter) { Slider(enabled, selected) }
    }

    /** The 412x915 disabled shot is a kitGalleryShot Scaffold body: the frame's 16 dp top stands for the padding's. */
    private val disabledShot: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopCenter) { Slider(false, "b") }
    }

    private val default = part(enabled = true, selected = "c")

    @Test fun kit_step_slider_default() { FlutterGoldenGate.check("kit_step_slider_default", content = default) }

    @Test fun kit_step_slider_default_1280x800() { FlutterGoldenGate.check("kit_step_slider_default_1280x800", content = default) }

    @Test fun kit_step_slider_default_text2() {
        FlutterGoldenGate.check("kit_step_slider_default_text2", modes = listOf("dark"), content = default)
    }

    @Test fun kit_step_slider_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_step_slider_default_text2_1280x800", modes = listOf("dark"), content = default)
    }

    @Test fun kit_step_slider_disabled() { FlutterGoldenGate.check("kit_step_slider_disabled", content = disabledShot) }

    @Test fun kit_step_slider_disabled_1280x800() {
        FlutterGoldenGate.check("kit_step_slider_disabled_1280x800", content = part(enabled = false, selected = "b"))
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_step_slider", caps = mapOf("KitStepSlider" to "reading"), content = default)
    }
}
