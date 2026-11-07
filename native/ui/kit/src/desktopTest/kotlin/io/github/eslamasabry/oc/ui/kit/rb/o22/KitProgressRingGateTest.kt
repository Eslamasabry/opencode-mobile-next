package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.setup.KitTrailMark
import io.github.eslamasabry.oc.ui.kit.state.KitProgressRing
import io.github.eslamasabry.oc.ui.kit.state.KitRingFigure
import kotlin.test.Test

/** test/goldens/kit/kit_progress_ring_golden_test.dart: measured, steps, halted and finished, with the five-step trail. */
class KitProgressRingGateTest {
    private val trail = listOf(KitTrailMark.Done, KitTrailMark.Done, KitTrailMark.Done, KitTrailMark.Current, KitTrailMark.Waiting)

    private val measured: @Composable () -> Unit = {
        KitProgressRing(KitRingFigure.Measured(.64, caption = "41 of 64 MB"), headline = "Downloading OpenCode", semanticsLabel = "Setup progress", trail = trail)
    }
    private val steps: @Composable () -> Unit = {
        KitProgressRing(KitRingFigure.Steps(3, 5), headline = "Starting OpenCode", semanticsLabel = "Setup progress", trail = trail)
    }
    private val halted: @Composable () -> Unit = {
        KitProgressRing(KitRingFigure.Steps(3, 5), headline = "Setup is paused", semanticsLabel = "Setup progress", trail = trail, halted = true)
    }
    private val finished: @Composable () -> Unit = {
        KitProgressRing(KitRingFigure.Finished, headline = "All set", semanticsLabel = "Setup progress", trail = List(5) { KitTrailMark.Done })
    }

    @Test fun kit_progress_ring_measured() { FlutterGoldenGate.check("kit_progress_ring_measured", content = measured) }
    @Test fun kit_progress_ring_steps() { FlutterGoldenGate.check("kit_progress_ring_steps", content = steps) }
    @Test fun kit_progress_ring_halted() { FlutterGoldenGate.check("kit_progress_ring_halted", content = halted) }
    @Test fun kit_progress_ring_finished() { FlutterGoldenGate.check("kit_progress_ring_finished", content = finished) }
    @Test fun kit_progress_ring_measured_1280x800() { FlutterGoldenGate.check("kit_progress_ring_measured_1280x800", content = measured) }
    @Test fun kit_progress_ring_measured_text2() { FlutterGoldenGate.check("kit_progress_ring_measured_text2", modes = listOf("dark"), content = measured) }
    @Test fun kit_progress_ring_measured_text2_1280x800() {
        FlutterGoldenGate.check("kit_progress_ring_measured_text2_1280x800", modes = listOf("dark"), content = measured)
    }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_progress_ring", caps = mapOf("KitProgressRing" to "glyph"), content = measured) }
}
