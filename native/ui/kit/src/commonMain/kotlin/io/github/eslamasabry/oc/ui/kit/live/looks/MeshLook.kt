package io.github.eslamasabry.oc.ui.kit.live.looks

import io.github.eslamasabry.oc.ui.kit.live.LiveLook
import io.github.eslamasabry.oc.ui.kit.live.LiveUniforms
import kotlin.math.sin

/**
 * Breathing mesh: four soft pools of colour wander slowly over the page and breathe, like a mesh gradient lit from
 * behind. Each pool is its own light (a Gaussian in page space, bent by a gentle warp), so where two meet their colours
 * add as light instead of averaging into grey: a green accent and a lavender partner no longer meet in mud. Between the
 * pools the ground shows through, which gives the page depth. The points move on the CPU once a frame (four uniforms).
 *
 * Per pixel: two value noises (the warp), four exps, four glow blends; no loops.
 */
public object MeshLook : LiveLook {
    override val id: String = "mesh"

    override val shader: String =
        """
uniform float2 uP0;
uniform float2 uP1;
uniform float2 uP2;
uniform float2 uP3;
uniform float uBreath;

// A pool of light at c (page shares) with radius s (page heights), in aspect-true space.
float pool(float2 q, float2 c, float aspect, float s) {
    float2 d = q - float2(c.x * aspect, c.y);
    return exp(-dot(d, d) / (2.0 * s * s));
}

half4 main(float2 fragCoord) {
    float aspect = uRes.x / uRes.y;
    float2 q = fragCoord / uRes.y;
    float t = uTime;
    // A slow, large warp so the pools are never perfect circles.
    q += 0.06 * (float2(liveNoise(q * 2.2 + t * 0.017), liveNoise(q * 2.2 + 9.0 - t * 0.013)) - 0.5);
    float3 a = float3(uAccent.rgb);
    float3 b = float3(uPartner.rgb);
    float3 g = float3(uGlow.rgb);
    float k = (0.21 + 0.015 * uBreath) * uIntensity;
    float3 col = float3(uGround.rgb);
    col = liveGlow(col, a, pool(q, uP0, aspect, 0.20) * k);
    col = liveGlow(col, b, pool(q, uP1, aspect, 0.22) * k);
    col = liveGlow(col, g, pool(q, uP2, aspect, 0.17) * k * 0.8);
    col = liveGlow(col, b, pool(q, uP3, aspect, 0.19) * k * 0.9);
    return liveOut(col, fragCoord);
}
"""

    private const val SPEED = .12f
    private val names = arrayOf("uP0", "uP1", "uP2", "uP3")
    private val orbits =
        arrayOf(
            LiveOrbit(
                x = LiveWave(mid = .20f, swing = .15f, speed = .7f),
                y = LiveWave(mid = .10f, swing = .06f, speed = .5f, phase = QUARTER_TURN),
            ),
            LiveOrbit(
                x = LiveWave(mid = .85f, swing = .10f, speed = .6f, phase = QUARTER_TURN),
                y = LiveWave(mid = .32f, swing = .08f, speed = .4f),
            ),
            LiveOrbit(
                x = LiveWave(mid = .30f, swing = .20f, speed = .3f),
                y = LiveWave(mid = .62f, swing = .06f, speed = .8f, phase = QUARTER_TURN),
            ),
            LiveOrbit(
                x = LiveWave(mid = .70f, swing = .15f, speed = .5f, phase = 1f),
                y = LiveWave(mid = .95f, swing = .05f, speed = .9f, phase = QUARTER_TURN),
            ),
        )

    override fun uniforms(u: LiveUniforms) {
        val t = u.time * SPEED
        for (i in orbits.indices) u.float2(names[i], orbits[i].x(t), orbits[i].y(t))
        u.float("uBreath", sin(t))
    }
}
