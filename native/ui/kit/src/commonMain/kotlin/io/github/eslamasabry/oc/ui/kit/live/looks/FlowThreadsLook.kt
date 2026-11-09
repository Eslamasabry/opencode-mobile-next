package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveResolution
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Flow threads: a loose bundle of fine silk threads drifting across the page in long, slow waves, each with a soft
 * halo, shading from the text colour into the accent along its length.
 */
public object FlowThreadsLook : LiveLook {
    override val id: String = "flowThreads"
    override val resolution: Float = LiveResolution.FULL // fine marks: crisp only at the screen's own pixels

    override val shader: String =
        LiveShaderLib.TINT +
            """
half4 main(float2 fragCoord) {
    float2 p = fragCoord / uDensity;
    float2 size = uRes / uDensity;
    float t = uTime;
    float x = p.x;
    float core = 0.0;
    float halo = 0.0;
    // Two loose bundles of six threads; inside a bundle the threads share one wave and fan apart and together.
    for (int i = 0; i < 12; i++) {
        float k = float(i);
        float b = step(5.5, k);                       // which bundle
        float n = k - 6.0 * b;                        // the thread inside it
        float base = size.y * mix(0.3, 0.68, b);
        float f1 = mix(0.0042, 0.0036, b);
        float f2 = mix(0.0105, 0.0088, b);
        float ph = t * mix(0.07, 0.05, b) + b * 2.7;
        float fan = (n - 2.5) * (7.0 + 6.0 * sin(x * 0.0031 + t * 0.045 + b * 1.9));
        float y = base + fan + 70.0 * sin(x * f1 + ph) + 22.0 * sin(x * f2 - ph * 1.3 + n * 0.2);
        float dy = 70.0 * f1 * cos(x * f1 + ph) + 22.0 * f2 * cos(x * f2 - ph * 1.3 + n * 0.2);
        float d = abs(p.y - y) / sqrt(1.0 + dy * dy);   // distance to the thread, in dp
        core = max(core, 1.0 - smoothstep(0.3, 0.3 + px() * 1.3, d));
        halo += exp(-d * d * 0.006) * 0.07;
    }
    float along = 0.5 + 0.5 * sin(x * 0.004 + t * 0.03);
    half3 tint = mix(uInk.rgb, glowTint(), half(0.55 + 0.45 * along));
    return liveOut(fragCoord, tint, max(core * 0.75, min(halo, 0.45)));
}
"""

    override fun uniforms(u: LiveUniforms) {
        // Only the shared uniforms.
    }
}
