package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow as ComposeShadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.Shadow
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.Corner
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Shapes
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.GlassSpec
import io.github.eslamasabry.oc.ui.kit.effects.glassRims
import io.github.eslamasabry.oc.ui.kit.effects.kitClearPanesOn
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceFade
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion

/**
 * What the style in force says about one surface (plan 06 §4.3, hooks H1-H11): [modifier] paints the style's fill, edge
 * and shadows (or, for Solid, the part's own [KitSurfaceInput.ownFill]), [words] is the colour of the words on it
 * (hook H3, null = the part's own), [faceShift] sinks the face while pressed (hook H2).
 */
@Immutable
public data class KitSurfaceLook(
    val modifier: Modifier,
    val words: Color?,
    val shape: Shape,
    val paints: Boolean = false,
    /** The style settled on a decoration of its own for this surface (not the part's own fill). */
    val dressed: Boolean = false,
)

/**
 * Paints [look] (its fill, edge and shadows, all on [KitSurfaceLook.shape]) and clips what follows (the part's own
 * content and press ink) to that same shape. A part's face and its clip are one shape, so a clip can never cut the
 * part's state outline at the corners (owner 2026-11-13: a focused field's outline stopped short of its four corners
 * because the field clipped to one named shape and drew its look on another). The look is painted outside the clip, so
 * an outline or ring a style draws on or past the face's edge (Brutal's focus ring) stays whole too.
 */
public fun Modifier.kitSurface(look: KitSurfaceLook): Modifier = this.then(look.modifier).clip(look.shape)

/** What a part asks for. [ownFill] is the part's own look under Solid; the other fields are the request. */
@Immutable
public data class KitSurfaceInput(
    val role: SurfaceRole,
    val shape: KitShapeId,
    val level: SurfaceLevel = SurfaceLevel.Surface1,
    val ownFill: Color? = null,
    val color: Color? = null,
    val selected: Boolean = false,
    val pressed: Boolean = false,
    val disabled: Boolean = false,
    val action: Boolean = false,
    val withinGroup: Boolean = false,
    /** A part-specific token radius, e.g. Flutter's composerRadius / composerRadiusWide. */
    val radius: Dp? = null,
    /** A floating sheet-radius card rounds all corners; edge-attached sheets keep their bottom square. */
    val roundSheetBottom: Boolean = false,
    /**
     * E20-S30: the face's fill keeps this much of its alpha (1 = as the style paints it). The composer lying on the
     * chat's translucent bottom veil passes the veil's alpha so the transcript shows through it. Glass ignores it (its
     * panes are already translucent); edges and shadows are unchanged.
     */
    val faceAlpha: Float = 1f,
    /**
     * An already-resolved corner radius (Corners knob applied by the caller), drawn as given. A control that nests one
     * shape inside another (a segmented thumb inside its track) derives the inner radius from the outer one, so it
     * must not be run through the Corners knob a second time.
     */
    val exactRadius: Dp? = null,
    /** Inside another outlined surface (a chip or disc in the message box): the style's edge, never a second shadow. */
    val nested: Boolean = false,
    /**
     * The part draws its own face unless the style dresses every part (`StyleTokens.dressesOwnLooks`: Brutal, Paper);
     * use [kitDressedLook], which answers null where the part keeps its own.
     */
    val ownLook: Boolean = false,
    /** Per-corner token radii (a bubble's tail corner), each run through the Corners knob; overrides [shape]'s radius. */
    val corners: Radii? = null,
)

/**
 * True inside a pane a style dresses with its own outline (the message box, a request card): every surface inside is
 * nested ([KitSurfaceInput.nested]), so it takes the style's edge but never casts a second shadow (Brutal; shadows do
 * not stack inside a pane). Styles that do not read nesting are unchanged.
 */
public val LocalKitNested: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> =
    androidx.compose.runtime.staticCompositionLocalOf { false }

private fun Argb.faded(a: Float): Argb = withAlpha(alpha / 255f * a)

private fun SurfaceDecoration.faded(a: Float): SurfaceDecoration =
    if (a >= 1f) this else copy(fill = fill?.faded(a), fillStart = fillStart?.faded(a))

