package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.scale
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Size
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

    /**
     * The picture blurred once per frost sigma and shared by every pane of that sigma (smoothness 2026-11-09): a pane
     * draws its part of the one blurred copy, clipped to its shape, instead of blurring the picture again on its own.
     * Made by the panes in the source's window, released with the source ([kitGlassBackdropSource]). A page showing a
     * shared picture ([KitGlassPictures]) uses that picture's copies, so its panes and the other page's share them.
     */
    internal var frosted: MutableMap<Float, SharedFrost> = HashMap()

    /**
     * Bumped whenever the picture's paint changes (another look, a theme, the strength) or its layer is made again
     * (glass-live 2026-11-09, owner: "the render is fixed with the previous background render").
     */
    internal var paintVersion: Int by mutableIntStateOf(0)

    /** Reads the state the picture steps with (its time), set by the live background; null while it does not step. */
    internal var stepping: () -> Any? = NotStepping

    /**
     * What the picture shows now, as a key. Read it in every draw block that shows the picture through anything but
     * the page's own layer (a pane's frost, a liquid face, a rim): the block then runs again, in the same frame, on
     * every picture step and every look or theme change. A layer that only references the picture's recording is not
     * enough: a recording drawn under several parents (the page and every pane) is not re-rendered under all of them
     * when it changes, so a pane kept showing an old frame until something else redrew it.
     */
    internal fun frame(): Any? {
        val v = paintVersion
        val s = stepping()
        return if (s == null) v else v to s
    }
}

private val NotStepping: () -> Any? = { null }

/**
 * One blurred copy of the backdrop's picture at [FROST_SCALE] of its size (the blur hides the lower resolution; a
 * quarter of the pixels to blur), kept as a reference to the picture's layer: a new frame of the picture blurs again on
 * the GPU without anything being recorded here.
 */
internal class SharedFrost(val layer: GraphicsLayer) {
    var from: GraphicsLayer? = null

    /** The picture's [KitGlassBackdrop.frame] this copy was recorded at: a new frame records it again. */
    var frame: Any? = Unset
    var size: androidx.compose.ui.unit.IntSize = androidx.compose.ui.unit.IntSize.Zero
}

private object Unset

/**
 * One recorded live picture shared by every framed page of one size under a theme root (smoothness round 2: one live
 * background for the whole app), and what is made from it: its blurred copies and a shader look's reduced frame. Each
 * page showing it records it again on every draw (it is never kept across a change of the picture), and each page's
 * panes still follow their own page's [KitGlassBackdrop.frame].
 */
internal class KitGlassPicture {
    internal var layer: GraphicsLayer? = null
    internal val frosted: MutableMap<Float, SharedFrost> = HashMap()

    /** The shader look's reduced-resolution layer, when the picture is a shader look (`rememberLiveShaderBackdrop`). */
    internal var shader: GraphicsLayer? = null

    /** What [shader] was last recorded from: every input of the shader's frame, so an equal key is the same pixels. */
    internal var shaderKey: Any? = null

    internal var users: Int = 0

    internal fun release(context: androidx.compose.ui.graphics.GraphicsContext) {
        layer?.let(context::releaseGraphicsLayer)
        shader?.let(context::releaseGraphicsLayer)
        frosted.values.forEach { context.releaseGraphicsLayer(it.layer) }
        layer = null
        shader = null
        shaderKey = null
        frosted.clear()
    }
}

/**
 * The live pictures of one theme root, one per page size (smoothness round 2). Every framed page of a size shows the
 * same picture and its panes blur the same copies, so a push or a pop draws the look once, not once per page. Pages of
 * other sizes (a list pane) get their own.
 */
internal class KitGlassPictures {
    private val bySize = HashMap<androidx.compose.ui.unit.IntSize, KitGlassPicture>()

    /** Pictures in use (tests). */
    internal val count: Int get() = bySize.size

    internal fun acquire(size: androidx.compose.ui.unit.IntSize): KitGlassPicture =
        bySize.getOrPut(size) { KitGlassPicture() }.also { it.users++ }

