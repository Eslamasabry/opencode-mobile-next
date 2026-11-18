package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.JoinBox
import io.github.eslamasabry.oc.ui.design.effects.JoinOutline
import io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.kit.foundation.allShadows
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/*
 * A menu that grows out of its anchor part, in every style (owner, B-446: "the nice fluidity animation from glass ...
 * how it evolves from the bar itself ... in all style"). Glass already draws a menu from the bar's capsule as one body
 * of liquid glass (KitLiquidJoin.kt, its shader). This is the same body without the glass: the same field
 * ([LiquidJoin]: the capsule's box and the menu's box joined by a cubic smooth minimum, the menu reaching up to the
 * capsule's centre line so its outer flank runs straight down from the capsule's end cap), traced as one outline
 * ([JoinOutline]) and painted in the style's own material: the capsule's own fill and edge, one path, no blur.
 *
 * The motion is the one Glass has ([MenuGrowth], "Drop, rebound, fill"): a column as wide as a control falls out of
 * the capsule under the control, passes its resting line once and comes back, then fills sideways to the menu's box;
 * the rows show once the body has formed; closing empties the width, then draws the column up; a predictive back
 * gesture scrubs the same way back.
 *
 * Who draws what: each part draws its own side of the one outline from its own node, as the glass panes do. The body
 * (the capsule, on the page) draws the part of the outline that lies within its own box's rows; the drop (the menu, in
 * the window's menu layer above the page) draws the rest. The split is one whole-pixel row on the capsule's edge, so the
 * two halves meet with no overlap and no gap: one fill, one edge, no seam.
 *
 * The fillet's radius is the menu's corner radius ([REACH_PER_RADIUS]), so a style with square corners (Brutal) joins
 * in a square step, and a round one in a round shoulder.
 */

/** The parts of one screen a menu may grow out of (the chat's header capsule "bar") and the menus growing now. */
@Stable
internal class KitGrowHost {
    internal val bodies = mutableStateMapOf<String, KitGrowBody>()
    internal val drops = mutableStateListOf<KitGrowDrop>()

    /** The body under [at] (window px), if any. */
    fun bodyAt(at: Offset): KitGrowBody? = bodies.values.firstOrNull { it.rest.contains(at) }

    /** The menu growing out of [body] now (snapshot read: a composition or a draw reading it runs again as it changes). */
    fun dropOf(body: KitGrowBody): KitGrowDrop? = drops.firstOrNull { it.body === body }
}

/** The screen's parts a menu grows out of (KitScreen, where its chrome floats); null: menus open as before. */
internal val LocalKitGrowHost: ProvidableCompositionLocal<KitGrowHost?> = staticCompositionLocalOf { null }

/** The material of a grown body: the body's own decoration; [insideEdge]: its edge lies inside the outline (Solid's hairline). */
@Immutable
internal data class KitGrowFace(val decoration: SurfaceDecoration, val insideEdge: Boolean)

/** A part a menu grows out of: its box in the window, its corner, and the material the joined body is painted in. */
@Stable
public class KitGrowBody internal constructor(internal val name: String) {
    internal var rest: Rect by mutableStateOf(Rect.Zero)
    internal var radius: Float by mutableFloatStateOf(0f)
    internal var face: KitGrowFace? by mutableStateOf(null)
    internal val paths: GrowPaths = GrowPaths()
}

/** A menu growing out of [body]: its box at rest (window px), its corner, and its body now (draw phase). */
@Stable
public class KitGrowDrop internal constructor(internal val body: KitGrowBody) {
    internal var rest: Rect by mutableStateOf(Rect.Zero)
    internal var radius: Float by mutableFloatStateOf(0f)

    /** Its body now from its box at rest (the growth's springs); null: its box at rest. Read in the draw phase. */
    internal var shape: ((Rect) -> Rect)? = null

    /** The join's reach now, px. Read in the draw phase. */
    internal var reach: () -> Float = { 0f }

    /** How much of its rows shows now, 0 to 1. Read in the draw phase. */
    internal var content: () -> Float = { 1f }

    internal fun glass(): Rect {
        val r = rest
        if (r.width <= 0f || r.height <= 0f) return r
        return shape?.invoke(r) ?: r
    }
}

/** The body named [name] of this screen while this part is composed and [on]; null where the screen has none. */
@Composable
internal fun rememberKitGrowBody(name: String, on: Boolean = true): KitGrowBody? {
    val host = LocalKitGrowHost.current
    if (host == null || !on) return null
    val body = remember(host, name) { KitGrowBody(name) }
    DisposableEffect(host, name, body) {
        host.bodies[name] = body
        onDispose { if (host.bodies[name] === body) host.bodies.remove(name) }
    }
    return body
}

