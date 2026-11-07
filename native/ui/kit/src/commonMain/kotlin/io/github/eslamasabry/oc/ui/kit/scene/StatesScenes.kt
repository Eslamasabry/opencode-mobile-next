package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitCurves
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.HAIRLINE
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.fade
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.interval
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.pen
import io.github.eslamasabry.oc.ui.kit.scene.StatesParts.outlined
import io.github.eslamasabry.oc.ui.kit.scene.StatesParts.spark
import io.github.eslamasabry.oc.ui.kit.scene.StatesParts.ticks
import io.github.eslamasabry.oc.ui.kit.scene.StatesParts.wash
import kotlin.math.PI

// The empty, quiet and failure drawings (states_scenes.dart). One drawing per kind of state, reused wherever that
// state is the same:
//
// | Drawing | State |
// |---|---|
// | StatesSheetScene | no conversations yet (Work, All conversations, a new chat) |
// | StatesFolderScene | no project yet, an empty folder |
// | StatesTrayScene | all caught up (Inbox) |
// | StatesSearchScene | a search that found nothing |
// | StatesTerminalScene | no terminal yet / not set up; a shell that ended |
// | StatesUnpluggedScene | not answering, offline, could not load |
//
// Every one sits on the same soft wash, draws its outline in muted line work and keeps the accent for the one thing
// that matters. They are still once drawn: none of them loops (these are resting screens), except the terminal's
// cursor where the person waits for the shell.

