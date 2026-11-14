package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostConditions
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.effects.LiquidBarShape
import io.github.eslamasabry.oc.ui.kit.effects.RIM_ALPHA_DARK
import io.github.eslamasabry.oc.ui.kit.effects.RIM_ALPHA_LIGHT
import io.github.eslamasabry.oc.ui.kit.effects.RIM_LIGHT_DP
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostRules
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/*
 * The top edge of a page whose body runs edge to edge under its chrome (owner's pick 2026-11-09, chat edge option A:
 * "the text scrolls under the status bar and blurs through the glass header, so the screen reads as one surface";
 * mockups in Storage2/mockups/2026-11-09-chat-edge). The body draws from the top of the window; at rest its content
 * starts below the bar (the body's top padding is the status bar plus the bar), so nothing is hidden.
 *
 * - Glass, liquid: the bar's capsule bends and blurs what scrolls under it; under the status bar the content dissolves
 *   into a soft blur of itself with a dim of the page's ground that ends just below the status bar, so the clock reads
 *   over text without a plate ([KitStatusEdge]).
 * - Glass, frosted: the same, the capsule frosted instead of bent ([kitFrostedBarFace]).
 * - Solid, Soft, Brutal, Paper (and Glass wherever the platform or a hold keeps it from blurring): nothing is painted
 *   behind the chrome (owner 2026-11-14: "why there are solids above the composer and on the header": a band of the
 *   page's ground there read as a slab laid on the page and cut the live background off below it). The page's
 *   background (its ground, its live picture) runs unbroken from the top of the window, the bar's words sit on it, and
 *   the body's own content is gone above the bar's bottom edge and fades in below it ([kitScrollEdges] in the frame),
 *   that fade growing with what lies under the chrome ([kitUnderTop]), so a page at rest looks as it did.
 *
 * Every blur here draws the body's own recording (the screen's frost source), never a new one.
 */

/** How the top chrome of an edge-to-edge page meets what scrolls under it. */
internal enum class KitTopEdgeLook {
    /** Glass with liquid edges: the bar's capsule bends the content; the status bar blurs and dims it. */
    Liquid,

    /** Glass with frosted edges: the bar's capsule frosts the content; the status bar blurs and dims it. */
    Frosted,

    /** Every other style (and Glass without a blur): nothing behind the chrome; the content is gone before the bar. */
    Clear,
}

/**
 * Whether a screen's body has content under its top chrome right now. The kit's scrollers report it
 * ([KitReportScrolledUnder]); with no report the frame assumes there is (the content keeps its fade below the bar:
 * nothing is ever read under bare chrome).
 */
@Stable
public class KitScreenScroll internal constructor() {
    internal var reporter: (() -> Boolean)? by mutableStateOf(null)

    /** Whether more of the body lies below the frame's bottom edge (the page's scroller reports it); null: unknown. */
    internal var moreReporter: (() -> Boolean)? by mutableStateOf(null)

    /**
     * How much of the body's own bottom is a pinned foot (KitPinnedFootBody's "4 of 4 / Finish" bar, a footer line) and
     * the gap above it, px: the frame's fade ends above it and never touches it.
     */
    internal var footRoom: Int by mutableIntStateOf(0)

    /**
     * A page frame that watches its body's scrolling (the stacked layout): for a body whose scroller reports nothing,
     * whether the person has scrolled its content up under the chrome ([KitScreenEdgeWatch]). Only changes on a
     * crossing, so it never recomposes per frame.
     */
    internal var guessing: Boolean = false
    internal var guessed: Boolean by mutableStateOf(false)

    internal val under: Boolean
        get() = reporter?.invoke() ?: if (guessing) guessed else true

    internal val more: Boolean
        get() = moreReporter?.invoke() ?: false
}

internal val LocalKitScreenScroll = staticCompositionLocalOf<KitScreenScroll?> { null }

/**
 * The page's scroller tells its frame where its content lies (audit 2, 2026-11-14: on most pages a hard clip cut the last
 * row's letters above the navigation bar and headings were sliced at the bar's lower edge): content scrolled up under
 * the top chrome ([ScrollableState.canScrollBackward]) dissolves below it, content running on past the bottom edge
 * ([ScrollableState.canScrollForward]) dissolves above it, and at rest neither edge touches a line. The first scroller in
 * a page reports; a nested one inside it (a code block, a card's own list) does not take over. Outside a page frame, or
 * inside a sheet, nothing.
 */
