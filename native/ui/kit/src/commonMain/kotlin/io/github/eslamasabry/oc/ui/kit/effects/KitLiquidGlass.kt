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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
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
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import io.github.eslamasabry.oc.ui.design.effects.FrostConditions
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.LiquidClaim
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.effects.RimLight
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
 * - the window's budget gives the part no room ([LiquidEdge.grant]; the lowest drop to frost first);
 * - the part's shape is not a rounded kit shape (the lens bends rounded rectangles only).
 *
 * The face: the content behind (a source's recording) pressed together in contrast, blurred a little, bent only inside a
 * rim of min(12 dp, 20 % of the shorter side) with no depth effect and no dispersion, under today's Glass tint and words
 * plate thickened by GlassFinish ([LiquidEdge.face]), with a soft rim light tinted from the accent ([RimLight]).
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
    internal var users: Int by mutableIntStateOf(0)
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
public fun kitLiquidFace(role: LiquidRole, source: KitFrostState?, shape: Shape, transform: () -> Float = NoTransform): Modifier? {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val backdrop = remember(source, ground, picture) { source?.let { FrostStateBackdrop(it, ground, picture) } }
    return liquidFace(role, source, backdrop, shape, transform)
}

/** [role]'s liquid face over a kit-owned [source] (the page, a body under its bar); null keeps the part's own face. */
@Composable
internal fun kitLiquidFace(role: LiquidRole, source: KitLiquidSource?, shape: Shape, transform: () -> Float = NoTransform): Modifier? {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val backdrop = remember(source, ground, picture) { source?.let { LiquidSourceBackdrop(it, ground, picture) } }
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
private fun liquidFace(role: LiquidRole, source: Any?, backdrop: Backdrop?, shape: Shape, transform: () -> Float): Modifier? {
    val on = kitLiquidOn() && backdrop != null
    val shaped = liquidShapeOk(shape)
    // Only rounded kit shapes reach the lens; a square one falls back quietly, anything else is a kit bug.
    kitAssert(!on || shaped || shape === RectangleShape) { "kitLiquidFace: $role's shape is not a rounded kit shape ($shape)" }
    val granted = kitLiquidClaim(role, source, on && shaped)
    val users = source as? KitLiquidSource
    if (users != null) {
        DisposableEffect(users, granted) {
            if (granted) users.users++
            onDispose { if (granted) users.users-- }
        }
    }
    if (!granted || backdrop == null) return null
    return Modifier.liquidPane(role, backdrop, shape as CornerBasedShape, transform)
}

@Composable
private fun Modifier.liquidPane(role: LiquidRole, backdrop: Backdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
    val roles = LocalKitRoles.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val recipe = remember(role, roles.dark) { LiquidEdge.recipe(role, roles.dark) }
    val face = remember(roles, role) { LiquidEdge.face(roles, role) }
    val fill = face.fill?.let { Color(it.v) } ?: Color.Transparent
    val fillStart = face.fillStart?.let { Color(it.v) } ?: fill
    val rim = remember(roles) { Color(RimLight.of(roles).v) }
    val rimBlend = if (roles.dark) BlendMode.Plus else BlendMode.SrcOver
    val shadow = Color.Black.copy(alpha = recipe.shadowAlpha)
    val latest by rememberUpdatedState(transform)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                val t = latest().coerceIn(0f, 1f)
                colorControls(brightness = recipe.brightness, contrast = recipe.contrast, saturation = recipe.saturation)
                // Into frost while a transform runs: the blur grows to the Glass float frost and the lens lets go.
                blur((recipe.blurDp + (GlassSpec.FLOAT_FROST - recipe.blurDp) * t).dp.toPx())
                if (size.isSpecified && t < 1f) {
                    val rimPx = LiquidEdge.rimPx(size.minDimension, density)
                    lens(rimPx, recipe.refractionDp.dp.toPx() * (1f - t))
                }
            },
            highlight = {
                val t = latest().coerceIn(0f, 1f)
                Highlight(
                    width = RIM_LIGHT_DP.dp,
                    blurRadius = RIM_LIGHT_DP.dp,
                    alpha = (1f - t),
                    style = HighlightStyle.Default(color = rim.copy(alpha = if (roles.dark) RIM_ALPHA_DARK else RIM_ALPHA_LIGHT), blendMode = rimBlend),
                )
            },
            shadow = if (recipe.shadowAlpha > 0f) ({ Shadow(radius = 24.dp, color = shadow) }) else null,
            onDrawSurface = {
                if (fillStart == fill) {
                    drawRect(fill)
                } else {
                    val start = Offset(if (rtl) size.width else 0f, 0f)
                    val end = Offset(if (rtl) 0f else size.width, size.height)
                    // Today's Glass tint, lit at its top start and plain from 70 % of the way across.
                    drawRect(Brush.linearGradient(0f to fillStart, .7f to fill, start = start, end = end))
                }
            },
        )
        .kitGlassStackRoot()
}

/** The rim light's width (and its soft blur), in dp: a soft line, never a hairline. */
internal const val RIM_LIGHT_DP: Float = 1.5f

/** The rim light's strength over a dark theme (added light) and a light one (laid over). */
internal const val RIM_ALPHA_DARK: Float = .55f
internal const val RIM_ALPHA_LIGHT: Float = .60f

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
        val stops =
            if (top) arrayOf(0f to ground.copy(alpha = EDGE_FADE_ALPHA), 1f to ground.copy(alpha = 0f))
            else arrayOf(0f to ground.copy(alpha = 0f), 1f to ground.copy(alpha = EDGE_FADE_ALPHA))
        drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = size.height))
    }
}

/** How much of the ground the edge fade reaches at the screen's edge. */
internal const val EDGE_FADE_ALPHA: Float = .82f

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
private class FrostStateBackdrop(val state: KitFrostState, val ground: Color, val picture: KitGlassBackdrop?) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val c = coordinates?.takeIf { it.isAttached } ?: return
        drawGroundUnder(ground, picture, c)
        val shift = state.sourceOrigin - c.positionInRoot()
        translate(shift.x, shift.y) { drawLayer(state.layer) }
    }
}

/**
 * A [KitLiquidSource] seen from a pane: in its own window by the layout's own mapping, from a popup's window (a menu) by
 * screen position, which both windows share.
 */
private class LiquidSourceBackdrop(val source: KitLiquidSource, val ground: Color, val picture: KitGlassBackdrop?) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val c = coordinates?.takeIf { it.isAttached } ?: return
        drawGroundUnder(ground, picture, c)
        val s = source.coordinates?.takeIf { it.isAttached } ?: return
        val shift =
            if (s.findRootCoordinates() === c.findRootCoordinates()) c.localPositionOf(s, Offset.Zero)
            else s.positionOnScreen() - c.positionOnScreen()
        translate(shift.x, shift.y) { drawLayer(source.layer) }
    }
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
            drawLayer(source.layer)
        } else {
            drawContent()
        }
    }
}
