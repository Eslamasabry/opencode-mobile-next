package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.effects.LiquidInk
import io.github.eslamasabry.oc.ui.design.effects.LiquidInkRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostConditions
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.LiquidClaim
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.GlassSpec
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassStackRoot
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/*
 * Glass's liquid edges (docs/designs/liquid-glass.md; owner 2026-11-08 "go full blown glass"), drawn by
 * Kyant0/AndroidLiquidGlass ("backdrop" 2.0.1, Apache-2.0). One entry point, [kitLiquidFace]: a floating part asks for
 * its liquid face and gets a modifier, or null, and then keeps exactly the face it draws today. Null whenever:
 * - FrostPolicy holds the edges (Saver, reduced motion, high contrast, a style other than Glass, frost Off, "Glass
 *   edges: Frosted", or no runtime shaders: Android below 13, desktop for now);
 * (beyond the window's budget, [LiquidEdge.grant], a part keeps its liquid face's shape and rim as frost, never solid);
 * - the part's shape is not a rounded kit shape (the lens bends rounded rectangles only).
 *
 * The face: the content behind (a source's recording) pressed together in contrast, blurred a little, bent only inside a
 * rim of min(12 dp, 20 % of the shorter side) with no depth effect and no dispersion, under today's Glass tint and words
 * tint thickened until its words read ([LiquidEdge.face]), with a soft white rim light ([LiquidRim]).
 *
 * Sources: a feature's [KitFrostState] (the transcript behind the chat's chrome, a list under its +), or a kit-owned
 * [KitLiquidSource] (the page under a sheet or a menu, a page body under its bar) that records only while a granted pane
 * uses it. A pane never lies inside the source it draws.
 */

/** Liquid edges' conditions as the kit reads them now: FrostPolicy's, with the platform's lens. */
@Composable
internal fun kitLiquidOn(): Boolean {
    val platform = LocalKitPlatform.current
    val signals by platform.signals.collectAsState()
    val appearance = LocalKitAppearance.current
    val conditions =
        FrostConditions(
            saver = appearance.saver,
            reducedMotion = LocalKitMotion.current.reduced,
            highContrast = signals.highContrast,
            styleAllows = LocalKitStyle.current.style == Style.Glass,
            lensSupported = platform.liquidGlass,
        )
    return FrostPolicy.liquidEdges(conditions, appearance.frost, appearance.glassEdges)
}

/** Whether Glass's floating parts may be liquid glass here and now (before the window's budget). */
@Composable
public fun kitLiquidGlassOn(): Boolean = kitLiquidOn()

/**
 * What a kit part records for liquid panes over it (the page under a sheet, a page body under its bar): only while a
 * granted pane uses it; at rest, and under every other style, it only draws its content.
 */
@Stable
public class KitLiquidSource internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null

    /** Each liquid role's ink flip over this content (see `KitFrostState.liquidFlips`). */
    internal val liquidFlips: MutableMap<Any, LiquidFlip> = HashMap()
    internal var users: Int by mutableIntStateOf(0)
    internal var version: Int = 0
}

@Composable
public fun rememberKitLiquidSource(): KitLiquidSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { KitLiquidSource(layer) }
}

/** Draws the content as usual and, while a liquid pane uses [source], records it there too. */
public fun Modifier.kitLiquidSource(source: KitLiquidSource): Modifier = this then LiquidSourceElement(source)

/**
 * A feature's scroll content recorded for the liquid panes over it (the conversations list under its +): the content
 * recorded by [kitFrostSource] while liquid glass may be drawn, nothing otherwise, so other styles draw exactly as before.
 */
@Composable
public fun Modifier.kitGlassSource(state: KitFrostState): Modifier = if (kitLiquidOn()) kitFrostSource(state) else this

/** The page under the window's sheets and menus (provided by `KitSheetHost`). */
internal val LocalKitLiquidPage: ProvidableCompositionLocal<KitLiquidSource?> = staticCompositionLocalOf { null }

