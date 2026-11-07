package io.github.eslamasabry.oc.ui.kit.scene

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitCurves
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.HAIRLINE
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.STROKE
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.fade
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.interval
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.pen
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnInk.wave
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The AI Team's drawings (team_scenes.dart): a small cast of line-drawn agents (a rounded body, a round head, two
// dot eyes and nothing more) in the brand's stroke.
//
// - TeamBoardScene: the team gathered at an empty board (no tasks yet).
// - TeamPlanningScene: two agents passing a card while the planner plans (ambient).
// - TeamWakingScene: the team waking up while it starts (ambient).
// - TeamMergedScene: a task merged; the card lands with a check and the agents cheer (a one-time celebration).
// - TeamNudgeScene: an agent peeks over the "Needs you" block and waves once.
// - TeamRestScene: one agent dozing (no agents running).
// - TeamIdleScene: two agents at ease (the Work tab's "Nothing running").

/** How one agent stands in a frame (team_scenes.dart:25-66). */
@Immutable
public data class TeamAgentPose(
    /** The bottom centre of the body, in scene units. */
    val base: Offset,
    val scale: Float = 1f,
    /** How much of the outline has drawn itself in (0..1). */
    val draw: Float = 1f,
    /** Overall opacity (0..1), for a figure arriving. */
    val show: Float = 1f,
    /** 0 closed (a line), 1 open (dots). */
    val eyes: Float = 1f,
    /** Where the eyes look, each axis -1..1. */
    val look: Offset = Offset.Zero,
    /** Hands in scene units; an arm is drawn from the nearer shoulder. Null keeps the arm down (not drawn). */
    val leftHand: Offset? = null,
    val rightHand: Offset? = null,
    /** The agent that matters in the scene: the accent outline and wash. */
    val lead: Boolean = false,
    /** Happy closed eyes (a small arch each) instead of dots. */
    val joy: Boolean = false,
)

/** Draws the cast; every team scene uses it, so the agents are one family (team_scenes.dart:68-236). */
public object TeamCast {
    public const val BODY_WIDTH: Float = 28.0f // F: lib/ui/kit/scenes/team_scenes.dart:70
    public const val BODY_HEIGHT: Float = 20.0f // F: lib/ui/kit/scenes/team_scenes.dart:71
    public const val HEAD_RADIUS: Float = 9.0f // F: lib/ui/kit/scenes/team_scenes.dart:72
    internal const val NECK: Float = 2.0f // F: lib/ui/kit/scenes/team_scenes.dart:73

    /** The head's centre above the base, at scale 1. */
    public const val HEAD_LIFT: Float = BODY_HEIGHT + NECK + HEAD_RADIUS

    /** Where a hand hangs at rest, relative to the base (scale 1). */
    public val REST_HAND: Offset = Offset(BODY_WIDTH / 2 + 1, -4f) // F: lib/ui/kit/scenes/team_scenes.dart:79

    /** Where a raised hand is, relative to the base (scale 1). */
    public val RAISED_HAND: Offset = Offset(BODY_WIDTH / 2 + 7, -BODY_HEIGHT - 12) // F: lib/ui/kit/scenes/team_scenes.dart:82

    /** The body's dome corner radius (team_scenes.dart:86). */
    internal const val DOME: Float = 11.0f // F: lib/ui/kit/scenes/team_scenes.dart:86

    /** The body: a dome on a flat base, bottom centre at the origin. */
    private val body by lazy {
        Path().apply {
            val w = BODY_WIDTH / 2 // F: lib/ui/kit/scenes/team_scenes.dart:86
            val h = BODY_HEIGHT
            moveTo(-w, 0f)
            lineTo(-w, -h + DOME)
            sceneArcTo(Offset(-w, -h + DOME), Offset(-w + DOME, -h), DOME)
            lineTo(w - DOME, -h)
            sceneArcTo(Offset(w - DOME, -h), Offset(w, -h + DOME), DOME)
            lineTo(w, 0f)
            close()
        }
    }

    private val head by lazy {
        Path().apply { addOval(Rect(Offset(0f, -HEAD_LIFT), HEAD_RADIUS)) }
    }

