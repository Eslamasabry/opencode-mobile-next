package io.github.eslamasabry.oc.android.terminal

import android.app.Activity
import android.view.MotionEvent
import android.widget.FrameLayout
import com.termux.view.TerminalView
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The grid in an agent sign-in ignored the keyboard: Termux's TerminalView is not focusable unless its layout XML says
 * so, and this one is built in code. A view that cannot take focus never gets an input connection, so a tap showed no
 * keyboard and a hardware keyboard's keys went nowhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TerminalKeyboardFocusTest {
    private fun attached(): Pair<TerminalView, TerminalSurfaceHost> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = TerminalView(activity, null)
        val host = TerminalSurfaceHost(activity, view)
        val root = FrameLayout(activity)
        activity.setContentView(root)
        root.addView(host, FrameLayout.LayoutParams(400, 400))
        return view to host
    }

    @Test
    fun `the grid takes focus, by touch too, once it is hosted`() {
        val (view, _) = attached()
        assertTrue(view.isFocusable, "focusable")
        assertTrue(view.isFocusableInTouchMode, "focusable in touch mode")
    }

    @Test
    fun `a tap on the grid focuses it and asks for the keyboard`() {
        val (view, _) = attached()
        TerminalInputClient(view).onSingleTapUp(MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 10f, 10f, 0))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(view.isFocused, "the tapped grid holds focus")
        assertTrue(shadowOf(view.context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)).isSoftInputVisible, "keyboard shown")
    }

    @Test
    fun `typing goes by characters so every keyboard sends what was typed`() {
        val (view, _) = attached()
        assertTrue(TerminalInputClient(view).shouldEnforceCharBasedInput())
    }
}
