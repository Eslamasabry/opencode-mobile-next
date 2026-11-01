package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

/**
 * A part that comes and goes (Dart `KitReveal`; plan 08 §5.15): it unfolds and fades in over STANDARD and folds out
 * over QUICK (leaving is quicker). The **only** layout animation the kit allows (MOT-5); never inside a scrolling list's
 * item (rule R5). The leaving child ignores touches and is hidden from TalkBack. A part composed visible shows at once.
 *
 * States: shown, hidden, unfolding, folding
 * Text: none (content decides)
 * Cap: none
 * Motion: height + fade STANDARD in, QUICK out; Calm = same; Off = instant (class Fold)
 * Replaces: KitReveal (lib/ui/kit/motion/kit_reveal.dart)
 */
@Composable
public fun KitReveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (KitMotion.state.reduced) {
        if (visible) Box(modifier) { content() }
        return
    }
    val specs = KitMotion.specs
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(specs.pace<IntSize>(KitPace.Standard)) + fadeIn(specs.pace(KitPace.Standard)),
        exit = shrinkVertically(specs.pace<IntSize>(KitPace.Quick)) + fadeOut(specs.pace(KitPace.Quick)),
        label = "kit.reveal",
    ) {
        Box(Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)) { content() }
    }
}
