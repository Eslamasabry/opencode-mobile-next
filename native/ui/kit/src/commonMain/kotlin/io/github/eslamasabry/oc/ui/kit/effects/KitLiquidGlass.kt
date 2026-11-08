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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.ui.design.effects.LiquidConditions
import io.github.eslamasabry.oc.ui.design.effects.LiquidGlass
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassStackRoot
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/*
 * Liquid glass under the Glass style (owner, 2026-11-08), drawn by Kyant0/AndroidLiquidGlass ("backdrop", Apache-2.0).
 * The floating parts (the bars, the composer, sheets, menus, the floating + and pills) are panes of glass over the
 * content behind them: the content, recorded once per frame by a source, is pressed together in contrast, blurred a
 * little and bent at the rim with a little dispersion, then laid under the theme's tint (thick enough that the words
 * keep 4.5:1 over any content: [LiquidGlass.tintAlpha]) and a light rim.
 *
 * Every other style, and Glass wherever liquid glass is held ([LiquidGlass.holdOf]: Saver, reduced motion, high
 * contrast, Android below 13, frost off), draws exactly what it drew before: every entry point here returns null or the
 * modifier unchanged, and the caller keeps its own face.
 *
 * Sources: a feature's [KitFrostState] (what scrolls behind the chat's chrome, a list under its floating +) through
 * [kitLiquidBackdrop], or a kit-owned [KitLiquidSource] (the page under a sheet or a menu, a page under its bar) that
 * records only while a liquid pane uses it, so nothing is recorded for glass nobody shows. A pane never draws a source
 * it lies inside: the sources are the siblings under the panes, never their ancestors.
 */

/** Liquid glass's conditions as the kit reads them now. */
@Composable
internal fun kitLiquidConditions(): LiquidConditions {
    val platform = LocalKitPlatform.current
    val signals by platform.signals.collectAsState()
    val appearance = LocalKitAppearance.current
    return LiquidConditions(
        style = LocalKitStyle.current.style,
        runtimeShaders = platform.liquidGlass,
        saver = appearance.saver,
        reducedMotion = LocalKitMotion.current.reduced,
        highContrast = signals.highContrast,
        frostOff = appearance.frost == FrostStrength.Off,
    )
}

/** Whether the floating parts are liquid glass here and now. */
@Composable
public fun kitLiquidGlassOn(): Boolean = LiquidGlass.isLiquid(kitLiquidConditions())

/**
 * What a kit part records for liquid panes over it (the page under a sheet, a page's body under its bar). It records
 * only while a pane uses it ([users] > 0); at rest, and under every other style, it only draws its content.
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
 * A feature's scroll content recorded for the liquid panes over it (the conversations list under its floating +):
 * [kitFrostSource] while liquid glass is on, nothing otherwise, so every other style draws exactly as before.
 */
@Composable
public fun Modifier.kitGlassSource(state: KitFrostState): Modifier = if (kitLiquidGlassOn()) kitFrostSource(state) else this

/** The page under the window's sheets and menus (provided by `KitSheetHost`). */
internal val LocalKitLiquidPage: ProvidableCompositionLocal<KitLiquidSource?> = staticCompositionLocalOf { null }

/** What scrolls behind a page's overlaid bar and bottom block (provided by `KitScreen` to those two slots only). */
public val LocalKitLiquidChrome: ProvidableCompositionLocal<Backdrop?> = staticCompositionLocalOf { null }

/** A liquid backdrop over [state], or null where liquid glass is held (or no source is given). */
@Composable
public fun kitLiquidBackdrop(state: KitFrostState?): Backdrop? {
    if (state == null || !kitLiquidGlassOn()) return null
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    return remember(state, ground, picture) { FrostStateBackdrop(state, ground, picture) }
}

/** A liquid backdrop over [source] that keeps [source] recording while the caller is composed; null where held. */
@Composable
internal fun kitLiquidBackdrop(source: KitLiquidSource?): Backdrop? {
    val on = source != null && kitLiquidGlassOn()
    if (source != null) {
        DisposableEffect(source, on) {
            if (on) source.users++
            onDispose { if (on) source.users-- }
        }
    }
    if (!on || source == null) return null
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    return remember(source, ground, picture) { LiquidSourceBackdrop(source, ground, picture) }
}

/** The page under this sheet or menu as a liquid backdrop, or null (held, or no host records one). */
@Composable
internal fun kitLiquidPageBackdrop(): Backdrop? = kitLiquidBackdrop(LocalKitLiquidPage.current)

/**
 * True inside a sheet or a dialog (set by `KitSheetHost`). The page source does not hold the sheet itself, so a menu
 * opened there would show the page through the sheet: it keeps its frosted Glass face instead. Glass is not stacked
 * on glass.
 */
internal val LocalKitLiquidOverModal: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** A menu's liquid backdrop: the page, unless the menu opens over a sheet or a dialog. */
@Composable
internal fun kitLiquidMenuBackdrop(): Backdrop? = kitLiquidBackdrop(LocalKitLiquidPage.current.takeIf { !LocalKitLiquidOverModal.current })

/**
 * [role]'s liquid pane over [backdrop], shaped [shape]: the backdrop pressed together, blurred, bent at the rim with a
 * little dispersion, under the theme's tint and a light rim, with the role's soft shadow. Glass panes inside it sit on
 * it ([kitGlassStackRoot]) instead of frosting again. The modifier clips its content to [shape].
 */