/** A menu growing out of [body] while this part is composed; null without a body. */
@Composable
internal fun rememberKitGrowDrop(body: KitGrowBody?): KitGrowDrop? {
    val host = LocalKitGrowHost.current
    if (host == null || body == null) return null
    val drop = remember(body) { KitGrowDrop(body) }
    DisposableEffect(host, drop) {
        host.drops += drop
        onDispose { host.drops -= drop }
    }
    return drop
}

/** A [shape]'s corner radius at [size], px (a corner-based shape's top-start corner; 0 for any other shape). */
internal fun cornerOf(shape: Shape, size: Size, density: Density): Float =
    ((shape as? CornerBasedShape)?.topStart?.toPx(size, density) ?: 0f).coerceIn(0f, size.minDimension / 2f)

/** Where [body] is: this node's box in the window and [shape]'s corner there. Put it on the node that wears the face. */
@Composable
internal fun Modifier.kitGrowBodyRest(body: KitGrowBody?, shape: Shape): Modifier {
    if (body == null) return this
    val density = LocalDensity.current
    return this.onGloballyPositioned {
        val size = it.size.toSize()
        body.rest = Rect(it.positionInWindow(), size)
        body.radius = cornerOf(shape, size, density)
    }
}

/** Where [drop] is: this node's box in the window and [shape]'s corner there. Put it on the node that wears the face. */
@Composable
internal fun Modifier.kitGrowDropRest(drop: KitGrowDrop?, shape: Shape): Modifier {
    if (drop == null) return this
    val density = LocalDensity.current
    return this.onGloballyPositioned {
        val size = it.size.toSize()
        drop.rest = Rect(it.positionInWindow(), size)
        drop.radius = cornerOf(shape, size, density)
    }
}

/** How far a join reaches per px of the menu's corner radius: [LiquidJoin.REACH_MENU_DP] was solved for a 24 dp fillet. */
internal const val REACH_PER_RADIUS: Float = LiquidJoin.REACH_MENU_DP / 24f

/** A reach below this, px, joins in a square step (the two boxes' union) instead of a fillet. */
private const val SQUARE_REACH_PX = 1f

/** About one point of a traced outline per this many px of the two boxes' bounding perimeter. */
private const val TRACE_STEP_PX = 4f
private const val TRACE_MAX_POINTS = 768

/** Far enough to stand for "no edge" in a clip, px. */
private const val FAR = 1e6f

/** The last traced outline of a body and its drop (plain fields: written and read in the draw phase; both sides ask for the same). */
internal class GrowPaths {
    private var key: FloatArray? = null
    private var last: Path? = null

    fun path(a: JoinBox, b: JoinBox, reach: Float): Path {
        val k = floatArrayOf(a.cx, a.cy, a.hx, a.hy, a.r, b.cx, b.cy, b.hx, b.hy, b.r, reach)
        val known = last
        if (known != null && key?.contentEquals(k) == true) return known
        val path =
            if (reach < SQUARE_REACH_PX) {
                Path().apply { op(a.roundPath(), b.roundPath(), PathOperation.Union) }
            } else {
                val w = max(a.right, b.right) - min(a.left, b.left)
                val h = max(a.bottom, b.bottom) - min(a.top, b.top)
                val count = ceil(2f * (w + h) / TRACE_STEP_PX).toInt().coerceIn(JoinOutline.MIN_POINTS, TRACE_MAX_POINTS)
                val points = JoinOutline.trace(a, b, reach, 0f, count)
                Path().apply {
                    moveTo(points[0], points[1])
                    var i = 2
                    while (i < points.size) {
                        lineTo(points[i], points[i + 1])
                        i += 2
                    }
                    close()
                }
            }
        key = k
        last = path
        return path
    }

    private fun JoinBox.roundPath(): Path {
        val r = min(r, min(hx, hy)).coerceAtLeast(0f)
        return Path().apply { addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(r, r))) }
    }
}

private fun Rect.joinBox(radius: Float = 0f): JoinBox = JoinBox.of(left, top, right, bottom, radius)

/**
 * The one outline of [body] and [drop] now, window px; null while the drop has not left the body's box (the body's own
 * outline is then the whole body). The drop reaches the body's centre line (shape rule 3), so a flank on the body's end
 * runs straight down into the menu.
 */
internal fun growPath(body: KitGrowBody, drop: KitGrowDrop): Path? {
    val me = body.rest
    val them = drop.glass()
    if (me.width <= 0f || me.height <= 0f || them.width <= 0f || them.height <= 0f) return null
    if (them.top >= me.top - .5f && them.bottom <= me.bottom + .5f) return null
    val a = me.joinBox(body.radius)
    val b = LiquidJoin.fieldBox(them.joinBox(drop.radius), a)
    return body.paths.path(a, b, drop.reach())
}

