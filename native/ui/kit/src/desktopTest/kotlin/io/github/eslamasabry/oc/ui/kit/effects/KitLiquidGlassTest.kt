@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostStrength
import io.github.eslamasabry.oc.ui.design.effects.GlassEdges
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitFab
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Glass's liquid edges in the kit (docs/designs/liquid-glass.md, owner 2026-11-08): when the floating parts are liquid
 * glass and when they keep today's face (B-KIT-018), that every other style draws pixel for pixel as before
 * (B-KIT-037), and that a chat's composer never flips between KitScreen's two layouts.
 */
class KitLiquidGlassTest {
    private fun liquidAt(appearance: KitAppearance, platform: FakeKitPlatform): Boolean {
        var on = false
        runComposeUiTest {
            setContent { KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) { on = kitLiquidGlassOn() } }
            waitForIdle()
        }
        return on
    }

    private val glass = KitAppearance(style = Style.Glass)
    private val lens = FakeKitPlatform(liquidGlass = true)

    @Test
    fun `B-KIT-018 Glass on a platform with the lens is liquid`() {
        assertTrue(liquidAt(glass, lens))
    }

    @Test
    fun `B-KIT-018 below Android 13 Glass keeps its frost`() {
        assertFalse(liquidAt(glass, FakeKitPlatform(liquidGlass = false)))
    }

    @Test
    fun `B-KIT-018 Saver, reduced motion and high contrast keep today's face`() {
        assertFalse(liquidAt(glass.copy(mode = AppearanceMode.Saver), lens), "Saver")
        assertFalse(liquidAt(glass.copy(motion = MotionLevel.Off), lens), "reduced motion")
        assertFalse(liquidAt(glass, FakeKitPlatform(KitSystemSignals(highContrast = true), liquidGlass = true)), "high contrast")
    }

    @Test
    fun `B-KIT-018 frost Off and Glass edges Frosted keep today's frost`() {
        assertFalse(liquidAt(glass.copy(frost = FrostStrength.Off), lens), "frost off")
        assertFalse(liquidAt(glass.copy(glassEdges = GlassEdges.Frosted), lens), "the kill switch")
    }

    @Test
    fun `B-KIT-037 no other style is ever liquid`() {
        for (style in Style.entries.filter { it != Style.Glass }) assertFalse(liquidAt(KitAppearance(style = style), lens), "$style")
    }

    /** A chat-like page: stripes scrolling behind an overlaid bar and a composer, a +, a jump pill. */
    @Composable
    private fun Scene(loading: Boolean, seen: (Boolean) -> Unit = {}) {
        Box(Modifier.size(412.dp, 720.dp)) {
            val source = rememberKitFrostState()
            KitScreen(
                page = true,
                overlayChrome = true,
                loading = loading,
                frost = source,
                topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) },
                bottom = {
                    val face = kitLiquidFace(LiquidRole.Composer, LocalKitLiquidChrome.current, RoundedCornerShape(24.dp))
                    seen(face != null)
                    Box(Modifier.fillMaxWidth().height(56.dp).then(face ?: Modifier.background(Color.Gray)).testTag("composer"))
                },
            ) {
                Box(Modifier.fillMaxSize()) {
                    Stripes(source)
                    KitFab("New", KitIcons.add, {}, Modifier.align(Alignment.BottomEnd), backdrop = source)
                    KitJumpPill("Latest", {}, visible = true, modifier = Modifier.align(Alignment.Center), backdrop = source)
                }
            }
        }
    }

    @Composable
    private fun Stripes(source: KitFrostState) {
        Column(Modifier.fillMaxSize().kitFrostSource(source)) {
            repeat(24) { i -> Box(Modifier.fillMaxWidth().height(30.dp).background(if (i % 2 == 0) Color(0xFF3355FF) else Color(0xFFFFCC22))) }
        }
    }

    @Test
    fun `B-KIT-018 the composer stays liquid when the loading line moves the chat into the stacked layout`() = runComposeUiTest {
        var loading by mutableStateOf(false)
        var liquid = false
        setContent {
            KitTheme(appearance = glass, platform = lens, localeTag = "en", stillFrame = true) { Scene(loading) { liquid = it } }
        }
        waitForIdle()
        assertTrue(liquid, "overlaid layout")
        loading = true
        waitForIdle()
        assertTrue(liquid, "stacked layout: no flip to frost")
    }

    @Test
    fun `B-KIT-037 every other style, and Glass wherever liquid is held, draws pixel for pixel as before`() = runComposeUiTest {
        var appearance by mutableStateOf(KitAppearance())
        var platform by mutableStateOf(FakeKitPlatform(liquidGlass = false))
        setContent {
            KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) { Scene(loading = false) }
        }
        val cases =
            Style.entries.filter { it != Style.Glass }.flatMap { s ->
                listOf(KitAppearance(style = s, mode = AppearanceMode.Dark), KitAppearance(style = s, mode = AppearanceMode.Light))
            } + listOf(
                glass.copy(motion = MotionLevel.Off),
                glass.copy(glassEdges = GlassEdges.Frosted),
                glass.copy(frost = FrostStrength.Off),
            )
        for (case in cases) {
            appearance = case
            platform = FakeKitPlatform(liquidGlass = false)
            waitForIdle()
            val before = onRoot().captureToImage().toPixelMap()
            platform = FakeKitPlatform(liquidGlass = true)
            waitForIdle()
            val after = onRoot().captureToImage().toPixelMap()
            assertEquals(before.width, after.width)
            var differ = 0
            for (y in 0 until before.height) for (x in 0 until before.width) if (before[x, y] != after[x, y]) differ++
            assertEquals(0, differ, "${case.style} ${case.mode} motion=${case.motion} edges=${case.glassEdges}: pixels changed")
        }
    }
}
