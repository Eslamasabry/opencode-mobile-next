package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitCurves
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.HAIRLINE
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.STROKE
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.fade
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.interval
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.pen
import kotlin.math.PI
import kotlin.math.ceil

// The AI Team's two discovery drawings (team_discover_scenes.dart), in the team's line-drawn cast (TeamCast):
//
// - TeamDiscoverTeaserScene: the Work tab's small entry while the team is off: three agents, the one in front
//   holding up a task card.
// - TeamDiscoverRelayScene: the intro's hero: a task card travels from the planner through the worker to the
//   reviewer and lands with a check: plan, work, check, merge, in one line.
//
// Both are resting drawings: the entrance plays once, nothing loops.

/** Three agents, the one in front (accent) holding up a task card. Small: it leads a row on the Work tab. */
public object TeamDiscoverTeaserScene : KitDrawnScene {
    override val box: Size = Size(90f, 60f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:27
    internal val LEAD = Offset(45f, 58f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:29
    internal val LEFT = Offset(19f, 58f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:30
    internal val RIGHT = Offset(71f, 58f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:31

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        with(TeamCast) {
            // The two teammates first, then the one in front.
            for ((base, begin, look) in listOf(
                Triple(LEFT, 0f, Offset(.8f, -.7f)), // F: lib/ui/kit/scenes/team_discover_scenes.dart:40
                Triple(RIGHT, .12f, Offset(-.8f, -.7f)), // F: lib/ui/kit/scenes/team_discover_scenes.dart:41
            )) {
                val rise = interval(t, begin, begin + .45f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_discover_scenes.dart:43
                agent(
                    p,
                    TeamAgentPose(
                        base = base + Offset(0f, 10f * (1 - rise)), // F: lib/ui/kit/scenes/team_discover_scenes.dart:48
                        scale = .8f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:49
                        show = interval(t, begin, begin + .3f), // F: lib/ui/kit/scenes/team_discover_scenes.dart:50
                        eyes = interval(t, .6f, .85f), // F: lib/ui/kit/scenes/team_discover_scenes.dart:51
                        look = look,
                    ),
                )
            }
            val lift = interval(t, .45f, .85f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_discover_scenes.dart:57
            val rise = interval(t, .2f, .6f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_discover_scenes.dart:58
            val base = LEAD + Offset(0f, 10f * (1 - rise)) // F: lib/ui/kit/scenes/team_discover_scenes.dart:59
            agent(
                p,
                TeamAgentPose(
                    base = base,
                    scale = .9f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:65
                    show = interval(t, .2f, .45f), // F: lib/ui/kit/scenes/team_discover_scenes.dart:66
                    eyes = interval(t, .7f, .95f), // F: lib/ui/kit/scenes/team_discover_scenes.dart:67
                    look = Offset(0f, -1f),
                    lead = true,
                    leftHand = if (lift > 0f) base + Offset(-8f, -20f - 14f * lift) else null, // F: lib/ui/kit/scenes/team_discover_scenes.dart:70
                    rightHand = if (lift > 0f) base + Offset(8f, -20f - 14f * lift) else null, // F: lib/ui/kit/scenes/team_discover_scenes.dart:71
                ),
            )
            // The task it holds up: the one thing that matters.
            card(
                p,
                centre = Offset(LEAD.x, 11f + 8f * (1 - lift)), // F: lib/ui/kit/scenes/team_discover_scenes.dart:78
                width = 22f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:79
                height = 15f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:80
                scale = lift,
                opacity = interval(t, .45f, .6f), // F: lib/ui/kit/scenes/team_discover_scenes.dart:82
            )
        }
    }
}

/**
 * The intro's hero: a task card goes from the planner (left) through the worker (middle) to the reviewer (right)
 * along a dotted route, lighting each stop as it passes, and lands with a check and a few sparks. The finished frame
 * keeps the route, the lit stops and the checked card, so it reads still.
 */
public object TeamDiscoverRelayScene : KitDrawnScene {
    override val box: Size = Size(200f, 112f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:96

    /** The relay shows progress along a line (LAY-8): it mirrors under RTL, so the card travels from the start edge. */
    override val mirrorsInRtl: Boolean
        get() = true

    internal const val GROUND: Float = 110.0f // F: lib/ui/kit/scenes/team_discover_scenes.dart:103
    internal val AGENTS =
        listOf(
            Offset(40f, GROUND), // F: lib/ui/kit/scenes/team_discover_scenes.dart:105
            Offset(100f, GROUND), // F: lib/ui/kit/scenes/team_discover_scenes.dart:106
            Offset(160f, GROUND), // F: lib/ui/kit/scenes/team_discover_scenes.dart:107
        )

    /** Where the card rests above each agent: the three stops. */
    internal val STOPS = listOf(Offset(40f, 48f), Offset(100f, 30f), Offset(160f, 48f)) // F: lib/ui/kit/scenes/team_discover_scenes.dart:111

    /** Where each stop sits along the route (0..1). */
    internal val STOP_AT = listOf(0.0f, .5f, 1.0f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:127

    private val route by lazy {
        Path().apply {
            moveTo(STOPS[0].x, STOPS[0].y)
            quadraticTo(70f, 20f, STOPS[1].x, STOPS[1].y) // F: lib/ui/kit/scenes/team_discover_scenes.dart:115
            quadraticTo(130f, 20f, STOPS[2].x, STOPS[2].y) // F: lib/ui/kit/scenes/team_discover_scenes.dart:116
        }
    }

    private val metric: PathMeasure by lazy { PathMeasure().apply { setPath(route, false) } }

    /** The route's length in scene units. */
    public val routeLength: Float
        get() = metric.length

    /** The point at [fraction] (0..1) of the route. */
    public fun along(fraction: Float): Offset = metric.getPosition(metric.length * fraction)

    /** The dots of the route, every 9 units, stopping short of the end (team_discover_scenes.dart:121-124). */
    public val dots: List<Offset> by lazy {
        val step = 9.0f // F: lib/ui/kit/scenes/team_discover_scenes.dart:122
        val stop = metric.length - 6 // F: lib/ui/kit/scenes/team_discover_scenes.dart:122
        generateSequence(step) { it + step }.takeWhile { it < stop }.map { metric.getPosition(it) }.toList()
    }

    /** How many dots show at [route] (0..1) of the route's own entrance (team_discover_scenes.dart:143). */
    public fun shownDots(route: Float): Int = ceil(dots.size * route).toInt()

    private val check by lazy {
        Path().apply {
            moveTo(-5f, 0f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:130
            lineTo(-1.5f, 3.5f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:131
            lineTo(5.5f, -4f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:132
        }
    }

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance

        // The route draws itself in, dot by dot.
        val routeIn = interval(t, .1f, .45f, FlutterKitCurves.steady) // F: lib/ui/kit/scenes/team_discover_scenes.dart:140
        if (routeIn > 0f) for (at in dots.take(shownDots(routeIn))) drawCircle(p.line, 1.6f, at) // F: lib/ui/kit/scenes/team_discover_scenes.dart:145

        // The card travels the route; each stop lights as it arrives.
        val travel = interval(t, .45f, .85f, FlutterKitCurves.emphasized) // F: lib/ui/kit/scenes/team_discover_scenes.dart:150
        STOPS.forEachIndexed { i, stop ->
            val lit = travel >= STOP_AT[i] && t >= .45f // F: lib/ui/kit/scenes/team_discover_scenes.dart:152
            val appear = interval(t, .1f + i * .1f, .3f + i * .1f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:153
            if (appear > 0f) {
                if (lit) drawCircle(fade(p.accent, appear), 3.6f, stop) // F: lib/ui/kit/scenes/team_discover_scenes.dart:157
                else drawCircle(fade(p.line, appear), 3.6f, stop, style = pen(HAIRLINE)) // F: lib/ui/kit/scenes/team_discover_scenes.dart:157
            }
        }

        val arrive = interval(t, .3f, .45f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_discover_scenes.dart:164
        val at = along(travel)
        val done = interval(t, .82f, 1f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:166
        with(TeamCast) {
            card(
                p,
                centre = at,
                width = 30f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:171
                height = 21f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:172
                lines = if (done > 0f) 1 else 2, // F: lib/ui/kit/scenes/team_discover_scenes.dart:173
                scale = arrive,
                opacity = interval(t, .3f, .4f), // F: lib/ui/kit/scenes/team_discover_scenes.dart:175
            )
            if (done > 0f) {
                withTransform({ translate(at.x + 6, at.y + 2) }) { // F: lib/ui/kit/scenes/team_discover_scenes.dart:179
                    drawPartial(check, done, p.success, STROKE * .7f) // F: lib/ui/kit/scenes/team_discover_scenes.dart:184
                }
                val burst = interval(t, .88f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_discover_scenes.dart:187
                for (i in 0 until 5) { // F: lib/ui/kit/scenes/team_discover_scenes.dart:188
                    val even = (i and 1) == 0
                    spark(
                        fade(if (even) p.accent else p.success, burst.coerceIn(0f, 1f)),
                        centre = at,
                        angle = -PI.toFloat() / 2 + (i - 2) * .55f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:189
                        radius = 16 + 6 * burst, // F: lib/ui/kit/scenes/team_discover_scenes.dart:195
                        length = if (even) 5f else 3.5f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:196
                    )
                }
            }

            // The team, arriving one by one and watching the card go by.
            AGENTS.forEachIndexed { i, base ->
                val begin = i * .1f // F: lib/ui/kit/scenes/team_discover_scenes.dart:203
                val rise = interval(t, begin, begin + .4f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_discover_scenes.dart:204
                val dx = (at.x - base.x) / 60 // F: lib/ui/kit/scenes/team_discover_scenes.dart:205
                val holding = i == 0 && travel <= 0f
                agent(
                    p,
                    TeamAgentPose(
                        base = base + Offset(0f, 14f * (1 - rise)), // F: lib/ui/kit/scenes/team_discover_scenes.dart:211
                        show = interval(t, begin, begin + .3f), // F: lib/ui/kit/scenes/team_discover_scenes.dart:212
                        eyes = interval(t, .3f + begin, .5f + begin), // F: lib/ui/kit/scenes/team_discover_scenes.dart:213
                        look = Offset(dx.coerceIn(-1f, 1f), -.9f), // F: lib/ui/kit/scenes/team_discover_scenes.dart:214
                        // The middle one works: the accent, the thing that matters.
                        lead = i == 1,
                        joy = i == 2 && done >= .6f, // F: lib/ui/kit/scenes/team_discover_scenes.dart:217
                        rightHand = if (holding && arrive > 0f) TeamCast.hand(base, 1f, 1f, arrive) else null,
                        leftHand = if (i == 2 && done > 0f) TeamCast.hand(base, 1f, -1f, done) else null, // F: lib/ui/kit/scenes/team_discover_scenes.dart:221
                    ),
                )
            }
        }
    }
}

/** The two discovery drawings in the order of the Flutter gallery (kit_scenes_golden_test.dart `team_discover`). */
public val TeamDiscoverScenesAll: List<KitDrawnScene> = listOf(TeamDiscoverTeaserScene, TeamDiscoverRelayScene)
