package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import io.github.eslamasabry.oc.ui.kit.foundation.kitDrawLayer
import io.github.eslamasabry.oc.ui.kit.foundation.kitRecord
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.eslamasabry.oc.ui.design.effects.JoinBox
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Joined glass (docs/designs/liquid-merge-one-body.md, owner 2026-11-11): while a menu is open from a control on the bar,
 * or the jump pill stands on the message box, the two panes are ONE body of glass: one outline with a crafted fillet,
 * one bend, one rim light, one frost. They never let go of each other.
 *
 * Each pane draws its own side of one signed-distance field ([LiquidJoin], `:ui:design`) from its own node
 * ([kitLiquidJoinedPane]), from the picture it already reads (the bar and the box: the transcript; a menu: the page):
 * 1. its backdrop at the body pane's liquid resolution, blurred by the BODY pane's frost (one frost), then bent along
 *    the body's mixed direction by the join shader's lens pass (mode 0, a runtime-shader render effect), its colour
 *    controls inside that pass;
 * 2. its tint, eased to its partner's over the material band (mode 3), and the cover of its side (mode 1, DstIn);
 * 3. one rim light along the body's outline on its side (mode 2);
 * 4. the finger's light (KitLiquidTouch.kt) from either pane, in the body's shape: the soft light under the finger
 *    (mode 4) and the brighter outline (mode 5). A pane's own touch light is in its own shape, which showed its old
 *    outline inside the body (owner 2026-11-11, on the phone: "notice the artifacts").
 * The two sides meet on the line where the two real boxes are equally near, where every mixed value is the same from
 * both sides.
 *
 * Who is joined ([KitLiquidJoin.partnerOf]): a body ([rememberKitLiquidJoinBody]: the bar's capsule, the message box)
 * and the first drop of it ([rememberKitLiquidJoinDrop]: a menu, the jump pill) while both hold a granted liquid face
 * (`kitLiquidFace(join = ...)`): Glass, a look with a lens, Android 13+, motion not reduced, within the window's budget.
 * A pane with no partner is drawn by the vendored library exactly as before.
 */

/** One pane of a joined body: where it is, how round, and the face it wears. Snapshot state, read in the draw phase. */
@Stable
public sealed class KitLiquidJoinPart(
    /** The screen's joined glass this pane belongs to. */
    internal val owner: KitLiquidJoin,
) {
    /** The pane's box at rest in the window, px (written where it is laid out: [kitLiquidJoinRest]). */
    internal var rest: Rect by mutableStateOf(Rect.Zero)

    /** Its corner radius at rest, px. */
    internal var radius: Float by mutableFloatStateOf(0f)

    /** Whether it holds a granted liquid face now (written by `kitLiquidFace`). */
    internal var granted: Boolean by mutableStateOf(false)

    /** The face it wears (written by `kitLiquidFace`): its recipe, tint, rim and ink flip. */
    internal var spec: KitLiquidPaneSpec? by mutableStateOf(null)

    /**
     * What it reads (written by `kitLiquidFace`): the bar and the box their body's recording, a menu the page. A joined
     * drop draws its side from its body's ([JoinedPaneNode]): one picture for the one body.
     */
    internal var backdrop: KitLiquidBackdrop? by mutableStateOf(null)

    /**
     * Whether the face was last composed as the joined pane (written by `kitLiquidFace` in composition; read after it
     * in the same composition by a part that hosts its joined face outside a clip, and in the draw phase by the pane's
     * own touch light, which then leaves the light to the joined pane).
     */
    internal var composedJoined: Boolean = false

    /**
     * The finger's light on this pane (KitLiquidTouch.kt), drawn by both joined panes over the one body: where the
     * finger is in the window, px (unspecified: no light gathers under it), and how strongly the pane is touched
     * (0 to 1). Read in the draw phase.
     */
    internal var touchAt: () -> Offset = NoTouchAt
    internal var touchLight: () -> Float = NoTouchLight

    /** Its glass now, from its box at rest; a drop's motion reads its springs here (draw phase). */
    internal open fun glass(): Rect = rest
}

internal val NoTouchAt: () -> Offset = { Offset.Unspecified }
internal val NoTouchLight: () -> Float = { 0f }

/** A large pane a drop joins (the bar's capsule "bar", the message box "bottom"). */
@Stable
public class KitLiquidJoinBody internal constructor(owner: KitLiquidJoin, internal val name: String) : KitLiquidJoinPart(owner) {
    /** The last outlines traced for this body (plain fields: written and read in the draw phase). */
    internal val outlines: JoinedOutlines = JoinedOutlines()
}

/**
 * Owner 2026-11-14 (c26, the message box's running glow): a line drawn along a body joined to its drop runs round the
 * one body of glass, never across the drop's foot along the body's own box. The band [widthPx] wide along this body's
 * joined outline now, inside it ([centred] false) or centred on it, as an even-odd path in this body's own px (its box's
 * top-left at [origin]); null while the body stands alone (its own box is then its outline). Read in the draw phase: it
 * follows the drop as it rises, rests joined and leaves.
 */
public fun KitLiquidJoinBody.joinedBand(widthPx: Float, centred: Boolean, origin: Offset = Offset.Zero): androidx.compose.ui.graphics.Path? =
    joinedPath(if (centred) -widthPx / 2f else 0f, if (centred) widthPx / 2f else widthPx, origin)

/** Whether a drop is joined to this body now (snapshot read: a composition reading it runs again as a drop joins or leaves). */
public val KitLiquidJoinBody.joined: Boolean get() = owner.partnerOf(this) != null

/** The joined body's whole outline now, filled, in this body's own px (its box's top-left at [origin]); null while it stands alone. */
public fun KitLiquidJoinBody.joinedFill(origin: Offset = Offset.Zero): androidx.compose.ui.graphics.Path? = joinedPath(0f, null, origin)

private fun KitLiquidJoinBody.joinedPath(outer: Float, inner: Float?, origin: Offset): androidx.compose.ui.graphics.Path? {
    val drop = owner.partnerOf(this) as? KitLiquidJoinDrop ?: return null
    val me = rest
    val them = drop.glass()
    if (me.width <= 0f || me.height <= 0f || them.width <= 0f || them.height <= 0f) return null
    val a = JoinBox.of(me.left, me.top, me.right, me.bottom, max(radius, 0f))
    val b = JoinBox.of(them.left, them.top, them.right, them.bottom, max(drop.radius, 0f))
    return outlines.path(a, b, drop.reach(), outer, inner, origin - me.topLeft)
}

/** A body's last few traced outlines, by their geometry (a glow asks for two or three widths every frame). */
internal class JoinedOutlines {
    private class Entry(val key: FloatArray, val path: androidx.compose.ui.graphics.Path)

    private val entries = ArrayList<Entry>(SLOTS)

    fun path(a: JoinBox, b: JoinBox, reach: Float, outer: Float, inner: Float?, shift: Offset): androidx.compose.ui.graphics.Path {
        val key =
            floatArrayOf(a.cx, a.cy, a.hx, a.hy, a.r, b.cx, b.cy, b.hx, b.hy, b.r, reach, outer, inner ?: Float.NaN, shift.x, shift.y)
        entries.firstOrNull { it.key.contentEquals(key) }?.let { return it.path }
        val path = androidx.compose.ui.graphics.Path()
        path.fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        path.addPolygon(io.github.eslamasabry.oc.ui.design.effects.JoinOutline.trace(a, b, reach, outer), shift)
        if (inner != null) path.addPolygon(io.github.eslamasabry.oc.ui.design.effects.JoinOutline.trace(a, b, reach, inner), shift)
        if (entries.size >= SLOTS) entries.removeAt(0)
        entries += Entry(key, path)
        return path
    }

    private fun androidx.compose.ui.graphics.Path.addPolygon(points: FloatArray, shift: Offset) {
        if (points.size < 6) return
        moveTo(points[0] + shift.x, points[1] + shift.y)
        var i = 2
        while (i < points.size) {
            lineTo(points[i] + shift.x, points[i + 1] + shift.y)
            i += 2
        }
        close()
    }

    private companion object {
        const val SLOTS = 6
    }
}

/** A drop of the body named [body] (a menu, the jump pill), joined to it while both are liquid. */
@Stable
public class KitLiquidJoinDrop internal constructor(owner: KitLiquidJoin, body: String) : KitLiquidJoinPart(owner) {
    internal var body: String by mutableStateOf(body)

    /** Its glass now from its box at rest (the drop's motion); null: its box at rest. Read in the draw phase. */
    internal var shape: ((Rect) -> Rect)? = null

    /** The join's reach now, px (its rest reach, swelled while it moves). Read in the draw phase. */
    internal var reach: () -> Float = { 0f }

    /** A menu: its field reaches the body's centre line (shape rule 3). */
    internal var reachesCentre: Boolean = false

    /** How much of its content (a menu's rows) shows now, 0 to 1. Read in the draw phase. */
    internal var content: () -> Float = { 1f }

    override fun glass(): Rect {
        val r = rest
        if (r.width <= 0f || r.height <= 0f) return r
        return shape?.invoke(r) ?: r
    }
}

/** One screen's joined glass: its bodies by name and their drops, and the join shader compiled for the platform. */
@Stable
public class KitLiquidJoin internal constructor() {
    internal val bodies = mutableStateMapOf<String, KitLiquidJoinBody>()
    internal val drops = mutableStateListOf<KitLiquidJoinDrop>()
    private var compiled: Any? = null

    /** The pane [part] is joined to now, or null (drawn as a lone pane). Snapshot reads: callers redraw as it changes. */
    internal fun partnerOf(part: KitLiquidJoinPart): KitLiquidJoinPart? =
        when (part) {
            is KitLiquidJoinBody -> drops.firstOrNull { it.body == part.name && it.granted }?.takeIf { part.granted }
            is KitLiquidJoinDrop -> bodies[part.body]?.takeIf { it.granted && part.granted && partnerOf(it) === part }
        }

    /** The body a drop that opens from [at] (window px) joins: the body under that point, if any. */
    internal fun bodyAt(at: Offset): KitLiquidJoinBody? = bodies.values.firstOrNull { it.rest.contains(at) }

    /** The join shader for [platform]: its lens pass (with a content input) and its brush passes; null where none. */
    internal fun programs(platform: KitPlatform): LiquidJoinPrograms? {
        (compiled as? LiquidJoinPrograms)?.let { return it }
        if (compiled === NONE) return null
        val compiler = platform.liveShaders?.takeIf { platform.liquidGlass }
        val effect = compiler?.compile(LiquidJoinShader.source(effect = true))
        val brush = compiler?.compile(LiquidJoinShader.source(effect = false))
        val programs = if (effect != null && brush != null) LiquidJoinPrograms(effect, brush) else null
        compiled = programs ?: NONE
        return programs
    }

    private companion object {
        val NONE = Any()
    }
}

internal class LiquidJoinPrograms(val lens: LiveShaderProgram, val brush: LiveShaderProgram)

/** The screen's joined glass (provided by `KitScreen` where its chrome is Glass over a recorded body); null: none. */
public val LocalKitLiquidJoin: ProvidableCompositionLocal<KitLiquidJoin?> = staticCompositionLocalOf { null }

/** The body named [name] of this screen's joined glass while this part is composed; null where the screen has none. */
@Composable
public fun rememberKitLiquidJoinBody(name: String): KitLiquidJoinBody? = rememberKitLiquidJoinBody(LocalKitLiquidJoin.current, name)

/** The body named [name] of [join] while this part is composed; null without one. */
@Composable
internal fun rememberKitLiquidJoinBody(join: KitLiquidJoin?, name: String): KitLiquidJoinBody? {
    if (join == null) return null
    val body = remember(join, name) { KitLiquidJoinBody(join, name) }
    DisposableEffect(join, name, body) {
        join.bodies[name] = body
        onDispose { if (join.bodies[name] === body) join.bodies.remove(name) }
    }
    return body
}

/** A drop of the body named [body] while this part is composed and [on]; null where the screen has no joined glass. */
@Composable
public fun rememberKitLiquidJoinDrop(body: String, on: Boolean = true): KitLiquidJoinDrop? {
    val join = LocalKitLiquidJoin.current
    if (join == null || !on) return null
    val drop = remember(join) { KitLiquidJoinDrop(join, body) }
    drop.body = body
    DisposableEffect(join, drop) {
        join.drops += drop
        onDispose { join.drops -= drop }
    }
    return drop
}

/**
 * Where [part] is: this node's box in the window and its [shape]'s corner radius there. Put it on the node that wears
 * the pane's face, before the face. Null [part]: nothing.
 */
@Composable
public fun Modifier.kitLiquidJoinRest(part: KitLiquidJoinPart?, shape: androidx.compose.ui.graphics.Shape): Modifier {
    if (part == null) return this
    val density = LocalDensity.current
    return this.onGloballyPositioned {
        val size = it.size.toSize()
        part.rest = Rect(it.positionInWindow(), size)
        part.radius = ((shape as? CornerBasedShape)?.topStart?.toPx(size, density) ?: 0f).coerceAtMost(size.minDimension / 2f)
    }
}

/**
 * The content of a joined drop (a menu's rows) clipped to its glass now: nothing is drawn outside the glass, and the
 * content eases in over [LiquidJoin.ROW_EASE_DP] behind each edge the glass is still moving across. Put it after the
 * face, on the same node as [kitLiquidJoinRest].
 */
internal fun Modifier.kitLiquidJoinClip(part: KitLiquidJoinPart): Modifier =
    this.graphicsLayer {
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
            alpha = (part as? KitLiquidJoinDrop)?.content?.invoke()?.coerceIn(0f, 1f) ?: 1f
        }
        .drawWithContent {
            val rest = part.rest
            val g = part.glass()
            if (rest.width <= 0f || rest.height <= 0f) {
                drawContent()
                return@drawWithContent
            }
            val l = g.left - rest.left
            val t = g.top - rest.top
            val r = g.right - rest.left
            val b = g.bottom - rest.top
            if (r <= l || b <= t) return@drawWithContent
            clipRect(l, t, r, b) { this@drawWithContent.drawContent() }
            val ease = LiquidJoin.ROW_EASE_DP.dp.toPx()
            if (l > .5f) {
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = l, endX = l + ease), blendMode = BlendMode.DstIn)
            }
            if (size.width - r > .5f) {
                drawRect(Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = r - ease, endX = r), blendMode = BlendMode.DstIn)
            }
        }

