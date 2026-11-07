package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** What a [KitStatusAnswer] says about the thing it answers for (Dart `KitAnswerTone`). */
public enum class KitAnswerTone { Ok, Paused, Working, NeedsYou, Off }

/** How large a [KitStatusAnswer] sets its answer (Dart `KitAnswerScale`): 40/46, 28/34, 24/30. */
public enum class KitAnswerScale { Large, Calm, Compact }

/**
 * The one big plain answer a status page opens on, centred (Dart `KitStatusAnswer`, lib/ui/kit/kit_status_answer.dart):
 * "Is it OK?" answered in large type, one small line under it, and at most one quiet text action. The mark above the
 * words carries the [tone]; the words always say it too. Both the answer and the line are set as headlines: grown with
 * the person's text (the answer up to 1.5×), never split inside a word, and balanced so no word is left alone on the
 * last line. A new answer cross-fades in over STANDARD with a short rise (instantly under reduced motion); the answer
 * is one live region. A countdown line is the caller's: it passes the current [line].
 *
 * States: none — its tones are variants of one answer
 * Text: reading (answer capped at 1.5×)
 * Cap: Conversation (700)
 * Motion: cross-fade + 4 % rise over STANDARD; Off = instant
 * Replaces: KitStatusAnswer (lib/ui/kit/kit_status_answer.dart)
 */
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
    val specs = KitMotion.specs
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // Wide margins keep the answer to a calm column; a narrow phone gives the words the room instead.
        val margin = if (maxWidth < 360.dp) tokens.gutter else tokens.gutter + tokens.gutter // F: lib/ui/kit/kit_status_answer.dart:274,276,287
        AnimatedContent(
            targetState = tone to answer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = margin.dp).semantics { liveRegion = LiveRegionMode.Polite },
            transitionSpec = {
                (fadeIn(specs.enter()) + slideInVertically(specs.enter()) { (it * .04f).toInt() }) togetherWith fadeOut(specs.exit()) // F: lib/ui/kit/kit_status_answer.dart:261
            },
            contentAlignment = Alignment.TopCenter,
            label = "kit.statusAnswer",
        ) { (shownTone, shownAnswer) ->
            val calm = scale != KitAnswerScale.Large
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (shownTone != null) {
                    AnswerMark(shownTone)
                    Spacer(Modifier.height(Space.S4.dp))
                }
                AnswerText(shownAnswer, scale, line = false, Modifier.semantics { heading() })
                if (line != null) {
                    Spacer(Modifier.height(if (calm) Space.S2.dp else Space.S3.dp))
                    AnswerText(line, scale, line = true)
                }
                if (action != null) {
                    Spacer(Modifier.height(Space.S4.dp))
                    KitButton(action, role = KitButtonRole.Tertiary)
                }
            }
        }
    }
}

/** The answer's size in its scale; Solid's large answer is the quieter 32 (kit_status_answer.dart:116-131). */
internal fun answerSize(scale: KitAnswerScale, firm: Boolean): Float =
    when (scale) {
        KitAnswerScale.Large -> if (firm) 40f else 32f // F: lib/ui/kit/kit_status_answer.dart:117,132
        KitAnswerScale.Calm -> 28f // F: lib/ui/kit/kit_status_answer.dart:118
        KitAnswerScale.Compact -> 24f // F: lib/ui/kit/kit_status_answer.dart:121
    }

/** The answer's line height in dp at its size (kit_status_answer.dart:138-143). */
internal fun answerLineHeight(scale: KitAnswerScale, firm: Boolean): Float =
    when (scale) {
        KitAnswerScale.Large -> if (firm) 46f else 38f // F: lib/ui/kit/kit_status_answer.dart:140,141
        KitAnswerScale.Calm -> 34f // F: lib/ui/kit/kit_status_answer.dart:142
        KitAnswerScale.Compact -> 30f // F: lib/ui/kit/kit_status_answer.dart:143
    }

/** The answer's tracking (kit_status_answer.dart:144-149). */
internal fun answerTracking(scale: KitAnswerScale, firm: Boolean): Float =
    when (scale) {
        KitAnswerScale.Large -> if (firm) -1.1f else -0.64f // F: lib/ui/kit/kit_status_answer.dart:146,147
        KitAnswerScale.Calm -> -0.6f // F: lib/ui/kit/kit_status_answer.dart:148
        KitAnswerScale.Compact -> -0.4f // F: lib/ui/kit/kit_status_answer.dart:149
    }

/**
 * The narrowest width that keeps [lines] lines, by twelve halvings between max(max / 2, widest word) and [max]
 * (kit_status_answer.dart:366-385); [lineCount] measures the words at a width.
 */
internal fun balancedWidth(max: Float, widest: Float, lines: Int, lineCount: (Float) -> Int): Float {
    if (lines <= 1) return max
    var high = max
    var low = min(high, max(max / 2f, ceil(widest))) // F: lib/ui/kit/kit_status_answer.dart:370
    repeat(12) { // F: lib/ui/kit/kit_status_answer.dart:371
        val mid = (low + high) / 2f // F: lib/ui/kit/kit_status_answer.dart:372
        if (lineCount(mid) <= lines) high = mid else low = mid
    }
    return ceil(high)
}

