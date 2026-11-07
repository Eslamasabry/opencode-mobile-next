package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where a server connection stands, as the link drawing shows it (Dart `ServersLinkState`, in its order). */
public enum class ServersLinkState {
    /** Nothing tried yet: the phone and the computer, a dotted gap between. */
    Idle,

    /** Pairing or checking: the link draws itself across and, while waiting, a dot travels it. */
    Linking,

    /** The computer answered: the link is solid and a spark lands on it. */
    Linked,

    /** Nothing answered: the link is broken in the middle. */
    Failed,
}

/**
 * This phone and a computer linking up (lib/ui/kit/scenes/servers_link_scene.dart, Add server): the phone on the left,
 * the laptop on the right with the portal on its screen, and the link between them says the [state]. [ServersLinkState.Idle]
 * with [intro] draws the two devices in; the other states keep the devices and draw only the link. Ambient (only while
 * linking) a dot travels the link.
 */
public data class ServersLinkScene(
    val state: ServersLinkState,
    /** The idle drawing's entrance draws the devices in; false once the link has moved (only the dotted gap returns). */
    val intro: Boolean = true,
) : SceneArt() {
    override val box: Size get() = Size(200f, 84f) // F: lib/ui/kit/scenes/servers_link_scene.dart:47

    /** The phone-to-computer link shows progress along a line (LAY-8): it mirrors under RTL. */
    override val mirrorsInRtl: Boolean get() = true

    internal companion object {
        val phone: ServersPhone = ServersPhone(Rect(26f, 18f, 54f, 66f)) // F: lib/ui/kit/scenes/servers_link_scene.dart:58
        val laptop: ServersLaptop = ServersLaptop(Rect(112f, 16f, 168f, 56f)) // F: lib/ui/kit/scenes/servers_link_scene.dart:59
        val portal: Offset = Offset(140f, 36f) // F: lib/ui/kit/scenes/servers_link_scene.dart:60

        val from: Offset = Offset(62f, 42f) // F: lib/ui/kit/scenes/servers_link_scene.dart:62
        val to: Offset = Offset(104f, 38f) // F: lib/ui/kit/scenes/servers_link_scene.dart:63
        val link: Path by lazy {
            Path().apply {
                moveTo(from.x, from.y)
                quadraticTo(83f, 20f, to.x, to.y) // F: lib/ui/kit/scenes/servers_link_scene.dart:66
            }
        }
        val metric: PathMeasure by lazy { PathMeasure().apply { setPath(link, false) } }

        fun segment(start: Float, end: Float): Path = Path().also { metric.getSegment(start, end, it, true) }

        /** The link's two halves with a gap in the middle (the failed state). */
        val left: Path by lazy { segment(0f, metric.length * .4f) } // F: lib/ui/kit/scenes/servers_link_scene.dart:70
        val right: Path by lazy { segment(metric.length * .6f, metric.length) } // F: lib/ui/kit/scenes/servers_link_scene.dart:72
        val middle: Offset by lazy { metric.getPosition(metric.length / 2) } // F: lib/ui/kit/scenes/servers_link_scene.dart:76
        val leftEnd: Offset by lazy { metric.getPosition(metric.length * .4f) } // F: lib/ui/kit/scenes/servers_link_scene.dart:79
        val rightEnd: Offset by lazy { metric.getPosition(metric.length * .6f) } // F: lib/ui/kit/scenes/servers_link_scene.dart:82

        /** Spacing of the idle gap's dots along the link, in scene units. */
        const val DOT_STEP: Float = 7f // F: lib/ui/kit/scenes/servers_link_scene.dart:87

        /** The idle gap: dots along the link, precomputed. */
        val dots: List<Offset> by lazy {
            generateSequence(0f) { it + DOT_STEP }.takeWhile { it <= metric.length }.map { metric.getPosition(it) }.toList()
        }

        /** The travelling dot's fade at [along] (0..1) of the link: in over the first 15 %, out over the last. */
        fun travelFade(along: Float): Float =
            when {
                along < .15f -> along / .15f // F: lib/ui/kit/scenes/servers_link_scene.dart:151,152
                along > .85f -> (1 - along) / .15f // F: lib/ui/kit/scenes/servers_link_scene.dart:153,154
                else -> 1f
            }
    }

    override fun paint(canvas: Canvas, frame: SceneFrame) {
        val palette = frame.palette
        val t = frame.entrance
        val first = state == ServersLinkState.Idle && intro

        // The devices: drawn in on the idle entrance, already there after.
        phone.paint(
            canvas,
            palette,
            if (first) SceneDraw.interval(t, 0f, .55f) else 1f, // F: lib/ui/kit/scenes/servers_link_scene.dart:101
            screen = if (state == ServersLinkState.Linked) palette.accent else null,
        )
        laptop.paint(canvas, palette, if (first) SceneDraw.interval(t, .12f, .65f) else 1f) // F: lib/ui/kit/scenes/servers_link_scene.dart:104
        if (state == ServersLinkState.Linked) {
            // The computer lights up as the link lands.
            canvas.drawRRect(
                laptop.screen.deflate(4f), // F: lib/ui/kit/scenes/servers_link_scene.dart:109
                3f, // F: lib/ui/kit/scenes/servers_link_scene.dart:110
                SceneDraw.fill(SceneDraw.fade(palette.accentSoft, SceneDraw.interval(t, .3f, .8f))), // F: lib/ui/kit/scenes/servers_link_scene.dart:113
            )
        }
        ServersPortal.paint(
            canvas,
            portal,
            22f, // F: lib/ui/kit/scenes/servers_link_scene.dart:120
            if (state == ServersLinkState.Failed) palette.muted else palette.accent,
            if (first) SceneDraw.interval(t, .45f, .9f) else 1f, // F: lib/ui/kit/scenes/servers_link_scene.dart:122
        )

        when (state) {
            ServersLinkState.Idle -> {
                val show = SceneDraw.interval(t, if (intro) .7f else 0f, 1f) // F: lib/ui/kit/scenes/servers_link_scene.dart:127
                val dot = SceneDraw.fill(SceneDraw.fade(palette.line, show))
                for (p in dots) canvas.drawCircle(p, 1.6f, dot) // F: lib/ui/kit/scenes/servers_link_scene.dart:130
            }
            ServersLinkState.Linking -> {
                // A wash under the link, then the link itself drawing across.
                val draw = SceneDraw.interval(t, 0f, .8f) // F: lib/ui/kit/scenes/servers_link_scene.dart:134
                SceneDraw.partialPath(canvas, link, draw, SceneDraw.pen(palette.accentSoft, 9f)) // F: lib/ui/kit/scenes/servers_link_scene.dart:139
                SceneDraw.partialPath(canvas, link, draw, SceneDraw.pen(palette.accent, SceneDraw.HAIRLINE))
                if (frame.looping) {
                    // A dot travelling phone → computer, fading at both ends.
                    val along = frame.loop
                    canvas.drawCircle(
                        metric.getPosition(metric.length * along),
                        4f, // F: lib/ui/kit/scenes/servers_link_scene.dart:158
                        SceneDraw.fill(SceneDraw.fade(palette.accent, travelFade(along))),
                    )
                } else {
                    // Still frame (tests, reduced motion): the dot rests midway.
                    val land = SceneDraw.interval(t, .6f, 1f) // F: lib/ui/kit/scenes/servers_link_scene.dart:163
                    if (land > 0f) canvas.drawCircle(middle, 4 * land, SceneDraw.fill(palette.accent)) // F: lib/ui/kit/scenes/servers_link_scene.dart:165
                }
            }
            ServersLinkState.Linked -> {
                SceneDraw.partialPath(
                    canvas,
                    link,
                    SceneDraw.interval(t, 0f, .35f), // F: lib/ui/kit/scenes/servers_link_scene.dart:172
                    SceneDraw.pen(palette.accent, 4f), // F: lib/ui/kit/scenes/servers_link_scene.dart:173
                )
                val land = SceneDraw.interval(t, .25f, .7f, SceneDraw.land) // F: lib/ui/kit/scenes/servers_link_scene.dart:175
                if (land > 0f) {
                    canvas.drawCircle(middle, 6.5f * land, SceneDraw.fill(palette.accent)) // F: lib/ui/kit/scenes/servers_link_scene.dart:177
                    canvas.drawCircle(middle, 11 * land, SceneDraw.pen(palette.accentSoft, 3f)) // F: lib/ui/kit/scenes/servers_link_scene.dart:180,181
                }
                // The spark: rays thrown out once, settling as short ticks that stay in the finished frame.
                val thrown = SceneDraw.interval(t, .35f, 1f, SceneDraw.land) // F: lib/ui/kit/scenes/servers_link_scene.dart:186
                if (thrown > 0f) {
                    val pen = SceneDraw.pen(SceneDraw.fade(palette.accent, .85f), SceneDraw.HAIRLINE) // F: lib/ui/kit/scenes/servers_link_scene.dart:189
                    for (i in 0 until 5) { // F: lib/ui/kit/scenes/servers_link_scene.dart:192
                        // A fan above the link: -150° .. -30°.
                        val a = -PI * (5.0 / 6) + i * PI * (1.0 / 6) // F: lib/ui/kit/scenes/servers_link_scene.dart:194
                        val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                        val start = 13 + 3 * thrown // F: lib/ui/kit/scenes/servers_link_scene.dart:196
                        canvas.drawLine(middle + d * start, middle + d * (start + 5 * thrown), pen) // F: lib/ui/kit/scenes/servers_link_scene.dart:199
                    }
                }
            }
            ServersLinkState.Failed -> {
                val draw = SceneDraw.interval(t, 0f, .5f) // F: lib/ui/kit/scenes/servers_link_scene.dart:205
                val apart = SceneDraw.interval(t, .35f, 1f) * 2.5f // F: lib/ui/kit/scenes/servers_link_scene.dart:206
                val pen = SceneDraw.pen(palette.muted, SceneDraw.HAIRLINE + .5f) // F: lib/ui/kit/scenes/servers_link_scene.dart:207
                canvas.save()
                canvas.translate(-apart, apart * .4f) // F: lib/ui/kit/scenes/servers_link_scene.dart:209
                SceneDraw.partialPath(canvas, left, draw, pen)
                canvas.restore()
                canvas.save()
                canvas.translate(apart, apart * .4f) // F: lib/ui/kit/scenes/servers_link_scene.dart:213
                SceneDraw.partialPath(canvas, right, draw, pen)
                canvas.restore()
                val ends = SceneDraw.interval(t, .45f, 1f) // F: lib/ui/kit/scenes/servers_link_scene.dart:216
                if (ends > 0f) {
                    val end = SceneDraw.fill(SceneDraw.fade(palette.failure, ends))
                    canvas.drawCircle(leftEnd + Offset(-apart, apart * .4f), 3.2f, end) // F: lib/ui/kit/scenes/servers_link_scene.dart:219
                    canvas.drawCircle(rightEnd + Offset(apart, apart * .4f), 3.2f, end) // F: lib/ui/kit/scenes/servers_link_scene.dart:220
                }
            }
        }
    }
}