@Composable
public fun Modifier.kitLiquidPane(role: LiquidRole, backdrop: Backdrop, shape: Shape): Modifier {
    val roles = LocalKitRoles.current
    val recipe = remember(role, roles.dark) { LiquidGlass.recipe(role, roles.dark) }
    val tint = remember(roles, role, recipe) {
        val alpha = LiquidGlass.tintAlpha(roles, role, recipe)
        Color(LiquidGlass.tintOf(roles, role).withAlpha(alpha).v)
    }
    val lensShape = remember(shape) { shape as? CornerBasedShape ?: RoundedCornerShape(0.dp) }
    val rim = Color.White.copy(alpha = recipe.rimAlpha)
    val shadow = Color.Black.copy(alpha = recipe.shadowAlpha)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { lensShape },
            effects = {
                colorControls(brightness = recipe.brightness, contrast = recipe.contrast, saturation = recipe.saturation)
                blur(recipe.blurDp.dp.toPx())
                // The lens bends at most half the pane's short side in from the rim (before the first layout the size
                // is not known yet; the effects run again once it is).
                val height = recipe.refractionHeightDp.dp.toPx()
                val half = if (size.isSpecified) size.minDimension / 2f else height
                lens(minOf(height, half), recipe.refractionAmountDp.dp.toPx(), depthEffect = recipe.depth, chromaticAberration = recipe.dispersion)
            },
            highlight = { Highlight(width = recipe.rimWidthDp.dp, style = HighlightStyle.Default(color = rim)) },
            shadow = if (recipe.shadowAlpha > 0f) ({ Shadow(radius = 24.dp, color = shadow) }) else null,
            onDrawSurface = { drawRect(tint) },
        )
        .kitGlassStackRoot()
}

/**
 * A bar's liquid pane, behind the bar's own content (call it first inside the bar's box): a floating capsule
 * [LIQUID_BAR_INSET_X] in from the bar's sides and [LIQUID_BAR_INSET_Y] from its top and bottom, its ends rounded by at
 * most [LIQUID_BAR_RADIUS]. The bar's content keeps its own place: only its ground becomes glass.
 */
@Composable
public fun BoxScope.KitLiquidBarPane(backdrop: Backdrop) {
    Box(
        Modifier.matchParentSize()
            .padding(horizontal = LIQUID_BAR_INSET_X.dp, vertical = LIQUID_BAR_INSET_Y.dp)
            .kitLiquidPane(LiquidRole.Bar, backdrop, RoundedCornerShape(LIQUID_BAR_RADIUS.dp))
            .testTag("kit-liquid-bar")
    )
}

/** How far a bar's capsule sits in from the bar's sides, in dp. */
public const val LIQUID_BAR_INSET_X: Int = 6

/** How far a bar's capsule sits in from the bar's top and bottom, in dp. */
public const val LIQUID_BAR_INSET_Y: Int = 4

/** The most a bar's capsule's ends round, in dp (a two-line bar is a rounded panel, not a lozenge). */
public const val LIQUID_BAR_RADIUS: Int = 26

/**
 * The edge where a page's content meets the overlaid bar ([top]) or bottom block under liquid glass: no band of ground,
 * only a soft fade of it towards the screen's edge, so what scrolls past the floating glass fades rather than cuts.
 */
@Composable
public fun Modifier.kitLiquidEdgeFade(top: Boolean): Modifier {
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    return drawBehind {
        val stops =
            if (top) arrayOf(0f to ground.copy(alpha = EDGE_FADE_ALPHA), 1f to ground.copy(alpha = 0f))
            else arrayOf(0f to ground.copy(alpha = 0f), 1f to ground.copy(alpha = EDGE_FADE_ALPHA))
        drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = size.height))
    }
}

/**
 * A page's body run up under its bar by [barPx] (the bar floats over it): measured that much taller and placed that
 * much higher, while it still takes only its own room in the page, so nothing below it moves.
 */
internal fun Modifier.kitRunUnderBar(barPx: Int): Modifier =
    layout { measurable, constraints ->
        if (barPx <= 0 || !constraints.hasBoundedHeight) {
            val p = measurable.measure(constraints)
            return@layout layout(p.width, p.height) { p.place(0, 0) }
        }
        val tall = constraints.maxHeight + barPx
        val p = measurable.measure(constraints.copy(minHeight = (constraints.minHeight + barPx).coerceAtMost(tall), maxHeight = tall))
        layout(p.width, (p.height - barPx).coerceAtLeast(0)) { p.place(0, -barPx) }
    }

/** How much of the ground the edge fade reaches at the screen's edge. */
internal const val EDGE_FADE_ALPHA: Float = .82f

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
private class FrostStateBackdrop(
    val state: KitFrostState,
    val ground: Color,
    val picture: KitGlassBackdrop?,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val c = coordinates?.takeIf { it.isAttached } ?: return
        drawGroundUnder(ground, picture, c)
        val shift = state.sourceOrigin - c.positionInRoot()
        translate(shift.x, shift.y) { drawLayer(state.layer) }
    }
}

/** A [KitLiquidSource] seen from a pane, in its window or in a popup's (a menu): placed by screen position there. */
private class LiquidSourceBackdrop(
    val source: KitLiquidSource,
    val ground: Color,
    val picture: KitGlassBackdrop?,
) : Backdrop {
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
