package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitTextScale
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.theme.*
import kotlin.math.ceil
import kotlin.math.floor

/** Inbox's two status-answer variants from `kit_status_answer.dart`: OK and unread source. */
@Composable
public fun KitInboxEmptyAnswer(
    answer: String,
    modifier: Modifier = Modifier,
    uncertain: Boolean = false,
    line: String? = null,
    retry: KitAction? = null,
) {
    val tokens = LocalKitTokens.current
    val colors = LocalKitColors.current
    val style = LocalKitStyle.current
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Conversation).fillMaxWidth()) {
        val margin = tokens.gutter
        KitSwap(uncertain to answer, Modifier.fillMaxWidth().padding(horizontal = margin.dp), pace = KitPace.Standard) { (shownUncertain, shownAnswer) ->
            Column(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(32.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) { // F: lib/ui/kit/kit_status_answer.dart:407
                    val dot = 14.dp // F: lib/ui/kit/kit_status_answer.dart:405
                    if (shownUncertain) Box(
                        Modifier.size(dot).border(kitFocusRingWidth(), colors.text3, CircleShape).testTag("inbox-answer-off")
                    ) else {
                        if (style.style != Style.Solid) Box(
                            Modifier.size(dot + 2.dp + 2.dp) // F: lib/ui/kit/kit_status_answer.dart:428
                                .blur(18.dp / 2, BlurredEdgeTreatment.Unbounded) // F: lib/ui/kit/kit_status_answer.dart:427-428
                                .background(colors.success.copy(alpha = if (colors.dark) .45f else .30f), CircleShape) // F: lib/ui/kit/kit_status_answer.dart:426
                        )
                        Box(Modifier.size(dot).background(colors.success, CircleShape).testTag("inbox-answer-ok"))
                    }
                }
                Spacer(Modifier.height(Space.S4.dp))
                BalancedAnswer(shownAnswer, line = false, modifier = Modifier.semantics { heading() }.testTag("inbox-answer-title"))
                line?.let {
                    Spacer(Modifier.height(Space.S3.dp))
                    BalancedAnswer(it, line = true)
                }
                retry?.let { action ->
                    Spacer(Modifier.height(Space.S4.dp))
                    KitButton(action, role = KitButtonRole.Tertiary)
                }
            }
        }
    }
}

/** Flutter's full-word fit and balanced lines, including the status answer's separate text-scale cap. */
@Composable
private fun BalancedAnswer(text: String, line: Boolean, modifier: Modifier = Modifier) {
    val typography = LocalKitTypography.current
    val type = LocalKitStyle.current.spec.tokens.type
    val colors = LocalKitColors.current
    val scale = kitTextScale()
    val density = LocalDensity.current
    val base = typography.style(if (line) TypeRole.Body else TypeRole.LargeTitle)
    val firm = type.firmWeights
    val size = if (line) base.fontSize.value else if (firm) 40f else 32f // F: lib/ui/kit/kit_status_answer.dart:117,132
    val height = if (line) base.lineHeight.value / base.fontSize.value else if (firm) 46f / size else 38f / size // F: lib/ui/kit/kit_status_answer.dart:140-141
    val fallbackWeight = if (!line && firm) 650 else requireNotNull(base.fontWeight).weight // F: lib/ui/kit/kit_text.dart:442
    val weight = (if (line) type.bodyWeight else type.largeTitleWeight ?: type.displayWeight) ?: fallbackWeight
    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
        val measure = rememberTextMeasurer()
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val grow = if (line) scale else scale.coerceAtMost(if (maxWidth < 300.dp) 1.2f else 1.5f) // F: lib/ui/kit/kit_status_answer.dart:119,324,330
            var fontSize = size * grow
            val tracking = if (line) base.letterSpacing else (if (firm) -1.1f else -.64f).sp // F: lib/ui/kit/kit_status_answer.dart:146-147
            fun textStyle() = base.copy(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * height).sp,
                fontWeight = FontWeight(weight + type.weightBoost.toInt()),
                letterSpacing = tracking,
                color = if (line) colors.text2 else colors.text1,
                textAlign = TextAlign.Center,
                textDirection = TextDirection.Content,
            )
            fun widestWord(): Float = text.split(' ').maxOfOrNull {
                measure.measure(AnnotatedString(it), textStyle(), softWrap = false).size.width.toFloat()
            } ?: 0f
            var width = constraints.maxWidth
            if (constraints.hasBoundedWidth) {
                var widest = widestWord()
                if (widest > width) {
                    fontSize = floor(fontSize * width / widest)
                    widest = widestWord()
                }
                val lines = measure.measure(AnnotatedString(text), textStyle(), constraints = Constraints(maxWidth = width)).lineCount
                if (lines > 1) {
                    var high = width.toFloat()
                    var low = minOf(high, maxOf(high / 2, ceil(widest))) // F: lib/ui/kit/kit_status_answer.dart:368
                    repeat(12) { // F: lib/ui/kit/kit_status_answer.dart:369
                        val mid = (low + high) / 2 // F: lib/ui/kit/kit_status_answer.dart:370
                        val count = measure.measure(AnnotatedString(text), textStyle(), constraints = Constraints(maxWidth = mid.toInt())).lineCount
                        if (count <= lines) high = mid else low = mid
                    }
                    width = ceil(high).toInt()
                }
            }
            BasicText(text, if (constraints.hasBoundedWidth) Modifier.width(with(LocalDensity.current) { width.toDp() }) else Modifier, style = textStyle())
        }
    }
}
