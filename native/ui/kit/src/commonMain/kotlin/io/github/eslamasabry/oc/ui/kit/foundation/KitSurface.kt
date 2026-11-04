package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.effects.SurfaceDecoration
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle

/** Resolve one real part's Solid paint through the chosen style (Flutter KitSurfaceLook). */
@Composable
public fun kitSurfaceLook(
    request: SurfaceRequest,
    fill: Color,
    edge: Color = Color.Transparent,
    edgeWidth: Float = 0f,
): SurfaceDecoration {
    val style = LocalKitStyle.current
    val roles = LocalKitRoles.current
    val solid = SurfaceDecoration(Argb(fill.toArgb()), Argb(edge.toArgb()), edgeWidth = edgeWidth)
    return remember(style, roles, request, solid) { style.resolve(request, roles, solid) }
}

/**
 * Fill, own-shape shadows, inset wells and the lit/ink edge. The hard shadow falls toward the reading end; the
 * face and its content sink into it while pressed. No platform elevation or Material tonal overlay is involved.
 */
@Composable
public fun Modifier.kitSurface(look: SurfaceDecoration, shape: Shape, clip: Boolean = true): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = KitMotion.specs
    val fill by animateColorAsState(Color(look.fill.v), motion.surface(), label = "kit-surface-fill")
    val edge by animateColorAsState(Color(look.edge.v), motion.surface(), label = "kit-surface-edge")
    val end by animateColorAsState(Color(look.edgeEnd.v), motion.surface(), label = "kit-surface-edge-end")
    var result = this
    if (look.hardOffset > 0f && look.hardShadow.alpha > 0) result = result.dropShadow(
        shape,
        Shadow(radius = 0.dp, color = Color(look.hardShadow.v), offset = DpOffset((if (rtl) -look.hardOffset else look.hardOffset).dp, look.hardOffset.dp)),
    )
    look.shadows.filter { !it.inset }.forEach { shadow ->
        result = result.dropShadow(shape, Shadow(radius = shadow.blur.dp, spread = shadow.spread.dp, color = Color(shadow.color.v), offset = DpOffset(shadow.dx.dp, shadow.dy.dp)))
    }
    if (look.faceShift != 0f) result = result.graphicsLayer {
        translationX = if (rtl) -look.faceShift.dp.toPx() else look.faceShift.dp.toPx()
        translationY = look.faceShift.dp.toPx()
    }
    result = result.background(fill, shape)
    look.shadows.filter { it.inset }.forEach { shadow ->
        result = result.innerShadow(shape, Shadow(radius = shadow.blur.dp, spread = shadow.spread.dp, color = Color(shadow.color.v), offset = DpOffset(shadow.dx.dp, shadow.dy.dp)))
    }
    if (look.edgeWidth > 0f) result = result.border(look.edgeWidth.dp, Brush.linearGradient(listOf(edge, end)), shape)
    return if (clip) result.clip(shape) else result
}

@Composable
public fun Modifier.kitSurface(
    role: SurfaceRole,
    shape: KitShapeId,
    fill: Color,
    edge: Color = Color.Transparent,
    edgeWidth: Float = 0f,
    request: SurfaceRequest = SurfaceRequest(role),
): Modifier = kitSurface(kitSurfaceLook(request, fill, edge, edgeWidth), shape.toShape())