private fun Color.toArgb(): Argb = Argb(
    ((alpha * 255f + .5f).toInt() shl 24) or ((red * 255f + .5f).toInt() shl 16) or ((green * 255f + .5f).toInt() shl 8) or (blue * 255f + .5f).toInt(),
)

private fun Argb.toColor(): Color = Color(v)

private fun KitShapeId.radiusDp(): Float =
    when (this) {
        KitShapeId.Pill, KitShapeId.Circle -> 999f
        else -> (Shapes.of(this) as Corner.Radius).dp.toFloat()
    }

/** The style's look for [input]: Solid keeps the part's own fill and shape; every other style draws its decoration. */
@Composable
public fun kitSurfaceLook(input: KitSurfaceInput): KitSurfaceLook {
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val sharp = style.spec.tokens.sharpRadius
    val base = input.exactRadius?.value ?: input.radius?.value ?: input.shape.radiusDp()
    val sheet = input.shape == KitShapeId.Sheet && !input.roundSheetBottom
    // A pane's corners grow with the style's pane inset (Glass, owner 2026-11-09), as its named shape does.
    // The look's shape is the named shape itself unless the part gives a radius or rounds a sheet's bottom.
    val named = input.radius == null && input.exactRadius == null && input.corners == null && !(input.shape == KitShapeId.Sheet && input.roundSheetBottom)
    // A named sheet's Sharp corner is the named shape's (KitShapes: at most 4 dp, twice the style's sharp radius), so
    // the decoration's corners and the shape a part clips to stay one (2026-11-13).
    val paneSharp = if (named && input.shape == KitShapeId.Sheet) minOf(4f, sharp * 2f) else sharp
    val r =
        if (input.exactRadius != null) base
        else if (input.shape.isPane) StyleResolver.paneCorner(base, style.knobs.corners, style.spec.tokens.copy(sharpRadius = paneSharp))
        else StyleResolver.corner(base, style.knobs.corners, sharp)
    val radii =
        input.corners?.map { StyleResolver.corner(it, style.knobs.corners, sharp) }
            ?: if (sheet) Radii(r, r, 0f, 0f) else Radii.all(if (input.shape == KitShapeId.Square) 0f else r)
    val shape =
        if (named) {
            input.shape.shape()
        } else {
            RoundedCornerShape(
                topStart = radii.topStart.dp, topEnd = radii.topEnd.dp,
                bottomStart = radii.bottomStart.dp, bottomEnd = radii.bottomEnd.dp,
            )
        }
    val request =
        SurfaceRequest(
            roles = roles,
            role = input.role,
            knobs = style.knobs,
            level = input.level,
            radii = radii,
            circle = input.shape == KitShapeId.Circle,
            color = input.color?.toArgb(),
            selected = input.selected,
            pressed = input.pressed,
            disabled = input.disabled,
            withinGroup = input.withinGroup,
            action = input.action,
            nested = input.nested || LocalKitNested.current,
            look = if (style.style == Style.Glass) io.github.eslamasabry.oc.ui.kit.effects.kitGlassLook().spec else io.github.eslamasabry.oc.ui.design.style.GlassLook.Default,
        )
    val isGlass = style.style == Style.Glass
    val backdrop = LocalKitGlassBackdrop.current
    val blurs = kitGlassBlurs(if (isGlass) backdrop else null)
    // Glass's in-flow panes are clear frosted glass where they blur a picture and nothing holds them (owner 2026-11-08);
    // floating parts and sheets resolve as before.
    val clear = isGlass && blurs && kitClearPanesOn()
    // A part with a look of its own keeps it unless the style dresses every part (Brutal, Paper).
    val keepsOwn = input.ownLook && !style.spec.tokens.dressesOwnLooks
    val resolved =
        remember(request, style, clear, keepsOwn) {
            if (keepsOwn) null else if (clear) GlassSpec.resolveClear(request) else style.spec.resolve(request)
        }
    val words = remember(request, style, keepsOwn) { if (keepsOwn) null else style.spec.onColor(request)?.toColor() }
    val glass = isGlass && resolved != null
    // Glass: the pane thickens until its words read as on the Solid surface it stands for, over the ground and, while
    // it blurs a picture, over the picture's spots (GlassFinish; Dart thickenedOver, kit_surface_look.dart:814-889). A
    // clear pane starts from its thin tint, so it thickens only as far as its words need; no plate behind them. Its
    // rims, and a chosen pane's, are lit (glassRims).
    val decoration =
        if (!glass) {
            resolved?.faded(input.faceAlpha)
        } else {
            val ground = roles.ground.withAlpha(1f)
            val spots = if (blurs && backdrop != null) listOf(ground) + backdrop.frostedSpots else listOf(ground)
            val solid = (input.ownFill?.toArgb() ?: request.levelFill).over(ground)
            // A clear content pane (a card, a row group) keeps its first line of words below Glass's pane inset, where its
            // top sheen has mostly faded (GlassFinish.sheenAt); a chip's or a field's words may lie anywhere on it.
            val wordsDp = if (clear && input.role == SurfaceRole.Content) GlassFinish.PANE_WORDS_DP else 0f
            remember(resolved, spots, solid, roles, clear, request.look, input.role) { glassRims(GlassFinish.thickened(resolved!!, roles, solid, spots, inks = if (clear) GlassFinish.paneInks(roles, solid) else null, wordsDp = wordsDp), roles, clear, request.look, even = input.role == SurfaceRole.Field) }
        }
    // The frost under the face is decided by the decoration the surface settles on, never a fading one, so the tree
    // holds through a fade (kit_surface_look.dart:1257-1266).
    val frost = Modifier.kitSurfaceGlass(if (glass) decoration else null)
    // The plain look as a decoration, so Solid can fade to and from the other styles.
    val ownFill = input.ownFill?.let { if (input.faceAlpha >= 1f) it else it.copy(alpha = it.alpha * input.faceAlpha) }
    val plain = SurfaceDecoration(radii = radii, circle = input.shape == KitShapeId.Circle, fill = ownFill?.toArgb())
    val shown = rememberSurfaceFade(decoration, plain, style.style)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    if (shown == null) {
        if (decoration == null) {
            val own = ownFill
            val modifier = if (own == null) Modifier else Modifier.background(own, shape)
            return KitSurfaceLook(modifier, words, shape, own != null && own.alpha > 0f)
        }
        val sink = if (decoration.faceShiftDp > 0f) Modifier.graphicsLayer { translationY = decoration.faceShiftDp * density } else Modifier
        val face = if (glass) frost.kitGlassFace(decoration) else Modifier.kitDecorated(decoration, rtl)
        return KitSurfaceLook(face.then(sink), words, shape, decoration.paints(), dressed = true)
    }
    // Mid-fade: the in-between look, painted the Glass way when either end is Glass.
    val (mid, glassy) = shown
    val sink = if (mid.faceShiftDp > 0f) Modifier.graphicsLayer { translationY = mid.faceShiftDp * density } else Modifier
    val face = if (glassy) frost.kitGlassFace(mid) else Modifier.kitDecorated(mid, rtl)
    return KitSurfaceLook(face.then(sink), words, shape, mid.paints() || (decoration ?: plain).paints(), dressed = decoration != null)
}