/** The joined face of [part], drawn behind its content (see the file comment). Use through `kitLiquidFace(join =)`. */
internal fun Modifier.kitLiquidJoinedPane(join: KitLiquidJoin, part: KitLiquidJoinPart, spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop): Modifier =
    this.semantics { this[KitLiquidJoined] = if (part is KitLiquidJoinBody) "body" else "drop" } then JoinedPaneElement(join, part, spec, backdrop)

/** Marks a pane drawn joined ("body" or "drop"), for tests; a pane keeps its own test tag. */
public val KitLiquidJoined: androidx.compose.ui.semantics.SemanticsPropertyKey<String> = androidx.compose.ui.semantics.SemanticsPropertyKey("KitLiquidJoined")

private data class JoinedPaneElement(
    val join: KitLiquidJoin,
    val part: KitLiquidJoinPart,
    val spec: KitLiquidPaneSpec,
    val backdrop: KitLiquidBackdrop,
) : ModifierNodeElement<JoinedPaneNode>() {
    override fun create(): JoinedPaneNode = JoinedPaneNode(join, part, spec, backdrop)

    override fun update(node: JoinedPaneNode) {
        node.join = join
        node.part = part
        node.spec = spec
        node.backdrop = backdrop
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "kitLiquidJoinedPane"
    }
}

