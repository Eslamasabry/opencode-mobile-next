package io.github.eslamasabry.oc.ui.kit.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.motion.CurveId
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

public enum class KitSetupSceneStage {
    Download,
    Unpack,
    Install,
    Start,
}

public enum class KitSetupSceneHalt {
    Paused,
    Failed,
}

internal data class SetupSceneFrame(val entrance: Float, val loop: Float, val looping: Boolean) {
    fun interval(from: Float, to: Float, land: Boolean = false): Float =
        when {
            entrance <= from -> 0f
            entrance >= to -> 1f
            else ->
                (if (land)
                        CubicBezierEasing(
                            CurveId.Land.x1,
                            CurveId.Land.y1,
                            CurveId.Land.x2,
                            CurveId.Land.y2,
                        )
                    else KitEasing.Enter)
                    .transform((entrance - from) / (to - from))
        }

    val breath: Float
        get() =
            if (looping) ((1 - cos(loop * 2 * PI)) / 2).toFloat() // F: lib/ui/kit/kit_illustration.dart:196-197
            else 0f // F: lib/ui/kit/kit_illustration.dart:196-197
}

/**
 * A scene keeps its entrance when its data changes; removing its ambient permission cancels its
 * loop.
 */
@Composable
internal fun setupSceneFrame(ambient: Boolean, celebration: Boolean = false): SetupSceneFrame {
    val motion = KitMotion.state
    val instant = motion.reduced || motion.stillFrame || (celebration && !LocalKitAppearance.current.celebrations)
    val entrance = remember { Animatable(if (instant) 1f else 0f) }
    val loop = remember { Animatable(0f) }
    LaunchedEffect(instant) {
        if (instant) entrance.snapTo(1f)
        else entrance.animateTo(1f, tween(if (celebration) FlutterKitMotion.CELEBRATION_MS else FlutterKitMotion.ENTRANCE_MS, easing = LinearEasing))
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
    return SetupSceneFrame(
        if (instant) 1f else entrance.value,
        if (looping) loop.value else 0f,
        looping,
    )
}

internal fun Color.faded(alpha: Float): Color = copy(alpha = this.alpha * alpha.coerceIn(0f, 1f))

internal fun scenePen(width: Float = 5f): Stroke = // F: lib/ui/kit/kit_illustration.dart:164-174
    Stroke(
        width,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    ) // F: lib/ui/kit/kit_illustration.dart:164-174

internal fun DrawScope.scenePath(
    path: Path,
    value: Float,
    color: Color,
    width: Float = 5f, // F: lib/ui/kit/kit_illustration.dart:164
) { // F: lib/ui/kit/kit_illustration.dart:164
    if (value <= 0f) return
    if (value >= 1f) drawPath(path, color, style = scenePen(width))
    else {
        val metric = PathMeasure().apply { setPath(path, false) }
        val shown = Path()
        metric.getSegment(0f, metric.length * value, shown)
        drawPath(shown, color, style = scenePen(width))
    }
}

/** SetupCast's circular bracket corners, rather than an approximation with quadratic corners. */
internal fun DrawScope.scenePortal(
    at: Offset,
    span: Float,
    color: Color,
    upper: Float = 1f,
    lower: Float = 1f,
    open: Float = 0f,
    width: Float = span / 7f, // F: lib/ui/kit/scenes/setup_cast.dart:52
) { // F: lib/ui/kit/scenes/setup_cast.dart:52
    val top =
        Path().apply {
            moveTo(61f, 31f) // F: lib/ui/kit/scenes/setup_cast.dart:18,19
            lineTo(43f, 31f) // F: lib/ui/kit/scenes/setup_cast.dart:18,19
            folderArc(Offset(43f, 31f), Offset(31f, 43f), 12f) // F: lib/ui/kit/scenes/setup_cast.dart:18-25
            lineTo(31f, 64f) // F: lib/ui/kit/scenes/setup_cast.dart:25
        }
    val bottom =
        Path().apply {
            moveTo(77f, 44f) // F: lib/ui/kit/scenes/setup_cast.dart:28,29
            lineTo(77f, 65f) // F: lib/ui/kit/scenes/setup_cast.dart:28,29
            folderArc(Offset(77f, 65f), Offset(65f, 77f), 12f, clockwise = true) // F: lib/ui/kit/scenes/setup_cast.dart:28-35
            lineTo(44f, 77f) // F: lib/ui/kit/scenes/setup_cast.dart:35
        }
    withTransform({
        translate(at.x - open, at.y - open)
        scale(span / 60f, span / 60f, Offset.Zero) // F: lib/ui/kit/scenes/setup_cast.dart:15
        translate(-54f, -54f) // F: lib/ui/kit/scenes/setup_cast.dart:15
    }) { // F: lib/ui/kit/scenes/setup_cast.dart:15
        scenePath(top, upper, color, width * 60f / span) // F: lib/ui/kit/scenes/setup_cast.dart:15
    }
    withTransform({
        translate(at.x + open, at.y + open)
        scale(span / 60f, span / 60f, Offset.Zero) // F: lib/ui/kit/scenes/setup_cast.dart:15
        translate(-54f, -54f) // F: lib/ui/kit/scenes/setup_cast.dart:15
    }) { // F: lib/ui/kit/scenes/setup_cast.dart:15
        scenePath(
            bottom,
            lower,
            color,
            width * 60f / span, // F: lib/ui/kit/scenes/setup_cast.dart:15
        ) // F: lib/ui/kit/scenes/setup_cast.dart:15
    }
}

/**
 * Measured setup strip. The stage and completed component count come from the job, never the clock.
 */
@Composable
public fun KitSetupSteps(
    stage: KitSetupSceneStage,
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    fraction: Float? = null,
    halted: KitSetupSceneHalt? = null,
    ambient: Boolean = false,
    width: Dp = FlutterKitTokens.ILLUSTRATION_PAGE.dp,
) {
    val c = LocalKitColors.current
    val frame = setupSceneFrame(ambient && halted == null)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier.width(width).aspectRatio(240f / 100f).clearAndSetSemantics {} // F: lib/ui/kit/scenes/setup_steps_scene.dart:45
    ) { // F: lib/ui/kit/scenes/setup_steps_scene.dart:45
        withTransform({
            scale(
                size.width / 240f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:45
                size.height / 100f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:45
                Offset.Zero,
            ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:45
            if (rtl) {
                translate(240f, 0f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:45-50
                scale(-1f, 1f, Offset.Zero)
            } // F: lib/ui/kit/scenes/setup_steps_scene.dart:45-50
        }) {
            clipRect(0f, 0f, 240f, 100f) { // F: lib/ui/kit/scenes/setup_steps_scene.dart:45
                val index = stage.ordinal
                val active =
                    when (halted) {
                        KitSetupSceneHalt.Failed -> c.text1
                        KitSetupSceneHalt.Paused -> c.text2
                        null -> c.accent
                    }
                val soft =
                    (if (halted == null) c.accent else c.text3).copy(
                        alpha = FlutterKitTokens.SCENE_WASH_ALPHA
                    )
                val measured =
                    fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
                        ?: .5f // F: lib/ui/kit/scenes/setup_steps_scene.dart:113
                fun station(i: Int) =
                    if (i == index) active
                    else if (i < index) c.text2
                    else c.text2.faded(.45f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:115-119
                val washAt =
                    when (stage) {
                        KitSetupSceneStage.Download ->
                            Offset(44f, 58f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:123
                        KitSetupSceneStage.Unpack ->
                            Offset(121f, 64f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:124
                        else -> Offset(200f, 50f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:125
                    }
                drawCircle(
                    soft.faded(frame.interval(.3f, .7f)), // F: lib/ui/kit/scenes/setup_steps_scene.dart:128-132
                    (if (index >= 2) 34f else 30f) + 2 * frame.breath, // F: lib/ui/kit/scenes/setup_steps_scene.dart:128-132
                    washAt,
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:128-132
                val cloud =
                    Path.combine(
                        PathOperation.Union,
                        Path.combine(
                            PathOperation.Union,
                            Path().apply {
                                addOval(Rect(Offset(30f - 10f, 58f - 10f), Size(10f * 2, 10f * 2))) // NV: circle bounding rect from centre and radius in setup_steps_scene.dart:66-69
                                addOval(Rect(Offset(57f - 11f, 58f - 11f), Size(11f * 2, 11f * 2))) // NV: circle bounding rect from centre and radius in setup_steps_scene.dart:66-69
                            }, // F: lib/ui/kit/scenes/setup_steps_scene.dart:66,67
                            Path().apply {
                                addOval(Rect(Offset(43f - 14f, 48f - 14f), Size(14f * 2, 14f * 2))) // NV: circle bounding rect from centre and radius in setup_steps_scene.dart:66-69
                            },
                        ), // F: lib/ui/kit/scenes/setup_steps_scene.dart:69
                        Path().apply { addRect(Rect(30f, 54f, 57f, 69f)) }, // F: lib/ui/kit/scenes/setup_steps_scene.dart:71
                    ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:71
                scenePath(
                    cloud,
                    frame.interval(0f, .4f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:139,140
                    station(0),
                    4f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:139,140
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:139,140
                if (stage == KitSetupSceneStage.Download)
                    withTransform({
                        translate(0f, 2 * frame.breath) // F: lib/ui/kit/scenes/setup_steps_scene.dart:148
                    }) { // F: lib/ui/kit/scenes/setup_steps_scene.dart:148
                        val ink =
                            active.faded(
                                frame.interval(.4f, .7f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:144
                            ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:144
                        drawLine(
                            ink,
                            Offset(43f, 48f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:146,149
                            Offset(43f, 61f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:146,149
                            3f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:146,149
                            StrokeCap.Round,
                        ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:146,149
                        drawPath(
                            Path().apply {
                                moveTo(38f, 56.5f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:74-76,146
                                lineTo(43f, 61.5f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:74-76,146
                                lineTo(48f, 56.5f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:74-76,146
                            },
                            ink,
                            style = scenePen(3f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:74-76,146
                        ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:74-76,146
                    }
                val parcel =
                    station(1)
                        .faded(
                            frame.interval(.15f, .5f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:156
                        ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:156
                drawRoundRect(
                    parcel,
                    Offset(104f, 50f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:78,79,158
                    Size(138f - 104f, 78f - 50f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:78,79,158
                    CornerRadius(4f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:78,79,158
                    style = scenePen(4f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:78,79,158
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:78,79,158
                val opened =
                    if (index > 1) 1f
                    else if (index == 1) maxOf(.35f, measured) // F: lib/ui/kit/scenes/setup_steps_scene.dart:160-164
                    else 0f // F: lib/ui/kit/scenes/setup_steps_scene.dart:160-164
                val angle =
                    opened * 2.2f + // F: lib/ui/kit/scenes/setup_steps_scene.dart:165,166
                        if (stage == KitSetupSceneStage.Unpack) .15f * frame.breath // F: lib/ui/kit/scenes/setup_steps_scene.dart:165,166
                        else 0f // F: lib/ui/kit/scenes/setup_steps_scene.dart:165,166
                drawLine(
                    parcel,
                    Offset(104f, 50f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:158,167,171
                    Offset(104f + cos(angle) * 17f, 50f - sin(angle) * 17f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:158,167,171
                    4f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:158,167,171
                    StrokeCap.Round,
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:158,167,171
                drawLine(
                    parcel,
                    Offset(138f, 50f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:158,168,176
                    Offset(138f - cos(angle) * 17f, 50f - sin(angle) * 17f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:158,168,176
                    4f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:158,168,176
                    StrokeCap.Round,
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:158,168,176
                drawLine(
                    parcel.faded(.6f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:180-182
                    Offset(121f, 50f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:180-182
                    Offset(121f, 60f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:180-182
                    2.5f, // F: lib/ui/kit/kit_illustration.dart:167
                    StrokeCap.Round,
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:180-182
                  // lib/ui/kit/kit_illustration.dart:167
                val lit =
                    frame.interval(.3f, .6f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:187
                drawRoundRect(
                    c.surface3.faded(lit),
                    Offset(184.5f, 13f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87
                    Size(215.5f - 184.5f, 89f - 13f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87
                    CornerRadius(5f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87
                if (index >= 2) // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87,190
                    drawRoundRect(
                        soft.faded(lit),
                        Offset(184.5f, 13f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87,190
                        Size(215.5f - 184.5f, 89f - 13f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87,190
                        CornerRadius(5f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87,190
                    ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:86,87,190
                scenePath(
                    Path().apply {
                        addRoundRect(RoundRect(Rect(178f, 6f, 222f, 96f), CornerRadius(10f))) // F: lib/ui/kit/scenes/setup_steps_scene.dart:82,83,197,198
                    },
                    frame.interval(.3f, .7f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:82,83,197,198
                    c.text2,
                    4.5f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:82,83,197,198
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:82,83,197,198
                fun leg(from: Float, to: Float, value: Float, travelling: Boolean) {
                    val shown =
                        frame.interval(.35f, .7f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:302
                    repeat(5) { i -> // F: lib/ui/kit/scenes/setup_steps_scene.dart:301
                        val at =
                            i / (5f - 1f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:301,305
                        val on =
                            at <= value + 1e-6f && // F: lib/ui/kit/scenes/setup_steps_scene.dart:306
                                value > 0 // F: lib/ui/kit/scenes/setup_steps_scene.dart:306
                        drawCircle(
                            (if (on) active else c.text3).faded(shown),
                            if (on) 2.1f else 1.6f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,309
                            Offset(from + (to - from) * at, 62f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,309
                        ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,309
                    }
                    if (frame.looping && travelling)
                        repeat(2) { k -> // F: lib/ui/kit/scenes/setup_steps_scene.dart:314
                            val at =
                                (frame.loop * 2 + k / 2f) % // F: lib/ui/kit/scenes/setup_steps_scene.dart:315
                                    1 // F: lib/ui/kit/scenes/setup_steps_scene.dart:315
                            drawCircle(
                                active.faded(sin(at * PI).toFloat()),
                                2.4f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,318,319
                                Offset(from + (to - from) * at, 62f - 7f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,318,319
                            ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,318,319
                        }
                }
                leg(
                    76f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,205
                    96f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,205
                    if (index > 0) 1f else measured,
                    stage == KitSetupSceneStage.Download,
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:89,205
                leg(
                    146f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:90,215-219
                    168f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:90,215-219
                    if (index > 1) 1f else if (index == 1) measured else 0f,
                    stage == KitSetupSceneStage.Install,
                ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:90,215-219
                val rows = total.coerceIn(0, 6) // F: lib/ui/kit/scenes/setup_steps_scene.dart:228
                val shown =
                    floor(done.coerceAtLeast(0).toFloat() * rows / total.coerceAtLeast(1)).toInt()
                repeat(rows) { i ->
                    val y = 22f + i * 8.5f // F: lib/ui/kit/scenes/setup_steps_scene.dart:231
                    val appear =
                        frame.interval(
                            .5f + i * .06f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:232
                            .75f + i * .05f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:232
                        ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:232
                    if (appear <= 0f) return@repeat
                    val finished = i < shown
                    val current = i == shown && stage != KitSetupSceneStage.Start
                    drawLine(
                        if (finished) (if (halted == null) c.accent else c.text2).faded(.95f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:240-244
                        else c.text3, // F: lib/ui/kit/scenes/setup_steps_scene.dart:240-244
                        Offset(192f, y), // F: lib/ui/kit/scenes/setup_steps_scene.dart:236,238,244
                        Offset(192f + 16 * appear, y), // F: lib/ui/kit/scenes/setup_steps_scene.dart:236,238,244
                        3f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:236,238,244
                        StrokeCap.Round,
                    ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:236,238,244
                    if (current)
                        drawLine(
                            active,
                            Offset(192f, y), // F: lib/ui/kit/scenes/setup_steps_scene.dart:248-252
                            Offset(
                                192f + 16 * appear * (fraction ?: (.35f + .3f * frame.breath)), // F: lib/ui/kit/scenes/setup_steps_scene.dart:248-252
                                y,
                            ),
                            3f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:248-252
                            StrokeCap.Round,
                        ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:248-252
                    if (appear > 0)
                        drawCircle(
                            if (finished) (if (halted == null) c.accent else c.text2)
                            else if (current) active else c.text3,
                            1.8f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:256,257
                            Offset(188f, y), // F: lib/ui/kit/scenes/setup_steps_scene.dart:256,257
                        ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:256,257
                }
                if (stage == KitSetupSceneStage.Start) {
                    scenePortal(
                        Offset(200f, 76f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,270-279
                        20f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,270-279
                        active,
                        frame.interval(.55f, .95f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,270-279
                        frame.interval(.65f, 1f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,270-279
                        1.2f * frame.breath, // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,270-279
                        3.2f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,270-279
                    ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,270-279
                    val angle =
                        frame.loop * 2 * PI - PI / 2 // F: lib/ui/kit/scenes/setup_cast.dart:120
                    val at =
                        Offset(200f, 76f) + // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,283
                            if (frame.looping)
                                Offset(cos(angle).toFloat(), sin(angle).toFloat()) * 1.5f // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,283
                            else Offset.Zero // F: lib/ui/kit/scenes/setup_steps_scene.dart:91,283
                    drawCircle(
                        active,
                        2 * frame.interval(.85f, 1f, land = true), // F: lib/ui/kit/scenes/setup_steps_scene.dart:281,284
                        at,
                    ) // F: lib/ui/kit/scenes/setup_steps_scene.dart:281,284
                }
            }
        }
    }
}

/** The waiting plug uses neutral ink for the unanswered portal and keeps amber out of the scene. */
@Composable
public fun KitSetupUnplugged(
    modifier: Modifier = Modifier,
    ambient: Boolean = false,
    width: Dp = FlutterKitTokens.ILLUSTRATION_PAGE.dp,
) {
    val c = LocalKitColors.current
    val f = setupSceneFrame(ambient)
    Canvas(
        modifier.width(width).aspectRatio(160f / 120f).clearAndSetSemantics {} // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:18
    ) { // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:18
        withTransform({
            scale(size.width / 160f, size.height / 120f, Offset.Zero) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:18
        }) { // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:18
            val wash = f.interval(0f, .4f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:46
            drawCircle(
                c.text3.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA * wash),
                34 * (.7f + .3f * wash), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,49
                Offset(120f, 60f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,49
            ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,49
            scenePortal(
                Offset(120f, 60f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,57-61
                48f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,57-61
                c.text2.faded(.75f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,57-61
                f.interval(.1f, .55f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,57-61
                f.interval(.2f, .65f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,57-61
                width = 5f, // F: lib/ui/kit/kit_illustration.dart:164
            ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,57-61
              // lib/ui/kit/kit_illustration.dart:167
            if (f.looping)
                drawCircle(
                    c.accent.faded(.5f * f.breath), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,66,67
                    3.5f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,66,67
                    Offset(120f, 60f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,66,67
                ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:27,66,67
            withTransform({
                translate(5 * f.breath, 0f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:73
            }) { // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:73
                scenePath(
                    Path().apply {
                        moveTo(40f, 60f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:21,22,77
                        cubicTo(22f, 60f, 30f, 96f, 6f, 100f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:21,22,77
                    },
                    f.interval(.3f, .7f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:21,22,77
                    c.text2,
                ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:21,22,77
                val pop =
                    f.interval(
                        .5f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:80
                        .8f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:80
                        land = true,
                    ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:80
                if (pop > 0f)
                    withTransform({
                        scale(pop, pop, Offset(50f, 60f)) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:83
                    }) { // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:83
                        drawRoundRect(
                            c.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA),
                            Offset(40f, 50f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24,25
                            Size(60f - 40f, 70f - 50f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24,25
                            CornerRadius(4f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24,25
                        ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24,25
                        drawRoundRect(
                            c.accent,
                            Offset(40f, 50f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24,25
                            Size(60f - 40f, 70f - 50f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24,25
                            CornerRadius(4f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24,25
                            style = scenePen(),
                        ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:24,25
                        drawLine(
                            c.accent,
                            Offset(60f, 55.5f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88,89
                            Offset(69f, 55.5f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88,89
                            3.5f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88,89
                            StrokeCap.Round,
                        ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88,89
                        drawLine(
                            c.accent,
                            Offset(60f, 64.5f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88,90
                            Offset(69f, 64.5f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88,90
                            3.5f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88,90
                            StrokeCap.Round,
                        ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:88,90
                    }
            }
            val marks = f.interval(.75f, 1f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:97
            listOf(-.9f, 0f, .9f).forEach { angle // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:31
                -> // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:31
                val dir = Offset(cos(angle), sin(angle))
                val start =
                    Offset(80f + 6f, 60f) + // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34,105
                        dir *
                            (11f - // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34,105
                                (11f - 5f) * // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34,105
                                    marks) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34,105
                                           // lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34,105
                drawLine(
                    c.text2.faded(marks * (1 - .5f * f.breath)), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34,100,105
                    start,
                    Offset(80f + 6f, 60f) + dir * 11f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34,100,105
                    2.5f, // F: lib/ui/kit/kit_illustration.dart:167
                    StrokeCap.Round,
                ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:33,34,100,105
                  // lib/ui/kit/kit_illustration.dart:167
            }
            val ink =
                c.text2.faded(
                    .4f * f.interval(.6f, 1f) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:113,114
                ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:113,114
            drawCircle(
                ink,
                2.2f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:115
                Offset(142f, 22f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:115
            ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:115
            drawCircle(
                ink,
                1.8f, // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:116
                Offset(24f, 30f), // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:116
            ) // F: lib/ui/kit/scenes/setup_unplugged_scene.dart:116
        }
    }
}

@Composable
public fun KitSetupPortal(
    modifier: Modifier = Modifier,
    ambient: Boolean = false,
    width: Dp = FlutterKitTokens.ILLUSTRATION_PAGE.dp,
) {
    val c = LocalKitColors.current
    val f = setupSceneFrame(ambient)
    Canvas(modifier.width(width).aspectRatio(1f).clearAndSetSemantics {}) {
        withTransform({
            scale(size.width / 120f, size.height / 120f, Offset.Zero) // F: lib/ui/kit/kit_illustration.dart:28
        }) { // F: lib/ui/kit/kit_illustration.dart:28
            val center = Offset(60f, 60f) // F: lib/ui/kit/scenes/portal_scene.dart:37
            if (f.looping)
                drawCircle(
                    c.accent.faded(.22f * (1 - f.breath)), // F: lib/ui/kit/scenes/portal_scene.dart:49,51
                    44 + 8 * f.breath, // F: lib/ui/kit/scenes/portal_scene.dart:49,51
                    center,
                    style = scenePen(2.5f), // F: lib/ui/kit/kit_illustration.dart:167
                ) // F: lib/ui/kit/scenes/portal_scene.dart:49,51
                  // lib/ui/kit/kit_illustration.dart:167
            scenePortal(
                center,
                (60f * 1.5f), // NV: SetupCast 60-unit mark times portal_scene.dart:20 scale of 1.5
                c.accent,
                f.interval(0f, .55f), // F: lib/ui/kit/scenes/portal_scene.dart:20-22,57,58,61,65
                f.interval(.2f, .75f), // F: lib/ui/kit/scenes/portal_scene.dart:20-22,57,58,61,65
                3 * f.breath, // F: lib/ui/kit/scenes/portal_scene.dart:20-22,57,58,61,65
                9f, // F: lib/ui/kit/scenes/portal_scene.dart:20-22,57,58,61,65
            ) // F: lib/ui/kit/scenes/portal_scene.dart:20-22,57,58,61,65
            val angle = f.loop * 2 * PI - PI / 2 // F: lib/ui/kit/scenes/portal_scene.dart:71
            val spark =
                center +
                    if (f.looping) Offset(cos(angle).toFloat(), sin(angle).toFloat()) * 7f // F: lib/ui/kit/scenes/portal_scene.dart:72
                    else Offset.Zero // F: lib/ui/kit/scenes/portal_scene.dart:72
            drawCircle(
                c.accent,
                5.5f * f.interval(.6f, 1f, land = true), // F: lib/ui/kit/scenes/portal_scene.dart:69,74
                spark,
            ) // F: lib/ui/kit/scenes/portal_scene.dart:69,74
        }
    }
}

/** Flutter's small circular arcToPoint arcs (the folder front has sloping sides). */
private fun Path.folderArc(from: Offset, to: Offset, radius: Float, clockwise: Boolean = false) {
    val delta = to - from
    val distance = delta.getDistance()
    val middle = (from + to) / 2f // NV: circular arc centre is the chord midpoint plus its normal
    val height =
        sqrt(
            (radius * radius - distance * distance / 4f).coerceAtLeast(0f) // NV: Pythagorean chord-to-centre distance
        ) // NV: Pythagorean chord-to-centre distance
    val sign =
        if (clockwise) 1f else -1f // NV: select the normal for the specified circular-arc direction
    val center = middle + Offset(-delta.y, delta.x) * (sign * height / distance)
    val start = atan2(from.y - center.y, from.x - center.x)
    var sweep = atan2(to.y - center.y, to.x - center.x) - start
    if (clockwise && sweep < 0)
        sweep +=
            (2 * PI).toFloat() // NV: normalize the SVG small-arc sweep in its specified direction
    if (!clockwise && sweep > 0)
        sweep -=
            (2 * PI).toFloat() // NV: normalize the SVG small-arc sweep in its specified direction
    arcToRad(
        Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius),
        start,
        sweep,
        false,
    )
}

/** Empty folders rest: their sheet rises and the front opens once, with no ambient loop. */
@Composable
public fun KitSetupFolders(
    modifier: Modifier = Modifier,
    width: Dp = FlutterKitTokens.ILLUSTRATION_PAGE.dp,
) {
    val c = LocalKitColors.current
    val f = setupSceneFrame(false)
    val back = remember {
        Path().apply {
            moveTo(52f, 36f) // F: lib/ui/kit/scenes/folders_open_scene.dart:16-18
            lineTo(46f, 29f) // F: lib/ui/kit/scenes/folders_open_scene.dart:16-18
            lineTo(28f, 29f) // F: lib/ui/kit/scenes/folders_open_scene.dart:16-18
            folderArc(
                Offset(28f, 29f), // F: lib/ui/kit/scenes/folders_open_scene.dart:18,19
                Offset(22f, 35f), // F: lib/ui/kit/scenes/folders_open_scene.dart:18,19
                6f, // F: lib/ui/kit/scenes/folders_open_scene.dart:18,19
                clockwise = true,
            ) // F: lib/ui/kit/scenes/folders_open_scene.dart:18,19
            lineTo(22f, 86f) // F: lib/ui/kit/scenes/folders_open_scene.dart:20-24
            folderArc(
                Offset(22f, 86f), // F: lib/ui/kit/scenes/folders_open_scene.dart:20-24
                Offset(28f, 92f), // F: lib/ui/kit/scenes/folders_open_scene.dart:20-24
                6f, // F: lib/ui/kit/scenes/folders_open_scene.dart:20-24
            ) // F: lib/ui/kit/scenes/folders_open_scene.dart:20-24
            lineTo(92f, 92f) // F: lib/ui/kit/scenes/folders_open_scene.dart:26-30
            folderArc(
                Offset(92f, 92f), // F: lib/ui/kit/scenes/folders_open_scene.dart:26-30
                Offset(98f, 86f), // F: lib/ui/kit/scenes/folders_open_scene.dart:26-30
                6f, // F: lib/ui/kit/scenes/folders_open_scene.dart:26-30
            ) // F: lib/ui/kit/scenes/folders_open_scene.dart:26-30
            lineTo(98f, 42f) // F: lib/ui/kit/scenes/folders_open_scene.dart:32-36
            folderArc(Offset(98f, 42f), Offset(92f, 36f), 6f) // F: lib/ui/kit/scenes/folders_open_scene.dart:32-36
            close() // F: lib/ui/kit/scenes/folders_open_scene.dart:32-36
        }
    }
    val front = remember {
        Path().apply {
            moveTo(26f, 92f) // F: lib/ui/kit/scenes/folders_open_scene.dart:54,55
            lineTo(92f, 92f) // F: lib/ui/kit/scenes/folders_open_scene.dart:54,55
            folderArc(
                Offset(92f, 92f), // F: lib/ui/kit/scenes/folders_open_scene.dart:55-59
                Offset(97f, 88f), // F: lib/ui/kit/scenes/folders_open_scene.dart:55-59
                5f, // F: lib/ui/kit/scenes/folders_open_scene.dart:55-59
            ) // F: lib/ui/kit/scenes/folders_open_scene.dart:55-59
            lineTo(103f, 60f) // F: lib/ui/kit/scenes/folders_open_scene.dart:61-65
            folderArc(
                Offset(103f, 60f), // F: lib/ui/kit/scenes/folders_open_scene.dart:61-65
                Offset(98f, 54f), // F: lib/ui/kit/scenes/folders_open_scene.dart:61-65
                5f, // F: lib/ui/kit/scenes/folders_open_scene.dart:61-65
            ) // F: lib/ui/kit/scenes/folders_open_scene.dart:61-65
            lineTo(22f, 54f) // F: lib/ui/kit/scenes/folders_open_scene.dart:67-71
            folderArc(
                Offset(22f, 54f), // F: lib/ui/kit/scenes/folders_open_scene.dart:67-71
                Offset(17f, 60f), // F: lib/ui/kit/scenes/folders_open_scene.dart:67-71
                5f, // F: lib/ui/kit/scenes/folders_open_scene.dart:67-71
            ) // F: lib/ui/kit/scenes/folders_open_scene.dart:67-71
            lineTo(21f, 88f) // F: lib/ui/kit/scenes/folders_open_scene.dart:73-77
            folderArc(Offset(21f, 88f), Offset(26f, 92f), 5f) // F: lib/ui/kit/scenes/folders_open_scene.dart:73-77
            close() // F: lib/ui/kit/scenes/folders_open_scene.dart:73-77
        }
    }
    Canvas(modifier.width(width).aspectRatio(1f).clearAndSetSemantics {}) {
        withTransform({
            scale(size.width / 120f, size.height / 120f, Offset.Zero) // F: lib/ui/kit/kit_illustration.dart:28
        }) { // F: lib/ui/kit/kit_illustration.dart:28
            scenePath(
                back,
                f.interval(0f, .45f), // F: lib/ui/kit/scenes/folders_open_scene.dart:91
                c.text2,
            ) // F: lib/ui/kit/scenes/folders_open_scene.dart:91
            val rise = f.interval(.3f, .7f) // F: lib/ui/kit/scenes/folders_open_scene.dart:95
            if (rise > 0f)
                withTransform({
                    translate(0f, 14 * (1 - rise)) // F: lib/ui/kit/scenes/folders_open_scene.dart:98
                }) { // F: lib/ui/kit/scenes/folders_open_scene.dart:98
                    val sheet =
                        Path().apply {
                            moveTo(40f, 62f) // F: lib/ui/kit/scenes/folders_open_scene.dart:42-46
                            lineTo(40f, 30f) // F: lib/ui/kit/scenes/folders_open_scene.dart:42-46
                            lineTo(66f, 30f) // F: lib/ui/kit/scenes/folders_open_scene.dart:42-46
                            lineTo(76f, 40f) // F: lib/ui/kit/scenes/folders_open_scene.dart:42-46
                            lineTo(76f, 62f) // F: lib/ui/kit/scenes/folders_open_scene.dart:42-46
                        } // F: lib/ui/kit/scenes/folders_open_scene.dart:42-46
                    val fold =
                        Path().apply {
                            moveTo(66f, 30f) // F: lib/ui/kit/scenes/folders_open_scene.dart:48-50
                            lineTo(66f, 40f) // F: lib/ui/kit/scenes/folders_open_scene.dart:48-50
                            lineTo(76f, 40f) // F: lib/ui/kit/scenes/folders_open_scene.dart:48-50
                        } // F: lib/ui/kit/scenes/folders_open_scene.dart:48-50
                    drawPath(
                        sheet,
                        c.text1.faded(rise),
                        style = scenePen(2.5f), // F: lib/ui/kit/kit_illustration.dart:167
                    ) // F: lib/ui/kit/kit_illustration.dart:167
                    drawPath(
                        fold,
                        c.text1.faded(rise),
                        style = scenePen(2.5f), // F: lib/ui/kit/kit_illustration.dart:167
                    ) // F: lib/ui/kit/kit_illustration.dart:167
                }
            val swing =
                f.interval(
                    .45f, // F: lib/ui/kit/scenes/folders_open_scene.dart:108
                    .9f, // F: lib/ui/kit/scenes/folders_open_scene.dart:108
                    land = true,
                ) // F: lib/ui/kit/scenes/folders_open_scene.dart:108
            if (swing > 0f)
                withTransform({
                    scale(1f, swing.coerceIn(0f, 1.2f), Offset(0f, 92f)) // F: lib/ui/kit/scenes/folders_open_scene.dart:112,113
                }) { // F: lib/ui/kit/scenes/folders_open_scene.dart:112,113
                    drawPath(front, c.surface3)
                    drawPath(front, c.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA))
                    drawPath(front, c.accent, style = scenePen())
                }
            val land =
                f.interval(
                    .75f, // F: lib/ui/kit/scenes/folders_open_scene.dart:122
                    1f,
                    land = true,
                ) // F: lib/ui/kit/scenes/folders_open_scene.dart:122
            val spark = Offset(88f, 22f) // F: lib/ui/kit/scenes/folders_open_scene.dart:81
            drawCircle(
                c.accent,
                4.5f * land, // F: lib/ui/kit/scenes/folders_open_scene.dart:124
                spark,
            ) // F: lib/ui/kit/scenes/folders_open_scene.dart:124
            listOf(
                    Offset(88f, 12f) to Offset(88f, 7f), // F: lib/ui/kit/scenes/folders_open_scene.dart:130-132
                    Offset(98f, 22f) to Offset(103f, 22f), // F: lib/ui/kit/scenes/folders_open_scene.dart:130-132
                    Offset(95f, 15f) to Offset(99f, 11f), // F: lib/ui/kit/scenes/folders_open_scene.dart:130-132
                )
                .forEach { (from, to) -> // F: lib/ui/kit/scenes/folders_open_scene.dart:130-132
                    drawLine(
                        c.accent.faded(land),
                        spark + (from - spark) * land,
                        spark + (to - spark) * land,
                        2.5f, // F: lib/ui/kit/kit_illustration.dart:167
                        StrokeCap.Round,
                    ) // F: lib/ui/kit/kit_illustration.dart:167
                }
        }
    }
}
