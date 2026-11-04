package io.github.eslamasabry.oc.ui.kit.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.color.SignalPalette
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.ceil

/** Quiet answers, as well as the primary answer, are hardware keys within the request's paper. */
public val LocalKitSignalInSlip: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf {
    false
}

/** Printed request paper below a printer slot; outside Signal this only arranges its content. */
@Composable
public fun KitSignalSlip(
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    endsWithTap: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = LocalKitColors.current
    if (LocalKitAppearance.current.style != KitSurfaceStyle.Signal) {
        Box(modifier) { content() }
        return
    }
    val p = SignalPalette.of(colors.dark)
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth()
                .height(5.dp)
                .background(Color(p.head.v), RoundedCornerShape(5.dp))
        )
        val focus = if (focused) Modifier.border(2.dp, colors.accent) else Modifier
        Box(
            Modifier.fillMaxWidth()
                .clip(SlipEdge)
                .background(Color(p.slip.v))
                .drawBehind {
                    val ink = Color(p.slipEdge.v)
                    drawRect(ink, size = Size(size.width, 1f))
                    drawRect(ink, size = Size(1f, size.height))
                    drawRect(
                        ink,
                        topLeft = Offset(size.width - 1f, 0f),
                        size = Size(1f, size.height),
                    )
                }
                .then(focus)
                .padding(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = if (endsWithTap) 6.dp else 22.dp,
                )
        ) {
            CompositionLocalProvider(LocalKitSignalInSlip provides true) { content() }
        }
    }
}

private object SlipEdge : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val body = Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
        val bites = Path()
        val pitch = with(density) { 10.dp.toPx() }
        val radius = with(density) { 4.dp.toPx() }
        val count = ceil(size.width / pitch).toInt()
        val start = (size.width - count * pitch) / 2
        repeat(count) { i ->
            val x = start + pitch * (i + .5f)
            bites.addOval(
                androidx.compose.ui.geometry.Rect(
                    x - radius,
                    size.height - radius,
                    x + radius,
                    size.height + radius,
                )
            )
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, bites))
    }
}

/**
 * Engraved attention label. Arabic uses the original words and never letter spacing or case
 * conversion.
 */
@Composable
public fun KitSignalNeedsYou(text: String, waiting: Boolean = true, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val signal = LocalKitAppearance.current.style == KitSurfaceStyle.Signal
    val p = SignalPalette.of(colors.dark)
    val ink = if (signal && waiting) Color(p.alertInk.v) else colors.text2
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val base = LocalKitTypography.current.style(TypeRole.Caption)
    val app = LocalKitAppDensity.current ?: LocalDensity.current
    val target =
        TextScalePolicy.frameDp(base.fontSize.value, with(app) { base.fontSize.toDp().value })
    val scale = target / base.fontSize.value
    val style =
        base.copy(
            color = ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = target.sp,
            lineHeight = (base.lineHeight.value * scale).sp,
            letterSpacing = if (signal && !rtl) (target * .12f).sp else 0.sp,
        )
    CompositionLocalProvider(LocalDensity provides Density(app.density, 1f)) {
        BasicText(
            if (signal && !rtl) text.uppercase() else text,
            modifier.clearAndSetSemantics { contentDescription = text },
            style = style,
        )
    }
}

/**
 * Signal's style preview is always drawn as Signal, even while another style is in use; mirrored in
 * Arabic.
 */
@Composable
public fun KitSignalSample(modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val p = SignalPalette.of(colors.dark)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(4.dp)).clearAndSetSemantics {}
    ) {
        fun x(v: Float): Float = if (rtl) size.width - v else v
        drawRect(Color(p.ground.v))
        val track = 18.dp.toPx()
        val ys = listOf(size.height * .22f, size.height * .5f, size.height * .78f)
        drawLine(Color(p.track.v), Offset(x(track), 0f), Offset(x(track), ys[1]), 2.dp.toPx())
        var y = ys[1]
        while (y < size.height) {
            drawLine(
                Color(p.siding.v),
                Offset(x(track), y),
                Offset(x(track), minOf(y + 4.dp.toPx(), size.height)),
                1.dp.toPx(),
            )
            y += 8.dp.toPx()
        }
        ys.forEachIndexed { index, at ->
            if (index < 2) {
                drawCircle(Color(p.head.v), 7.dp.toPx(), Offset(x(track), at))
                drawCircle(
                    Color(if (index == 0) SignalPalette.LampRed.v else SignalPalette.LampGreen.v),
                    3.dp.toPx(),
                    Offset(x(track), at),
                )
            } else {
                drawCircle(
                    colors.text2,
                    4.dp.toPx(),
                    Offset(x(track), at),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            drawLine(
                colors.text1,
                Offset(x(track + 14.dp.toPx()), at),
                Offset(x(size.width - (60 + index * 10).dp.toPx()), at),
                4.dp.toPx(),
            )
        }
        val keyX = if (rtl) 8.dp.toPx() else size.width - 42.dp.toPx()
        drawRect(
            Color(p.head.v),
            Offset(keyX, ys[0] - 7.dp.toPx()),
            Size(34.dp.toPx(), 14.dp.toPx()),
        )
    }
}
