package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/*
 * Glass painting (order 34-glass; Dart `kit_surface_look.dart`, `kit_surface_paint.dart`, `glass/kit_glass.dart` at
 * GLASS_REF 1bbdc3f9c). Three pieces:
 *
 * 1. [KitGlassBackdrop] and [kitGlassBackdropSource]: the picture behind a page's panes (the painted aurora, drawn by
 *    `KitLiveBackground`), recorded once per frame into one layer. Only the picture is recorded, never the page's
 *    content, so a pane never draws itself.
 * 2. [kitSurfaceGlass]: the frost under a pane's face. Once per stack (Dart `_KitSurfaceScope.frosted`): a pane inside
 *    a pane that frosts already sits on that one and draws nothing here. A top pane draws the picture behind it
 *    through the platform blur (Android API 31+, `AndroidFrost`), clipped to its shape; where there is no picture, no
 *    blur (below API 31, desktop), reduced motion, or the pane is in another window (a popup), it draws the page's
 *    opaque ground instead. That is the solid fallback: the tint then lies on the plain ground, exactly as the pane
 *    looks over a page with no background, and nothing behind it shows through.
 * 3. [kitGlassFace]: the pane itself (`_KitSurfacePainter.paint`, `kit_surface_paint.dart:14-145`): the long shadow
 *    only outside the face, the tint with its lit top-start corner, the glow along the top edge and the soft rim.
 *
 * No glow round the pane, no moving shine, no colour shift: only what Flutter's Glass draws.
 */

/** The picture behind one page's glass panes. Made by `KitLiveBackground`; panes read it from [LocalKitGlassBackdrop]. */
@Stable
internal class KitGlassBackdrop {
    internal var layer: GraphicsLayer? = null
    internal var origin: Offset = Offset.Zero
    internal var root: LayoutCoordinates? = null

    /** A picture is being drawn (the live background is on and its plate has loaded). */
    internal var painted: Boolean by mutableStateOf(false)

    /** The picture's darkest and brightest spots under a pane's frost, for [io.github.eslamasabry.oc.ui.design.style.GlassFinish]. */
    internal var frostedSpots: List<Argb> by mutableStateOf(emptyList())
}

@Composable
internal fun rememberKitGlassBackdrop(): KitGlassBackdrop = remember { KitGlassBackdrop() }

/** The backdrop of the page being drawn, or null where no live background hosts the page. */
internal val LocalKitGlassBackdrop: ProvidableCompositionLocal<KitGlassBackdrop?> = staticCompositionLocalOf { null }

/** How much a frost lifts the saturation of the colour it blurs (1 keeps it): frosted glass glows with what is behind. */
internal const val FROST_SATURATION: Float = 1.7f // FG: lib/ui/kit/kit_surface_look.dart:101

/**
 * Draws [paint] (the picture only) and records it for the panes above ([KitGlassBackdrop]). It also bounds the panes'
 * "once per stack" rule: a pane over this source frosts on its own. The live background puts it on a node of its own
 * behind the page's content ([kitGlassBackdropBoundary] bounds the content), so a step of the picture re-records the
 * picture alone, never the page.
 */
internal fun Modifier.kitGlassBackdropSource(backdrop: KitGlassBackdrop, paint: DrawScope.() -> Unit): Modifier =
    this then BackdropSourceElement(backdrop, paint)

/**
 * The frost under a decorated surface's face (fills the lead stub; standard §12 seam). Only Glass draws one, only for a
 * decoration that asks ([SurfaceDecoration.frost] > 0, `kit_style_glass.dart:247`, `:306`); everything else returns
 * the modifier unchanged, so no other style changes. The blur's sigma is the decoration's own (Glass `paneFrost` 20,
 * `floatFrost` 26; `kit_style_glass.dart:96`, `:100`) with saturation [FROST_SATURATION].
 */
