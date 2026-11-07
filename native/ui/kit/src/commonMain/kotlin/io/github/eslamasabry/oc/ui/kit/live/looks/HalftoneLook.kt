package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Halftone: a print screen of fine dots at 45 degrees whose size follows a soft, slowly drifting cloud, so light and
 * shade wander across the page the way ink does on newsprint.
 */
public object HalftoneLook : LiveLook {
    override val id: String = "halftone"

    override val shader: String =
        LiveShaderLib.TINT +
            """
half4 main(float2 fragCoord) {
    float2 p = fragCoord / uDensity;
    float t = uTime;
    const float cell = 9.0;
    // Rotate the screen 45 degrees, as print does.
    float2 r = float2(p.x + p.y, p.y - p.x) * 0.70710678;
    float2 g = (floor(r / cell) + 0.5) * cell;
    // Sample the cloud at the dot's centre so every dot stays round.
    float2 gc = float2(g.x - g.y, g.x + g.y) * 0.70710678;
    float cloud = fbm2(gc * 0.0055 + float2(t * 0.012, t * 0.008));
    cloud = smoothstep(0.3, 0.8, cloud);
    float radius = cell * 0.5 * sqrt(cloud) * 0.85;
    float d = length(r - g);
    float disc = 1.0 - smoothstep(radius - px(), radius + px(), d);
    half3 tint = mix(uInk.rgb, glowTint(), half(0.45 + 0.4 * cloud));
    return liveOut(fragCoord, tint, disc * 0.6 * step(0.05, radius));
}
"""

    override fun uniforms(u: LiveUniforms) {
        // Only the shared uniforms.
    }
}