/** The row, window px, where the body's half of the outline ends and the drop's begins: the body's edge facing the drop. */
private fun splitOf(body: KitGrowBody, drop: KitGrowDrop): Float {
    val below = drop.rest.center.y >= body.rest.center.y
    return kotlin.math.round(if (below) body.rest.bottom else body.rest.top)
}

private fun Argb.color(): Color = Color(v)

/**
 * Paints [face] on [path]: its crisp shadows (offset blocks, rules), its fill, its edge. Blurred shadows are not drawn
 * here: the body keeps its own on its own box, and the menu's half casts none (as a menu never did in these styles).
 */
internal fun DrawScope.drawGrowFace(path: Path, face: KitGrowFace) {
    val d = face.decoration
    val ltr = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val bounds = path.getBounds()
    d.allShadows().filter { it.blurDp <= 0f }.forEach { s ->
        translate(s.dxDp.dp.toPx() * ltr, s.dyDp.dp.toPx()) {
            val spread = s.spreadDp.dp.toPx()
            drawPath(path, s.color.color())
            if (spread > 0f) drawPath(path, s.color.color(), style = Stroke(2f * spread))
        }
    }
    d.hardShadow?.let { block ->
        if (d.hardOffsetDp > 0f) {
            val o = d.hardOffsetDp.dp.toPx()
            translate(o * ltr, o) { drawPath(path, block.color()) }
        }
    }
    d.fill?.let { fill ->
        val start = d.fillStart
        if (start != null && start != fill) drawPath(path, Brush.linearGradient(listOf(start.color(), fill.color()), bounds.topLeft, bounds.bottomRight))
        else drawPath(path, fill.color())
    }
    if (d.edgeWidthDp > 0f) {
        val start = d.edgeStart ?: d.edgeEnd
        val end = d.edgeEnd ?: d.edgeStart
        if (start != null && end != null) {
            val w = d.edgeWidthDp.dp.toPx()
            val rest = d.edgeRest
            val brush =
                if (start == end && rest == null) SolidColor(start.color())
                else Brush.linearGradient(listOfNotNull(start.color(), rest?.color(), end.color()), bounds.topLeft, bounds.bottomRight)
            if (face.insideEdge) clipPath(path) { drawPath(path, brush, style = Stroke(2f * w)) }
            else drawPath(path, brush, style = Stroke(w))
        }
    }
}

/**
 * The body's face while a menu grows out of it: its half of the one outline (the rows of its own box), or its own
 * outline while the menu is still inside it. Put it where the body's own face was, after [kitGrowBodyRest].
 */
internal fun Modifier.kitGrowBodyFace(host: KitGrowHost, body: KitGrowBody, own: Shape): Modifier =
    this.drawBehind {
        val face = body.face ?: return@drawBehind
        val drop = host.dropOf(body)
        val path = drop?.let { growPath(body, it) }
        if (drop == null || path == null) {
            drawGrowFace(Path().apply { addOutline(own.createOutline(size, layoutDirection, this@drawBehind)) }, face)
            return@drawBehind
        }
        val origin = body.rest.topLeft
        val split = splitOf(body, drop)
        val below = drop.rest.center.y >= body.rest.center.y
        translate(-origin.x, -origin.y) {
            clipRect(-FAR, if (below) -FAR else split, FAR, if (below) split else FAR) { drawGrowFace(path, face) }
        }
    }

/** The menu's face while it grows: its half of the one outline (everything past the body's edge). Nothing before it leaves the body. */
internal fun Modifier.kitGrowDropFace(drop: KitGrowDrop): Modifier =
    this.drawBehind {
        val body = drop.body
        val face = body.face ?: return@drawBehind
        val path = growPath(body, drop) ?: return@drawBehind
        val origin = drop.rest.topLeft
        val split = splitOf(body, drop)
        val below = drop.rest.center.y >= body.rest.center.y
        translate(-origin.x, -origin.y) {
            clipRect(-FAR, if (below) split else -FAR, FAR, if (below) FAR else split) { drawGrowFace(path, face) }
        }
    }

/** A growing menu's rows: clipped to its body now, shown as much as [KitGrowDrop.content] says. Nothing shows before it is placed. */
internal fun Modifier.kitGrowClip(drop: KitGrowDrop): Modifier =
    this.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = drop.content().coerceIn(0f, 1f)
        }
        .drawWithContent {
            val rest = drop.rest
            if (rest.width <= 0f || rest.height <= 0f) return@drawWithContent
            val g = drop.glass()
            val l = g.left - rest.left
            val t = g.top - rest.top
            val r = g.right - rest.left
            val b = g.bottom - rest.top
            if (r <= l || b <= t) return@drawWithContent
            clipRect(l, t, r, b) { this@drawWithContent.drawContent() }
        }