@Composable
internal fun Modifier.kitSurfaceGlass(decoration: SurfaceDecoration?): Modifier {
    if (decoration == null || decoration.frost <= 0f) return this
    if (LocalKitStyle.current.style != Style.Glass) return this
    val platform = LocalKitPlatform.current
    val signals by platform.signals.collectAsState()
    val reduced = LocalKitMotion.current.reduced
    val density = LocalDensity.current.density
    val backdrop = LocalKitGlassBackdrop.current
    val ground = LocalKitColors.current.ground
    // Reduced motion and high contrast hold the frost solid (Dart `KitFrost.heldBy`; high contrast already draws Solid).
    val held = reduced || signals.highContrast
    val sigma = decoration.frost
    val effect =
        remember(sigma, density, platform, held) {
            if (held) null else platform.frostEffect(FrostSpec(sigma, FROST_SATURATION, 0f), density)
        }
    return this then GlassFrostElement(if (effect == null) null else backdrop, effect, ground, decoration.shape())
}

/**
 * Marks a liquid glass pane (`KitLiquidGlass`) as the frosted pane of its stack: Glass panes inside it sit on it and draw
 * no frost of their own (Dart `_KitSurfaceScope.frosted`), as they do inside a frosted pane. It draws nothing itself.
 */
internal fun Modifier.kitGlassStackRoot(): Modifier =
    this then GlassFrostElement(null, null, Color.Transparent, androidx.compose.ui.graphics.RectangleShape)

/** Whether a Glass pane over [backdrop] blurs the picture behind it here (for the legibility measure). */
@Composable
internal fun kitGlassBlurs(backdrop: KitGlassBackdrop?): Boolean {
    if (backdrop == null || !backdrop.painted) return false
    return kitGlassFrostAvailable()
}

/**
 * Whether a Glass pane can blur a picture here at all: the platform has the blur (Android 12+) and neither reduced motion
 * nor high contrast holds it. The live background records a shader look for the panes only then.
 */
@Composable
internal fun kitGlassFrostAvailable(): Boolean {
    val platform = LocalKitPlatform.current
    val signals by platform.signals.collectAsState()
    if (LocalKitMotion.current.reduced || signals.highContrast) return false
    val density = LocalDensity.current.density
    return remember(platform, density) { platform.frostEffect(FrostSpec(1f, FROST_SATURATION, 0f), density) != null }
}

/**
 * Bounds the panes' "once per stack" rule at a live background (the picture is recorded on a node of its own beside the
 * content, [kitGlassBackdropSource]): a pane over it frosts on its own. Draws nothing.
 */
internal fun Modifier.kitGlassBackdropBoundary(): Modifier = this then BackdropBoundaryElement

private object BackdropBoundaryElement : ModifierNodeElement<BackdropBoundaryNode>() {
    override fun create(): BackdropBoundaryNode = BackdropBoundaryNode()

    override fun update(node: BackdropBoundaryNode) {}

    override fun InspectorInfo.inspectableProperties() {
        name = "kitGlassBackdropBoundary"
    }

    override fun hashCode(): Int = 0x47_42_42

    override fun equals(other: Any?): Boolean = other === this
}

private class BackdropBoundaryNode : Modifier.Node(), TraversableNode {
    override val traverseKey: Any get() = GlassTraverseKey
}

/**
 * A sheet's, a dialog's or a menu's own panel under Glass: its frosted pane (role sheet, Dart `KitSurfaceRole.sheet`,
 * `kit_style_glass.dart:62`, `:100`), or null under every other style, where the part keeps its own fill exactly as
 * before.
 */
@Composable
internal fun kitGlassSheet(shape: io.github.eslamasabry.oc.ui.design.tokens.KitShapeId, ownFill: Color): Modifier? {
    if (LocalKitStyle.current.style != Style.Glass) return null
    return kitSurfaceLook(
        KitSurfaceInput(
            io.github.eslamasabry.oc.ui.design.style.SurfaceRole.Sheet,
            shape,
            io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel.Surface2,
            ownFill = ownFill,
        )
    ).modifier
}

/** The Glass pane's face (Dart `_KitSurfacePainter.paint`): shadows outside it, then the tint, the glow and the rim. */
internal fun Modifier.kitGlassFace(d: SurfaceDecoration): Modifier = drawBehind { drawGlassFace(d) }

