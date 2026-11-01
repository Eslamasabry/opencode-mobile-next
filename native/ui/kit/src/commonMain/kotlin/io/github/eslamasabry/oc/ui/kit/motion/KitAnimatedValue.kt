package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Eases a number toward [value] (Dart `KitAnimatedValue`, `KitPace` merged in; plan 08 §5.15). The first build shows
 * [value] at once; later changes ease on [pace] (STANDARD on `Enter`); [jump] shows the new value at once (a new job, a
 * reset). [content] should change paint or a transform (a bar's fill), never layout.
 *
 * States: settled, easing, jumped
 * Text: none (the host states the value in words)
 * Cap: none
 * Motion: tween STANDARD on Enter; Calm = same; Off = instant (class Fade)
 * Replaces: KitAnimatedValue, KitPace (lib/ui/kit/motion/kit_motion_parts.dart)
 */
@Composable
public fun KitAnimatedValue(
    value: Float,
    pace: KitPace = KitPace.Standard,
    jump: Boolean = false,
    content: @Composable (Float) -> Unit,
) {
    content(animateKitValue(value, pace, jump))
}

/** The value form of [KitAnimatedValue], for kit parts that read it in draw. */
@Composable
public fun animateKitValue(value: Float, pace: KitPace = KitPace.Standard, jump: Boolean = false): Float {
    val reduced = KitMotion.state.reduced
    val specs = KitMotion.specs
    val anim = remember { Animatable(value) }
    LaunchedEffect(value, jump, reduced) {
        if (jump || reduced) anim.snapTo(value) else anim.animateTo(value, specs.pace(pace))
    }
    // Off and jump show the new value on this very frame, not after the effect runs.
    return if (jump || reduced) value else anim.value
}
