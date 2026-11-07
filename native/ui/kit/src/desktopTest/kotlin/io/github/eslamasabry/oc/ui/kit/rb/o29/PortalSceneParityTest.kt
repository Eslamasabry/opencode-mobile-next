@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertNear
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.none
import io.github.eslamasabry.oc.ui.kit.scene.KitFoldersOpenScene
import io.github.eslamasabry.oc.ui.kit.scene.KitPortalScene
import io.github.eslamasabry.oc.ui.kit.scene.SceneDraw
import io.github.eslamasabry.oc.ui.kit.scene.SceneIllustration
import io.github.eslamasabry.oc.ui.kit.scene.ScenePalette
import io.github.eslamasabry.oc.ui.kit.scene.arcToPoint
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/portal_scene.dart, and of the kit_illustration.dart host it draws in. */
class PortalSceneParityTest {
    init {
        SceneProbe.ensure()
    }

    private val scene = KitPortalScene()

    @Test
    fun `B-KIT-O29-001 the portal keeps the mark's geometry in the 120-unit box`() {
        assertEquals(Size(120f, 120f), scene.box)
        assertFalse(scene.mirrorsInRtl)
        assertEquals(1.5f, KitPortalScene.SCALE)
        assertEquals(18f, KitPortalScene.RADIUS)
        assertEquals(Offset(60f, 60f), KitPortalScene.center)
        assertEquals(Offset(70.5f, 25.5f), KitPortalScene.p(61f, 31f))
        assertEquals(Offset(15f, 15f), KitPortalScene.p(24f, 24f))
        assertNear(Offset(70.5f, 25.5f), SceneProbe.start(KitPortalScene.upper), "upper start")
        assertNear(Offset(25.5f, 75f), SceneProbe.end(KitPortalScene.upper), "upper end")
        assertNear(Offset(94.5f, 45f), SceneProbe.start(KitPortalScene.lower), "lower start")
        assertNear(Offset(45f, 94.5f), SceneProbe.end(KitPortalScene.lower), "lower end")
    }

    @Test
    fun `B-KIT-O29-002 the finished portal draws both brackets round the right corner and lands its spark`() {
        val px = SceneProbe.render(scene)
        assertColor(accent, px.at(60f, 60f), "spark")
        assertColor(accent, px.at(57f, 25.5f), "upper bracket")
        assertColor(accent, px.at(60f, 94.5f), "lower bracket")
        // The upper corner turns about (43, 43) in mark units (counter-clockwise), so its middle sits outside-in.
        assertColor(accent, px.at(30.77f, 30.77f), "upper corner")
        assertColor(none, px.at(38.23f, 38.23f), "no corner bulging inwards")
        assertColor(accent, px.at(89.23f, 89.23f), "lower corner")
    }

    @Test
    fun `B-KIT-O29-003 before the entrance nothing is drawn and the spark lands last`() {
        val empty = SceneProbe.render(scene, SceneProbe.frame(entrance = 0f))
        assertColor(none, empty.at(57f, 25.5f), "upper bracket at t=0")
        assertColor(none, empty.at(60f, 60f), "spark at t=0")
        val early = SceneProbe.render(scene, SceneProbe.frame(entrance = .6f))
        assertColor(none, early.at(60f, 60f), "spark before .6")
    }

    @Test
    fun `B-KIT-O29-004 only a looping portal shows the halo and circles its spark`() {
        val still = SceneProbe.render(scene)
        assertColor(none, still.at(104f, 60f), "no halo at rest")
        val looping = SceneProbe.render(scene, SceneProbe.frame(loop = 0f, looping = true))
        assertColor(accent.copy(alpha = .22f), looping.at(104f, 60f), "halo at breath 0", tol = .05f)
        assertColor(accent, looping.at(60f, 53f), "spark at the top of its 7-unit orbit")
        assertColor(none, looping.at(60f, 60.6f), "spark has left the middle")
    }

