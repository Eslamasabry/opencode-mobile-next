package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens

/** What the phone in [SetupPhoneScene] is doing (lib/ui/kit/scenes/setup_phone_scene.dart `SetupPhoneMood`). */
public enum class SetupPhoneMood {
    /** First run: the screen becomes a terminal and the portal forms on it. */
    Fresh,

    /** OpenCode starting (or the app connecting to it): the portal breathes, the cursor blinks, a dot circles. */
    Starting,

    /** OpenCode in Termux: terminal lines, the last one lit. */
    Termux,

    /** OpenCode installed and ready here: the portal on a lit screen. */
    Ready,

    /** Stopped: a dark screen, a quiet grey portal and a small moon. */
    Stopped,
}

/** A phone with a rounded screen, the setup's hero: what OpenCode on this phone is doing, in the brand's line. */
public data class SetupPhoneScene(val mood: SetupPhoneMood = SetupPhoneMood.Fresh) : SceneArt() {
    override val box: Size get() = Size(160f, 120f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:35

    internal companion object {
        val center: Offset = Offset(80f, 60f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:41
        val frameRect: Rect = Rect(54f, 8f, 106f, 112f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:42
        val screenRect: Rect = Rect(61f, 17f, 99f, 101f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:43
        val phoneFrame: Path by lazy { SetupCast.phone(frameRect, 12f) } // F: lib/ui/kit/scenes/setup_phone_scene.dart:44
        const val SCREEN_RADIUS: Float = 5f // F: lib/ui/kit/scenes/setup_phone_scene.dart:47
        val prompt: Path by lazy { SetupCast.prompt(Offset(66f, 26f), 8f) } // F: lib/ui/kit/scenes/setup_phone_scene.dart:49
        val moon: Path by lazy {
            Path.combine(
                PathOperation.Difference,
                Path().apply { addOval(Rect(Offset(128f, 26f), 9f)) }, // F: lib/ui/kit/scenes/setup_phone_scene.dart:52
                Path().apply { addOval(Rect(Offset(133f, 21f), 8f)) }, // F: lib/ui/kit/scenes/setup_phone_scene.dart:53
            )
        }

        /** The terminal's lines, each (start, length). */
        val termLines: List<Pair<Offset, Float>> =
            listOf(
                Offset(66f, 44f) to 22f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:57
                Offset(66f, 53f) to 14f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:58
                Offset(66f, 62f) to 26f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:59
                Offset(66f, 71f) to 18f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:60
            )
    }

    override fun paint(canvas: Canvas, frame: SceneFrame) {
        val p = frame.palette
        val t = frame.entrance
        val breath = if (frame.looping) SceneDraw.wave(frame.loop) else 0f
        val stopped = mood == SetupPhoneMood.Stopped

        // The wash behind the phone grows in, and swells with each breath. A stopped phone's wash is the quiet
        // neutral at the accent wash's own strength (LOOK-36).
        val wash = SceneDraw.interval(t, 0f, .4f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:73
        if (wash > 0f) {
            canvas.drawCircle(
                center,
                (50 + 3 * breath) * (.7f + .3f * wash), // F: lib/ui/kit/scenes/setup_phone_scene.dart:77
                SceneDraw.fill(
                    if (stopped) SceneDraw.fade(p.line, FlutterKitTokens.SCENE_WASH_ALPHA * wash)
                    else SceneDraw.fade(p.accentSoft, wash)
                ),
            )
        }

        // A dot circling the phone while it starts.
        if (mood == SetupPhoneMood.Starting && frame.looping) {
            val dot = SetupCast.orbit(center, 56f, frame.loop) // F: lib/ui/kit/scenes/setup_phone_scene.dart:88
            canvas.drawCircle(dot, 2.6f, SceneDraw.fill(p.accent)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:89
            canvas.drawCircle(
                SetupCast.orbit(center, 56f, frame.loop - .04f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:91
                1.8f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:92
                SceneDraw.fill(SceneDraw.fade(p.accent, .45f)), // F: lib/ui/kit/scenes/setup_phone_scene.dart:93
            )
        }

        // The screen lights, then the frame draws itself around it.
        val lit = SceneDraw.interval(t, .25f, .55f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:98
        if (lit > 0f) {
            canvas.drawRRect(screenRect, SCREEN_RADIUS, SceneDraw.fill(SceneDraw.fade(p.surface, lit)))
            if (!stopped) {
                canvas.drawRRect(
                    screenRect,
                    SCREEN_RADIUS,
                    SceneDraw.fill(SceneDraw.fade(p.accentSoft, lit * .9f)), // F: lib/ui/kit/scenes/setup_phone_scene.dart:107
                )
            }
        }
        SceneDraw.partialPath(
            canvas,
            phoneFrame,
            SceneDraw.interval(t, .05f, .5f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:114
            SceneDraw.pen(if (stopped) p.muted else p.ink),
        )
        // The home indicator.
        val home = SceneDraw.interval(t, .45f, .6f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:118
        if (home > 0f) {
            canvas.drawLine(
                Offset(74f, 106.5f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:121
                Offset(74 + 12 * home, 106.5f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:122
                SceneDraw.pen(SceneDraw.fade(p.muted, .8f), 2f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:123
            )
        }

        when (mood) {
            SetupPhoneMood.Fresh,
            SetupPhoneMood.Starting -> {
                promptLine(canvas, frame, cursor = true)
                portal(canvas, frame, breath, begin = .5f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:131
            }
            SetupPhoneMood.Ready ->
                portal(canvas, frame, breath, begin = .4f, center = Offset(80f, 58f)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:133
            SetupPhoneMood.Termux -> {
                promptLine(canvas, frame, cursor = false)
                terminal(canvas, frame)
            }
            SetupPhoneMood.Stopped -> {
                SetupCast.portal(
                    canvas,
                    center = Offset(80f, 60f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:140
                    size = 30f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:141
                    color = SceneDraw.fade(p.muted, .7f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:142
                    upper = SceneDraw.interval(t, .45f, .8f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:143
                    lower = SceneDraw.interval(t, .55f, .9f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:144
                    open = -2f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:145
                    width = 4f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:146
                )
                val moonIn = SceneDraw.interval(t, .7f, 1f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:148
                if (moonIn > 0f) {
                    canvas.drawPath(moon, SceneDraw.fill(SceneDraw.fade(p.muted, .8f * moonIn))) // F: lib/ui/kit/scenes/setup_phone_scene.dart:152
                }
                dots(canvas, frame, quiet = true)
                return
            }
        }
        sparkles(canvas, frame, breath)
        dots(canvas, frame)
    }

    private fun promptLine(canvas: Canvas, frame: SceneFrame, cursor: Boolean) {
        val p = frame.palette
        val t = frame.entrance
        SceneDraw.partialPath(
            canvas,
            prompt,
            SceneDraw.interval(t, .4f, .55f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:168
            SceneDraw.pen(p.accent, SceneDraw.HAIRLINE),
        )
        val typed = SceneDraw.interval(t, .5f, .62f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:171
        if (typed > 0f && (!cursor || SetupCast.cursorOn(frame))) {
            canvas.drawLine(
                Offset(73f, 34f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:174
                Offset(73 + 7 * typed, 34f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:175
                SceneDraw.pen(p.accent, SceneDraw.HAIRLINE),
            )
        }
    }

    private fun terminal(canvas: Canvas, frame: SceneFrame) {
        val p = frame.palette
        val t = frame.entrance
        termLines.forEachIndexed { index, (start, length) ->
            val last = index == termLines.size - 1
            val drawn = SceneDraw.interval(t, .5f + index * .1f, .7f + index * .1f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:186
            if (drawn <= 0f) return@forEachIndexed
            canvas.drawLine(
                start,
                start + Offset(length * drawn, 0f),
                SceneDraw.pen(
                    if (last) p.accent else SceneDraw.fade(p.muted, .7f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:192
                    SceneDraw.HAIRLINE,
                ),
            )
            if (last && drawn >= 1f) {
                val land = SceneDraw.interval(t, .88f, 1f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_phone_scene.dart:197
                canvas.drawCircle(
                    start + Offset(length + 6, 0f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:199
                    2.4f * land, // F: lib/ui/kit/scenes/setup_phone_scene.dart:200
                    SceneDraw.fill(p.accent),
                )
            }
        }
    }

    private fun portal(
        canvas: Canvas,
        frame: SceneFrame,
        breath: Float,
        begin: Float,
        center: Offset = Offset(80f, 66f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:212
    ) {
        val p = frame.palette
        val t = frame.entrance
        SetupCast.portal(
            canvas,
            center = center,
            size = if (mood == SetupPhoneMood.Ready) 34f else 30f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:219
            color = p.accent,
            upper = SceneDraw.interval(t, begin, begin + .3f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:221
            lower = SceneDraw.interval(t, begin + .1f, begin + .4f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:222
            open = 1.6f * breath, // F: lib/ui/kit/scenes/setup_phone_scene.dart:223
            width = 4.5f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:224
        )
        val land = SceneDraw.interval(t, begin + .3f, 1f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_phone_scene.dart:226
        if (land > 0f) {
            val spark =
                if (frame.looping) SetupCast.orbit(center, 2.4f, frame.loop) // F: lib/ui/kit/scenes/setup_phone_scene.dart:229
                else center
            canvas.drawCircle(spark, 3.2f * land, SceneDraw.fill(p.accent)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:231
        }
    }

    private fun sparkles(canvas: Canvas, frame: SceneFrame, breath: Float) {
        val p = frame.palette
        val t = frame.entrance
        val big = SceneDraw.interval(t, .7f, .95f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_phone_scene.dart:238
        val small = SceneDraw.interval(t, .8f, 1f, SceneDraw.land) // F: lib/ui/kit/scenes/setup_phone_scene.dart:239
        SetupCast.sparkle(
            canvas,
            Offset(126f, 26f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:242
            9 + 1.2f * breath, // F: lib/ui/kit/scenes/setup_phone_scene.dart:243
            p.accent,
            pop = big,
        )
        SetupCast.sparkle(
            canvas,
            Offset(138f, 46f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:249
            4.5f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:250
            SceneDraw.fade(p.accent, .8f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:251
            pop = small,
        )
        SetupCast.sparkle(
            canvas,
            Offset(32f, 90f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:256
            5f, // F: lib/ui/kit/scenes/setup_phone_scene.dart:257
            SceneDraw.fade(p.accent, .55f), // F: lib/ui/kit/scenes/setup_phone_scene.dart:258
            pop = small,
        )
    }

    private fun dots(canvas: Canvas, frame: SceneFrame, quiet: Boolean = false) {
        val p = frame.palette
        val shown = SceneDraw.interval(frame.entrance, .6f, 1f) // F: lib/ui/kit/scenes/setup_phone_scene.dart:265
        if (shown <= 0f) return
        val paint = SceneDraw.fill(SceneDraw.fade(p.muted, (if (quiet) .35f else .5f) * shown)) // F: lib/ui/kit/scenes/setup_phone_scene.dart:268
        canvas.drawCircle(Offset(34f, 34f), 2.2f, paint) // F: lib/ui/kit/scenes/setup_phone_scene.dart:270
        canvas.drawCircle(Offset(22f, 62f), 1.6f, paint) // F: lib/ui/kit/scenes/setup_phone_scene.dart:271
        canvas.drawCircle(Offset(132f, 94f), 2.4f, paint) // F: lib/ui/kit/scenes/setup_phone_scene.dart:272
    }
}
