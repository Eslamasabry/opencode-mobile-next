package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Back (a direction glyph: it mirrors in RTL, B-KIT-001) or Close (never mirrors). */
internal enum class ExitGlyph { Back, Close, Chevron }

/**
 * The frame's exit and switcher glyphs, drawn until the Phosphor glyphs land with `KitIcon` (K0). Decorative: the
 * control around it carries the label. Back and the chevron mirror by reading the draw scope's layout direction
 * (plan 06 §12.1 rule 2).
 */
@Composable
internal fun KitExitGlyph(glyph: ExitGlyph, color: Color, modifier: Modifier = Modifier) {
    val size = LocalKitTokens.current.smallIcon * minOf(kitTextScale(), TextScalePolicy.FRAME_CAP)
    Canvas(modifier.size(size.dp)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun x(v: Float) = if (rtl) w - v * w else v * w
        when (glyph) {
            ExitGlyph.Back -> {
                drawLine(color, Offset(x(0.16f), w / 2), Offset(x(0.84f), w / 2), stroke.width, StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(x(0.44f), w * 0.2f)
                        lineTo(x(0.16f), w / 2)
                        lineTo(x(0.44f), w * 0.8f)
                    },
                    color, style = stroke,
                )
            }
            ExitGlyph.Close -> {
                drawLine(color, Offset(w * 0.22f, w * 0.22f), Offset(w * 0.78f, w * 0.78f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, w * 0.22f), Offset(w * 0.22f, w * 0.78f), stroke.width, StrokeCap.Round)
            }
            ExitGlyph.Chevron ->
                drawPath(
                    Path().apply {
                        moveTo(x(0.38f), w * 0.22f)
                        lineTo(x(0.66f), w / 2)
                        lineTo(x(0.38f), w * 0.78f)
                    },
                    color, style = stroke,
                )
        }
    }
}
