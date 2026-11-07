package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertNear
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.muted
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.none
import io.github.eslamasabry.oc.ui.kit.scene.ServersWelcomeScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Every constant and rule of lib/ui/kit/scenes/servers_welcome_scene.dart. */
class ServersWelcomeSceneParityTest {
    init {
        SceneProbe.ensure()
    }

    private val scene = ServersWelcomeScene()

    @Test
    fun `B-KIT-O29-090 the welcome keeps its box and cast`() {
        assertEquals(Size(200f, 120f), scene.box)
        assertFalse(scene.mirrorsInRtl)
        assertEquals(Rect(78f, 14f, 176f, 78f), ServersWelcomeScene.laptop.screen)
        assertEquals(Rect(24f, 52f, 56f, 108f), ServersWelcomeScene.phone.body)
        assertEquals(Offset(127f, 46f), ServersWelcomeScene.portal)
        assertEquals(6.5f, ServersWelcomeScene.DOT_STEP)
        assertNear(Offset(40f, 44f), SceneProbe.start(ServersWelcomeScene.path), "path starts above the phone")
        assertNear(Offset(88f, 9f), SceneProbe.end(ServersWelcomeScene.path), "path ends at the laptop")
        assertNear(Offset(40f, 44f), ServersWelcomeScene.dots.first(), "first dot")
    }

    @Test
    fun `B-KIT-O29-091 the path dots across one at a time between 55 and 90 percent`() {
        val n = ServersWelcomeScene.dots.size
        assertEquals(0, ServersWelcomeScene.shownDots(.5f, n))
        assertEquals(0, ServersWelcomeScene.shownDots(.55f, n))
        assertEquals(n, ServersWelcomeScene.shownDots(.9f, n))
        assertEquals(n, ServersWelcomeScene.shownDots(1f, n))
        val mid = ServersWelcomeScene.shownDots(.7f, n)
        kotlin.test.assertTrue(mid in 1 until n, "part of the path at .7: $mid of $n")
    }

    @Test
    fun `B-KIT-O29-092 the finished welcome shows laptop, portal spark, phone, path and sparkles`() {
        val px = SceneProbe.render(scene)
        assertColor(muted, px.at(78f, 46f), "laptop outline")
        assertColor(accent, px.at(127f, 46f), "spark in the portal")
        assertColor(muted, px.at(24f, 80f), "phone outline")
        assertColor(accent, px.at(ServersWelcomeScene.dots[3]), "a path dot")
        assertColor(accent.copy(alpha = .55f), px.at(188f, 23f), "sparkle's vertical stroke", .08f)
    }

    @Test
    fun `B-KIT-O29-093 nothing shows before the entrance and a resting welcome never loops`() {
        val empty = SceneProbe.render(scene, SceneProbe.frame(entrance = 0f))
        assertColor(none, empty.at(78f, 46f), "laptop at t=0")
        assertEquals(
            SceneProbe.render(scene).all().toList(),
            SceneProbe.render(scene, SceneProbe.frame(loop = .5f, looping = true)).all().toList(),
        )
    }
}
