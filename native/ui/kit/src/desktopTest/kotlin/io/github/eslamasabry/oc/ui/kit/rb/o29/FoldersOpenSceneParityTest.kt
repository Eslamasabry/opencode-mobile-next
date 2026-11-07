package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertNear
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.ink
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.muted
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.none
import io.github.eslamasabry.oc.ui.kit.scene.KitFoldersOpenScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/folders_open_scene.dart. */
class FoldersOpenSceneParityTest {
    init {
        SceneProbe.ensure()
    }

    private val scene = KitFoldersOpenScene()

    @Test
    fun `B-KIT-O29-010 the folder keeps its geometry`() {
        assertEquals(Size(120f, 120f), scene.box)
        assertFalse(scene.mirrorsInRtl)
        assertEquals(Offset(88f, 22f), KitFoldersOpenScene.spark)
        assertEquals(1.2f, KitFoldersOpenScene.SWING_MAX)
        assertEquals(
            listOf(
                Offset(88f, 12f) to Offset(88f, 7f),
                Offset(98f, 22f) to Offset(103f, 22f),
                Offset(95f, 15f) to Offset(99f, 11f),
            ),
            KitFoldersOpenScene.rays,
        )
        assertNear(Offset(52f, 36f), SceneProbe.start(KitFoldersOpenScene.back), "back starts at the tab's end")
        assertNear(Offset(40f, 62f), SceneProbe.start(KitFoldersOpenScene.sheet), "sheet start")
        assertNear(Offset(76f, 62f), SceneProbe.end(KitFoldersOpenScene.sheet), "sheet end")
        assertNear(Offset(76f, 40f), SceneProbe.end(KitFoldersOpenScene.fold), "fold end")
        assertNear(Offset(26f, 92f), SceneProbe.start(KitFoldersOpenScene.front), "front start")
    }

    @Test
    fun `B-KIT-O29-011 the finished folder shows back, sheet, front and spark in their roles`() {
        val px = SceneProbe.render(scene)
        assertColor(muted, px.at(37f, 29f), "back tab edge (muted)")
        assertColor(ink, px.at(52f, 30f), "sheet top (ink hairline)")
        assertColor(accent, px.at(60f, 92f), "front bottom edge (accent)")
        assertColor(accent, px.at(88f, 22f), "spark")
        assertColor(accent, px.at(88f, 9.5f), "the top ray reached its end")
        // The front is opaque: the surface, then the accent wash, hides the sheet behind it.
        val face = px.at(60f, 75f)
        assertTrue(face.alpha > .99f, "the front is opaque")
        assertTrue(face.blue > .8f && face.green > .8f, "surface under the wash: $face")
    }

    @Test
    fun `B-KIT-O29-012 the front swings in after the back has drawn, and the spark lands last`() {
        val half = SceneProbe.render(scene, SceneProbe.frame(entrance = .45f))
        assertColor(muted, half.at(37f, 29f), "back finished at .45")
        assertColor(none, half.at(30f, 75f), "no front yet at .45")
        assertColor(none, half.at(88f, 22f), "no spark yet")
        val start = SceneProbe.render(scene, SceneProbe.frame(entrance = 0f))
        assertColor(none, start.at(37f, 29f), "nothing at t=0")
        val done = SceneProbe.render(scene)
        assertTrue(done.at(30f, 75f).alpha > .99f, "front in place at the end")
    }

    @Test
    fun `B-KIT-O29-013 a resting folder never loops`() {
        val still = SceneProbe.render(scene)
        val looping = SceneProbe.render(scene, SceneProbe.frame(loop = .5f, looping = true))
        assertEquals(still.all().toList(), looping.all().toList())
    }
}