/**
 * What scrolls behind a page's bar and bottom block (the chat's transcript recording), provided by `KitScreen` to those
 * two slots in both of its layouts, so the composer never flips between liquid and frost when a status line, the
 * loading line or Find moves the page into its stacked layout.
 */
public val LocalKitLiquidChrome: ProvidableCompositionLocal<KitFrostState?> = staticCompositionLocalOf { null }

/**
 * True inside a sheet or a dialog (set by `KitSheetHost`). The page recording does not hold the sheet, so a menu opened
 * there would show the page through the sheet: it keeps its frosted face. Glass is not stacked on glass.
 */
internal val LocalKitLiquidOverModal: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** One window's liquid surfaces and who of them is drawn liquid (provided by `KitSheetHost`; none: no cap). */
@Stable
internal class KitLiquidBudget {
    internal val claims = mutableStateListOf<LiquidClaim<Any>>()
    private var next = 0
    internal val granted by derivedStateOf { LiquidEdge.grant(claims.toList()) }

    internal fun order(): Int = next++
}

internal val LocalKitLiquidBudget: ProvidableCompositionLocal<KitLiquidBudget?> = staticCompositionLocalOf { null }

/** [source] as the budget counts it: a kit recording costs one, a feature's recording nothing. */
private fun costed(source: Any?): Any? = source as? KitLiquidSource

/** Asks the window's budget for [role]'s liquid face over [source]; true when granted (always, without a budget). */
@Composable
private fun kitLiquidClaim(role: LiquidRole, source: Any?, asking: Boolean): Boolean {
    val budget = LocalKitLiquidBudget.current ?: return asking
    val key = remember { Any() }
    val order = remember(budget) { budget.order() }
    DisposableEffect(budget, role, source, asking) {
        val claim = LiquidClaim(key, role, costed(source), order)
        if (asking) budget.claims += claim
        onDispose { budget.claims.remove(claim) }
    }
    return asking && key in budget.granted
}

/**
 * [role]'s liquid face over [source] (a feature's recording, or null), shaped [shape]; null keeps the part's own face.
 * [transform] is how far a transform the library cannot follow has run (a sheet's predictive-back shrink, a menu's
 * grow-in; 0 at rest, 1 fully): the lens and its rim light fade into plain frost as it rises, so nothing misaligns.
 */
@Composable
public fun kitLiquidFace(
    role: LiquidRole,
    source: KitFrostState?,
    shape: Shape,
    transform: () -> Float = NoTransform,
    /** A chosen or "on" part (an active chip): its glass leans to the accent, as Glass's chosen panes do. */
    chosen: Boolean = false,
): Modifier? {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val backdrop = remember(source, ground, picture) { source?.let { frostStateBackdrop(it, ground, picture) } }
    return liquidFace(role, source, backdrop, shape, transform, chosen)
}

/** [role]'s liquid face over a kit-owned [source] (the page, a body under its bar); null keeps the part's own face. */
@Composable
internal fun kitLiquidFace(role: LiquidRole, source: KitLiquidSource?, shape: Shape, transform: () -> Float = NoTransform): Modifier? {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val backdrop = remember(source, ground, picture) { source?.let { liquidSourceBackdrop(it, ground, picture) } }
    return liquidFace(role, source, backdrop, shape, transform)
}

/** A menu's liquid face over the page, unless it opens over a sheet or a dialog. */
@Composable
internal fun kitLiquidMenuFace(shape: Shape, transform: () -> Float): Modifier? =
    kitLiquidFace(LiquidRole.Menu, LocalKitLiquidPage.current.takeIf { !LocalKitLiquidOverModal.current }, shape, transform)

/** A sheet's liquid face over the page. */
@Composable
internal fun kitLiquidSheetFace(shape: Shape, transform: () -> Float): Modifier? =
    kitLiquidFace(LiquidRole.Sheet, LocalKitLiquidPage.current, shape, transform)

