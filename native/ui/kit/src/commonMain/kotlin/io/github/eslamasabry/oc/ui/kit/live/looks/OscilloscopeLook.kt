package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveResolution
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Oscilloscope: a faint scope graticule (divisions, centre axes with fine ticks) with one calm accent trace across the
 * middle, a soft phosphor glow around it and a fainter afterimage a moment behind. The trace is three slow sine waves,
 * so it breathes rather than flickers.
 */
public object OscilloscopeLook : LiveLook {
    override val id: String = "oscilloscope"
    override val resolution: Float = LiveResolution.FULL // fine marks: crisp only at the screen's own pixels
    override val shader: String = LiveShaderLib.LAYER + BODY

    override fun uniforms(u: LiveUniforms) {
        u.float("uDiv", DIVISION_DP)
    }

    private const val DIVISION_DP = 48f
}

private const val BODY = """
uniform float uDiv;

float osWave(float x, float t) {
    return 0.55 * sin(x * 0.021 + t * 0.5) + 0.3 * sin(x * 0.047 - t * 0.7 + 1.3) + 0.15 * sin(x * 0.093 + t * 0.9);
}

float osSlope(float x, float t) {
    return 0.55 * 0.021 * cos(x * 0.021 + t * 0.5) + 0.3 * 0.047 * cos(x * 0.047 - t * 0.7 + 1.3)
        + 0.15 * 0.093 * cos(x * 0.093 + t * 0.9);
}

half4 main(float2 fc) {
    float d = max(uDensity, 1.0);
    float2 sz = uRes / d;
    float2 q = lvDp(fc) - sz * 0.5;

    float2 m = (fract(q / uDiv + 0.5) - 0.5) * uDiv * d;
    float grid = max(lvLine(m.x, 0.5), lvLine(m.y, 0.5));
    float fine = uDiv * 0.2;
    float2 t = (fract(q / fine + 0.5) - 0.5) * fine * d;
    float ticks = max(lvLine(t.x, 0.5) * (1.0 - step(3.0, abs(q.y))), lvLine(t.y, 0.5) * (1.0 - step(3.0, abs(q.x))));
    float axes = max(lvLine(q.x * d, 0.6), lvLine(q.y * d, 0.6));
    float graticule = max(grid * 0.45, max(axes * 0.8, ticks * 0.8));

    float amp = min(sz.y * 0.08, 64.0);
    float y = amp * osWave(q.x, uTime);
    float dy = amp * osSlope(q.x, uTime);
    float dist = abs(q.y - y) / sqrt(1.0 + dy * dy) * d;
    float core = lvLine(dist, 0.75 * d);
    float sig = 7.0 * d;
    float glow = exp(-dist * dist / (2.0 * sig * sig));

    float y2 = amp * osWave(q.x, uTime - 0.8);
    float dist2 = abs(q.y - y2) * d;
    float ghost = exp(-dist2 * dist2 / (2.0 * sig * sig * 0.5));

    float k = lvAmt(0.045) * graticule;
    float g = lvAmt(0.02) + lvAmt(0.2) * core + lvAmt(0.07) * glow + lvAmt(0.03) * ghost;
    return lvOut(lvLayer(k, g), fc);
}
"""