@Composable
public fun KitReportPageScroll(state: androidx.compose.foundation.gestures.ScrollableState) {
    val scroll = LocalKitScreenScroll.current ?: return
    DisposableEffect(scroll, state) {
        val under: () -> Boolean = { state.canScrollBackward }
        val more: () -> Boolean = { state.canScrollForward }
        // First come keeps it: an outer page scroller composes (and reports) before anything scrolling inside it.
        val took = scroll.reporter == null && scroll.moreReporter == null
        if (took) {
            scroll.reporter = under
            scroll.moreReporter = more
        }
        onDispose {
            if (took && scroll.reporter === under) scroll.reporter = null
            if (took && scroll.moreReporter === more) scroll.moreReporter = null
        }
    }
}

/** A page's [androidx.compose.foundation.ScrollState] that reports to its frame ([KitReportPageScroll]). */
@Composable
public fun kitPageScrollState(initial: Int = 0): androidx.compose.foundation.ScrollState {
    val state = androidx.compose.foundation.rememberScrollState(initial)
    KitReportPageScroll(state)
    return state
}

/** A page's [androidx.compose.foundation.lazy.LazyListState] that reports to its frame ([KitReportPageScroll]). */
@Composable
public fun kitPageListState(): androidx.compose.foundation.lazy.LazyListState {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    KitReportPageScroll(state)
    return state
}

/**
 * Watches a body that reports nothing ([KitScreenScroll.guessing]): a scroll that moves its content up puts content
 * under the chrome; a pull that the content cannot follow (it is at its top) or a way back of the same length takes it
 * out. A scroll made by the app (a restored place, a jump) is not seen: the page then keeps a clean edge, as before.
 */
internal class KitScreenEdgeWatch(private val scroll: KitScreenScroll) : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    private var moved = 0f

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
    ): Offset {
        moved = (moved - consumed.y).coerceAtLeast(0f)
        if (available.y > 0f) moved = 0f
        val under = moved > 0.5f
        if (scroll.guessed != under) scroll.guessed = under
        return Offset.Zero
    }
}

/**
 * Tells the screen frame whether this body's content lies under the top chrome ([under]: read as state, e.g. a lazy
 * list's `canScrollBackward`, or `canScrollForward` for a reversed one). The frame shows its scrolled-under look (the
 * content's fade below the bar, the status edge's dim) only while it does. Outside an edge-to-edge frame it does nothing.
 */
@Composable
public fun KitReportScrolledUnder(under: () -> Boolean) {
    val scroll = LocalKitScreenScroll.current ?: return
    val latest = rememberUpdatedState(under)
    DisposableEffect(scroll) {
        val report: () -> Boolean = { latest.value() }
        scroll.reporter = report
        onDispose { if (scroll.reporter === report) scroll.reporter = null }
    }
}

/**
 * How strongly the top chrome's scrolled-under look shows (0–1, read while drawing): it eases in and out over frost's
 * [KitFrostRules.FADE_MS] as content comes under the chrome and leaves, at once under reduced motion. The first report
 * lands at once, so a page that opens with nothing under its chrome never flashes a fade.
 */
@Composable
internal fun kitUnderTop(scroll: KitScreenScroll): () -> Float {
    val target = if (scroll.under) 1f else 0f
    val reported = scroll.reporter != null || scroll.guessing
    val anim = remember { Animatable(target) }
    val settled = remember { mutableStateOf(false) }
    val reduced = KitMotion.state.reduced
    LaunchedEffect(target, reported, reduced) {
        if (!reported) return@LaunchedEffect
        if (!settled.value || reduced) anim.snapTo(target) else anim.animateTo(target, tween(KitFrostRules.FADE_MS))
        settled.value = true
    }
    return remember(scroll, anim) { { if (settled.value) anim.value else if (scroll.under) 1f else 0f } }
}

/**
 * How strongly the bottom edge's dissolve shows (0–1, read while drawing): in while more of the body lies below the
 * frame's bottom edge, out at its end, easing over frost's [KitFrostRules.FADE_MS] (at once under reduced motion; the
 * first reading lands at once).
 */
@Composable
internal fun kitMoreBelow(scroll: KitScreenScroll): () -> Float {
    val target = if (scroll.more) 1f else 0f
    val anim = remember { Animatable(target) }
    val settled = remember { mutableStateOf(false) }
    val reduced = KitMotion.state.reduced
    LaunchedEffect(target, reduced) {
        if (!settled.value || reduced) anim.snapTo(target) else anim.animateTo(target, tween(KitFrostRules.FADE_MS))
        settled.value = true
    }
    return remember(scroll, anim) { { if (settled.value) anim.value else if (scroll.more) 1f else 0f } }
}