/**
 * The style's face for a part that otherwise draws a look of its own (the jump pill, the person's bubble, code, menus,
 * the message box's chips): non-null only where the style dresses every part (`StyleTokens.dressesOwnLooks`: Brutal's
 * ink edge and hard shadow, Paper's rules and square corners), so one screen reads as one school. Null keeps the part's
 * own look pixel for pixel (Solid, Soft, Glass, Signal). The [input]'s own fill is ignored: the part draws it itself.
 */
@Composable
public fun kitDressedLook(input: KitSurfaceInput): KitSurfaceLook? =
    kitSurfaceLook(input.copy(ownLook = true, ownFill = null)).takeIf { it.dressed }

/**
 * The style's decoration as data, for a part that draws its own frame by hand (a code block's open-sided chunks, a
 * table's grid, a diff's face): the fill, edge and corners the style gives a [role] surface of [shape] with the part's
 * own [color], where the style dresses every part ([kitDressedLook]); null keeps the part's own look. No fade.
 */
@Composable
public fun kitDressedDecoration(role: SurfaceRole, shape: KitShapeId, level: SurfaceLevel = SurfaceLevel.Surface1, color: Color? = null): SurfaceDecoration? {
    val style = LocalKitStyle.current
    if (!style.spec.tokens.dressesOwnLooks) return null
    val roles = LocalKitRoles.current
    val r = StyleResolver.corner(shape.radiusDp(), style.knobs.corners, style.spec.tokens.sharpRadius)
    val request =
        SurfaceRequest(
            roles = roles,
            role = role,
            knobs = style.knobs,
            level = level,
            radii = Radii.all(if (shape == KitShapeId.Square) 0f else r),
            circle = shape == KitShapeId.Circle,
            color = color?.toArgb(),
        )
    return remember(request, style) { style.spec.resolve(request) }
}

