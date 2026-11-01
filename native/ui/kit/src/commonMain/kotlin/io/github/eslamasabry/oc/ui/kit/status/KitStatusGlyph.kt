package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusGlyph
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * The tone glyph of a status (plan 06 §4.10): drawn, decorative (the words carry the meaning), and grown with text up
 * to 1.5×. A stand-in until the Phosphor glyphs land with `KitIcon` (K0); then it draws the same glyph ids.
 * None of the shapes has a direction, so nothing mirrors in RTL.
 */
@Composable
internal fun KitStatusGlyph(glyph: StatusGlyph, color: Color, modifier: Modifier = Modifier) {
    val size = LocalKitTokens.current.smallIcon * minOf(kitTextScale(), TextScalePolicy.ICON_CAP)
    Canvas(modifier.size(size.dp).clearAndSetSemantics {}) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val inset = w * 0.12f
        val box = Size(w - 2 * inset, w - 2 * inset)
        when (glyph) {
            StatusGlyph.EmptyRing -> drawCircle(color, radius = box.width / 2, style = stroke)
            StatusGlyph.Sync -> {
                drawArc(color, 200f, 250f, useCenter = false, topLeft = Offset(inset, inset), size = box, style = stroke)
                drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.5f, w * 0.5f))
            }
            StatusGlyph.Check -> {
                val p = Path().apply {
                    moveTo(w * 0.22f, w * 0.52f)
                    lineTo(w * 0.42f, w * 0.72f)
                    lineTo(w * 0.80f, w * 0.30f)
                }
                drawPath(p, color, style = stroke)
            }
            StatusGlyph.Warning -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, w * 0.14f)
                    lineTo(w * 0.88f, w * 0.84f)
                    lineTo(w * 0.12f, w * 0.84f)
                    close()
                }
                drawPath(p, color, style = stroke)
                drawLine(color, Offset(w * 0.5f, w * 0.40f), Offset(w * 0.5f, w * 0.60f), stroke.width, StrokeCap.Round)
                drawCircle(color, radius = stroke.width * 0.6f, center = Offset(w * 0.5f, w * 0.72f))
            }
            StatusGlyph.Error -> {
                drawCircle(color, radius = box.width / 2, style = stroke)
                drawLine(color, Offset(w * 0.5f, w * 0.30f), Offset(w * 0.5f, w * 0.56f), stroke.width, StrokeCap.Round)
                drawCircle(color, radius = stroke.width * 0.6f, center = Offset(w * 0.5f, w * 0.68f))
            }
        }
    }
}