    internal fun release(picture: KitGlassPicture, context: androidx.compose.ui.graphics.GraphicsContext) {
        picture.users--
        if (picture.users > 0) return
        bySize.entries.removeAll { it.value === picture }
        picture.release(context)
    }

    /** The picture recorded for [size], if a page of that size is showing one. */
    internal fun at(size: androidx.compose.ui.unit.IntSize): KitGlassPicture? = bySize[size]
}

/** The scale the shared frost blurs the picture at. */
internal const val FROST_SCALE: Float = .5f

@Composable
internal fun rememberKitGlassBackdrop(): KitGlassBackdrop = remember { KitGlassBackdrop() }

/**
 * The live picture of the page under a window's sheets and menus (provided by `KitSheetHost`, set by the page's live
 * background): a sheet's or a menu's liquid face lies outside the page's [LocalKitGlassBackdrop], yet must follow every
 * frame of the picture the page shows through it (glass-live 2026-11-09).
 */
@Stable
internal class KitLivePicture {
    internal var backdrop: KitGlassBackdrop? by mutableStateOf(null)
}

internal val LocalKitLivePicture: ProvidableCompositionLocal<KitLivePicture?> = staticCompositionLocalOf { null }

/** The backdrop of the page being drawn, or null where no live background hosts the page. */
internal val LocalKitGlassBackdrop: ProvidableCompositionLocal<KitGlassBackdrop?> = staticCompositionLocalOf { null }

/**
 * How much a frost lifts the saturation of the colour it blurs (1 keeps it): frosted glass glows with what is behind.
 * The glass look's own ([io.github.eslamasabry.oc.ui.design.style.GlassLook.frostSaturation]) where one is drawn.
 */
internal val FROST_SATURATION: Float get() = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.frostSaturation // FG: lib/ui/kit/kit_surface_look.dart:101 (1.7)

/**
 * Draws [paint] (the picture only) and records it for the panes above ([KitGlassBackdrop]). It also bounds the panes'
 * "once per stack" rule: a pane over this source frosts on its own. The live background puts it on a node of its own
 * behind the page's content ([kitGlassBackdropBoundary] bounds the content), so a step of the picture re-records the
 * picture alone, never the page.
 */
internal fun Modifier.kitGlassBackdropSource(
    backdrop: KitGlassBackdrop,
    /** The theme root's shared pictures (a framed page, smoothness round 2), or null: the page's own picture. */
    shared: KitGlassPictures? = null,
    paint: DrawScope.() -> Unit,
): Modifier = this then BackdropSourceElement(backdrop, shared, paint)

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
    val light = kitRimLightGround()
    val saturation = io.github.eslamasabry.oc.ui.kit.effects.kitGlassLook().spec.frostSaturation
    // Reduced motion and high contrast hold the frost solid (Dart `KitFrost.heldBy`; high contrast already draws Solid).
    val held = reduced || signals.highContrast
    val sigma = decoration.frost
    // The shared copy is blurred at FROST_SCALE of the picture's size, so the blur's radius is scaled with it.
    val effect =
        remember(sigma, density, platform, held, saturation) {
            if (held) null else platform.frostEffect(FrostSpec(sigma, saturation, 0f), density * FROST_SCALE)
        }
    return this then GlassFrostElement(if (effect == null) null else backdrop, effect, ground, decoration.shape(), sigma, light)
}

/**
 * Marks a liquid glass pane (`KitLiquidGlass`) as the frosted pane of its stack: Glass panes inside it sit on it and draw
 * no frost of their own (Dart `_KitSurfaceScope.frosted`), as they do inside a frosted pane. It draws nothing itself.
 */
internal fun Modifier.kitGlassStackRoot(): Modifier =
    this then GlassFrostElement(null, null, Color.Transparent, androidx.compose.ui.graphics.RectangleShape, 0f)

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
internal fun Modifier.kitGlassFace(d: SurfaceDecoration): Modifier = this then GlassFaceElement(d)

private data class GlassFaceElement(val d: SurfaceDecoration) : ModifierNodeElement<GlassFaceNode>() {
    override fun create() = GlassFaceNode(d)

    override fun update(node: GlassFaceNode) {
        node.d = d
    }
}

