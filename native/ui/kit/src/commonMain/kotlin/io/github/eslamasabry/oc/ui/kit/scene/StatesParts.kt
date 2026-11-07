package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterCurve
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitCurves
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The drawn-scene foundation the states and team scenes paint into. Flutter keeps it in kit_illustration.dart
// (KitScene, KitPalette, KitSceneFrame, KitDraw, KitIllustration), whose native home KitIllustration.kt belongs to
// order 07 and is not on this branch yet; these "KitDrawn" types carry the same contract under names that cannot
// collide with it, so order 07 can fold them in later without touching the scenes' drawing code.

/** A drawing (Flutter `KitScene`): paints one frame into its own design box, in scene units. */
public interface KitDrawnScene {
    /** The design box the scene draws in, in scene units (Flutter's default 120 x 120). */
    public val box: Size
        get() = Size(120f, 120f) // F: lib/ui/kit/kit_illustration.dart:28

    /** True for a drawing that shows progress along a line: flipped horizontally under RTL (LAY-8). */
    public val mirrorsInRtl: Boolean
        get() = false

    /** Paints one frame into [box]-sized scene units. */
    public fun DrawScope.paint(frame: KitDrawnFrame)
}

/** The colours a scene draws with, all theme roles (Flutter `KitPalette.fromRoles`). */
@Immutable
public data class KitDrawnPalette(
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
        /** The one role mapping (kit_illustration.dart:68-79): no red for failures, flat washes at sceneWashAlpha. */
        public fun of(roles: KitColors): KitDrawnPalette =
            KitDrawnPalette(
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

/** One frame of a scene (Flutter `KitSceneFrame`). */
@Immutable
public data class KitDrawnFrame(
    /** 0 → 1 over the entrance, linear; 1 when finished or reduced. */
    val entrance: Float,
    /** 0 → 1, repeating once per loop period while [looping]; 0 otherwise. */
    val loop: Float,
    /** Whether the ambient loop runs; a scene at `looping = false` looks complete and still at `loop = 0`. */
    val looping: Boolean,
    val palette: KitDrawnPalette,
)

/** The drawing helpers every scene shares (Flutter `KitDraw`): one stroke weight, one way of drawing in. */
public object KitDrawnInk {
    /** The standard stroke in scene units (a 120-unit box). */
    public const val STROKE: Float = 5.0f // F: lib/ui/kit/kit_illustration.dart:164

    /** A thin stroke for detail. */
    public const val HAIRLINE: Float = 2.5f // F: lib/ui/kit/kit_illustration.dart:167

    /** A round-capped, round-joined pen of [width] (kit_illustration.dart:169-175). */
    public fun pen(width: Float = STROKE): Stroke = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** [t] of [begin]..[end], clamped and eased by [curve] (kit_illustration.dart:184-193). */
    public fun interval(t: Float, begin: Float, end: Float, curve: FlutterCurve = FlutterKitCurves.enter): Float {
        return when {
            t <= begin -> 0f
            t >= end -> 1f
            else -> easing(curve).transform((t - begin) / (end - begin))
        }
    }

    /** A smooth 0 → 1 → 0 wave over one loop, shifted by [phase] (kit_illustration.dart:196-197). */
    public fun wave(loop: Float, phase: Float = 0f): Float =
        ((1 - cos((loop + phase) * 2 * PI)) / 2).toFloat() // F: lib/ui/kit/kit_illustration.dart:197

    /** [color] at [opacity] of its own alpha (kit_illustration.dart:218-219). */
    public fun fade(color: Color, opacity: Float): Color = color.copy(alpha = color.alpha * opacity.coerceIn(0f, 1f))

    /** A Flutter curve as an easing, from the SDK's own definition (curves.dart `ThreePointCubic`). */
    public fun easing(curve: FlutterCurve): Easing =
        when (curve) {
            is FlutterCurve.Cubic -> CubicBezierEasing(curve.x1, curve.y1, curve.x2, curve.y2)
            FlutterCurve.Linear -> LinearEasing
            is FlutterCurve.Flipped -> {
                val inner = easing(curve.curve)
                Easing { t -> 1f - inner.transform(1f - t) }
            }
            is FlutterCurve.ThreePointCubic -> {
                val mx = curve.midX
                val my = curve.midY
                val first = CubicBezierEasing(curve.a1x / mx, curve.a1y / my, curve.b1x / mx, curve.b1y / my)
                val sx = 1f - mx
                val sy = 1f - my
                val second =
                    CubicBezierEasing(
                        (curve.a2x - mx) / sx,
                        (curve.a2y - my) / sy,
                        (curve.b2x - mx) / sx,
                        (curve.b2y - my) / sy,
                    )
                Easing { t ->
                    when {
                        t <= 0f -> 0f
                        t >= 1f -> 1f
                        t < mx -> first.transform(t / mx) * my
                        else -> second.transform((t - mx) / sx) * sy + my
                    }
                }
            }
        }
}

/** Flutter `Rect.fromCenter`: a [width] x [height] rectangle centred on [center]. */
public fun sceneRect(center: Offset, width: Float, height: Float): Rect =
    Rect(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2) // NV: Rect.fromCenter halves

/** Radians to the degrees Compose's arcs take (states_parts.dart:126 converts the other way). */
internal fun Float.sceneDegrees(): Float = (this * 180 / PI).toFloat() // F: lib/ui/kit/scenes/states_parts.dart:126

/**
 * Flutter `Path.arcToPoint(end, radius, clockwise)` for the short arc of a circle: a corner of a sheet, a folder
 * or a body. Clockwise is on screen (y down), as in Flutter.
 */
internal fun Path.sceneArcTo(from: Offset, to: Offset, radius: Float, clockwise: Boolean = true) {
    val d = to - from
    val chord = d.getDistance()
    if (chord == 0f) return
    val mid = (from + to) / 2f // NV: the chord's midpoint
    val h = sqrt((radius * radius - chord * chord / 4).coerceAtLeast(0f)) // NV: half the chord, squared
    val normal = Offset(-d.y / chord, d.x / chord)
    val centre = if (clockwise) mid + normal * h else mid - normal * h
    val start = atan2(from.y - centre.y, from.x - centre.x)
    val end = atan2(to.y - centre.y, to.x - centre.x)
    var sweep = end - start
    val turn = (2 * PI).toFloat() // F: lib/ui/kit/kit_illustration.dart:197
    if (clockwise && sweep < 0) sweep += turn
    if (!clockwise && sweep > 0) sweep -= turn
    arcTo(Rect(centre, radius), start.sceneDegrees(), sweep.sceneDegrees(), false)
}

/**
 * Draws the first [t] (0..1) of the summed length of [paths]: a line drawing itself
 * (`KitDraw.partialPath`, kit_illustration.dart:200-215). A Flutter path with several contours is passed as one
 * path per contour.
 */
public fun DrawScope.drawPartial(paths: List<Path>, t: Float, color: Color, width: Float = KitDrawnInk.STROKE) {
    if (t <= 0f) return
    val pen = KitDrawnInk.pen(width)
    if (t >= 1f) {
        paths.forEach { drawPath(it, color, style = pen) }
        return
    }
    val measures = paths.map { p -> PathMeasure().apply { setPath(p, false) } }
    var remaining = measures.sumOf { it.length.toDouble() }.toFloat() * t
    for (m in measures) {
        if (remaining <= 0f) break
        val length = min(remaining, m.length)
        val shown = Path()
        m.getSegment(0f, length, shown, true)
        drawPath(shown, color, style = pen)
        remaining -= length
    }
}

/** One-contour form of [drawPartial]. */
public fun DrawScope.drawPartial(path: Path, t: Float, color: Color, width: Float = KitDrawnInk.STROKE): Unit =
    drawPartial(listOf(path), t, color, width)

/** One stroked straight line in the scene pen (Flutter `canvas.drawLine(a, b, KitDraw.pen(c, w))`). */
public fun DrawScope.drawSceneLine(color: Color, from: Offset, to: Offset, width: Float = KitDrawnInk.STROKE) {
    drawLine(color, from, to, width, cap = StrokeCap.Round)
}

/**
 * Plays a scene's entrance once and, when [ambient] and the motion level allows, its loop
 * (kit_illustration.dart:270-338). Reduced motion, a still frame, or a celebration the person turned off show the
 * finished drawing at once.
 */
@Composable
public fun rememberKitDrawnFrame(ambient: Boolean, celebration: Boolean = false): KitDrawnFrame {
    val motion = KitMotion.state
    val palette = KitDrawnPalette.of(LocalKitColors.current)
    val instant = motion.reduced || motion.stillFrame || (celebration && !LocalKitAppearance.current.celebrations)
    val entrance = remember { Animatable(if (instant) 1f else 0f) }
    val loop = remember { Animatable(0f) }
    LaunchedEffect(instant) {
        if (instant) entrance.snapTo(1f)
        else
            entrance.animateTo(
                1f,
                tween(
                    if (celebration) FlutterKitMotion.CELEBRATION_MS else FlutterKitMotion.ENTRANCE_MS,
                    easing = LinearEasing,
                ),
            )
    }
    val looping = ambient && motion.ambientLoops && entrance.value == 1f
    LaunchedEffect(looping) {
        loop.snapTo(0f)
        if (looping)
            while (true) {
                loop.animateTo(1f, tween(FlutterKitMotion.BREATH_MS, easing = LinearEasing))
                loop.snapTo(0f)
            }
    }
    return KitDrawnFrame(if (instant) 1f else entrance.value, if (looping) loop.value else 0f, looping, palette)
}

/**
 * Shows a [KitDrawnScene] at [width] (Flutter `KitIllustration`): scaled uniformly from its box, centred, clipped
 * to the box, mirrored under RTL for a progress drawing. Decorative unless [semanticLabel] is given.
 */
@Composable
public fun KitDrawnIllustration(
    scene: KitDrawnScene,
    modifier: Modifier = Modifier,
    width: Dp = FlutterKitTokens.ILLUSTRATION_PAGE.dp,
    ambient: Boolean = false,
    celebration: Boolean = false,
    semanticLabel: String? = null,
) {
    val frame = rememberKitDrawnFrame(ambient, celebration)
    val mirror = scene.mirrorsInRtl && LocalLayoutDirection.current == LayoutDirection.Rtl
    val box = scene.box
    val height = width * (box.height / box.width)
    val label = semanticLabel
    val described =
        if (label == null) modifier.clearAndSetSemantics {} else modifier.semantics { contentDescription = label }
    Canvas(described.size(width, height)) {
        val factor = min(size.width / box.width, size.height / box.height)
        withTransform({
            translate((size.width - box.width * factor) / 2, (size.height - box.height * factor) / 2) // F: lib/ui/kit/kit_illustration.dart:396,397
            scale(factor, factor, Offset.Zero)
            if (mirror) {
                translate(box.width, 0f)
                scale(-1f, 1f, Offset.Zero)
            }
        }) {
            clipRect(0f, 0f, box.width, box.height) { with(scene) { paint(frame) } }
        }
    }
}

/**
 * The pieces the empty, quiet and failure drawings share (Flutter `StatesParts`, states_parts.dart), so a folded
 * sheet, a spark or the soft wash behind a drawing look the same in every scene.
 */
public object StatesParts {
    /** The wash's default centre and radius (states_parts.dart:16,17). */
    public val WASH_CENTRE: Offset = Offset(60f, 62f) // F: lib/ui/kit/scenes/states_parts.dart:16
    public const val WASH_RADIUS: Float = 44f // F: lib/ui/kit/scenes/states_parts.dart:17

    /** A sheet's default folded corner and corner radius (states_parts.dart:30). */
    public const val SHEET_FOLD: Float = 14f // F: lib/ui/kit/scenes/states_parts.dart:30
    public const val SHEET_RADIUS: Float = 6f // F: lib/ui/kit/scenes/states_parts.dart:30

    /** The soft accent wash every state drawing sits on: it grows in first, so the line work lands on something. */
    public fun DrawScope.wash(frame: KitDrawnFrame, center: Offset = WASH_CENTRE, radius: Float = WASH_RADIUS) {
        val grow = KitDrawnInk.interval(frame.entrance, 0f, .35f) // F: lib/ui/kit/scenes/states_parts.dart:19
        if (grow <= 0f) return
        drawCircle(
            KitDrawnInk.fade(frame.palette.accentSoft, .75f * grow), // F: lib/ui/kit/scenes/states_parts.dart:24
            radius * (.82f + .18f * grow), // F: lib/ui/kit/scenes/states_parts.dart:23
            center,
        )
    }

    /** A sheet of paper with its top end corner folded over: the cast's "folded sheet" (states_parts.dart:30-43). */
    public fun sheet(rect: Rect, fold: Float = SHEET_FOLD, radius: Float = SHEET_RADIUS): Path =
        Path().apply {
            moveTo(rect.left + radius, rect.top)
            lineTo(rect.right - fold, rect.top)
            lineTo(rect.right, rect.top + fold)
            lineTo(rect.right, rect.bottom - radius)
            sceneArcTo(Offset(rect.right, rect.bottom - radius), Offset(rect.right - radius, rect.bottom), radius)
            lineTo(rect.left + radius, rect.bottom)
            sceneArcTo(Offset(rect.left + radius, rect.bottom), Offset(rect.left, rect.bottom - radius), radius)
            lineTo(rect.left, rect.top + radius)
            sceneArcTo(Offset(rect.left, rect.top + radius), Offset(rect.left + radius, rect.top), radius)
            close()
        }

    /** The folded-over flap of [sheet]'s corner (states_parts.dart:46-55). */
    public fun fold(rect: Rect, fold: Float = SHEET_FOLD): Path =
        Path().apply {
            moveTo(rect.right - fold, rect.top)
            lineTo(rect.right - fold, rect.top + fold - 3) // F: lib/ui/kit/scenes/states_parts.dart:48
            quadraticTo(rect.right - fold, rect.top + fold, rect.right - fold + 3, rect.top + fold) // F: lib/ui/kit/scenes/states_parts.dart:52
            lineTo(rect.right, rect.top + fold)
        }

    /** A filled, outlined shape: [fill] under a [pen]-wide outline in [ink] that draws itself in over [t]. */
    public fun DrawScope.outlined(path: Path, t: Float, fill: Color, ink: Color, pen: Float = KitDrawnInk.STROKE) {
        if (t <= 0f) return
        drawPath(path, KitDrawnInk.fade(fill, t))
        drawPartial(path, t, ink, pen)
    }

    /** The path of a four-pointed spark at [center], [size] from the middle to a tip, scaled by [t]. */
    public fun sparkPath(center: Offset, size: Float, t: Float): Path {
        val s = size * t
        val w = s * .28f // F: lib/ui/kit/scenes/states_parts.dart:82
        val k = w * .35f // F: lib/ui/kit/scenes/states_parts.dart:86
        return Path().apply {
            moveTo(center.x, center.y - s)
            quadraticTo(center.x + k, center.y - k, center.x + s, center.y)
            quadraticTo(center.x + k, center.y + k, center.x, center.y + s)
            quadraticTo(center.x - k, center.y + k, center.x - s, center.y)
            quadraticTo(center.x - k, center.y - k, center.x, center.y - s)
            close()
        }
    }

    /** A four-pointed spark at [center], scaled by [t] (0..1, may overshoot) (states_parts.dart:73-111). */
    public fun DrawScope.spark(center: Offset, size: Float, t: Float, color: Color) {
        if (t <= 0f) return
        drawPath(sparkPath(center, size, t), color)
    }

    /** The end points of the strokes [ticks] throws out from [center] at [angles] degrees (0 right, clockwise). */
    public fun tickLines(
        center: Offset,
        angles: List<Float>,
        inner: Float,
        outer: Float,
        t: Float,
    ): List<Pair<Offset, Offset>> =
        angles.map { degrees ->
            val a = degrees * PI.toFloat() / 180 // F: lib/ui/kit/scenes/states_parts.dart:126
            val dir = Offset(cos(a), sin(a))
            (center + dir * inner) to (center + dir * (inner + (outer - inner) * t))
        }

    /** Short strokes thrown out from [center] at [angles], between [inner] and [outer] (states_parts.dart:115-132). */
    public fun DrawScope.ticks(
        center: Offset,
        angles: List<Float>,
        inner: Float,
        outer: Float,
        t: Float,
        color: Color,
        width: Float = KitDrawnInk.STROKE,
    ) {
        if (t <= 0f) return
        for ((start, end) in tickLines(center, angles, inner, outer, t)) drawSceneLine(color, start, end, width)
    }
}
