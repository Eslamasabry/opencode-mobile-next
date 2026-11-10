package io.github.eslamasabry.oc.ui.kit.platform

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec

/** The only blur in the app (owner rule R3; Konsist bans `RenderEffect` elsewhere). Blur, then saturation. */
@RequiresApi(31)
internal object AndroidFrost {
    fun effect(spec: FrostSpec, density: Float): RenderEffect {
        val radius = FrostPolicy.blurRadiusPx(spec.sigmaDp, density)
        val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        val saturate = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(spec.saturation) })
        return android.graphics.RenderEffect.createColorFilterEffect(saturate, blur).asComposeRenderEffect()
    }

    /**
     * Joined glass's lens pass: the join shader over its input blurred by [blurPx] (no saturation here: the shader
     * applies the glass's colour controls itself). API 33+.
     */
    @RequiresApi(33)
    fun join(shader: android.graphics.RuntimeShader, blurPx: Float): RenderEffect {
        val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        if (blurPx <= 0f) return lens.asComposeRenderEffect()
        val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        return android.graphics.RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
    }
}