/**
 * Draws a pane's face from a picture of it, painted again only when the decoration, the pane's size or the reading
 * direction changes. A face is some ten outlines, gradients and clips; painting it afresh allocated all of them every
 * time the pane was drawn, and under Glass every pane on a page is drawn again on every frame of a scroll (the picture
 * behind it moved), so a page of row groups rebuilt all of them per frame (jank pass 2026-11-10). The picture is as
 * much larger than the pane as its shadows reach, so nothing outside the face is cut.
 */
private class GlassFaceNode(var d: SurfaceDecoration) : Modifier.Node(), DrawModifierNode {
    private var picture: GraphicsLayer? = null
    private var of: SurfaceDecoration? = null
    private var ofSize: Size = Size.Unspecified
    private var ofDirection: LayoutDirection? = null
    private var margin = 0

    override fun ContentDrawScope.draw() {
        if (size.width > 0f && size.height > 0f) {
            val layer = picture ?: requireGraphicsContext().createGraphicsLayer().also { picture = it }
            if (of != d || ofSize != size || ofDirection != layoutDirection) {
                val face = d
                margin = kotlin.math.ceil(glassFaceReachDp(face).dp.toPx()).toInt() + 2
                val m = margin.toFloat()
                val faceWidth = size.width
                val faceHeight = size.height
                val wide = kotlin.math.ceil(faceWidth).toInt() + 2 * margin
                val tall = kotlin.math.ceil(faceHeight).toInt() + 2 * margin
                // A snapshot of the density, as recordDetached: the scope itself as the picture's density loops back.
                layer.record(Density(density, fontScale), layoutDirection, IntSize(wide, tall)) {
                    // The face is painted at the pane's own size, [margin] in from the picture's top and start.
                    inset(m, m, wide - m - faceWidth, tall - m - faceHeight) { drawGlassFace(face) }
                }
                of = face
                ofSize = size
                ofDirection = layoutDirection
            }
            val m = margin.toFloat()
            translate(-m, -m) { drawLayer(layer) }
        }
        drawContent()
    }

    override fun onDetach() {
        picture?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        picture = null
        of = null
    }
}

