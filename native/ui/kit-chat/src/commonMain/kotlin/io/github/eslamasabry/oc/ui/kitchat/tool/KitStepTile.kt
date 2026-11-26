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
import androidx.compose.ui.unit.dp
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
@Suppress("LongParameterList")
@Composable
internal fun KitStepTile(
    icon: ImageVector,
    look: KitStepNodeLook,
    size: Dp,
    modifier: Modifier = Modifier,
    glyphColor: Color? = null,
    glyphScale: Float = 1f,
    animate: Boolean = true,
) {
    val colors = LocalKitColors.current
    val live = look == KitStepNodeLook.Running || look == KitStepNodeLook.Background
    // F: lib/ui/kit/chat/kit_step_light.dart:147-149 glyphColor
    val glyph =
        glyphColor ?: when {
            live -> colors.accent
            look == KitStepNodeLook.Queued -> colors.text3
            // Owner 2026-11-14 ("icons are tiny and unrecognisable"): a step's glyph is drawn near the words' own ink,
            // so a read, a search, an edit and a command tell apart at arm's length.
            else -> colors.text1.copy(alpha = colors.text1.alpha * GLYPH_INK)
        }
    // In an outline (the step light, queued, waiting) the glyph keeps clear of it: a magnifier's handle reaching into
    // the corner touched the light's rounded square at the full share (owner 2026-11-15); the terminal's box had room.
    val framed = look != KitStepNodeLook.Plain
    Box(modifier.size(size).testTag("kit-step-node"), contentAlignment = Alignment.Center) {
        val share = if (framed) GLYPH_SHARE_FRAMED else GLYPH_SHARE
        KitToolGlyph(icon, glyph, size * share * glyphScale, Modifier.testTag(STEP_GLYPH_TAG))
        when (look) {
            KitStepNodeLook.Running,
            KitStepNodeLook.Background ->
                KitStepLight(
                    if (look == KitStepNodeLook.Background) KitStepLightState.Background else KitStepLightState.Working,
                    // F: lib/ui/kit/chat/kit_step_light.dart:288 calm background light at .6
                    if (look == KitStepNodeLook.Background) colors.accent.copy(alpha = colors.accent.alpha * .6f) else colors.accent, // F: lib/ui/kit/chat/kit_step_light.dart:284
                    size,
                    animate = animate && !KitMotion.state.reduced,
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
 * The glyph's share of its node. Flutter's 0.6 sat in a tile; bare, the glyph fills most of the square (owner
 * 2026-11-14: "icons are tiny"; measured in the pictures a 17 dp glyph drew about 12 dp of ink): the kit's medium icon
 * (22 dp) in the 24 dp node, about 18 dp of ink with a 1.4 dp stroke, still clear of the step light's outline.
 */
internal const val GLYPH_SHARE: Float = 22f / 24f

/**
 * The glyph's share of a node drawn in an outline: about 18 dp in the 24 dp node, so even a glyph that reaches into its
 * corners (the magnifier's handle) stands about 2 dp clear of the outline's rounded corner, as the terminal does.
 */
internal const val GLYPH_SHARE_FRAMED: Float = 18f / 24f

/** A node's glyph, for tests. */
internal const val STEP_GLYPH_TAG: String = "kit-step-glyph"

/** How strong a still step's glyph is against the words' primary ink. */
internal const val GLYPH_INK: Float = .82f

/** A still outline inside the tile's own edge (Dart `_Look.still`). */
@Composable
internal fun StillOutline(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = KitStepLightLap.FILL_STROKE.dp.toPx()
        val inset = stroke / 2f // F: lib/ui/kit/chat/kit_step_light.dart:322
        val s = this.size.minDimension
        val r = maxOf(0f, stepTileRadius(s) - inset)
        drawRoundRect(color, Offset(inset, inset), Size(s - stroke, s - stroke), CornerRadius(r), style = Stroke(stroke))
    }
}
