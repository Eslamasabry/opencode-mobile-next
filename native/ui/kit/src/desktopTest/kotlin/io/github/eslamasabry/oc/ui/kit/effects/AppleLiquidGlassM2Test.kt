@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.KitGlassTier
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitVisibility
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitVisibility
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Apple Liquid Glass Milestone 2 verification:
 * - B-610: tilt- & scroll-reactive specular rim lighting, deadband filtering (~0% visible idle CPU),
 *   visibility gating (0% hidden CPU), API 35+ capability gating, and Reduced Motion discipline.
 * - B-616: gelatinous spring flex and luminous white inner illumination rules.
 */
class AppleLiquidGlassM2Test {

    private class LightObservingPlatform(
        private val base: FakeKitPlatform,
        private val tier: KitGlassTier = KitGlassTier.AppleLiquid,
    ) : KitPlatform by base {
        var activeCount = 0
        var lastActive: Boolean? = null
        var callback: ((Float) -> Unit)? = null

        override val glassTier: KitGlassTier get() = tier

        @Composable
        override fun ObserveGlassLight(active: Boolean, onAngleDeg: (Float) -> Unit) {
            lastActive = active
            if (active) activeCount++
            callback = onAngleDeg
        }
    }

    @Test
    fun `B-610 gravity tilt shifts specular angle smoothly and deadband suppresses steady jitter`() {
        // Resting flat on a table stays at canonical 45 degrees.
        assertEquals(GlassTuning.LIGHT_DEG, GlassLightMath.tiltAngleDeg(0f, 0f, 9.81f), 1e-3f)
        // Tilting left vs right sweeps light in opposite directions around 45 degrees.
        val tiltRight = GlassLightMath.tiltAngleDeg(4.5f, 7.5f, 4.2f)
        val tiltLeft = GlassLightMath.tiltAngleDeg(-4.5f, 7.5f, 4.2f)
        assertTrue(tiltRight > GlassTuning.LIGHT_DEG + 5f, "right tilt shifts light angle ($tiltRight)")
        assertTrue(tiltLeft < GlassTuning.LIGHT_DEG - 5f, "left tilt shifts light angle ($tiltLeft)")

        // Deadband suppresses sub-threshold jitter so stationary devices trigger zero redraws (~0% visible idle CPU).
        val light = KitGlassLight()
        light.onTiltDeg(GlassTuning.LIGHT_DEG + 0.2f)
        assertEquals(GlassTuning.LIGHT_DEG, light.tiltDeg, "jitter inside deadband must not mutate state")
        light.onTiltDeg(GlassTuning.LIGHT_DEG + 8f)
        assertEquals(GlassTuning.LIGHT_DEG + 8f, light.tiltDeg, "real tilt beyond deadband updates light")

        // Scroll delta shifts specular angle and reset restores 45 degrees.
        val beforeScroll = light.effectiveDeg()
        light.onScrollDelta(120f, 3f)
        assertTrue(abs(light.effectiveDeg() - beforeScroll) >= 2f, "scroll impulse sweeps specular angle")
        light.reset()
        assertEquals(GlassTuning.LIGHT_DEG, light.effectiveDeg(), 1e-3f)
    }

    @Test
    fun `B-610 tilt sensor is strictly gated on Glass, AppleLiquid tier, visibility, and motion enabled`() {
        fun checkActive(
            appearance: KitAppearance = KitAppearance(style = Style.Glass),
            visible: Boolean = true,
            tier: KitGlassTier = KitGlassTier.AppleLiquid,
            stillFrame: Boolean = false,
        ): Boolean {
            val platform = LightObservingPlatform(FakeKitPlatform(liquidGlass = tier == KitGlassTier.AppleLiquid || tier == KitGlassTier.StandardLiquid), tier)
            val vis = KitVisibility(visible = visible)
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalKitVisibility provides vis) {
                        KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = stillFrame) {}
                    }
                }
                waitForIdle()
            }
            return platform.lastActive == true
        }

        assertTrue(checkActive(), "active on Glass + AppleLiquid + visible + full motion")
        assertFalse(checkActive(visible = false), "0% hidden CPU: sensor must be inactive when hidden")
        assertFalse(checkActive(appearance = KitAppearance(style = Style.Glass, motion = MotionLevel.Off)), "Reduced Motion disables tilt sensor")
        assertFalse(checkActive(appearance = KitAppearance(style = Style.Solid)), "non-Glass style never activates tilt sensor")
        assertFalse(checkActive(tier = KitGlassTier.StandardLiquid), "non-API-35 tier does not register tilt sensor")
        assertFalse(checkActive(stillFrame = true), "stillFrame test mode keeps deterministic 45-degree speculars")
    }

    @Test
    fun `B-616 gelatinous touch rules define directional flex, elastic spring and luminous white press bloom`() {
        assertTrue(LiquidTouchRules.SWELL > 0.05f, "gelatinous swell exceeds flat 5% scale")
        assertTrue(LiquidTouchRules.FLEX_AXIS_BIAS > 0f, "press flex has directional axis bias")
        assertTrue(LiquidTouchRules.GEL_DAMPING in 0.45f..0.75f, "spring damping gives tactile gelatinous rebound")
        assertTrue(LiquidTouchRules.BLOOM_DARK >= 0.20f && LiquidTouchRules.BLOOM_LIGHT >= 0.35f, "inner press illumination is visibly luminous")
        assertTrue(LiquidTouchRules.EDGE_LIGHT >= 0.35f, "light-mode pressed specular edge stays bright white")
    }
}
