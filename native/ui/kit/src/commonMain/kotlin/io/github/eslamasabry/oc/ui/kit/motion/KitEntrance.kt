package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens

/**
 * A late part arriving (Dart `KitEntrance`; plan 08 §5.15): it fades in over STANDARD and, at Full, rises
 * ENTRANCE_RISE (8 dp) into place, paint only. It plays when composed and again whenever [key] changes; [play] false
 * shows it at once (a part that was there from the start: first build final, R8).
 *
 * States: arriving, at rest
 * Text: none (content decides)
 * Cap: none
 * Motion: fade STANDARD + 8 dp rise; Calm = fade; Off = instant (class Travel)
 * Replaces: KitEntrance (lib/ui/kit/motion/kit_reveal.dart), EntranceReveal (lib/ui/widgets/entrance.dart)
 */
@Composable
public fun KitEntrance(modifier: Modifier = Modifier, key: Any? = Unit, play: Boolean = true, content: @Composable () -> Unit) {
    val state = KitMotion.state
    if (state.reduced || !play) {
        Box(modifier) { content() }
        return
    }
    val specs = KitMotion.specs
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, specs.pace(KitPace.Standard)) }
    val rise = if (state.travels) MotionTokens.ENTRANCE_RISE_DP.dp else 0.dp
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = rise.toPx() * (1f - p)
        },
    ) { content() }
}
