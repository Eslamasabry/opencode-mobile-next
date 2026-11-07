package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.ink
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.muted
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.none
import io.github.eslamasabry.oc.ui.kit.scene.SetupPhoneMood
import io.github.eslamasabry.oc.ui.kit.scene.SetupPhoneScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/setup_phone_scene.dart. */
class SetupPhoneSceneParityTest {
    init {
        SceneProbe.ensure()
    }

    @Test
    fun `B-KIT-O29-030 the phone keeps its box, moods and geometry`() {
        val scene = SetupPhoneScene()
        assertEquals(SetupPhoneMood.Fresh, scene.mood)
        assertEquals(Size(160f, 120f), scene.box)
        assertFalse(scene.mirrorsInRtl)
        SceneProbe.assertOrder(listOf("Fresh", "Starting", "Termux", "Ready", "Stopped"), SetupPhoneMood.entries)
        assertEquals(Offset(80f, 60f), SetupPhoneScene.center)
        assertEquals(Rect(54f, 8f, 106f, 112f), SetupPhoneScene.frameRect)
        assertEquals(Rect(61f, 17f, 99f, 101f), SetupPhoneScene.screenRect)
        assertEquals(5f, SetupPhoneScene.SCREEN_RADIUS)
        assertEquals(
            listOf(Offset(66f, 44f) to 22f, Offset(66f, 53f) to 14f, Offset(66f, 62f) to 26f, Offset(66f, 71f) to 18f),
            SetupPhoneScene.termLines,
        )
    }

    @Test
    fun `B-KIT-O29-031 a phone differs only by its mood`() {
        assertEquals(SetupPhoneScene(SetupPhoneMood.Ready), SetupPhoneScene(SetupPhoneMood.Ready))
        assertNotEquals(SetupPhoneScene(SetupPhoneMood.Ready), SetupPhoneScene(SetupPhoneMood.Stopped))
    }

    private fun render(mood: SetupPhoneMood, frame: io.github.eslamasabry.oc.ui.kit.scene.SceneFrame = SceneProbe.frame()) =
        SceneProbe.render(SetupPhoneScene(mood), frame)

    @Test
    fun `B-KIT-O29-032 a fresh phone shows an ink frame, a prompt, the portal and its sparkles`() {
        val px = render(SetupPhoneMood.Fresh)
        assertColor(ink, px.at(54f, 60f), "frame (ink)")
        assertColor(accent, px.at(80f, 66f), "portal spark at (80, 66)")
        assertColor(accent, px.at(70f, 30f), "prompt tip")
        assertColor(accent, px.at(76f, 34f), "typed line")
        assertColor(accent, px.at(126f, 26f), "big sparkle")
        assertColor(muted.copy(alpha = .5f), px.at(34f, 34f), "a quiet dot at half strength", tol = .08f)
        assertColor(none, px.at(80f, 4f), "no orbiting dot while still")
    }

    @Test
    fun `B-KIT-O29-033 a ready phone raises its portal to (80, 58) with no prompt`() {
        val px = render(SetupPhoneMood.Ready)
        assertColor(accent, px.at(80f, 58f), "spark at (80, 58)")
        assertFalse(SceneProbe.close(px.at(76f, 34f), accent), "no typed line on a ready phone")
    }

    @Test
    fun `B-KIT-O29-034 a stopped phone is muted, has a moon and no accent anywhere`() {
        val px = render(SetupPhoneMood.Stopped)
        assertColor(muted, px.at(54f, 60f), "frame (muted)")
        assertColor(muted.copy(alpha = .8f), px.at(122f, 30f), "moon", tol = .08f)
        assertTrue(px.all().none { it.green > .9f && it.red < .1f && it.blue < .1f && it.alpha > .5f }, "no accent pixel")
    }

    @Test
    fun `B-KIT-O29-035 the termux phone types lines and lights the last one`() {
        val px = render(SetupPhoneMood.Termux)
        // The first line is muted at .7 over the lit screen (surface), so the muted red dominates.
        val first = px.at(70f, 44f)
        assertTrue(first.red > .6f && first.green < .45f && first.alpha > .99f, "first line muted over the screen: $first")
        assertColor(accent, px.at(75f, 71f), "last line accent")
        assertColor(accent, px.at(90f, 71f), "the spark after the last line")
    }

    @Test
    fun `B-KIT-O29-036 only a starting phone that loops sends a dot round it`() {
        val loop = SceneProbe.frame(loop = 0f, looping = true)
        assertColor(accent, render(SetupPhoneMood.Starting, loop).at(80f, 4f), "orbit dot at the top")
        assertColor(none, render(SetupPhoneMood.Fresh, loop).at(80f, 4f), "fresh never orbits")
    }

    @Test
    fun `B-KIT-O29-037 nothing shows before the entrance`() {
        val px = render(SetupPhoneMood.Fresh, SceneProbe.frame(entrance = 0f))
        assertColor(none, px.at(54f, 60f), "frame")
        assertColor(none, px.at(80f, 66f), "spark")
    }
}