    @Test
    fun `B-KIT-O29-005 the shared drawing helpers match KitDraw`() {
        assertEquals(5f, SceneDraw.STROKE)
        assertEquals(2.5f, SceneDraw.HAIRLINE)
        assertEquals(0f, SceneDraw.interval(.2f, .3f, .7f))
        assertEquals(1f, SceneDraw.interval(.7f, .3f, .7f))
        assertEquals(.5f, SceneDraw.interval(.5f, .3f, .7f, LinearEasing))
        assertTrue(SceneDraw.interval(.5f, .3f, .7f) > .5f, "the default curve is easeOutCubic")
        assertTrue(SceneDraw.interval(.75f, .6f, 1f, SceneDraw.land) > SceneDraw.interval(.75f, .6f, 1f), "land overshoots")
        assertNear(0f, SceneDraw.wave(0f), "wave at 0")
        assertNear(1f, SceneDraw.wave(.5f), "wave at .5")
        assertNear(.5f, SceneDraw.wave(.25f), "wave at .25")
        assertNear(1f, SceneDraw.wave(0f, .5f), "wave phase")
        assertNear(.25f, SceneDraw.fade(Color.Black.copy(alpha = .5f), .5f).alpha, "fade multiplies alpha")
        assertNear(.5f, SceneDraw.fade(Color.Black.copy(alpha = .5f), 3f).alpha, "fade clamps at 1")
        assertEquals(0f, SceneDraw.fade(Color.Black, -1f).alpha)
    }

    @Test
    fun `B-KIT-O29-006 arcToPoint turns the shorter way in the asked direction`() {
        val anti = Path().apply { moveTo(10f, 0f); arcToPoint(Offset(10f, 0f), Offset(0f, 10f), 10f, clockwise = false) }
        val r = 10f - 10f / sqrt(2f)
        assertNear(Offset(r, r), SceneProbe.along(anti, .5f), "counter-clockwise middle", .05f)
        val clock = Path().apply { moveTo(10f, 0f); arcToPoint(Offset(10f, 0f), Offset(0f, 10f), 10f, clockwise = true) }
        assertNear(Offset(10f - r, 10f - r), SceneProbe.along(clock, .5f), "clockwise middle", .05f)
    }

    @Test
    fun `B-KIT-O29-007 a scene equals another of its kind and nothing else`() {
        assertEquals(KitPortalScene(), KitPortalScene())
        assertEquals(KitPortalScene().hashCode(), KitPortalScene().hashCode())
        assertNotEquals<Any>(KitPortalScene(), KitFoldersOpenScene())
    }

    @Test
    fun `B-KIT-O29-008 the palette maps theme roles as KitPalette fromRoles does`() = runComposeUiTest {
        var colors: KitColors? = null
        setContent { KitTheme(platform = DesktopKitPlatform()) { colors = LocalKitColors.current } }
        runOnIdle {
            val c = colors!!
            val p = ScenePalette.of(c)
            assertEquals(c.accent, p.accent)
            assertEquals(c.accent.copy(alpha = FlutterKitTokens.SCENE_WASH_ALPHA), p.accentSoft)
            assertEquals(.12f, FlutterKitTokens.SCENE_WASH_ALPHA)
            assertEquals(c.text1, p.ink)
            assertEquals(c.text2, p.muted)
            assertEquals(c.text3, p.line)
            assertEquals(c.surface3, p.surface)
            assertEquals(c.success, p.success)
            assertEquals(c.attention, p.warning)
            assertEquals(c.text1, p.failure, "no red for failures (LOOK-5)")
            assertEquals(c.accent, p.progress)
        }
    }

    private fun spark(motion: MotionLevel, advanceMs: Long?): Color {
        var centre: Color = none
        runComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = motion)) {
                    Box(Modifier.testTag("scene")) { SceneIllustration(scene, width = 120.dp) }
                }
            }
            mainClock.advanceTimeByFrame()
            if (advanceMs != null) mainClock.advanceTimeBy(advanceMs)
            waitForIdle()
            val map = onNodeWithTag("scene").captureToImage().toPixelMap()
            centre = map[map.width / 2, map.height / 2]
        }
        return centre
    }

    @Test
    fun `B-KIT-O29-009 the host plays the entrance, and motion off shows the finished drawing at once`() {
        assertEquals(900, FlutterKitMotion.ENTRANCE_MS)
        assertEquals(4000, FlutterKitMotion.BREATH_MS)
        assertEquals(160, FlutterKitTokens.ILLUSTRATION_PAGE)
        assertTrue(spark(MotionLevel.Full, null).alpha < .5f, "with motion on, the spark has not landed on the first frame")
        assertTrue(spark(MotionLevel.Full, FlutterKitMotion.ENTRANCE_MS + 100L).alpha > .9f, "the entrance finishes")
        assertTrue(spark(MotionLevel.Off, null).alpha > .9f, "motion off: finished at once")
    }
}