/** The decoration's edge as one colour (its start), or null when it draws none. */
public val SurfaceDecoration.edgeColor: Color?
    get() = if (edgeWidthDp > 0f) (edgeStart ?: edgeEnd)?.let { Color(it.v) } else null

private fun SurfaceDecoration.paints(): Boolean =
    fill?.let { it.v ushr 24 != 0 } == true ||
        (edgeWidthDp > 0f &&
            (edgeStart?.let { it.v ushr 24 != 0 } == true ||
                edgeEnd?.let { it.v ushr 24 != 0 } == true)) ||
        shadows.isNotEmpty() || hardShadow?.let { it.v ushr 24 != 0 } == true ||
        paired?.let { it.distanceDp > 0f && (it.light.v ushr 24 != 0 || it.dark.v ushr 24 != 0) } == true

/** One surface's cross-fade (Dart `_KitSurfaceBoxState.didUpdateWidget`, kit_surface_look.dart:1207-1229). */
private class SurfaceFadeState(var to: SurfaceDecoration?, var style: Style, var theme: Pair<Roles, KitColors>) {
    var from: SurfaceDecoration? = null
    var fromGlass: Boolean = false
    var ms: Int = 0
    var pending: Boolean = false
    var token: Int = 0
    val progress: Animatable<Float, AnimationVector1D> = Animatable(1f)
}

/**
 * The look to paint while [target] fades in, with whether to paint it the Glass way; null at rest (paint [target]
 * itself). A change of Style fades over 200 ms ([SurfaceFade.STYLE_FADE_MS]); a state change (pressed, chosen) fades
 * over 120 ms under Glass only ([SurfaceFade.STATE_FADE_MS]; the other styles keep their instant state changes);
 * reduced motion and motion Off change at once (`Duration.zero`). The curve is Flutter's pulse
 * (kit_surface_look.dart:1238). A theme change (new colours) changes at once too: the theme cover already fades the
 * screen, and a part kept out of that fade (the mode control, s33) would otherwise show a grey mid-fade face.
 */