@Composable
private fun AnswerText(text: String, scale: KitAnswerScale, line: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val firm = LocalKitStyle.current.spec.tokens.type.firmWeights
    val fontScale = density.fontScale
    val base: TextStyle =
        if (line) {
            typography.style(TypeRole.Body).copy(color = colors.text2)
        } else {
            val size = answerSize(scale, firm)
            typography.style(TypeRole.LargeTitle).copy(
                fontSize = size.sp,
                letterSpacing = answerTracking(scale, firm).sp,
                color = colors.text1,
            )
        }
    val unscaled = Density(density.density, 1f)
    // The words are laid out with no further text scaling (Dart textScaler: noScaling): sizes are set here.
    CompositionLocalProvider(LocalDensity provides unscaled) {
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val maxPx = constraints.maxWidth.toFloat()
            val baseSize = base.fontSize.value
            val size = if (line) answerSize(scale, firm) else baseSize
            val scaled = if (line) baseSize * fontScale else size * min(fontScale, 1.5f) // F: lib/ui/kit/kit_status_answer.dart:119
            val narrow = size * min(fontScale, 1.2f) // F: lib/ui/kit/kit_status_answer.dart:324
            var fontSize = if (!line && maxWidth < 300.dp) narrow else scaled // F: lib/ui/kit/kit_status_answer.dart:330
            val ratio = if (line) base.lineHeight.value / baseSize else answerLineHeight(scale, firm) / size
            fun style(fs: Float) = base.copy(fontSize = fs.sp, lineHeight = (fs * ratio).sp, textAlign = TextAlign.Center)
            fun widestWord(s: TextStyle) =
                text.split(' ').maxOfOrNull { measurer.measure(it, s, softWrap = false).size.width.toFloat() } ?: 0f
            var widest = widestWord(style(fontSize))
            if (widest > maxPx) {
                fontSize = floor(fontSize * maxPx / widest)
                widest = widestWord(style(fontSize))
            }
            val final = style(fontSize)
            fun count(w: Float) = measurer.measure(text, final, constraints = Constraints(maxWidth = max(1, w.toInt()))).lineCount
            val width = balancedWidth(maxPx, widest, count(maxPx), ::count)
            BasicText(text, Modifier.width(with(density) { width.toDp() }), style = final)
        }
    }
}

@Composable
private fun AnswerMark(tone: KitAnswerTone) {
    val colors = LocalKitColors.current
    val edge = kitFocusRingWidth()
    // Solid: the lamp is one flat dot, no glow. Other styles keep its soft halo.
    val flat = LocalKitStyle.current.style == Style.Solid
    Box(Modifier.size(32.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) { // F: lib/ui/kit/kit_status_answer.dart:407
        if (tone == KitAnswerTone.Working) {
            KitWorkingMark(colors.text2, LocalKitTokens.current.smallIcon.dp)
            return@Box
        }
        val lamp = when (tone) {
            KitAnswerTone.Ok -> colors.success
            KitAnswerTone.NeedsYou -> colors.attention
            else -> null
        }
        if (!flat && lamp != null) {
            Canvas(Modifier.fillMaxSize().blur(18.dp, BlurredEdgeTreatment.Unbounded)) { // F: lib/ui/kit/kit_status_answer.dart:427
                drawCircle(lamp.copy(alpha = if (colors.dark) .45f else .30f), 14.dp.toPx() / 2 + 2.dp.toPx()) // F: lib/ui/kit/kit_status_answer.dart:405,426,428
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val dot = 14.dp.toPx() // F: lib/ui/kit/kit_status_answer.dart:405
            val radius = dot / 2 // F: lib/ui/kit/kit_status_answer.dart:448
            when (tone) {
                KitAnswerTone.Ok, KitAnswerTone.NeedsYou -> drawCircle(lamp!!, radius)
                KitAnswerTone.Off -> drawCircle(colors.text3, radius - edge.toPx() / 2, style = Stroke(edge.toPx())) // F: lib/ui/kit/kit_status_answer.dart:448
                KitAnswerTone.Paused -> {
                    val bar = 4.dp.toPx() // F: lib/ui/kit/kit_status_answer.dart:406
                    val gap = Space.S1.dp.toPx()
                    for (i in 0..1) {
                        drawRoundRect(
                            colors.text2,
                            topLeft = Offset(center.x - bar - gap / 2 + i * (bar + gap), center.y - radius), // F: lib/ui/kit/kit_status_answer.dart:448
                            size = Size(bar, dot),
                            cornerRadius = CornerRadius(bar / 2), // F: lib/ui/kit/kit_status_answer.dart:448
                        )
                    }
                }
                KitAnswerTone.Working -> Unit
            }
        }
    }
}
