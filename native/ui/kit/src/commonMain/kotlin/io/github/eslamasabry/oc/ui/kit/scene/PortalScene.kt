package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as DrawingCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.motion.CurveId
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------------------------------
// The scene contract (lib/ui/kit/kit_illustration.dart). Order 07 owns `KitIllustration.kt`; until it lands, the scenes
// of orders 29 and 30 draw through this interim host, under names that cannot clash with the ones order 07 will add
// (`KitScene`, `KitPalette`, `KitSceneFrame`, `KitDraw`, `KitIllustration`).
// ---------------------------------------------------------------------------------------------------------------------

/**
 * A drawing (Flutter `KitScene`): it paints one frame from a [SceneFrame] into its own design [box]; the host scales the
 * box uniformly to its width and centres it. Two scenes are equal when they are the same kind with the same data (the
 * Dart `differs`), so a host repaints only when the drawing really changed.
 */
@Immutable
public abstract class SceneArt {
    /** The design box the scene draws in, in scene units. */
    public open val box: Size get() = Size(120f, 120f) // F: lib/ui/kit/kit_illustration.dart:28

    /** True for a drawing that shows progress along a line: the host flips it under RTL (LAY-8). */
    public open val mirrorsInRtl: Boolean get() = false

    /** Paints one frame into [box]-sized scene units. */
    internal abstract fun paint(canvas: Canvas, frame: SceneFrame)

    override fun equals(other: Any?): Boolean = other != null && other::class == this::class

    override fun hashCode(): Int = this::class.hashCode()
}

/** The colours a scene draws with (Flutter `KitPalette.fromRoles`): always theme roles, never literals. */
@Immutable
public data class ScenePalette(
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
        /** kit_illustration.dart `KitPalette.fromRoles`: failure is text1 (no red, LOOK-5), the wash at sceneWashAlpha. */
        public fun of(c: KitColors): ScenePalette =
            ScenePalette(
                accent = c.accent,
                accentSoft = c.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA),
                ink = c.text1,
                muted = c.text2,
                line = c.text3,
                surface = c.surface3,
                success = c.success,
                warning = c.attention,
                failure = c.text1,
                progress = c.accent,
            )
    }
}

/** One frame of a scene (Flutter `KitSceneFrame`). */
@Immutable
public data class SceneFrame(
    /** 0 → 1 over the entrance, linear; 1 when finished or reduced. */
    val entrance: Float,
    /** 0 → 1, repeating once per loop period while [looping]; 0 otherwise. */
    val loop: Float,
    /** Whether the ambient loop runs; a still scene must look complete at `loop = 0`. */
    val looping: Boolean,
    val palette: ScenePalette,
)

/** Drawing helpers every scene shares (Flutter `KitDraw`), so line weight and staging are the same everywhere. */
public object SceneDraw {
    /** The standard stroke in scene units (a 120-unit box). */
    public const val STROKE: Float = 5f // F: lib/ui/kit/kit_illustration.dart:164

    /** A thin stroke for detail. */
    public const val HAIRLINE: Float = 2.5f // F: lib/ui/kit/kit_illustration.dart:167

    /** Flutter `KitMotion.enter` (easeOutCubic). */
    public val enter: Easing = KitEasing.Enter

    /** Flutter `KitMotion.land` (easeOutBack, overshoots). */
    public val land: Easing = CubicBezierEasing(CurveId.Land.x1, CurveId.Land.y1, CurveId.Land.x2, CurveId.Land.y2)

    public fun pen(color: Color, width: Float = STROKE): Paint =
        Paint().apply {
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Round
            strokeJoin = StrokeJoin.Round
            strokeWidth = width
            this.color = color
            isAntiAlias = true
        }

    public fun fill(color: Color): Paint =
        Paint().apply {
            style = PaintingStyle.Fill
            this.color = color
            isAntiAlias = true
        }

    /** [t] of [begin]..[end], clamped and eased: stages a part of an entrance. */
    public fun interval(t: Float, begin: Float, end: Float, curve: Easing = enter): Float =
        when {
            t <= begin -> 0f
            t >= end -> 1f
            else -> curve.transform((t - begin) / (end - begin))
        }

