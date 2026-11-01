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
}
