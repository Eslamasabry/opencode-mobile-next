package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.effects.JoinBox
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * A liquid body drawn whole by ONE node (the edge dock, owner 2026-11-11 "Why isn't it glass?"): two rounded boxes,
 * box A and box B, joined by the joined-glass field ([LiquidJoin], the join shader of KitLiquidJoin.kt with its uniforms
 * unchanged). Where a bar and its menu each draw their own side of one body from two nodes, this node draws both sides:
 * it hands the shader box A as "this side" and a box far outside the window as the side box of B, so every pixel of the
 * body is this node's to draw and the material ramp stays on this side (t = 0). The shader text is not changed, so the
 * bar + menu and box + pill joins draw exactly as before.
 *
 * The material is one frost for the whole body, mixed by the body's growth ([LiquidEdge.mix]): the [rest] role's recipe
 * and tint at rest, the [grown] role's fully grown. The lens pass, the tint, the cover and one rim light follow the
 * body's outline. No finger light and no shadow.
 */

/** One frame of a two-box body, in the drawing node's own px; written by the owner in the draw phase. */
internal class KitLiquidBodyBoxes {
    var a: Rect = Rect.Zero
    var aRadius: Float = 0f

    /** Whether box B is out. */
    var hasB: Boolean = false
    var b: Rect = Rect.Zero
    var bRadius: Float = 0f

    /** The join field's reach, px (0: the plain union of the two boxes). */
    var reach: Float = 0f

    /** 0 at rest, 1 fully grown: mixes the material. */
    var growth: Float = 0f
}

/**
 * A liquid body's face, granted: [face] draws the glass body behind the node it is on; [words] goes on an ANCESTOR of
 * the words, marks and controls that sit on the body (the ink flip, the words' second line on glass, one glass stack);
 * [ink], [ink2] are the theme's first and second ink on this glass and [flipInk], [flipInk2] the other theme's, mixed by
 * [flip] (read its progress where the marks are drawn).
 */
@Immutable
internal class KitLiquidBodyFace(
    val face: Modifier,
    val words: Modifier,
    val ink: Color,
    val ink2: Color,
    val flipInk: Color,
    val flipInk2: Color,
    val flip: LiquidFlip,
)

/** Marks the node that draws a liquid body whole (the value names the role), for tests. */
public val KitLiquidBodyDrawn: SemanticsPropertyKey<String> = SemanticsPropertyKey("KitLiquidBodyDrawn")

/**
 * [role]'s liquid body over a feature's recording [source], read from [boxes] in the draw phase (it fills the holder and
 * returns false when there is no body to draw); null where it may not be liquid glass: not Glass, no lens (Frosted, saver,
 * reduced motion, high contrast, below Android 13, desktop), no recording, no join shader on the platform, or beyond the
 * window's budget. The material is [rest]'s at rest and [grown]'s fully grown. The words' area for the ink flip is the
 * body's bounds ([wordsArea], node px; null: the whole node). [rank] is the body's place in the window's budget now;
 * beyond the budget the body is frost of the same shape (no bend, the rim kept), as every liquid pane is.
 */
@Composable
internal fun kitLiquidFace(
    role: LiquidRole,
    source: KitFrostState?,
    rest: LiquidRole,
    grown: LiquidRole,
    boxes: (KitLiquidBodyBoxes) -> Boolean,
    wordsArea: () -> Rect?,
    rank: Int = role.rank,
): KitLiquidBodyFace? {
    val platform = LocalKitPlatform.current
    val programs = remember(platform) { KitLiquidJoin() }.programs(platform)
    val asking = kitLiquidOn() && source != null && programs != null
    val granted = kitLiquidClaim(role, source, asking, rank)
    if (!asking || source == null) return null
    val roles = LocalKitRoles.current
    val appearance = LocalKitAppearance.current
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val picture = LocalKitGlassBackdrop.current
    val backdrop = remember(source, ground, picture) { frostStateBackdrop(source, ground, picture) }
    val flip = liquidFlipFor(source, role) ?: remember { LiquidFlip() }
    val restSpec = kitLiquidPaneSpec(rest, flip, chosen = false, keepRim = false)
    val grownSpec = kitLiquidPaneSpec(grown, flip, chosen = false, keepRim = false)
    // The words sample what lies under the body only: the body's bounds stretched over the node's whole area.
    val sampled = remember(backdrop, wordsArea) { areaBackdrop(backdrop, wordsArea) }
    val words = kitLiquidWords(sampled, flip)
    val other = remember(roles, appearance.pack, appearance.accent) { ThemeResolver.roles(appearance.pack, appearance.accent, !roles.dark, saver = false, dynamic = null) }
    val second = LiquidJoin.SECOND_INK_ON_GLASS
    val ink = Color(roles.text1.v)
    val flipInk = Color(other.text1.v)
    return KitLiquidBodyFace(
        face = Modifier.semantics { this[KitLiquidBodyDrawn] = if (granted) role.name else "${role.name} frost" } then
            LiquidBodyElement(boxes, restSpec, grownSpec, backdrop, lens = granted),
        words = Modifier.kitLiquidPlateHost(words),
        ink = ink,
        ink2 = lerp(Color(roles.text2.v), ink, second),
        flipInk = flipInk,
        flipInk2 = lerp(Color(other.text2.v), flipInk, second),
        flip = flip,
    )
}

