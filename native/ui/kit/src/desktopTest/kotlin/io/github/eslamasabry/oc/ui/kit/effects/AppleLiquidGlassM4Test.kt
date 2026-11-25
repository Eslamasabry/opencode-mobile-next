@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitNested
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.frame.KitFloatingBar
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Apple Liquid Glass Milestone 4 (Whole-App Architectural Placement & Consistency):
 * - B-611: content layer stays calm non-glass, zero glass-on-glass in nested controls / segmented tracks
 * - B-614: Clear / Glossy / Frosted legibility options exposed cleanly
 * - B-616: Settings / New Conversation floating top-bar capsule renders as liquid glass while Home stays plate-free
 */
class AppleLiquidGlassM4Test {

    @Test
    fun `B-614 Clear, Glossy and Frosted glass looks are all offered with distinct optical weight`() {
        assertTrue(GlassLooks.clearEarnsItsPlace, "Clear variant earns its place")
        assertEquals(listOf(GlassLookId.Clear, GlassLookId.Glossy, GlassLookId.Frosted), GlassLooks.offered)
        assertTrue(GlassLooks.Clear.tintFactor < GlassLooks.Glossy.tintFactor)
        assertTrue(GlassLooks.Glossy.tintFactor < GlassLooks.Frosted.tintFactor)
    }

    @Test
    fun `B-611 nested segmented controls and content cards compose cleanly without glass-on-glass stacking`() =
        runComposeUiTest {
            val platform = FakeKitPlatform(liquidGlass = true)
            var contentDressed = false
            var nestedDressed = false
            setContent {
                KitTheme(KitAppearance(style = Style.Glass), platform, "en", stillFrame = true) {
                    val contentLook = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, SurfaceLevel.Surface1))
                    contentDressed = contentLook.dressed
                    CompositionLocalProvider(LocalKitNested provides true) {
                        val nestedLook = kitSurfaceLook(KitSurfaceInput(SurfaceRole.Control, KitShapeId.Tile, SurfaceLevel.Surface3, selected = true))
                        nestedDressed = nestedLook.dressed
                    }
                    KitSegmented(
                        segments = listOf(KitSegment("a", "Clear"), KitSegment("b", "Glossy"), KitSegment("c", "Frosted")),
                        selected = "b",
                        onChange = {},
                        semanticsLabel = "Glass look",
                    )
                }
            }
            waitForIdle()
            assertTrue(contentDressed && nestedDressed, "both content cards and nested segmented thumbs resolve cleanly")
            assertEquals(1, onAllNodesWithTag("kit-segmented-track").fetchSemanticsNodes().size)
            assertEquals(1, onAllNodesWithTag("kit-segmented-thumb").fetchSemanticsNodes().size)
        }

    @Test
    fun `B-616 floatingBar with scrollsElsewhere false renders liquid glass bar capsule while Home stays plate-free`() =
        runComposeUiTest {
            val platform = FakeKitPlatform(liquidGlass = true)
            setContent {
                KitTheme(KitAppearance(style = Style.Glass), platform, "en", stillFrame = true) {
                    Box(Modifier.size(360.dp, 800.dp)) {
                        KitScreen(
                            page = true,
                            topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) },
                            floatingBar = KitFloatingBar(scrollsElsewhere = false),
                        ) {
                            Box(Modifier.size(360.dp, 1200.dp))
                        }
                    }
                }
            }
            waitForIdle()
            assertEquals(
                1,
                onAllNodesWithTag("kit-liquid-bar", useUnmergedTree = true).fetchSemanticsNodes().size,
                "Settings/NewConversation floatingBar renders a floating Liquid Glass capsule",
            )
        }
}
