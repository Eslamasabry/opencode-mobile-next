package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
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
            color = input.color?.toArgb(),
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
    if (outlined) box = box.border(hairline, argbColor(roles.hairline), look.shape)
    if (clip) box = box.clip(look.shape)
    Box(box.padding(padding.dp())) { content() }
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
