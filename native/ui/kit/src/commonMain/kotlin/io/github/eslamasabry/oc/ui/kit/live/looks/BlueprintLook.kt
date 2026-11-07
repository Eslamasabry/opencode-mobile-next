package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Blueprint: the back of an engineering sheet. A fine grid with heavier lines every fifth step, measurement ticks along
 * the heavy lines and a few registration rings, all drifting very slowly on a faint accent wash; a broad soft light
 * wanders across the sheet so it never looks flat.
 */
public object BlueprintLook : LiveLook {
    override val id: String = "blueprint"
    override val shader: String = LiveShaderLib.LAYER + BODY

    override fun uniforms(u: LiveUniforms) {
        u.float("uStep", STEP_DP)
    }

    private const val STEP_DP = 12f
}

private const val BODY = """
uniform float uStep;

half4 main(float2 fc) {
    float d = max(uDensity, 1.0);
    float2 base = lvDp(fc);
    float2 p = base + float2(uTime * 1.5, uTime * 0.8);
    float s = uStep;
    float big = s * 5.0;

    float2 m = (fract(p / s + 0.5) - 0.5) * s * d;
    float minor = max(lvLine(m.x, 0.5), lvLine(m.y, 0.5));
    float2 mm = (fract(p / big + 0.5) - 0.5) * big * d;
    float major = max(lvLine(mm.x, 0.75), lvLine(mm.y, 0.75));

    float hs = s * 0.5;
    float2 t = (fract(p / hs + 0.5) - 0.5) * hs * d;
    float tickX = lvLine(t.x, 0.5) * (1.0 - smoothstep(2.0 * d, 2.6 * d, abs(mm.y)));
    float tickY = lvLine(t.y, 0.5) * (1.0 - smoothstep(2.0 * d, 2.6 * d, abs(mm.x)));
    float ticks = max(tickX, tickY);

    float2 cell = floor(p / big + 0.5);
    float ringOn = step(0.78, lvHash(cell + 3.1));
    float ring = ringOn * lvLine(length(mm) - 5.0 * d, 0.6);

    float light = 0.45 + 0.55 * lvNoise(base / 280.0 + float2(uTime * 0.025, -uTime * 0.018));
    float lines = max(minor * 0.4, max(major, max(ticks * 0.75, ring)));

    float g = lvAmt(0.035) + lvAmt(0.12) * lines * light;
    float k = lvAmt(0.03) * max(major, ring) * light;
    return lvOut(lvLayer(k, g), fc);
}
"""
