package io.github.eslamasabry.oc.ui.kit.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

public enum class KitTrailMark { Done, Current, Waiting }

/** Flutter kit_progress_ring.dart: a real current-step measurement, steps, or a completed check. */
@Composable
public fun KitProgressRing(
    headline: String,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    value: Float? = null,
    done: Int = 0,
    of: Int = 0,
    finished: Boolean = false,
    halted: Boolean = false,
    caption: String? = null,
    trail: List<KitTrailMark> = emptyList(),
) {
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val target = ringFraction(value, done, of, finished)
    val arc = remember { Animatable(target) }
    val motion = KitMotion.specs
    LaunchedEffect(target, motion.state.reduced) {
        if (target < arc.value || motion.state.reduced) arc.snapTo(target)
        else arc.animateTo(target, motion.enter())
    }
    val whole = Numbers.format(if (value == null) done else floor(target * 100).toInt()) // F: lib/ui/kit/kit_progress_ring.dart:181
    val total = Numbers.format(of)
    val spoken = when {
        finished -> stringResource(Res.string.kitProgressRingFinished)
        value != null -> stringResource(Res.string.kitProgressRingPercent, whole)
        else -> stringResource(Res.string.kitProgressRingSteps, whole, total)
    }
    val suffix = if (value != null) stringResource(Res.string.kitProgressRingPercentSign)
        else " " + stringResource(Res.string.kitProgressRingOfTotal, total)
    val stepsLabel = stringResource(Res.string.kitProgressRingStepsDone)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = (maxWidth.value * .64f).coerceIn(150f, 264f) // F: lib/ui/kit/kit_progress_ring.dart:199
            val stroke = (diameter * .052f).coerceIn(9f, 14f) // F: lib/ui/kit/kit_progress_ring.dart:200
            Box(Modifier.size(diameter.dp).clearAndSetSemantics {
                contentDescription = listOfNotNull(semanticsLabel, spoken, caption).joinToString(", ")
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            }, contentAlignment = Alignment.Center) {
                if (!halted && arc.value > 0) Canvas(Modifier.fillMaxSize().blur((stroke * .9f).dp, BlurredEdgeTreatment.Unbounded)) { // F: lib/ui/kit/kit_progress_ring.dart:473
                    val width = stroke.dp.toPx()
                    val inset = width / 2 // F: lib/ui/kit/kit_progress_ring.dart:487
                    drawArc(colors.accent.copy(alpha = if (colors.dark) .32f else .18f), // F: lib/ui/kit/kit_progress_ring.dart:300
                        -90f, (if (rtl) -1 else 1) * 360f * arc.value, false, // NV: Flutter's radians converted to Compose degrees
                        Offset(inset, inset), Size(size.width - width, size.height - width), style = Stroke(width * 1.6f, cap = StrokeCap.Round)) // F: lib/ui/kit/kit_progress_ring.dart:470
                }
                Canvas(Modifier.fillMaxSize()) {
                    val width = stroke.dp.toPx()
                    val inset = width / 2 // F: lib/ui/kit/kit_progress_ring.dart:487
                    val bounds = Size(size.width - width, size.height - width)
                    drawArc(colors.text3.copy(alpha = if (colors.dark) .22f else .18f), 0f, 360f, false, Offset(inset, inset), bounds, style = Stroke(width)) // NV: a full Compose arc is 360 degrees; Flutter uses 2*pi radians
                    // F: lib/ui/kit/kit_progress_ring.dart:294,300
                    if (arc.value > 0) drawArc(
                        if (halted) colors.text3.copy(alpha = .7f) else colors.accent, // F: lib/ui/kit/kit_progress_ring.dart:296
                        -90f, (if (rtl) -1 else 1) * 360f * arc.value, false, // NV: radians converted to degrees for Compose drawArc
                        Offset(inset, inset), bounds, style = Stroke(width, cap = StrokeCap.Round),
                    )
                }
                if (finished) {
                    io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph(
                        io.github.eslamasabry.oc.ui.kit.icon.KitIcons.check,
                        colors.accent, (diameter * .3f).dp, // F: lib/ui/kit/kit_progress_ring.dart:281
                    )
                } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val big = typography.style(TypeRole.LargeTitle).copy(
                        color = if (halted) colors.text2 else colors.text1,
                        fontSize = (diameter * .25f).sp, lineHeight = (diameter * .25f).sp, // F: lib/ui/kit/kit_progress_ring.dart:205
                        fontWeight = FontWeight.W600, letterSpacing = (-diameter * .006f).sp, // F: lib/ui/kit/kit_progress_ring.dart:208
                        fontFeatureSettings = "tnum",
                    )
                    val text = buildAnnotatedString {
                        append(whole)
                        withStyle(SpanStyle(fontSize = (diameter * if (value != null) .13f else .1f).sp, letterSpacing = 0.sp)) { append(suffix) } // F: lib/ui/kit/kit_progress_ring.dart:228,259
                    }
                    BasicText(text, style = big, maxLines = 1)
                    if (value == null || caption != null) {
                        Spacer(Modifier.height(Space.S2.dp))
                        KitText(caption ?: stepsLabel, role = TypeRole.Secondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height((Space.S6 + Space.S2).dp))
        KitText(headline, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, role = TypeRole.Title, textAlign = TextAlign.Center, maxLines = 3) // F: lib/ui/kit/kit_progress_ring.dart:343
        if (trail.isNotEmpty()) {
            Spacer(Modifier.height(Space.S4.dp))
            RingTrail(trail, halted)
        }
    }
}

internal fun ringFraction(value: Float?, done: Int, of: Int, finished: Boolean): Float =
    if (finished) 1f else if (value != null) (if (value.isNaN()) 0f else value.coerceIn(0f, 1f))
    else if (of <= 0) 0f else (done.toFloat() / of).coerceIn(0f, 1f)

@Composable
private fun RingTrail(marks: List<KitTrailMark>, halted: Boolean) {
    val colors = LocalKitColors.current
    val ringWidth = kitFocusRingWidth()
    val lit = if (halted) colors.text2 else colors.accent
    val waiting = colors.text3.copy(alpha = .45f) // F: lib/ui/kit/kit_progress_ring.dart:375
    val dot = 8.dp // F: lib/ui/kit/kit_progress_ring.dart:373
    Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
        marks.forEachIndexed { index, mark ->
            if (index > 0) Canvas(Modifier.padding(horizontal = Space.S1.dp).width(14.dp).height(1.5.dp)) { // F: lib/ui/kit/kit_progress_ring.dart:385,386
                drawRect(if (mark == KitTrailMark.Waiting) waiting else lit.copy(alpha = .55f)) // F: lib/ui/kit/kit_progress_ring.dart:388
            }
            Canvas(Modifier.width(if (mark == KitTrailMark.Current) 24.dp else dot).height(dot)) { // F: lib/ui/kit/kit_progress_ring.dart:396
                val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2) // F: lib/ui/kit/kit_progress_ring.dart:461
                drawRoundRect(if (mark == KitTrailMark.Waiting) waiting else lit, cornerRadius = radius,
                    style = if (mark == KitTrailMark.Waiting) Stroke(ringWidth.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
            }
        }
    }
}
