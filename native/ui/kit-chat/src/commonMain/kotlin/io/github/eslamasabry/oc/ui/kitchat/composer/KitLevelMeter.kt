package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Nine bars that light from the start edge as the voice gets louder; drawn, never recomposed (MOT rule). */
@Composable
public fun KitLevelMeter(level: () -> Float, modifier: Modifier = Modifier, active: Boolean = true) {
    val colors = LocalKitColors.current
    val on = colors.accent
    val off = colors.surface3
    val bars = KitLevelMeterRules.BARS
    val width = FlutterKitTokens.METER_BAR_WIDTH
    val tallest = FlutterKitTokens.METER_BAR_MAX
    val shortest = FlutterKitTokens.METER_BAR_MIN
    Box(
        modifier
            .kitMaxWidth(CapRole.Reading)
            .width((bars * width + (bars - 1) * Space.S1).dp)
            .height(tallest.dp)
            .clearAndSetSemantics {}
            .drawBehind {
                val barWidth = width.dp.toPx().roundToInt().toFloat()
                val gap = Space.S1.dp.toPx().roundToInt().toFloat()
                val center = (bars - 1) / 2f // F: lib/ui/kit/kit_level_meter.dart:145
                val now = level()
                for (i in 0 until bars) {
                    val span = (tallest - shortest) * abs(i - center) / center
                    val h = (shortest + span).dp.toPx().roundToInt().toFloat()
                    val slot = if (layoutDirection == LayoutDirection.Rtl) bars - 1 - i else i
                    drawRoundRect(
                        color = if (active && KitLevelMeterRules.isLit(i, now)) on else off,
                        // F: lib/ui/kit/kit_level_meter.dart:159
                        topLeft =
                            Offset(
                                (slot * (barWidth + gap)).roundToInt().toFloat(),
                                ((size.height - h) / 2).roundToInt().toFloat(),
                            ),
                        size = Size(barWidth, h),
                        // F: lib/ui/kit/kit_level_meter.dart:164
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                    )
                }
            },
    )
}
