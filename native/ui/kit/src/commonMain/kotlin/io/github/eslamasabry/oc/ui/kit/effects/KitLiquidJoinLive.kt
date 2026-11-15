package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind
import io.github.eslamasabry.oc.ui.design.effects.JoinBox
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
import io.github.eslamasabry.oc.ui.kit.foundation.kitDrawLayer
import io.github.eslamasabry.oc.ui.kit.foundation.kitRecord
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Live glass, first build (the Glass lab's A/B "Lens through motion", preview and dev builds only; shipped value 0 keeps
 * today's join in KitMenu.kt and KitLiquidJoin.kt): the bar's ⋯ menu as one body with the bar whose every value is a
 * function of ONE progress on ONE spring, so the body opens, closes and turns round anywhere with no step.
 *
 * What it fixes against today's join (reports: live-glass-study.md B.1):
 * - (a) one backdrop resolution for the whole joined life ([LiveJoinMotion.RESOLUTION]): today the body drops to half
 *   resolution in the frame its blur reaches 4 dp (the menu fully open) and sharpens again as soon as it closes.
 * - (b) a reversal continues from where it is: today three springs restart from rest, the rows' share changes its divisor
 *   with the direction (a step in the rows, the blur and the local hold) and a reopen waits the drop's whole turn
 *   (206 ms) before it fills again. Here the progress keeps its position and its velocity, and nothing waits.
 *
 * The motion is the approved one ("drop, rebound, fill", LiquidJoin): today's three curves are laid out on the progress
 * through the open's own time ([LiveJoinMotion.timeOf]), so an open from rest is today's open frame for frame. Closing
 * runs the same curves back (the width empties, then the column is drawn up); the drop's rebound follows the direction,
 * eased over [LiveJoinMotion.REBOUND_TAU_MS], so a close never bounces and a turn never steps. Lens strength (the drop's
 * thickness: the bar's bend growing to the menu's), blur and tint follow the fill, the rows follow the fill too.
 */

/** The live join's numbers and its pure maths: the progress spring and every value as a function of the progress. */
internal object LiveJoinMotion {
    /** The progress spring: critically damped, both ways (omega 10/s). */
    const val K: Float = 100f

    /** Opening aims this far past 1 and stops at 1, 500 ms after an open from rest; nothing creeps afterwards. */
    const val OPEN_PAST: Float = .042f

    /** Closing aims this far below 0 and stops at 0, about 325 ms after a close from rest (inside LiquidJoin.CLOSE_MS). */
    const val CLOSE_PAST: Float = .2f

    /**
     * The rows show over this share of the fill, both ways (today: the first 8 % opening, half closing): a close empties
     * fastest at its end, and a narrower share faded the rows out in one 60 Hz frame.
     */
    const val ROWS: Float = .8f

    /**
     * The drop's rebound follows the direction over this many ms (an exponential ease, one step per frame); a turn
     * where the rebound adds nothing switches it at once.
     */
    const val REBOUND_TAU_MS: Float = 50f

    /** The joined body's backdrop resolution, the whole time it is joined (1: full; never per frame). */
    const val RESOLUTION: Float = 1f

    private val omega: Float = sqrt(K)

    /** When today's drop first reaches its resting line, s (it passes it, turns, and settles after). */
    private val dropCross: Float = run {
        val s = sqrt(1f - LiquidJoin.DROP_ZETA * LiquidJoin.DROP_ZETA)
        (kotlin.math.PI.toFloat() - kotlin.math.atan(s / LiquidJoin.DROP_ZETA)) / (sqrt(LiquidJoin.DROP_K) * s)
    }

    /** When the drop's fill starts on today's open, s (the drop's turn plus LiquidJoin.FILL_AFTER_TURN_MS). */
    private val fillAt: Float = (LiquidJoin.turnMs(LiquidJoin.DROP_ZETA, LiquidJoin.DROP_K) + LiquidJoin.FILL_AFTER_TURN_MS) / 1000f

    /** An open from rest's progress at [t] s (before it is stopped at 1). */
    fun progressAt(t: Float): Float = (1f + OPEN_PAST) * (1f - (1f + omega * t) * exp(-omega * t))

    /** When an open from rest reaches 1, s. */
    val endS: Float = run {
        var lo = 0f
        var hi = 3f
        repeat(48) {
            val m = (lo + hi) / 2f
            if (progressAt(m) < 1f) lo = m else hi = m
        }
        (lo + hi) / 2f
    }

    private const val SAMPLES = 512
    private val table = FloatArray(SAMPLES + 1) { min(progressAt(endS * it / SAMPLES), 1f) }.also { it[SAMPLES] = 1f }

    /** The open's time at progress [p], s: where today's curves are read for it. */
    fun timeOf(p: Float): Float {
        if (p <= 0f) return 0f
        if (p >= 1f) return endS
        var lo = 0
        var hi = SAMPLES
        while (hi - lo > 1) {
            val m = (lo + hi) / 2
            if (table[m] <= p) lo = m else hi = m
        }
        val a = table[lo]
        val b = table[hi]
        val f = if (b > a) (p - a) / (b - a) else 0f
        return endS * (lo + f) / SAMPLES
    }

    /** A spring of [zeta] and [k] from 0 to 1, at [t] s (0 before 0). */
    fun response(zeta: Float, k: Float, t: Float): Float {
        if (t <= 0f) return 0f
        val w = sqrt(k)
        if (zeta >= 1f) return 1f - (1f + w * t) * exp(-w * t)
        val s = sqrt(1f - zeta * zeta)
        val wd = w * s
        return 1f - exp(-zeta * w * t) * (kotlin.math.cos(wd * t) + zeta / s * kotlin.math.sin(wd * t))
    }

    /** [x] at [t] with what is left of it at [endS] folded in by [r], so the end of the open is exactly at rest. */
    private fun rest(x: Float, end: Float, r: Float): Float = x + (1f - end) * r

    /** Every value of the body at progress [p], the rebound shown by [rebound] (0 to 1), into [out]. */
    fun values(p: Float, rebound: Float, out: LiveJoinFrame = LiveJoinFrame()): LiveJoinFrame {
        val t = timeOf(p.coerceIn(0f, 1f))
        val r = (t / endS).let { it * it * it * it }
        val fillT = t - fillAt
        val fillEnd = endS - fillAt
        val fr = if (fillT > 0f) (fillT / fillEnd).let { it * it * it * it } else 0f
        val drop = rest(response(LiquidJoin.DROP_ZETA, LiquidJoin.DROP_K, t), response(LiquidJoin.DROP_ZETA, LiquidJoin.DROP_K, endS), r)
        // The column without its rebound: today's drop up to its resting line, then the line (closing never bounces).
        val settled = if (t >= dropCross) 1f else min(drop, 1f)
        out.length = settled + (drop - settled) * rebound.coerceIn(0f, 1f)
        out.fill = rest(response(LiquidJoin.FILL_ZETA, LiquidJoin.FILL_K, fillT), response(LiquidJoin.FILL_ZETA, LiquidJoin.FILL_K, fillEnd), fr).coerceIn(0f, 1f)
        out.calm = rest(response(1f, LiquidJoin.DROP_K, t), response(1f, LiquidJoin.DROP_K, endS), r).coerceIn(0f, 1f)
        out.content = LiquidJoin.smoothstep(0f, ROWS, out.fill)
        return out
    }

    /**
     * One frame of [s]'s spring: [dt] s at [timeScale] (KitMotionState.debugTimeScale), exact for a critically damped
     * spring at any frame length. Stops at 1 opening and at 0 closing, with no velocity left.
     */
    fun step(s: LiveJoinSpring, dt: Float, timeScale: Float) {
        val scale = max(timeScale, .01f)
        val ease = 1f - exp(-dt * 1000f / (REBOUND_TAU_MS * scale))
        s.rebound += (s.direction - s.rebound) * ease
        if (!s.moving) return
        val w = omega / scale
        val target = if (s.direction > .5f) 1f + OPEN_PAST else -CLOSE_PAST
        val x0 = s.progress - target
        val v0 = s.velocity
        val e = exp(-w * dt)
        val x = (x0 + (v0 + w * x0) * dt) * e
        val v = (v0 - w * (v0 + w * x0) * dt) * e
        s.progress = target + x
        s.velocity = v
        if (s.direction > .5f && s.progress >= 1f) s.stopAt(1f)
        else if (s.direction <= .5f && s.progress <= 0f) s.stopAt(0f)
    }
}

/** The body's values at one progress (see [LiveJoinMotion.values]). */
internal class LiveJoinFrame {
    /** The drop's column, 0 to 1 (passing 1 once while it opens). */
    var length: Float = 0f

    /** The fill sideways, 0 to 1. */
    var fill: Float = 0f

    /** The reach's calm (1: at rest; below: swelled while the column pours). */
    var calm: Float = 0f

    /** How much of the rows shows, 0 to 1. */
    var content: Float = 0f
}

/** How little the rebound may add for a turn to switch it at once (a share of the column's length). */
private const val REBOUND_FREE: Float = .002f

/** The progress spring's state: plain numbers, stepped by [LiveJoinMotion.step]. */
internal open class LiveJoinSpring {
    open var progress: Float = 0f
    var velocity: Float = 0f

    /** 1 opening, 0 closing. */
    var direction: Float = 1f

    /** How much of the drop's rebound shows (follows [direction], eased). */
    open var rebound: Float = 1f
    var moving: Boolean = false

    /** Turns toward open ([leaving] false) or closed, keeping position and velocity. */
    fun aim(leaving: Boolean) {
        val dir = if (leaving) 0f else 1f
        // Where the rebound adds nothing now (anywhere but the drop's turn), it follows the direction at once.
        val p = progress
        if (kotlin.math.abs(LiveJoinMotion.values(p, 1f, probe).length - LiveJoinMotion.values(p, 0f, probe).length) < REBOUND_FREE) rebound = dir
        direction = dir
        moving = if (leaving) progress > 0f else progress < 1f
    }

    private val probe = LiveJoinFrame()

    fun stopAt(end: Float) {
        progress = end
        velocity = 0f
        rebound = direction
        moving = false
    }
}

/**
 * A live joined menu's motion: one [LiveJoinSpring] whose progress is snapshot state (the joined panes draw again as it
 * moves), and the body's values at it ([frame]). It is the drop's `content` too, so the joined pane knows it is drawing
 * a live body ([drawJoinedLive]) without a field of its own.
 */
@Stable
internal class KitLiquidJoinLive : LiveJoinSpring(), () -> Float {
    private var progressState by mutableFloatStateOf(0f)
    private var reboundState by mutableFloatStateOf(1f)
    override var progress: Float
        get() = progressState
        set(v) {
            progressState = v
        }

    override var rebound: Float
        get() = reboundState
        set(v) {
            reboundState = v
        }

    private var lastNanos: Long = NO_FRAME
    private val cached = LiveJoinFrame()
    private var cachedAt = Float.NaN
    private var cachedRebound = Float.NaN

    /** The body's values now (snapshot reads: a pane reading them draws again as they move). */
    fun frame(): LiveJoinFrame {
        val p = progress
        val r = rebound
        if (p != cachedAt || r != cachedRebound) {
            LiveJoinMotion.values(p, r, cached)
            cachedAt = p
            cachedRebound = r
        }
        return cached
    }

    /** The rows' share now (the drop's `content`). */
    override fun invoke(): Float = frame().content

    /**
     * Runs the spring toward open or closed until it rests, one step per frame. A run cancelled by a turn hands its last
     * frame's time to the next, so the turn neither waits a frame nor loses its velocity. Nothing runs at rest.
     */
    suspend fun run(leaving: Boolean, timeScale: Float) {
        aim(leaving)
        while (moving) {
            withFrameNanos { now ->
                val last = lastNanos
                val dt = if (last == NO_FRAME || now - last > MAX_GAP_NANOS || now < last) 0f else (now - last) / 1e9f
                lastNanos = now
                LiveJoinMotion.step(this, dt, timeScale)
            }
        }
        lastNanos = NO_FRAME
    }

    private companion object {
        const val NO_FRAME: Long = Long.MIN_VALUE
        const val MAX_GAP_NANOS: Long = 100_000_000L
    }
}

/** True where the Glass lab's "Lens through motion" is on (preview and dev builds: the lab's tuning; null elsewhere). */
@Composable
internal fun kitLiquidJoinLiveOn(): Boolean =
    (LocalKitGlassTuning.current?.knobs(GlassPaneKind.Menu, LocalKitColors.current.dark)?.liveMotion ?: 0f) >= .5f

/**
 * The live joined menu's motion (KitMenu.kt's one branch; today's is `KitMenuJoinMotion` there): the same drop, rebound
 * and fill, from one progress, written to [drop] for the draw phase. [at] is the control it opened from (window px).
 */
@Composable
internal fun KitMenuJoinMotionLive(drop: KitLiquidJoinDrop, home: KitLiquidJoinBody, at: IntRect, leaving: Boolean) {
    val density = LocalDensity.current
    val timeScale = KitMotion.state.debugTimeScale
    val live = remember { KitLiquidJoinLive() }
    val j = LiquidJoin
    val columnPx = with(density) { j.COLUMN_DP.dp.toPx() }
    // The Glass lab's join curve (its shipped number is REACH_MENU_DP), as today's.
    val tunedReach = LocalKitGlassTuning.current?.knobs(GlassPaneKind.Menu, LocalKitColors.current.dark)?.joinReachDp
    val reachPx = with(density) { (tunedReach ?: j.REACH_MENU_DP).dp.toPx() }
    drop.reachesCentre = true
    drop.content = live
    drop.reach = { reachPx * (1f + j.MENU_REACH_SWELL * (1f - live.frame().calm)) }
    drop.shape = { rest ->
        val f = live.frame()
        val (l, r) = j.menuColumn(home.rest.liveBox(), at.left.toFloat(), at.right.toFloat(), columnPx)
        val box = j.dropBox(rest.liveBox(), l.coerceIn(rest.left, rest.right - 1f), r.coerceIn(rest.left + 1f, rest.right), f.length, f.fill)
        Rect(box.left, box.top, box.right, box.bottom)
    }
    LaunchedEffect(leaving) { live.run(leaving, timeScale) }
}

private fun Rect.liveBox(): JoinBox = JoinBox.of(left, top, right, bottom, 0f)

/**
 * One side of a live joined body (KitLiquidJoin.kt's one branch; today's is `JoinedPaneNode.drawJoined` there), drawn
 * with the same passes as today's, from the drop's one progress: the body's blur, the drop's bend depth (its thickness)
 * and its tint follow the fill, the local hold's reach follows the rows, and the backdrop is drawn at one resolution for
 * the body's whole joined life. The knee and every other value are today's.
 */
internal fun ContentDrawScope.drawJoinedLive(
    live: KitLiquidJoinLive,
    c: LayoutCoordinates,
    back: GraphicsLayer,
    mask: GraphicsLayer,
    programs: LiquidJoinPrograms,
    platform: KitPlatform,
    part: KitLiquidJoinPart,
    partner: KitLiquidJoinPart?,
    spec: KitLiquidPaneSpec,
    theirSpec: KitLiquidPaneSpec,
    geometry: JoinGeometry,
    reach: Float,
    mine: Rect,
    theirs: Rect,
    source: KitLiquidBackdrop,
    area: Rect,
    local: Offset,
    full: IntSize,
    dark: Boolean,
) {
    val f = live.frame()
    val iAmDrop = part is KitLiquidJoinDrop
    val bodySpec = if (iAmDrop) theirSpec else spec
    val dropSpec = if (iAmDrop) spec else theirSpec
    val frostsBody = dropSpec.recipe.hold.knee
    val recipe = if (frostsBody) joinedFrost(bodySpec.recipe, dropSpec.recipe, f.fill) else bodySpec.recipe
    val res = LiveJoinMotion.RESOLUTION
    // The drop's thickness: the bar's bend while it pours, the menu's once it has filled.
    val dropBend = (bodySpec.recipe.refractionDp + (dropSpec.recipe.refractionDp - bodySpec.recipe.refractionDp) * f.fill).dp.toPx()
    val bodyBend = bodySpec.recipe.refractionDp.dp.toPx()
    // The drop's tint: the bar's while it pours, its own once it has filled (the band still eases it at the meeting line).
    val dropTint = lerp(tintNow(bodySpec), tintNow(dropSpec), f.fill)
    val bodyTint = tintNow(bodySpec)
    val u =
        JoinUniforms(
            geometry = geometry.relativeTo(area.topLeft),
            reach = reach,
            band = LiquidJoin.BAND_DP.dp.toPx(),
            rimMine = LiquidEdge.rimPx(min(mine.width, mine.height), density, spec.recipe),
            rimTheirs = LiquidEdge.rimPx(min(theirs.width, theirs.height), density, theirSpec.recipe),
            refractionMine = if (iAmDrop) dropBend else bodyBend,
            refractionTheirs = if (iAmDrop) bodyBend else dropBend,
            saturation = recipe.saturation,
            contrast = recipe.contrast,
            brightness = recipe.brightness,
            knee = kneeOf(spec),
            hold = if (frostsBody) dropSpec.recipe.hold.chroma else 0f,
            local = (if (frostsBody) dropSpec.recipe.hold else recipe.hold).let { localOf(it, it.localDp.dp.toPx() * (if (frostsBody) f.content else 1f)) },
            tintMine = if (iAmDrop) dropTint else bodyTint,
            tintTheirs = if (iAmDrop) bodyTint else dropTint,
            rimPx = spec.rimDp.dp.toPx(),
            falloff = spec.rimFalloff,
            rimFloor = spec.rimFloor,
            lightDir = spec.lightDir,
            bendShape = spec.bendShape,
        )
    val small = IntSize(kotlin.math.ceil(full.width * res).toInt().coerceAtLeast(1), kotlin.math.ceil(full.height * res).toInt().coerceAtLeast(1))
    // 1. The backdrop at the body's one resolution, blurred by the body's frost, bent and pressed by the lens pass.
    u.set(programs.lens, mode = 0f, res = res)
    back.renderEffect = platform.liquidJoinEffect(programs.lens, recipe.blurDp.dp.toPx() * res)
    kitRecord(back, small) { scale(res, res, Offset.Zero) { translate(-local.x, -local.y) { source.draw(this, c) } } }
    // 2. The tint eased over the band, then this side's cover.
    kitRecord(mask, full) {
        scale(1f / res, 1f / res, Offset.Zero) { kitDrawLayer(back) }
        u.set(programs.brush, mode = 3f, res = 1f)
        drawRect(programs.brush.brush)
        u.set(programs.brush, mode = 1f, res = 1f)
        drawRect(programs.brush.brush, blendMode = BlendMode.DstIn)
    }
    translate(local.x, local.y) {
        kitDrawLayer(mask)
        // 3. One rim light along the body's outline on this side.
        u.set(programs.brush, mode = 2f, res = 1f)
        drawRect(
            programs.brush.brush,
            size = full.toSize(),
            alpha = spec.rim.alpha,
            colorFilter = ColorFilter.tint(spec.rim.copy(alpha = 1f)),
            blendMode = if (spec.rimAdds) BlendMode.Plus else BlendMode.SrcOver,
        )
        // 4. The finger's light, from this pane or its partner, on this side of the body.
        for (lit in arrayOf(part, partner)) {
            if (lit == null) continue
            val a = lit.touchLight().coerceIn(0f, 1f)
            if (a <= 0f) continue
            val at = lit.touchAt()
            if (at.isSpecified) {
                val centre = at - area.topLeft
                val strength = (if (dark) LiquidTouchRules.BLOOM_DARK else LiquidTouchRules.BLOOM_LIGHT) * a
                u.set(programs.brush, mode = 4f, res = 1f, light = floatArrayOf(centre.x, centre.y, LiquidTouchRules.BLOOM_DP.dp.toPx(), strength))
                drawRect(programs.brush.brush, size = full.toSize(), blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver)
            }
            val edge = (if (dark) LiquidTouchRules.EDGE_DARK else LiquidTouchRules.EDGE_LIGHT) * a
            u.set(programs.brush, mode = 5f, res = 1f, light = floatArrayOf(0f, 0f, 1.dp.toPx(), edge))
            drawRect(
                programs.brush.brush,
                size = full.toSize(),
                colorFilter = if (dark) null else ColorFilter.tint(Color.Black),
                blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
            )
        }
    }
}