private val NoTransform: () -> Float = { 0f }

/**
 * Whether [shape] can carry the lens: the kit's rounded shapes are rounded rectangles (`RoundedCornerShape`, the circle
 * and the stadium included). A square kit shape (an end side sheet, Sharp corners' boxes) keeps its frosted face.
 */
internal fun liquidShapeOk(shape: Shape): Boolean = shape is CornerBasedShape

/** How far a transform the library cannot follow has run, as [kitLiquidFace]'s `transform` takes it. */
internal object LiquidMotion {
    /** The predictive back gesture's progress (0–1) as the face's way into frost: all frost by a quarter of the way. */
    fun shrinkFraction(back: Float): Float = (back / BACK_TO_FROST).coerceIn(0f, 1f)

    /** A grow-in from [from] scale (a menu's 0.9) at [scale] now: 1 at its start, 0 once it has grown. */
    fun growFraction(scale: Float, from: Float): Float = if (from >= 1f) 0f else ((1f - scale) / (1f - from)).coerceIn(0f, 1f)

    const val BACK_TO_FROST: Float = .25f
}

@Composable
private fun liquidFace(role: LiquidRole, source: Any?, backdrop: KitLiquidBackdrop?, shape: Shape, transform: () -> Float, chosen: Boolean = false): Modifier? {
    val on = kitLiquidOn() && backdrop != null
    val shaped = liquidShapeOk(shape)
    // Only rounded kit shapes reach the lens; a square one falls back quietly, anything else is a kit bug.
    kitAssert(!on || shaped || shape === RectangleShape) { "kitLiquidFace: $role's shape is not a rounded kit shape ($shape)" }
    val asking = on && shaped
    val granted = kitLiquidClaim(role, source, asking)
    val users = source as? KitLiquidSource
    if (users != null) {
        DisposableEffect(users, asking) {
            if (asking) users.users++
            onDispose { if (asking) users.users-- }
        }
    }
    if (!asking || backdrop == null) return null
    // Beyond the window's budget a part is frost of the same shape through the same glass (the lens let go, the rim
    // kept), never its solid face: nothing changes shape or flickers as a menu or sheet opens (owner 2026-11-08).
    val face = if (granted) transform else HeldFrost
    return Modifier.liquidPane(role, backdrop, shape as CornerBasedShape, face, liquidFlipFor(source, role), chosen, keepRim = !granted)
}

/** A claim beyond the budget: all frost, no lens. */
private val HeldFrost: () -> Float = { 1f }

/**
 * The ink flip [role]'s pane over [source] shares with the words beside it (a bar's words lie beside its capsule): one
 * per source and role, so the face and the words always flip together.
 */
internal fun liquidFlipFor(source: Any?, role: LiquidRole): LiquidFlip? =
    when (source) {
        is KitFrostState -> source.liquidFlips.getOrPut(role) { LiquidFlip() }
        is KitLiquidSource -> source.liquidFlips.getOrPut(role) { LiquidFlip() }
        else -> null
    }

