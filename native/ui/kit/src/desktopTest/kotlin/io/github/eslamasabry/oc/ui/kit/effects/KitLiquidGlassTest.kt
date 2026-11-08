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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
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
import io.github.eslamasabry.oc.ui.kit.theme.KitHct
import io.github.eslamasabry.oc.ui.kit.theme.ThemeSwitchColorRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Glass's liquid edges in the kit (docs/designs/liquid-glass.md, owner 2026-11-08): when the floating parts are liquid
 * glass and when they keep today's face (B-KIT-018), that every other style draws pixel for pixel as before
 * (B-KIT-037), and that a chat's composer never flips between KitScreen's two layouts.
 */
class KitLiquidGlassTest {
    @Test
    fun `B-KIT-003 the rim light is the accent leaning to s33's cool night and warm day anchors, never white`() {
        for (pack in PackId.entries) {
            val accent = { dark: Boolean ->
                Color(ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null).accent.v).copy(alpha = 1f)
            }
            val dark = LiquidRim.of(accent(true), dark = true)
            val light = LiquidRim.of(accent(false), dark = false)
            assertNotEquals(dark, light, "$pack")
            for ((c, anchor) in listOf(dark to ThemeSwitchColorRules.NIGHT_HUE, light to ThemeSwitchColorRules.DAY_HUE)) {
                val hct = KitHct.of(c)
                assertTrue(hct.chroma >= 8.0, "$pack $c has colour (chroma ${hct.chroma})")
                // The pull leaves at most (1 - PULL) of a half turn between the rim's hue and the anchor.
                assertTrue(hueGap(hct.hue, anchor) <= 110.0, "$pack $c hue ${hct.hue} leans to $anchor")
            }
        }
    }

    private fun hueGap(a: Double, b: Double): Double {
        val d = kotlin.math.abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

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

    /** [Scene] alone in a fresh composition, captured once it is idle. */
    private fun render(appearance: KitAppearance, platform: FakeKitPlatform): androidx.compose.ui.graphics.PixelMap {
        lateinit var image: androidx.compose.ui.graphics.PixelMap
        runComposeUiTest {
            setContent {
                KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                    Box(Modifier.testTag("scene")) { Scene(loading = false) }
                }
            }
            waitForIdle()
            image = onNodeWithTag("scene").captureToImage().toPixelMap()
        }
        return image
    }

    @Test
    fun `B-KIT-037 every other style, and Glass wherever liquid is held, draws pixel for pixel as before`() {
        // The same scene on a platform without the lens and on one with it: every pixel must match.
        val cases =
            Style.entries.filter { it != Style.Glass }.flatMap { s ->
                listOf(KitAppearance(style = s, mode = AppearanceMode.Dark), KitAppearance(style = s, mode = AppearanceMode.Light))
            } + listOf(
                glass.copy(motion = MotionLevel.Off),
                glass.copy(glassEdges = GlassEdges.Frosted),
                glass.copy(frost = FrostStrength.Off),
            )
        for (case in cases) {
            val without = render(case, FakeKitPlatform(liquidGlass = false))
            val with = render(case, FakeKitPlatform(liquidGlass = true))
            assertEquals(without.width to without.height, with.width to with.height, "${case.style} size")
            var differ = 0
            for (y in 0 until without.height) for (x in 0 until without.width) if (without[x, y] != with[x, y]) differ++
            assertEquals(0, differ, "${case.style} ${case.mode} motion=${case.motion} edges=${case.glassEdges}: $differ pixels changed")
        }
    }

    @Test
    fun `B-KIT-037 the same scene renders the same twice (the comparison is sound)`() {
        val a = render(KitAppearance(), FakeKitPlatform(liquidGlass = false))
        val b = render(KitAppearance(), FakeKitPlatform(liquidGlass = false))
        var differ = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) differ++
        assertEquals(0, differ, "two renders of one scene differ by $differ pixels")
    }
}
