package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.shadow.Shadow as ComposeShadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpOffset
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.design.effects.SurfaceDecoration as EffectsSurfaceDecoration
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest as EffectsSurfaceRequest
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole as EffectsSurfaceRole
import io.github.eslamasabry.oc.ui.design.style.Style

/**
 * What the style in force says about one surface (plan 06 §4.3, hooks H1-H11): [modifier] paints the style's fill, edge
 * and shadows (or, for Solid, the part's own [KitSurfaceInput.ownFill]), [words] is the colour of the words on it
 * (hook H3, null = the part's own), [faceShift] sinks the face while pressed (hook H2).
 */
@Immutable
public data class KitSurfaceLook(val modifier: Modifier, val words: Color?, val shape: Shape)

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
)

private fun Color.toKitArgb(): Argb = Argb(
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
    val shape = input.shape.shape()
    val sharp = style.spec.tokens.sharpRadius
    val base = input.shape.radiusDp()
    val sheet = input.shape == KitShapeId.Sheet
    val r = StyleResolver.corner(base, style.knobs.corners, sharp)
    val radii = if (sheet) Radii(r, r, 0f, 0f) else Radii.all(if (input.shape == KitShapeId.Square) 0f else r)
    val request =
        SurfaceRequest(
            roles = roles,
            role = input.role,
            knobs = style.knobs,
            level = input.level,
            radii = radii,
            circle = input.shape == KitShapeId.Circle,
            color = input.color?.toKitArgb(),
            selected = input.selected,
            pressed = input.pressed,
            disabled = input.disabled,
            withinGroup = input.withinGroup,
            action = input.action,
        )
    val decoration = remember(request, style) { style.spec.resolve(request) }
    val words = remember(request, style) { style.spec.onColor(request)?.toColor() }
    if (decoration == null) {
        val own = input.ownFill
        val modifier = if (own == null) Modifier else Modifier.background(own, shape)
        return KitSurfaceLook(modifier, words, shape)
    }
    val sink = if (decoration.faceShiftDp > 0f) Modifier.graphicsLayer { translationY = decoration.faceShiftDp * density } else Modifier
    return KitSurfaceLook(Modifier.kitDecorated(decoration).then(sink), words, shape)
}

private fun SurfaceDecoration.shape(): Shape =
    if (circle) CircleShape else RoundedCornerShape(radii.topStart.dp, radii.topEnd.dp, radii.bottomEnd.dp, radii.bottomStart.dp)

/** Paints a style decoration: shadows behind, then the fill, then the edge. Blur is drawn as stacked soft layers. */
public fun Modifier.kitDecorated(d: SurfaceDecoration): Modifier =
    drawBehind {
        val shape = d.shape()
        val outline = shape.createOutline(size, layoutDirection, this)
        val rtl = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
        d.shadows.forEach { s -> drawShadow(s, shape, rtl) }
        d.paired?.let { p ->
            if (p.inset <= 0f && (p.distanceDp > 0f || p.blurDp > 0f)) {
                drawShadow(Shadow(-p.distanceDp, -p.distanceDp, p.blurDp, p.light), shape, rtl)
                drawShadow(Shadow(p.distanceDp, p.distanceDp, p.blurDp, p.dark), shape, rtl)
            }
        }
        d.hardShadow?.let { hard ->
            if (d.hardOffsetDp > 0f) {
                val o = d.hardOffsetDp.dp.toPx()
                translate(o * rtl, o) { drawOutline(shape.createOutline(size, layoutDirection, this), hard.toColor()) }
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
                if (start == end) drawOutline(outline, start.toColor(), style = Stroke(w))
                else drawOutline(outline, Brush.linearGradient(listOf(start.toColor(), end.toColor())), style = Stroke(w))
            }
        }
    }