@Composable
private fun Modifier.liquidPane(
    role: LiquidRole,
    backdrop: KitLiquidBackdrop,
    shape: CornerBasedShape,
    transform: () -> Float,
    shared: LiquidFlip?,
    chosen: Boolean,
    keepRim: Boolean = false,
): Modifier {
    val own = remember { LiquidFlip() }
    val flip = shared ?: own
    val roles = LocalKitRoles.current
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val spec =
        remember(roles, role, rtl, appearance.pack, appearance.accent, flip, chosen, keepRim) {
            // The face carries its words itself (no plate): its tint is thickened for the theme's ink over the content
            // that ink lies over; over content of the other brightness the words flip to the other theme's ink and the
            // face to the other theme's face, which carries that ink there.
            val other = ThemeResolver.roles(appearance.pack, appearance.accent, !roles.dark, saver = false, dynamic = null)
            val face = LiquidEdge.face(roles, role)
            val otherFace = LiquidEdge.face(other, role)
            // A chosen part's glass leans to the accent and holds a little more (GlassSpec's chosen tint and lift).
            fun lean(c: Color, dark: Boolean): Color {
                if (!chosen) return c
                val theme = if (dark == roles.dark) roles else other
                val opaque = io.github.eslamasabry.oc.ui.design.color.Argb(c.copy(alpha = 1f).toArgb())
                return Color(GlassSpec.chosenTintOf(theme, opaque).v).copy(alpha = minOf(1f, c.alpha + GlassSpec.CHOSEN_LIFT))
            }
            val fill = lean(face.fill?.let { Color(it.v) } ?: Color.Transparent, roles.dark)
            val recipe = LiquidEdge.recipe(role, roles.dark)
            val rim = LiquidRim.Light
            KitLiquidPaneSpec(
                recipe = recipe,
                fill = fill,
                fillStart = face.fillStart?.let { lean(Color(it.v), roles.dark) } ?: fill,
                rtl = rtl,
                rim = rim.copy(alpha = if (roles.dark) RIM_ALPHA_DARK else RIM_ALPHA_LIGHT),
                rimAdds = roles.dark,
                rimDp = RIM_LIGHT_DP,
                shadow = Color.Black.copy(alpha = recipe.shadowAlpha),
                frostBlurDp = GlassSpec.FLOAT_FROST,
                sheen = Color(face.sheen.v),
                specular = rim.copy(alpha = if (roles.dark) SPECULAR_ALPHA_DARK else SPECULAR_ALPHA_LIGHT),
                flipFill = otherFace.fill?.let { lean(Color(it.v), !roles.dark) } ?: fill,
                flipFillStart = (otherFace.fillStart ?: otherFace.fill)?.let { lean(Color(it.v), !roles.dark) } ?: fill,
                flipSheen = Color(otherFace.sheen.v),
                flip = flip,
                keepRim = keepRim,
            )
        }
    val words = kitLiquidWords(backdrop, flip)
    val latest by rememberUpdatedState(transform)
    return this.then(platform.liquidPane(spec, backdrop, shape) { latest() }).kitGlassStackRoot().kitLiquidPlateHost(words)
}

/**
 * What the words on a liquid pane over [backdrop] are drawn with: the theme's own ink, and the opposite theme's ink the
 * pane flips to over content of the other brightness ([LiquidInkRule]). No plate behind them (owner 2026-11-08: "get
 * rid of the stupid haze behind the text"): the face's tint and blur carry them ([LiquidEdge.face]).
 */
@Composable
internal fun kitLiquidWords(backdrop: KitLiquidBackdrop?, flip: LiquidFlip?): KitLiquidWords {
    val roles = LocalKitRoles.current
    val appearance = LocalKitAppearance.current
    return remember(roles, backdrop, appearance.pack, appearance.accent, flip) {
        val other = ThemeResolver.roles(appearance.pack, appearance.accent, !roles.dark, saver = false, dynamic = null)
        KitLiquidWords(
            flipInk = Color(other.text1.v),
            themeInk = LiquidInkRule.of(roles.dark),
            backdrop = backdrop,
            flip = flip ?: LiquidFlip(),
        )
    }
}

/** The words' inks on one liquid pane (see [kitLiquidWords]). */
@Immutable
internal data class KitLiquidWords(
    val flipInk: Color,
    val themeInk: LiquidInk,
    val backdrop: KitLiquidBackdrop?,
    /** Shared with the pane's face, which flips with the words. */
    val flip: LiquidFlip,
)

/**
 * Marks a liquid pane for the words on it ([kitWordsPlate] on each KitText finds it): with a backdrop, the pane samples
 * the content under it and flips its words' ink over content of the other brightness.
 */
internal fun Modifier.kitLiquidPlateHost(words: KitLiquidWords): Modifier = this then PlateHostElement(words)

private object LiquidPlateKey