private class JoinedPaneNode(
    var join: KitLiquidJoin,
    var part: KitLiquidJoinPart,
    var spec: KitLiquidPaneSpec,
    var backdrop: KitLiquidBackdrop,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
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
        drawJoined()
        drawContent()
    }

    private fun ContentDrawScope.drawJoined() {
        val c = coordinates?.takeIf { it.isAttached } ?: return
        val back = back ?: return
        val mask = mask ?: return
        val platform = currentValueOf(LocalKitPlatform)
        val programs = join.programs(platform) ?: return
        // Snapshot reads: the pane draws again when its partner comes, goes or moves. In the frame its partner has gone
        // and the face has not yet been composed back to the library's pane, it draws itself alone (reach 0).
        val partner = join.partnerOf(part)
        val theirSpec = partner?.spec ?: spec
        val mine = part.glass()
        if (mine.width <= 0f || mine.height <= 0f) return
        val theirs = partner?.glass()?.takeIf { it.width > 0f && it.height > 0f }
        val drop = (part as? KitLiquidJoinDrop) ?: (partner as? KitLiquidJoinDrop)
        val reach = if (theirs != null && drop != null) drop.reach() else 0f
        val bodySpec = if (part is KitLiquidJoinBody) spec else theirSpec
        val geometry =
            if (theirs == null || drop == null) JoinGeometry.alone(mine, part.radius)
            else
                JoinGeometry.of(
                    mine = mine,
                    mineRadius = part.radius,
                    theirs = theirs,
                    theirRadius = partner?.radius ?: 0f,
                    body = if (part is KitLiquidJoinBody) mine else theirs,
                    dropIsMine = part === drop,
                    reachesCentre = drop.reachesCentre,
                )
        // One body reads one picture (cut audit 1, c14/c25: a darker rounded shape inside the joined bar and menu, about
        // x385 y320 on the emulator): the menu's side used to draw from the page, which holds the bar's own face and the
        // pills over the page, and its hold pulled the bar's tint into a band under the bar, ending in a rounded corner on
        // the line where the two sides meet. A drop draws its side from its body's picture, as the body does.
        val source = if (part is KitLiquidJoinDrop && theirs != null) partner?.backdrop ?: backdrop else backdrop
        // Only this pane's side is drawn, and it lies within reach/6 of this pane's own box.
        val origin = c.positionInWindow()
        val grow = reach / 6f + 2f
        val area = Rect(floor(mine.left - grow), floor(mine.top - grow), ceil(mine.right + grow), ceil(mine.bottom + grow))
        val local = area.topLeft - origin
        val full = IntSize(area.width.toInt().coerceAtLeast(1), area.height.toInt().coerceAtLeast(1))
        // One frost for the body, but a menu frosts as a sheet does (words on glass 3): while a menu is joined, the body's
        // frost moves from the bar's towards the menu's as the menu's rows come in (the drop's content, 0 to 1), so the
        // bar and the menu stay one body of glass and nothing steps as the menu opens or closes.
        val dropSpec = if (part is KitLiquidJoinDrop) spec else if (partner is KitLiquidJoinDrop) theirSpec else null
        val frostsBody = theirs != null && dropSpec != null && dropSpec.recipe.hold.knee && drop != null
        val recipe =
            if (frostsBody) joinedFrost(bodySpec.recipe, dropSpec!!.recipe, drop!!.content())
            else bodySpec.recipe
        val res = liquidResolutionOf(recipe, platform.liquidResolution)
        val small = IntSize(ceil(full.width * res).toInt().coerceAtLeast(1), ceil(full.height * res).toInt().coerceAtLeast(1))
        val u =
            JoinUniforms(
                geometry = geometry.relativeTo(area.topLeft),
                reach = reach,
                band = LiquidJoin.BAND_DP.dp.toPx(),
                rimMine = LiquidEdge.rimPx(min(mine.width, mine.height), density, spec.recipe),
                rimTheirs = theirs?.let { LiquidEdge.rimPx(min(it.width, it.height), density, theirSpec.recipe) } ?: 0f,
                refractionMine = spec.recipe.refractionDp.dp.toPx(),
                refractionTheirs = theirSpec.recipe.refractionDp.dp.toPx(),
                saturation = recipe.saturation,
                contrast = recipe.contrast,
                brightness = recipe.brightness,
                knee = kneeOf(spec),
                hold = if (frostsBody) dropSpec!!.recipe.hold.chroma else 0f,
                // The menu's detail softening is the body's too, coming in with its rows as its frost does (one material).
                local = (if (frostsBody) dropSpec!!.recipe.hold else recipe.hold).let {
                    val reach = if (frostsBody) drop!!.content().coerceIn(0f, 1f) else 1f
                    localOf(it, it.localDp.dp.toPx() * reach)
                },
                tintMine = tintNow(spec),
                tintTheirs = tintNow(theirSpec),
                rimPx = spec.rimDp.dp.toPx(),
                falloff = spec.rimFalloff,
                rimFloor = spec.rimFloor,
                lightDir = spec.lightDir,
                bendShape = spec.bendShape,
            )
        // 1. The backdrop at the body's resolution, blurred by the body's frost, bent and pressed by the lens pass.
        u.set(programs.lens, mode = 0f, res = res)
        back.renderEffect = platform.liquidJoinEffect(programs.lens, recipe.blurDp.dp.toPx() * res)
        kitRecord(back, small) {
            scale(res, res, Offset.Zero) { translate(-local.x, -local.y) { source.draw(this, c) } }
        }
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
            // 3. One rim light along the body's outline on this side (the library's highlight on the body's direction).
            u.set(programs.brush, mode = 2f, res = 1f)
            drawRect(
                programs.brush.brush,
                size = full.toSize(),
                alpha = spec.rim.alpha,
                colorFilter = ColorFilter.tint(spec.rim.copy(alpha = 1f)),
                blendMode = if (spec.rimAdds) BlendMode.Plus else BlendMode.SrcOver,
            )
            // 4. The finger's light, from this pane or its partner, on this side of the body.
            val dark = currentValueOf(LocalKitRoles).dark
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
}

