@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoothness round 2 (docs/native/qa/smoothness-2026-11-09): a frosted liquid pane draws its blurred, bent backdrop at
 * the platform's reduced resolution (Android: half per side; the vendored third_party/backdrop scales it back up), so
 * Glass fits a 120 Hz frame. The lightly frosted chips and the + keep full resolution, where text behind them would
 * read softer. The rim light, specular and tint are never in the reduced layer (the library draws them on top).
 */
class KitLiquidResolutionTest {
    private val glass = KitAppearance(style = Style.Glass)

    /** The roles a page with a composer, a + and a jump pill asks the platform to draw, with each pane's resolution. */
    private fun resolutions(platform: FakeKitPlatform): Map<LiquidRole, Set<Float>> {
        runComposeUiTest {
            setContent {
                KitTheme(appearance = glass, platform = platform, localeTag = "en", stillFrame = true) {
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
                                KitJumpPill("Latest", {}, visible = true, modifier = Modifier.align(Alignment.Center), backdrop = source)
                            }
                        }
                    }
                }
            }
            waitForIdle()
        }
        return platform.liquidSpecs
            .groupBy { spec -> LiquidRole.entries.first { LiquidEdge.recipe(it, false) == spec.recipe || LiquidEdge.recipe(it, true) == spec.recipe } }
            .mapValues { (_, specs) -> specs.map { it.resolution }.toSet() }
    }

    @Test
    fun `B-KIT-018 a frosted liquid pane draws its backdrop at the platform's reduced resolution, chips and the + at full`() {
        val seen = resolutions(FakeKitPlatform(liquidGlass = true, liquidResolution = .5f))
        assertTrue(LiquidRole.Composer in seen && LiquidRole.Action in seen && LiquidRole.Pill in seen, "panes drawn: ${seen.keys}")
        // The blur cut (owner 2026-11-11): the message box's 2 dp frost no longer hides a half-resolution backdrop.
        assertEquals(setOf(1f), seen.getValue(LiquidRole.Composer), "the message box")
        assertEquals(setOf(1f), seen.getValue(LiquidRole.Action), "the +")
        assertEquals(setOf(1f), seen.getValue(LiquidRole.Pill), "the jump pill")
    }

    @Test
    fun `B-KIT-018 a platform at full resolution keeps every liquid pane at full resolution`() {
        val seen = resolutions(FakeKitPlatform(liquidGlass = true))
        assertTrue(seen.isNotEmpty(), "panes drawn")
        seen.forEach { (role, res) -> assertEquals(setOf(1f), res, "$role") }
    }

    @Test
    fun `B-KIT-018 sheets and dark menus are the panes frosted enough for the reduced resolution`() {
        // After the blur cut (owner 2026-11-11: bars 3 dp, the message box 2, menus 6 dark and 3 light) only panes
        // frosted at least LIQUID_REDUCED_MIN_BLUR_DP keep the reduced resolution.
        for (dark in listOf(false, true)) {
            for (role in LiquidRole.entries) {
                val expected =
                    when (role) {
                        LiquidRole.Sheet -> .5f
                        LiquidRole.Menu -> if (dark) .5f else 1f
                        LiquidRole.Bar, LiquidRole.Dock, LiquidRole.Composer, LiquidRole.Pill, LiquidRole.Status, LiquidRole.Action -> 1f
                    }
                assertEquals(expected, liquidResolutionOf(LiquidEdge.recipe(role, dark), .5f), "$role dark=$dark")
                assertEquals(1f, liquidResolutionOf(LiquidEdge.recipe(role, dark), 1f), "$role dark=$dark, full platform")
            }
        }
    }
}
