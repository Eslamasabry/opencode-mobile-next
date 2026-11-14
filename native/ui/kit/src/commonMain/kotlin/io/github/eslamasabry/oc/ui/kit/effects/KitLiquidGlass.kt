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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
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
import io.github.eslamasabry.oc.ui.kit.foundation.kitBackdropRimLight
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassFrostAvailable
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassStackRoot
import io.github.eslamasabry.oc.ui.kit.live.kitLivePictureSpots
import io.github.eslamasabry.oc.ui.kit.platform.KitBackdropSampler
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.foundation.kitRecord
import io.github.eslamasabry.oc.ui.kit.foundation.kitDrawLayer

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
internal fun kitLiquidOn(): Boolean = kitEdgesHeldBy() == null

/** Why Glass's floating parts are not liquid glass here and now, or null when they are ([FrostPolicy.edgesHeldBy]). */
@Composable
private fun kitEdgesHeldBy(): io.github.eslamasabry.oc.ui.design.effects.EdgeHold? {
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
    return FrostPolicy.edgesHeldBy(conditions, appearance.frost, kitGlassLook().id)
}

/**
 * Whether Glass's panels over the page (sheets, dialogs, menus) are frost of the page here: the look has no lens (the
 * Frosted look) and the platform can blur (Android 12+, desktop). Without this they fell to their own solid face, an
 * opaque sheet over the page (owner 2026-11-09: "Frosted does not blur at all").
 */
@Composable
internal fun kitFrostedPanelsOn(): Boolean =
    kitEdgesHeldBy() == io.github.eslamasabry.oc.ui.design.effects.EdgeHold.Frosted && kitGlassFrostAvailable()

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

    /** Counts the recordings; snapshot state read by the panes' draws (see `KitFrostState.version`). */
    internal var version: Int by mutableIntStateOf(0)
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
 * there would show the page through the sheet: it is a raised piece of the sheet's glass instead
 * ([kitMenuOverModalFace]). Glass is not stacked on glass.
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
internal fun kitLiquidClaim(role: LiquidRole, source: Any?, asking: Boolean, rank: Int = role.rank): Boolean {
    val budget = LocalKitLiquidBudget.current ?: return asking
    val key = remember { Any() }
    val order = remember(budget) { budget.order() }
    DisposableEffect(budget, role, source, asking, rank) {
        val claim = LiquidClaim(key, role, costed(source), order, rank)
        if (asking) budget.claims += claim
        onDispose { budget.claims.remove(claim) }
    }
    // Read through its own derived state: a menu opening changes the window's grants, and only a pane whose own grant
    // changed recomposes (smoothness 2026-11-09), not every pane on the page.
    val mine by remember(budget, key) { derivedStateOf { key in budget.granted } }
    return asking && mine
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
    /**
     * This pane's part of a joined body (KitLiquidJoin.kt: the message box, the jump pill): while its partner is
     * liquid too, the face is the joined pane, drawn with it as one body of glass; alone it is the library's pane.
     */
    join: KitLiquidJoinPart? = null,
    /** No rim light and no edge highlight: glass known by its bend alone (the top pane of a page, owner 2026-11-14). */
    rimless: Boolean = false,
): Modifier? {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val backdrop = remember(source, ground, picture) { source?.let { frostStateBackdrop(it, ground, picture) } }
    return liquidFace(role, source, backdrop, shape, transform, chosen, join, rimless = rimless)
}

/** [role]'s liquid face over a kit-owned [source] (the page, a body under its bar); null keeps the part's own face. */
@Composable
internal fun kitLiquidFace(
    role: LiquidRole,
    source: KitLiquidSource?,
    shape: Shape,
    transform: () -> Float = NoTransform,
    join: KitLiquidJoinPart? = null,
    /** A dialog's panel: drawn with the sheet's role, tuned as a dialog (the Glass lab). */
    dialog: Boolean = false,
    /** No rim light and no edge highlight (the top pane of a page). */
    rimless: Boolean = false,
): Modifier? {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val live = io.github.eslamasabry.oc.ui.kit.foundation.LocalKitLivePicture.current?.backdrop
    val band = kitModalBand(role, dialog)
    val backdrop = remember(source, ground, picture, live, band) { source?.let { pageBackdrop(band, it, ground, picture, live) } }
    // A joined menu draws from its body's picture: it keeps the same band there (KitLiquidJoin.kt).
    if (join != null) SideEffect { join.band = band }
    return liquidFace(role, source, backdrop, shape, transform, join = join, dialog = dialog, rimless = rimless)
}

