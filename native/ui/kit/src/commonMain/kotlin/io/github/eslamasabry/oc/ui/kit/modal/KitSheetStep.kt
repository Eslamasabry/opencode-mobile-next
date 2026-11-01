package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.kitLeaving
import io.github.eslamasabry.oc.ui.kit.motion.pace

/**
 * A sheet's second view replacing the first in place (Dart `KitSheetStep`; plan 08 §5.5): no sheet on a sheet. The
 * discard question, a confirmation step or a sub-choice swaps in: the old view fades out over QUICK, the new one in over
 * STANDARD, and the sheet's height follows on `Spatial`, clipped (a sheet is not a scrolling list, so Fold is allowed).
 * The leaving view is picture only.
 *
 * States: one per step
 * Text: none (steps decide)
 * Cap: none (the sheet's)
 * Motion: cross-fade QUICK out / STANDARD in + height on Spatial; Calm = same; Off = instant (class Fold)
 * Replaces: KitSheetStep (lib/ui/kit/kit_sheet_parts.dart)
 */
@Composable
public fun <T> KitSheetStep(step: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    if (KitMotion.state.reduced) {
        Box(modifier) { key(step) { content(step) } }
        return
    }
    val specs = KitMotion.specs
    AnimatedContent(
        targetState = step,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(specs.pace(KitPace.Standard)) togetherWith fadeOut(specs.pace(KitPace.Quick)))
                .using(SizeTransform(clip = true) { _, _ -> specs.spatial<IntSize>() })
        },
        label = "kit.sheet.step",
    ) { shown ->
        Box(Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)) { content(shown) }
    }
}
