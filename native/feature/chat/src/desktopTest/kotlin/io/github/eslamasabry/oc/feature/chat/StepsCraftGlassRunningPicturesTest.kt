package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import kotlin.test.Test

/**
 * Pictures for the owner (2026-11-14, the steps of a long turn) — Glass running shard.
 */
@OptIn(ExperimentalTestApi::class)
class StepsCraftGlassRunningPicturesTest : StepsCraftPicturesHarness() {
    @Test fun glassDarkContourRunning() = runShot(Look.GlassDarkContour, Moment.Running) { compareGolden(it) }
}