private fun DrawScope.drawShadow(s: Shadow, shape: Shape, rtl: Float) {
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

private fun DrawScope.drawGrown(shape: Shape, grow: Float, color: Color) {
    val w = size.width + 2 * grow
    val h = size.height + 2 * grow
    if (w <= 0f || h <= 0f) return
    translate(-grow, -grow) {
        drawOutline(shape.createOutline(androidx.compose.ui.geometry.Size(w, h), layoutDirection, this), color)
    }
}

// ---- Port-kit surface rules (design.effects): the parts ported with the screen units paint through these. ----
/** Resolve one real part's Solid paint through the chosen style (Flutter KitSurfaceLook). */
@Composable
public fun kitSurfaceLook(
    request: EffectsSurfaceRequest,
    fill: Color,
    edge: Color = Color.Transparent,
    edgeWidth: Float = 0f,
): EffectsSurfaceDecoration {
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val solid = EffectsSurfaceDecoration(Argb(fill.toArgb()), Argb(edge.toArgb()), edgeWidth = edgeWidth)
    val nested = LocalSurfaceDepth.current > 0
    return remember(style, roles, request, solid, nested) {
        if (nested && request.role == EffectsSurfaceRole.Content && style.style != Style.Solid) EffectsSurfaceDecoration(Argb(0))
        else style.resolve(request, roles, solid).let { look ->
            if (nested && style.style == Style.Brutal) look.copy(hardOffset = 0f, faceShift = 0f) else look
        }
    }
}

/**
 * Fill, own-shape shadows, inset wells and the lit/ink edge. The hard shadow falls toward the reading end; the
 * face and its content sink into it while pressed. No platform elevation or Material tonal overlay is involved.
 */
@Composable
public fun Modifier.kitSurface(look: EffectsSurfaceDecoration, shape: Shape, clip: Boolean = true): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = KitMotion.specs
    val fill by animateColorAsState(Color(look.fill.v), motion.surface(), label = "kit-surface-fill")
    val edge by animateColorAsState(Color(look.edge.v), motion.surface(), label = "kit-surface-edge")
    val end by animateColorAsState(Color(look.edgeEnd.v), motion.surface(), label = "kit-surface-edge-end")
    var result = this
    if (look.hardOffset > 0f && look.hardShadow.alpha > 0) result = result.dropShadow(
        shape,
        ComposeShadow(radius = 0.dp, color = Color(look.hardShadow.v), offset = DpOffset((if (rtl) -look.hardOffset else look.hardOffset).dp, look.hardOffset.dp)),
    )
    look.shadows.filter { !it.inset }.forEach { shadow ->
        result = result.dropShadow(shape, ComposeShadow(radius = shadow.blur.dp, spread = shadow.spread.dp, color = Color(shadow.color.v), offset = DpOffset(shadow.dx.dp, shadow.dy.dp)))
    }
    if (look.faceShift != 0f) result = result.graphicsLayer {
        translationX = if (rtl) -look.faceShift.dp.toPx() else look.faceShift.dp.toPx()
        translationY = look.faceShift.dp.toPx()
    }
    result = result.background(fill, shape)
    look.shadows.filter { it.inset }.forEach { shadow ->
        result = result.innerShadow(shape, ComposeShadow(radius = shadow.blur.dp, spread = shadow.spread.dp, color = Color(shadow.color.v), offset = DpOffset(shadow.dx.dp, shadow.dy.dp)))
    }
    if (look.edgeWidth > 0f) result = result.border(look.edgeWidth.dp, Brush.linearGradient(listOf(edge, end)), shape)
    return if (clip) result.clip(shape) else result
}

@Composable
public fun Modifier.kitSurface(
    role: EffectsSurfaceRole,
    shape: KitShapeId,
    fill: Color,
    edge: Color = Color.Transparent,
    edgeWidth: Float = 0f,
    request: EffectsSurfaceRequest = EffectsSurfaceRequest(role),
): Modifier = kitSurface(kitSurfaceLook(request, fill, edge, edgeWidth), shape.toShape())

/** Words on an inverted/raised face follow the spec while attention keeps its own meaning. */
@Composable
public fun KitSurfaceContent(look: EffectsSurfaceDecoration, content: @Composable () -> Unit) {
    val colors = LocalKitColors.current
    val ink = look.words?.let { Color(it.v) }
    if (ink == null) content() else CompositionLocalProvider(LocalKitColors provides colors.copy(text1 = ink, text2 = ink, accent = ink, onAccent = ink)) { content() }
}

private val LocalSurfaceDepth = staticCompositionLocalOf { 0 }

/** Non-Solid content nested in a pane shares its parent's face; only the outer Brutal surface casts. */
@Composable
public fun KitSurfaceScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSurfaceDepth provides LocalSurfaceDepth.current + 1) { content() }
}