/**
 * The frost of a body joined to a menu: the body's [body] moved towards the menu's [menu] by [t] (0 to 1), blur and
 * nothing else (the bend, rim and tint ease by the join's own band): linear in [t], so it has no step.
 */
internal fun joinedFrost(body: io.github.eslamasabry.oc.ui.design.effects.LiquidRecipe, menu: io.github.eslamasabry.oc.ui.design.effects.LiquidRecipe, t: Float): io.github.eslamasabry.oc.ui.design.effects.LiquidRecipe =
    body.copy(blurDp = body.blurDp + (menu.blurDp - body.blurDp) * t.coerceIn(0f, 1f))

/**
 * The knee that holds back bright content in [spec]'s lens (LiquidJoin.held): it holds the bright end under light words
 * and lifts the dark end under dark words, following the words' ink flip.
 */
internal fun kneeOf(spec: KitLiquidPaneSpec): FloatArray {
    val flipped = (spec.flip?.progress?.value ?: 0f) > .5f
    val lightInk = spec.rimAdds != flipped
    return floatArrayOf(spec.knee, spec.kneeSlope, if (lightInk) 1f else 0f)
}

/**
 * True for the words on a liquid pane (the bar, a menu): their second line is drawn brighter than on the page
 * (LiquidJoin.SECOND_INK_ON_GLASS of the way to the first line), so it reads over what passes behind the glass.
 */
