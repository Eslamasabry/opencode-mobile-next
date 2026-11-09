package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveResolution
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Light rays: soft shafts of accent light falling from above the top corner, slowly breathing and swaying, fading out
 * well before the lower half of the page.
 */
public object LightRaysLook : LiveLook {
    override val id: String = "lightRays"
    override val resolution: Float = LiveResolution.FULL // fine marks: crisp only at the screen's own pixels

    override val shader: String =
        LiveShaderLib.TINT +
            """
half4 main(float2 fragCoord) {
    float2 p = fragCoord / uDensity;
    float2 size = uRes / uDensity;
    float t = uTime;
    float2 src = float2(size.x * (0.18 + 0.04 * sin(t * 0.03)), -size.y * 0.12);
    float2 v = p - src;
    float ang = atan(v.x, v.y);           // 0 straight down
    float dist = length(v);
    float sway = t * 0.04;
    float rays = 0.5 + 0.5 * sin(ang * 23.0 + sway * 3.0);
    rays *= 0.55 + 0.45 * sin(ang * 37.0 - sway * 2.0 + 1.3);
    rays = rays * 0.7 + 0.3 * vnoise(float2(ang * 18.0, t * 0.05));
    rays = smoothstep(0.15, 0.95, rays);
    float fall = exp(-dist / (size.y * 0.55));
    float cone = smoothstep(1.45, 0.2, abs(ang - 0.35));
    float glow = exp(-dist / (size.y * 0.22)) * 0.35;
    return liveOut(fragCoord, glowTint(), (rays * 0.85 * cone + glow) * fall);
}
"""

    override fun uniforms(u: LiveUniforms) {
        // Only the shared uniforms.
    }
}
