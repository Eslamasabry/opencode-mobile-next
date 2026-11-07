package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.presentation.BackExitStep
import io.github.eslamasabry.oc.feature.chats.presentation.HomeBackExit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** E05-S21: the double-back exit window on the bare conversation list (Flutter home_screen.dart `_onRootPop`). */
class HomeBackExitTest {
    @Test
    fun `E05-S21 the first back shows the hint and keeps the app`() {
        val idle = HomeBackExit.Idle
        assertTrue(idle.holdsBack, "the bare list must catch the first Back, or one Back closes the app")
        assertEquals(BackExitStep.ShowHint, idle.step())
        val after = idle.afterBack()
        assertTrue(after.hintShowing)
    }

    @Test
    fun `E05-S21 a second back inside the window leaves the app`() {
        val armed = HomeBackExit.Idle.afterBack()
        assertEquals(BackExitStep.LeaveApp, armed.step())
        assertFalse(armed.holdsBack, "inside the window the system takes Back and closes the app")
    }

    @Test
    fun `E05-S21 after the window the hint folds away and the next back starts over`() {
        val ended = HomeBackExit.Idle.afterBack().cleared()
        assertFalse(ended.hintShowing)
        assertTrue(ended.holdsBack)
        assertEquals(BackExitStep.ShowHint, ended.step())
    }

    @Test
    fun `E05-S21 the window is the released app's two seconds`() {
        assertEquals(2_000L, HomeBackExit.WINDOW_MS)
    }
}
