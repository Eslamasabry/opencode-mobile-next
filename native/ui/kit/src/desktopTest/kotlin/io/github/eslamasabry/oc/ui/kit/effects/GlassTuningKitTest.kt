@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.GlassKnob
import io.github.eslamasabry.oc.ui.design.effects.GlassPaneKind
import io.github.eslamasabry.oc.ui.design.effects.GlassPreset
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The Glass lab in the kit (2026-11-13): the tuning reaches every liquid pane's spec, and an untouched or switched-off
 * tuning draws exactly today's glass.
 */
class GlassTuningKitTest {
    /** The specs a page with a message box and a + asks the platform to draw under [appearance]. */
    private fun specs(appearance: KitAppearance, over: GlassTuning? = null, unset: Boolean = true): List<KitLiquidPaneSpec> {
        val platform = FakeKitPlatform(liquidGlass = true)
        runComposeUiTest {
            setContent {
                KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                    if (unset) Page() else KitGlassTuned(over) { Page() }
                }
            }
            waitForIdle()
        }
        // The flip is a per-pane object; the look is everything else.
        return platform.liquidSpecs.map { it.copy(flip = null) }.distinct()
    }

    @Composable
    private fun Page() {
        Box(Modifier.size(412.dp, 720.dp)) {
            val source = rememberKitFrostState()
            KitScreen(
                page = true,
                overlayChrome = true,
                frost = source,
                bottom = {
                    val face = kitLiquidFace(LiquidRole.Composer, LocalKitLiquidChrome.current, RoundedCornerShape(24.dp))
                    Box(Modifier.fillMaxWidth().height(56.dp).then(face ?: Modifier))
                },
                edges = KitScreenEdges.Bleed,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().kitFrostSource(source)) {}
                    KitFab("New", KitIcons.add, {}, Modifier.align(Alignment.BottomEnd), backdrop = source)
                }
            }
        }
    }

    private val glass = KitAppearance(style = Style.Glass)

    @Test
    fun `B-GLASS-LAB-010 the shipped tuning worn in the whole app draws every pane exactly as today`() {
        val today = specs(glass)
        assertTrue(today.isNotEmpty(), "panes drawn")
        assertEquals(today, specs(glass.copy(glassTuning = GlassTuning.Shipped.copy(inApp = true))))
        assertEquals(today, specs(glass, over = GlassTuning.Shipped, unset = false))
    }

    @Test
    fun `B-GLASS-LAB-011 with Use in the whole app off the app draws the shipped glass`() {
        val moved = GlassTuning.Shipped.with(GlassPaneKind.MessageBox, true, GlassKnob.Blur, 9f)
        val off = specs(glass.copy(glassTuning = moved.copy(inApp = false)))
        assertEquals(specs(glass), off)
        val on = specs(glass.copy(glassTuning = moved.copy(inApp = true)))
        assertTrue(on.any { it.recipe.blurDp == 9f }, "the message box wears the tuned blur: ${on.map { it.recipe.blurDp }}")
        // The + is another kind: untouched.
        assertTrue(on.any { it.recipe == LiquidEdge.recipe(LiquidRole.Action, true) })
    }

    @Test
    fun `B-GLASS-LAB-012 a knob at its shipped number leaves the spec as it is, a moved one replaces only its own`() {
        val base =
            KitLiquidPaneSpec(
                recipe = LiquidEdge.recipe(LiquidRole.Bar, true),
                fill = Color(0x2E202020),
                fillStart = Color(0x2E202020),
                rtl = false,
                rim = Color.White.copy(alpha = .72f),
                rimAdds = true,
                rimDp = 1f,
                shadow = Color.Transparent,
                frostBlurDp = 8f,
            )
        val shipped = GlassTuning.shipped(GlassPaneKind.Bar, true)
        assertSame(base, base.tuned(shipped, shipped, 1f))
        val moved = base.tuned(GlassKnob.LensStrength.on(shipped, 70f), shipped, 1f)
        assertEquals(70f, moved.recipe.refractionDp)
        assertEquals(base.copy(recipe = base.recipe.copy(refractionDp = 70f)), moved)
        val clear = base.tuned(GlassTuning.presetKnobs(GlassPreset.ClearLiquid, GlassPaneKind.Bar, true), shipped, .5f)
        assertEquals(.5f, clear.recipe.blurDp)
        assertEquals(1f, clear.resolution, "a clear pane draws its backdrop at full resolution")
        assertTrue(clear.specular.alpha > 0f && clear.bendShape != 0f && clear.recipe.hold.knee)
        assertNull(GlassTuning.decode("not a block"))
    }
}