/** [inner] seen from a pane as if the pane were only [area] (node px) of itself: what the words' sampler reads. */
private fun areaBackdrop(inner: KitLiquidBackdrop, area: () -> Rect?): KitLiquidBackdrop =
    KitLiquidBackdrop(inner.version) { c ->
        val r = area()
        val s = c.size.toSize()
        if (r == null || r.width <= 0f || r.height <= 0f || s.width <= 0f || s.height <= 0f) {
            inner.draw(this, c)
            return@KitLiquidBackdrop
        }
        scale(s.width / r.width, s.height / r.height, Offset.Zero) { translate(-r.left, -r.top) { inner.draw(this, c) } }
    }

private data class LiquidBodyElement(
    val boxes: (KitLiquidBodyBoxes) -> Boolean,
    val rest: KitLiquidPaneSpec,
    val grown: KitLiquidPaneSpec,
    val backdrop: KitLiquidBackdrop,
    val lens: Boolean,
) : ModifierNodeElement<LiquidBodyNode>() {
    override fun create(): LiquidBodyNode = LiquidBodyNode(boxes, rest, grown, backdrop, lens)

    override fun update(node: LiquidBodyNode) {
        node.boxes = boxes
        node.rest = rest
        node.grown = grown
        node.backdrop = backdrop
        node.lens = lens
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitLiquidBody"
    }
}

