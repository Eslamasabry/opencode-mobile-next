package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import kotlin.math.roundToInt

/**
 * Cross-fades between contents (Dart `KitSwap`; plan 08 §5.15): [target] says when the content is new. No scale and no
 * size animation (MOT-2, MOT-5). The leaving child keeps its state while it fades (`8bff010c9`), ignores touches and is
 * hidden from TalkBack.
 *
 * States: at rest, changing
 * Text: none (content decides)
 * Cap: none
 * Motion: cross-fade [pace] (QUICK) both ways; Calm = same; Off = instant (class Fade)
 * Replaces: KitSwap (lib/ui/kit/motion/kit_motion_parts.dart)
 */
@Composable
public fun <T> KitSwap(
    target: T,
    modifier: Modifier = Modifier,
    pace: KitPace = KitPace.Quick,
    alignment: Alignment = Alignment.Center,
    content: @Composable (T) -> Unit,
) {
    if (KitMotion.state.reduced) {
        Box(modifier, contentAlignment = alignment) { key(target) { content(target) } }
        return
    }
    val specs = KitMotion.specs
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = alignment,
        transitionSpec = {
            (fadeIn(specs.pace(pace)) togetherWith fadeOut(specs.pace(pace)))
                .using(SizeTransform(clip = false) { _, _ -> snap<IntSize>() })
        },
        label = "kit.swap",
    ) { shown ->
        Box(Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)) { content(shown) }
    }
}

/**
 * A count that changes in place (Dart `KitSwap.count`; plan 08 §5.15): the new number rolls in from below as the old one
 * rolls out above (the other way when it falls), both fading, on QUICK, travelling 60 % of the number's height. Clipped
 * to its box only while it rolls.
 *
 * States: at rest, rolling
 * Text: none (content decides)
 * Cap: none
 * Motion: roll QUICK at Full; Calm = cross-fade; Off = instant (class Travel)
 * Replaces: KitSwap.count (lib/ui/kit/motion/kit_motion_parts.dart)
 */
@Composable
public fun KitSwapCount(
    count: Int,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    content: @Composable (Int) -> Unit,
) {
    val state = KitMotion.state
    if (!state.travels) {
        KitSwap(count, modifier, KitPace.Quick, alignment, content)
        return
    }
    val specs = KitMotion.specs
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        contentAlignment = alignment,
        transitionSpec = {
            val up = if (targetState >= initialState) 1 else -1
            val share = MotionTokens.COUNT_ROLL_SHARE
            (
                slideInVertically(specs.pace<IntOffset>(KitPace.Quick)) { h -> up * (h * share).roundToInt() } +
                    fadeIn(specs.pace(KitPace.Quick))
                ).togetherWith(
                slideOutVertically(specs.pace<IntOffset>(KitPace.Quick)) { h -> -up * (h * share).roundToInt() } +
                    fadeOut(specs.pace(KitPace.Quick)),
            ).using(SizeTransform(clip = true) { _, _ -> snap<IntSize>() })
        },
        label = "kit.swap.count",
    ) { shown ->
        Box(Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)) { content(shown) }
    }
}
