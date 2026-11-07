package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The part of setup that is happening now, as the drawing shows it (Dart `SetupSceneStage`, in its order). */
public enum class SetupSceneStage {
    Download,
    Unpack,
    Install,
    Start,
}

/** Why a setup drawing stands still (Dart `SetupSceneHalt`). */
public enum class SetupSceneHalt {
    Paused,
    Failed,
}

/**
 * Setup's progress as a small journey (lib/ui/kit/scenes/setup_steps_scene.dart): a cloud, a parcel, the phone. Dots
 * carry the download from the cloud, the parcel's flaps open while it unpacks, the phone's screen fills one line per
 * finished component while it installs, and the portal forms on the screen when OpenCode starts. Every part it lights
 * comes from the job's real state; ambient, it carries dots along the active leg.
 */
public data class SetupStepsScene(
    val stage: SetupSceneStage,
    /** Finished components, of [total]. */
    val done: Int,
    val total: Int,
    /** How far the current component is (0..1), when it is measured. */
    val fraction: Float? = null,
    /** Stopped part way or failed: nothing moves and the colour drains. */
    val halted: SetupSceneHalt? = null,
) : SceneArt() {
    override val box: Size get() = Size(240f, 100f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:45

    /** The step strip shows progress along a line (LAY-8): it mirrors under RTL. */
    override val mirrorsInRtl: Boolean get() = true

    internal companion object {
        val cloud: Path by lazy {
            Path.combine(
                PathOperation.Union,
                Path.combine(
                    PathOperation.Union,
                    Path().apply {
                        addOval(Rect(Offset(30f, 58f), 10f)) // F: lib/ui/kit/scenes/setup_steps_scene.dart:66
                        addOval(Rect(Offset(57f, 58f), 11f)) // F: lib/ui/kit/scenes/setup_steps_scene.dart:67
                    },
                    Path().apply { addOval(Rect(Offset(43f, 48f), 14f)) }, // F: lib/ui/kit/scenes/setup_steps_scene.dart:69
                ),
                Path().apply { addRect(Rect(30f, 54f, 57f, 69f)) }, // F: lib/ui/kit/scenes/setup_steps_scene.dart:71
            )
        }
        val arrowHead: Path by lazy {
            Path().apply {
                moveTo(38f, 56.5f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:74
                lineTo(43f, 61.5f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:75
                lineTo(48f, 56.5f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:76
            }
        }
        val parcel: Rect = Rect(104f, 50f, 138f, 78f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:78
        const val PARCEL_RADIUS: Float = 4f // F: lib/ui/kit/scenes/setup_steps_scene.dart:79
        val phoneFrame: Path by lazy {
            SetupCast.phone(
                Rect(178f, 6f, 222f, 96f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:82
                10f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:83
            )
        }
        val screen: Rect = Rect(184.5f, 13f, 215.5f, 89f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:86
        const val SCREEN_RADIUS: Float = 5f // F: lib/ui/kit/scenes/setup_steps_scene.dart:87
        val legOne: Pair<Offset, Offset> = Offset(76f, 62f) to Offset(96f, 62f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:89
        val legTwo: Pair<Offset, Offset> = Offset(146f, 62f) to Offset(168f, 62f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:90
        val portal: Offset = Offset(200f, 76f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:91

        /** Where the wash sits under the part that is working now, per stage. */
        val washAt: List<Offset> =
            listOf(
                Offset(44f, 58f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:123
                Offset(121f, 64f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:124
                Offset(200f, 50f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:125
                Offset(200f, 50f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:126
            )

        /** The screen shows at most this many component lines. */
        const val MAX_ROWS: Int = 6 // F: lib/ui/kit/scenes/setup_steps_scene.dart:228

        /** Dots per leg. */
        const val LEG_DOTS: Int = 5 // F: lib/ui/kit/scenes/setup_steps_scene.dart:301

        /** How many of [rows] screen lines show as finished for [done] of [total] (Dart line 229). */
        fun shownRows(done: Int, total: Int, rows: Int): Int =
            if (rows == 0) 0 else floor(done * rows / max(total, 1).toFloat()).toInt()
    }

    private val index: Int get() = stage.ordinal

    override fun paint(canvas: Canvas, frame: SceneFrame) {
        val p = frame.palette
        val t = frame.entrance
        val halt = halted
        val active =
            when (halt) {
                SetupSceneHalt.Failed -> p.failure
                SetupSceneHalt.Paused -> p.muted
                null -> p.accent
            }
        // A halted strip washes in the quiet neutral, never louder than the accent's own wash (LOOK-36).
        val soft = if (halt == null) p.accentSoft else SceneDraw.fade(p.line, FlutterKitTokens.SCENE_WASH_ALPHA)
        val loop = frame.looping && halt == null
        val breath = if (loop) SceneDraw.wave(frame.loop) else 0f
        val measured = (fraction ?: .5f).coerceIn(0f, 1f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:113

        fun station(i: Int): Color =
            when {
                i == index -> active
                i < index -> p.muted
                else -> SceneDraw.fade(p.muted, .45f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:119
            }

        // A wash under the part that is working now.
        val wash = SceneDraw.interval(t, .3f, .7f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:128
        canvas.drawCircle(
            washAt[index],
            (if (index >= 2) 34f else 30f) + 2 * breath, // F: lib/ui/kit/scenes/setup_steps_scene.dart:131
            SceneDraw.fill(SceneDraw.fade(soft, wash)),
        )

        // 1. The cloud.
        SceneDraw.partialPath(
            canvas,
            cloud,
            SceneDraw.interval(t, 0f, .4f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:139
            SceneDraw.pen(station(0), 4f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:140
        )
        // While downloading, an arrow inside it drops a little with each breath.
        if (stage == SetupSceneStage.Download) {
            val arrow = SceneDraw.interval(t, .4f, .7f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:144
            if (arrow > 0f) {
                val pen = SceneDraw.pen(SceneDraw.fade(active, arrow), 3f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:146
                canvas.save()
                canvas.translate(0f, 2 * breath) // F: lib/ui/kit/scenes/setup_steps_scene.dart:148
                canvas.drawLine(Offset(43f, 48f), Offset(43f, 61f), pen) // F: lib/ui/kit/scenes/setup_steps_scene.dart:149
                canvas.drawPath(arrowHead, pen)
                canvas.restore()
            }
        }

        // 2. The parcel, its tape and flaps: shut, then open once unpacked.
        val parcelIn = SceneDraw.interval(t, .15f, .5f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:156
        if (parcelIn > 0f) {
            val pen = SceneDraw.pen(SceneDraw.fade(station(1), parcelIn), 4f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:158
            canvas.drawRRect(parcel, PARCEL_RADIUS, pen)
            val opened =
                when {
                    index > 1 -> 1f
                    index == 1 -> max(.35f, measured) // F: lib/ui/kit/scenes/setup_steps_scene.dart:163
                    else -> 0f
                }
            val angle =
                opened * 2.2f + // F: lib/ui/kit/scenes/setup_steps_scene.dart:166
                    (if (stage == SetupSceneStage.Unpack) .15f * breath else 0f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:166
            val pivotL = Offset(104f, 50f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:167
            val pivotR = Offset(138f, 50f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:168
            canvas.drawLine(pivotL, pivotL + Offset(cos(angle), -sin(angle)) * 17f, pen) // F: lib/ui/kit/scenes/setup_steps_scene.dart:171
            canvas.drawLine(pivotR, pivotR + Offset(-cos(angle), -sin(angle)) * 17f, pen) // F: lib/ui/kit/scenes/setup_steps_scene.dart:176
            canvas.drawLine(
                Offset(121f, 50f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:180
                Offset(121f, 60f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:181
                SceneDraw.pen(SceneDraw.fade(station(1), .6f * parcelIn), SceneDraw.HAIRLINE), // F: lib/ui/kit/scenes/setup_steps_scene.dart:182
            )
        }

        // 3. The phone and its lit screen.
        val lit = SceneDraw.interval(t, .3f, .6f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:187
        if (lit > 0f) {
            canvas.drawRRect(screen, SCREEN_RADIUS, SceneDraw.fill(SceneDraw.fade(p.surface, lit)))
            if (index >= 2) { // F: lib/ui/kit/scenes/setup_steps_scene.dart:190
                canvas.drawRRect(screen, SCREEN_RADIUS, SceneDraw.fill(SceneDraw.fade(soft, lit)))
            }
        }
        SceneDraw.partialPath(
            canvas,
            phoneFrame,
            SceneDraw.interval(t, .3f, .7f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:197
            SceneDraw.pen(p.muted, 4.5f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:198
        )

        // The legs between them: dotted, lit as far as the job has come.
        leg(
            canvas,
            legOne,
            if (index > 0) 1f else measured,
            t,
            p,
            active,
            travelling = loop && stage == SetupSceneStage.Download,
            loopAt = frame.loop,
        )
        leg(
            canvas,
            legTwo,
            when {
                index > 1 -> 1f
                index == 1 -> measured
                else -> 0f
            },
            t,
            p,
            active,
            travelling = loop && stage == SetupSceneStage.Install,
            loopAt = frame.loop,
        )

        // One line on the screen per component: done, this one, still to come.
        val rows = min(total, MAX_ROWS)
        val shown = shownRows(done, total, rows)
        for (i in 0 until rows) {
            val y = 22f + i * 8.5f // F: lib/ui/kit/scenes/setup_steps_scene.dart:231
            val appear = SceneDraw.interval(t, .5f + i * .06f, .75f + i * .05f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:232
            if (appear <= 0f) continue
            val finished = i < shown
            val current = i == shown && stage != SetupSceneStage.Start
            val end = 192 + 16 * appear // F: lib/ui/kit/scenes/setup_steps_scene.dart:236
            canvas.drawLine(
                Offset(192f, y), // F: lib/ui/kit/scenes/setup_steps_scene.dart:238
                Offset(end, y),
                SceneDraw.pen(
                    if (finished) SceneDraw.fade(if (halt == null) p.accent else p.muted, .95f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:242
                    else SceneDraw.fade(p.line, 1f),
                    3f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:244
                ),
            )
            if (current) {
                val part = fraction ?: (.35f + .3f * breath) // F: lib/ui/kit/scenes/setup_steps_scene.dart:248
                canvas.drawLine(
                    Offset(192f, y), // F: lib/ui/kit/scenes/setup_steps_scene.dart:250
                    Offset(192 + 16 * appear * part, y), // F: lib/ui/kit/scenes/setup_steps_scene.dart:251
                    SceneDraw.pen(active, 3f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:252
                )
            }
            canvas.drawCircle(
                Offset(188f, y), // F: lib/ui/kit/scenes/setup_steps_scene.dart:256
                1.8f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:257
                SceneDraw.fill(
                    when {
                        finished -> if (halt == null) p.accent else p.muted
                        current -> active
                        else -> p.line
                    }
                ),
            )
        }

        // 4. OpenCode starts: the portal forms on the screen and breathes.
        if (stage == SetupSceneStage.Start) {
            val form = SceneDraw.interval(t, .55f, .95f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:270
            SetupCast.portal(
                canvas,
                center = portal,
                size = 20f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:274
                color = active,
                upper = form,
                lower = SceneDraw.interval(t, .65f, 1f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:277
                open = 1.2f * breath, // F: lib/ui/kit/scenes/setup_steps_scene.dart:278
                width = 3.2f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:279
            )
            val land = SceneDraw.interval(t, .85f, 1f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_steps_scene.dart:281
            canvas.drawCircle(
                if (loop) SetupCast.orbit(portal, 1.5f, frame.loop) else portal, // F: lib/ui/kit/scenes/setup_steps_scene.dart:283
                2 * land, // F: lib/ui/kit/scenes/setup_steps_scene.dart:284
                SceneDraw.fill(active),
            )
        }
    }

    private fun leg(
        canvas: Canvas,
        leg: Pair<Offset, Offset>,
        lit: Float,
        t: Float,
        p: ScenePalette,
        active: Color,
        travelling: Boolean,
        loopAt: Float,
    ) {
        val (from, to) = leg
        val shown = SceneDraw.interval(t, .35f, .7f) // F: lib/ui/kit/scenes/setup_steps_scene.dart:302
        if (shown <= 0f) return
        for (i in 0 until LEG_DOTS) {
            val at = i / (LEG_DOTS - 1).toFloat()
            val on = at <= lit + 1e-6f && lit > 0f // F: lib/ui/kit/scenes/setup_steps_scene.dart:306
            canvas.drawCircle(
                lerp(from, to, at),
                if (on) 2.1f else 1.6f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:309
                SceneDraw.fill(SceneDraw.fade(if (on) active else p.line, shown)),
            )
        }
        if (travelling) {
            for (k in 0 until 2) { // F: lib/ui/kit/scenes/setup_steps_scene.dart:314
                val at = (loopAt * 2 + k / 2f) % 1 // F: lib/ui/kit/scenes/setup_steps_scene.dart:315
                val fade = sin(at * PI).toFloat()
                canvas.drawCircle(
                    lerp(from, to, at) + Offset(0f, -7f), // F: lib/ui/kit/scenes/setup_steps_scene.dart:318
                    2.4f, // F: lib/ui/kit/scenes/setup_steps_scene.dart:319
                    SceneDraw.fill(SceneDraw.fade(active, fade)),
                )
            }
        }
    }
}
