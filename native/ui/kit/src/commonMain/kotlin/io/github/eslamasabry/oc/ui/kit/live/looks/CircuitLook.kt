package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveResolution
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Circuit: faint board traces on a grid that always join up (each cell edge is shared by its two cells), with pads at
 * trace ends and dots at junctions; small pulses of accent light travel along some lanes at about 25 dp a second.
 */
public object CircuitLook : LiveLook {
    override val id: String = "circuit"
    override val resolution: Float = LiveResolution.FULL // fine marks: crisp only at the screen's own pixels
    override val shader: String = LiveShaderLib.LAYER + BODY

    override fun uniforms(u: LiveUniforms) {
        u.float("uCell", CELL_DP)
    }

    private const val CELL_DP = 22f
}

private const val BODY = """
uniform float uCell;

// A horizontal edge is on mostly along busy rows (buses) and rarely elsewhere (branches); vertical edges likewise by
// column. key is the edge's shared id, lane its row or column.
float cxEdge(float2 key, float lane) {
    float bus = step(0.62, lvHash(float2(lane, 23.0)));
    return step(mix(0.86, 0.18, bus), lvHash(key + float2(41.0, 17.0)));
}

float cxArm(float2 f, float2 dir, float len) {
    float t = clamp(dot(f, dir), 0.0, len);
    return length(f - dir * t);
}

half4 main(float2 fc) {
    float d = max(uDensity, 1.0);
    float2 p = lvDp(fc);
    float s = uCell;
    float2 c = floor(p / s);
    float2 f = (fract(p / s) - 0.5) * s * d;
    float hl = 0.5 * s * d;

    float r = cxEdge(c * 2.0 + float2(1.0, 0.0), c.y);
    float l = cxEdge(c * 2.0 - float2(1.0, 0.0), c.y);
    float u = cxEdge(c * 2.0 + float2(0.0, 1.0), c.x + 300.0);
    float dn = cxEdge(c * 2.0 - float2(0.0, 1.0), c.x + 300.0);
    float n = r + l + u + dn;

    float far = 1.0e5;
    float dist = far;
    dist = min(dist, mix(far, cxArm(f, float2(1.0, 0.0), hl), r));
    dist = min(dist, mix(far, cxArm(f, float2(-1.0, 0.0), hl), l));
    dist = min(dist, mix(far, cxArm(f, float2(0.0, 1.0), hl), u));
    dist = min(dist, mix(far, cxArm(f, float2(0.0, -1.0), hl), dn));
    float trace = lvLine(dist, 0.55 * d);

    float rc = length(f);
    float endPad = step(0.5, n) * (1.0 - step(1.5, n));
    float pad = endPad * max(lvLine(rc - 2.6 * d, 0.6 * d), 0.35 * (1.0 - smoothstep(1.6 * d, 2.2 * d, rc)));
    float node = step(2.5, n) * (1.0 - smoothstep(1.5 * d - 0.5, 1.5 * d + 0.5, rc));
    float board = max(trace, max(pad, node));

    float onH = step(abs(f.y), abs(f.x));
    float lane = mix(c.x + 300.0, c.y, onH);
    float along = mix(p.y, p.x, onH);
    float dir = mix(-1.0, 1.0, step(0.5, lvHash(float2(lane, 6.0))));
    float busy = step(0.55, lvHash(float2(lane, 2.0)));
    // ph is 0 at the pulse head (moving towards +dir) and grows along the tail behind it.
    float ph = fract(uTime * 0.055 - along / 460.0 * dir + lvHash(float2(lane, 9.0)));
    float pulse = busy * exp(-ph * 16.0) * smoothstep(0.0, 0.01, ph);

    float k = lvAmt(0.06) * board;
    float g = lvAmt(0.03) * board + lvAmt(0.2) * pulse * max(trace, node);
    return lvOut(lvLayer(k, g), fc);
}
"""