private data class PlateHostElement(val words: KitLiquidWords) : ModifierNodeElement<PlateHostNode>() {
    override fun create(): PlateHostNode = PlateHostNode(words)

    override fun update(node: PlateHostNode) {
        node.words = words
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitLiquidPlateHost"
    }
}

private class PlateHostNode(var words: KitLiquidWords) :
    Modifier.Node(), TraversableNode, GlobalPositionAwareModifierNode, androidx.compose.ui.node.CompositionLocalConsumerModifierNode {
    override val traverseKey: Any get() = LiquidPlateKey

    /** 0: the theme's own ink; 1: the flipped one (shared with the pane's face). */
    val flip: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> get() = words.flip.progress

    private var coordinates: LayoutCoordinates? = null
    private var ink: LiquidInk = words.themeInk
    private var sample: GraphicsLayer? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun onAttach() {
        ink = words.themeInk
        val backdrop = words.backdrop ?: return
        val platform = currentValueOf(LocalKitPlatform)
        coroutineScope.launch {
            var seen = Int.MIN_VALUE
            while (isActive) {
                delay(LiquidInkRule.SAMPLE_MS)
                // Only while the content moves: a source that has not recorded again shows nothing new.
                val v = backdrop.version()
                if (v == seen) continue
                val c = coordinates?.takeIf { it.isAttached } ?: continue
                seen = v
                val w = (c.size.width / SAMPLE_SCALE).coerceAtLeast(1)
                val h = (c.size.height / SAMPLE_SCALE).coerceAtLeast(1)
                val layer = sample ?: requireGraphicsContext().createGraphicsLayer().also { sample = it }
                layer.record(requireDensity(), LayoutDirection.Ltr, IntSize(w, h)) {
                    scale(1f / SAMPLE_SCALE, 1f / SAMPLE_SCALE, Offset.Zero) { backdrop.draw(this, c) }
                }
                val pixels = IntArray(w * h)
                // A sample the platform could not read back (empty or clear) says nothing about the content.
                if (!platform.readPixels(layer.toImageBitmap(), pixels, w, h)) continue
                if (pixels.count { (it ushr 24) > 0x80 } < pixels.size / 2) continue
                val (mean, _) = LiquidInkRule.measure(pixels)
                val next = LiquidInkRule.next(ink, mean)
                if (next != ink) {
                    ink = next
                    launch { flip.animateTo(if (next == words.themeInk) 0f else 1f, tween(LiquidInkRule.FLIP_MS)) }
                }
            }
        }
    }

    override fun onDetach() {
        sample?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        sample = null
        coordinates = null
    }
}

/** The sampled copy of a pane's backdrop is this many times smaller on each side. */
private const val SAMPLE_SCALE: Int = 8

/**
 * A text on a liquid pane: its ink cross-fades to the other theme's while the pane flips (adaptive glass). It draws no
 * plate behind the words (owner 2026-11-08: no haze behind text anywhere). Outside a liquid pane, and under every other
 * style, it draws the words alone.
 */
public fun Modifier.kitWordsPlate(): Modifier = this then WordsPlateElement

private object WordsPlateElement : ModifierNodeElement<WordsPlateNode>() {
    override fun create(): WordsPlateNode = WordsPlateNode()

    override fun update(node: WordsPlateNode) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "kitWordsPlate"
    }

    override fun hashCode(): Int = 0x57_50_4C

    override fun equals(other: Any?): Boolean = other === this
}

private class WordsPlateNode : Modifier.Node(), DrawModifierNode {
    private var host: PlateHostNode? = null

    override fun onDetach() {
        host = null
    }

    /**
     * The pane above, looked up when drawing: a pane's host may arrive after the words (its face appears once the
     * window's budget grants it), so a lookup at attach would miss it.
     */
    private fun hostNow(): PlateHostNode? {
        host?.takeIf { it.isAttached }?.let { return it }
        return (findNearestAncestor(LiquidPlateKey) as? PlateHostNode).also { host = it }
    }