/** How far below the status bar the status edge's blur and dim reach, in dp (the mockup's 24 px under a 34 px bar). */
internal const val STATUS_EDGE_REACH_DP: Int = 24

/** The status edge's blur, sigma in dp (the mockup's strongest layer, 9 px). */
internal const val STATUS_EDGE_BLUR_DP: Float = 8f

/** The share of the status bar, from the top, where the content is all blur (the dissolve starts below it). */
internal const val STATUS_EDGE_SOLID_SHARE: Float = .5f

/** The dim under the status bar: the page's ground at these alphas, at these shares of the edge (the mockup's). */
private val DIM_STOPS: List<Pair<Float, Float>> = listOf(0f to .62f, .34f to .40f, .62f to .12f, 1f to 0f)

/** Where, from the top, the status edge's dissolve starts: the body is all blur above it. */
internal fun statusEdgeSolid(statusPx: Float): Float = statusPx * STATUS_EDGE_SOLID_SHARE

/** Points along a smooth step for the edges' gradients. */
private const val EDGE_STEPS = 9

/**
 * Evenly spaced colours from [from] to [to] alpha of [color] along a smooth step ([edgeEase]): no knee at either end,
 * so no faint line where the gradient starts or stops.
 */
internal fun easedStops(color: Color, from: Float, to: Float): List<Color> =
    List(EDGE_STEPS) { i ->
        val f = i / (EDGE_STEPS - 1f)
        color.copy(alpha = (color.alpha * (from + (to - from) * edgeEase(f))).coerceIn(0f, 1f))
    }

/** The blur an edge (the status edge, the bottom edge) draws with here, or null where the platform cannot blur or a hold keeps it sharp. */
@Composable
internal fun kitStatusEdgeBlur(sigmaDp: Float = STATUS_EDGE_BLUR_DP): RenderEffect? {
    val platform = LocalKitPlatform.current
    val density = LocalDensity.current.density
    if (frostConditionsHold()) return null
    return remember(platform, density, sigmaDp) { platform.frostEffect(FrostSpec(sigmaDp, 1f, 0f), density) }
}

/** True when a hold (Saver, reduced motion, high contrast) keeps every frost of the page from drawing. */
@Composable
private fun frostConditionsHold(): Boolean {
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val signals by platform.signals.collectAsState()
    val c = FrostConditions(appearance.saver, LocalKitMotion.current.reduced, signals.highContrast, styleAllows = true)
    return FrostPolicy.heldBy(c) != null
}

/**
 * The status bar's edge over an edge-to-edge body (Glass): [source]'s content, blurred, dissolving from all blur at
 * the top into the sharp body ([statusEdgeSolid] down to [STATUS_EDGE_REACH_DP] below the status bar; the body clears
 * its sharp copy along the same curve), with the page's ground as a dim over it while content is [under] (0–1). Where
 * the platform cannot blur, only the body's fade and the dim are left.
 */
@Composable
internal fun BoxScope.KitStatusEdge(source: KitFrostState, statusPx: Int, under: () -> Float, blur: RenderEffect?) {
    if (statusPx <= 0) return
    val density = LocalDensity.current
    val edge = rememberStatusEdgeLayers()
    val origin = remember { arrayOf(Offset.Zero) }
    val height = with(density) { statusPx.toDp() + STATUS_EDGE_REACH_DP.dp }
    Box(
        Modifier.align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { origin[0] = it.positionInRoot() }
            .drawBehind {
                drawStatusEdge(edge, statusPx.toFloat(), blur, under()) {
                    val shift = source.sourceOrigin - origin[0]
                    translate(shift.x, shift.y) { drawLayer(source.layer) }
                }
            }
            .testTag("kit-status-edge")
    )
}

/** The status edge's two small layers: the blurred copy and its dissolve (no recording of content of their own). */
internal class StatusEdgeLayers(internal val blur: GraphicsLayer, internal val mask: GraphicsLayer, internal val ground: Color)

@Composable
internal fun rememberStatusEdgeLayers(): StatusEdgeLayers {
    val blur = rememberGraphicsLayer()
    val mask = rememberGraphicsLayer()
    val ground = LocalKitColors.current.ground
    return remember(blur, mask, ground) { StatusEdgeLayers(blur, mask, ground) }
}

/**
 * Draws the status edge from the top of this scope down to [statusPx] plus [STATUS_EDGE_REACH_DP]: [content] (a
 * recording that already exists, drawn where it lies) blurred by [effect] and dissolving out along the edge's curve,
 * then the dim of the ground at [under] strength.
 */
