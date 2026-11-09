@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.foundation.ChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.ChromeVeilPolicy
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitChromeVeil
import io.github.eslamasabry.oc.ui.kit.foundation.VeilLook
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E20-S30 (owner 2026-11-07): the chat's bottom chrome is no opaque ground band. The veil policy stays (the jump pill
 * still lies on one); since 2026-11-09 the chrome itself lays none.
 */
class E20S30ChromeVeilTest {
    /** KitScreen's bottom block pads its content S2 above and below; its test tag sits inside that padding. */
    private val BLOCK_PADDING = 16.dp

    // ---- the policy (pure)

    @Test
    fun `E20-S30 the veil blurs at 76 percent with a radius near 22 dp when the platform can blur`() {
        val v = ChromeVeilPolicy.veil(highContrast = false, saver = false, FrostStrength.Balanced, blurAvailable = true)
        assertEquals(VeilLook.Blur, v.look)
        assertTrue(v.tintAlpha in .70f..0.80f, "tint ${v.tintAlpha}")
        // A blur radius is about sigma / 0.577: the story asks for 20–24 dp.
        val radius = v.sigmaDp / 0.57735f
        assertTrue(radius in 20f..24f, "radius $radius")
        assertEquals(v.tintAlpha, v.faceAlpha)
    }

    @Test
    fun `E20-S30 below API 31, on Saver or with frost off the veil is a plain 92 percent tint`() {
        val noBlur = ChromeVeilPolicy.veil(false, false, FrostStrength.Strong, blurAvailable = false)
        val saver = ChromeVeilPolicy.veil(false, true, FrostStrength.Strong, blurAvailable = true)
        val off = ChromeVeilPolicy.veil(false, false, FrostStrength.Off, blurAvailable = true)
        listOf(noBlur, saver, off).forEach {
            assertEquals(VeilLook.Tint, it.look)
            assertEquals(.92f, it.tintAlpha)
            assertEquals(0f, it.sigmaDp)
        }
    }

    @Test
    fun `E20-S30 high contrast holds the veil opaque`() {
        val v = ChromeVeilPolicy.veil(highContrast = true, saver = false, FrostStrength.Balanced, blurAvailable = true)
        assertEquals(VeilLook.Opaque, v.look)
        assertEquals(1f, v.faceAlpha)
    }

    // ---- KitScreen

    private class Seen {
        var veil: ChromeVeil? = null
        var padding: PaddingValues? = null
    }

    private fun scene(
        seen: Seen,
        frost: Boolean,
        style: Style = Style.Solid,
        platform: FakeKitPlatform = FakeKitPlatform(frost = true),
    ): @androidx.compose.runtime.Composable () -> Unit = {
        KitTheme(appearance = KitAppearance(style = style), platform = platform, localeTag = "en", stillFrame = true) {
            Box(Modifier.size(412.dp, 800.dp)) {
                val source = rememberKitFrostState()
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = if (frost) source else null,
                    bottom = {
                        seen.veil = LocalKitChromeVeil.current
                        Box(Modifier.fillMaxWidth().height(64.dp).testTag("composer"))
                    },
                    edges = KitScreenEdges.Bleed,
                ) {
                    seen.padding = kitScreenChromePadding()
                    Box(Modifier.fillMaxSize().kitFrostSource(source))
                }
            }
        }
    }

    // Owner 2026-11-09 ("an opaque slab under the composer", "a rough line just below Jump to latest"): the veil's
    // ground ended in a hard line over a live background. A chat's bottom block now lays nothing behind its parts; the
    // transcript fades out over its top 16 dp instead (ChromeEdgeBlendTest guards the look in every style).
    @Test
    fun `E20-S30 a chat screen's bottom block lays no veil and the body scrolls under it with no fade room`() {
        for (platform in listOf(FakeKitPlatform(frost = true), FakeKitPlatform(frost = false), FakeKitPlatform(KitSystemSignals(highContrast = true), frost = true)))
            runComposeUiTest {
                val seen = Seen()
                setContent(scene(seen, frost = true, platform = platform))
                waitForIdle()
                val block = onNodeWithTag("kit-screen-bottom").getBoundsInRoot()
                assertNull(seen.veil, "no veil band behind the bottom block")
                // The body's room is the block itself (the tag sits inside its 8 dp padding, top and bottom).
                assertEquals(block.bottom - block.top + BLOCK_PADDING, assertNotNull(seen.padding).calculateBottomPadding())
            }
    }

    @Test
    fun `E20-S30 a screen that records nothing keeps its ground and fade, and Glass keeps its frost`() = runComposeUiTest {
        var frost by androidx.compose.runtime.mutableStateOf(false)
        var style by androidx.compose.runtime.mutableStateOf(Style.Solid)
        val seen = Seen()
        setContent {
            val s = scene(seen, frost = frost, style = style)
            s()
        }
        waitForIdle()
        assertNull(seen.veil)
        val block = onNodeWithTag("kit-screen-bottom").getBoundsInRoot()
        assertEquals(block.bottom - block.top + BLOCK_PADDING + 16.dp, assertNotNull(seen.padding).calculateBottomPadding())
        frost = true
        style = Style.Glass
        waitForIdle()
        assertNull(seen.veil, "Glass frosts the block its own way")
    }
}
