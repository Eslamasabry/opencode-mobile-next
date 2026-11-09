package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderProgram
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Fluid glass (owner 2026-11-10, "some fluid glass merges"): a piece of glass that leaves another, or joins it, is one
 * body of liquid with it for a moment. The jump pill rises out of the message box as a drop whose neck thins and
 * lets go; a menu is drawn out of its bar's capsule and separates from it.
 *
 * The large glass (a [kitLiquidMergeBody]: the bar's capsule, the message box) and the piece ([KitLiquidDrop]) keep
 * drawing themselves. Between them the screen draws the bridge ([KitLiquidMergeLayer]): the smooth union of the two
 * rounded boxes (a signed-distance blend in a runtime shader, Android 13+), filled with the same blurred content and
 * tint the glass shows, with a light line along its free edges. How wide the liquid reaches is the drop's [pull]: 1
 * while it is leaving or arriving, easing to 0 at rest, where nothing is drawn. Where the platform has no runtime
 * shaders, or under reduced motion, the pieces move as before with no bridge.
 */

/** The reach of the liquid between a drop and its body at full pull, in dp (they join while closer than half of it). */
internal const val LIQUID_MERGE_REACH_DP: Float = 40f

/** The bridge's blur, sigma in dp (the frost the glass lets go to while it moves). */
private const val LIQUID_MERGE_BLUR_DP: Float = 8f

/** A large piece of glass a drop can leave or join: where it is in the window (px) and how round its corners are. */
@Stable
internal class KitLiquidMergeBody {
    var rect: Rect by mutableStateOf(Rect.Zero)
    var radius: Float by mutableFloatStateOf(0f)
    var tint: Color by mutableStateOf(Color.Transparent)
}

/**
 * A piece of glass leaving or joining the body named [body] ("bar", "bottom"): the part sets where it is now in the
 * window ([rect], px), its corner [radius] (px) and its [pull] (0 at rest, 1 while it leaves or arrives).
 */
@Stable
public class KitLiquidDrop internal constructor(body: String) {
    internal var body: String by mutableStateOf(body)
    public var rect: Rect by mutableStateOf(Rect.Zero)
    public var radius: Float by mutableFloatStateOf(0f)
    public var pull: Float by mutableFloatStateOf(0f)
}

/** One screen's fluid glass: its bodies by name and the drops near them. */
@Stable
public class KitLiquidMerge internal constructor() {
    internal val bodies = mutableStateMapOf<String, KitLiquidMergeBody>()
    internal val drops = mutableStateListOf<KitLiquidDrop>()
}

/** The screen's fluid glass; null where the screen draws none (no recording of its body, no runtime shaders). */
public val LocalKitLiquidMerge: ProvidableCompositionLocal<KitLiquidMerge?> = staticCompositionLocalOf { null }

/**
 * Names this part as the fluid glass's body [name] with corners of [radius]; its bridges take [role]'s glass tint.
 * Outside a screen that draws fluid glass: nothing.
 */
@Composable
public fun Modifier.kitLiquidMergeBody(name: String, radius: Dp, role: LiquidRole): Modifier {
    val merge = LocalKitLiquidMerge.current ?: return this
    val body = remember(merge, name) { KitLiquidMergeBody() }
    val dark = LocalKitRoles.current.dark
    body.radius = with(LocalDensity.current) { radius.toPx() }
    body.tint = LocalKitColors.current.surface2.copy(alpha = LiquidEdge.recipe(role, dark, kitGlassLook()).tint)
    DisposableEffect(merge, name, body) {
        merge.bodies[name] = body
        onDispose { if (merge.bodies[name] === body) merge.bodies.remove(name) }
    }
    return onGloballyPositioned { body.rect = it.boundsInWindow() }
}

/** A drop of the body named [body] while this part is composed; null where the screen draws no fluid glass. */
@Composable
public fun rememberKitLiquidDrop(body: String, on: Boolean = true): KitLiquidDrop? {
    val merge = LocalKitLiquidMerge.current
    if (merge == null || !on) return null
    val drop = remember(merge, body) { KitLiquidDrop(body) }
    DisposableEffect(merge, drop) {
        merge.drops += drop
        onDispose { merge.drops -= drop }
    }
    return drop
}

/** The rounded-box distance blend: its fill (mode 0) and the light along its free edges (mode 1). */
private const val MERGE_SHADER: String =
    """
uniform float4 a;
uniform float ra;
uniform float4 b;
uniform float rb;
uniform float k;
uniform float mode;
float box(float2 p, float4 r, float c) {
    float2 q = abs(p - r.xy) - r.zw + c;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - c;
}
half4 main(float2 p) {
    float da = box(p, a, ra);
    float db = box(p, b, rb);
    float h = max(k - abs(da - db), 0.0) / k;
    float d = min(da, db) - h * h * k * 0.25;
    if (mode < 0.5) { return half4(clamp(0.5 - d, 0.0, 1.0)); }
    float line = clamp(1.0 - abs(d + 0.75) / 1.25, 0.0, 1.0);
    float own = clamp(1.0 - min(abs(da), abs(db)) / 2.5, 0.0, 1.0);
    return half4(line * (1.0 - own));
}
"""

private class MergeLayers(val blur: GraphicsLayer, val mask: GraphicsLayer)

/**
 * Draws the bridges between this screen's drops and their bodies over the body's content and under its chrome:
 * [source]'s recording, blurred and tinted as the glass is, inside the smooth union of each pair.
 */
@Composable
internal fun BoxScope.KitLiquidMergeLayer(merge: KitLiquidMerge, source: KitFrostState, program: LiveShaderProgram, blur: RenderEffect?) {
    val layers = MergeLayers(rememberGraphicsLayer(), rememberGraphicsLayer())
    val origin = remember { arrayOf(Offset.Zero) }
    val dark = LocalKitRoles.current.dark
    val ground = LocalKitColors.current.ground.copy(alpha = 1f)
    val reach = with(LocalDensity.current) { LIQUID_MERGE_REACH_DP.dp.toPx() }
    Box(
        Modifier.matchParentSize()
            .onGloballyPositioned { origin[0] = it.positionInWindow() }
            .drawBehind {
                for (drop in merge.drops) {
                    val k = reach * drop.pull.coerceIn(0f, 1f)
                    val body = merge.bodies[drop.body] ?: continue
                    if (k < 1f || drop.rect.isEmpty || body.rect.isEmpty) continue
                    val a = body.rect.translate(-origin[0])
                    val b = drop.rect.translate(-origin[0])
                    // Only the room between and around the two can change: draw that box, not the screen.
                    val box = Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
                        .inflate(k)
                        .intersect(Rect(Offset.Zero, size))
                    if (box.isEmpty) continue
                    val at = Offset(floor(box.left), floor(box.top))
                    val px = IntSize(ceil(box.width).toInt().coerceAtLeast(1) + 1, ceil(box.height).toInt().coerceAtLeast(1) + 1)
                    val area = Size(px.width.toFloat(), px.height.toFloat())
                    fun uniforms(mode: Float) {
                        program.float4("a", a.center.x - at.x, a.center.y - at.y, a.width / 2f, a.height / 2f)
                        program.float("ra", minOf(body.radius, a.minDimension / 2f))
                        program.float4("b", b.center.x - at.x, b.center.y - at.y, b.width / 2f, b.height / 2f)
                        program.float("rb", minOf(drop.radius, b.minDimension / 2f))
                        program.float("k", k)
                        program.float("mode", mode)
                    }
                    layers.blur.renderEffect = blur
                    layers.blur.record(px) {
                        drawRect(ground)
                        val shift = source.sourceOrigin - origin[0] - at
                        translate(shift.x, shift.y) { drawLayer(source.layer) }
                    }
                    layers.mask.compositingStrategy = CompositingStrategy.Offscreen
                    layers.mask.record(px) {
                        drawLayer(layers.blur)
                        if (body.tint.alpha > 0f) drawRect(body.tint)
                        uniforms(0f)
                        drawRect(program.brush, size = area, blendMode = BlendMode.DstIn)
                    }
                    translate(at.x, at.y) {
                        drawLayer(layers.mask)
                        uniforms(1f)
                        drawRect(
                            program.brush,
                            size = area,
                            alpha = if (dark) .55f else .9f,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (dark) Color.White else Color.Black.copy(alpha = .16f)),
                            blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
                        )
                    }
                }
            }
            .testTag("kit-liquid-merge")
    )
}

/** The program and blur a screen's fluid glass draws with here; null where the platform has no runtime shaders. */
@Composable
internal fun rememberKitLiquidMergeProgram(): LiveShaderProgram? {
    val platform = LocalKitPlatform.current
    return remember(platform) { if (platform.liquidGlass) platform.liveShaders?.compile(MERGE_SHADER) else null }
}

@Composable
internal fun kitLiquidMergeBlur(): RenderEffect? {
    val platform = LocalKitPlatform.current
    val density = LocalDensity.current.density
    return remember(platform, density) { platform.frostEffect(FrostSpec(LIQUID_MERGE_BLUR_DP, 1f, 0f), density) }
}
