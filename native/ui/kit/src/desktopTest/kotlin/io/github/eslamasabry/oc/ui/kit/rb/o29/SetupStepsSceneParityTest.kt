package io.github.eslamasabry.oc.ui.kit.rb.o29

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.accent
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.assertColor
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.ink
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.line
import io.github.eslamasabry.oc.ui.kit.rb.o29.SceneProbe.muted
import io.github.eslamasabry.oc.ui.kit.scene.SetupSceneHalt
import io.github.eslamasabry.oc.ui.kit.scene.SetupSceneStage
import io.github.eslamasabry.oc.ui.kit.scene.SetupStepsScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/setup_steps_scene.dart. */
class SetupStepsSceneParityTest {
    init {
        SceneProbe.ensure()
    }

    private val download = SetupStepsScene(SetupSceneStage.Download, done = 3, total = 5, fraction = .6f)

    @Test
    fun `B-KIT-O29-050 the strip keeps its box, stages, halts and geometry, and mirrors under RTL`() {
        assertEquals(Size(240f, 100f), download.box)
        assertTrue(download.mirrorsInRtl)
        SceneProbe.assertOrder(listOf("Download", "Unpack", "Install", "Start"), SetupSceneStage.entries)
        SceneProbe.assertOrder(listOf("Paused", "Failed"), SetupSceneHalt.entries)
        assertEquals(Rect(104f, 50f, 138f, 78f), SetupStepsScene.parcel)
        assertEquals(4f, SetupStepsScene.PARCEL_RADIUS)
        assertEquals(Rect(184.5f, 13f, 215.5f, 89f), SetupStepsScene.screen)
        assertEquals(5f, SetupStepsScene.SCREEN_RADIUS)
        assertEquals(Offset(76f, 62f) to Offset(96f, 62f), SetupStepsScene.legOne)
        assertEquals(Offset(146f, 62f) to Offset(168f, 62f), SetupStepsScene.legTwo)
        assertEquals(Offset(200f, 76f), SetupStepsScene.portal)
        assertEquals(
            listOf(Offset(44f, 58f), Offset(121f, 64f), Offset(200f, 50f), Offset(200f, 50f)),
            SetupStepsScene.washAt,
        )
        assertEquals(6, SetupStepsScene.MAX_ROWS)
        assertEquals(5, SetupStepsScene.LEG_DOTS)
    }

    @Test
    fun `B-KIT-O29-051 finished screen lines follow the job's count, never the clock`() {
        assertEquals(3, SetupStepsScene.shownRows(done = 3, total = 5, rows = 5))
        assertEquals(5, SetupStepsScene.shownRows(done = 5, total = 6, rows = 6))
        assertEquals(1, SetupStepsScene.shownRows(done = 3, total = 10, rows = 6))
        assertEquals(0, SetupStepsScene.shownRows(done = 0, total = 0, rows = 0))
        assertEquals(6, SetupStepsScene.shownRows(done = 10, total = 10, rows = 6))
    }

    @Test
    fun `B-KIT-O29-052 a strip differs by every field`() {
        assertEquals(download, download.copy())
        assertNotEquals(download, download.copy(done = 4))
        assertNotEquals(download, download.copy(fraction = .7f))
        assertNotEquals(download, download.copy(halted = SetupSceneHalt.Paused))
        assertNotEquals(download, download.copy(stage = SetupSceneStage.Unpack))
    }

    @Test
    fun `B-KIT-O29-053 the first leg lights as far as the measured fraction`() {
        val px = SceneProbe.render(download)
        assertColor(accent, px.at(76f, 62f), "dot at 0")
        assertColor(accent, px.at(86f, 62f), "dot at .5 (lit, .5 <= .6)")
        assertColor(line, px.at(91f, 62f), "dot at .75 (not yet)")
        assertColor(accent, px.at(43f, 34f), "the cloud is the active station")
        assertColor(accent, px.at(43f, 55f), "the download arrow")
    }

    @Test
    fun `B-KIT-O29-054 a failed strip draws its active part in the neutral failure ink, never the accent`() {
        val px = SceneProbe.render(download.copy(halted = SetupSceneHalt.Failed))
        assertColor(ink, px.at(86f, 62f), "lit dot in failure ink")
        assertColor(ink, px.at(43f, 34f), "cloud in failure ink")
        assertTrue(px.all().none { it.green > .9f && it.red < .1f && it.blue < .1f && it.alpha > .5f }, "no accent pixel")
        val paused = SceneProbe.render(download.copy(halted = SetupSceneHalt.Paused))
        assertColor(muted, paused.at(86f, 62f), "paused: muted")
    }

    @Test
    fun `B-KIT-O29-055 starting shows the portal on the screen and every finished line`() {
        val px = SceneProbe.render(SetupStepsScene(SetupSceneStage.Start, done = 5, total = 6))
        assertColor(accent, px.at(200f, 76f), "the spark lands in the portal")
        for (i in 0 until 5) assertColor(accent.copy(alpha = .95f), px.at(200f, 22f + i * 8.5f), "finished line $i", .08f)
        val last = px.at(200f, 22f + 5 * 8.5f)
        assertFalse(SceneProbe.close(last, accent, .2f), "the sixth line is still to come: $last")
    }

    @Test
    fun `B-KIT-O29-056 under RTL the strip flips so the phone sits at the start`() {
        val ltr = SceneProbe.render(download)
        val rtl = SceneProbe.render(download, mirror = true)
        assertColor(accent, ltr.at(43f, 34f), "cloud at the left")
        assertColor(accent, rtl.at(240f - 43f, 34f), "cloud at the right when mirrored")
        assertFalse(SceneProbe.close(rtl.at(43f, 34f), accent), "nothing accent where the cloud was")
    }

    @Test
    fun `B-KIT-O29-057 a halted strip stops its loop`() {
        val loop = SceneProbe.frame(loop = .5f, looping = true)
        val halted = download.copy(halted = SetupSceneHalt.Paused)
        assertEquals(
            SceneProbe.render(halted).all().toList(),
            SceneProbe.render(halted, loop).all().toList(),
            "a paused strip draws the same at any loop phase",
        )
    }
}
