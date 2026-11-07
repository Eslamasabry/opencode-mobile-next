package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.setup.KitProgressRing
import io.github.eslamasabry.oc.ui.kit.setup.KitTrailMark
import kotlin.test.Test

/**
 * The scenes of test/goldens/kit/kit_progress_ring_golden_test.dart: a measured download (64 %, "41 of 64 MB"), three
 * of five steps, the same steps halted, and finished, each over a five-mark trail.
 */
class KitProgressRingGateTest {
    private val trail = listOf(KitTrailMark.Done, KitTrailMark.Done, KitTrailMark.Done, KitTrailMark.Current, KitTrailMark.Waiting)

    private val measured: @Composable () -> Unit = {
        KitProgressRing(
            headline = "Downloading OpenCode",
            semanticsLabel = "Setup progress",
            value = .64f,
            caption = "41 of 64 MB",
            trail = trail,
        )
    }

    private val steps: @Composable () -> Unit = {
        KitProgressRing(headline = "Starting OpenCode", semanticsLabel = "Setup progress", done = 3, of = 5, trail = trail)
    }

    private val halted: @Composable () -> Unit = {
        KitProgressRing(headline = "Setup is paused", semanticsLabel = "Setup progress", done = 3, of = 5, halted = true, trail = trail)
    }

    private val finished: @Composable () -> Unit = {
        KitProgressRing(headline = "All set", semanticsLabel = "Setup progress", finished = true, trail = List(5) { KitTrailMark.Done })
    }

    @Test fun kit_progress_ring_finished() { FlutterGoldenGate.check("kit_progress_ring_finished", content = finished) }

    @Test fun kit_progress_ring_halted() { FlutterGoldenGate.check("kit_progress_ring_halted", content = halted) }

    @Test fun kit_progress_ring_measured() { FlutterGoldenGate.check("kit_progress_ring_measured", content = measured) }

    @Test fun kit_progress_ring_measured_1280x800() { FlutterGoldenGate.check("kit_progress_ring_measured_1280x800", content = measured) }

    @Test fun kit_progress_ring_measured_text2() {
        FlutterGoldenGate.check("kit_progress_ring_measured_text2", modes = listOf("dark"), content = measured)
    }

    @Test fun kit_progress_ring_measured_text2_1280x800() {
        FlutterGoldenGate.check("kit_progress_ring_measured_text2_1280x800", modes = listOf("dark"), content = measured)
    }

    @Test fun kit_progress_ring_steps() { FlutterGoldenGate.check("kit_progress_ring_steps", content = steps) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_progress_ring", caps = mapOf("KitProgressRing" to "glyph"), content = measured)
    }
}