    /** The outline width of an agent at [scale], drawn inside its scaled frame (team_scenes.dart:110). */
    public fun penWidth(scale: Float): Float = STROKE * .8f / scale // F: lib/ui/kit/scenes/team_scenes.dart:110

    /** One agent in [pose] (team_scenes.dart:103-164). */
    public fun DrawScope.agent(palette: KitDrawnPalette, pose: TeamAgentPose) {
        if (pose.show <= 0f) return
        val s = pose.scale
        val outline = fade(if (pose.lead) palette.accent else palette.muted, pose.show)

        // Arms first: the body covers where they join the shoulders.
        for ((hand, side) in listOf(pose.leftHand to -1f, pose.rightHand to 1f)) {
            if (hand == null) continue
            val shoulder = pose.base + Offset(side * (BODY_WIDTH / 2 - 4), -BODY_HEIGHT + 7) * s // F: lib/ui/kit/scenes/team_scenes.dart:117
            drawSceneLine(outline, shoulder, hand, STROKE * .8f) // F: lib/ui/kit/scenes/team_scenes.dart:111
        }

        withTransform({
            translate(pose.base.x, pose.base.y)
            scale(s, s, Offset.Zero)
        }) {
            val fill = fade(palette.surface, pose.show * pose.draw)
            drawPath(body, fill)
            drawPath(head, fill)
            if (pose.lead) {
                val wash = fade(palette.accentSoft, pose.show * pose.draw)
                drawPath(body, wash)
                drawPath(head, wash)
            }
            drawPartial(body, pose.draw, outline, penWidth(s))
            drawPartial(head, pose.draw, outline, penWidth(s))

            if (pose.draw >= .6f) { // F: lib/ui/kit/scenes/team_scenes.dart:137
                val eyeColor = fade(outline, (pose.draw - .6f) / .4f) // F: lib/ui/kit/scenes/team_scenes.dart:138
                val centre = eyeCentre(pose.look)
                for (side in listOf(-1f, 1f)) {
                    val eye = centre + Offset(side * EYE_SPREAD, 0f)
                    if (pose.joy) {
                        val arch = Rect(eye + Offset(0f, 1.2f), 2.2f) // F: lib/ui/kit/scenes/team_scenes.dart:148
                        drawArc(
                            eyeColor,
                            (PI * 1.1).toFloat().sceneDegrees(), // F: lib/ui/kit/scenes/team_scenes.dart:149
                            (PI * .8).toFloat().sceneDegrees(), // F: lib/ui/kit/scenes/team_scenes.dart:150
                            false,
                            arch.topLeft,
                            arch.size,
                            style = pen(1.6f), // F: lib/ui/kit/scenes/team_scenes.dart:152
                        )
                    } else {
                        val dot = sceneRect(eye, EYE_WIDTH, eyeHeight(pose.eyes))
                        drawOval(eyeColor, dot.topLeft, dot.size)
                    }
                }
            }
        }
    }

    internal const val EYE_SPREAD: Float = 3.6f // F: lib/ui/kit/scenes/team_scenes.dart:145
    internal const val EYE_WIDTH: Float = 3.6f // F: lib/ui/kit/scenes/team_scenes.dart:157

    /** The two eyes' midpoint for [look] (team_scenes.dart:139-143). */
    public fun eyeCentre(look: Offset): Offset =
        Offset(0f, -HEAD_LIFT + .5f) + // F: lib/ui/kit/scenes/team_scenes.dart:143
            Offset(look.x.coerceIn(-1f, 1f) * 2.2f, look.y.coerceIn(-1f, 1f) * 1.6f) // F: lib/ui/kit/scenes/team_scenes.dart:140,141

    /** An open dot eye's height for [eyes] (0 a line, 1 open) (team_scenes.dart:155-157). */
    public fun eyeHeight(eyes: Float): Float = .9f + 2.7f * eyes.coerceIn(0f, 1f) // F: lib/ui/kit/scenes/team_scenes.dart:157

    /** The hand between rest and raised on [side] (-1 left, 1 right) (team_scenes.dart:167-171). */
    public fun hand(base: Offset, scale: Float, side: Float, raise: Float): Offset {
        val rest = Offset(side * REST_HAND.x, REST_HAND.y)
        val up = Offset(side * RAISED_HAND.x, RAISED_HAND.y)
        return base + lerp(rest, up, raise) * scale
    }

