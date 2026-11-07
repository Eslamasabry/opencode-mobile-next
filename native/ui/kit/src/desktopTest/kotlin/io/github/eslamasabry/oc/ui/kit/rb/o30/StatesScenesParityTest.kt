package io.github.eslamasabry.oc.ui.kit.rb.o30

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.scene.StatesFolderScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesScenesAll
import io.github.eslamasabry.oc.ui.kit.scene.StatesSearchScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesSheetScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesTerminalScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesTrayScene
import io.github.eslamasabry.oc.ui.kit.scene.StatesUnpluggedScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/scenes/states_scenes.dart. */
class StatesScenesParityTest {
    init {
        ensureGraphics()
    }

    @Test fun `the six state drawings, in the gallery's order, draw in the default box and never mirror`() {
        assertEquals(
            listOf(
                StatesSheetScene,
                StatesFolderScene,
                StatesTrayScene,
                StatesSearchScene,
                StatesTerminalScene(),
                StatesUnpluggedScene,
            ),
            StatesScenesAll,
        )
        for (scene in StatesScenesAll) {
            assertEquals(Size(120f, 120f), scene.box)
            assertFalse(scene.mirrorsInRtl)
        }
    }

    @Test fun `the sheet scene's geometry`() {
        assertEquals(Rect(44f, 20f, 92f, 84f), StatesSheetScene.BACK)
        assertEquals(Rect(26f, 32f, 80f, 102f), StatesSheetScene.FRONT)
        assertEquals(12f, StatesSheetScene.BACK_FOLD)
        assertEquals(
            listOf(
                Offset(36f, 58f) to Offset(68f, 58f),
                Offset(36f, 70f) to Offset(62f, 70f),
                Offset(36f, 82f) to Offset(50f, 82f),
            ),
            StatesSheetScene.LINES,
        )
    }

    @Test fun `the folder and tray sit on a wash at 60,64, the tray's sheet and the search's lens`() {
        assertEquals(Offset(60f, 64f), StatesFolderScene.WASH_CENTRE)
        assertEquals(Rect(36f, 26f, 84f, 80f), StatesTrayScene.SHEET)
        assertEquals(Rect(22f, 22f, 72f, 86f), StatesSearchScene.SHEET)
        assertEquals(Offset(70f, 64f), StatesSearchScene.LENS)
        assertEquals(21f, StatesSearchScene.LENS_RADIUS)
    }

    @Test fun `the terminal window, its cursor and the ended state`() {
        assertEquals(Rect(14f, 26f, 106f, 96f), StatesTerminalScene.WINDOW)
        assertEquals(10f, StatesTerminalScene.WINDOW_RADIUS)
        assertEquals(Offset(47f, 72f), StatesTerminalScene.CURSOR_FROM)
        assertEquals(Offset(61f, 72f), StatesTerminalScene.CURSOR_TO)
        assertNotEquals(StatesTerminalScene(), StatesTerminalScene(ended = true), "a scene differs when ended changes")
    }

    @Test fun `the cursor arrives at 70 percent, blinks twice, then rests lit`() {
        assertFalse(StatesTerminalScene.cursorLit(frameAt(.69f)), "not arrived yet")
        assertTrue(StatesTerminalScene.cursorLit(frameAt(.75f)))
        assertFalse(StatesTerminalScene.cursorLit(frameAt(.80f)), "first blink .78-.84")
        assertTrue(StatesTerminalScene.cursorLit(frameAt(.87f)))
        assertFalse(StatesTerminalScene.cursorLit(frameAt(.93f)), "second blink .9-.96")
        assertTrue(StatesTerminalScene.cursorLit(frameAt(1f)), "rests lit")
    }

    @Test fun `while waiting the cursor blinks four times a loop`() {
        assertTrue(StatesTerminalScene.cursorLit(frameAt(1f, loop = .1f, looping = true)))
        assertFalse(StatesTerminalScene.cursorLit(frameAt(1f, loop = .15f, looping = true)))
        assertTrue(StatesTerminalScene.cursorLit(frameAt(1f, loop = .26f, looping = true)))
        assertFalse(StatesTerminalScene.cursorLit(frameAt(1f, loop = .9f, looping = true)))
    }

    @Test fun `the unplugged scene is drawn 1_2 times about the middle, with three gap strokes`() {
        assertEquals(1.2f, StatesUnpluggedScene.ZOOM)
        assertEquals(Offset(63f, 61f), StatesUnpluggedScene.GAP)
        assertEquals(listOf(-118f, -90f, -62f), StatesUnpluggedScene.GAP_ANGLES)
    }

    @Test fun `every state drawing is empty before its entrance and inked after it, with the accent`() {
        for (scene in StatesScenesAll) {
            val name = scene::class.simpleName
            assertEquals(0, inked(paint(scene, frameAt(0f))), "$name draws nothing at entrance 0")
            val done = paint(scene, frameAt(1f))
            assertTrue(inked(done) > 2000, "$name is drawn when finished")
            assertTrue(pixelsOf(done, testPalette.accent) > 10, "$name keeps the accent for the one thing that matters")
            assertTrue(inked(paint(scene, frameAt(.5f))) > 0, "$name is part-drawn halfway")
        }
    }

    @Test fun `the ended terminal has no accent at all`() {
        val ended = paint(StatesTerminalScene(ended = true), frameAt(1f))
        assertEquals(0, pixelsOf(ended, testPalette.accent))
        assertTrue(pixelsOf(paint(StatesTerminalScene(), frameAt(1f)), testPalette.accent) > 0)
    }

    @Test fun `the unplugged cables start drawing at the very start`() {
        assertTrue(inked(paint(StatesUnpluggedScene, frameAt(.1f))) > 0)
    }
}
