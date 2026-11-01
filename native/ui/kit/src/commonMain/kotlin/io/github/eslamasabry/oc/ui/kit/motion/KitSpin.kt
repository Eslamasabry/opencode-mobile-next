package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * Rotates its child to [turns] (0.5 = half a turn), clockwise in both directions (Dart `KitSpin`; plan 08 §5.15). The
 * rotation is paint only. First build is final.
 *
 * States: at rest, turning
 * Text: none
 * Cap: none
 * Motion: rotation on [pace] at Full; Calm = cross-fade between the two angles; Off = instant (class Travel)
 * Replaces: KitSpin (lib/ui/kit/motion/kit_motion_parts.dart)
 */
@Composable
public fun KitSpin(
    turns: Float,
    modifier: Modifier = Modifier,
    pace: KitPace = KitPace.Quick,
    content: @Composable () -> Unit,
) {
    SpinBody(turns, modifier, spring = false, pace = pace, content = content)
}

/**
 * The kit's one disclosure chevron (Dart `KitSpin.chevron`, decision D10): a vertical chevron in `text2`, pointing down
 * when folded and up when [expanded], so it never depends on the reading direction. Decorative: the host carries the
 * expanded state.
 *
 * Motion: 180° on `SpatialFast` at Full; Calm = glyph cross-fade; Off = instant (class Travel)
 */
@Composable
public fun KitChevron(expanded: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val size = LocalKitTokens.current.smallIcon.dp
    SpinBody(if (expanded) 0.5f else 0f, modifier, spring = true, pace = KitPace.Quick) {
        KitGlyph(KitPartGlyphs.ChevronDown, colors.text2, size)
    }
}

@Composable
private fun SpinBody(turns: Float, modifier: Modifier, spring: Boolean, pace: KitPace, content: @Composable () -> Unit) {
    val state = KitMotion.state
    if (state.reduced) {
        Box(modifier.graphicsLayer { rotationZ = turns * 360f }) { content() }
        return
    }
    if (!state.travels) {
        KitSwap(turns, modifier) { t -> Box(Modifier.graphicsLayer { rotationZ = t * 360f }) { content() } }
        return
    }
    val specs = KitMotion.specs
    val shown by animateFloatAsState(turns, if (spring) specs.spatialFast() else specs.pace(pace), label = "kit.spin")
    Box(modifier.graphicsLayer { rotationZ = shown * 360f }) { content() }
}
