package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path

/**
 * A folder opening (lib/ui/kit/scenes/folders_open_scene.dart), for a folder with nothing in it yet: the back of the
 * folder draws itself, a folded sheet rises out of it, the front swings into place in the accent, and a spark lands
 * where the next project will be. It plays once; it has no loop (an empty folder is a resting screen).
 */
public class KitFoldersOpenScene : SceneArt() {
    internal companion object {
        // The back of the folder with its tab, drawn from the tab's end round.
        val back: Path by lazy {
            scenePath(Offset(52f, 36f)) { // F: lib/ui/kit/scenes/folders_open_scene.dart:16
                lineTo(46f, 29f) // F: lib/ui/kit/scenes/folders_open_scene.dart:17
                lineTo(28f, 29f) // F: lib/ui/kit/scenes/folders_open_scene.dart:18
                arcTo(Offset(22f, 35f), 6f) // F: lib/ui/kit/scenes/folders_open_scene.dart:19
                lineTo(22f, 86f) // F: lib/ui/kit/scenes/folders_open_scene.dart:20
                arcTo(Offset(28f, 92f), 6f, clockwise = false) // F: lib/ui/kit/scenes/folders_open_scene.dart:22,23
                lineTo(92f, 92f) // F: lib/ui/kit/scenes/folders_open_scene.dart:26
                arcTo(Offset(98f, 86f), 6f, clockwise = false) // F: lib/ui/kit/scenes/folders_open_scene.dart:28,29
                lineTo(98f, 42f) // F: lib/ui/kit/scenes/folders_open_scene.dart:32
                arcTo(Offset(92f, 36f), 6f, clockwise = false) // F: lib/ui/kit/scenes/folders_open_scene.dart:34,35
                close()
            }
        }

        // A folded sheet: the outline, then its turned corner.
        val sheet: Path by lazy {
            scenePath(Offset(40f, 62f)) { // F: lib/ui/kit/scenes/folders_open_scene.dart:42
                lineTo(40f, 30f) // F: lib/ui/kit/scenes/folders_open_scene.dart:43
                lineTo(66f, 30f) // F: lib/ui/kit/scenes/folders_open_scene.dart:44
                lineTo(76f, 40f) // F: lib/ui/kit/scenes/folders_open_scene.dart:45
                lineTo(76f, 62f) // F: lib/ui/kit/scenes/folders_open_scene.dart:46
            }
        }
        val fold: Path by lazy {
            scenePath(Offset(66f, 30f)) { // F: lib/ui/kit/scenes/folders_open_scene.dart:48
                lineTo(66f, 40f) // F: lib/ui/kit/scenes/folders_open_scene.dart:49
                lineTo(76f, 40f) // F: lib/ui/kit/scenes/folders_open_scene.dart:50
            }
        }

        // The front, leaning open: its top edge is wider than the folder.
        val front: Path by lazy {
            scenePath(Offset(26f, 92f)) { // F: lib/ui/kit/scenes/folders_open_scene.dart:54
                lineTo(92f, 92f) // F: lib/ui/kit/scenes/folders_open_scene.dart:55
                arcTo(Offset(97f, 88f), 5f, clockwise = false) // F: lib/ui/kit/scenes/folders_open_scene.dart:57,58
                lineTo(103f, 60f) // F: lib/ui/kit/scenes/folders_open_scene.dart:61
                arcTo(Offset(98f, 54f), 5f, clockwise = false) // F: lib/ui/kit/scenes/folders_open_scene.dart:63,64
                lineTo(22f, 54f) // F: lib/ui/kit/scenes/folders_open_scene.dart:67
                arcTo(Offset(17f, 60f), 5f, clockwise = false) // F: lib/ui/kit/scenes/folders_open_scene.dart:69,70
                lineTo(21f, 88f) // F: lib/ui/kit/scenes/folders_open_scene.dart:73
                arcTo(Offset(26f, 92f), 5f, clockwise = false) // F: lib/ui/kit/scenes/folders_open_scene.dart:75,76
                close()
            }
        }

        val spark: Offset = Offset(88f, 22f) // F: lib/ui/kit/scenes/folders_open_scene.dart:81

        /** The spark's three rays, each (from, to). */
        val rays: List<Pair<Offset, Offset>> =
            listOf(
                Offset(88f, 12f) to Offset(88f, 7f), // F: lib/ui/kit/scenes/folders_open_scene.dart:130
                Offset(98f, 22f) to Offset(103f, 22f), // F: lib/ui/kit/scenes/folders_open_scene.dart:131
                Offset(95f, 15f) to Offset(99f, 11f), // F: lib/ui/kit/scenes/folders_open_scene.dart:132
            )

        const val SWING_MAX: Float = 1.2f // F: lib/ui/kit/scenes/folders_open_scene.dart:113
    }

    override fun paint(canvas: Canvas, frame: SceneFrame) {
        val palette = frame.palette
        val t = frame.entrance

        SceneDraw.partialPath(
            canvas,
            back,
            SceneDraw.interval(t, 0f, .45f), // F: lib/ui/kit/scenes/folders_open_scene.dart:91
            SceneDraw.pen(palette.muted),
        )

        val rise = SceneDraw.interval(t, .3f, .7f, SceneDraw.enter) // F: lib/ui/kit/scenes/folders_open_scene.dart:95
        if (rise > 0f) {
            canvas.save()
            canvas.translate(0f, 14 * (1 - rise)) // F: lib/ui/kit/scenes/folders_open_scene.dart:98
            val sheetPen = SceneDraw.pen(SceneDraw.fade(palette.ink, rise), SceneDraw.HAIRLINE)
            canvas.drawPath(sheet, sheetPen)
            canvas.drawPath(fold, sheetPen)
            canvas.restore()
        }

        val swing = SceneDraw.interval(t, .45f, .9f, SceneDraw.land) // F: lib/ui/kit/scenes/folders_open_scene.dart:108
        if (swing > 0f) {
            canvas.save()
            // Hinged at the bottom edge: it tips up into place.
            canvas.translate(0f, 92f) // F: lib/ui/kit/scenes/folders_open_scene.dart:112
            canvas.scale(1f, swing.coerceIn(0f, SWING_MAX))
            canvas.translate(0f, -92f) // F: lib/ui/kit/scenes/folders_open_scene.dart:114
            // Opaque first, so the sheet behind it is hidden, then the wash.
            canvas.drawPath(front, SceneDraw.fill(palette.surface))
            canvas.drawPath(front, SceneDraw.fill(palette.accentSoft))
            canvas.drawPath(front, SceneDraw.pen(palette.accent))
            canvas.restore()
        }

        val land = SceneDraw.interval(t, .75f, 1f, SceneDraw.land) // F: lib/ui/kit/scenes/folders_open_scene.dart:122
        if (land > 0f) {
            canvas.drawCircle(spark, 4.5f * land, SceneDraw.fill(palette.accent)) // F: lib/ui/kit/scenes/folders_open_scene.dart:124
            val ray = SceneDraw.pen(SceneDraw.fade(palette.accent, land.coerceIn(0f, 1f)), SceneDraw.HAIRLINE)
            for ((from, to) in rays) {
                canvas.drawLine(lerp(spark, from, land), lerp(spark, to, land), ray)
            }
        }
    }
}