/** A fresh folded sheet with a caret waiting on its last line, and a spark: "no conversations yet". */
public object StatesSheetScene : KitDrawnScene {
    internal val BACK = Rect(44f, 20f, 92f, 84f) // F: lib/ui/kit/scenes/states_scenes.dart:31
    internal val FRONT = Rect(26f, 32f, 80f, 102f) // F: lib/ui/kit/scenes/states_scenes.dart:32
    internal const val BACK_FOLD = 12f // F: lib/ui/kit/scenes/states_scenes.dart:33
    private val backPath by lazy { StatesParts.sheet(BACK, fold = BACK_FOLD) }
    private val frontPath by lazy { StatesParts.sheet(FRONT) }
    private val frontFold by lazy { StatesParts.fold(FRONT) }
    internal val LINES =
        listOf(
            Offset(36f, 58f) to Offset(68f, 58f), // F: lib/ui/kit/scenes/states_scenes.dart:37
            Offset(36f, 70f) to Offset(62f, 70f), // F: lib/ui/kit/scenes/states_scenes.dart:38
            Offset(36f, 82f) to Offset(50f, 82f), // F: lib/ui/kit/scenes/states_scenes.dart:39
        )

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        wash(frame)
        // The sheet behind: another page, quieter.
        outlined(backPath, interval(t, .05f, .45f), p.surface, p.line, HAIRLINE) // F: lib/ui/kit/scenes/states_scenes.dart:52
        outlined(frontPath, interval(t, .12f, .6f), p.surface, p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:59
        drawPartial(frontFold, interval(t, .5f, .65f), p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:66
        LINES.forEachIndexed { index, (from, to) ->
            val draw = interval(t, .5f + index * .08f, .66f + index * .08f) // F: lib/ui/kit/scenes/states_scenes.dart:71
            if (draw > 0f) drawSceneLine(p.line, from, lerp(from, to, draw), 4f) // F: lib/ui/kit/scenes/states_scenes.dart:69
        }
        // The block caret where the next words go (the chat's own caret).
        val caret = interval(t, .78f, .92f) // F: lib/ui/kit/scenes/states_scenes.dart:77
        if (caret > 0f) {
            drawRoundRect(
                p.accent,
                Offset(55f, 88f - 13f * caret), // F: lib/ui/kit/scenes/states_scenes.dart:81
                Size(62f - 55f, 13f * caret), // F: lib/ui/kit/scenes/states_scenes.dart:81
                CornerRadius(1.5f), // F: lib/ui/kit/scenes/states_scenes.dart:82
            )
        }
        val pop = interval(t, .8f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/states_scenes.dart:87
        spark(Offset(100f, 38f), 8f, pop, p.accent) // F: lib/ui/kit/scenes/states_scenes.dart:88
        drawCircle(fade(p.accent, .55f), 2.6f * pop.coerceIn(0f, 1f), Offset(90f, 100f)) // F: lib/ui/kit/scenes/states_scenes.dart:90-92
    }
}

/** An open folder with a spark rising out of it: "no project yet" and an empty folder. */
public object StatesFolderScene : KitDrawnScene {
    private val back by lazy {
        Path().apply {
            moveTo(20f, 92f) // F: lib/ui/kit/scenes/states_scenes.dart:103
            lineTo(20f, 42f) // F: lib/ui/kit/scenes/states_scenes.dart:104
            sceneArcTo(Offset(20f, 42f), Offset(26f, 36f), 6f) // F: lib/ui/kit/scenes/states_scenes.dart:104,105
            lineTo(44f, 36f) // F: lib/ui/kit/scenes/states_scenes.dart:106
            lineTo(51f, 44f) // F: lib/ui/kit/scenes/states_scenes.dart:107
            lineTo(88f, 44f) // F: lib/ui/kit/scenes/states_scenes.dart:108
            sceneArcTo(Offset(88f, 44f), Offset(94f, 50f), 6f) // F: lib/ui/kit/scenes/states_scenes.dart:108,109
            lineTo(94f, 58f) // F: lib/ui/kit/scenes/states_scenes.dart:110
        }
    }
    private val front by lazy {
        Path().apply {
            moveTo(31f, 58f) // F: lib/ui/kit/scenes/states_scenes.dart:113
            lineTo(100f, 58f) // F: lib/ui/kit/scenes/states_scenes.dart:114
            quadraticTo(104f, 58f, 103f, 62f) // F: lib/ui/kit/scenes/states_scenes.dart:115
            lineTo(96f, 92f) // F: lib/ui/kit/scenes/states_scenes.dart:116
            quadraticTo(95f, 96f, 91f, 96f) // F: lib/ui/kit/scenes/states_scenes.dart:117
            lineTo(24f, 96f) // F: lib/ui/kit/scenes/states_scenes.dart:118
            quadraticTo(19f, 96f, 20f, 91f) // F: lib/ui/kit/scenes/states_scenes.dart:119
            lineTo(27f, 62f) // F: lib/ui/kit/scenes/states_scenes.dart:120
            quadraticTo(28f, 58f, 31f, 58f) // F: lib/ui/kit/scenes/states_scenes.dart:121
            close()
        }
    }
    internal val WASH_CENTRE = Offset(60f, 64f) // F: lib/ui/kit/scenes/states_scenes.dart:128

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        wash(frame, center = WASH_CENTRE)
        drawPartial(back, interval(t, .05f, .5f), p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:133
        // Inside the folder: a quiet sheet edge, nothing on it.
        val inside = interval(t, .35f, .6f) // F: lib/ui/kit/scenes/states_scenes.dart:137
        if (inside > 0f) drawSceneLine(p.line, Offset(32f, 52f), Offset(32f + 48f * inside, 52f), HAIRLINE) // F: lib/ui/kit/scenes/states_scenes.dart:140,141
        outlined(front, interval(t, .25f, .75f), p.surface, p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:148
        // The spark rises out of the folder and settles above it.
        val rise = interval(t, .65f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/states_scenes.dart:153
        if (rise > 0f) {
            val y = 58f - 34f * rise // F: lib/ui/kit/scenes/states_scenes.dart:155
            spark(Offset(64f, y), 9f, rise.coerceIn(0f, 1.2f), p.accent) // F: lib/ui/kit/scenes/states_scenes.dart:156
            drawCircle(fade(p.accent, .55f), 2.6f * rise.coerceIn(0f, 1f), Offset(80f, y + 10f)) // F: lib/ui/kit/scenes/states_scenes.dart:158-160
            drawCircle(fade(p.accent, .35f), 2f * rise.coerceIn(0f, 1f), Offset(50f, y + 16f)) // F: lib/ui/kit/scenes/states_scenes.dart:163-165
        }
    }
}

/** An inbox tray: the last sheet settles into it and a check draws itself on top: "all caught up". */
public object StatesTrayScene : KitDrawnScene {
    internal val SHEET = Rect(36f, 26f, 84f, 80f) // F: lib/ui/kit/scenes/states_scenes.dart:176
    private val sheetPath by lazy { StatesParts.sheet(SHEET, fold = 12f) } // F: lib/ui/kit/scenes/states_scenes.dart:177
    private val check by lazy {
        Path().apply {
            moveTo(49f, 47f) // F: lib/ui/kit/scenes/states_scenes.dart:179
            lineTo(57f, 55f) // F: lib/ui/kit/scenes/states_scenes.dart:180
            lineTo(72f, 39f) // F: lib/ui/kit/scenes/states_scenes.dart:181
        }
    }
    private val tray by lazy {
        Path().apply {
            moveTo(18f, 70f) // F: lib/ui/kit/scenes/states_scenes.dart:184
            lineTo(40f, 70f) // F: lib/ui/kit/scenes/states_scenes.dart:185
            quadraticTo(43f, 70f, 44f, 73f) // F: lib/ui/kit/scenes/states_scenes.dart:186
            lineTo(46f, 77f) // F: lib/ui/kit/scenes/states_scenes.dart:187
            quadraticTo(47f, 80f, 50f, 80f) // F: lib/ui/kit/scenes/states_scenes.dart:188
            lineTo(70f, 80f) // F: lib/ui/kit/scenes/states_scenes.dart:189
            quadraticTo(73f, 80f, 74f, 77f) // F: lib/ui/kit/scenes/states_scenes.dart:190
            lineTo(76f, 73f) // F: lib/ui/kit/scenes/states_scenes.dart:191
            quadraticTo(77f, 70f, 80f, 70f) // F: lib/ui/kit/scenes/states_scenes.dart:192
            lineTo(102f, 70f) // F: lib/ui/kit/scenes/states_scenes.dart:193
            lineTo(102f, 92f) // F: lib/ui/kit/scenes/states_scenes.dart:194
            sceneArcTo(Offset(102f, 92f), Offset(94f, 100f), 8f) // F: lib/ui/kit/scenes/states_scenes.dart:194,195
            lineTo(26f, 100f) // F: lib/ui/kit/scenes/states_scenes.dart:196
            sceneArcTo(Offset(26f, 100f), Offset(18f, 92f), 8f) // F: lib/ui/kit/scenes/states_scenes.dart:196,197
            close()
        }
    }

    /** The tray's back rim: two strokes, one contour each (states_scenes.dart:200-204). */
    private val rim by lazy {
        listOf(
            Path().apply {
                moveTo(18f, 70f) // F: lib/ui/kit/scenes/states_scenes.dart:201
                lineTo(26f, 54f) // F: lib/ui/kit/scenes/states_scenes.dart:202
            },
            Path().apply {
                moveTo(102f, 70f) // F: lib/ui/kit/scenes/states_scenes.dart:203
                lineTo(94f, 54f) // F: lib/ui/kit/scenes/states_scenes.dart:204
            },
        )
    }

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        wash(frame, center = StatesFolderScene.WASH_CENTRE)
        // The tray's back rim, behind the sheet.
        drawPartial(rim, interval(t, .05f, .35f), p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:216
        // The sheet drops in from above and settles with a small give.
        val drop = interval(t, .2f, .7f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/states_scenes.dart:221
        if (drop > 0f) {
            withTransform({ translate(0f, -34f * (1 - drop)) }) { // F: lib/ui/kit/scenes/states_scenes.dart:224
                val appear = drop.coerceIn(0f, 1f)
                drawPath(sheetPath, fade(p.surface, appear))
                drawPath(sheetPath, fade(p.muted, appear), style = pen(HAIRLINE + 1))
                drawPartial(check, interval(t, .7f, .95f), p.accent, 6f) // F: lib/ui/kit/scenes/states_scenes.dart:237,238
            }
        }
        outlined(tray, interval(t, .05f, .5f), p.surface, p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:246
    }
}

/** A magnifier over a sheet, its lens resting on a blank part of the page: a search that found nothing. */
public object StatesSearchScene : KitDrawnScene {
    internal val SHEET = Rect(22f, 22f, 72f, 86f) // F: lib/ui/kit/scenes/states_scenes.dart:258
    private val sheetPath by lazy { StatesParts.sheet(SHEET, fold = 12f) } // F: lib/ui/kit/scenes/states_scenes.dart:259
    private val sheetFold by lazy { StatesParts.fold(SHEET, fold = 12f) } // F: lib/ui/kit/scenes/states_scenes.dart:260
    internal val LENS = Offset(70f, 64f) // F: lib/ui/kit/scenes/states_scenes.dart:261
    internal const val LENS_RADIUS = 21.0f // F: lib/ui/kit/scenes/states_scenes.dart:262

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        wash(frame)
        outlined(sheetPath, interval(t, .05f, .45f), p.surface, p.muted, HAIRLINE + 1) // F: lib/ui/kit/scenes/states_scenes.dart:273
        drawPartial(sheetFold, interval(t, .35f, .5f), p.muted, HAIRLINE + 1) // F: lib/ui/kit/scenes/states_scenes.dart:280
        val lines = interval(t, .3f, .55f) // F: lib/ui/kit/scenes/states_scenes.dart:283
        if (lines > 0f) {
            drawSceneLine(p.line, Offset(31f, 46f), Offset(31f + 22f * lines, 46f), 4f) // F: lib/ui/kit/scenes/states_scenes.dart:285,286
            drawSceneLine(p.line, Offset(31f, 57f), Offset(31f + 14f * lines, 57f), 4f) // F: lib/ui/kit/scenes/states_scenes.dart:285,287
        }
        // The magnifier slides in a little and lands over the sheet.
        val land = interval(t, .35f, .85f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/states_scenes.dart:291
        if (land <= 0f) return
        withTransform({ translate(10f * (1 - land), 10f * (1 - land)) }) { // F: lib/ui/kit/scenes/states_scenes.dart:294
            val appear = land.coerceIn(0f, 1f)
            drawCircle(fade(p.surface, appear), LENS_RADIUS, LENS)
            val handleFrom = LENS + Offset(1f, 1f) * (LENS_RADIUS * .72f) // F: lib/ui/kit/scenes/states_scenes.dart:301
            drawSceneLine(p.muted, handleFrom, handleFrom + Offset(15f, 15f) * appear, 8f) // F: lib/ui/kit/scenes/states_scenes.dart:304,305
            drawCircle(fade(p.accent, appear), LENS_RADIUS, LENS, style = pen())
            // A glint on the glass.
            val glint = Rect(LENS, LENS_RADIUS - 7) // F: lib/ui/kit/scenes/states_scenes.dart:314
            drawArc(
                fade(p.accent, .45f * appear), // F: lib/ui/kit/scenes/states_scenes.dart:318
                (PI * 1.1).toFloat().sceneDegrees(), // F: lib/ui/kit/scenes/states_scenes.dart:315
                (PI * .4).toFloat().sceneDegrees(), // F: lib/ui/kit/scenes/states_scenes.dart:316
                false,
                glint.topLeft,
                glint.size,
                style = pen(HAIRLINE),
            )
        }
    }
}

/**
 * A terminal window with a `>_` prompt. Its cursor blinks twice as it arrives and then rests lit; [ended] draws the
 * prompt and cursor dim, the way a terminal looks when its shell is gone. With `ambient` (only where the person waits
 * for the shell), the cursor keeps blinking.
 */
public data class StatesTerminalScene(
    /** The shell has ended: no live cursor. */
    val ended: Boolean = false,
) : KitDrawnScene {
    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        wash(frame)
        outlined(WINDOW_PATH, interval(t, .05f, .5f), p.surface, p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:358
        // The title bar: a hairline and three dots.
        val bar = interval(t, .3f, .55f) // F: lib/ui/kit/scenes/states_scenes.dart:363
        if (bar > 0f) {
            drawSceneLine(p.line, Offset(WINDOW.left, 41f), Offset(WINDOW.left + WINDOW.width * bar, 41f), HAIRLINE) // F: lib/ui/kit/scenes/states_scenes.dart:366,367
            for (i in 0 until 3) drawCircle(p.line, 2.3f * bar, Offset(24f + i * 8f, 33.5f)) // F: lib/ui/kit/scenes/states_scenes.dart:370-373
        }
        drawPartial(PROMPT, interval(t, .45f, .7f), if (ended) p.muted else p.accent) // F: lib/ui/kit/scenes/states_scenes.dart:382
        if (ended) {
            // No live cursor: a dim one where the prompt was.
            val rest = interval(t, .65f, .9f) // F: lib/ui/kit/scenes/states_scenes.dart:387
            if (rest > 0f) drawSceneLine(p.line, CURSOR_FROM, Offset(CURSOR_FROM.x + 14f * rest, CURSOR_FROM.y)) // F: lib/ui/kit/scenes/states_scenes.dart:391
            return
        }
        if (cursorLit(frame)) drawSceneLine(p.accent, CURSOR_FROM, CURSOR_TO)
    }

    public companion object {
        internal val WINDOW = Rect(14f, 26f, 106f, 96f) // F: lib/ui/kit/scenes/states_scenes.dart:337
        internal const val WINDOW_RADIUS = 10f // F: lib/ui/kit/scenes/states_scenes.dart:339
        private val WINDOW_PATH by lazy {
            Path().apply { addRoundRect(RoundRect(WINDOW, CornerRadius(WINDOW_RADIUS))) }
        }
        private val PROMPT by lazy {
            Path().apply {
                moveTo(28f, 56f) // F: lib/ui/kit/scenes/states_scenes.dart:341
                lineTo(38f, 64f) // F: lib/ui/kit/scenes/states_scenes.dart:342
                lineTo(28f, 72f) // F: lib/ui/kit/scenes/states_scenes.dart:343
            }
        }
        internal val CURSOR_FROM = Offset(47f, 72f) // F: lib/ui/kit/scenes/states_scenes.dart:405
        internal val CURSOR_TO = Offset(61f, 72f) // F: lib/ui/kit/scenes/states_scenes.dart:406

        /**
         * The cursor: arrives at .7, blinks twice, then rests lit; while waiting (looping) it blinks four times a
         * loop (states_scenes.dart:399-403).
         */
        public fun cursorLit(frame: KitDrawnFrame): Boolean {
            val t = frame.entrance
            val arrived = t >= .7f // F: lib/ui/kit/scenes/states_scenes.dart:399
            val blink =
                if (frame.looping) (frame.loop * 4) % 1 < .5f // F: lib/ui/kit/scenes/states_scenes.dart:401
                else !(t > .78f && t < .84f) && !(t > .9f && t < .96f) // F: lib/ui/kit/scenes/states_scenes.dart:402
            return arrived && blink
        }
    }
}

/** A plug pulled out of its socket, with a small spark in the gap: not answering, offline, could not load. */
public object StatesUnpluggedScene : KitDrawnScene {
    private val leftCable by lazy {
        Path().apply {
            moveTo(0f, 74f) // F: lib/ui/kit/scenes/states_scenes.dart:420
            cubicTo(10f, 74f, 12f, 62f, 22f, 62f) // F: lib/ui/kit/scenes/states_scenes.dart:421
        }
    }
    private val rightCable by lazy {
        Path().apply {
            moveTo(98f, 58f) // F: lib/ui/kit/scenes/states_scenes.dart:423
            cubicTo(106f, 58f, 108f, 52f, 118f, 52f) // F: lib/ui/kit/scenes/states_scenes.dart:424
        }
    }
    private val plug by lazy {
        Path().apply {
            addRoundRect(RoundRect(Rect(22f, 49f, 44f, 75f), CornerRadius(5f))) // F: lib/ui/kit/scenes/states_scenes.dart:428,429
        }
    }
    private val socket by lazy {
        Path().apply {
            addRoundRect(RoundRect(Rect(74f, 44f, 98f, 72f), CornerRadius(6f))) // F: lib/ui/kit/scenes/states_scenes.dart:435,436
        }
    }

    /** Drawn a size up around the middle, so the plug and socket carry the scene (states_scenes.dart:447-450). */
    internal const val ZOOM = 1.2f // F: lib/ui/kit/scenes/states_scenes.dart:449
    internal val GAP = Offset(63f, 61f) // F: lib/ui/kit/scenes/states_scenes.dart:512
    internal val GAP_ANGLES = listOf(-118f, -90f, -62f) // F: lib/ui/kit/scenes/states_scenes.dart:513

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        wash(frame)
        val middle = Offset(60f, 60f) // F: lib/ui/kit/scenes/states_scenes.dart:448
        withTransform({
            translate(middle.x, middle.y)
            scale(ZOOM, ZOOM, Offset.Zero)
            translate(-middle.x, -middle.y)
        }) {
            drawPartial(leftCable, interval(t, 0f, .4f), p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:456
            drawPartial(rightCable, interval(t, .05f, .45f), p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:462
            // The socket, fixed; its two holes.
            outlined(socket, interval(t, .25f, .6f), p.surface, p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:470
            val holes = interval(t, .5f, .65f) // F: lib/ui/kit/scenes/states_scenes.dart:474
            if (holes > 0f)
                for (y in listOf(53f, 63f)) drawSceneLine(p.line, Offset(80f, y), Offset(80f + 6f * holes, y), 4f) // F: lib/ui/kit/scenes/states_scenes.dart:476-480
            // The plug eases back from the socket with a small give.
            val pull = interval(t, .35f, .85f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/states_scenes.dart:486
            withTransform({ translate(10f * (1 - pull), 0f) }) { // F: lib/ui/kit/scenes/states_scenes.dart:488
                outlined(plug, interval(t, .2f, .55f), p.surface, p.muted) // F: lib/ui/kit/scenes/states_scenes.dart:492
                val prongs = interval(t, .45f, .6f) // F: lib/ui/kit/scenes/states_scenes.dart:496
                if (prongs > 0f)
                    for (y in listOf(56f, 68f)) drawSceneLine(p.muted, Offset(44f, y), Offset(44f + 8f * prongs, y), 4f) // F: lib/ui/kit/scenes/states_scenes.dart:498-502
            }
            // The gap: a small spark and a few short strokes.
            val gap = interval(t, .75f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/states_scenes.dart:509
            ticks(GAP, GAP_ANGLES, 11f, 18f, gap.coerceIn(0f, 1f), p.accent, 4f) // F: lib/ui/kit/scenes/states_scenes.dart:514,515,517
        }
    }
}

/** The six state drawings in the order of the Flutter gallery (kit_scenes_golden_test.dart `states`). */
public val StatesScenesAll: List<KitDrawnScene> =
    listOf(
        StatesSheetScene,
        StatesFolderScene,
        StatesTrayScene,
        StatesSearchScene,
        StatesTerminalScene(),
        StatesUnpluggedScene,
    )