/** [kitGlassFace]'s painting, for a face drawn inside another box (a chosen row's inset face). */
internal fun DrawScope.drawGlassFace(d: SurfaceDecoration) {
    // A pane laid out at zero size (a fold or grow animation's first frame) draws nothing.
    if (size.width <= 0f || size.height <= 0f) return
    val shape = d.shape()
    val outline = shape.createOutline(size, layoutDirection, this)
    val rtl = layoutDirection == LayoutDirection.Rtl
    val sign = if (rtl) -1f else 1f
    // Shadows fall only outside the surface, so a clear pane is never darkened by its own shadow
    // (kit_surface_paint.dart:32-43).
    val raisedPaired = d.paired?.let { it.inset <= 0f && (it.distanceDp > 0f || it.blurDp > 0f) } == true
    val hard = d.hardShadow?.let { it.alpha > 0 } == true && d.hardOffsetDp > 0f
    if (d.shadows.any { it.color.alpha > 0 } || raisedPaired || hard) {
        val face = Path().apply { addOutline(outline) }
        clipPath(face, ClipOp.Difference) {
            d.hardShadow?.let { hs ->
                if (d.hardOffsetDp > 0f) {
                    val o = d.hardOffsetDp.dp.toPx()
                    translate(o * sign, o) { drawOutline(outline, Color(hs.v)) }
                }
            }
            d.shadows.forEach { s -> if (s.color.alpha > 0) drawSurfaceShadow(s, shape, sign) }
            d.paired?.let { p ->
                if (raisedPaired) {
                    drawSurfaceShadow(io.github.eslamasabry.oc.ui.design.style.Shadow(-p.distanceDp, -p.distanceDp, p.blurDp, p.light), shape, sign)
                    drawSurfaceShadow(io.github.eslamasabry.oc.ui.design.style.Shadow(p.distanceDp, p.distanceDp, p.blurDp, p.dark), shape, sign)
                }
            }
        }
    }
    val topStart = Offset(if (rtl) size.width else 0f, 0f)
    val bottomEnd = Offset(if (rtl) 0f else size.width, size.height)
    // The tint, lit at its top start and plain from 70 % of the way across (kit_surface_paint.dart:75-90).
    val fill = d.fill
    if (fill != null) {
        val start = d.fillStart ?: fill
        if (start == fill) {
            if (fill.alpha > 0) drawOutline(outline, Color(fill.v))
        } else if (fill.alpha > 0 || start.alpha > 0) {
            drawOutline(
                outline,
                Brush.linearGradient(
                    0f to Color(start.v),
                    .7f to Color(fill.v), // FG: lib/ui/kit/kit_surface_paint.dart:86
                    start = topStart,
                    end = bottomEnd,
                ),
            )
        }
    }
    // A clear pane's top sheen (owner 2026-11-08, "more glossy"): lighter at the top, gone 30 % of the way down, and at
    // most SHEEN_MAX_DP down a tall pane (owner 2026-11-09: the gloss is at the top, not over the rows).
    if (d.sheen.alpha > 0) {
        val sheen = Color(d.sheen.v)
        drawOutline(outline, Brush.verticalGradient(listOf(sheen, sheen.copy(alpha = 0f)), startY = 0f, endY = sheenDepthPx()))
    }
    // The glow inside the face along its top edge, fading out innerGlowDepth down (kit_surface_paint.dart:92-106).
    if (d.innerGlow.alpha > 0) {
        val depth = SurfaceDecoration.INNER_GLOW_DEPTH_DP.dp.toPx().coerceIn(0f, size.height / 2f)
        if (depth > 0f) {
            val glow = Color(d.innerGlow.v)
            drawOutline(outline, Brush.verticalGradient(listOf(glow, glow.copy(alpha = 0f)), startY = 0f, endY = depth))
        }
    }
    // Pressed in (a fade from Soft): as kitDecorated draws it.
    d.paired?.let { p ->
        if (p.inset > 0f) {
            val w = (p.distanceDp.coerceAtLeast(1.5f)).dp.toPx()
            drawOutline(outline, Brush.linearGradient(listOf(Color(p.dark.v), Color.Transparent, Color(p.light.v))), style = Stroke(w))
        }
    }
    // The rim, fading from the top start to the bottom end, inside the face (kit_surface_paint.dart:132-144).
    if (d.edgeWidthDp > 0f) {
        val s = d.edgeStart ?: d.edgeEnd
        val e = d.edgeEnd ?: d.edgeStart
        if (s != null && e != null && (s.alpha > 0 || e.alpha > 0)) {
            val w = d.edgeWidthDp.dp.toPx()
            // The rim is inset by half its width: a pane narrower than the rim has no inside to draw it in.
            if (size.width > w && size.height > w) inset(w / 2f) {
                val rim = shape.createOutline(size, layoutDirection, this)
                if (s == e) {
                    drawOutline(rim, Color(s.v), style = Stroke(w))
                } else {
                    drawOutline(
                        rim,
                        Brush.linearGradient(
                            listOf(Color(s.v), Color(e.v)),
                            start = Offset(if (rtl) size.width else 0f, 0f),
                            end = Offset(if (rtl) 0f else size.width, size.height),
                        ),
                        style = Stroke(w),
                    )
                }
            }
        }
    }
    // The specular over the rim (owner 2026-11-09, "crisper glossy glass"): a bright segment at the top start and a thin
    // line just inside the top edge. Crisp, never blurred.
    if (d.specular.alpha > 0) {
        val top = if (d.circle) minOf(size.width, size.height) / 2f else d.radii.topStart.dp.toPx()
        drawGlassSpecular(shape, Color(d.specular.v), rtl, top, d.edgeWidthDp)
    }
}

