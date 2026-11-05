package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.*

public enum class KitAnswerTone { Ok, Paused, Working, NeedsYou, Off }
public enum class KitAnswerScale { Large, Calm, Compact }

/** The centered status answer from kit_status_answer.dart; the caller owns observed facts and countdowns. */
@Composable
public fun KitStatusAnswer(
    answer: String,
    modifier: Modifier = Modifier,
    tone: KitAnswerTone? = null,
    line: String? = null,
    scale: KitAnswerScale = KitAnswerScale.Large,
    action: KitAction? = null,
) {
    val tokens = LocalKitTokens.current
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth()) {
        val margin = if (maxWidth < 360.dp) tokens.gutter else tokens.gutter * 2 // F: lib/ui/kit/kit_status_answer.dart:287,370
        Column(Modifier.fillMaxWidth().padding(horizontal = margin.dp).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally) {
            tone?.let {
                AnswerMark(it)
                Spacer(Modifier.height(Space.S4.dp))
            }
            AnswerWords(answer, scale)
            line?.let {
                Spacer(Modifier.height(if (scale == KitAnswerScale.Large) Space.S3.dp else Space.S2.dp))
                KitText(it, role = TypeRole.Body, tone = io.github.eslamasabry.oc.ui.design.type.TextTone.Secondary, textAlign = TextAlign.Center)
            }
            action?.let {
                Spacer(Modifier.height(Space.S4.dp))
                KitButton(it, role = KitButtonRole.Tertiary)
            }
        }
    }
}

internal fun statusAnswerSize(scale: KitAnswerScale, firm: Boolean): Int = when (scale) {
    KitAnswerScale.Large -> if (firm) 40 else 32 // F: lib/ui/kit/kit_status_answer.dart:129,131
    KitAnswerScale.Calm -> 28 // F: lib/ui/kit/kit_status_answer.dart:118
    KitAnswerScale.Compact -> 24 // F: lib/ui/kit/kit_status_answer.dart:121
}

@Composable
private fun AnswerWords(text: String, scale: KitAnswerScale) {
    val density = LocalDensity.current
    val style = LocalKitStyle.current
    val firm = style.spec.tokens.type.firmWeights
    val size = statusAnswerSize(scale, firm)
    val height = when (scale) {
        KitAnswerScale.Large -> if (firm) 46 else 38 // F: lib/ui/kit/kit_status_answer.dart:140,141
        KitAnswerScale.Calm -> 34 // F: lib/ui/kit/kit_status_answer.dart:142
        KitAnswerScale.Compact -> 30 // F: lib/ui/kit/kit_status_answer.dart:143
    }
    val tracking = when (scale) {
        KitAnswerScale.Large -> if (firm) -1.1f else -.64f // F: lib/ui/kit/kit_status_answer.dart:146,147
        KitAnswerScale.Calm -> -.6f // F: lib/ui/kit/kit_status_answer.dart:148
        KitAnswerScale.Compact -> -.4f // F: lib/ui/kit/kit_status_answer.dart:149
    }
    val base = LocalKitTypography.current.style(TypeRole.LargeTitle)
    val colors = LocalKitColors.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val scaling = density.fontScale.coerceAtMost(if (maxWidth < 300.dp) 1.2f else 1.5f) // F: lib/ui/kit/kit_status_answer.dart:119,324,330
        val target = base.copy(fontSize = (size * scaling).sp, lineHeight = (height * scaling).sp,
            letterSpacing = (tracking * scaling).sp, textAlign = TextAlign.Center, color = colors.text1)
        val width = with(density) { maxWidth.roundToPx() }
        val longest = text.split(' ').maxOfOrNull { measurer.measure(it, target, softWrap = false).size.width } ?: 0
        val factor = if (longest > width) width.toFloat() / longest else 1f
        val fitted = target.copy(fontSize = (target.fontSize.value * factor).sp, lineHeight = (target.lineHeight.value * factor).sp)
        val lines = measurer.measure(text, fitted, constraints = Constraints(maxWidth = width)).lineCount
        var high = width
        var low = maxOf(width / 2, minOf(longest, width)) // F: lib/ui/kit/kit_status_answer.dart:372
        if (lines > 1) repeat(12) { // F: lib/ui/kit/kit_status_answer.dart:371
            val mid = (low + high) / 2 // F: lib/ui/kit/kit_status_answer.dart:372
            if (measurer.measure(text, fitted, constraints = Constraints(maxWidth = mid)).lineCount <= lines) high = mid else low = mid
        }
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            BasicText(text, Modifier.width(with(density) { high.toDp() }).semantics { heading() }, style = fitted)
        }
    }
    }
}

@Composable
private fun AnswerMark(tone: KitAnswerTone) {
    val colors = LocalKitColors.current
    val edge = kitFocusRingWidth()
    val flat = LocalKitStyle.current.style == Style.Solid
    Box(Modifier.size(32.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) { // F: lib/ui/kit/kit_status_answer.dart:407
    if (tone == KitAnswerTone.Working) {
        KitWorkingMark(colors.text2, LocalKitTokens.current.smallIcon.dp)
        return@Box
    }
    val lamp = when (tone) {
        KitAnswerTone.Ok -> colors.success
        KitAnswerTone.NeedsYou -> colors.attention
        else -> null
    }
    if (!flat && lamp != null) Canvas(Modifier.fillMaxSize().blur(18.dp, BlurredEdgeTreatment.Unbounded)) { // F: lib/ui/kit/kit_status_answer.dart:427
        drawCircle(lamp.copy(alpha = if (colors.dark) .45f else .30f), 14.dp.toPx() / 2 + 2.dp.toPx()) // F: lib/ui/kit/kit_status_answer.dart:405,426,428,448
    }
    Canvas(Modifier.fillMaxSize()) {
        val dot = 14.dp.toPx() // F: lib/ui/kit/kit_status_answer.dart:405
        val radius = dot / 2 // F: lib/ui/kit/kit_status_answer.dart:448
        when (tone) {
            KitAnswerTone.Ok -> drawCircle(colors.success, radius)
            KitAnswerTone.NeedsYou -> drawCircle(colors.attention, radius)
            KitAnswerTone.Working -> drawCircle(colors.text2, radius)
            KitAnswerTone.Off -> drawCircle(colors.text3, radius, style = Stroke(edge.toPx()))
            KitAnswerTone.Paused -> {
                val bar = 4.dp.toPx() // F: lib/ui/kit/kit_status_answer.dart:406
                val gap = Space.S1.dp.toPx()
                for (i in 0..1) drawRoundRect(colors.text2,
                    topLeft = Offset(center.x - bar - gap / 2 + i * (bar + gap), center.y - radius), // F: lib/ui/kit/kit_status_answer.dart:448
                    size = Size(bar, dot), cornerRadius = CornerRadius(bar / 2)) // F: lib/ui/kit/kit_status_answer.dart:448
            }
        }
    }
}
}
