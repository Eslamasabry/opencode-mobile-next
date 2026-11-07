package io.github.eslamasabry.oc.ui.kit.rb.o31

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.KitLivePalette
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import io.github.eslamasabry.oc.ui.kit.live.liveMix
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleAurora
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

/** Every constant and rule of lib/ui/kit/live_background/kit_live_style.dart at 8302de537. */
class KitLiveStyleParityTest {
    private object Plain : KitLiveStyle() {
        override val id = "plain"
        override val label = Res.string.liveStyleAurora

        override fun paint(canvas: DrawScope, size: Size, frame: KitLiveFrame) {}
    }

    @Test fun aStyleDefaultsToTheCeilingAndTheClocksRate() {
        assertEquals(1.0, Plain.ceilingFactor)
        assertEquals(12, Plain.framesPerSecond)
        assertFalse(Plain.wantsTextHalo(palette(dark = true)))
    }

    @Test fun theCeilingIsSixPercentDarkAndFourAndAHalfLight() {
        assertEquals(0.06, palette(dark = true).ceiling)
        assertEquals(0.045, palette(dark = false).ceiling)
    }

    @Test fun thePaletteIsTheThemesRoles() {
        for (dark in listOf(true, false)) {
            val c = ThemeResolver.roles(PackId.Opencode, null, dark, false, null).toKitColors()
            val p = KitLivePalette.of(c)
            assertEquals(c.ground, p.ground)
            assertEquals(c.text1, p.ink)
            assertEquals(c.text2, p.muted)
            assertEquals(c.text3, p.faint)
            assertEquals(c.accent, p.green)
            assertEquals(c.accent, p.glow)
            assertEquals(c.codeKeyword, p.blue)
            assertEquals(liveMix(c.accent, c.codeKeyword, .35), p.teal) // kit_live_background.dart:102
            assertEquals(dark, p.dark)
        }
    }

    @Test fun memoryKeepsOneValuePerKeyUntilCleared() {
        val m = KitLiveMemory()
        val a = m.of("k") { Any() }
        assertSame(a, m.of("k") { Any() })
        m.clear()
        assertFalse(a === m.of("k") { Any() })
    }

    @Test fun liveMixIsFlutterColorLerpPerChannel() {
        val mid = liveMix(Color(0f, 0f, 0f, 1f), Color(1f, .5f, .2f, 1f), .5)
        assertEquals(.5f, mid.red, 1f / 255)
        assertEquals(.25f, mid.green, 1f / 255)
        assertEquals(.1f, mid.blue, 1f / 255)
        assertEquals(liveMix(Color.Black, Color.White, 2.0), Color.White, "t is clamped to 0..1")
    }

    private fun palette(dark: Boolean) = KitLivePalette.of(ThemeResolver.roles(PackId.Opencode, null, dark, false, null).toKitColors())
}