@Composable
private fun rememberSurfaceFade(target: SurfaceDecoration?, plain: SurfaceDecoration, style: Style): Pair<SurfaceDecoration, Boolean>? {
    val reduced = LocalKitMotion.current.reduced
    // The roles change on the commit, the kit's colours a frame later (after the theme cover's capture): either is a
    // theme change.
    val theme = LocalKitRoles.current to LocalKitColors.current
    val fade = remember { SurfaceFadeState(target, style, theme) }
    if (fade.to != target || fade.style != style) {
        val styleChanged = fade.style != style
        val themeChanged = fade.theme.first != theme.first || fade.theme.second != theme.second
        val ms =
            when {
                reduced || themeChanged -> 0
                styleChanged -> SurfaceFade.STYLE_FADE_MS
                style == Style.Glass -> SurfaceFade.STATE_FADE_MS
                else -> 0
            }
        val now = fade.current(plain)
        fade.fromGlass = fade.style == Style.Glass || (fade.fromGlass && now != null)
        fade.from = if (ms > 0) (now ?: fade.to ?: plain) else null
        fade.to = target
        fade.style = style
        fade.ms = ms
        fade.pending = ms > 0
        fade.token++
    }
    fade.theme = theme
    // A surface that never changes look launches nothing.
    if (fade.token > 0) {
        LaunchedEffect(fade.token) {
            if (fade.pending) {
                fade.pending = false
                fade.progress.snapTo(0f)
                fade.progress.animateTo(1f, tween(fade.ms, easing = KitEasing.Pulse))
            } else {
                fade.progress.snapTo(1f)
            }
            fade.from = null
        }
    }
    // The progress is read on every pass, also the one that starts a fade: a pass that skipped the read would not be
    // run again as the fade advances, and the surface would stay on its old look (the owner's Corners knob drew the
    // last corners' face under the new clip and ring, 2026-11-08).
    val progress = fade.progress.value
    val from = fade.from ?: return null
    val t = if (fade.pending) 0f else progress
    if (t >= 1f) return null
    return SurfaceFade.between(from, target ?: plain, t) to (fade.fromGlass || style == Style.Glass)
}

/** The look on screen now, mid-fade; null at rest. */
private fun SurfaceFadeState.current(plain: SurfaceDecoration): SurfaceDecoration? {
    val from = from ?: return null
    val t = if (pending) 0f else progress.value
    if (t >= 1f) return null
    return SurfaceFade.between(from, to ?: plain, t)
}

internal fun SurfaceDecoration.shape(): Shape =
    if (circle) CircleShape else RoundedCornerShape(radii.topStart.dp, radii.topEnd.dp, radii.bottomEnd.dp, radii.bottomStart.dp)

/**
 * Paints a style decoration: shadows behind, then the fill, then the edge. A shadow with blur is a real Gaussian
 * shadow ([dropShadow], the same blur Flutter's `BoxShadow` draws), so it fades out smoothly and never ends in hard
 * bands; a shadow without blur (Brutal's offset block, Signal's rule, a focus ring) stays a crisp shape. [rtl] mirrors
 * the shadows' sideways offsets (the hard shadows read the layout direction themselves).
 */
public fun Modifier.kitDecorated(d: SurfaceDecoration, rtl: Boolean = false): Modifier {
    val shape = d.shape()
    val sign = if (rtl) -1f else 1f
    val all = buildList {
        addAll(d.shadows)
        d.paired?.let { p ->
            if (p.inset <= 0f && (p.distanceDp > 0f || p.blurDp > 0f)) {
                add(Shadow(-p.distanceDp, -p.distanceDp, p.blurDp, p.light))
                add(Shadow(p.distanceDp, p.distanceDp, p.blurDp, p.dark))
            }
        }
    }.filter { it.color.v ushr 24 != 0 }
    val (soft, hard) = all.partition { it.blurDp > 0f }
    var m = this
    soft.forEach { s ->
        m = m.dropShadow(
            shape,
            ComposeShadow(
                radius = s.blurDp.dp,
                color = s.color.toColor(),
                spread = s.spreadDp.dp,
                offset = DpOffset((s.dxDp * sign).dp, s.dyDp.dp),
            ),
        )
    }
    return m.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val ltr = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
        hard.forEach { s -> drawShadow(s, shape, ltr) }
        d.hardShadow?.let { block ->
            if (d.hardOffsetDp > 0f) {
                val o = d.hardOffsetDp.dp.toPx()
                translate(o * ltr, o) { drawOutline(shape.createOutline(size, layoutDirection, this), block.toColor()) }
            }
        }
        d.fill?.let { drawOutline(outline, it.toColor()) }
        d.paired?.let { p ->
            if (p.inset > 0f) {
                // Pressed in: the dark edge lies along the top start, the light along the bottom end.
                val w = (p.distanceDp.coerceAtLeast(1.5f)).dp.toPx()
                drawOutline(outline, Brush.linearGradient(listOf(p.dark.toColor(), Color.Transparent, p.light.toColor())), style = Stroke(w))
            }
        }
        if (d.edgeWidthDp > 0f) {
            val start = d.edgeStart ?: d.edgeEnd
            val end = d.edgeEnd ?: d.edgeStart
            if (start != null && end != null) {
                val w = d.edgeWidthDp.dp.toPx()
                val rest = d.edgeRest
                if (start == end && rest == null) drawOutline(outline, start.toColor(), style = Stroke(w))
                else drawOutline(outline, Brush.linearGradient(listOfNotNull(start.toColor(), rest?.toColor(), end.toColor())), style = Stroke(w))
            }
        }
    }
}