/**
 * The band of the page [role]'s pane keeps along its open edges here: the shipped one ([LiquidEdge.modalBand]) or the
 * Glass lab's; null for a pane that is not modal (a bar, the message box, a pill: they see the page whole).
 */
@Composable
internal fun kitModalBand(role: LiquidRole, dialog: Boolean = false): io.github.eslamasabry.oc.ui.design.effects.ModalBand? {
    if (!role.modal) return null
    val kind = io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind.of(role, dialog)
    val tuning = LocalKitGlassTuning.current ?: return LiquidEdge.modalBand(kind)
    val dark = if (kitGlassLook().smoked) true else LocalKitRoles.current.dark
    return io.github.eslamasabry.oc.ui.design.effects.GlassTuning.bandOf(kind, tuning.knobs(kind, dark))
}

/**
 * A menu's liquid face over the page (frost of it under the Frosted look). Over a sheet or a dialog it is a raised piece
 * of that pane's own glass ([kitMenuOverModalFace]).
 */
@Composable
internal fun kitLiquidMenuFace(shape: Shape, transform: () -> Float, join: KitLiquidJoinPart? = null): Modifier? {
    val page = LocalKitLiquidPage.current
    if (LocalKitLiquidOverModal.current) return kitMenuOverModalFace(page, shape, transform)
    return kitFrostedPanelFace(LiquidRole.Menu, page, shape) ?: kitLiquidFace(LiquidRole.Menu, page, shape, transform, join)
}

/**
 * A menu opened inside a sheet or a dialog (cut audit 2, the Servers row's menu: a flat white pane, another material than
 * the glass sheet it opened in). Glass is not stacked on glass, and the page's recording does not hold the sheet, so the
 * menu cannot show the sheet's rows (nor should it: a menu is modal while open, and nothing behind it may read as a
 * shape). It is a raised piece of the sheet's own glass: it sees what the sheet sees in its middle (the page's ground and
 * live background, never the page's content) under the sheet's own tint, then wears the menu's thin tint and 1 dp rim on
 * top, so it reads as the same material a step nearer, everywhere a menu opens over a sheet or a dialog. Under the Frosted
 * look it is frost of the same; where the sheet itself is not glass (another style, no blur) the menu keeps its own face.
 */
@Composable
private fun kitMenuOverModalFace(page: KitLiquidSource?, shape: Shape, transform: () -> Float): Modifier? {
    page ?: return null
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val live = io.github.eslamasabry.oc.ui.kit.foundation.LocalKitLivePicture.current?.backdrop
    val sheetFlip = liquidFlipFor(page, LiquidRole.Sheet) ?: remember { LiquidFlip() }
    val sheet = kitLiquidPaneSpec(LiquidRole.Sheet, sheetFlip, chosen = false, keepRim = false)
    val sheetNow by rememberUpdatedState(sheet)
    val backdrop =
        remember(page, ground, picture, live) {
            overModalBackdrop(liquidSourceBackdrop(page, ground, picture, live)) { tintNow(sheetNow) }
        }
    return kitFrostedPanelFace(LiquidRole.Menu, page, shape, backdrop = backdrop) ?: liquidFace(LiquidRole.Menu, page, backdrop, shape, transform)
}

/**
 * What a menu over a sheet sees ([kitMenuOverModalFace]): [inner]'s picture alone (its ground and live background, none of
 * the page's content), under the sheet's tint ([sheetTint], read while drawing: it cross-fades with the sheet's ink flip).
 */
internal fun overModalBackdrop(inner: KitLiquidBackdrop, sheetTint: () -> Color): KitLiquidBackdrop =
    KitLiquidBackdrop(inner.version) { c ->
        inner.version()
        inner.layers?.under?.invoke(this, c)
        val t = sheetTint()
        if (t.alpha > 0f) {
            val w = maxOf(size.width, c.size.width.toFloat())
            val h = maxOf(size.height, c.size.height.toFloat())
            drawRect(t, Offset(-w, -h), Size(w * 3f, h * 3f))
        }
    }

/** A sheet's liquid face over the page (frost of it under the Frosted look). */
@Composable
internal fun kitLiquidSheetFace(shape: Shape, transform: () -> Float, dialog: Boolean = false): Modifier? {
    val page = LocalKitLiquidPage.current
    return kitFrostedPanelFace(LiquidRole.Sheet, page, shape, dialog) ?: kitLiquidFace(LiquidRole.Sheet, page, shape, transform, dialog = dialog)
}

