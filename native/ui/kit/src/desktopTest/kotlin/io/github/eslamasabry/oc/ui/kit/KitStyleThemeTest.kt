package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Corner
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import io.github.eslamasabry.oc.ui.kit.style.KitStyleSolid
import io.github.eslamasabry.oc.ui.kit.style.KitStyleState
import io.github.eslamasabry.oc.ui.kit.style.KitStylePaper
import io.github.eslamasabry.oc.ui.kit.style.KitShadow
import io.github.eslamasabry.oc.ui.kit.style.KitStyles
import io.github.eslamasabry.oc.ui.kit.style.KitSwitchLook
import io.github.eslamasabry.oc.ui.kit.style.LocalKitStyle
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.MaterialBridge
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The Style reaches the theme: the roles, the M3 shapes and the knobs every kit part reads (B-KIT-004/005/007). */
@OptIn(ExperimentalTestApi::class)
class KitStyleThemeTest {
    private class Seen {
        var colors: KitColors? = null
        var style: KitStyleState? = null
        var shapes: Shapes? = null
        var background: Color? = null
        var tint: Color? = null
        var motion: MotionLevel? = null
    }

    private fun seen(appearance: KitAppearance, signals: KitSystemSignals = KitSystemSignals()): Seen {
        val seen = Seen()
        runComposeUiTest {
            setContent {
                KitTheme(appearance = appearance, platform = FakeKitPlatform(signals), localeTag = "en", stillFrame = true) {
                    seen.colors = LocalKitColors.current
                    seen.style = LocalKitStyle.current
                    seen.shapes = MaterialTheme.shapes
                    seen.background = MaterialTheme.colorScheme.background
                    seen.tint = MaterialTheme.colorScheme.surfaceTint
                    seen.motion = LocalKitMotion.current.level
                }
            }
            waitForIdle()
        }
        return seen
    }

    @Test
    fun `B-KIT-037 KitTheme provides Solid at its defaults and keeps the theme's own colours`() {
        val s = seen(KitAppearance())
        assertEquals(KitStyleState.Solid, s.style)
        assertEquals(Graphite.dark.toKitColors(), s.colors)
        assertSame(MaterialBridge.shapes, s.shapes)
    }

    @Test
    fun `B-KIT-046 the chosen style moves the page ground for every screen, and high contrast turns Soft Solid`() {
        val paper = seen(KitAppearance(mode = AppearanceMode.Light, style = KitSurfaceStyle.Paper))
        assertEquals(KitSurfaceStyle.Paper, paper.style!!.style)
        assertEquals(Color(KitStyles.restyled(Graphite.light, KitSurfaceStyle.Paper).ground.v), paper.colors!!.ground)
        assertNotEquals(Color(Graphite.light.ground.v), paper.colors!!.ground)
        val soft = seen(KitAppearance(style = KitSurfaceStyle.Soft), KitSystemSignals(highContrast = true))
        assertEquals(KitSurfaceStyle.Solid, soft.style!!.style)
        assertEquals(Graphite.dark.toKitColors(), soft.colors)
        val saver = seen(KitAppearance(mode = AppearanceMode.Saver, style = KitSurfaceStyle.Brutal))
        assertEquals(KitStyleState.Solid, saver.style)
    }

    @Test
    fun `B-KIT-004 no theme elevation lifts content and the only shadow is the elevation shadow`() {
        val s = seen(KitAppearance())
        assertEquals(Color.Transparent, s.tint, "tonal elevation never tints a surface")
        val lift = KitStyleSolid.lift(Graphite.dark)
        assertEquals(KitShadow(argb(0x4D000000), dy = 6f, blur = 16f, spread = -6f), lift)
        assertEquals(KitStyleSolid.lift(Graphite.light), lift.copy(color = Graphite.light.elevationShadow))
        // No kit part raises a Material elevation or draws a shadow of its own.
        val sources = File("src/commonMain/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.size > 20, "the kit's sources are found")
        val raised = Regex("""(tonalElevation|shadowElevation|elevation)\s*=\s*(?!0\.dp)[0-9]+(\.[0-9]+)?\.dp|Modifier\.shadow\(|\.shadow\(""")
        val lifted = sources.filter { raised.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals(emptyList(), lifted)
    }

    @Test
    fun `B-KIT-004 a switch that cannot act looks unavailable, on or off`() {
        for (roles in listOf(Graphite.dark, Graphite.light)) {
            val colors = roles.toKitColors()
            val live = KitSwitchLook.of(colors, checked = true, active = true)
            val fixed = KitSwitchLook.of(colors, checked = true, active = false)
            assertEquals(colors.accent, live.track)
            assertNotEquals(colors.accent, fixed.track)
            assertNotEquals(live, fixed)
            assertNotEquals(KitSwitchLook.of(colors, checked = false, active = true).track, fixed.track)
        }
    }

    @Test
    fun `B-KIT-005 shapes come from the kit tokens and Sharp corners squares them all`() {
        assertSame(MaterialBridge.shapes, seen(KitAppearance(style = KitSurfaceStyle.Brutal, corners = KitCorners.Round)).shapes)
        val sharp = seen(KitAppearance(style = KitSurfaceStyle.Brutal, corners = KitCorners.Sharp)).shapes!!
        val two = RoundedCornerShape(2.dp)
        assertEquals(listOf(two, two, two, two, two), listOf(sharp.extraSmall, sharp.small, sharp.medium, sharp.large, sharp.extraLarge))
        val paper = seen(KitAppearance(style = KitSurfaceStyle.Paper)).shapes!!
        assertEquals(RoundedCornerShape(0.dp), paper.medium)
        assertEquals(0f, KitStylePaper.tokens.sharpRadius)
        val soft = seen(KitAppearance(style = KitSurfaceStyle.Solid, corners = KitCorners.Soft)).shapes!!
        assertEquals(RoundedCornerShape(11.dp), soft.medium)
        // The kit's named shapes: buttons, fields, cards and pills square; a circle stays a circle.
        val state = KitStyleState.of(KitAppearance(style = KitSurfaceStyle.Brutal, corners = KitCorners.Sharp), highContrast = false)
        for (shape in KitShapeId.entries - KitShapeId.Circle - KitShapeId.Square) {
            assertEquals(Corner.Radius(2), state.corner(shape), "$shape")
        }
        assertEquals(Corner.Full, state.corner(KitShapeId.Circle))
        assertEquals(Corner.Radius(14), KitStyleState.Solid.corner(KitShapeId.Button))
    }

    @Test
    fun `B-KIT-007 the page is its plain colour until a look is chosen and the stored Motion choice is obeyed`() {
        val plain = seen(KitAppearance(motion = MotionLevel.Calm))
        assertEquals(plain.colors!!.ground, plain.background)
        assertEquals(Color(Graphite.dark.ground.v), plain.background)
        assertEquals(MotionLevel.Calm, plain.motion)
        var appearance by mutableStateOf(KitAppearance(motion = MotionLevel.Calm))
        val levels = mutableListOf<MotionLevel>()
        runComposeUiTest {
            setContent {
                KitTheme(appearance = appearance, platform = FakeKitPlatform(), localeTag = "en", stillFrame = true) {
                    levels += LocalKitMotion.current.level
                }
            }
            waitForIdle()
            appearance = KitAppearance(motion = MotionLevel.Full)
            waitForIdle()
            appearance = KitAppearance(motion = MotionLevel.Off)
            waitForIdle()
        }
        assertEquals(listOf(MotionLevel.Calm, MotionLevel.Full, MotionLevel.Off), levels.distinct())
    }
}