    /** A smooth 0 → 1 → 0 wave over one loop, shifted by [phase] (0..1). */
    public fun wave(loop: Float, phase: Float = 0f): Float =
        ((1 - cos((loop + phase) * 2 * PI)) / 2).toFloat() // F: lib/ui/kit/kit_illustration.dart:197

    /** Draws the first [t] (0..1) of [path]'s length: a line drawing itself. */
    public fun partialPath(canvas: Canvas, path: Path, t: Float, paint: Paint) {
        if (t <= 0f) return
        if (t >= 1f) {
            canvas.drawPath(path, paint)
            return
        }
        val metric = PathMeasure().apply { setPath(path, false) }
        val shown = Path()
        metric.getSegment(0f, metric.length * t, shown, true)
        canvas.drawPath(shown, paint)
    }

    /** [color] at [opacity] of its own alpha. */
    public fun fade(color: Color, opacity: Float): Color = color.copy(alpha = color.alpha * opacity.coerceIn(0f, 1f))

    /** A flat accent fill (LOOK-36), never above the scene wash alpha. */
    public fun wash(palette: ScenePalette, opacity: Float = 1f): Paint = fill(fade(palette.accentSoft, opacity))
}

/**
 * Dart `Path.arcToPoint` for a circular [radius]: the shorter arc from the current point to [to], turning clockwise on
 * screen when [clockwise] (Dart's default).
 */
internal fun Path.arcToPoint(from: Offset, to: Offset, radius: Float, clockwise: Boolean = true) {
    val mid = (from + to) / 2f // NV: chord midpoint (SVG arc endpoint-to-centre conversion)
    val half = (to - from).getDistance() / 2f // NV: half the chord length (SVG arc conversion)
    if (half == 0f) return
    val r = maxOf(radius, half)
    val h = sqrt(maxOf(r * r - half * half, 0f))
    val n = Offset(-(to.y - from.y), to.x - from.x) / (half * 2f) // NV: unit normal of the chord (SVG arc conversion)
    fun sweepFrom(c: Offset): Pair<Float, Float> {
        val a0 = atan2(from.y - c.y, from.x - c.x)
        val a1 = atan2(to.y - c.y, to.x - c.x)
        var s = a1 - a0
        while (s > PI) s -= (2 * PI).toFloat() // NV: wrap the sweep into (-pi, pi]
        while (s <= -PI) s += (2 * PI).toFloat() // NV: wrap the sweep into (-pi, pi]
        return a0 to s
    }
    val first = mid + n * h
    val (firstStart, firstSweep) = sweepFrom(first)
    val takeFirst = (firstSweep > 0f) == clockwise
    val centre = if (takeFirst) first else mid - n * h
    val (start, otherSweep) = if (takeFirst) firstStart to firstSweep else sweepFrom(centre)
    // A half circle (h = 0) has one centre: only the direction changes.
    val sweep = if ((otherSweep > 0f) == clockwise) otherSweep else -otherSweep
    val deg = (180 / PI).toFloat() // NV: radians to degrees for Path.arcTo
    arcTo(Rect(centre, r), start * deg, sweep * deg, false)
}

/** Dart `canvas.drawRRect(RRect.fromRectAndRadius(rect, Radius.circular(radius)), paint)`. */
internal fun Canvas.drawRRect(rect: Rect, radius: Float, paint: Paint) {
    drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, radius, radius, paint)
}

/** Builds a path from a start point and the Dart builder calls that follow it. */
internal inline fun scenePath(start: Offset, build: ScenePathBuilder.() -> Unit): Path =
    ScenePathBuilder(Path(), start).apply { path.moveTo(start.x, start.y); build() }.path

/** Tracks the current point so [arcTo] can follow Dart's `arcToPoint`. */
internal class ScenePathBuilder(val path: Path, private var at: Offset) {
    fun lineTo(x: Float, y: Float) {
        path.lineTo(x, y)
        at = Offset(x, y)
    }

    fun lineTo(p: Offset): Unit = lineTo(p.x, p.y)

    fun arcTo(to: Offset, radius: Float, clockwise: Boolean = true) {
        path.arcToPoint(at, to, radius, clockwise)
        at = to
    }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
        path.quadraticTo(cx, cy, x, y)
        at = Offset(x, y)
    }

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
        path.cubicTo(x1, y1, x2, y2, x, y)
        at = Offset(x, y)
    }

    fun close() {
        path.close()
    }
}

