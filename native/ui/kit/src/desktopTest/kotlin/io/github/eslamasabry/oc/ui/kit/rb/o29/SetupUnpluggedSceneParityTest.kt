package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertNear
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.muted
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.none
import io.github.eslamasabry.oc.ui.kit.scene.SetupUnpluggedScene
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/setup_unplugged_scene.dart. */
class SetupUnpluggedSceneParityTest {
    init {
        SceneProbe.ensure()
    }

    private val scene = SetupUnpluggedScene()

    @Test
    fun `B-KIT-O29-060 the unplugged scene keeps its box, plug, portal and marks`() {
        assertEquals(Size(160f, 120f), scene.box)
        assertFalse(scene.mirrorsInRtl)
        assertEquals(Rect(40f, 50f, 60f, 70f), SetupUnpluggedScene.plug)
        assertEquals(4f, SetupUnpluggedScene.PLUG_RADIUS)
        assertEquals(Offset(120f, 60f), SetupUnpluggedScene.portal)
        assertNear(Offset(40f, 60f), SceneProbe.start(SetupUnpluggedScene.cable), "cable start")
        assertNear(Offset(6f, 100f), SceneProbe.end(SetupUnpluggedScene.cable), "cable end")
        assertEquals(3, SetupUnpluggedScene.marks.size)
        listOf(-.9f, 0f, .9f).forEachIndexed { i, a ->
            val d = Offset(cos(a), sin(a))
            assertNear(Offset(80f, 60f) + d * 5f, SetupUnpluggedScene.marks[i].first, "mark $i inner")
            assertNear(Offset(80f, 60f) + d * 11f, SetupUnpluggedScene.marks[i].second, "mark $i outer")
        }
    }

    @Test
    fun `B-KIT-O29-061 at rest the plug is accent, the portal grey and the gap marked`() {
        val px = SceneProbe.render(scene)
        assertColor(accent, px.at(40f, 60f), "plug edge")
        assertColor(accent, px.at(65f, 55.5f), "upper prong")
        assertColor(accent, px.at(65f, 64.5f), "lower prong")
        val bracket = px.at(118f, 41.6f)
        assertTrue(bracket.red > .6f && bracket.green < .3f, "grey portal in the muted role: $bracket")
        assertColor(muted, px.at(94f, 60f), "the middle gap mark, shifted 6 right")
    }

    @Test
    fun `B-KIT-O29-062 while it keeps trying the plug nudges and a spark flickers in the portal`() {
        val still = SceneProbe.render(scene)
        assertFalse(SceneProbe.close(still.at(120f, 60f), accent, .3f), "no spark at rest")
        val reaching = SceneProbe.render(scene, SceneProbe.frame(loop = .5f, looping = true))
        val spark = reaching.at(120f, 60f)
        assertTrue(spark.green > .5f && spark.red < .2f, "an accent spark at full reach: $spark")
        assertColor(accent, reaching.at(45f, 60f), "the plug moved 5 to the right")
        assertColor(accent, still.at(40f, 55f), "the plug's edge at rest")
        assertColor(none, reaching.at(40f, 55f), "which it left while reaching")
    }
}
