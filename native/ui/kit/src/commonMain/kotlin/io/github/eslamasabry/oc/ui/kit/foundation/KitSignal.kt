package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.SignalPalette
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

public enum class KitSignalAspect { Stop, Go, Off }

/** A track at the logical start, mirrored once by the draw scope. */
@Composable
public fun Modifier.kitTrack(centre: Dp, live: Boolean): Modifier {
    val palette = SignalPalette.of(LocalKitColors.current.dark)
    return drawBehind {
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - centre.toPx() else centre.toPx()
        if (live) drawRect(Color(palette.track.v), Offset(x - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
        else {
            var top = 0f
            val dash = 6.dp.toPx(); val gap = 4.dp.toPx(); val stroke = 2.dp.toPx()
            while (top < size.height) {
                drawRect(Color(palette.siding.v), Offset(x - stroke / 2, top), Size(stroke, minOf(dash, size.height - top)))
                top += dash + gap
            }
        }
    }
}

/** The exact 16×26 railway head and 9/32-width lamp. Decorative; the row supplies its state words. */
@Composable
public fun KitSignalHead(aspect: KitSignalAspect, modifier: Modifier = Modifier, height: Dp = 26.dp) {
    val palette = SignalPalette.of(LocalKitColors.current.dark)
    val lamp = when (aspect) { KitSignalAspect.Stop -> SignalPalette.LampRed; KitSignalAspect.Go -> SignalPalette.LampGreen; KitSignalAspect.Off -> palette.lampOff }
    Canvas(modifier.size(height * (16f / 26f), height).clearAndSetSemantics {}) {
        drawRoundRect(Color(palette.head.v), cornerRadius = CornerRadius(size.width / 2f))
        val radius = size.width * 9f / 32f
        if (aspect != KitSignalAspect.Off) drawCircle(Color(lamp.v).copy(alpha = if (palette == SignalPalette.Night) .55f else .25f), radius + 2.dp.toPx())
        drawCircle(Color(lamp.v), radius)
    }
}

@Composable
public fun KitSignalRing(modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    Canvas(modifier.size(12.dp).clearAndSetSemantics {}) {
        drawCircle(colors.ground)
        drawCircle(colors.text2, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
}

public val LocalKitSignalSlip: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** A real printed slip, with the printer slot, three ruled sides and the half-round tear perforation. */
@Composable
public fun KitSignalSlip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = SignalPalette.of(LocalKitColors.current.dark)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(Color(palette.head.v)))
        Box(Modifier.fillMaxWidth().clip(SlipTear).background(Color(palette.slip.v)).drawBehind {
            val ink = Color(palette.slipEdge.v); val width = 1.dp.toPx()
            drawRect(ink, size = Size(size.width, width))
            drawRect(ink, size = Size(width, size.height))
            drawRect(ink, Offset(size.width - width, 0f), Size(width, size.height))
        }.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 22.dp)) {
            CompositionLocalProvider(LocalKitSignalSlip provides true) { content() }
        }
    }
}

private object SlipTear : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val body = Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
        val holes = Path()
        with(density) {
            val pitch = 10.dp.toPx(); val radius = 4.dp.toPx()
            val count = kotlin.math.ceil(size.width / pitch).toInt()
            val start = (size.width - count * pitch) / 2f
            repeat(count) { index ->
                val x = start + pitch * (index + .5f)
                holes.addOval(androidx.compose.ui.geometry.Rect(x - radius, size.height - radius, x + radius, size.height + radius))
            }
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, holes))
    }
}
