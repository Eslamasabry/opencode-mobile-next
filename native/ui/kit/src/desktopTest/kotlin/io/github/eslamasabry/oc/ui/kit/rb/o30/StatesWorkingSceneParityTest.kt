package io.github.eslamasabry.oc.ui.kit.rb.o30

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.scene.StatesWorkingScene
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/states_working_scene.dart. */
class StatesWorkingSceneParityTest {
    init {
        ensureGraphics()
    }

    @Test fun `the mark draws in a 48-unit box and never mirrors`() {
        assertEquals(Size(48f, 48f), StatesWorkingScene.box)
        assertFalse(StatesWorkingScene.mirrorsInRtl)
    }

    @Test fun `mark_svg's viewBox 24 to 84 maps into 4 to 44`() {
        assertNear(40f / 60f, StatesWorkingScene.S)
        assertEquals(Offset(4f, 4f), StatesWorkingScene.p(24f, 24f))
        assertNear(44f, StatesWorkingScene.p(84f, 84f).x)
        assertNear(44f, StatesWorkingScene.p(84f, 84f).y)
        assertNear(8f, StatesWorkingScene.RADIUS)
        assertEquals(Offset(24f, 24f), StatesWorkingScene.CENTRE)
    }

    @Test fun `the pen, drift, orbit and spark sizes`() {
        assertEquals(5.5f, StatesWorkingScene.PEN)
        assertEquals(1.4f, StatesWorkingScene.DRIFT)
        assertEquals(5f, StatesWorkingScene.ORBIT)
        assertEquals(3.6f, StatesWorkingScene.SPARK)
        assertEquals(3.8f, StatesWorkingScene.REST)
    }

    @Test fun `the spark starts its orbit at the top and goes round once a loop`() {
        assertNear((-PI / 2).toFloat(), StatesWorkingScene.sparkAngle(0f))
        assertNear((PI / 2).toFloat(), StatesWorkingScene.sparkAngle(.5f))
        assertNear((3 * PI / 2).toFloat(), StatesWorkingScene.sparkAngle(1f))
    }

    @Test fun `still it is the mark with the spark in the middle, working the spark leaves the middle`() {
        val still = paint(StatesWorkingScene, frameAt(1f), scale = 4)
        assertTrue(pixelsOf(still, testPalette.accent) > 500)
        assertTrue(centre(still), "the resting spark sits in the middle")
        val working = paint(StatesWorkingScene, frameAt(1f, loop = .25f, looping = true), scale = 4)
        assertFalse(centre(working), "the travelling spark has left the middle")
        assertNotEquals(inked(still), inked(working))
    }

    @Test fun `before the spark lands only the brackets draw`() {
        val early = paint(StatesWorkingScene, frameAt(.5f), scale = 4)
        assertTrue(inked(early) > 0)
        assertFalse(centre(early))
    }
}
