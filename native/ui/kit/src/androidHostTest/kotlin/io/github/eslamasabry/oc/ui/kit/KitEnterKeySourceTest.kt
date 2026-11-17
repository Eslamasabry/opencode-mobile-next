package io.github.eslamasabry.oc.ui.kit

import android.view.KeyCharacterMap
import android.view.KeyEvent
import io.github.eslamasabry.oc.ui.kit.input.isFromSoftKeyboard
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue 148: an on-screen keyboard's Enter is told apart from a real keyboard's by the key event itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [KitRobolectric.SDK], application = Application::class)
class KitEnterKeySourceTest {
    private fun enter(device: Int, flags: Int) =
        KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, 0, device, 0, flags, 0)

    @Test
    fun `a key from the virtual keyboard device is soft`() {
        assertTrue(enter(KeyCharacterMap.VIRTUAL_KEYBOARD, 0).isFromSoftKeyboard())
    }

    @Test
    fun `a key flagged soft keyboard is soft even from a real device id`() =
        assertTrue(enter(5, KeyEvent.FLAG_SOFT_KEYBOARD).isFromSoftKeyboard())

    @Test
    fun `an editor action key is soft`() =
        assertTrue(enter(5, KeyEvent.FLAG_EDITOR_ACTION).isFromSoftKeyboard())

    @Test
    fun `a physical keyboard key is not soft`() = assertFalse(enter(5, 0).isFromSoftKeyboard())
}