internal fun DrawScope.drawStatusEdge(
    layers: StatusEdgeLayers,
    statusPx: Float,
    effect: RenderEffect?,
    under: Float,
    alpha: Float = 1f,
    content: DrawScope.() -> Unit,
) {
    if (statusPx <= 0f) return
    val w = size.width
    // On whole pixels, the same ones the body's own fade clears on (kitScrollEdges, wholePx): the two are complements.
    val h = wholePx(statusPx + STATUS_EDGE_REACH_DP.dp.toPx())
    if (w <= 0f) return
    val solid = wholePx(statusEdgeSolid(statusPx))
    if (effect != null) {
        val box = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
        layers.blur.renderEffect = effect
        layers.blur.record(box) { content() }
        layers.mask.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        layers.mask.record(box) {
            drawLayer(layers.blur)
            // Keep the blur where the body is cleared: all of it above [solid], none at the edge's end.
            drawRect(Color.Black, Offset.Zero, Size(w, solid), blendMode = BlendMode.DstIn)
            drawRect(
                Brush.verticalGradient(easedStops(Color.Black, 1f, 0f), startY = solid, endY = h),
                Offset(0f, solid),
                Size(w, h - solid),
                blendMode = BlendMode.DstIn,
            )
        }
        layers.mask.alpha = alpha.coerceIn(0f, 1f)
        drawLayer(layers.mask)
    }
    val k = under.coerceIn(0f, 1f)
    if (k > 0f) {
        val g = layers.ground
        val stops = DIM_STOPS.map { (at, a) -> at to g.copy(alpha = g.alpha * a * k) }.toTypedArray()
        drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = h), Offset.Zero, Size(w, h))
    }
}

/**
 * Glass with frosted edges: the bar's capsule frosts what scrolls under it (the page's ground and live picture with the
 * body's recording, blurred at the person's frost strength) under the liquid bar's tint, which is thickened for its
 * words over blurred text ([LiquidEdge.face]), with the liquid panes' 1 dp rim light. Null where it cannot blur (the
 * platform, a hold, frost Off, a style other than Glass): the bar then lies on the page's background with nothing behind it.
 */
@Composable
internal fun kitFrostedBarFace(frost: KitFrostState?): Modifier? {
    frost ?: return null
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val colors = LocalKitColors.current
    val roles = LocalKitRoles.current
    val signals by platform.signals.collectAsState()
    val density = LocalDensity.current.density
    val conditions =
        FrostConditions(
            appearance.saver,
            LocalKitMotion.current.reduced,
            signals.highContrast,
            styleAllows = LocalKitStyle.current.style == Style.Glass,
        )
    val spec = FrostPolicy.spec(FrostRole.ChatHeader, appearance.frost, colors.dark, conditions) ?: return null
    val effect = remember(spec.sigmaDp, spec.saturation, density, platform) {
        platform.frostEffect(FrostSpec(spec.sigmaDp, spec.saturation, 0f), density)
    } ?: return null
    val tint = remember(roles) { LiquidEdge.face(roles, LiquidRole.Bar).fill?.let { Color(it.v) } ?: Color(roles.ground.v) }
    val rim = Color.White.copy(alpha = if (roles.dark) RIM_ALPHA_DARK else RIM_ALPHA_LIGHT)
    val ground = colors.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val blur = rememberGraphicsLayer()
    val at = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return Modifier.onGloballyPositioned { at[0] = it }
        .clip(LiquidBarShape)
        .drawBehind {
            val c = at[0]?.takeIf { it.isAttached }
            blur.renderEffect = effect
            blur.record {
                drawRect(ground)
                if (c != null) {
                    val bd = picture
                    val pl = bd?.layer
                    if (bd != null && pl != null && bd.painted && bd.root === c.findRootCoordinates()) {
                        val shift = bd.origin - c.positionInRoot()
                        translate(shift.x, shift.y) { drawLayer(pl) }
                    }
                    val shift = frost.sourceOrigin - c.positionInRoot()
                    translate(shift.x, shift.y) { drawLayer(frost.layer) }
                }
            }
            drawLayer(blur)
            drawRect(tint)
            drawOutline(LiquidBarShape.createOutline(size, layoutDirection, this), rim, style = Stroke(RIM_LIGHT_DP.dp.toPx()))
        }
        .testTag("kit-frosted-bar")
}

/** The look the top chrome takes for an edge-to-edge page: liquid, frosted, or clear. */
internal fun topEdgeLook(liquid: Boolean, frosted: Boolean): KitTopEdgeLook =
    when {
        liquid -> KitTopEdgeLook.Liquid
        frosted -> KitTopEdgeLook.Frosted
        else -> KitTopEdgeLook.Clear
    }
