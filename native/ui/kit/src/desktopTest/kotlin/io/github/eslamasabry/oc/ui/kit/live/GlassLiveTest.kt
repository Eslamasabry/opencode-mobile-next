@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiveSpots
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.RIM_LIGHT_GAIN
import io.github.eslamasabry.oc.ui.kit.foundation.rimLightMatrix
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** glass-live (owner 2026-11-09): what the glass knows about the live picture behind it, and how it catches its light. */
class GlassLiveTest {
    private fun pictureSpots(appearance: KitAppearance): Pair<List<Argb>, Roles> {
        var spots: List<Argb>? = null
        var roles: Roles? = null
        runDesktopComposeUiTest(10, 10) {
            setContent {
                KitTheme(appearance, FakeKitPlatform(), "en", stillFrame = true) {
                    spots = kitLivePictureSpots()
                    roles = LocalKitRoles.current
                }
            }
            waitForIdle()
        }
        return requireNotNull(spots) to requireNotNull(roles)
    }

    @Test
    fun `B-KIT-LIVE-048 a liquid face knows the picture behind it from the appearance, wherever it is`() {
        val glass = KitAppearance(mode = AppearanceMode.Dark, style = Style.Glass, liveBackground = true, liveStyle = "matrixRain")
        val (matrix, roles) = pictureSpots(glass)
        assertEquals(LiveSpots.forLook(roles, "matrixRain", lens = true), matrix)
        val (painted, r2) = pictureSpots(glass.copy(liveStyle = AuroraImage.ID))
        assertEquals(AuroraImage.spotsOf(r2.ground, r2.accent, r2.dark, frosted = true).toList(), painted)
        // The flat ground: Background off, Saver, an id the app does not draw.
        assertEquals(emptyList(), pictureSpots(glass.copy(liveBackground = false)).first)
        assertEquals(emptyList(), pictureSpots(glass.copy(mode = AppearanceMode.Saver)).first)
        assertEquals(emptyList(), pictureSpots(glass.copy(liveStyle = "notALook")).first)
    }

    @Test
    fun `B-KIT-LIVE-049 the glass picture steps at most 60 times a second however fast the screen`() {
        val clock = LiveFrameClock().apply { acquire() }
        var stepped: (() -> Float)? = null
        var every: (() -> Float)? = null
        runDesktopComposeUiTest(10, 10) {
            setContent {
                stepped = rememberLiveTime(clock, animates = true, fps = GLASS_PICTURE_FPS)
                every = rememberLiveTime(clock, animates = true)
            }
            waitForIdle()
        }
        val seen = mutableSetOf<Float>()
        val seenEvery = mutableSetOf<Float>()
        var t = 0L
        repeat(120) { // one second at 120 Hz
            clock.frame(t)
            seen += stepped!!()
            seenEvery += every!!()
            t += 8_333_333L
        }
        assertTrue(seen.size in 55..61, "the picture took ${seen.size} steps in a second")
        assertTrue(seenEvery.size >= 115, "the shader itself still follows every frame (${seenEvery.size})")
    }

    @Test
    fun `B-KIT-LIVE-050 the rim catches the picture's light as white, only where it is brighter than the ground`() {
        val ground = .08f
        val m = rimLightMatrix(ground)
        fun lit(r: Int, g: Int, b: Int): FloatArray {
            val v = m.values
            fun row(i: Int) = (v[i * 5] * r + v[i * 5 + 1] * g + v[i * 5 + 2] * b + v[i * 5 + 3] * 255f + v[i * 5 + 4]).coerceIn(0f, 255f)
            return floatArrayOf(row(0), row(1), row(2), row(3))
        }
        val g = (ground * 255f).toInt()
        assertTrue(lit(g, g, g)[3] < 2f, "nothing over the plain ground")
        assertEquals(0f, lit(5, 5, 5)[3], "nothing over darker than the ground")
        val green = lit(40, 200, 90) // a matrix glyph's head
        assertTrue(green[3] > 100f, "a bright spot lights the rim: ${green[3]}")
        assertTrue(green.take(3).all { it == 255f }, "always white light, never the look's colour")
        assertTrue(lit(120, 120, 120)[3] > lit(60, 60, 60)[3], "brighter behind, brighter rim")
        assertTrue(RIM_LIGHT_GAIN > 1f)
    }
}