    override fun ContentDrawScope.draw() {
        val h = hostNow()
        val f = h?.flip?.value ?: 0f
        val words = h?.words
        if (words == null || f <= 0f) {
            drawContent()
            return
        }
        // The flip: the theme's ink fades out while the other ink (the glyphs drawn through a tint) fades in.
        val bounds = androidx.compose.ui.geometry.Rect(Offset(-size.width, -size.height), Size(size.width * 3f, size.height * 3f))
        drawIntoCanvas { canvas ->
            if (f < 1f) {
                canvas.saveLayer(bounds, Paint().apply { alpha = 1f - f })
                drawContent()
                canvas.restore()
            }
            canvas.saveLayer(bounds, Paint().apply { alpha = f; colorFilter = ColorFilter.tint(words.flipInk, BlendMode.SrcIn) })
            drawContent()
            canvas.restore()
        }
    }
}

/** The rim light's width, in dp: a sharp, thin 1 dp line with no blur (owner 2026-11-09, "crisper"). */
internal const val RIM_LIGHT_DP: Float = 1f

/**
 * The rim light's strength over a dark theme (added light) and a light one (laid over): soft white light (owner
 * 2026-11-08: glass edges are a neutral white light, not accent-tinted; an HCT-pulled yellow accent read as blue-teal),
 * low in a light theme where white on milk needs little.
 */
internal const val RIM_ALPHA_DARK: Float = .36f
internal const val RIM_ALPHA_LIGHT: Float = .55f

/**
 * The specular light (the rim light's colour): a bright segment round the top-start corner and a thin glint line just
 * inside the top edge, as Glass's in-flow panes draw it (`drawGlassSpecular`). Restrained, never plastic.
 */
internal const val SPECULAR_ALPHA_DARK: Float = .60f
internal const val SPECULAR_ALPHA_LIGHT: Float = .85f

/**
 * The rim light's colour on every glass edge, liquid and frosted (panes, sheets, menus): neutral white light (owner
 * 2026-11-08). The accent marks chosen states only (a chosen row's frost, the check, the chosen ring).
 */
internal object LiquidRim {
    val Light: Color = Color.White
}

/**
 * A bar's liquid face, behind the bar's own content (call it first inside the bar's box): a floating capsule
 * [LIQUID_BAR_INSET_X] in from the bar's sides and [LIQUID_BAR_INSET_Y] from its top and bottom, its ends rounded by at
 * most [LIQUID_BAR_RADIUS]. The bar's content keeps its own place: only its ground becomes glass.
 */
@Composable
internal fun BoxScope.KitLiquidBarPane(face: Modifier) {
    Box(
        Modifier.matchParentSize()
            .padding(horizontal = LIQUID_BAR_INSET_X.dp, vertical = LIQUID_BAR_INSET_Y.dp)
            .then(face)
            .testTag("kit-liquid-bar")
    )
}

/** The bar capsule's shape. */
internal val LiquidBarShape: CornerBasedShape = RoundedCornerShape(26.dp)

/** How far a bar's capsule sits in from the bar's sides, in dp. */
public const val LIQUID_BAR_INSET_X: Int = 6

/** How far a bar's capsule sits in from the bar's top and bottom, in dp. */
public const val LIQUID_BAR_INSET_Y: Int = 4

/** The most a bar's capsule's ends round, in dp (a two-line bar is a rounded panel, not a lozenge). */
public const val LIQUID_BAR_RADIUS: Int = 26

/**
 * The edge where a page's content meets its floating bar ([top]) or bottom block: no band of ground, only a soft fade
 * of it towards the screen's edge, so what scrolls past the glass fades rather than cuts.
 */
