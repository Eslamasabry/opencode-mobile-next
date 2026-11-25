package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.chat.E20Goldens.compareGolden
import kotlin.test.Test

/**
 * Pictures for the owner (2026-11-14, the steps of a long turn) — Glass opened and Solid dark shard.
 */
@OptIn(ExperimentalTestApi::class)
class StepsCraftGlassOpenedPicturesTest : StepsCraftPicturesHarness() {
    @Test fun glassDarkContourOpened() = runShot(Look.GlassDarkContour, Moment.Opened) { compareGolden(it) }

    @Test fun solidDarkFolded() = runShot(Look.SolidDark, Moment.Folded) { compareGolden(it) }

    @Test fun solidDarkOpened() = runShot(Look.SolidDark, Moment.Opened) { compareGolden(it) }

    @Test fun solidDarkRunning() = runShot(Look.SolidDark, Moment.Running) { compareGolden(it) }
}
