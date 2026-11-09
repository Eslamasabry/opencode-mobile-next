package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws [look]'s shader as one rect over the whole node, in its own layer, at the look's own resolution
 * ([LiveResolution.of]): the line and dot looks at every screen pixel, the smooth fields at half per side, filtered up
 * into a full-size layer whose finish ([LiveShaderLib.FINISH]) decodes them and dithers at full resolution. [time] is read
 * only inside the draw block, so a clock step re-records this layer alone: nothing recomposes, nothing above redraws.
 * Where the platform has no runtime shaders, it draws [LiveStill] instead.
 */
@Composable
internal fun LiveShaderLayer(
    look: LiveLook,
    strength: Float,
    time: () -> Float,
    modifier: Modifier = Modifier,
) {
    val platform = LocalKitPlatform.current
    val program = remember(look, platform) { platform.liveShaders?.compile(LiveShaderLib.PRELUDE + look.shader) }
    val palette = rememberLivePalette()
    if (program == null) {
        LiveStill(palette, strength, modifier)
        return
    }
    val density = LocalDensity.current.density
    val scale = LiveResolution.of(look, platform.liveResolutionCap)
    // A reduced look is filtered up into a full-size layer whose finish decodes and dithers it at the screen's pixels.
    val finish = if (scale < 1f) remember(platform, palette.ground) { platform.liveFinish(palette.ground) } else null
    val encoded = finish != null
    Spacer(
        modifier
            .then(
                if (finish != null) {
                    Modifier.graphicsLayer {
                        renderEffect = finish
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                } else {
                    Modifier
                }
            )
            .then(if (scale < 1f) Modifier.reducedResolution(scale) else Modifier)
            .drawBehind {
                program.setSize(size.width, size.height, density * scale)
                program.setFrame(look, time(), strength, palette, encoded)
                drawRect(program.brush)
            }
    )
}

/**
 * [look]'s shader as a picture for Glass's panes to blur (owner 2026-11-09: in-flow panes show real glass over the
 * shader looks): the paint the glass backdrop records (`kitGlassBackdropSource`), drawing the ground and the shader at
 * the look's own resolution into one offscreen layer scaled up and finished, exactly as [LiveShaderLayer] draws it. The page shows the
 * backdrop's recording, so the look is drawn once per frame and every pane samples that one picture; [time] is read in
 * the paint, so a clock step re-records this paint alone, and a still look (Calm, reduced motion, a resting clock)
 * records nothing. Null where the platform has no runtime shaders.
 */
@Composable
internal fun rememberLiveShaderBackdrop(
    look: LiveLook,
    strength: Float,
    time: () -> Float,
    /** The theme root's shared pictures: pages of one size draw one reduced shader layer between them, once a frame. */
    pictures: io.github.eslamasabry.oc.ui.kit.foundation.KitGlassPictures? = null,
): (DrawScope.() -> Unit)? {
    val platform = LocalKitPlatform.current
    val program = remember(look, platform) { platform.liveShaders?.compile(LiveShaderLib.PRELUDE + look.shader) } ?: return null
    val palette = rememberLivePalette()
    val density = LocalDensity.current.density
    val own = rememberGraphicsLayer()
    val finished = rememberGraphicsLayer()
    val context = LocalGraphicsContext.current
    val scale = LiveResolution.of(look, platform.liveResolutionCap)
    val finish = if (scale < 1f) remember(platform, palette.ground) { platform.liveFinish(palette.ground) } else null
    return remember<DrawScope.() -> Unit>(program, palette, strength, density, own, time, look, pictures, context, finish, finished, scale) {
        {
            drawRect(palette.ground)
            val sw = max(1, (size.width * scale).roundToInt())
            val sh = max(1, (size.height * scale).roundToInt())
            val shared = pictures?.at(IntSize(size.width.toInt(), size.height.toInt()))
            val reduced = if (shared != null) shared.shader ?: context.createGraphicsLayer().also { shared.shader = it } else own
            val t = time()
            // Every input of this frame's pixels: a page that finds the shared layer already recorded from the same
            // inputs this frame draws it as it is; any change (look, time, strength, theme, size) records it again.
            val encoded = finish != null
            val key = ShaderFrame(look, t, strength, palette, sw, sh, density, encoded)
            if (shared == null || shared.shaderKey != key) {
                reduced.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                reduced.pivotOffset = Offset.Zero
                reduced.scaleX = size.width / sw
                reduced.scaleY = size.height / sh
                reduced.record(Density(density), LayoutDirection.Ltr, IntSize(sw, sh)) {
                    program.setSize(sw.toFloat(), sh.toFloat(), density * scale)
                    program.setFrame(look, t, strength, palette, encoded)
                    drawRect(program.brush)
                }
                shared?.shaderKey = key
            }
            if (finish != null) {
                // The finish: the reduced layer filtered up into a full-size layer, decoded and dithered at full resolution.
                finished.renderEffect = finish
                finished.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
                finished.record(Density(density), LayoutDirection.Ltr, IntSize(max(1, size.width.roundToInt()), max(1, size.height.roundToInt()))) {
                    drawLayer(reduced)
                }
                drawLayer(finished)
            } else {
                drawLayer(reduced)
            }
        }
    }
}

/** Everything one frame of a shader look is drawn from (see [rememberLiveShaderBackdrop]). */
private data class ShaderFrame(
    val look: LiveLook,
    val time: Float,
    val strength: Float,
    val palette: LivePalette,
    val width: Int,
    val height: Int,
    val density: Float,
    val encoded: Boolean,
)

/** The drawn size in render pixels and render pixels per dp; [setFrame] sends them with the frame. */
internal fun LiveUniforms.setSize(width: Float, height: Float, density: Float) {
    this.width = width
    this.height = height
    this.density = density
}

/**
 * Sets one frame's uniforms after [setSize]: the shared ones, then [look]'s own. The host calls it in its draw block;
 * the desktop stills and look tests call the same code, so they draw exactly what a device draws.
 */
internal fun LiveUniforms.setFrame(look: LiveLook, time: Float, strength: Float, palette: LivePalette, encoded: Boolean = false) {
    this.time = time
    this.intensity = strength
    this.dark = palette.dark
    setShared(palette)
    // A reduced pass with a full-resolution finish writes the look's distance from the ground, undithered.
    float(LiveShaderLib.ENCODE, if (encoded) 1f else 0f)
    look.uniforms(this)
}

private fun LiveUniforms.setShared(p: LivePalette) {
    float2(LiveShaderLib.RES, width, height)
    float(LiveShaderLib.TIME, time)
    float(LiveShaderLib.INTENSITY, intensity)
    float(LiveShaderLib.DENSITY, density)
    float(LiveShaderLib.DARK, if (dark) 1f else 0f)
    color(LiveShaderLib.GROUND, p.ground)
    color(LiveShaderLib.ACCENT, p.accent)
    color(LiveShaderLib.PARTNER, p.partner)
    color(LiveShaderLib.GLOW, p.glow)
    color(LiveShaderLib.INK, p.ink)
}

private fun LiveUniforms.color(name: String, c: Color) = float4(name, c.red, c.green, c.blue, 1f)

/**
 * Lays the child out at [scale] of the node's size and draws it scaled back up through an offscreen layer, so the
 * shader runs on `scale²` of the pixels and the result is filtered up by the GPU.
 */
private fun Modifier.reducedResolution(scale: Float): Modifier =
    layout { measurable, constraints ->
        val w = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: constraints.minWidth
        val h = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: constraints.minHeight
        val sw = max(1, (w * scale).roundToInt())
        val sh = max(1, (h * scale).roundToInt())
        val placeable = measurable.measure(Constraints.fixed(sw, sh))
        layout(w, h) {
            placeable.placeWithLayer(0, 0) {
                scaleX = w.toFloat() / sw
                scaleY = h.toFloat() / sh
                transformOrigin = TransformOrigin(0f, 0f)
                compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }

/**
 * The still fallback where the platform has no runtime shaders (Android 8–12): the ground with two soft glows in the
 * look's colours, drawn once and cached, never redrawn per frame.
 */
@Composable
internal fun LiveStill(palette: LivePalette, strength: Float, modifier: Modifier = Modifier) {
    val a = (STILL_ALPHA * strength).coerceIn(0f, STILL_ALPHA_MAX)
    Spacer(
        modifier.drawWithCache {
            val r = max(size.width, size.height)
            val top = Brush.radialGradient(*easedGlow(palette.accent, a), center = Offset(size.width * TOP_X, size.height * TOP_Y), radius = r * .7f)
            val bottom =
                Brush.radialGradient(*easedGlow(palette.partner, a * .8f), center = Offset(size.width * BOTTOM_X, size.height * BOTTOM_Y), radius = r * .6f)
            onDrawBehind {
                drawRect(palette.ground)
                drawRect(top)
                drawRect(bottom)
            }
        }
    )
}

/**
 * A glow's stops that ease to nothing (a Gaussian sampled at [EASED_STOPS] points, reaching zero exactly at the rim), so
 * the still's glows dissolve into the ground: two stops made a linear ramp whose end showed as a ring.
 */
internal fun easedGlow(color: Color, alpha: Float): Array<Pair<Float, Color>> =
    Array(EASED_STOPS) { i ->
        val x = i / (EASED_STOPS - 1f)
        val fall = (kotlin.math.exp(-x * x * 4.5f) - kotlin.math.exp(-4.5f)) / (1f - kotlin.math.exp(-4.5f))
        x to color.copy(alpha = alpha * fall)
    }

private const val EASED_STOPS: Int = 9

/**
 * The time a look reads in draw: the clock while it [animates], else the still frame's moment. With [fps] the time steps
 * at most that many times a second: a draw block that reads it is invalidated only when the step changes (a derived
 * state), so a faster screen does not redraw it more often.
 */
@Composable
internal fun rememberLiveTime(clock: LiveFrameClock, animates: Boolean, fps: Int = 0): () -> Float =
    remember<() -> Float>(clock, animates, fps) {
        when {
            !animates -> {
                { STILL_SECONDS }
            }
            fps <= 0 -> {
                { clock.seconds + STILL_SECONDS }
            }
            else -> {
                val stepped = derivedStateOf { floor(clock.seconds * fps) / fps }
                ({ stepped.value + STILL_SECONDS })
            }
        }
    }

/** The moment a still frame shows: a calm, well-composed point of every look. */
internal const val STILL_SECONDS: Float = 40f

private const val TOP_X: Float = .15f
private const val TOP_Y: Float = .12f
private const val BOTTOM_X: Float = .9f
private const val BOTTOM_Y: Float = .85f
private const val STILL_ALPHA: Float = .10f
private const val STILL_ALPHA_MAX: Float = .16f
