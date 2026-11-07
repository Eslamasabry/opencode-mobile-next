package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyph
import io.github.eslamasabry.oc.ui.kit.foundation.kitFocusRingWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.setup.KitTrailMark
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitProgressRingFinished
import io.github.eslamasabry.oc.ui.words.res.kitProgressRingOfTotal
import io.github.eslamasabry.oc.ui.words.res.kitProgressRingPercent
import io.github.eslamasabry.oc.ui.words.res.kitProgressRingPercentSign
import io.github.eslamasabry.oc.ui.words.res.kitProgressRingSteps
import io.github.eslamasabry.oc.ui.words.res.kitProgressRingStepsDone
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** What a [KitProgressRing]'s figure says (Dart's three constructors). */
public sealed interface KitRingFigure {
    /** The current step's measured share, 0..1 ("64%", floored), with an optional amount under it ("18 of 30 MB"). */
    public data class Measured(val value: Double, val caption: String? = null) : KitRingFigure

    /** Steps done of all ("2 of 6"), for a step that reports no amount; never a guessed percent. */
    public data class Steps(val done: Int, val of: Int) : KitRingFigure

    /** A full ring with a check. */
    public data object Finished : KitRingFigure
}

/** The arc's share of the circle, 0..1 (Dart `KitProgressRing.fraction`). */
public fun kitRingFraction(figure: KitRingFigure): Double = when (figure) {
    is KitRingFigure.Measured -> if (figure.value.isNaN()) 0.0 else figure.value.coerceIn(0.0, 1.0)
    is KitRingFigure.Steps -> if (figure.of <= 0) 0.0 else (figure.done.toDouble() / figure.of).coerceIn(0.0, 1.0)
    KitRingFigure.Finished -> 1.0
}

/** The whole percent a measured ring shows: floored, so it never claims more than arrived. */
public fun kitRingPercent(fraction: Double): Int =
    floor(fraction * 100.0 + 1e-9).toInt().coerceIn(0, 100) // F: lib/ui/kit/kit_progress_ring.dart:181

/**
 * One long job (phone setup) as one big calm ring with its figure inside, one sentence under it of what is happening
 * now ([headline]), and a slim [trail] of the job's steps (Dart `KitProgressRing`, `lib/ui/kit/kit_progress_ring.dart`).
 *
 * [halted] (paused, stopped or failed) turns the arc and the current step's mark neutral: never red, and not amber.
 * The arc eases forward over the standard motion; a smaller value is a new arc and lands at once, so the ring never
 * runs backwards. Under reduced motion every change is instant. The ring is one semantics node ([semanticsLabel], the
 * figure and the caption); the headline is a polite live region.
 *
 * States: none — the caller supplies every value.
 * Cap: glyph
 */
