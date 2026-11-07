package io.github.eslamasabry.oc.ui.kit.rb.o30

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.scene.TeamDiscoverRelayScene
import io.github.eslamasabry.oc.ui.kit.scene.TeamDiscoverScenesAll
import io.github.eslamasabry.oc.ui.kit.scene.TeamDiscoverTeaserScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/team_discover_scenes.dart. */
class TeamDiscoverScenesParityTest {
    init {
        ensureGraphics()
    }

    @Test fun `the teaser and the relay, in the gallery's order`() {
        assertEquals(listOf(TeamDiscoverTeaserScene, TeamDiscoverRelayScene), TeamDiscoverScenesAll)
    }

    @Test fun `the teaser is a 90 by 60 picture of three agents`() {
        assertEquals(Size(90f, 60f), TeamDiscoverTeaserScene.box)
        assertFalse(TeamDiscoverTeaserScene.mirrorsInRtl)
        assertEquals(Offset(45f, 58f), TeamDiscoverTeaserScene.LEAD)
        assertEquals(Offset(19f, 58f), TeamDiscoverTeaserScene.LEFT)
        assertEquals(Offset(71f, 58f), TeamDiscoverTeaserScene.RIGHT)
    }

    @Test fun `the relay is a 200 by 112 progress drawing that mirrors under RTL`() {
        assertEquals(Size(200f, 112f), TeamDiscoverRelayScene.box)
        assertTrue(TeamDiscoverRelayScene.mirrorsInRtl, "LAY-8: progress direction mirrors")
        assertEquals(110f, TeamDiscoverRelayScene.GROUND)
        assertEquals(listOf(Offset(40f, 110f), Offset(100f, 110f), Offset(160f, 110f)), TeamDiscoverRelayScene.AGENTS)
        assertEquals(listOf(Offset(40f, 48f), Offset(100f, 30f), Offset(160f, 48f)), TeamDiscoverRelayScene.STOPS)
        assertEquals(listOf(0f, .5f, 1f), TeamDiscoverRelayScene.STOP_AT)
    }

    @Test fun `the route runs stop to stop and its dots are 9 apart, short of the end`() {
        assertNear(40f, TeamDiscoverRelayScene.along(0f).x, .05f)
        assertNear(48f, TeamDiscoverRelayScene.along(0f).y, .05f)
        assertNear(160f, TeamDiscoverRelayScene.along(1f).x, .05f)
        assertNear(48f, TeamDiscoverRelayScene.along(1f).y, .05f)
        // The route is symmetric: its middle is the worker's stop.
        assertNear(100f, TeamDiscoverRelayScene.along(.5f).x, .2f)
        assertNear(30f, TeamDiscoverRelayScene.along(.5f).y, .2f)
        val length = TeamDiscoverRelayScene.routeLength
        val dots = TeamDiscoverRelayScene.dots
        // for (d = 9; d < length - 6; d += 9)
        var expected = 0
        var d = 9f
        while (d < length - 6) { expected++; d += 9f }
        assertEquals(expected, dots.size)
        assertTrue(dots.size > 10)
        assertEquals(0, TeamDiscoverRelayScene.shownDots(0f))
        assertEquals(1, TeamDiscoverRelayScene.shownDots(.001f), "a dot shows as soon as the route starts")
        assertEquals(dots.size, TeamDiscoverRelayScene.shownDots(1f))
    }

    @Test fun `both are empty at 0 and finished at 1, the relay's card checked in the success colour`() {
        for (scene in TeamDiscoverScenesAll) {
            assertEquals(0, inked(paint(scene, frameAt(0f))), "${scene::class.simpleName} empty at 0")
            val done = paint(scene, frameAt(1f))
            assertTrue(pixelsOf(done, testPalette.accent) > 10, "${scene::class.simpleName} has its accent")
        }
        val relay = paint(TeamDiscoverRelayScene, frameAt(1f))
        assertTrue(pixelsOf(relay, testPalette.success) > 10, "the check and the sparks")
        val travelling = paint(TeamDiscoverRelayScene, frameAt(.7f))
        assertEquals(0, pixelsOf(travelling, testPalette.success), "no check before .82")
    }
}
