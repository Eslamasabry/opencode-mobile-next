package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveShaderLib
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms

/**
 * Matrix rain: thin columns of small pixel glyphs drift down slowly in the accent, each head a little brighter with a
 * soft fading trail; glyphs change now and then. About half the columns rain at any time, at 20–45 dp a second.
 */
public object MatrixRainLook : LiveLook {
    override val id: String = "matrixRain"
    override val shader: String = LiveShaderLib.LAYER + BODY

    override fun uniforms(u: LiveUniforms) {
        u.float("uCell", CELL_DP)
    }

    private const val CELL_DP = 13f
}

private const val BODY = """
uniform float uCell;

half4 main(float2 fc) {
    float d = max(uDensity, 1.0);
    float2 p = lvDp(fc);
    float cw = uCell;
    float ch = uCell * 1.3;
    float col = floor(p.x / cw);
    float row = floor(p.y / ch);
    float2 inCell = float2(fract(p.x / cw), fract(p.y / ch));

    float active = step(0.48, lvHash(float2(col, 3.7)));
    float speed = mix(1.2, 2.6, lvHash(float2(col, 11.3)));
    float rows = uRes.y / d / ch;
    float cycle = rows + mix(16.0, 40.0, lvHash(float2(col, 5.1)));
    float head = mod(uTime * speed + lvHash(float2(col, 8.1)) * cycle, cycle) - 4.0;
    float behind = head - row;
    float trail = mix(12.0, 26.0, lvHash(float2(col, 8.8)));
    float lit = active * step(0.0, behind) * exp(-behind / trail * 2.6);
    float isHead = active * step(0.0, behind) * (1.0 - step(1.0, behind));

    // A 3x5 pixel glyph inside a padded box; blocks keep a hairline gap so they read as glyphs.
    float2 g = (inCell - float2(0.2, 0.12)) / float2(0.6, 0.76);
    float inside = step(0.0, g.x) * step(g.x, 1.0) * step(0.0, g.y) * step(g.y, 1.0);
    float2 gb = g * float2(3.0, 5.0);
    float2 b = floor(gb);
    float epoch = floor(uTime * 0.45 + lvHash(float2(col, row)) * 7.0);
    float bit = step(0.42, lvHash(b + float2(col * 7.0 + epoch * 13.0, row * 5.0)));
    float2 blockPx = float2(0.6 * cw / 3.0, 0.76 * ch / 5.0) * d;
    float2 bf = fract(gb);
    float2 edge = min(bf, 1.0 - bf) * blockPx;
    float cover = smoothstep(0.25, 1.0, min(edge.x, edge.y));
    float glyph = inside * bit * cover;

    float gA = lvAmt(0.26) * lit * glyph;
    float kA = lvAmt(0.1) * isHead * glyph;
    return lvOut(lvLayer(kA, gA), fc);
}
"""