@Composable
internal fun Modifier.kitLiquidEdgeFade(top: Boolean): Modifier {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    return drawBehind {
        // The content fades out over the block's inner quarter, so the parts that lie on the block itself (the chips
        // above the message box, the bar's own words) sit on the page's ground, never on the words scrolling past.
        if (top) {
            val stops = arrayOf(0f to ground.copy(alpha = EDGE_FADE_ALPHA), .55f to ground.copy(alpha = EDGE_FADE_ALPHA * .8f), 1f to ground.copy(alpha = 0f))
            drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = size.height))
        } else {
            // The scroll edge: the content fades out over EDGE_FADE_DP above the block, and the block itself (the chips
            // row, the gaps round the message box) lies on the ground. The message box stays glass over the content.
            val reach = EDGE_FADE_DP.dp.toPx()
            drawRect(
                Brush.verticalGradient(0f to ground.copy(alpha = 0f), 1f to ground.copy(alpha = EDGE_FADE_ALPHA), startY = -reach, endY = 0f),
                topLeft = Offset(0f, -reach),
                size = Size(size.width, reach),
            )
            drawRect(ground.copy(alpha = EDGE_FADE_ALPHA))
        }
    }
}

/**
 * The soft fade of the page's ground behind a row of glass chips over the message box (owner 2026-11-09: "transcript
 * text must not read through behind controls"): under the chips only, never a band behind the whole bottom block (the
 * message box stays glass over the transcript), feathered [CHIPS_FADE_FEATHER_DP] past the row on every side. Drawn
 * only where the chips are liquid glass on a chat's bottom block; elsewhere the modifier draws nothing.
 */
@Composable
public fun Modifier.kitLiquidChipsFade(): Modifier {
    if (LocalKitLiquidChrome.current == null || !kitLiquidOn()) return this
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    return drawBehind {
        val f = CHIPS_FADE_FEATHER_DP.dp.toPx()
        val w = size.width + 2 * f
        val h = size.height + 2 * f
        if (w <= 0f || h <= 0f) return@drawBehind
        val core = ground.copy(alpha = CHIPS_FADE_ALPHA)
        val clear = ground.copy(alpha = 0f)
        val fy = f / h
        val fx = f / w
        drawIntoCanvas { canvas ->
            val bounds = androidx.compose.ui.geometry.Rect(-f, -f, size.width + f, size.height + f)
            canvas.saveLayer(bounds, Paint())
            drawRect(
                Brush.verticalGradient(0f to clear, fy * 1.6f to core, 1f - fy to core, 1f to clear, startY = -f, endY = size.height + f),
                topLeft = Offset(-f, -f),
                size = Size(w, h),
            )
            // Feather the ends: keep the band only where the horizontal mask is opaque.
            drawRect(
                Brush.horizontalGradient(0f to Color.Transparent, fx * 1.6f to Color.Black, 1f - fx * 1.6f to Color.Black, 1f to Color.Transparent, startX = -f, endX = size.width + f),
                topLeft = Offset(-f, -f),
                size = Size(w, h),
                blendMode = BlendMode.DstIn,
            )
            canvas.restore()
        }
    }
}

/** How far the chips' fade reaches past the row, in dp. */
internal const val CHIPS_FADE_FEATHER_DP: Int = 14

/** How much of the ground lies behind the chips at the fade's core: words behind them no longer read. */
internal const val CHIPS_FADE_ALPHA: Float = .86f

$a
internal const val EDGE_FADE_DP: Int = 20

/** The words of [role]'s liquid pane over a feature's [source], for words that lie beside their pane (a bar). */
@Composable
internal fun kitLiquidWordsHost(role: LiquidRole, source: KitFrostState?): Modifier {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val backdrop = remember(source, ground, picture) { source?.let { frostStateBackdrop(it, ground, picture) } }
    return Modifier.kitLiquidPlateHost(kitLiquidWords(backdrop, liquidFlipFor(source, role)))
}

