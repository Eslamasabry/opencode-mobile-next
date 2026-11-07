package io.github.eslamasabry.oc.ui.kit.content.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.animateKitValue
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/**
 * A quota window's bar with a pace marker (Usage › Limits, B-159). The fill is what is LEFT, as every quota bar in
 * the app (KitWindowRow). [evenLeft] is where the fill would end if the window had been spent evenly so far
 * (1 − elapsed/length); a small upright tick marks it. A fill that stops short of the tick means the person is using
 * the window faster than an even pace, and the fill takes the attention colour so it reads without the tick too.
 *
 * States: on pace or behind (accent fill), ahead of pace (attention fill), no pace known (no tick)
 * Text: none; the host passes one sentence for [semantics] (value, reset and pace in words)
 * Cap: Fill
 * Motion: the fill eases STANDARD (about 200 ms) when a new reading moves it; Off = instant (animateKitValue)
 * Hidden: none (the host states the same numbers in words)
 */
@Composable
public fun KitLimitBar(
    left: Float,
    evenLeft: Float?,
    semantics: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val target = if (left.isFinite()) left.coerceIn(0f, 1f) else 0f
    val fraction = animateKitValue(target, KitPace.Standard)
    val pace = evenLeft?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val ahead = pace != null && LimitBarRules.ahead(target, pace)
    val track = if (compact) (tokens.progressBar - 1).coerceAtLeast(2) else tokens.progressBar + 2
    val overhang = if (compact) 2 else 3
    Canvas(
        modifier.fillMaxWidth().height((track + overhang * 2).dp).clearAndSetSemantics {
            contentDescription = semantics
            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
        }
    ) {
        val barTop = overhang.dp.toPx()
        val barHeight = track.dp.toPx()
        val radius = CornerRadius(barHeight / 2)
        drawRoundRect(colors.surface3, Offset(0f, barTop), Size(size.width, barHeight), radius)
        if (fraction > 0f) {
            val width = size.width * fraction
            drawRoundRect(
                if (ahead) colors.attention else colors.accent,
                topLeft = Offset(if (rtl) size.width - width else 0f, barTop),
                size = Size(width, barHeight),
                cornerRadius = radius,
            )
        }
        if (pace != null && size.width > 4.dp.toPx()) {
            val tick = 2.dp.toPx()
            val x = (if (rtl) size.width * (1f - pace) else size.width * pace)
                .coerceIn(tick / 2, size.width - tick / 2)
            drawRect(colors.text1, Offset(x - tick / 2, 0f), Size(tick, size.height))
        }
    }
}

/** Pure rules of [KitLimitBar], testable without Compose. */
public object LimitBarRules {
    /** Half a percent of slack, so a reading exactly on pace never flickers between colours. */
    public const val SLACK: Float = 0.005f

    /** True when less is left than an even pace would leave: the window is being spent faster than its length. */
    public fun ahead(left: Float, evenLeft: Float): Boolean = left < evenLeft - SLACK
}
