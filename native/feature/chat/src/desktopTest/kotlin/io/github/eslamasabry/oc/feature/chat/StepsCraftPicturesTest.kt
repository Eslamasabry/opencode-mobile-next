package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiOptions
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlin.test.Test

/**
 * Pictures for the owner (2026-11-14, the steps of a long turn) — Glass folded and step-arriving strip shard.
 * Keeps `StepsCraftPicturesTest.glassDarkContourStepArrivingStrip` on its exact class name so `flicker-baseline.tsv`
 * entries remain untouched. Sibling shards: [StepsCraftGlassOpenedPicturesTest], [StepsCraftGlassRunningPicturesTest],
 * [StepsCraftGlassStripPicturesTest].
 */
@OptIn(ExperimentalTestApi::class)
class StepsCraftPicturesTest : StepsCraftPicturesHarness() {
    @Test fun glassDarkContourFolded() = runShot(Look.GlassDarkContour, Moment.Folded) { compareGolden(it) }

    @Test
    fun glassDarkContourStepArrivingStrip() =
        kitUiTest(WIDTH, HEIGHT, options = KitUiOptions(timeout = SCENE_TIMEOUT)) {
            var arrived by mutableStateOf(false)
            mainClock.autoAdvance = false
            screen(Look.GlassDarkContour, running = true, arrived = { arrived })
            mainClock.advanceTimeBy(SETTLE)
            arrived = true
            // Cropped low enough that the arriving row is whole in every frame.
            try {
                strip(
                    "steps-craft-glass-dark-contour-strip-step-arriving",
                    MotionTokens.STANDARD,
                    top = .55f,
                    bottom = .90f,
                )
            } finally {
                end()
            }
        }
}
