package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertNear
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.ink
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.none
import io.github.eslamasabry.oc.ui.kit.scene.SetupReadyScene
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Every constant and rule of lib/ui/kit/scenes/setup_ready_scene.dart. */
class SetupReadySceneParityTest {
    init {
        SceneProbe.ensure()
    }

    private val scene = SetupReadyScene()

    @Test
    fun `B-KIT-O29-040 the celebration keeps its box, burst and confetti`() {
        assertEquals(Size(160f, 120f), scene.box)
        assertFalse(scene.mirrorsInRtl)
        assertEquals(Offset(80f, 60f), SetupReadyScene.center)
        assertEquals(Offset(80f, 58f), SetupReadyScene.portal)
        assertEquals(Rect(61f, 17f, 99f, 101f), SetupReadyScene.screen)
        assertEquals(5f, SetupReadyScene.SCREEN_RADIUS)
        assertEquals(8, SetupReadyScene.rays.size)
        SetupReadyScene.rays.forEachIndexed { i, r ->
            val a = i * Math.PI / 4 + .3
            assertNear(Offset(cos(a).toFloat(), sin(a).toFloat()), r, "ray $i")
        }
        assertEquals(
            listOf(
                Triple(Offset(28f, 30f), .6f, false),
                Triple(Offset(136f, 72f), -.5f, false),
                Triple(Offset(124f, 100f), .9f, true),
                Triple(Offset(24f, 76f), -.8f, true),
                Triple(Offset(140f, 20f), .2f, true),
            ),
            SetupReadyScene.confetti,
        )
    }

    @Test
    fun `B-KIT-O29-041 the finished frame rests with check, sparkles and confetti stay, rays are gone`() {
        val px = SceneProbe.render(scene)
        assertColor(ink, px.at(54f, 60f), "phone frame (ink)")
        assertColor(accent, px.at(80f - 15f * .1f, 58f + 15f * .28f), "check's turn")
        assertColor(accent, px.at(126f, 30f), "big sparkle")
        assertColor(accent.copy(alpha = .9f), px.at(28f, 30f), "accent confetti", tol = .08f)
        // A ray of the burst would sit at about (145, 77) half-way; at rest nothing is drawn there.
        assertColor(none, px.at(147f, 77.3f), "no ray at rest")
    }

    @Test
    fun `B-KIT-O29-042 mid-burst the rays shoot out and fade`() {
        val px = SceneProbe.render(scene, SceneProbe.frame(entrance = .775f))
        val ray = px.at(147f, 77.3f)
        assertEquals(true, ray.alpha > .02f && ray.green > .5f, "a faint accent ray mid-burst: $ray")
    }

    @Test
    fun `B-KIT-O29-043 nothing shows before the entrance`() {
        val px = SceneProbe.render(scene, SceneProbe.frame(entrance = 0f))
        assertColor(none, px.at(54f, 60f), "frame")
        assertColor(none, px.at(126f, 30f), "sparkle")
    }
}