/**
 * Shows a [SceneArt] at [width] (Flutter `KitIllustration`): plays its entrance once and, when [ambient] and motion
 * allows ambient loops, repeats its loop. Reduced motion, still frames (tests, screenshots) and a celebration the
 * person turned off show the finished drawing at once. Decorative unless [semanticLabel] is given.
 */
@Composable
internal fun SceneIllustration(
    scene: SceneArt,
    modifier: Modifier = Modifier,
    width: Dp = FlutterKitTokens.ILLUSTRATION_PAGE.dp,
    ambient: Boolean = false,
    loopPeriodMs: Int = FlutterKitMotion.BREATH_MS,
    animateEntrance: Boolean = true,
    entranceMs: Int = FlutterKitMotion.ENTRANCE_MS,
    semanticLabel: String? = null,
) {
    val motion = KitMotion.state
    val skipCelebration = entranceMs >= FlutterKitMotion.CELEBRATION_MS && !LocalKitAppearance.current.celebrations
    val instant = motion.reduced || motion.stillFrame || skipCelebration
    val entrance = remember { Animatable(if (instant || !animateEntrance) 1f else 0f) }
    val loop = remember { Animatable(0f) }
    LaunchedEffect(instant) {
        if (instant) entrance.snapTo(1f) else entrance.animateTo(1f, tween(entranceMs, easing = LinearEasing))
    }
    val looping = !instant && ambient && motion.ambientLoops && entrance.value >= 1f
    LaunchedEffect(looping, loopPeriodMs) {
        loop.snapTo(0f)
        if (looping) {
            while (true) {
                loop.animateTo(1f, tween(loopPeriodMs, easing = LinearEasing))
                loop.snapTo(0f)
            }
        }
    }
    val palette = ScenePalette.of(LocalKitColors.current)
    val mirror = scene.mirrorsInRtl && LocalLayoutDirection.current == LayoutDirection.Rtl
    val box = scene.box
    val semantics =
        if (semanticLabel == null) Modifier.clearAndSetSemantics {}
        else
            Modifier.clearAndSetSemantics {
                contentDescription = semanticLabel
                role = Role.Image
            }
    DrawingCanvas(modifier.sceneSize(width, box).then(semantics)) {
        val frame = SceneFrame(if (instant) 1f else entrance.value, if (looping) loop.value else 0f, looping, palette)
        // Flutter paints at the exact (fractional) size; the layout box is that size rounded up, so draw the exact
        // size centred in it instead of letting a rounded-down height shrink the drawing.
        val exact = Size(width.toPx(), width.toPx() * box.height / box.width)
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.translate((size.width - exact.width) / 2, (size.height - exact.height) / 2) // F: lib/ui/kit/kit_illustration.dart:396,397
            paintScene(canvas, exact, scene, frame, mirror)
            canvas.restore()
        }
    }
}