private class LiquidBodyNode(
    var boxes: (KitLiquidBodyBoxes) -> Boolean,
    var rest: KitLiquidPaneSpec,
    var grown: KitLiquidPaneSpec,
    var backdrop: KitLiquidBackdrop,
    /** False beyond the window's budget: frost of the same shape (no bend; the blur, tint, cover and rim kept). */
    var lens: Boolean,
) : androidx.compose.ui.Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private val join = KitLiquidJoin()
    private val frame = KitLiquidBodyBoxes()
    private var coordinates: LayoutCoordinates? = null
    private var back: GraphicsLayer? = null
    private var mask: GraphicsLayer? = null

    override fun onAttach() {
        val g = requireGraphicsContext()
        back = g.createGraphicsLayer()
        mask = g.createGraphicsLayer().also { it.compositingStrategy = CompositingStrategy.Offscreen }
    }

    override fun onDetach() {
        val g = requireGraphicsContext()
        back?.let { g.releaseGraphicsLayer(it) }
        mask?.let { g.releaseGraphicsLayer(it) }
        back = null
        mask = null
        coordinates = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    override fun ContentDrawScope.draw() {
        drawBody()
        drawContent()
    }

    private fun ContentDrawScope.drawBody() {
        val c = coordinates?.takeIf { it.isAttached } ?: return
        val back = back ?: return
        val mask = mask ?: return
        val platform = currentValueOf(LocalKitPlatform)
        val programs = join.programs(platform) ?: return
        // Snapshot reads of the owner's motion: the body draws again every frame it moves.
        if (!boxes(frame)) return
        val f = frame
        if (f.a.width <= 0f || f.a.height <= 0f) return
        val hasB = f.hasB && f.b.width > 0f && f.b.height > 0f
        val bounds = if (hasB) f.a.copy(left = min(f.a.left, f.b.left), top = min(f.a.top, f.b.top), right = max(f.a.right, f.b.right), bottom = max(f.a.bottom, f.b.bottom)) else f.a
        // Only what lies on this node is drawn (the strip runs past the screen edge).
        val area =
            Rect(
                floor(max(bounds.left - 2f, 0f)),
                floor(max(bounds.top - 2f, 0f)),
                ceil(min(bounds.right + 2f, size.width)),
                ceil(min(bounds.bottom + 2f, size.height)),
            )
        if (area.width < 1f || area.height < 1f) return
        val full = IntSize(area.width.toInt(), area.height.toInt())
        val g = f.growth.coerceIn(0f, 1f)
        val recipe = LiquidEdge.mix(rest.recipe, grown.recipe, g)
        // One resolution for the whole growth (a step in it would show as the body grows): the grown end's.
        val res = liquidResolutionOf(grown.recipe, platform.liquidResolution)
        val small = IntSize(ceil(full.width * res).toInt().coerceAtLeast(1), ceil(full.height * res).toInt().coerceAtLeast(1))
        val geometry = LiquidBodyGeometry.of(f, hasB).relativeTo(area.topLeft)
        val shortSide = if (hasB) min(min(f.a.width, f.a.height), min(f.b.width, f.b.height)) else min(f.a.width, f.a.height)
        val rim = LiquidEdge.rimPx(shortSide, density, recipe)
        val refraction = if (lens) recipe.refractionDp.dp.toPx() else 0f
        val tint = lerp(tintNow(rest), tintNow(grown), g)
        val u =
            JoinUniforms(
                geometry = geometry,
                reach = if (hasB) f.reach else 0f,
                band = LiquidJoin.BAND_DP.dp.toPx(),
                rimMine = rim,
                rimTheirs = rim,
                refractionMine = refraction,
                refractionTheirs = refraction,
                saturation = recipe.saturation,
                contrast = recipe.contrast,
                brightness = recipe.brightness,
                knee = kneeOf(rest),
                tintMine = tint,
                tintTheirs = tint,
                rimPx = rest.rimDp.dp.toPx(),
            )
        val local = area.topLeft
        // 1. The backdrop at one resolution, blurred by the mixed frost, bent and pressed by the lens pass.
        u.set(programs.lens, mode = 0f, res = res)
        back.renderEffect = platform.liquidJoinEffect(programs.lens, recipe.blurDp.dp.toPx() * res)
        back.record(small) {
            scale(res, res, Offset.Zero) { translate(-local.x, -local.y) { backdrop.draw(this, c) } }
        }
        // 2. The tint, then the body's cover (both sides: every pixel is this node's).
        mask.record(full) {
            scale(1f / res, 1f / res, Offset.Zero) { drawLayer(back) }
            u.set(programs.brush, mode = 3f, res = 1f)
            drawRect(programs.brush.brush)
            u.set(programs.brush, mode = 1f, res = 1f)
            drawRect(programs.brush.brush, blendMode = BlendMode.DstIn)
        }
        translate(local.x, local.y) {
            drawLayer(mask)
            // 3. One rim light along the whole body's outline.
            u.set(programs.brush, mode = 2f, res = 1f)
            drawRect(
                programs.brush.brush,
                size = Size(full.width.toFloat(), full.height.toFloat()),
                alpha = rest.rim.alpha,
                colorFilter = ColorFilter.tint(rest.rim.copy(alpha = 1f)),
                blendMode = if (rest.rimAdds) BlendMode.Plus else BlendMode.SrcOver,
            )
        }
    }
}

/**
 * The join shader's four boxes for a body drawn whole by one node: box A and box B as the field sees them, A as this
 * side's box and a box far outside the window as B's side box, so the shader counts every pixel as this side's
 * (`mine` = 1) with this side's material (t = 0). Without B, B is that far box too (a lone pane).
 */
internal object LiquidBodyGeometry {
    private val FAR = JoinBox(-1e6f, -1e6f, 0f, 0f, 0f)

    fun of(a: JoinBox, b: JoinBox?): JoinGeometry = JoinGeometry(a, b ?: FAR, a, FAR)

    fun of(f: KitLiquidBodyBoxes, hasB: Boolean): JoinGeometry =
        of(
            JoinBox.of(f.a.left, f.a.top, f.a.right, f.a.bottom, max(f.aRadius, 0f)),
            if (hasB) JoinBox.of(f.b.left, f.b.top, f.b.right, f.b.bottom, max(f.bRadius, 0f)) else null,
        )
}
