package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertNear
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.ink
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.line
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.muted
import io.github.eslamasabry.oc.ui.kit.scene.ServersLinkScene
import io.github.eslamasabry.oc.ui.kit.scene.ServersLinkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/servers_link_scene.dart. */
class ServersLinkSceneParityTest {
    init {
        SceneProbe.ensure()
    }

    @Test
    fun `B-KIT-O29-080 the link scene keeps its box, states and geometry, and mirrors under RTL`() {
        val scene = ServersLinkScene(ServersLinkState.Idle)
        assertEquals(Size(200f, 84f), scene.box)
        assertTrue(scene.mirrorsInRtl)
        assertTrue(scene.intro)
        SceneProbe.assertOrder(listOf("Idle", "Linking", "Linked", "Failed"), ServersLinkState.entries)
        assertEquals(Rect(26f, 18f, 54f, 66f), ServersLinkScene.phone.body)
        assertEquals(Rect(112f, 16f, 168f, 56f), ServersLinkScene.laptop.screen)
        assertEquals(Offset(140f, 36f), ServersLinkScene.portal)
        assertNear(Offset(62f, 42f), SceneProbe.start(ServersLinkScene.link), "link starts at the phone")
        assertNear(Offset(104f, 38f), SceneProbe.end(ServersLinkScene.link), "link ends at the laptop")
        assertEquals(7f, ServersLinkScene.DOT_STEP)
    }

    @Test
    fun `B-KIT-O29-081 the link's halves leave a gap in the middle fifth`() {
        val length = ServersLinkScene.metric.length
        assertNear(ServersLinkScene.metric.getPosition(length * .4f), ServersLinkScene.leftEnd, "left end at .4")
        assertNear(ServersLinkScene.metric.getPosition(length * .6f), ServersLinkScene.rightEnd, "right end at .6")
        assertNear(ServersLinkScene.leftEnd, SceneProbe.end(ServersLinkScene.left), "left half ends at .4", .05f)
        assertNear(ServersLinkScene.rightEnd, SceneProbe.start(ServersLinkScene.right), "right half starts at .6", .05f)
        assertTrue(ServersLinkScene.middle.y < 38f, "the link arcs above its ends: ${ServersLinkScene.middle}")
        val dots = ServersLinkScene.dots
        assertEquals((length / 7f).toInt() + 1, dots.size)
        assertNear(Offset(62f, 42f), dots.first(), "first dot at the phone")
    }

    @Test
    fun `B-KIT-O29-082 the travelling dot fades in and out over 15 percent at each end`() {
        assertNear(0f, ServersLinkScene.travelFade(0f), "start")
        assertNear(.5f, ServersLinkScene.travelFade(.075f), "fading in")
        assertNear(1f, ServersLinkScene.travelFade(.5f), "middle")
        assertNear(.5f, ServersLinkScene.travelFade(.925f), "fading out")
        assertNear(0f, ServersLinkScene.travelFade(1f), "end")
    }

    @Test
    fun `B-KIT-O29-083 a link scene differs by state and intro`() {
        assertEquals(ServersLinkScene(ServersLinkState.Linked), ServersLinkScene(ServersLinkState.Linked))
        assertNotEquals(ServersLinkScene(ServersLinkState.Linked), ServersLinkScene(ServersLinkState.Failed))
        assertNotEquals(ServersLinkScene(ServersLinkState.Idle), ServersLinkScene(ServersLinkState.Idle, intro = false))
    }

    @Test
    fun `B-KIT-O29-084 each state draws its own link`() {
        val mid = ServersLinkScene.middle
        val linking = SceneProbe.render(ServersLinkScene(ServersLinkState.Linking))
        assertColor(accent, linking.at(mid), "linking: the still dot rests midway")
        val linked = SceneProbe.render(ServersLinkScene(ServersLinkState.Linked))
        assertColor(accent, linked.at(mid), "linked: the spark on the link")
        assertColor(accent, linked.at(34f, 18f + 48f * .34f), "linked: the phone's screen lines turn accent")
        val failed = SceneProbe.render(ServersLinkScene(ServersLinkState.Failed))
        assertFalse(SceneProbe.close(failed.at(mid), accent, .3f), "failed: nothing in the gap")
        assertColor(ink, failed.at(ServersLinkScene.leftEnd + Offset(-2.5f, 1f)), "failed: the ends in the failure ink")
        assertColor(muted, failed.at(140f, 36f - 22f * 23f / 60f), "failed: the portal goes grey")
        val idle = SceneProbe.render(ServersLinkScene(ServersLinkState.Idle))
        assertColor(line, idle.at(ServersLinkScene.dots[2]), "idle: a dotted gap in the line role")
        assertColor(accent, idle.at(140f, 36f - 22f * 23f / 60f), "idle: the portal in the accent")
    }

    @Test
    fun `B-KIT-O29-085 under RTL the phone moves to the right`() {
        val scene = ServersLinkScene(ServersLinkState.Idle)
        val ltr = SceneProbe.render(scene)
        val rtl = SceneProbe.render(scene, mirror = true)
        assertColor(muted, ltr.at(26f, 42f), "phone outline on the left")
        assertColor(muted, rtl.at(200f - 26f, 42f), "phone outline on the right when mirrored")
    }
}
