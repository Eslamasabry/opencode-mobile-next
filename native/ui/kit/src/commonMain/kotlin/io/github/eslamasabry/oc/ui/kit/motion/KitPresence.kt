package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

/**
 * A slot along a row that comes and goes (Dart `KitPresence`; plan 08 §5.15): a chip, a count, a trailing control. Not
 * composed while hidden. At Full its width opens on `SpatialFast` while it fades on QUICK; at Calm it only fades and the
 * slot jumps; at Off it is there or not.
 *
 * States: shown, hidden, arriving, leaving
 * Text: none (content decides)
 * Cap: none
 * Motion: width SpatialFast + fade QUICK; Calm = fade, slot jumps; Off = instant (class Travel)
 * Replaces: KitPresence (lib/ui/kit/kit_presence.dart)
 */
@Composable
public fun KitPresence(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = KitMotion.state
    if (state.reduced) {
        if (visible) Box(modifier) { content() }
        return
    }
    val specs = KitMotion.specs
    val travels = state.travels
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            if (travels) expandHorizontally(specs.spatialFast<IntSize>()) + fadeIn(specs.pace(KitPace.Quick))
            else fadeIn(specs.pace(KitPace.Quick)),
        exit =
            if (travels) shrinkHorizontally(specs.spatialFast<IntSize>()) + fadeOut(specs.pace(KitPace.Quick))
            else fadeOut(specs.pace(KitPace.Quick)),
        label = "kit.presence",
    ) {
        Box(Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)) { content() }
    }
}