public val LocalKitOnLiquidGlass: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** A pane's tint now: its face, cross-faded to the other theme's as its words flip. */
internal fun tintNow(spec: KitLiquidPaneSpec): Color {
    val f = spec.flip?.progress?.value ?: 0f
    return if (f > 0f) lerp(spec.fill, spec.flipFill, f) else spec.fill
}

/** The four boxes one joined pane draws from, in px: its own and its partner's, as the field and as the side see them. */
internal data class JoinGeometry(val a: JoinBox, val b: JoinBox, val sideA: JoinBox, val sideB: JoinBox) {
    fun relativeTo(o: Offset): JoinGeometry = JoinGeometry(a.minus(o), b.minus(o), sideA.minus(o), sideB.minus(o))

    companion object {
        /** A pane with no partner: its own box, and a point far outside the window as the other. */
        fun alone(mine: Rect, radius: Float): JoinGeometry {
            val me = mine.box(radius)
            val far = JoinBox(-1e6f, -1e6f, 0f, 0f, 0f)
            return JoinGeometry(me, far, me, far)
        }

        fun of(mine: Rect, mineRadius: Float, theirs: Rect, theirRadius: Float, body: Rect, dropIsMine: Boolean, reachesCentre: Boolean): JoinGeometry {
            val me = mine.box(mineRadius)
            val them = theirs.box(theirRadius)
            val bodyBox = body.box(0f)
            val fieldMe = if (dropIsMine && reachesCentre) LiquidJoin.fieldBox(me, bodyBox) else me
            val fieldThem = if (!dropIsMine && reachesCentre) LiquidJoin.fieldBox(them, bodyBox) else them
            return JoinGeometry(fieldMe, fieldThem, me, them)
        }

        private fun Rect.box(radius: Float): JoinBox = JoinBox.of(left, top, right, bottom, max(radius, 0f))

        private fun JoinBox.minus(o: Offset): JoinBox = copy(cx = cx - o.x, cy = cy - o.y)
    }
}

