package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** How full the context window is (Dart `KitContextRing` levels, B-CHAT-057): calm, warning, full. */
public enum class KitContextLevel {
    Calm,
    Warning,
    Full,
}

/**
 * The context ring (Dart `KitContextRing`, B-CHAT-019/057): a thin circle filled to [percent] (0..100, whole) in the
 * level's colour: calm = the quiet text colour, warning = attention, full = attention line. [label] is what a screen
 * reader hears ("Context 42 % used"). The fill eases with the kit's quick motion; reduced motion jumps. Decorative
 * geometry only: the words are always the caller's.
 */
@Composable
public fun KitContextRing(
    percent: Int,
    level: KitContextLevel,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp, // F: lib/ui/kit/kit_context_ring.dart:115
) {
    val colors = LocalKitColors.current
    val target = percent.coerceIn(0, 100) / 100f
    val sweep by animateFloatAsState(target, KitMotion.specs.quick(), label = "context-ring")
    val track = colors.text3.copy(alpha = 0.35f) // F: lib/ui/kit/kit_context_ring.dart:328
    val fill =
        when (level) {
            KitContextLevel.Calm -> colors.accent
            KitContextLevel.Warning -> colors.attention
            KitContextLevel.Full -> colors.text1
        }
    val large = size == 64.dp // F: lib/ui/kit/kit_context_ring.dart:116
    Canvas(modifier.size(size).semantics { contentDescription = label }) {
        val stroke = if (large) 6.dp.toPx() else this.size.minDimension * 2.5f / 24f // F: lib/ui/kit/kit_context_ring.dart:115-116
        val radius = if (large) 24.dp.toPx() else this.size.minDimension * 9f / 24f // F: lib/ui/kit/kit_context_ring.dart:115-116
        val arcSize = androidx.compose.ui.geometry.Size(radius + radius, radius + radius)
        val topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (sweep > 0f)
            drawArc(fill, -90f, 360f * sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