@Composable
public fun KitProgressRing(
    figure: KitRingFigure,
    headline: String,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    trail: List<KitTrailMark> = emptyList(),
    halted: Boolean = false,
) {
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val motion = KitMotion.specs
    val isHalted = halted && figure != KitRingFigure.Finished
    val exact = kitRingFraction(figure)
    val target = exact.toFloat()
    val arc = remember { Animatable(target) }
    LaunchedEffect(target, motion.state.reduced) {
        // Backwards is a new arc (a new stage, a new job): it lands at once.
        if (target < arc.value || motion.state.reduced) arc.snapTo(target) else arc.animateTo(target, motion.enter())
    }
    val whole: String?
    val total: String?
    val spoken: String
    when (figure) {
        is KitRingFigure.Measured -> {
            whole = Numbers.format(kitRingPercent(exact).toLong())
            total = null
            spoken = stringResource(Res.string.kitProgressRingPercent, whole)
        }
        is KitRingFigure.Steps -> {
            whole = Numbers.format(figure.done.toLong())
            total = Numbers.format(figure.of.toLong())
            spoken = stringResource(Res.string.kitProgressRingSteps, whole, total)
        }
        KitRingFigure.Finished -> {
            whole = null
            total = null
            spoken = stringResource(Res.string.kitProgressRingFinished)
        }
    }
    val caption = (figure as? KitRingFigure.Measured)?.caption
    val percentSign = stringResource(Res.string.kitProgressRingPercentSign)
    val ofTotal = total?.let { stringResource(Res.string.kitProgressRingOfTotal, it) }
    val stepsDone = stringResource(Res.string.kitProgressRingStepsDone)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.kitMaxWidth(CapRole.Reading).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = semanticsLabel
                stateDescription = listOfNotNull(spoken, caption).joinToString(", ")
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
            contentAlignment = Alignment.Center,
        ) {
            val width = if (constraints.hasBoundedWidth) maxWidth.value else 264f // F: lib/ui/kit/kit_progress_ring.dart:198
            val diameter = min(width * .64f, 264f).coerceIn(150f, 264f) // F: lib/ui/kit/kit_progress_ring.dart:199
            val stroke = (diameter * .052f).coerceIn(9f, 14f) // F: lib/ui/kit/kit_progress_ring.dart:200
            val inner = (diameter - stroke * 2) * .74f // F: lib/ui/kit/kit_progress_ring.dart:201
            val ink = if (isHalted) colors.text2 else colors.text1
            val arcColor = if (isHalted) colors.text3.copy(alpha = .7f) else colors.accent // F: lib/ui/kit/kit_progress_ring.dart:296
            val glow = if (isHalted) null else colors.accent.copy(alpha = if (colors.dark) .32f else .18f) // F: lib/ui/kit/kit_progress_ring.dart:300
            val track = colors.text3.copy(alpha = if (colors.dark) .22f else .18f) // F: lib/ui/kit/kit_progress_ring.dart:294
            val tail = srgbLerp(arcColor, colors.ground, .28f) // F: lib/ui/kit/kit_progress_ring.dart:488
            Box(Modifier.size(diameter.dp), contentAlignment = Alignment.Center) {
                if (glow != null && arc.value > 0f) {
                    // Flutter's MaskFilter.blur takes a sigma; Compose's blur takes a radius.
                    Canvas(Modifier.fillMaxSize().blur(blurRadiusForSigma(stroke * .9f).dp, BlurredEdgeTreatment.Unbounded)) { // F: lib/ui/kit/kit_progress_ring.dart:473
                        val s = stroke.dp.toPx()
                        mirrored(rtl) {
                            val half = s / 2 // F: lib/ui/kit/kit_progress_ring.dart:485
                            drawArc(glow, TWELVE, TURN * arc.value, false, Offset(half, half), Size(size.width - s, size.height - s),
                                style = Stroke(s * 1.6f, cap = StrokeCap.Round)) // F: lib/ui/kit/kit_progress_ring.dart:470
                        }
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val s = stroke.dp.toPx()
                    val radius = (size.minDimension - s) / 2 // F: lib/ui/kit/kit_progress_ring.dart:442
                    val topLeft = Offset(center.x - radius, center.y - radius)
                    val box = Size(size.minDimension - s, size.minDimension - s)
                    drawCircle(track, radius, style = Stroke(s))
                    val a = arc.value
                    if (a > 0f) mirrored(rtl) {
                        val sweep = TURN * a
                        if (glow != null && a < 1f) {
                            // The tail is a little deeper than the head; the gradient starts half a cap before it.
                            val cap = s / 2 / radius * DEGREES // F: lib/ui/kit/kit_progress_ring.dart:485
                            val end = min(TURN, sweep + cap * 2) // F: lib/ui/kit/kit_progress_ring.dart:487
                            rotate(TWELVE - cap) {
                                drawArc(
                                    Brush.sweepGradient(0f to tail, end / TURN to arcColor, 1f to arcColor, center = center),
                                    cap, sweep, false, topLeft, box, style = Stroke(s, cap = StrokeCap.Round),
                                )
                            }
                        } else {
                            drawArc(arcColor, TWELVE, sweep, false, topLeft, box, style = Stroke(s, cap = StrokeCap.Round))
                        }
                    }
                }
                ScaleDown(inner) {
                    when (figure) {
                        KitRingFigure.Finished ->
                            KitGlyph(KitIcons.check, colors.accent, (diameter * .3f).dp) // F: lib/ui/kit/kit_progress_ring.dart:281
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val big = typography.style(TypeRole.LargeTitle).copy(
                                color = ink,
                                fontSize = (diameter * .25f).sp, // F: lib/ui/kit/kit_progress_ring.dart:205
                                lineHeight = TextUnit.Unspecified,
                                fontWeight = FontWeight.W600, // F: lib/ui/kit/kit_progress_ring.dart:207
                                letterSpacing = (-diameter * .006f).sp, // F: lib/ui/kit/kit_progress_ring.dart:208
                                fontFeatureSettings = "tnum",
                            )
                            val text = buildAnnotatedString {
                                append(whole.orEmpty())
                                if (figure is KitRingFigure.Measured) {
                                    withStyle(SpanStyle(fontSize = (diameter * .13f).sp, letterSpacing = 0.sp)) { append(percentSign) } // F: lib/ui/kit/kit_progress_ring.dart:228
                                } else {
                                    withStyle(
                                        SpanStyle(
                                            fontSize = (diameter * .1f).sp, // F: lib/ui/kit/kit_progress_ring.dart:259
                                            letterSpacing = 0.sp,
                                            fontWeight = FontWeight.W500, // F: lib/ui/kit/kit_progress_ring.dart:261
                                            color = colors.text2,
                                        ),
                                    ) { append(" " + ofTotal.orEmpty()) }
                                }
                            }
                            // The measured figure always reads left to right ("64%").
                            if (figure is KitRingFigure.Measured) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    OneEmLine(big.fontSize) { BasicText(text, style = big, maxLines = 1) }
                                }
                            } else {
                                OneEmLine(big.fontSize) { BasicText(text, style = big, maxLines = 1) }
                            }
                            val small = caption ?: if (figure is KitRingFigure.Steps) stepsDone else null
                            if (small != null) {
                                Spacer(Modifier.height(Space.S2.dp))
                                // Dart `Text(caption, style: small)`: the secondary style in text2, not a KitText.
                                BasicText(
                                    small,
                                    style = typography.style(TypeRole.Secondary).copy(color = colors.text2, textAlign = TextAlign.Center),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height((Space.S6 + Space.S2).dp))
        Crossfade(headline, Modifier.fillMaxWidth(), animationSpec = motion.enter(), label = "headline") { line ->
            KitText(
                line,
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                role = TypeRole.Title,
                textAlign = TextAlign.Center,
                maxLines = 3, // F: lib/ui/kit/kit_progress_ring.dart:343
            )
        }
        if (trail.isNotEmpty()) {
            Spacer(Modifier.height(Space.S4.dp))
            ScaleDown(null) { Trail(trail, isHalted) }
        }
    }
}

/** The slim trail: a dot per step, the current one a short bar, joined by hairlines (Dart `_Trail`). */
@Composable
private fun Trail(marks: List<KitTrailMark>, halted: Boolean) {
    val colors = LocalKitColors.current
    val ring = kitFocusRingWidth()
    val lit = if (halted) colors.text2 else colors.accent
    val waiting = colors.text3.copy(alpha = .45f) // F: lib/ui/kit/kit_progress_ring.dart:375
    val dot = 8f // F: lib/ui/kit/kit_progress_ring.dart:373
    Row(Modifier.clearAndSetSemantics { }, verticalAlignment = Alignment.CenterVertically) {
        marks.forEachIndexed { i, mark ->
            if (i > 0) {
                val reached = mark != KitTrailMark.Waiting
                Canvas(Modifier.padding(horizontal = Space.S1.dp).width(14.dp).height(1.5.dp)) { // F: lib/ui/kit/kit_progress_ring.dart:385,386
                    drawRect(if (reached) lit.copy(alpha = .55f) else waiting) // F: lib/ui/kit/kit_progress_ring.dart:388
                }
            }
            Canvas(Modifier.width(if (mark == KitTrailMark.Current) 24.dp else dot.dp).height(dot.dp)) { // F: lib/ui/kit/kit_progress_ring.dart:396
                val r = CornerRadius((dot / 2).dp.toPx()) // F: lib/ui/kit/kit_progress_ring.dart:406
                if (mark == KitTrailMark.Waiting) {
                    // Flutter's Border.all draws inside the box.
                    val w = ring.toPx()
                    val half = w / 2 // F: lib/ui/kit/kit_progress_ring.dart:485
                    drawRoundRect(waiting, Offset(half, half), Size(size.width - w, size.height - w), CornerRadius(r.x - half), style = Stroke(w))
                } else {
                    drawRoundRect(lit, cornerRadius = r)
                }
            }
        }
    }
}

/**
 * Dart `TextStyle(height: 1)`: the line box is exactly one font size tall. Flutter's golden shows the glyphs centred in
 * it (the font's ascent + descent overflow the box evenly), so the natural line is measured and centred in a box of the
 * font size; Compose will not shrink a line below the font's own height.
 */
@Composable
private fun OneEmLine(fontSize: TextUnit, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minHeight = 0))
        val target = fontSize.toPx().roundToInt()
        val at = Alignment.Center.align(IntSize(p.width, p.height), IntSize(p.width, target), layoutDirection)
        layout(p.width, target) { p.place(at.x, at.y) }
    }
}