/** Every uniform of one joined pane's passes (see [LiquidJoinShader]). */
internal class JoinUniforms(
    val geometry: JoinGeometry,
    val reach: Float,
    val band: Float,
    val rimMine: Float,
    val rimTheirs: Float,
    val refractionMine: Float,
    val refractionTheirs: Float,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    /** The knee that holds back bright content: (knee, slope, dark); slope 1 = off. */
    val knee: FloatArray,
    val tintMine: Color,
    val tintTheirs: Color,
    val rimPx: Float,
    /** The most chroma the body lets through in its inner part (LiquidEdge.heldRgb, LiquidHold.chroma); 0 = no limit. */
    val hold: Float = 0f,
    /** The rim light's falloff and floor ([io.github.eslamasabry.oc.ui.design.effects.RimLight]) and its direction. */
    val falloff: Float = RIM_FALLOFF,
    val rimFloor: Float = io.github.eslamasabry.oc.ui.design.effects.RimLight.FLOOR,
    val lightDir: FloatArray = SHIPPED_LIGHT,
    /** The bend's profile (-1 soft dome, 0 the shipped circle, 1 sharp bevel). */
    val bendShape: Float = 0f,
    /** The local hold: (reach px, free, slope, ink kept, ink in the rim); reach 0 = off, ink kept 1 = off. */
    val local: FloatArray = floatArrayOf(0f, 0f, 1f, 1f, 1f),
) {
    /** [light]: the finger's light for modes 4 and 5 (centre x, centre y, radius or line width in px, strength). */
    fun set(p: LiveShaderProgram, mode: Float, res: Float, light: FloatArray = NO_LIGHT) {
        val g = geometry
        p.float4("light", light[0], light[1], light[2], light[3])
        p.float4("a", g.a.cx, g.a.cy, g.a.hx, g.a.hy)
        p.float("ra", g.a.r)
        p.float4("b", g.b.cx, g.b.cy, g.b.hx, g.b.hy)
        p.float("rb", g.b.r)
        p.float4("sa", g.sideA.cx, g.sideA.cy, g.sideA.hx, g.sideA.hy)
        p.float4("sb", g.sideB.cx, g.sideB.cy, g.sideB.hx, g.sideB.hy)
        p.float("reach", reach)
        p.float("band", band)
        p.float("mode", mode)
        p.float("res", res)
        p.float2("rim", rimMine, rimTheirs)
        p.float2("refr", refractionMine, refractionTheirs)
        p.float4("cc", saturation, contrast, brightness, 0f)
        p.float3("knee", knee[0], knee[1], knee[2])
        p.float("hold", hold)
        p.float4("local", local[0], local[1], local[2], local.getOrElse(3) { 1f })
        p.float("inkRim", local.getOrElse(4) { 1f })
        p.float4("tintA", tintMine.red * tintMine.alpha, tintMine.green * tintMine.alpha, tintMine.blue * tintMine.alpha, tintMine.alpha)
        p.float4("tintB", tintTheirs.red * tintTheirs.alpha, tintTheirs.green * tintTheirs.alpha, tintTheirs.blue * tintTheirs.alpha, tintTheirs.alpha)
        p.float("rimPx", rimPx)
        p.float("falloff", falloff)
        p.float("rimFloor", rimFloor)
        p.float2("lightDir", lightDir[0], lightDir[1])
        p.float("bendShape", bendShape)
    }
}