    /** The lines on a card: (start x, end x, y) for each of [lines] (team_scenes.dart:210-215). */
    public fun cardLines(width: Float, height: Float, lines: Int): List<Triple<Float, Float, Float>> {
        val gap = height / (lines + 1)
        return (1..lines).map { i ->
            val y = -height / 2 + gap * i // F: lib/ui/kit/scenes/team_scenes.dart:212
            val end = if (i == lines) width * .1f else width * .28f // F: lib/ui/kit/scenes/team_scenes.dart:213
            Triple(-width / 2 + 4.5f, end, y) // F: lib/ui/kit/scenes/team_scenes.dart:214
        }
    }

    /** A small card (a task) centred at [centre]: [width] x [height] (team_scenes.dart:174-217). */
    public fun DrawScope.card(
        palette: KitDrawnPalette,
        centre: Offset,
        width: Float = 18f, // F: lib/ui/kit/scenes/team_scenes.dart:178
        height: Float = 13f, // F: lib/ui/kit/scenes/team_scenes.dart:179
        angle: Float = 0f,
        scale: Float = 1f,
        opacity: Float = 1f,
        lines: Int = 2, // F: lib/ui/kit/scenes/team_scenes.dart:183
    ) {
        if (opacity <= 0f || scale <= 0f) return
        withTransform({
            translate(centre.x, centre.y)
            rotate(angle.sceneDegrees(), Offset.Zero)
            this.scale(scale, scale, Offset.Zero)
        }) {
            val rect = sceneRect(Offset.Zero, width, height)
            val topLeft = rect.topLeft
            val size = rect.size
            val corner = CornerRadius(3.5f) // F: lib/ui/kit/scenes/team_scenes.dart:192
            drawRoundRect(fade(palette.surface, opacity), topLeft, size, corner)
            drawRoundRect(fade(palette.accentSoft, opacity), topLeft, size, corner)
            drawRoundRect(fade(palette.accent, opacity), topLeft, size, corner, style = pen(HAIRLINE))
            val lineColor = fade(palette.accent, opacity * .8f) // F: lib/ui/kit/scenes/team_scenes.dart:207
            for ((from, to, y) in cardLines(width, height, lines))
                drawSceneLine(lineColor, Offset(from, y), Offset(to, y), HAIRLINE * .8f) // F: lib/ui/kit/scenes/team_scenes.dart:208
        }
    }

    /** A short spark stroke pointing away from [centre] at [angle] (team_scenes.dart:220-235). */
    public fun DrawScope.spark(
        color: Color,
        centre: Offset,
        angle: Float,
        radius: Float,
        length: Float = 5f, // F: lib/ui/kit/scenes/team_scenes.dart:226
        width: Float = HAIRLINE,
    ) {
        val dir = Offset(cos(angle), sin(angle))
        drawSceneLine(color, centre + dir * radius, centre + dir * (radius + length), width)
    }
}