/** How far down a face its sheen reaches: [SurfaceDecoration.SHEEN_SHARE] of it, at most [SurfaceDecoration.SHEEN_MAX_DP]. */
internal fun DrawScope.sheenDepthPx(): Float =
    minOf(size.height * SurfaceDecoration.SHEEN_SHARE, SurfaceDecoration.SHEEN_MAX_DP.dp.toPx())

/**
 * Glass's specular light on a face of [shape] (in-flow panes and liquid panes alike; owner 2026-11-09): a bright
 * segment of the 1 dp rim round the top-start corner, fading along both edges within [SPECULAR_REACH_DP] (at most
 * [SPECULAR_REACH_SHARE] of the width), and a thin line [SurfaceDecoration.GLINT_DROP_DP] inside the top edge, between
 * the top corners, brightest a quarter of the way from the start and gone at both ends ([SurfaceDecoration.GLINT_SHARE]
 * of [color]). [topRadiusPx] is the top-start corner's radius. Both are crisp strokes clipped to the face.
 */
internal fun DrawScope.drawGlassSpecular(shape: Shape, color: Color, rtl: Boolean, topRadiusPx: Float, rimDp: Float = 1f) {
    val w = rimDp.coerceIn(1f, 1.5f).dp.toPx()
    if (size.width <= w * 4f || size.height <= w * 4f || color.alpha <= 0f) return
    val corner = Offset(if (rtl) size.width else 0f, 0f)
    val reach = minOf(SPECULAR_REACH_DP.dp.toPx(), size.width * SPECULAR_REACH_SHARE)
    val segment = Brush.radialGradient(0f to color, .45f to color.copy(alpha = color.alpha * .55f), 1f to color.copy(alpha = 0f), center = corner, radius = reach)
    inset(w / 2f) { drawOutline(shape.createOutline(size, layoutDirection, this), segment, style = Stroke(w)) }
    // The glint: one thin line inside the top edge, clear of the corners' curve.
    val y = SurfaceDecoration.GLINT_DROP_DP.dp.toPx()
    val margin = maxOf(topRadiusPx * .8f, w * 4f)
    val x0 = margin
    val x1 = size.width - margin
    if (x1 - x0 < w * 8f || y + w >= size.height / 2f) return
    val glint = color.copy(alpha = color.alpha * SurfaceDecoration.GLINT_SHARE)
    val peak = if (rtl) .75f else .25f
    val brush = Brush.horizontalGradient(0f to Color.Transparent, peak to glint, 1f to Color.Transparent, startX = x0, endX = x1)
    clipPath(Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawGlassSpecular)) }) {
        drawLine(brush, Offset(x0, y), Offset(x1, y), strokeWidth = w)
    }
}

/** How far the specular's bright rim segment reaches from the top-start corner, in dp, and at most this share of the width. */
internal const val SPECULAR_REACH_DP: Float = 120f
internal const val SPECULAR_REACH_SHARE: Float = .45f

/**
 * Records [block] into [layer] in the layer's own draw scope, with a snapshot of this scope's density, direction and
 * size. The `DrawScope.record { }` shorthand instead re-enters the node's own (shared) draw scope with that very scope
 * as its density, so the scope's density points back at itself and any `toPx()` in [block] recurses until the stack
 * overflows (N-29: Settings > Appearance > Style). Nothing recorded here can reach the scope recording it.
 */