/** The words of [role]'s liquid pane over a kit [source] (a page body under its bar). */
@Composable
internal fun kitLiquidWordsHost(role: LiquidRole, source: KitLiquidSource?): Modifier {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val backdrop = remember(source, ground, picture) { source?.let { liquidSourceBackdrop(it, ground, picture) } }
    return Modifier.kitLiquidPlateHost(kitLiquidWords(backdrop, liquidFlipFor(source, role)))
}

/** How much of the ground the edge fade reaches at the screen's edge. */
internal const val EDGE_FADE_ALPHA: Float = .96f


/**
 * A page's body run up under its bar by [barPx] (the bar floats over it): measured that much taller and placed that
 * much higher, while it takes only its own room in the page, so nothing below it moves.
 */
internal fun Modifier.kitRunUnderBar(barPx: () -> Int): Modifier =
    layout { measurable, constraints ->
        val px = barPx()
        if (px <= 0 || !constraints.hasBoundedHeight) {
            val p = measurable.measure(constraints)
            return@layout layout(p.width, p.height) { p.place(0, 0) }
        }
        val tall = constraints.maxHeight + px
        val p = measurable.measure(constraints.copy(minHeight = (constraints.minHeight + px).coerceAtMost(tall), maxHeight = tall))
        layout(p.width, (p.height - px).coerceAtLeast(0)) { p.place(0, -px) }
    }

/** Paints the page's ground (and the live picture, where one is drawn) under a source's recording. */
private fun DrawScope.drawGroundUnder(ground: Color, picture: KitGlassBackdrop?, at: LayoutCoordinates) {
    drawRect(ground, Offset(-size.width, -size.height), Size(size.width * 3f, size.height * 3f))
    val bd = picture ?: return
    val layer = bd.layer ?: return
    if (!bd.painted || bd.root !== at.findRootCoordinates()) return
    val shift = bd.origin - at.positionInRoot()
    translate(shift.x, shift.y) { drawLayer(layer) }
}

/** A feature's [KitFrostState] (its content recorded by [kitFrostSource]) seen from a pane in the same window. */
private fun frostStateBackdrop(state: KitFrostState, ground: Color, picture: KitGlassBackdrop?): KitLiquidBackdrop =
    KitLiquidBackdrop({ state.version }) { c ->
        drawGroundUnder(ground, picture, c)
        val shift = state.sourceOrigin - c.positionInRoot()
        translate(shift.x, shift.y) { drawLayer(state.layer) }
    }

/**
 * A [KitLiquidSource] seen from a pane: in its own window by the layout's own mapping, from a popup's window (a menu) by
 * screen position, which both windows share.
 */
private fun liquidSourceBackdrop(source: KitLiquidSource, ground: Color, picture: KitGlassBackdrop?): KitLiquidBackdrop =
    KitLiquidBackdrop({ source.version }) { c ->
        drawGroundUnder(ground, picture, c)
        val s = source.coordinates?.takeIf { it.isAttached } ?: return@KitLiquidBackdrop
        val shift =
            if (s.findRootCoordinates() === c.findRootCoordinates()) c.localPositionOf(s, Offset.Zero)
            else s.positionOnScreen() - c.positionOnScreen()
        translate(shift.x, shift.y) { drawLayer(source.layer) }
    }

private data class LiquidSourceElement(val source: KitLiquidSource) : ModifierNodeElement<LiquidSourceNode>() {
    override fun create(): LiquidSourceNode = LiquidSourceNode(source)

    override fun update(node: LiquidSourceNode) {
        if (node.source !== source) {
            node.source.coordinates = null
            node.source = source
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitLiquidSource"
    }
}

private class LiquidSourceNode(var source: KitLiquidSource) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        source.coordinates = coordinates
    }

    override fun onDetach() {
        source.coordinates = null
    }

    override fun ContentDrawScope.draw() {
        // Read in the draw phase: a pane arriving or leaving redraws this once, to start or stop recording.
        if (source.users > 0) {
            source.layer.record { this@draw.drawContent() }
            source.version++
            drawLayer(source.layer)
        } else {
            drawContent()
        }
    }
}