/** No tasks yet: the team gathered at an empty board whose one accent slot waits for a task. Still once drawn. */
public object TeamBoardScene : KitDrawnScene {
    private val board by lazy {
        Path().apply { addRoundRect(RoundRect(Rect(18f, 12f, 102f, 74f), CornerRadius(10f))) } // F: lib/ui/kit/scenes/team_scenes.dart:244-248
    }

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        drawPartial(board, interval(t, 0f, .45f), p.muted, STROKE * .8f) // F: lib/ui/kit/scenes/team_scenes.dart:260,261
        // Three columns: their heads and the hairlines between them.
        val columns = interval(t, .25f, .6f) // F: lib/ui/kit/scenes/team_scenes.dart:264
        if (columns > 0f) {
            for (x in listOf(46f, 74f)) // F: lib/ui/kit/scenes/team_scenes.dart:267
                drawSceneLine(fade(p.line, columns), Offset(x, 26f), Offset(x, 26f + 38f * columns), HAIRLINE) // F: lib/ui/kit/scenes/team_scenes.dart:268
            for (x in listOf(25f, 53f, 81f)) // F: lib/ui/kit/scenes/team_scenes.dart:274
                drawSceneLine(fade(p.muted, columns), Offset(x, 21f), Offset(x + 14f * columns, 21f), HAIRLINE) // F: lib/ui/kit/scenes/team_scenes.dart:275
        }
        // The empty slot a task will fill: the one accent.
        val slot = interval(t, .7f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:279
        if (slot > 0f) {
            withTransform({
                translate(32f, 36f) // F: lib/ui/kit/scenes/team_scenes.dart:282
                scale(slot, slot, Offset.Zero)
            }) {
                val rect = sceneRect(Offset.Zero, 17f, 13f) // F: lib/ui/kit/scenes/team_scenes.dart:285
                val topLeft = rect.topLeft
                val size = rect.size
                val corner = CornerRadius(3.5f) // F: lib/ui/kit/scenes/team_scenes.dart:286
                drawRoundRect(p.accentSoft, topLeft, size, corner)
                drawRoundRect(p.accent, topLeft, size, corner, style = pen(HAIRLINE))
                drawSceneLine(p.accent, Offset(-3f, 0f), Offset(3f, 0f), HAIRLINE) // F: lib/ui/kit/scenes/team_scenes.dart:291
                drawSceneLine(p.accent, Offset(0f, -3f), Offset(0f, 3f), HAIRLINE) // F: lib/ui/kit/scenes/team_scenes.dart:292
            }
        }

        // The team arrives in front of it, one by one.
        fun pose(base: Offset, begin: Float, scale: Float, look: Offset, rightHand: Offset? = null): TeamAgentPose {
            val arrive = interval(t, begin, begin + .4f) // F: lib/ui/kit/scenes/team_scenes.dart:304
            val rise = interval(t, begin, begin + .4f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:305
            return TeamAgentPose(
                base = base + Offset(0f, 14f * (1 - rise)), // F: lib/ui/kit/scenes/team_scenes.dart:307
                scale = scale,
                show = arrive,
                eyes = interval(t, .8f, 1f), // F: lib/ui/kit/scenes/team_scenes.dart:310
                look = look,
                rightHand = rightHand,
            )
        }
        val wave = interval(t, .7f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:316
        with(TeamCast) {
            agent(p, pose(Offset(30f, 114f), .3f, 1f, Offset(.7f, -.8f))) // F: lib/ui/kit/scenes/team_scenes.dart:320
            val right = Offset(90f, 114f) // F: lib/ui/kit/scenes/team_scenes.dart:326
            val waving = if (wave > 0f) TeamCast.hand(right, 1f, 1f, wave) else null
            agent(p, pose(right, .5f, 1f, Offset(-.8f, -.6f), waving)) // F: lib/ui/kit/scenes/team_scenes.dart:327,329
            agent(p, pose(Offset(60f, 118f), .4f, .9f, Offset(0f, -1f))) // F: lib/ui/kit/scenes/team_scenes.dart:338
        }
    }
}

/**
 * The planner planning: the planner (accent) and a teammate pass a card between them; while waiting it travels from
 * one to the other and back, their eyes following it.
 */
public object TeamPlanningScene : KitDrawnScene {
    override val box: Size = Size(120f, 84f) // F: lib/ui/kit/scenes/team_scenes.dart:350
    internal val LEFT = Offset(30f, 82f) // F: lib/ui/kit/scenes/team_scenes.dart:352
    internal val RIGHT = Offset(90f, 82f) // F: lib/ui/kit/scenes/team_scenes.dart:353

    /** Where the card is between the two (0 left, 1 right); held up between them at rest (team_scenes.dart:361). */
    public fun pass(frame: KitDrawnFrame): Float = if (frame.looping) wave(frame.loop, .25f) else .5f // F: lib/ui/kit/scenes/team_scenes.dart:361

    /** The card's centre at [pass] (team_scenes.dart:362). */
    public fun cardAt(pass: Float): Offset = Offset(50f + 20f * pass, 42f - 12f * sin(PI.toFloat() * pass)) // F: lib/ui/kit/scenes/team_scenes.dart:362

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        val pass = pass(frame)
        val held = cardAt(pass)

        fun reach(base: Offset, side: Float, amount: Float): Offset {
            val shoulder = base + Offset(side * (TeamCast.BODY_WIDTH / 2 - 4), -TeamCast.BODY_HEIGHT + 7) // F: lib/ui/kit/scenes/team_scenes.dart:367
            val toward = held - shoulder
            val length = toward.getDistance()
            val arm = min(length - 9, 10 + 8 * amount) // F: lib/ui/kit/scenes/team_scenes.dart:370
            return shoulder + toward / length * arm
        }

        fun look(base: Offset): Offset {
            val d = held - (base + Offset(0f, -TeamCast.HEAD_LIFT))
            return d / d.getDistance()
        }

        val arms = interval(t, .45f, .85f) // F: lib/ui/kit/scenes/team_scenes.dart:380
        data class Who(val base: Offset, val side: Float, val begin: Float, val lead: Boolean, val amount: Float)
        with(TeamCast) {
            for (who in listOf(Who(LEFT, 1f, 0f, true, 1 - pass), Who(RIGHT, -1f, .15f, false, pass))) { // F: lib/ui/kit/scenes/team_scenes.dart:383
                val arrive = interval(t, who.begin, who.begin + .45f) // F: lib/ui/kit/scenes/team_scenes.dart:385
                val rise = interval(t, who.begin, who.begin + .45f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:386
                val hand = if (arms > 0f) reach(who.base, who.side, who.amount * arms) else null
                agent(
                    p,
                    TeamAgentPose(
                        base = who.base + Offset(0f, 12f * (1 - rise)), // F: lib/ui/kit/scenes/team_scenes.dart:392
                        show = arrive,
                        lead = who.lead,
                        eyes = interval(t, .5f, .8f), // F: lib/ui/kit/scenes/team_scenes.dart:395
                        look = look(who.base),
                        leftHand = if (who.side < 0f) hand else null,
                        rightHand = if (who.side > 0f) hand else null,
                    ),
                )
            }
            val pop = interval(t, .55f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:403
            card(
                p,
                centre = held,
                width = 24f, // F: lib/ui/kit/scenes/team_scenes.dart:408
                height = 17f, // F: lib/ui/kit/scenes/team_scenes.dart:409
                angle = (pass - .5f) * .5f, // F: lib/ui/kit/scenes/team_scenes.dart:410
                scale = pop,
                opacity = interval(t, .55f, .8f), // F: lib/ui/kit/scenes/team_scenes.dart:412
            )
        }
    }
}

/**
 * The team starting: three agents wake (their eyes open) under a spark that powers on; while it starts they breathe
 * in turn and blink.
 */
public object TeamWakingScene : KitDrawnScene {
    override val box: Size = Size(140f, 92f) // F: lib/ui/kit/scenes/team_scenes.dart:423
    internal const val GROUND: Float = 86.0f // F: lib/ui/kit/scenes/team_scenes.dart:425
    internal val SPARK = Offset(70f, 14f) // F: lib/ui/kit/scenes/team_scenes.dart:426

    /** The three agents: (index, x, scale) (team_scenes.dart:442-446). */
    internal val AGENTS =
        listOf(
            Triple(0, 36.0f, .88f), // F: lib/ui/kit/scenes/team_scenes.dart:443
            Triple(1, 70.0f, 1.0f), // F: lib/ui/kit/scenes/team_scenes.dart:444
            Triple(2, 104.0f, .88f), // F: lib/ui/kit/scenes/team_scenes.dart:445
        )

    /** Agent [i]'s eyes at [frame]: they open in turn, then each blinks at its own moment of the breath. */
    public fun eyes(frame: KitDrawnFrame, i: Int): Float {
        var eyes = interval(frame.entrance, .7f + .08f * i, .86f + .08f * i) // F: lib/ui/kit/scenes/team_scenes.dart:450
        if (frame.looping) {
            val since = (frame.loop - (.2f + i * .3f)).mod(1f) // F: lib/ui/kit/scenes/team_scenes.dart:452
            if (since < .05f) eyes = abs(since - .025f) / .025f // F: lib/ui/kit/scenes/team_scenes.dart:453
        }
        return eyes
    }

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        val ground = interval(t, 0f, .35f) // F: lib/ui/kit/scenes/team_scenes.dart:433
        val half = 56f * ground // F: lib/ui/kit/scenes/team_scenes.dart:436
        if (ground > 0f) drawSceneLine(p.line, Offset(70f - half, GROUND), Offset(70f + half, GROUND), HAIRLINE) // F: lib/ui/kit/scenes/team_scenes.dart:436,437
        with(TeamCast) {
            for ((i, x, size) in AGENTS) {
                val begin = .1f + .1f * i // F: lib/ui/kit/scenes/team_scenes.dart:447
                val bob = if (frame.looping) 2.5f * wave(frame.loop, i / 3f) else 0f // F: lib/ui/kit/scenes/team_scenes.dart:448
                val eyes = eyes(frame, i)
                agent(
                    p,
                    TeamAgentPose(
                        base = Offset(x, GROUND - 2.5f - bob), // F: lib/ui/kit/scenes/team_scenes.dart:459
                        scale = size,
                        draw = interval(t, begin, begin + .5f), // F: lib/ui/kit/scenes/team_scenes.dart:461
                        eyes = eyes,
                        look = Offset(0f, -.6f * eyes), // F: lib/ui/kit/scenes/team_scenes.dart:463
                        lead = i == 1,
                    ),
                )
            }
            // The spark that wakes them: lands, then its rays breathe.
            val land = interval(t, .65f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:470
            if (land > 0f) {
                drawCircle(p.accent, 4.5f * land, SPARK) // F: lib/ui/kit/scenes/team_scenes.dart:472
                val breath = if (frame.looping) wave(frame.loop) else 0f
                val rays = interval(t, .8f, 1f) // F: lib/ui/kit/scenes/team_scenes.dart:474
                for (i in 0 until 6) { // F: lib/ui/kit/scenes/team_scenes.dart:475
                    spark(
                        fade(p.accent, rays * (.9f - .35f * breath)), // F: lib/ui/kit/scenes/team_scenes.dart:478
                        centre = SPARK,
                        angle = i * PI.toFloat() / 3 - PI.toFloat() / 2, // F: lib/ui/kit/scenes/team_scenes.dart:480
                        radius = 7.5f + 1.5f * breath, // F: lib/ui/kit/scenes/team_scenes.dart:481
                        length = 3.5f * rays + 1.5f * breath, // F: lib/ui/kit/scenes/team_scenes.dart:482
                    )
                }
            }
        }
    }
}

/**
 * A task merged: its card drops into place with a check, sparks fly out, and the two agents beside it throw their
 * arms up. Played once; its finished frame keeps the sparks, so it reads as a celebration still.
 */
public object TeamMergedScene : KitDrawnScene {
    override val box: Size = Size(160f, 100f) // F: lib/ui/kit/scenes/team_scenes.dart:496
    internal val CARD = Offset(80f, 44f) // F: lib/ui/kit/scenes/team_scenes.dart:498
    internal val LEFT = Offset(30f, 96f) // F: lib/ui/kit/scenes/team_scenes.dart:499
    internal val RIGHT = Offset(130f, 96f) // F: lib/ui/kit/scenes/team_scenes.dart:500
    private val check by lazy {
        Path().apply {
            moveTo(CARD.x + 3, CARD.y + 1) // F: lib/ui/kit/scenes/team_scenes.dart:502
            lineTo(CARD.x + 7.5f, CARD.y + 5.5f) // F: lib/ui/kit/scenes/team_scenes.dart:503
            lineTo(CARD.x + 15, CARD.y - 3.5f) // F: lib/ui/kit/scenes/team_scenes.dart:504
        }
    }

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        with(TeamCast) {
            // Sparks: thrown out from the card, settling as a ring of short strokes (accent and success alternating).
            val burst = interval(t, .5f, 1f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:513
            if (burst > 0f) {
                for (i in 0 until 10) { // F: lib/ui/kit/scenes/team_scenes.dart:515
                    val even = (i and 1) == 0
                    val far = if (even) 34.0f else 29.0f // F: lib/ui/kit/scenes/team_scenes.dart:517
                    spark(
                        fade(if (even) p.accent else p.success, burst.coerceIn(0f, 1f)),
                        centre = CARD,
                        angle = i * PI.toFloat() / 5 - PI.toFloat() / 2 + .31f, // F: lib/ui/kit/scenes/team_scenes.dart:516
                        radius = 16 + (far - 16) * burst, // F: lib/ui/kit/scenes/team_scenes.dart:523
                        length = if (even) 6f else 4f, // F: lib/ui/kit/scenes/team_scenes.dart:524
                    )
                }
            }
            // The card lands with a small overshoot.
            val land = interval(t, .1f, .55f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:530
            if (land > 0f) {
                card(
                    p,
                    centre = Offset(CARD.x, -20 + (CARD.y + 20) * land), // F: lib/ui/kit/scenes/team_scenes.dart:535
                    width = 40f, // F: lib/ui/kit/scenes/team_scenes.dart:536
                    height = 28f, // F: lib/ui/kit/scenes/team_scenes.dart:537
                    lines = 3, // F: lib/ui/kit/scenes/team_scenes.dart:538
                    opacity = interval(t, .1f, .3f), // F: lib/ui/kit/scenes/team_scenes.dart:539
                )
            }
            drawPartial(check, interval(t, .5f, .75f), p.success, STROKE * .8f) // F: lib/ui/kit/scenes/team_scenes.dart:545,546

            val cheer = interval(t, .55f, .95f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:549
            for ((base, begin, look) in listOf(
                Triple(LEFT, 0f, Offset(.8f, -.5f)), // F: lib/ui/kit/scenes/team_scenes.dart:551
                Triple(RIGHT, .12f, Offset(-.8f, -.5f)), // F: lib/ui/kit/scenes/team_scenes.dart:552
            )) {
                val arrive = interval(t, begin, begin + .4f) // F: lib/ui/kit/scenes/team_scenes.dart:554
                val rise = interval(t, begin, begin + .4f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:555
                val cheering = cheer >= .6f // F: lib/ui/kit/scenes/team_scenes.dart:563
                agent(
                    p,
                    TeamAgentPose(
                        base = base + Offset(0f, 14f * (1 - rise)), // F: lib/ui/kit/scenes/team_scenes.dart:560
                        show = arrive,
                        eyes = interval(t, .35f, .6f), // F: lib/ui/kit/scenes/team_scenes.dart:562
                        joy = cheering,
                        look = if (cheering) Offset.Zero else look,
                        leftHand = if (cheer > 0f) TeamCast.hand(base, 1f, -1f, cheer) else null,
                        rightHand = if (cheer > 0f) TeamCast.hand(base, 1f, 1f, cheer) else null,
                    ),
                )
            }
        }
    }
}

/** Needs you: an agent (accent) rises over the edge of the block below and waves once, peeking over its top. */
public object TeamNudgeScene : KitDrawnScene {
    override val box: Size = Size(56f, 40f) // F: lib/ui/kit/scenes/team_scenes.dart:579
    internal val BASE = Offset(22f, 50f) // F: lib/ui/kit/scenes/team_scenes.dart:581

    /** The wave's swing about the raised hand: twice, dying away (team_scenes.dart:591-594). */
    public fun swing(t: Float): Float {
        val waving = interval(t, .55f, 1f, FlutterKitCurves.steady) // F: lib/ui/kit/scenes/team_scenes.dart:591
        return if (waving <= 0f || waving >= 1f) 0f
        else .45f * sin(waving * 4 * PI.toFloat()) * (1 - waving) // F: lib/ui/kit/scenes/team_scenes.dart:594
    }

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        val rise = interval(t, 0f, .45f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:587
        val base = BASE + Offset(0f, 30f * (1 - rise)) // F: lib/ui/kit/scenes/team_scenes.dart:588
        val raise = interval(t, .35f, .65f, FlutterKitCurves.land) // F: lib/ui/kit/scenes/team_scenes.dart:589
        val shoulder = base + Offset(TeamCast.BODY_WIDTH / 2 - 4, -TeamCast.BODY_HEIGHT + 7) // F: lib/ui/kit/scenes/team_scenes.dart:597
        val hand =
            if (raise > 0f) {
                val direction = -PI.toFloat() / 2 + .45f - .45f * (1 - raise) * 2 + swing(t) // F: lib/ui/kit/scenes/team_scenes.dart:601
                val distance = 8 + 10 * raise // F: lib/ui/kit/scenes/team_scenes.dart:602
                shoulder + Offset(cos(direction), sin(direction)) * distance
            } else null
        with(TeamCast) {
            agent(
                p,
                TeamAgentPose(
                    base = base,
                    show = interval(t, 0f, .2f), // F: lib/ui/kit/scenes/team_scenes.dart:610
                    lead = true,
                    eyes = interval(t, .3f, .5f), // F: lib/ui/kit/scenes/team_scenes.dart:612
                    look = Offset(.5f, -.2f), // F: lib/ui/kit/scenes/team_scenes.dart:613
                    rightHand = hand,
                ),
            )
            // Two short marks by the hand: "over here".
            val marks = interval(t, .7f, 1f) // F: lib/ui/kit/scenes/team_scenes.dart:618
            if (marks > 0f && hand != null) {
                for ((angle, length) in listOf(-1.2f to 4.0f, -.45f to 3.5f)) // F: lib/ui/kit/scenes/team_scenes.dart:620
                    spark(fade(p.accent, marks), centre = hand, angle = angle, radius = 4.5f, length = length * marks) // F: lib/ui/kit/scenes/team_scenes.dart:626
            }
        }
    }
}

/** No agents running: one agent dozing on the ground, with its z's. */
public object TeamRestScene : KitDrawnScene {
    override val box: Size = Size(120f, 100f) // F: lib/ui/kit/scenes/team_scenes.dart:639

    private fun z(x: Float, y: Float, s: Float): Path =
        Path().apply {
            moveTo(x, y)
            lineTo(x + s, y)
            lineTo(x, y + s)
            lineTo(x + s, y + s)
        }

    /** The two z's, one contour each (team_scenes.dart:641-650). */
    private val zs by lazy { listOf(z(70f, 44f, 7f), z(82f, 28f, 10f)) } // F: lib/ui/kit/scenes/team_scenes.dart:648,649

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        val ground = interval(t, 0f, .35f) // F: lib/ui/kit/scenes/team_scenes.dart:656
        val half = 38f * ground // F: lib/ui/kit/scenes/team_scenes.dart:659
        if (ground > 0f) drawSceneLine(p.line, Offset(54f - half, 92f), Offset(54f + half, 92f), HAIRLINE) // F: lib/ui/kit/scenes/team_scenes.dart:659,660
        with(TeamCast) {
            agent(
                p,
                TeamAgentPose(
                    base = Offset(50f, 89.5f), // F: lib/ui/kit/scenes/team_scenes.dart:668
                    draw = interval(t, .1f, .7f), // F: lib/ui/kit/scenes/team_scenes.dart:669
                    eyes = 0f,
                    look = Offset(0f, .4f), // F: lib/ui/kit/scenes/team_scenes.dart:671
                ),
            )
        }
        drawPartial(zs, interval(t, .6f, 1f), p.muted, HAIRLINE) // F: lib/ui/kit/scenes/team_scenes.dart:677
    }
}

/** Nothing running: two agents at ease, turned to each other. */
public object TeamIdleScene : KitDrawnScene {
    override val box: Size = Size(64f, 44f) // F: lib/ui/kit/scenes/team_scenes.dart:688

    override fun DrawScope.paint(frame: KitDrawnFrame) {
        val p = frame.palette
        val t = frame.entrance
        with(TeamCast) {
            for ((x, look, begin) in listOf(
                Triple(18.0f, Offset(.8f, 0f), 0f), // F: lib/ui/kit/scenes/team_scenes.dart:695
                Triple(46.0f, Offset(-.8f, 0f), .15f), // F: lib/ui/kit/scenes/team_scenes.dart:696
            )) {
                agent(
                    p,
                    TeamAgentPose(
                        base = Offset(x, 43f), // F: lib/ui/kit/scenes/team_scenes.dart:702
                        scale = .95f, // F: lib/ui/kit/scenes/team_scenes.dart:703
                        draw = interval(t, begin, begin + .6f), // F: lib/ui/kit/scenes/team_scenes.dart:704
                        eyes = interval(t, .6f, .9f), // F: lib/ui/kit/scenes/team_scenes.dart:705
                        look = look,
                    ),
                )
            }
        }
    }
}

/** The seven team drawings in the order of the Flutter gallery (kit_scenes_golden_test.dart `team`). */
public val TeamScenesAll: List<KitDrawnScene> =
    listOf(
        TeamBoardScene,
        TeamPlanningScene,
        TeamWakingScene,
        TeamMergedScene,
        TeamNudgeScene,
        TeamRestScene,
        TeamIdleScene,
    )