/*
 * The motion, shared by Glass's joined menu and every other style's grown menu ("Drop, rebound, fill", [LiquidJoin]'s
 * springs): [length] the column's fall (passes 1 once), [fill] its widening, [calm] the reach's swell easing to rest,
 * [scrub] a predictive back gesture's share of the way back.
 */

/** How much of the column's length a full back gesture takes back (the width empties first, as when closing). */
private const val SCRUB_LENGTH = .35f

/** How long a back gesture that ended waits to see whether it closed the menu before the menu springs back, ms. */
private const val BACK_SETTLE_MS = 48L

/** A grown menu's rows show from this share of its fill (the body has formed), fully at its end. */
internal const val GROW_ROWS_FROM: Float = .55f

@Stable
internal class MenuGrowth {
    val length: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val fill: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val calm: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val scrub: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var leaving: Boolean = false

    fun lengthNow(): Float = length.value * (1f - SCRUB_LENGTH * scrub.value)

    fun fillNow(): Float = fill.value * (1f - scrub.value)

    /** The reach's share now: up to [LiquidJoin.MENU_REACH_SWELL] more while the column falls, 1 at rest. */
    fun swell(): Float = 1f + LiquidJoin.MENU_REACH_SWELL * (1f - calm.value.coerceIn(0f, 1f))

    /** A grown menu's rows: none until the body has formed ([GROW_ROWS_FROM] of the fill), all at its end. */
    fun rows(): Float = ((fillNow() - GROW_ROWS_FROM) / (1f - GROW_ROWS_FROM)).coerceIn(0f, 1f)

    /** The menu's body now from its box at [rest], opened from the control at [at] on the body at [body] (window px). */
    fun glass(rest: Rect, body: Rect, at: IntRect, columnPx: Float): Rect {
        val (l, r) = LiquidJoin.menuColumn(body.joinBox(), at.left.toFloat(), at.right.toFloat(), columnPx)
        val box = LiquidJoin.dropBox(rest.joinBox(), l.coerceIn(rest.left, rest.right - 1f), r.coerceIn(rest.left + 1f, rest.right), lengthNow(), fillNow())
        return Rect(box.left, box.top, box.right, box.bottom)
    }
}

/**
 * The growth's springs: open while not [leaving] (drop, rebound, then fill), closing once it is (the width empties, the
 * column is drawn up). [back] is the predictive back gesture's progress: it scrubs the menu back toward its column and
 * springs it open again if the gesture is cancelled; a completed gesture closes from where it was.
 */
@Composable
internal fun rememberMenuGrowth(leaving: Boolean, back: KitBackProgress): MenuGrowth {
    val specs = KitMotion.specs
    val g = remember { MenuGrowth() }
    val j = LiquidJoin
    LaunchedEffect(leaving) {
        g.leaving = leaving
        if (!leaving) {
            launch { g.length.animateTo(1f, specs.liquid(j.DROP_ZETA, j.DROP_K)) }
            launch { g.calm.animateTo(1f, specs.liquid(1f, j.DROP_K)) }
            launch {
                kotlinx.coroutines.delay(specs.delayMs(j.turnMs(j.DROP_ZETA, j.DROP_K) + j.FILL_AFTER_TURN_MS))
                g.fill.animateTo(1f, specs.liquid(j.FILL_ZETA, j.FILL_K))
            }
        } else {
            // A back gesture that closed the menu: it closes from where the gesture left it.
            if (g.scrub.value > 0f) {
                val f = g.fillNow()
                val l = g.lengthNow()
                g.scrub.snapTo(0f)
                g.fill.snapTo(f)
                g.length.snapTo(l)
            }
            launch { g.fill.animateTo(0f, specs.liquid(1f, j.EMPTY_K)) }
            launch {
                kotlinx.coroutines.delay(specs.delayMs(j.DRAW_UP_AFTER_MS.toFloat()))
                launch { g.calm.animateTo(0f, specs.liquid(1f, j.DRAW_UP_K)) }
                g.length.animateTo(0f, specs.liquid(1f, j.DRAW_UP_K))
            }
        }
    }
    LaunchedEffect(g, back) {
        snapshotFlow { back.value() }.collectLatest { p ->
            if (g.leaving) return@collectLatest
            if (p > 0f) {
                g.scrub.snapTo(p.coerceIn(0f, 1f))
            } else if (g.scrub.value > 0f) {
                kotlinx.coroutines.delay(BACK_SETTLE_MS)
                if (!g.leaving) g.scrub.animateTo(0f, specs.liquid(1f, j.FILL_K))
            }
        }
    }
    return g
}
