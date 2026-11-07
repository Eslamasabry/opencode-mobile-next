package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * One of the app's line drawings (Dart `KitScene`, lib/ui/kit/kit_illustration.dart): it paints one frame from a
 * [KitSceneFrame] into its own design [box]; [KitIllustration] owns the time.
 */
public interface KitScene {
    /** The design box the scene draws in, in scene units (120 × 120 by default). */
    public val box: Size get() = Size(120f, 120f) // F: lib/ui/kit/kit_illustration.dart:28

    /** True for a drawing that shows progress along a line: it flips horizontally under RTL (LAY-8). */
    public val mirrorsInRtl: Boolean get() = false

    /** Paints one frame in [box]-sized scene units. */
    public fun DrawScope.paint(frame: KitSceneFrame)
}

/** The colours a scene draws with, always from the theme's roles (Dart `KitPalette.fromRoles`). */
@Immutable
public data class KitPalette(
    val accent: Color,
    val accentSoft: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val surface: Color,
    val success: Color,
    val warning: Color,
    val failure: Color,
    val progress: Color,
) {
    public companion object {
        /** No red for failures: [failure] is text1, said in words (LOOK-5); flat fills at the scene wash alpha. */
        public fun fromRoles(roles: KitColors): KitPalette =
            KitPalette(
                accent = roles.accent,
                accentSoft = roles.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA),
                ink = roles.text1,
                muted = roles.text2,
                line = roles.text3,
                surface = roles.surface3,
                success = roles.success,
                warning = roles.attention,
                failure = roles.text1,
                progress = roles.accent,
            )
    }
}

/** One frame of a scene: [entrance] 0 → 1 (1 when finished or reduced), [loop] 0 → 1 while [looping]. */
@Immutable
public data class KitSceneFrame(
    val entrance: Float,
    val loop: Float,
    val looping: Boolean,
    val palette: KitPalette,
)

/** Drawing helpers every scene shares (Dart `KitDraw`): one line weight, one way of drawing in. */
public object KitDraw {
    /** The standard stroke in scene units (a 120-unit box). */
    public const val STROKE: Float = 5.0f // F: lib/ui/kit/kit_illustration.dart:164

    /** A thin stroke for detail. */
    public const val HAIRLINE: Float = 2.5f // F: lib/ui/kit/kit_illustration.dart:167

    /** A round-capped, round-joined stroke of [width]. */
    public fun pen(width: Float = STROKE): Stroke = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** [t] of [begin]..[end], clamped and eased: stages a part of an entrance. */
    public fun interval(t: Float, begin: Float, end: Float, curve: Easing = KitEasing.Enter): Float =
        when {
            t <= begin -> 0f
            t >= end -> 1f
            else -> curve.transform((t - begin) / (end - begin))
        }

    /** A smooth 0 → 1 → 0 wave over one loop, shifted by [phase] (0..1). */
    public fun wave(loop: Float, phase: Float = 0f): Float =
        ((1 - cos((loop + phase) * 2 * PI)) / 2).toFloat() // F: lib/ui/kit/kit_illustration.dart:197

    /** Draws the first [t] (0..1) of [path]'s length: a line drawing itself. */
    public fun DrawScope.partialPath(path: Path, t: Float, color: Color, width: Float = STROKE) {
        if (t <= 0f) return
        if (t >= 1f) {
            drawPath(path, color, style = pen(width))
            return
        }
        val measure = PathMeasure()
        measure.setPath(path, false)
        val part = Path()
        measure.getSegment(0f, measure.length * t, part, true)
        drawPath(part, color, style = pen(width))
    }

    /** [color] at [opacity] of its own alpha. */
    public fun fade(color: Color, opacity: Float): Color = color.copy(alpha = color.alpha * opacity.coerceIn(0f, 1f))

    /** A flat accent fill (LOOK-36): never above the scene wash alpha. */
    public fun wash(palette: KitPalette, opacity: Float = 1f): Color = fade(palette.accentSoft, opacity)
}

/**
 * Shows a [KitScene] at [width], playing its entrance once and, when [ambient] and allowed, its loop (Dart
 * `KitIllustration`). Reduced motion, a still frame, or a celebration the person turned off shows the finished
 * drawing. Decorative unless [semanticLabel] is given.
 *
 * States: entrance, finished, ambient, reduced (decorative; not interactive)
 * Cap: Reading
 * Motion: entrance (900 ms, linear), the loop (4 s); Off = the finished drawing
 * Replaces: KitIllustration (lib/ui/kit/kit_illustration.dart)
 */
@Composable
public fun KitIllustration(
    scene: KitScene,
    modifier: Modifier = Modifier,
    width: Dp = FlutterKitTokens.ILLUSTRATION_PAGE.dp,
    ambient: Boolean = false,
    loopPeriodMs: Int = FlutterKitMotion.BREATH_MS,
    animateEntrance: Boolean = true,
    entranceDurationMs: Int = FlutterKitMotion.ENTRANCE_MS,
    semanticLabel: String? = null,
) {
    val motion = KitMotion.state
    val celebrations = LocalKitAppearance.current.celebrations
    val skipCelebration = entranceDurationMs >= FlutterKitMotion.CELEBRATION_MS && !celebrations
    val finished = motion.reduced || motion.stillFrame || skipCelebration || !animateEntrance
    val entrance = remember { Animatable(if (finished) 1f else 0f) }
    val loop = remember { Animatable(0f) }
    val looping = ambient && motion.ambientLoops && !skipCelebration
    LaunchedEffect(finished, looping, loopPeriodMs) {
        if (finished) entrance.snapTo(1f) else entrance.animateTo(1f, tween(entranceDurationMs, easing = LinearEasing))
        if (looping) {
            while (true) {
                loop.snapTo(0f)
                loop.animateTo(1f, tween(loopPeriodMs, easing = LinearEasing))
            }
        } else {
            loop.snapTo(0f)
        }
    }
    val palette = KitPalette.fromRoles(LocalKitColors.current)
    val mirror = scene.mirrorsInRtl && LocalLayoutDirection.current == LayoutDirection.Rtl
    val box = scene.box
    val semantics =
        if (semanticLabel == null) Modifier.clearAndSetSemantics {}
        else Modifier.clearAndSetSemantics {
            contentDescription = semanticLabel
            role = Role.Image
        }
    Canvas(modifier.size(width, width * box.height / box.width).then(semantics)) {
        val scale = min(size.width / box.width, size.height / box.height)
        withTransform({
            translate((size.width - box.width * scale) / 2, (size.height - box.height * scale) / 2) // F: lib/ui/kit/kit_illustration.dart:396,397
            scale(scale, scale, Offset.Zero)
            if (mirror) {
                translate(box.width, 0f)
                scale(-1f, 1f, Offset.Zero)
            }
        }) {
            clipRect(0f, 0f, box.width, box.height) {
                with(scene) {
                    paint(KitSceneFrame(entrance.value, if (looping) loop.value else 0f, looping, palette))
                }
            }
        }
    }
}