/**
 * [role]'s panel over the page under the Frosted look: the page blurred by the common frost ([kitFrostPane], so it
 * needs no runtime shaders) under the face the look's liquid pane would wear, its rim kept, its words on it. Null
 * outside that look, where the panel keeps its liquid face or its own. It takes no part in the window's liquid budget:
 * it has no lens.
 */
@Composable
private fun kitFrostedPanelFace(role: LiquidRole, source: KitLiquidSource?, shape: Shape, dialog: Boolean = false, backdrop: KitLiquidBackdrop? = null): Modifier? {
    val on = source != null && shape is CornerBasedShape && kitFrostedPanelsOn()
    // The page records only while a pane uses it.
    DisposableEffect(source, on) {
        if (on) source!!.users++
        onDispose { if (on) source!!.users-- }
    }
    if (!on) return null
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val live = io.github.eslamasabry.oc.ui.kit.foundation.LocalKitLivePicture.current?.backdrop
    val band = kitModalBand(role, dialog)
    val own = remember(source, ground, picture, live, band) { pageBackdrop(band, source!!, ground, picture, live) }
    val seen = backdrop ?: own
    val flip = liquidFlipFor(source, role)
    val spec = kitLiquidPaneSpec(role, flip ?: remember { LiquidFlip() }, chosen = false, keepRim = true, dialog = dialog)
    return Modifier.kitBackdropRimLight(shape)
        .kitFrostPane(spec, seen, shape as CornerBasedShape, spec.frostBlurDp, kitGlassLook().spec.frostSaturation)
        .kitGlassStackRoot()
        .kitLiquidPlateHost(kitLiquidWords(seen, flip))
        .testTag("kit-frosted-panel")
}

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
private fun liquidFace(
    role: LiquidRole,
    source: Any?,
    backdrop: KitLiquidBackdrop?,
    shape: Shape,
    transform: () -> Float,
    chosen: Boolean = false,
    join: KitLiquidJoinPart? = null,
    dialog: Boolean = false,
    rimless: Boolean = false,
): Modifier? {
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
    // Joined glass (KitLiquidJoin.kt): a pane that is part of a body or a drop says whether it holds a granted liquid
    // face; it is drawn joined while its partner holds one too and the platform runs the join shader.
    val platform = LocalKitPlatform.current
    val joinable = join != null && asking && granted && join.owner.programs(platform) != null
    if (join != null) {
        DisposableEffect(join, joinable) {
            join.granted = joinable
            onDispose { join.granted = false }
        }
    }
    if (!asking || backdrop == null) return null
    // Beyond the window's budget a part is frost of the same shape through the same glass (the lens let go, the rim
    // kept), never its solid face: nothing changes shape or flickers as a menu or sheet opens (owner 2026-11-08).
    val face = if (granted) transform else HeldFrost
    val joined = joinable && join!!.owner.partnerOf(join) != null
    join?.composedJoined = joined
    return Modifier.liquidPane(role, backdrop, shape as CornerBasedShape, face, liquidFlipFor(source, role), chosen, keepRim = !granted, join = join, joined = joined, dialog = dialog, rimless = rimless)
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
    join: KitLiquidJoinPart? = null,
    joined: Boolean = false,
    dialog: Boolean = false,
    rimless: Boolean = false,
): Modifier {
    val own = remember { LiquidFlip() }
    val flip = shared ?: own
    val whole = kitLiquidPaneSpec(role, flip, chosen, keepRim, dialog = dialog)
    // Owner 2026-11-14: "Get rid of the border around the top conversation area." A rimless pane keeps its glass and
    // its bend; nothing is drawn along its outline.
    val spec = if (rimless) remember(whole) { whole.copy(rim = Color.Transparent, specular = Color.Transparent) } else whole
    val platform = LocalKitPlatform.current
    val words = kitLiquidWords(backdrop, flip)
    if (join != null) SideEffect {
        join.spec = spec
        join.backdrop = backdrop
    }
    if (joined && join != null) {
        // One body with its partner: the joined pane draws this side (no clip to its own shape, no rim of its own).
        return this.kitLiquidJoinedPane(join.owner, join, spec, backdrop).kitGlassStackRoot().kitLiquidPlateHost(words)
    }
    val latest by rememberUpdatedState(transform)
    // The rim catches the live picture's light over the whole pane (glass-live): drawn after it, so it lies on top.
    return (if (rimless) this else this.kitBackdropRimLight(shape)).then(platform.liquidPane(spec, backdrop, shape) { latest() }).kitGlassStackRoot().kitLiquidPlateHost(words)
}