/** The shipped rim light's direction: the top-start / bottom-end diagonal. */
internal val SHIPPED_LIGHT: FloatArray = floatArrayOf(0.70710678f, 0.70710678f)

private val NO_LIGHT: FloatArray = floatArrayOf(0f, 0f, 1f, 0f)

/** How tightly the rim light gathers at its lit arcs (the plain pane's: RimLight.FALLOFF). */
internal const val RIM_FALLOFF: Float = io.github.eslamasabry.oc.ui.design.effects.RimLight.FALLOFF

/** The knee switched off (the glass keeps all light). */
internal val KNEE_OFF: FloatArray = floatArrayOf(1f, 1f, 1f)

/**
 * The join shader: AGSL on Android 13+ and SkSL on desktop, the same text. One source, two programs: the lens pass reads
 * the blurred backdrop as `content` (used as a render effect); the brush passes have no input. `mode` picks the pass:
 * 0 the lens (bend, colour controls, the knee), 1 this side's cover, 2 the rim light, 3 the tint, 4 the light under a
 * finger and 5 the touched body's brighter outline (both on this side, `light` = centre, radius or width, strength). Coordinates are the
 * drawn area's px; the lens pass runs at `res` of them. The field is [LiquidJoin]'s, line for line.
 */
internal object LiquidJoinShader {
    fun source(effect: Boolean): String =
        (if (effect) "uniform shader content;\nhalf4 sampleContent(float2 p) { return content.eval(p); }\n"
        else "half4 sampleContent(float2 p) { return half4(0.0); }\n") + io.github.eslamasabry.oc.ui.design.effects.RimLight.SHADER_LIT + BODY

