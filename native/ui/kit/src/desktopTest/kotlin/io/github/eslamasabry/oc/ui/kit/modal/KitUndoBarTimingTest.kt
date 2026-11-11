package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Device QA 2026-11-11, sweep B: a tap on Undo soon after a removal was ignored, and a tap where Undo had just been
 * reached what lies under the bar. With the clock paused and stepped frame by frame, at full motion, and a control as
 * large as the page under the bar: Undo runs from the bar's first frame (its touch place does not travel with the
 * rise), a tap on the leaving bar runs nothing at all, the bar's place stays inert 300 ms ([GUARD_MS]) after the window
 * ran out, and a finger on the bar holds the window.
 */
@OptIn(ExperimentalTestApi::class)
class KitUndoBarTimingTest {
    private class Scene {
        var offer by mutableStateOf<KitUndoOffer?>(null)
        var under = 0
        var undo = 0
        var timedOut = 0
    }

    private fun ComposeUiTest.host(scene: Scene, motion: MotionLevel = MotionLevel.Full) {
        mainClock.autoAdvance = false
        setContent {
            KitTheme(KitAppearance(motion = motion), FakeKitPlatform(), "en") {
                Box(Modifier.fillMaxSize()) {
                    // What lies under the bar: a control as large as the page.
                    Box(Modifier.fillMaxSize().clickable { scene.under++ })
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        KitUndoBar(
                            scene.offer,
                            // The host's part: Undo and the timeout end the offer.
                            onUndo = {
                                scene.undo++
                                scene.offer = null
                            },
                            onTimeout = {
                                scene.timedOut++
                                scene.offer = null
                            },
                        )
                    }
                }
            }
        }
        mainClock.advanceTimeBy(500)
    }

    /** Where Undo's centre stands at rest (dp), found with a first offer that is then undone and let go. */
    private fun ComposeUiTest.restingUndo(scene: Scene): Pair<Dp, Dp> {
        scene.offer = KitUndoOffer("rest", MESSAGE)
        mainClock.advanceTimeBy(1_000)
        val b = onNodeWithText("Undo").getBoundsInRoot()
        scene.offer = null
        mainClock.advanceTimeBy(2_000)
        return ((b.left + b.right) / 2) to ((b.top + b.bottom) / 2)
    }

    private fun ComposeUiTest.tapAt(at: Pair<Dp, Dp>) {
        onRoot().performTouchInput { click(Offset(at.first.toPx(), at.second.toPx())) }
    }

    @Test
    fun `UNDO-T1 Undo runs from the bar's first frame, and nothing under it fires`() =
        runDesktopComposeUiTest(W, H) {
            val scene = Scene()
            host(scene)
            val undo = restingUndo(scene)
            scene.offer = OFFER
            mainClock.advanceTimeByFrame()
            onNodeWithTag("kit-undo").assertExistsNow()
            tapAt(undo)
            mainClock.advanceTimeBy(500)
            assertEquals(1, scene.undo, "a tap on Undo's place in the bar's first frame runs Undo")
            assertEquals(0, scene.under, "the control under the bar")
        }

    @Test
    fun `UNDO-T2 at rest Undo runs and nothing under it fires`() =
        runDesktopComposeUiTest(W, H) {
            val scene = Scene()
            host(scene)
            val undo = restingUndo(scene)
            scene.offer = OFFER
            mainClock.advanceTimeBy(2_000)
            tapAt(undo)
            mainClock.advanceTimeBy(500)
            assertEquals(1, scene.undo, "Undo")
            assertEquals(0, scene.under, "the control under the bar")
        }

    @Test
    fun `UNDO-T3 in the leaving bar's last frame a tap on Undo's place runs nothing`() =
        runDesktopComposeUiTest(W, H) {
            val scene = Scene()
            host(scene)
            val undo = restingUndo(scene)
            scene.offer = OFFER
            mainClock.advanceTimeBy(UNDO_WINDOW_MS + 16)
            assertEquals(1, scene.timedOut, "the window ran out")
            // The exit lasts QUICK (150 ms): nine frames in, it is on its last frame.
            repeat(9) { mainClock.advanceTimeByFrame() }
            onNodeWithTag("kit-undo").assertExistsNow()
            tapAt(undo)
            mainClock.advanceTimeBy(50)
            assertEquals(0, scene.undo, "the window has closed: Undo no longer runs")
            assertEquals(0, scene.under, "the control under the leaving bar")
        }

    @Test
    fun `UNDO-T4 after the window ran out the bar's place stays inert for the guard, then the page is live again`() =
        runDesktopComposeUiTest(W, H) {
            val scene = Scene()
            host(scene)
            val undo = restingUndo(scene)
            scene.offer = OFFER
            mainClock.advanceTimeBy(UNDO_WINDOW_MS + 16)
            // The exit (150 ms) is over; 100 ms into the guard.
            mainClock.advanceTimeBy(250)
            tapAt(undo)
            mainClock.advanceTimeBy(50)
            assertEquals(0, scene.under, "a late tap meant for Undo, inside the guard")
            assertEquals(0, scene.undo, "Undo")
            mainClock.advanceTimeBy(GUARD_MS + 100)
            tapAt(undo)
            mainClock.advanceTimeBy(50)
            assertEquals(1, scene.under, "after the guard the page under the bar's place is live again")
        }

    @Test
    fun `UNDO-T5 after the person's own Undo the place under the bar is live as soon as the bar is gone`() =
        runDesktopComposeUiTest(W, H) {
            val scene = Scene()
            host(scene)
            val undo = restingUndo(scene)
            scene.offer = OFFER
            mainClock.advanceTimeBy(1_000)
            tapAt(undo)
            mainClock.advanceTimeBy(250)
            assertEquals(1, scene.undo, "Undo")
            tapAt(undo)
            mainClock.advanceTimeBy(50)
            assertEquals(1, scene.under, "no guard after the person's own Undo")
            assertEquals(1, scene.undo, "Undo ran once")
        }

    @Test
    fun `UNDO-T6 a finger on the bar holds the window, and lifting it starts the whole window again`() =
        runDesktopComposeUiTest(W, H) {
            val scene = Scene()
            host(scene)
            scene.offer = OFFER
            mainClock.advanceTimeBy(7_000)
            onNodeWithText(MESSAGE).performTouchInput { down(center) }
            mainClock.advanceTimeBy(3_000)
            assertEquals(0, scene.timedOut, "held: the window does not run out under a finger")
            onNodeWithText(MESSAGE).performTouchInput { up() }
            mainClock.advanceTimeBy(UNDO_WINDOW_MS - 1_000)
            assertEquals(0, scene.timedOut, "lifting started the whole window again")
            mainClock.advanceTimeBy(1_500)
            assertEquals(1, scene.timedOut, "then it runs out")
            assertEquals(0, scene.under, "the touch on the bar's words reached nothing under it")
        }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsNow() {
        assertExists("the bar is on screen at this moment")
    }

    private companion object {
        const val W = 412
        const val H = 860
        const val MESSAGE = "Alpha left the dock"

        /** The kit's UNDO_GUARD_MS (written out so the test also runs against a bar that has none). */
        const val GUARD_MS = 300L
        val OFFER = KitUndoOffer("u1", MESSAGE)
    }
}
