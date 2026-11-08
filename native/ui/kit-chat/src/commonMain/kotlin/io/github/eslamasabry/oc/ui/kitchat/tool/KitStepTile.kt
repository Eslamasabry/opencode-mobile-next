package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** How a step's node looks (Dart `KitStepNodeState`, the still looks of kit_step_light.dart). */
internal enum class KitStepNodeLook { Plain, Queued, Running, Background, Waiting }

/** The node's tile radius: the one number the tile and every outline share (Dart `_tileRadius`). */
internal fun stepTileRadius(size: Float): Float = size * .3f // F: lib/ui/kit/chat/kit_step_light.dart:14

/**
 * A step's node (Dart `_StepLight`): the step's [icon], bare — the owner's rule is no icons in boxes, so Flutter's
 * `surface3` tile is not drawn; the outline the light traces keeps the tile's square (radius 0.3 of its [size]). The
 * glyph is [GLYPH_SHARE] of the node, `text2`, `text3` when queued, the accent while it runs. A running step carries the
 * step light round the node and, on a timeline, down the rail from the step before to the next ([KitStepLight]; still
 * under reduced motion); a queued one a dim neutral outline; a waiting one the attention outline.
 */
@Composable
internal fun KitStepTile(
    icon: ImageVector,
    look: KitStepNodeLook,
    size: Dp,
    modifier: Modifier = Modifier,
    glyphColor: Color? = null,
    glyphScale: Float = 1f,
) {
    val colors = LocalKitColors.current
    val live = look == KitStepNodeLook.Running || look == KitStepNodeLook.Background
    // F: lib/ui/kit/chat/kit_step_light.dart:147-149 glyphColor
    val glyph =
        glyphColor ?: when {
            live -> colors.accent
            look == KitStepNodeLook.Queued -> colors.text3
            else -> colors.text2
        }
    Box(modifier.size(size).testTag("kit-step-node"), contentAlignment = Alignment.Center) {
        KitToolGlyph(icon, glyph, size * GLYPH_SHARE * glyphScale)
        when (look) {
            KitStepNodeLook.Running,
            KitStepNodeLook.Background ->
                KitStepLight(
                    if (look == KitStepNodeLook.Background) KitStepLightState.Background else KitStepLightState.Working,
                    // F: lib/ui/kit/chat/kit_step_light.dart:288 calm background light at .6
                    if (look == KitStepNodeLook.Background) colors.accent.copy(alpha = colors.accent.alpha * .6f) else colors.accent, // F: lib/ui/kit/chat/kit_step_light.dart:284
                    size,
                    animate = !KitMotion.state.reduced,
                    rail = LocalStepLightRail.current,
                    railColor = colors.text3,
                )
            // F: lib/ui/kit/chat/kit_step_light.dart:305 queued: text3 at .45, dim
            KitStepNodeLook.Queued -> StillOutline(colors.text3.copy(alpha = colors.text3.alpha * .45f), size) // F: lib/ui/kit/chat/kit_step_light.dart:303
            // F: lib/ui/kit/chat/kit_step_light.dart:303 waiting: the attention outline, held
            KitStepNodeLook.Waiting -> StillOutline(colors.attention, size)
            KitStepNodeLook.Plain -> Unit
        }
    }
}

/**
 * The glyph's share of its node. Flutter's 0.6 sat in a tile; bare, the glyph takes a little more of the square so it
 * reads at the same weight as the words beside it.
 */
internal const val GLYPH_SHARE: Float = .7f

/** A still outline inside the tile's own edge (Dart `_Look.still`). */
@Composable
internal fun StillOutline(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = KitStepLightLap.FILL_STROKE
        val inset = stroke / 2f // F: lib/ui/kit/chat/kit_step_light.dart:322
        val s = this.size.minDimension
        val r = maxOf(0f, stepTileRadius(s) - inset)
        drawRoundRect(color, Offset(inset, inset), Size(s - stroke, s - stroke), CornerRadius(r), style = Stroke(stroke))
    }
}
