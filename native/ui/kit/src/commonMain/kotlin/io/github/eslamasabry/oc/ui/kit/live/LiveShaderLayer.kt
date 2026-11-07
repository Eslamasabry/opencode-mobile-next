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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.max
import kotlin.math.roundToInt

/** The theme colours a look is drawn in (kit colour roles, so every look follows light, dark and the accent). */
internal data class LivePalette(val ground: Color, val accent: Color, val partner: Color, val glow: Color, val dark: Boolean)

@Composable
internal fun rememberLivePalette(): LivePalette {
    val c = LocalKitColors.current
    return remember(c) { LivePalette(c.ground, c.accent, c.partner, lerp(c.accent, c.text1, GLOW_TOWARD_TEXT), c.dark) }
}

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
    val program = remember(look, platform) { platform.liveShader(LiveShaderLib.PRELUDE + look.shader) }
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
                    center = Offset(size.width * .15f, size.height * .12f),
                    radius = r * .7f,
                )
            val bottom =
                Brush.radialGradient(
                    listOf(palette.partner.copy(alpha = a * .8f), Color.Transparent),
                    center = Offset(size.width * .9f, size.height * .85f),
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

/** Share of render pixels per side the shader runs at. */
internal const val RENDER_SCALE: Float = .5f

/** The moment a still frame shows: a calm, well-composed point of every look. */
internal const val STILL_SECONDS: Float = 40f

private const val GLOW_TOWARD_TEXT: Float = .3f
private const val STILL_ALPHA: Float = .10f
private const val STILL_ALPHA_MAX: Float = .16f
