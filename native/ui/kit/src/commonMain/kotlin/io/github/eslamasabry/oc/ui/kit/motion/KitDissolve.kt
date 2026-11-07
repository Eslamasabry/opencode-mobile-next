package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * A state change that dissolves (owner reference 2026-11-07): the old content blurs and fades out while the new one
 * sharpens in, for whole-block changes — placeholder rows → content, an empty search → results, one setup step → the
 * next. The blur is a RenderEffect on API 31+ and on desktop; below API 31 it is a plain cross-fade. The leaving
 * content keeps its state while it fades, ignores touches and is hidden from TalkBack. The size jumps (no layout
 * animation, MOT-5); put it where the block's size is the page's.
 *
 * States: at rest, dissolving
 * Text: none (content decides)
 * Cap: none
 * Motion: fade on the effects springs + 8 dp blur at Full; Calm = cross-fade; Off = instant (class Fade)
 */
@Composable
public fun <T> KitDissolve(
    target: T,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit,
) {
    val state = KitMotion.state
    if (state.reduced) {
        Box(modifier, contentAlignment = alignment) { key(target) { content(target) } }
        return
    }
    val specs = KitMotion.specs
    val blurs = state.travels
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = alignment,
        transitionSpec = {
            (fadeIn(specs.effects()) togetherWith fadeOut(specs.effectsFast()))
                .using(SizeTransform(clip = false) { _, _ -> snap<IntSize>() })
        },
        label = "kit.dissolve",
    ) { shown ->
        val blur = if (blurs) motionBlur(DISSOLVE_BLUR) else 0.dp
        Box(
            Modifier.kitLeaving(transition.targetState == EnterExitState.PostExit)
                .blur(blur, BlurredEdgeTreatment.Unbounded),
        ) { content(shown) }
    }
}

/** How soft a dissolving block gets at its faintest. */
public val DISSOLVE_BLUR: androidx.compose.ui.unit.Dp = 8.dp