/** The scene's box at [width], each side rounded up to whole pixels (Flutter lays it out at its fractional size). */
private fun Modifier.sceneSize(width: Dp, box: Size): Modifier =
    layout { measurable, constraints ->
        val w = ceil(width.toPx()).toInt()
        val h = ceil(width.toPx() * box.height / box.width).toInt()
        val placeable = measurable.measure(Constraints.fixed(constraints.constrainWidth(w), constraints.constrainHeight(h)))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** The host's painter (kit_illustration.dart `_ScenePainter.paint`): fit, centre, mirror, clip, paint. */
internal fun paintScene(canvas: Canvas, size: Size, scene: SceneArt, frame: SceneFrame, mirror: Boolean) {
    val box = scene.box
    val scale = min(size.width / box.width, size.height / box.height)
    canvas.save()
    canvas.translate(
        (size.width - box.width * scale) / 2, // F: lib/ui/kit/kit_illustration.dart:396
        (size.height - box.height * scale) / 2, // F: lib/ui/kit/kit_illustration.dart:397
    )
    canvas.scale(scale, scale)
    if (mirror) {
        canvas.translate(box.width, 0f)
        canvas.scale(-1f, 1f)
    }
    canvas.clipRect(0f, 0f, box.width, box.height)
    scene.paint(canvas, frame)
    canvas.restore()
}

// ---------------------------------------------------------------------------------------------------------------------
// KitPortalScene (lib/ui/kit/scenes/portal_scene.dart)
// ---------------------------------------------------------------------------------------------------------------------

/**
 * The brand's "open portal": two open brackets forming an aperture. Entrance: each bracket draws itself, then a spark
 * lands in the middle. Ambient: the brackets breathe apart and back while the spark circles inside.
 */
public class KitPortalScene : SceneArt() {
    internal companion object {
        // mark.svg's geometry (viewBox 24..84) mapped into the 120-unit box.
        const val SCALE: Float = 1.5f // F: lib/ui/kit/scenes/portal_scene.dart:20

        fun p(x: Float, y: Float): Offset =
            Offset((x - 24) * SCALE + 15, (y - 24) * SCALE + 15) // F: lib/ui/kit/scenes/portal_scene.dart:22

        const val RADIUS: Float = 12 * SCALE // F: lib/ui/kit/scenes/portal_scene.dart:23

        val upper: Path by lazy {
            scenePath(p(61f, 31f)) { // F: lib/ui/kit/scenes/portal_scene.dart:26
                lineTo(p(43f, 31f)) // F: lib/ui/kit/scenes/portal_scene.dart:27
                arcTo(p(31f, 43f), RADIUS, clockwise = false) // F: lib/ui/kit/scenes/portal_scene.dart:28
                lineTo(p(31f, 64f)) // F: lib/ui/kit/scenes/portal_scene.dart:29
            }
        }

        val lower: Path by lazy {
            scenePath(p(77f, 44f)) { // F: lib/ui/kit/scenes/portal_scene.dart:32
                lineTo(p(77f, 65f)) // F: lib/ui/kit/scenes/portal_scene.dart:33
                arcTo(p(65f, 77f), RADIUS, clockwise = true) // F: lib/ui/kit/scenes/portal_scene.dart:34
                lineTo(p(44f, 77f)) // F: lib/ui/kit/scenes/portal_scene.dart:35
            }
        }

        val center: Offset = Offset(60f, 60f) // F: lib/ui/kit/scenes/portal_scene.dart:37
    }

    override fun paint(canvas: Canvas, frame: SceneFrame) {
        val palette = frame.palette
        val t = frame.entrance
        val breath = if (frame.looping) SceneDraw.wave(frame.loop) else 0f

        // A soft halo that swells with each breath (only while waiting).
        if (frame.looping) {
            canvas.drawCircle(
                center,
                44 + 8 * breath, // F: lib/ui/kit/scenes/portal_scene.dart:49
                SceneDraw.pen(
                    SceneDraw.fade(palette.accent, .22f * (1 - breath)), // F: lib/ui/kit/scenes/portal_scene.dart:51
                    SceneDraw.HAIRLINE,
                ),
            )
        }

        val pen = SceneDraw.pen(palette.accent, 9f) // F: lib/ui/kit/scenes/portal_scene.dart:57
        val drift = 3f * breath // F: lib/ui/kit/scenes/portal_scene.dart:58
        canvas.save()
        canvas.translate(-drift, -drift)
        SceneDraw.partialPath(canvas, upper, SceneDraw.interval(t, 0f, .55f), pen) // F: lib/ui/kit/scenes/portal_scene.dart:61
        canvas.restore()
        canvas.save()
        canvas.translate(drift, drift)
        SceneDraw.partialPath(canvas, lower, SceneDraw.interval(t, .2f, .75f), pen) // F: lib/ui/kit/scenes/portal_scene.dart:65
        canvas.restore()

        // The spark: lands with a small overshoot, then circles while waiting.
        val land = SceneDraw.interval(t, .6f, 1f, SceneDraw.land) // F: lib/ui/kit/scenes/portal_scene.dart:69
        if (land > 0f) {
            val angle = frame.loop * 2 * PI - PI / 2 // F: lib/ui/kit/scenes/portal_scene.dart:71
            val orbit = if (frame.looping) 7f else 0f // F: lib/ui/kit/scenes/portal_scene.dart:72
            val spark = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * orbit
            canvas.drawCircle(spark, 5.5f * land, SceneDraw.fill(palette.accent)) // F: lib/ui/kit/scenes/portal_scene.dart:74
        }
    }
}