internal fun DrawScope.drawSurfaceShadow(s: Shadow, shape: Shape, rtl: Float) {
    val blur = s.blurDp.dp.toPx()
    val spread = s.spreadDp.dp.toPx()
    val color = s.color.toColor()
    translate(s.dxDp.dp.toPx() * rtl, s.dyDp.dp.toPx()) {
        if (blur <= 0f) {
            drawGrown(shape, spread, color)
        } else {
            val steps = 6
            for (i in 0 until steps) {
                val grow = spread + blur * (i.toFloat() / steps - .25f)
                drawGrown(shape, grow, color.copy(alpha = color.alpha / steps * (1f - i.toFloat() / steps) * 2f))
            }
        }
    }
}

/** A shadow without blur: the shape grown by its spread, moved by its offset. */
private fun DrawScope.drawShadow(s: Shadow, shape: Shape, rtl: Float) {
    translate(s.dxDp.dp.toPx() * rtl, s.dyDp.dp.toPx()) {
        drawGrown(shape, s.spreadDp.dp.toPx(), s.color.toColor())
    }
}

/**
 * How far the style in force paints past a [role] surface's edge, in dp (offset + blur + spread of its farthest
 * shadow; 0 for Solid). A container that clips (a sideways scroller, a faded edge) keeps this much room so the
 * shadows of the parts inside it are never cut into a hard rectangle.
 */
@Composable
public fun kitShadowReach(role: SurfaceRole = SurfaceRole.Control): Dp {
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val reach = remember(style, roles, role) {
        val decoration =
            style.spec.resolve(SurfaceRequest(roles = roles, role = role, knobs = style.knobs, radii = Radii.all(8f)))
        if (decoration == null) {
            0f
        } else {
            // A blurred shadow still shows past its blur radius (the radius is about 1.7 sigma): half a radius more
            // takes it to about 2.6 sigma, under one colour step, so a clip there draws no line.
            val shadows = decoration.shadows.map { maxOf(kotlin.math.abs(it.dxDp), kotlin.math.abs(it.dyDp)) + it.blurDp * BLUR_TAIL + it.spreadDp }
            val paired = decoration.paired?.takeIf { it.inset <= 0f }?.let { it.distanceDp + it.blurDp * BLUR_TAIL } ?: 0f
            maxOf(shadows.maxOrNull() ?: 0f, paired, decoration.hardOffsetDp)
        }
    }
    return Dp(kotlin.math.ceil(reach))
}

private const val BLUR_TAIL = 1.5f

/**
 * Grows a clipping container by [room] above and below without changing the space it takes: the content is laid out
 * [room] taller and placed [room] higher, so a clip applied after this modifier reaches past the reported bounds. Pad
 * the content by the same [room] vertically. Used with [kitShadowReach] so style shadows inside scrollers never clip.
 *
 * Device pass 2026-11-13 (Soft): a sideways scroller cut its chips' shadows at its ends too (the "Ask first" chip's
 * shadow ended in a straight vertical edge where the strip hugging it ended). [start] and [end] grow it sideways the
 * same way (pad the scrolling content by the same amounts at its ends), so a chip resting at an end keeps its shadow.
 */