private fun DrawScope.recordDetached(layer: GraphicsLayer, block: DrawScope.() -> Unit) {
    val snapshot = Density(density, fontScale)
    layer.record(snapshot, layoutDirection, size.toIntSize(), block)
}

/** Shared by both Glass nodes: the nearest one up the tree tells a pane whether it is nested. */
private object GlassTraverseKey

private data class BackdropSourceElement(
    val backdrop: KitGlassBackdrop,
    val paint: DrawScope.() -> Unit,
) : ModifierNodeElement<BackdropSourceNode>() {
    override fun create(): BackdropSourceNode = BackdropSourceNode(backdrop, paint)

    override fun update(node: BackdropSourceNode) {
        node.backdrop = backdrop
        node.paint = paint
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitGlassBackdropSource"
    }
}

private class BackdropSourceNode(
    var backdrop: KitGlassBackdrop,
    var paint: DrawScope.() -> Unit,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, TraversableNode {
    override val traverseKey: Any get() = GlassTraverseKey
    private var own: GraphicsLayer? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.origin = coordinates.positionInRoot()
        backdrop.root = coordinates.findRootCoordinates()
    }

    override fun onDetach() {
        own?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        if (backdrop.layer === own) backdrop.layer = null
        own = null
    }

    override fun ContentDrawScope.draw() {
        val layer = own ?: requireGraphicsContext().createGraphicsLayer().also { own = it }
        backdrop.layer = layer
        // Recorded in the layer's own scope, never through this node's: the picture cannot draw into, or ask for its
        // density from, the scope that is drawing it (N-29, a sample's paint reading toPx() recursed for ever).
        recordDetached(layer, paint)
        drawLayer(layer)
        drawContent()
    }
}

private data class GlassFrostElement(
    val backdrop: KitGlassBackdrop?,
    val effect: RenderEffect?,
    val ground: Color,
    val shape: Shape,
) : ModifierNodeElement<GlassFrostNode>() {
    override fun create(): GlassFrostNode = GlassFrostNode(backdrop, effect, ground, shape)

    override fun update(node: GlassFrostNode) {
        node.backdrop = backdrop
        node.effect = effect
        node.ground = ground
        node.shape = shape
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitSurfaceGlass"
    }
}

private class GlassFrostNode(
    var backdrop: KitGlassBackdrop?,
    var effect: RenderEffect?,
    var ground: Color,
    var shape: Shape,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, TraversableNode {
    override val traverseKey: Any get() = GlassTraverseKey
    private var coordinates: LayoutCoordinates? = null
    private var last: Offset = Offset.Unspecified
    private var blur: GraphicsLayer? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        // The picture under a pane moves when the pane does (a scroll): draw it again from the new place.
        val at = coordinates.positionInRoot()
        if (at != last) {
            last = at
            if (effect != null && backdrop != null) invalidateDraw()
        }
    }

    override fun onDetach() {
        blur?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        blur = null
        coordinates = null
        last = Offset.Unspecified
    }

    /** Inside a pane that frosts already: sit on its frost (Dart `_KitSurfaceScope.frosted`, kit_surface_look.dart:1263-1265). */
    private fun nested(): Boolean = findNearestAncestor(GlassTraverseKey) is GlassFrostNode

    override fun ContentDrawScope.draw() {
        if (!nested()) {
            val outline = shape.createOutline(size, layoutDirection, this)
            val bd = backdrop
            val fx = effect
            val c = coordinates
            val picture = bd?.layer
            if (fx != null && bd != null && picture != null && bd.painted && c != null && c.isAttached &&
                bd.root === c.findRootCoordinates()
            ) {
                val layer = blur ?: requireGraphicsContext().createGraphicsLayer().also { blur = it }
                layer.renderEffect = fx
                val shift = bd.origin - c.positionInRoot()
                recordDetached(layer) { translate(shift.x, shift.y) { drawLayer(picture) } }
                clipPath(Path().apply { addOutline(outline) }) { drawLayer(layer) }
            } else {
                drawOutline(outline, ground)
            }
        }
        drawContent()
    }
}
