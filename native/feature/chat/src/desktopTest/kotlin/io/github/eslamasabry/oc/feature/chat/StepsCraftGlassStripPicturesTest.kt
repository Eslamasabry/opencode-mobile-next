package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiOptions
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlin.test.Test

/**
 * Pictures for the owner (2026-11-14, the steps of a long turn) — Glass fold-opening strip and Solid light shard.
 */
@OptIn(ExperimentalTestApi::class)
class StepsCraftGlassStripPicturesTest : StepsCraftPicturesHarness() {
    @Test fun solidLightFolded() = runShot(Look.SolidLight, Moment.Folded) { compareGolden(it) }

    @Test fun solidLightOpened() = runShot(Look.SolidLight, Moment.Opened) { compareGolden(it) }

    @Test fun solidLightRunning() = runShot(Look.SolidLight, Moment.Running) { compareGolden(it) }

    @Test
    fun glassDarkContourFoldOpeningStrip() =
        kitUiTest(WIDTH, HEIGHT, options = KitUiOptions(timeout = SCENE_TIMEOUT)) {
            mainClock.autoAdvance = false
            screen(Look.GlassDarkContour, running = false)
            mainClock.advanceTimeBy(SETTLE)
            try {
                // The first frame is the folded row before the finger lands (the tap's own press fill is not the fold's
                // motion); the row then stays where it was tapped while its steps open below it.
                strip(
                    "steps-craft-glass-dark-contour-strip-fold-opening",
                    foldMs,
                    top = .40f,
                    bottom = .88f,
                ) {
                    tap("chat-work-summary")
                }
            } finally {
                end()
            }
        }
}