/** [role]'s liquid pane as the look here draws it: its face measured for its words, its recipe, rim and gloss. */
@Composable
internal fun kitLiquidPaneSpec(role: LiquidRole, flip: LiquidFlip, chosen: Boolean, keepRim: Boolean, swell: Boolean = false, dialog: Boolean = false): KitLiquidPaneSpec {
    val roles = LocalKitRoles.current
    val platform = LocalKitPlatform.current
    val appearance = LocalKitAppearance.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // What the page's live background can put behind the content (glass-live): the face is thickened for that look
    // alone, not for every picture the app can draw, so the motion behind it shows through.
    val picture = kitLivePictureSpots()
    val look = kitGlassLook()
    val spec =
        remember(roles, role, rtl, appearance.pack, appearance.accent, flip, chosen, keepRim, swell, picture, look, platform.liquidResolution) {
            // The face carries its words itself (no plate): its tint is thickened for the theme's ink over the content
            // that ink lies over; over content of the other brightness the words flip to the other theme's ink and the
            // face to the other theme's face, which carries that ink there.
            val other = ThemeResolver.roles(appearance.pack, appearance.accent, !roles.dark, saver = false, dynamic = null)
            // Smoked (glass looks D6): the dark theme's face in both modes, measured over the page it floats over; it
            // never flips (smokedInk), so its other face is itself.
            val smoke = if (look.smoked) (if (roles.dark) roles else other) else null
            val face =
                if (smoke != null) LiquidEdge.face(smoke, role, picture = picture, look = look, page = roles)
                else LiquidEdge.face(roles, role, picture = picture, look = look)
            // The other theme's face (over content of the other brightness) keeps the whole measure.
            val otherFace = if (smoke != null) face else LiquidEdge.face(other, role, look = look)
            // A chosen part's glass leans to the accent and holds a little more (GlassSpec's chosen tint and lift).
            fun lean(c: Color, dark: Boolean): Color {
                if (!chosen) return c
                val theme = if (dark == roles.dark) roles else other
                val opaque = io.github.eslamasabry.oc.ui.design.color.Argb(c.copy(alpha = 1f).toArgb())
                return Color(GlassSpec.chosenTintOf(theme, opaque).v).copy(alpha = minOf(1f, c.alpha + GlassSpec.CHOSEN_LIFT))
            }
            val faceDark = smoke?.dark ?: roles.dark
            val fill = lean(face.fill?.let { Color(it.v) } ?: Color.Transparent, faceDark)
            // The edge dock's grown end is the menu's old glass (6 dp dark, 3 light), not today's menu.
            val recipe = if (swell && role == LiquidRole.Menu) LiquidEdge.swellRecipe(faceDark, look) else LiquidEdge.recipe(role, faceDark, look)
            val rim = LiquidRim.Light
            KitLiquidPaneSpec(
                recipe = recipe,
                fill = fill,
                fillStart = face.fillStart?.let { lean(Color(it.v), faceDark) } ?: fill,
                rtl = rtl,
                // Light glass: a white rim vanishes on a white page, so its edge is a faint dark line (the glass's own
                // thickness seen edge-on); dark glass keeps the added light rim.
                rim = if (faceDark) rim.copy(alpha = look.spec.liquidRimDark) else Color.Black.copy(alpha = LIGHT_EDGE_ALPHA),
                rimAdds = faceDark,
                rimDp = RIM_LIGHT_DP,
                shadow = Color.Black.copy(alpha = recipe.shadowAlpha),
                frostBlurDp = GlassSpec.FLOAT_FROST,
                sheen = Color(face.sheen.v),
                // No painted glint: the edge's own light (the rim highlight) is the shine.
                specular = Color.Transparent,
                flipFill = if (smoke != null) fill else otherFace.fill?.let { lean(Color(it.v), !roles.dark) } ?: fill,
                flipFillStart = if (smoke != null) face.fillStart?.let { lean(Color(it.v), faceDark) } ?: fill else (otherFace.fillStart ?: otherFace.fill)?.let { lean(Color(it.v), !roles.dark) } ?: fill,
                flipSheen = Color(otherFace.sheen.v),
                flip = flip,
                keepRim = keepRim,
                glintShare = look.spec.glintShare,
                resolution = liquidResolutionOf(recipe, platform.liquidResolution),
            )
        }
    // The Glass lab's tuning (null: the shipped glass). The dock's grown swell keeps the menu's old glass.
    val tuning = LocalKitGlassTuning.current ?: return spec
    if (swell) return spec
    val kind = io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind.of(role, dialog)
    val dark = if (kitGlassLook().smoked) true else roles.dark
    return remember(spec, tuning, kind, dark, platform.liquidResolution) {
        spec.tuned(tuning.knobs(kind, dark), io.github.eslamasabry.oc.ui.design.effects.GlassTuning.shipped(kind, dark), platform.liquidResolution)
    }
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
    val smoked = kitGlassLook().smoked
    return remember(roles, backdrop, appearance.pack, appearance.accent, flip, smoked) {
        val other = ThemeResolver.roles(appearance.pack, appearance.accent, !roles.dark, saver = false, dynamic = null)
        KitLiquidWords(
            flipInk = Color(other.text1.v),
            themeInk = LiquidInkRule.of(roles.dark),
            backdrop = backdrop,
            flip = flip ?: LiquidFlip(),
            // Smoked: the dark theme's ink in both modes, whatever lies behind (its face is measured for it).
            fixedFlip = if (smoked) (if (roles.dark) 0f else 1f) else null,
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
    /** A flip that never samples (Smoked: 0 in a dark theme, 1 in a light one); null samples the content. */
    val fixedFlip: Float? = null,
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
    private var sampler: KitBackdropSampler? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun onAttach() {
        ink = words.themeInk
        if (words.fixedFlip != null) return
        val backdrop = words.backdrop ?: return
        val platform = currentValueOf(LocalKitPlatform)
        // One sampler for the pane's life (smoothness 2026-11-09): a sample reuses one small target and never holds the
        // UI thread on the GPU. Without one (desktop), a snapshot of a small layer is read back as before.
        val own = platform.backdropSampler().also { sampler = it }
        coroutineScope.launch {
            var seen = Int.MIN_VALUE
            while (isActive) {
                delay(LiquidInkRule.SAMPLE_MS)
                // Only while the content moves: a source that has not recorded again shows nothing new.
                val v = backdrop.version()
                if (v == seen) continue
                val c = coordinates?.takeIf { it.isAttached } ?: continue
                val w = (c.size.width / SAMPLE_SCALE).coerceAtLeast(1)
                val h = (c.size.height / SAMPLE_SCALE).coerceAtLeast(1)
                val small: DrawScope.() -> Unit = { scale(1f / SAMPLE_SCALE, 1f / SAMPLE_SCALE, Offset.Zero) { backdrop.draw(this, c) } }
                val pixels =
                    if (own != null) {
                        own.sample(w, h, requireDensity(), small) ?: continue
                    } else {
                        val layer = sample ?: requireGraphicsContext().createGraphicsLayer().also { sample = it }
                        layer.kitRecord(requireDensity(), LayoutDirection.Ltr, IntSize(w, h), small)
                        val read = IntArray(w * h)
                        // A sample the platform could not read back (empty or clear) says nothing about the content.
                        if (!platform.readPixels(layer.toImageBitmap(), read, w, h)) continue
                        read
                    }
                // Counted as seen only once a sample came back: a sample the sampler skipped (another pane's was in
                // flight, or the GPU is slow) is asked for again on the next tick, not only when the content next moves.
                seen = v
                if (pixels.size < w * h || pixels.count { (it ushr 24) > 0x80 } < pixels.size / 2) continue
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
        sampler?.release()
        sampler = null
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
        val words = h?.words
        val f = words?.fixedFlip ?: h?.flip?.value ?: 0f
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

/** Light glass's dark edge line (see kitLiquidPaneSpec). */
internal const val LIGHT_EDGE_ALPHA: Float = .10f

/**
 * The rim light's strength over a dark theme (added light) and a light one (laid over): soft white light (owner
 * 2026-11-08: glass edges are a neutral white light, not accent-tinted; an HCT-pulled yellow accent read as blue-teal),
 * low in a light theme where white on milk needs little.
 */
internal val RIM_ALPHA_DARK: Float get() = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.liquidRimDark
internal val RIM_ALPHA_LIGHT: Float get() = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.liquidRimLight

/**
 * The specular light (the rim light's colour): a bright segment round the top-start corner and a thin glint line just
 * inside the top edge, as Glass's in-flow panes draw it (`drawGlassSpecular`). Restrained, never plastic.
 */
internal val SPECULAR_ALPHA_DARK: Float get() = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.liquidSpecularDark
internal val SPECULAR_ALPHA_LIGHT: Float get() = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.liquidSpecularLight

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
internal fun BoxScope.KitLiquidBarPane(face: Modifier, join: KitLiquidJoinBody? = null) {
    Box(
        Modifier.matchParentSize()
            .padding(horizontal = LIQUID_BAR_INSET_X.dp, vertical = LIQUID_BAR_INSET_Y.dp)
            .kitLiquidJoinRest(join, LiquidBarShape)
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
    // Sized by the pane as well as by this scope: the words' ink sampler draws the backdrop scaled down into a target an
    // eighth of the pane's size, where this scope's size is that small target's, so a ground sized by it alone covered a
    // corner of the sample. Content the page cleared (the transcript ended above the jump pill) then read as see-through,
    // the sample was dropped as saying nothing, and the ink kept a flip taken over the words that lay there before.
    val w = maxOf(size.width, at.size.width.toFloat())
    val h = maxOf(size.height, at.size.height.toFloat())
    drawRect(ground, Offset(-w, -h), Size(w * 3f, h * 3f))
    val bd = picture ?: return
    // The pane draws again on every new frame of the picture or another look (glass-live: never a stale picture).
    bd.frame()
    val layer = bd.layer ?: return
    if (!bd.painted || bd.root !== at.findRootCoordinates()) return
    val shift = bd.origin - at.positionInRoot()
    translate(shift.x, shift.y) { kitDrawLayer(layer) }
}

/** A feature's [KitFrostState] (its content recorded by [kitFrostSource]) seen from a pane in the same window. */
internal fun frostStateBackdrop(state: KitFrostState, ground: Color, picture: KitGlassBackdrop?): KitLiquidBackdrop {
    val page: DrawScope.(LayoutCoordinates) -> Unit = { c ->
        val shift = state.sourceOrigin - c.positionInRoot()
        translate(shift.x, shift.y) { kitDrawLayer(state.layer) }
    }
    return KitLiquidBackdrop({ state.version }) { c ->
        // Read here so the pane draws again when the content behind it records again (a scroll under the bar).
        state.version
        drawGroundUnder(ground, picture, c)
        page(this, c)
    }.also { it.layers = KitBackdropLayers(under = { c -> drawGroundUnder(ground, picture, c) }, page = page, pictured = { c -> picture.paintedFor(c) }) }
}

/** Whether this picture is painted and lies in [c]'s window, so a pane there can draw it under the page. */
private fun KitGlassBackdrop?.paintedFor(c: LayoutCoordinates): Boolean =
    this != null && painted && layer != null && root === c.findRootCoordinates()

/**
 * A [KitLiquidSource] seen from a pane: in its own window by the layout's own mapping, from a popup's window (a menu) by
 * screen position, which both windows share. Its parts ([KitBackdropLayers]): the page's ground and its picture (the
 * live background, recorded or the window's), and the page's content.
 */
private fun liquidSourceBackdrop(source: KitLiquidSource, ground: Color, picture: KitGlassBackdrop?, live: KitGlassBackdrop? = null): KitLiquidBackdrop {
    val page: DrawScope.(LayoutCoordinates) -> Unit = page@{ c ->
        val s = source.coordinates?.takeIf { it.isAttached } ?: return@page
        val shift =
            if (s.findRootCoordinates() === c.findRootCoordinates()) c.localPositionOf(s, Offset.Zero)
            else s.positionOnScreen() - c.positionOnScreen()
        translate(shift.x, shift.y) { kitDrawLayer(source.layer) }
    }
    val bg = picture ?: live
    return KitLiquidBackdrop({ source.version }) { c ->
        source.version
        // A sheet or a menu outside the page's live background still follows the picture the page shows.
        if (picture == null) live?.frame()
        drawGroundUnder(ground, picture, c)
        page(this, c)
    }.also { it.layers = KitBackdropLayers(under = { c -> drawGroundUnder(ground, bg, c) }, page = page, pictured = { c -> bg.paintedFor(c) }) }
}

/** The page as a pane with [band] sees it: a modal pane only along its open edges ([kitModalView]), any other whole. */
private fun pageBackdrop(band: io.github.eslamasabry.oc.ui.design.effects.ModalBand?, source: KitLiquidSource, ground: Color, picture: KitGlassBackdrop?, live: KitGlassBackdrop?): KitLiquidBackdrop {
    val page = liquidSourceBackdrop(source, ground, picture, live)
    return if (band == null) page else kitModalView(page, { band })
}

/**
 * The page under a modal sheet or dialog (cut audit 1, LiquidEdge.MODAL_PAGE_KEEP_DP): its ground and its picture (the
 * live background) everywhere, the page's own content only along the sheet's edges that border the visible page, as far
 * in as the rim bends it and fading out past that ([LiquidEdge.modalPageKept]). So no control, card or word of the page
 * reads through the sheet's middle (the green "Allow once" ghost, the send button's smear, the card's dark plate), while
 * the background's colour passes and the top rim still bends the page. Where the page shows no recorded picture (the
 * live background off or not painted yet) the page is the only picture there is, and the sheet shows it as before.
 */
internal fun modalSourceBackdrop(source: KitLiquidSource, ground: Color, picture: KitGlassBackdrop?, live: KitGlassBackdrop?): KitLiquidBackdrop =
    kitModalView(liquidSourceBackdrop(source, ground, picture, live), { LiquidEdge.SHEET_BAND })

/**
 * [inner] as a modal pane over it sees it (cut audits 1 and 2: the page's controls, chips and icons read through sheets,
 * menus and the dock's list as blobs). Its picture ([KitBackdropLayers.under]) whole; its content only in [band] along
 * the pane's edges that border the visible page, scaled by how open each edge is (an edge on the window's edge shows
 * none of it; [closed] names, as [ModalSides] bits, edges that face glass the pane is joined to and so show none either)
 * and, everywhere else, at [base] (read while drawing: 0 for a pane that is all modal; the dock's list eases it from 1).
 * [inner] without parts, or without a picture here, is drawn whole, as before.
 */
internal fun kitModalView(
    inner: KitLiquidBackdrop,
    band: () -> io.github.eslamasabry.oc.ui.design.effects.ModalBand,
    closed: (LayoutCoordinates) -> Int = { 0 },
    base: () -> Float = { 0f },
    box: ((LayoutCoordinates) -> androidx.compose.ui.geometry.Rect)? = null,
): KitLiquidBackdrop {
    val parts = inner.layers ?: return inner
    return KitLiquidBackdrop(inner.version) { c ->
        inner.version()
        if (!parts.pictured(c)) {
            inner.draw(this, c)
            return@KitLiquidBackdrop
        }
        parts.under(this, c)
        val pane = box?.invoke(c) ?: androidx.compose.ui.geometry.Rect(0f, 0f, c.size.width.toFloat(), c.size.height.toFloat())
        val shut = closed(c)
        val open = ModalEdges.of(c, density, pane).open()
        for (side in 0 until 4) if (shut and (1 shl side) != 0) open[side] = 0f
        drawPageAtOpenEdges(pane, open, band(), base()) { parts.page(this, c) }
    }
}

/** Bits for [kitModalView]'s closed edges, in [ModalEdges.open]'s order. */
internal object ModalSides {
    const val TOP: Int = 1
    const val BOTTOM: Int = 2
    const val LEFT: Int = 4
    const val RIGHT: Int = 8
}

/** How far each edge of a pane lies from the window's edge, dp (negative: past it). */
internal data class ModalEdges(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** How open each edge is (top, bottom, left, right; 0 on the window's edge, 1 from MODAL_PAGE_OPEN_DP in). */
    fun open(): FloatArray = floatArrayOf(LiquidEdge.modalPageKept(0f, top), LiquidEdge.modalPageKept(0f, bottom), LiquidEdge.modalPageKept(0f, left), LiquidEdge.modalPageKept(0f, right))

    companion object {
        fun of(c: LayoutCoordinates, density: Float): ModalEdges =
            of(c, density, androidx.compose.ui.geometry.Rect(0f, 0f, c.size.width.toFloat(), c.size.height.toFloat()))

        /** [box] (in [c]'s own px) as it lies in the window. */
        fun of(c: LayoutCoordinates, density: Float, box: androidx.compose.ui.geometry.Rect): ModalEdges {
            val root = c.findRootCoordinates().size
            val at = c.positionInRoot()
            val l = at.x + box.left
            val t = at.y + box.top
            return ModalEdges(l / density, t / density, (root.width - l - box.width) / density, (root.height - t - box.height) / density)
        }
    }
}

/**
 * Draws [page] (in pane-local px) kept only along the edges of [box] (pane-local px) that [open] says are open (top,
 * bottom, left, right; 0-1), [band] deep and fading out past it, and at [base] everywhere (0-1): the page in a layer of
 * its own, cut by a mask layer of the bands (DstIn), each band reaching out past the box. Nothing is drawn when no edge
 * is open and [base] is 0. The bands' ends are whole pixels (a DstIn gradient at fractional pixels once crashed an
 * emulator's colour buffer).
 */
private fun DrawScope.drawPageAtOpenEdges(box: androidx.compose.ui.geometry.Rect, open: FloatArray, band: io.github.eslamasabry.oc.ui.design.effects.ModalBand, base: Float, page: DrawScope.() -> Unit) {
    val keep = band.keepDp.coerceAtLeast(0f).dp.toPx()
    val fade = band.fadeDp.coerceAtLeast(0f).dp.toPx()
    val b = base.coerceIn(0f, 1f)
    if (b <= 0f && open.all { it <= 0f }) return
    val out = kotlin.math.ceil(keep + fade + maxOf(box.width, box.height)).toFloat()
    val bounds = androidx.compose.ui.geometry.Rect(box.left - out, box.top - out, box.right + out, box.bottom + out)
    drawIntoCanvas { it.saveLayer(bounds, Paint()) }
    page(this)
    drawIntoCanvas { it.saveLayer(bounds, Paint().apply { blendMode = BlendMode.DstIn }) }
    if (b >= 1f) {
        drawRect(Color.Black, bounds.topLeft, bounds.size)
    } else {
        if (b > 0f) drawRect(Color.Black.copy(alpha = b), bounds.topLeft, bounds.size)
        val l = box.left
        val t = box.top
        val r = box.right
        val btm = box.bottom
        // Whole pixels for the gradient's ends and the rects' edges (DstIn at fractional pixels crashed an emulator).
        val top0 = kotlin.math.floor(t + keep)
        val top1 = maxOf(kotlin.math.ceil(t + keep + fade), top0 + 1f)
        val bot0 = kotlin.math.ceil(btm - keep)
        val bot1 = minOf(kotlin.math.floor(btm - keep - fade), bot0 - 1f)
        val left0 = kotlin.math.floor(l + keep)
        val left1 = maxOf(kotlin.math.ceil(l + keep + fade), left0 + 1f)
        val right0 = kotlin.math.ceil(r - keep)
        val right1 = minOf(kotlin.math.floor(r - keep - fade), right0 - 1f)
        val x0 = kotlin.math.floor(bounds.left)
        val y0 = kotlin.math.floor(bounds.top)
        val x1 = kotlin.math.ceil(bounds.right)
        val y1 = kotlin.math.ceil(bounds.bottom)
        val off = Color.Transparent
        fun on(a: Float) = Color.Black.copy(alpha = a)
        if (open[0] > 0f) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to on(open[0]), 1f to off, startY = top0, endY = top1), Offset(x0, y0), Size(x1 - x0, top1 - y0))
        if (open[1] > 0f) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to off, 1f to on(open[1]), startY = bot1, endY = bot0), Offset(x0, bot1), Size(x1 - x0, y1 - bot1))
        if (open[2] > 0f) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to on(open[2]), 1f to off, startX = left0, endX = left1), Offset(x0, y0), Size(left1 - x0, y1 - y0))
        if (open[3] > 0f) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to off, 1f to on(open[3]), startX = right1, endX = right0), Offset(right1, y0), Size(x1 - right1, y1 - y0))
        // A closed edge (on the window's edge, or joined to glass) takes the page away again in its own band, across the
        // open edges' bands too (wave-one check, c-5-details-topright: a hard white wedge in the Details sheet's top-right
        // corner). The lens's bend turns at a corner's diagonal, from reading down into the top band to reading in from
        // the side; with the top band running into the corner, the side's half read the page (a bubble) while the top's
        // read the background, so the diagonal showed as a hard edge. With nothing of the page near a closed side, both
        // halves read the background and the corner follows the pane's radius.
        val k = 1f - b
        fun cut(a: Float) = Color.Black.copy(alpha = a)
        val cl = (1f - open[2]) * k
        val cr = (1f - open[3]) * k
        val ct = (1f - open[0]) * k
        val cb = (1f - open[1]) * k
        val dstOut = androidx.compose.ui.graphics.BlendMode.DstOut
        if (ct > 0f) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to cut(ct), 1f to off, startY = top0, endY = top1), Offset(x0, y0), Size(x1 - x0, top1 - y0), blendMode = dstOut)
        if (cb > 0f) drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to off, 1f to cut(cb), startY = bot1, endY = bot0), Offset(x0, bot1), Size(x1 - x0, y1 - bot1), blendMode = dstOut)
        if (cl > 0f) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to cut(cl), 1f to off, startX = left0, endX = left1), Offset(x0, y0), Size(left1 - x0, y1 - y0), blendMode = dstOut)
        if (cr > 0f) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(0f to off, 1f to cut(cr), startX = right1, endX = right0), Offset(right1, y0), Size(x1 - right1, y1 - y0), blendMode = dstOut)
    }
    drawIntoCanvas { it.restore() }
    drawIntoCanvas { it.restore() }
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
            kitRecord(source.layer) { this@draw.drawContent() }
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { source.version++ }
            kitDrawLayer(source.layer)
        } else {
            drawContent()
        }
    }
}