/** How far outside the face its shadows can paint, in dp (offsets, blur and spread; generous, it only sizes the picture). */
internal fun glassFaceReachDp(d: SurfaceDecoration): Float {
    var reach = d.hardOffsetDp.coerceAtLeast(0f)
    for (s in d.shadows) reach = maxOf(reach, kotlin.math.abs(s.dxDp) + kotlin.math.abs(s.dyDp) + s.blurDp * 3f + s.spreadDp)
    d.paired?.let { reach = maxOf(reach, it.distanceDp * 2f + it.blurDp * 3f) }
    return reach
}

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
    // The rim, fading from the top start to the bottom end, inside the face (kit_surface_paint.dart:132-144); with a rest,
    // lit at both ends.
    if (d.edgeWidthDp > 0f) {
        val s = d.edgeStart ?: d.edgeEnd
        val e = d.edgeEnd ?: d.edgeStart
        // The rest between the two lit ends (glass-in-the-kit 1a): a third stop halfway along the fade; null, a straight fade.
        val r = d.edgeRest
        if (s != null && e != null && (s.alpha > 0 || e.alpha > 0 || (r?.alpha ?: 0) > 0)) {
            val w = d.edgeWidthDp.dp.toPx()
            // The rim is inset by half its width: a pane narrower than the rim has no inside to draw it in.
            if (size.width > w && size.height > w) inset(w / 2f) {
                val rim = shape.createOutline(size, layoutDirection, this)
                if (s == e && r == null) {
                    drawOutline(rim, Color(s.v), style = Stroke(w))
                } else {
                    drawOutline(
                        rim,
                        Brush.linearGradient(
                            listOfNotNull(Color(s.v), r?.let { Color(it.v) }, Color(e.v)),
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
        drawGlassSpecular(shape, Color(d.specular.v), rtl, top, d.edgeWidthDp, d.glint)
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
internal fun DrawScope.drawGlassSpecular(
    shape: Shape,
    color: Color,
    rtl: Boolean,
    topRadiusPx: Float,
    rimDp: Float = 1f,
    glintShare: Float = SurfaceDecoration.GLINT_SHARE,
    reachDp: Float = SPECULAR_REACH_DP,
) {
    val w = rimDp.coerceIn(1f, 1.5f).dp.toPx()
    if (size.width <= w * 4f || size.height <= w * 4f || color.alpha <= 0f) return
    val corner = Offset(if (rtl) size.width else 0f, 0f)
    val reach = minOf(reachDp.dp.toPx(), size.width * SPECULAR_REACH_SHARE).coerceAtLeast(1f)
    val segment = Brush.radialGradient(0f to color, .45f to color.copy(alpha = color.alpha * .55f), 1f to color.copy(alpha = 0f), center = corner, radius = reach)
    inset(w / 2f) { drawOutline(shape.createOutline(size, layoutDirection, this), segment, style = Stroke(w)) }
    // The glint: one thin line inside the top edge, clear of the corners' curve.
    val y = SurfaceDecoration.GLINT_DROP_DP.dp.toPx()
    val margin = maxOf(topRadiusPx * .8f, w * 4f)
    val x0 = margin
    val x1 = size.width - margin
    if (x1 - x0 < w * 8f || y + w >= size.height / 2f) return
    val glint = color.copy(alpha = color.alpha * glintShare)
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
    val shared: KitGlassPictures?,
    val paint: DrawScope.() -> Unit,
) : ModifierNodeElement<BackdropSourceNode>() {
    override fun create(): BackdropSourceNode = BackdropSourceNode(backdrop, shared, paint)

    override fun update(node: BackdropSourceNode) {
        if (node.backdrop !== backdrop || node.shared !== shared) node.letGo()
        node.shared = shared
        node.backdrop = backdrop
        node.paint = paint
        node.invalidateDraw()
        // Another paint (a look, a theme, the strength): every pane showing the picture draws again.
        backdrop.paintVersion++
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitGlassBackdropSource"
    }
}

private class BackdropSourceNode(
    var backdrop: KitGlassBackdrop,
    var shared: KitGlassPictures?,
    var paint: DrawScope.() -> Unit,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, TraversableNode {
    override val traverseKey: Any get() = GlassTraverseKey
    private var own: GraphicsLayer? = null

    /** The shared picture this page shows (smoothness round 2), and the page size it was taken for. */
    private var held: KitGlassPicture? = null
    private var heldSize: androidx.compose.ui.unit.IntSize? = null

    /** Lets go of a shared picture (released once its last page lets go); the page gets fresh copies of its own. */
    fun letGo() {
        val p = held ?: return
        held = null
        heldSize = null
        if (backdrop.layer === p.layer) backdrop.layer = null
        if (backdrop.frosted === p.frosted) backdrop.frosted = HashMap()
        backdrop.paintVersion++
        if (isAttached) shared?.release(p, requireGraphicsContext())
    }

    /** The layer this page records its picture into: the shared one of its size, or its own. */
    private fun layerFor(size: androidx.compose.ui.unit.IntSize): GraphicsLayer {
        val pictures = shared ?: return own ?: requireGraphicsContext().createGraphicsLayer().also { own = it }
        val p = held?.takeIf { heldSize == size } ?: run {
            letGo()
            pictures.acquire(size).also {
                held = it
                heldSize = size
            }
        }
        if (backdrop.frosted !== p.frosted) backdrop.frosted = p.frosted
        return p.layer ?: requireGraphicsContext().createGraphicsLayer().also { p.layer = it }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        backdrop.origin = coordinates.positionInRoot()
        backdrop.root = coordinates.findRootCoordinates()
    }

    override fun onDetach() {
        letGo()
        own?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        if (backdrop.layer === own) backdrop.layer = null
        own = null
        backdrop.paintVersion++
        backdrop.frosted.values.forEach { requireGraphicsContext().releaseGraphicsLayer(it.layer) }
        backdrop.frosted.clear()
    }

    override fun ContentDrawScope.draw() {
        val layer = layerFor(size.toIntSize())
        if (backdrop.layer !== layer) {
            backdrop.layer = layer
            // A new layer: every pane showing the old one draws again (outside this draw's own reads).
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { backdrop.paintVersion++ }
        }
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
    val sigma: Float,
    /** The backdrop's rim light (a dark theme, a look with one), or null: no rim light. */
    val light: KitRimLight? = null,
) : ModifierNodeElement<GlassFrostNode>() {
    override fun create(): GlassFrostNode = GlassFrostNode(backdrop, effect, ground, shape, sigma, light)

    override fun update(node: GlassFrostNode) {
        node.backdrop = backdrop
        node.effect = effect
        node.ground = ground
        node.shape = shape
        node.sigma = sigma
        node.light = light
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
    var sigma: Float,
    var light: KitRimLight?,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, TraversableNode {
    override val traverseKey: Any get() = GlassTraverseKey
    private var coordinates: LayoutCoordinates? = null
    private var last: Offset = Offset.Unspecified
    private val rim = BackdropRimLight()

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
        rim.release(requireGraphicsContext())
        coordinates = null
        last = Offset.Unspecified
    }

    /** The picture blurred once for every pane of this sigma, made on first use and kept in step with the picture. */
    private fun ContentDrawScope.shared(bd: KitGlassBackdrop, picture: GraphicsLayer, fx: RenderEffect): GraphicsLayer {
        val frost = bd.frosted.getOrPut(sigma) { SharedFrost(requireGraphicsContext().createGraphicsLayer()) }
        val layer = frost.layer
        val full = picture.size
        val now = bd.frame()
        if (frost.from !== picture || frost.size != full || layer.renderEffect == null || frost.frame != now) {
            frost.from = picture
            frost.size = full
            frost.frame = now
            val small = androidx.compose.ui.unit.IntSize(
                (full.width * FROST_SCALE).toInt().coerceAtLeast(1),
                (full.height * FROST_SCALE).toInt().coerceAtLeast(1),
            )
            layer.renderEffect = fx
            layer.pivotOffset = Offset.Zero
            layer.scaleX = full.width.toFloat() / small.width
            layer.scaleY = full.height.toFloat() / small.height
            // A reference to the picture's layer, drawn small: each new frame of the picture is blurred again by the GPU
            // with nothing recorded here.
            layer.record(Density(density, fontScale), layoutDirection, small) {
                scale(small.width.toFloat() / full.width, small.height.toFloat() / full.height, Offset.Zero) { drawLayer(picture) }
            }
        }
        return layer
    }

    /** Inside a pane that frosts already: sit on its frost (Dart `_KitSurfaceScope.frosted`, kit_surface_look.dart:1263-1265). */
    private fun nested(): Boolean = findNearestAncestor(GlassTraverseKey) is GlassFrostNode

    override fun ContentDrawScope.draw() {
        if (nested()) {
            drawContent()
            return
        }
        val outline = shape.createOutline(size, layoutDirection, this)
        val bd = backdrop
        val fx = effect
        val c = coordinates
        // Subscribes this draw to the picture: a new frame of it, or another look, draws this pane again.
        if (fx != null) bd?.frame()
        val picture = bd?.layer
        if (fx != null && bd != null && picture != null && bd.painted && c != null && c.isAttached &&
            bd.root === c.findRootCoordinates()
        ) {
            val layer = shared(bd, picture, fx)
            val shift = bd.origin - c.positionInRoot()
            clipPath(Path().apply { addOutline(outline) }) { translate(shift.x, shift.y) { drawLayer(layer) } }
            drawContent()
            // Over the face's own rim: the backdrop's light where the picture behind is bright (dark themes).
            light?.let { rim.draw(this, requireGraphicsContext(), picture, shift, shape, it) }
        } else {
            drawOutline(outline, ground)
            drawContent()
        }
    }
}

/**
 * The rim catching the backdrop's light (glass-live 2026-11-09; owner: "glass not interacting with the background", "so
 * frosty and not glossy"): the pane's thin rim shows the live picture behind it as neutral white light, brighter where
 * the picture is brighter than the ground and nothing over the plain ground. White, never the look's colour (owner
 * 2026-11-08: neutral rims). Dark themes only; a light theme's rim is already white on milk.
 *
 * One light (owner's first phone look, 2026-11-11: a thick bright bar on the straight part of a panel's start side, hard
 * ended where the thin, dim corner began, once the page's glow was bright): the light is the rim itself, a ring as wide
 * as the rim ([rimLightWidthDp], never wider) round the pane's whole outline, corners included, so it rises and falls
 * with the picture along the edge with no start or stop of its own. It used to be a [RIM_LIGHT_BAND_DP] band clipped
 * to two rectangles on the straight top edge and start side only.
 *
 * Cheap by design: the light's layer holds one reference to the page's one picture recording (never a copy), so the
 * picture stepping re-records nothing here; it is drawn through one ring clip (two rounded rectangles, even-odd), kept
 * until the pane's size or shape changes, and recorded again only when the pane moves or changes size.
 */
internal class BackdropRimLight {
    private var layer: GraphicsLayer? = null
    private var filtered: KitRimLight? = null

    fun draw(scope: DrawScope, context: androidx.compose.ui.graphics.GraphicsContext, picture: GraphicsLayer, shift: Offset, shape: Shape, light: KitRimLight) {
        with(scope) {
            val band = rimLightWidthDp(light).dp.toPx()
            if (light.gain <= 0f || size.width <= band * 4f || size.height <= band * 4f) return
            val l = layer ?: context.createGraphicsLayer().also { layer = it }
            if (filtered != light) {
                l.colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(rimLightMatrix(light.groundLuma, light.gain))
                filtered = light
            }
            l.alpha = light.max
            recordDetached(l) { translate(shift.x, shift.y) { drawLayer(picture) } }
            // The rim's own ring, round the whole outline: the light is the rim, never a stroke of its own.
            clipPath(ringOf(shape, size, layoutDirection, this, band)) { drawLayer(l) }
        }
    }

    private var ring: Path? = null
    private var ringKey: Any? = null

    /** The band [w] px wide just inside [shape]'s outline, as one even-odd path; kept while its inputs stay. */
    private fun ringOf(shape: Shape, size: Size, direction: LayoutDirection, density: Density, w: Float): Path {
        val key = listOf(shape, size, direction, density.density, w)
        ring?.takeIf { ringKey == key }?.let { return it }
        val path = rimRing(shape.createOutline(size, direction, density), w) {
            shape.createOutline(Size(size.width - 2f * w, size.height - 2f * w), direction, density)
        }
        ring = path
        ringKey = key
        return path
    }

    fun release(context: androidx.compose.ui.graphics.GraphicsContext) {
        layer?.let { context.releaseGraphicsLayer(it) }
        layer = null
        filtered = null
        ring = null
        ringKey = null
    }
}

/** The pane's rim width, in dp: the glass rim and the liquid rim are both 1 dp. */
internal const val RIM_WIDTH_DP: Float = 1f

/** How wide the picture's light on a rim is: the look's band, never wider than the rim itself ("one light"). */
internal fun rimLightWidthDp(light: KitRimLight): Float = minOf(light.bandDp, RIM_WIDTH_DP)

/**
 * The ring [w] px wide just inside [outline]: the outline less the same shape [w] in from every side (its corners
 * [w] tighter, so the ring is as wide round a corner as along an edge), filled even-odd. [inner] makes the inner outline
 * of a shape that is neither a rectangle nor a rounded one.
 */
internal fun rimRing(outline: androidx.compose.ui.graphics.Outline, w: Float, inner: () -> androidx.compose.ui.graphics.Outline): Path {
    val path = Path().apply { fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd }
    when (outline) {
        is androidx.compose.ui.graphics.Outline.Rectangle -> {
            path.addRect(outline.rect)
            path.addRect(outline.rect.deflate(w))
        }
        is androidx.compose.ui.graphics.Outline.Rounded -> {
            val o = outline.roundRect
            path.addRoundRect(o)
            fun tight(r: androidx.compose.ui.geometry.CornerRadius) =
                androidx.compose.ui.geometry.CornerRadius((r.x - w).coerceAtLeast(0f), (r.y - w).coerceAtLeast(0f))
            path.addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    o.left + w, o.top + w, o.right - w, o.bottom - w,
                    tight(o.topLeftCornerRadius), tight(o.topRightCornerRadius), tight(o.bottomRightCornerRadius), tight(o.bottomLeftCornerRadius),
                )
            )
        }
        is androidx.compose.ui.graphics.Outline.Generic -> {
            path.addPath(outline.path)
            path.addPath(Path().apply { addOutline(inner()) }, Offset(w, w))
        }
    }
    return path
}

/**
 * White at an alpha of [RIM_LIGHT_GAIN] × how far the picture's luma lies above the ground's ([groundLuma], 0..1): the
 * picture as light. Colour matrix rows in Compose's order (offsets 0..255).
 */
internal fun rimLightMatrix(groundLuma: Float, gain: Float = RIM_LIGHT_GAIN): androidx.compose.ui.graphics.ColorMatrix {
    val k = gain
    return androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 0f, 255f,
            k * LUMA_R, k * LUMA_G, k * LUMA_B, 0f, -k * groundLuma * 255f,
        )
    )
}

/** The rim light's ground luma and the glass look's gain, most and band ([BackdropRimLight]). */
internal data class KitRimLight(val groundLuma: Float, val gain: Float, val max: Float, val bandDp: Float)

/** The rim light in a dark theme under a look that has one, or null (a light theme, or a look without it). */
@Composable
internal fun kitRimLightGround(): KitRimLight? {
    val roles = io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles.current
    if (!roles.dark) return null
    val look = io.github.eslamasabry.oc.ui.kit.effects.kitGlassLook().spec
    if (look.backdropLightGain <= 0f) return null
    val g = roles.ground
    val luma = (LUMA_R * g.red + LUMA_G * g.green + LUMA_B * g.blue) / 255f
    return KitRimLight(luma, look.backdropLightGain, look.backdropLightMax, look.backdropLightBandDp)
}

/** The look's lit band inside a pane's edge, in dp; drawn no wider than the rim ([rimLightWidthDp]). */
internal val RIM_LIGHT_BAND_DP: Float get() = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.backdropLightBandDp

/** Light per unit of luma above the ground: a look at its cap (about .2 above a dark ground) lights the rim about half. */
internal val RIM_LIGHT_GAIN: Float get() = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.backdropLightGain

/** The most the rim light adds (its layer's alpha). */
internal val RIM_LIGHT_MAX: Float get() = io.github.eslamasabry.oc.ui.design.style.GlassLook.Default.backdropLightMax

private const val LUMA_R: Float = .2126f
private const val LUMA_G: Float = .7152f
private const val LUMA_B: Float = .0722f

/**
 * A liquid pane's rim catching the page's live picture ([BackdropRimLight]), drawn over the pane and its content. Nothing
 * where no live picture lies behind it in this window (a light theme, the flat ground, a popup).
 */
@Composable
internal fun Modifier.kitBackdropRimLight(shape: Shape): Modifier {
    val backdrop = LocalKitGlassBackdrop.current ?: return this
    val light = kitRimLightGround() ?: return this
    return this then RimLightElement(backdrop, shape, light)
}

private data class RimLightElement(val backdrop: KitGlassBackdrop, val shape: Shape, val light: KitRimLight) : ModifierNodeElement<RimLightNode>() {
    override fun create(): RimLightNode = RimLightNode(backdrop, shape, light)

    override fun update(node: RimLightNode) {
        node.backdrop = backdrop
        node.shape = shape
        node.light = light
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitBackdropRimLight"
    }
}

private class RimLightNode(var backdrop: KitGlassBackdrop, var shape: Shape, var light: KitRimLight) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var coordinates: LayoutCoordinates? = null
    private var last: Offset = Offset.Unspecified
    private val rim = BackdropRimLight()

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        val at = coordinates.positionInRoot()
        if (at != last) {
            last = at
            invalidateDraw()
        }
    }

    override fun onDetach() {
        rim.release(requireGraphicsContext())
        coordinates = null
        last = Offset.Unspecified
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val bd = backdrop
        bd.frame()
        val c = coordinates?.takeIf { it.isAttached } ?: return
        val picture = bd.layer ?: return
        if (!bd.painted || bd.root !== c.findRootCoordinates()) return
        rim.draw(this, requireGraphicsContext(), picture, bd.origin - c.positionInRoot(), shape, light)
    }
}