/** Dart `FittedBox(fit: BoxFit.scaleDown)`, in a square of [side] dp when given, else in the incoming width. */
@Composable
private fun ScaleDown(side: Float?, content: @Composable () -> Unit) {
    Layout(content, if (side != null) Modifier.size(side.dp) else Modifier) { measurables, constraints ->
        val p = measurables.first().measure(Constraints())
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else p.width
        val maxH = if (constraints.hasBoundedHeight && side != null) constraints.maxHeight else p.height
        val scale = minOf(1f, maxW.toFloat() / p.width.coerceAtLeast(1), maxH.toFloat() / p.height.coerceAtLeast(1))
        val w = if (side != null) maxW else (p.width * scale).toInt()
        val h = if (side != null) maxH else (p.height * scale).toInt()
        layout(w, h) {
            val at = Alignment.Center.align(IntSize(p.width, p.height), IntSize(w, h), layoutDirection)
            p.placeWithLayer(at.x, at.y) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.mirrored(
    rtl: Boolean,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    // Clockwise from twelve o'clock; mirrored for a right-to-left reader.
    if (rtl) scale(-1f, 1f) { block() } else block()
}

/** Flutter's Color.lerp: straight sRGB channels (Compose's lerp mixes in Oklab). */
private fun srgbLerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** Skia's blur turns a radius into sigma = 0.57735 r + 0.5; this is its inverse, so the glow keeps Flutter's sigma. */
private fun blurRadiusForSigma(sigma: Float): Float = ((sigma - .5f) / .57735f).coerceAtLeast(0f) // NV: Skia's radius-to-sigma constants

private const val DEGREES: Float = (180.0 / PI).toFloat() // NV: radians to degrees
private const val TURN: Float = 360f // NV: a whole turn in Compose degrees (Flutter: 2 pi)
private const val TWELVE: Float = -90f // NV: twelve o'clock in Compose degrees (Flutter: -pi / 2)