public fun Modifier.kitShadowRoom(room: Dp, start: Dp = Dp(0f), end: Dp = Dp(0f)): Modifier =
    if (room <= Dp(0f) && start <= Dp(0f) && end <= Dp(0f)) this
    else layout { measurable, constraints ->
        val r = room.roundToPx().coerceAtLeast(0)
        val s = start.roundToPx().coerceAtLeast(0)
        val e = end.roundToPx().coerceAtLeast(0)
        val grown =
            constraints.copy(
                minWidth = constraints.minWidth + s + e,
                maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + s + e else constraints.maxWidth,
                minHeight = constraints.minHeight + 2 * r,
                maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + 2 * r else constraints.maxHeight,
            )
        val placeable = measurable.measure(grown)
        layout((placeable.width - s - e).coerceAtLeast(0), (placeable.height - 2 * r).coerceAtLeast(0)) { placeable.placeRelative(-s, -r) }
    }

private fun DrawScope.drawGrown(shape: Shape, grow: Float, color: Color) {
    val w = size.width + 2 * grow
    val h = size.height + 2 * grow
    if (w <= 0f || h <= 0f) return
    translate(-grow, -grow) {
        drawOutline(shape.createOutline(androidx.compose.ui.geometry.Size(w, h), layoutDirection, this), color)
    }
}

/** How much inside space a [KitSurface] gives its child: none, compact (space 3) or panel (space 4). */
public enum class KitSurfacePadding { None, Compact, Panel }

private fun KitSurfacePadding.dp(): Dp =
    when (this) {
        KitSurfacePadding.None -> Dp(0f)
        KitSurfacePadding.Compact -> Dp(Space.S3.toFloat()) // F: lib/ui/kit/kit_surface.dart:187 lib/ui/kit/kit_tokens.dart:40-44
        KitSurfacePadding.Panel -> Dp(Space.S4.toFloat()) // F: lib/ui/kit/kit_surface.dart:188 lib/ui/kit/kit_tokens.dart:40-44
    }

private fun argbColor(v: Argb): Color = Color(v.v)

/**
 * The one solid box (Dart `KitSurface`, kit_surface.dart): a fill from the surface steps, a token [shape] and token
 * [padding], with an optional hairline [outlined] edge (LOOK-21). Not interactive: a surface that opens something wraps
 * in `KitTappable`. [flatInLists] draws no fill, edge or padding where the style sets lists flat on the page.
 *
 * Motion: the style change fades the decoration (class Fade)
 * Cap: wrapper (it adds no width of its own)
 */
@Composable
public fun KitSurface(
    modifier: Modifier = Modifier,
    level: SurfaceLevel = SurfaceLevel.Surface1,
    shape: KitShapeId = KitShapeId.Panel,
    padding: KitSurfacePadding = KitSurfacePadding.Panel,
    outlined: Boolean = false,
    clip: Boolean = true,
    flatInLists: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (flatInLists && LocalKitStyle.current.spec.tokens.flatLists) {
        Box(modifier) { content() }
        return
    }
    SurfaceBox(modifier, level, shape, padding, outlined, clip, inset = false, content = content)
}

@Composable
private fun SurfaceBox(
    modifier: Modifier,
    level: SurfaceLevel,
    shape: KitShapeId,
    padding: KitSurfacePadding,
    outlined: Boolean,
    clip: Boolean,
    inset: Boolean,
    role: SurfaceRole = SurfaceRole.Content,
    content: @Composable () -> Unit,
) {
    val roles = LocalKitRoles.current
    val own =
        when {
            inset -> if (roles.dark) roles.surface1 else roles.ground // F: lib/ui/kit/kit_tokens.dart insetSurface, surface1 in dark and ground in light
            level == SurfaceLevel.Ground -> roles.ground
            level == SurfaceLevel.Surface1 -> roles.surface1
            level == SurfaceLevel.Surface2 -> roles.surface2
            else -> roles.surface3
        }
    val surfaceRole = if (level == SurfaceLevel.Ground && role == SurfaceRole.Content) SurfaceRole.Ground else role
    val look = kitSurfaceLook(KitSurfaceInput(role = surfaceRole, shape = shape, level = level, ownFill = argbColor(own), pressed = inset))
    val hairline = kitHairline()
    var box = modifier.then(look.modifier)
    if (outlined) box = box.kitHairlineBorder(hairline, argbColor(roles.hairline), look.shape)
    if (clip) box = box.clip(look.shape)
    // Glass keeps its pane inset beyond the surface's own padding (owner 2026-11-09: room for the rim and the sheen).
    val inset = if (padding == KitSurfacePadding.None) 0.dp else kitPaneInset()
    Box(box.padding(padding.dp() + inset)) { content() }
}