    private const val BODY: String =
        """
uniform float4 a;
uniform float ra;
uniform float4 b;
uniform float rb;
uniform float4 sa;
uniform float4 sb;
uniform float reach;
uniform float band;
uniform float mode;
uniform float res;
uniform float2 rim;
uniform float2 refr;
uniform float4 cc;
uniform float3 knee;
uniform float hold;
uniform float4 local;
uniform float4 tintA;
uniform float4 tintB;
uniform float rimPx;
uniform float falloff;
uniform float rimFloor;
uniform float2 lightDir;
uniform float bendShape;
uniform float4 light;

float sat01(float x) { return clamp(x, 0.0, 1.0); }
float ss(float e0, float e1, float x) { float t = sat01((x - e0) / (e1 - e0)); return t * t * (3.0 - 2.0 * t); }
float sdBox(float2 p, float4 r, float c) {
    float k = max(min(c, min(r.z, r.w)), 0.0);
    float2 q = abs(p - r.xy) - r.zw + k;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - k;
}
float2 dirBox(float2 p, float4 r, float c, float soften) {
    float gr = min(max(min(c, min(r.z, r.w)), 0.0) * soften, min(r.z, r.w));
    float2 rel = p - r.xy;
    float2 q = abs(rel) - (r.zw - gr);
    float2 s = sign(rel);
    if (q.x >= 0.0 || q.y >= 0.0) {
        float2 m = max(q, 0.0);
        return s * m / max(length(m), 0.000001);
    }
    float g = q.x >= q.y ? 1.0 : 0.0;
    return s * float2(g, 1.0 - g);
}
float depthOf(float4 r, float c) { return max(min(c, min(r.z, r.w)), 0.001); }
float4 holdSample(float2 p) {
    float4 s = float4(sampleContent(p));
    float3 rgb = s.rgb / max(s.a, 0.0001);
    float l = dot(rgb, float3(0.213, 0.715, 0.072));
    return float4(clamp(cc.y * (l + cc.x * (rgb - l)) + (0.5 - 0.5 * cc.y + cc.z), 0.0, 1.0), s.a);
}
""" + LiquidHoldShader.LOCAL_HOLD + """

half4 main(float2 q) {
    float2 p = q / res;
    float da = sdBox(p, a, ra);
    float db = sdBox(p, b, rb);
    float2 ga = dirBox(p, a, ra, 1.0);
    float2 gb = dirBox(p, b, rb, 1.0);
    float facing = sat01(0.5 - 0.5 * dot(ga, gb));
    float ma = depthOf(a, ra);
    float mb = depthOf(b, rb);
    float inside = (1.0 - ss(0.5 * ma, ma, -da)) * (1.0 - ss(0.5 * mb, mb, -db));
    float k = max(reach * facing * inside, 0.001);
    float h = max(k - abs(da - db), 0.0) / k;
    float m = h * h * 0.5;
    float w = da <= db ? m : 1.0 - m;
    float d = min(da, db) - h * h * h * k / 6.0;
    float2 g = mix(ga, gb, w);
    float dist = d / max(length(g), 0.05);
    float das = sdBox(p, sa, ra);
    float dbs = sdBox(p, sb, rb);
    float mine = das <= dbs ? 1.0 : 0.0;
    float t = ss(-band, band, das - dbs);
    float2 bend = mix(dirBox(p, a, ra, 1.5), dirBox(p, b, rb, 1.5), w);
    if (mode < 0.5) {
        float rimW = mix(rim.x, rim.y, t);
        float amount = mix(refr.x, refr.y, t);
        float depth = max(-dist, 0.0);
        float2 at = q;
        if (rimW > 0.0 && depth < rimW) {
            float x = 1.0 - depth / rimW;
            float dome = 1.0 - sqrt(max(1.0 - x * x, 0.0));
            float x2 = x * x;
            float shaped = bendShape < 0.0 ? mix(dome, x, -bendShape) : mix(dome, x2 * x2 * x2 * x2, bendShape);
            at = q - shaped * amount * bend * res;
        }
        float4 c = float4(sampleContent(at));
        float3 rgb = c.rgb;
        float l = dot(rgb, float3(0.213, 0.715, 0.072));
        rgb = clamp(cc.y * (l + cc.x * (rgb - l)) + (0.5 - 0.5 * cc.y + cc.z), 0.0, 1.0);
        // The rim keeps its detail and its colour (content must show there); from half its width inward the holds are
        // in full.
        float deep = rimW > 0.0 ? ss(0.5 * rimW, rimW, depth) : 1.0;
        // The local hold (LiquidHoldShader.LOCAL_HOLD, as the plain pane's): detail to its local mean, ink-like strokes to
        // the page's ground; then the knee below, as without it.
        if (local.x > 0.0) rgb = localHold(at, rgb, deep, res, knee.z);
        if (knee.y < 1.0) {
            float lum = dot(rgb, float3(0.213, 0.715, 0.072));
            float3 scaled;
            float grey;
            if (knee.z > 0.5) {
                grey = lum > knee.x ? knee.x + (lum - knee.x) * knee.y : lum;
                scaled = rgb * (grey / max(lum, 0.0001));
            } else {
                float inv = 1.0 - lum;
                float heldInv = inv > knee.x ? knee.x + (inv - knee.x) * knee.y : inv;
                grey = 1.0 - heldInv;
                scaled = 1.0 - (1.0 - rgb) * (heldInv / max(inv, 0.0001));
            }
            // The colour left is limited (hold = the most chroma let through, 0 = no limit), in the lens's inner part.
            float chroma = max(scaled.r, max(scaled.g, scaled.b)) - min(scaled.r, min(scaled.g, scaled.b));
            float easy = 0.4 * hold;
            float limited = (hold > 0.0 && chroma > easy) ? (easy + (hold - easy) * (1.0 - exp(-(chroma - easy) / (hold - easy)))) / chroma : 1.0;
            rgb = mix(float3(grey), scaled, mix(1.0, limited, deep));
            rgb = clamp(rgb, 0.0, 1.0);
        }
        return half4(float4(rgb * c.a, c.a));
    }
    if (mode < 1.5) {
        return half4(sat01(0.5 - dist) * mine);
    }
    if (mode < 2.5) {
        float gl = max(length(bend), 0.05);
        // The plain pane's light (KitRimShader): its two lit arcs the brightest, never below the floor elsewhere.
        float lit = rimLit(dot(bend / gl, lightDir), falloff, rimFloor);
        float edge = sat01(0.5 - dist) * sat01(dist + rimPx + 0.5);
        return half4(edge * lit * mine);
    }
    if (mode > 4.5) {
        float line = sat01(0.5 - dist) * sat01(dist + light.z + 0.5);
        return half4(line * mine * light.w);
    }
    if (mode > 3.5) {
        float glow = sat01(1.0 - length(p - light.xy) / max(light.z, 1.0));
        return half4(sat01(0.5 - dist) * mine * glow * light.w);
    }
    return half4(mix(tintA, tintB, t));
}
"""
}
