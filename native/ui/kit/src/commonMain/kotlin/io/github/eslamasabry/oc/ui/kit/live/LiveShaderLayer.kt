package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws [look]'s shader as one rect over the whole node, in its own layer, at [RENDER_SCALE] of the node's pixels and
 * scaled up (the looks are soft; a quarter of the fragments keeps every look far inside its GPU budget). [time] is read
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
    Spacer(
        modifier
            .reducedResolution(RENDER_SCALE)
            .drawBehind {
                program.width = size.width
                program.height = size.height
                program.time = time()
                program.intensity = strength
                program.density = density * RENDER_SCALE
                program.dark = palette.dark
                program.setShared(palette)
                look.uniforms(program)
                drawRect(program.brush)
            }
    )
}

private fun LiveShaderProgram.setShared(p: LivePalette) {
    float2(LiveShaderLib.RES, width, height)
    float(LiveShaderLib.TIME, time)
    float(LiveShaderLib.INTENSITY, intensity)
    float(LiveShaderLib.DENSITY, density)
    float(LiveShaderLib.DARK, if (dark) 1f else 0f)
    color(LiveShaderLib.GROUND, p.ground)
    color(LiveShaderLib.ACCENT, p.accent)
    color(LiveShaderLib.PARTNER, p.partner)
    color(LiveShaderLib.GLOW, p.glow)
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
            val top =
                Brush.radialGradient(
                    listOf(palette.accent.copy(alpha = a), Color.Transparent),
                    center = Offset(size.width * TOP_X, size.height * TOP_Y),
                    radius = r * .7f,
                )
            val bottom =
                Brush.radialGradient(
                    listOf(palette.partner.copy(alpha = a * .8f), Color.Transparent),
                    center = Offset(size.width * BOTTOM_X, size.height * BOTTOM_Y),
                    radius = r * .6f,
                )
            onDrawBehind {
                drawRect(palette.ground)
                drawRect(top)
                drawRect(bottom)
            }
        }
    )
}

/** The time a look reads in draw: the clock while it [animates], else the still frame's moment. */
@Composable
internal fun rememberLiveTime(clock: LiveFrameClock, animates: Boolean): () -> Float =
    remember(clock, animates) {
        if (animates) {
            { clock.seconds + STILL_SECONDS }
        } else {
            { STILL_SECONDS }
        }
    }

/** Share of render pixels per side the shader runs at. */
internal const val RENDER_SCALE: Float = .5f

/** The moment a still frame shows: a calm, well-composed point of every look. */
internal const val STILL_SECONDS: Float = 40f

private const val TOP_X: Float = .15f
private const val TOP_Y: Float = .12f
private const val BOTTOM_X: Float = .9f
private const val BOTTOM_Y: Float = .85f
private const val STILL_ALPHA: Float = .10f
private const val STILL_ALPHA_MAX: Float = .16f