/**
 * A grouped panel (Dart `KitSurface.panel`): `surface1`, the panel shape, panel padding, and an optional one-line header
 * (a small [icon] in `text2` and a headline [title]) above [content].
 */
@Composable
public fun KitSurfacePanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    padding: KitSurfacePadding = KitSurfacePadding.Panel,
    content: @Composable () -> Unit,
) {
    SurfaceBox(modifier, SurfaceLevel.Surface1, KitShapeId.Panel, padding, outlined = false, clip = true, inset = false) {
        PanelBody(title, icon, content)
    }
}

/** A panel set into a sheet or a dialog (Dart `KitSurface.inset`): surface1 in dark, the ground in light, pressed in under Soft. */
@Composable
public fun KitSurfaceInset(
    modifier: Modifier = Modifier,
    padding: KitSurfacePadding = KitSurfacePadding.Panel,
    content: @Composable () -> Unit,
) {
    SurfaceBox(modifier, SurfaceLevel.Surface1, KitShapeId.Panel, padding, outlined = false, clip = true, inset = true, content = content)
}

/** A leading icon in its tile (Dart `KitSurface.tile`): the icon tile of `surface3`, a small glyph in [tone]. */
@Composable
public fun KitSurfaceTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: TextTone = TextTone.Primary,
    semanticsLabel: String? = null,
) {
    val tokens = LocalKitTokens.current
    val color = LocalKitColors.current.tone(tone)
    val size = Dp(tokens.iconTile.toFloat())
    SurfaceBox(
        modifier.size(size).then(if (semanticsLabel != null) Modifier.semantics { contentDescription = semanticsLabel } else Modifier),
        SurfaceLevel.Surface3,
        KitShapeId.Tile,
        KitSurfacePadding.None,
        outlined = false,
        clip = true,
        inset = false,
        role = SurfaceRole.Control,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { KitGlyph(icon, color, Dp(tokens.smallIcon.toFloat())) }
    }
}

@Composable
private fun PanelBody(title: String?, icon: ImageVector?, content: @Composable () -> Unit) {
    if (title == null && icon == null) {
        content()
        return
    }
    val tokens = LocalKitTokens.current
    Column {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                KitGlyph(icon, LocalKitColors.current.text2, Dp(tokens.smallIcon.toFloat()))
                Spacer(Modifier.width(Dp(tokens.labelGap.toFloat())))
            }
            if (title != null) Box(Modifier.weight(1f)) { KitText(title, role = TypeRole.Headline) }
        }
        Spacer(Modifier.height(Dp(tokens.labelGap.toFloat())))
        content()
    }
}

/**
 * A border of exactly [width], painted inside [shape] the way Flutter's `Border.all` (stroke aligned inside) paints it.
 * Compose's `Modifier.border` rounds a stroke up to a whole pixel, so a one-physical-pixel hairline at a pixel ratio
 * above the picture's density (the golden gate renders a DPR-3 scene into a 1x picture) came out three times as heavy as
 * Flutter's. Here a sub-pixel width stays sub-pixel (antialiased), and at a device's own density it is the same one
 * physical pixel `Modifier.border` draws. F: lib/ui/kit/kit_tokens.dart:315-318 (hairlineWidth).
 */
public fun Modifier.kitHairlineBorder(width: Dp, color: Color, shape: Shape): Modifier =
    drawWithCache {
        val w = width.toPx()
        if (w <= 0f || color.alpha == 0f || size.minDimension <= w) return@drawWithCache onDrawWithContent { drawContent() }
        val inner = Size(size.width - w, size.height - w)
        val outline = shape.createOutline(inner, layoutDirection, this)
        val stroke = Stroke(w)
        onDrawWithContent {
            drawContent()
            translate(w / 2f, w / 2f) { drawOutline(outline, color, style = stroke) }
        }
    }
